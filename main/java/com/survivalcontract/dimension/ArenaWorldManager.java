package com.survivalcontract.dimension;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.mixin.MinecraftServerAccessor;
import com.survivalcontract.mixin.ServerLevelSeedMixin;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * 竞技场世界管理器 —— 模仿 Yet Another Bingo 的大厅模式：
 * <ul>
 *   <li>每次开始游戏 → 动态创建<b>全新随机种子</b>的竞技场维度组（主世界/下界/末地）</li>
 *   <li>游戏结束回大厅后 → <b>彻底删除</b>竞技场维度存档（下一局从零生成，不会带着上一局的破坏痕迹）</li>
 * </ul>
 *
 * 实现方式（26.2 / 1.21.11）：
 * <ol>
 *   <li>从 LEVEL_STEM 注册表拷贝原版 nether/end 的 LevelStem（维度类型 + 生成器），
 *       用原版 {@code createLevels} 相同的方式手动构造 ServerLevel 并注册进
 *       {@code MinecraftServer.levels}（{@link MinecraftServerAccessor}）</li>
 *   <li>随机种子拦截：{@link ServerLevelSeedMixin} 注入 {@code ServerLevel.getSeed()}，
 *       竞技场维度返回本局随机种子 → ChunkMap 的 RandomState / 结构放置全部用新种子</li>
 *   <li>龙战：26.2 的 ServerLevel 构造器按 {@code DimensionType.hasEnderDragonFight()}
 *       自动为竞技场末地创建全新 {@code EnderDragonFight}（数据存在维度自己的
 *       SavedDataStorage 里）—— 无需任何手动代码，删档即全新龙战</li>
 *   <li>存档位置：自定义维度自动落到 {@code <world>/dimensions/survival-contract/arena*}，
 *       删文件夹即删档（龙战数据等 SavedData 也在维度自己的 data 文件夹里）</li>
 * </ol>
 */
public class ArenaWorldManager {

	/** 竞技场主世界 */
	public static final ResourceKey<Level> ARENA = createKey("arena");
	/** 竞技场下界 */
	public static final ResourceKey<Level> ARENA_NETHER = createKey("arena_nether");
	/** 竞技场末地 */
	public static final ResourceKey<Level> ARENA_END = createKey("arena_end");

	private static final Set<ResourceKey<Level>> ARENA_KEYS = Set.of(ARENA, ARENA_NETHER, ARENA_END);

	/** 本局竞技场随机种子（createArenaWorlds 前无意义，teardown 后清零） */
	private static long currentSeed = 0;

	private ArenaWorldManager() {}

	private static ResourceKey<Level> createKey(String name) {
		return ResourceKey.create(Registries.DIMENSION,
				Identifier.fromNamespaceAndPath("survival-contract", name));
	}

	public static boolean isArenaLevel(ResourceKey<Level> dimension) {
		return dimension != null && ARENA_KEYS.contains(dimension);
	}

	/** 本局竞技场随机种子（getSeed mixin 用） */
	public static long currentSeed() {
		return currentSeed;
	}

	public static ServerLevel getArenaOverworld(MinecraftServer server) {
		return server.getLevel(ARENA);
	}

	public static ServerLevel getArenaNether(MinecraftServer server) {
		return server.getLevel(ARENA_NETHER);
	}

	public static ServerLevel getArenaEnd(MinecraftServer server) {
		return server.getLevel(ARENA_END);
	}

	/**
	 * 下界传送门重定向：竞技场主世界 ↔ 竞技场下界。非竞技场维度返回 null（走原版逻辑）。
	 */
	public static ResourceKey<Level> getNetherDestination(ResourceKey<Level> from) {
		if (from == ARENA) return ARENA_NETHER;
		if (from == ARENA_NETHER) return ARENA;
		return null;
	}

	/**
	 * 开始新游戏：创建全新随机种子的竞技场维度组。
	 * 必须在 {@code setupNewRandomWorld} 之前调用（出生点搜索需要竞技场主世界）。
	 *
	 * @return false = 旧档未能删除（旧区块会被新预设"复用"，任何地形预设都不会生效）
	 *         或维度注册失败——调用方必须中止开局，绝不能把玩家放进原版主世界
	 */
	public static boolean createArenaWorlds(MinecraftServer server) {
		// 1) 清掉上一局残留（包括上次服务器崩溃遗留的存档文件夹）
		removeArenaLevels(server);
		// 1.1) 删档校验：Windows 下句柄释放有延迟，删不掉时新维度会直接复用旧区块
		//      → 玩家无论选哪种地形预设看到的都是上一局的地形（"四种预设都是主世界"根因之一）
		LevelStorageSource.LevelStorageAccess storage = ((MinecraftServerAccessor) server).sc$getStorageSource();
		for (ResourceKey<Level> key : ARENA_KEYS) {
			Path dir = storage.getDimensionPath(key);
			if (Files.exists(dir)) {
				SurvivalContract.LOGGER.error("[Arena] Arena save folder still exists after cleanup: {} "
						+ "—— 中止开局（旧区块会让地形预设失效）", dir);
				return false;
			}
		}

		// 2) 每局保证不同的随机种子
		currentSeed = UUID.randomUUID().getMostSignificantBits() ^ System.nanoTime();
		SurvivalContract.LOGGER.info("[Arena] Creating arena worlds with random seed: {}", currentSeed);
		boolean built = buildArenaLevels(server, currentSeed);
		if (!built) {
			// 构造中途失败可能已生成部分维度文件夹；不清掉会让下一局一直卡在「旧档未删除」
			removeArenaLevels(server);
			currentSeed = 0;
		}
		return built;
	}

	/**
	 * 恢复局内会话：用<b>已保存的同一个种子</b>重建竞技场维度组，<b>不删档</b>。
	 * 单人存档退出重进时，磁盘上的 dimensions/survival-contract/arena* 仍在，
	 * 用相同种子重建后区块数据与玩家建造/破坏完全延续。
	 */
	public static boolean restoreArenaWorlds(MinecraftServer server, long seed) {
		if (seed == 0) {
			SurvivalContract.LOGGER.warn("[Arena] restoreArenaWorlds called with seed 0, falling back to new random seed");
			return createArenaWorlds(server);
		}
		// 只清掉内存中的残留引用（不删档）
		removeArenaLevelsFromRegistryOnly(server);
		currentSeed = seed;
		SurvivalContract.LOGGER.info("[Arena] Restoring arena worlds from saved seed: {} (save kept)", seed);
		return buildArenaLevels(server, seed);
	}

	/** 用指定种子构造并注册竞技场三维度（不含删档逻辑）；返回三维度是否全部注册成功 */
	private static boolean buildArenaLevels(MinecraftServer server, long seed) {
		long obfSeed = BiomeManager.obfuscateSeed(seed);

		// 3) 拷贝原版三个 LevelStem（维度类型 + 区块生成器，无需 JSON 定义）
		Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
		LevelStem netherStem = stems.getValue(LevelStem.NETHER);
		LevelStem endStem = stems.getValue(LevelStem.END);
		// 文明时代：竞技场主世界用自定义地形预设（超平坦变体，第四十九批）；其他模式用原版主世界
		LevelStem owStem = com.survivalcontract.game.GameStateManager.getInstance().isCivilization()
				? terrainStem(server, stems)
				: stems.getValue(LevelStem.OVERWORLD);

		ServerLevelData overworldData = server.getWorldData().overworldData();
		Executor executor = ((MinecraftServerAccessor) server).sc$getExecutor();
		LevelStorageSource.LevelStorageAccess storage = ((MinecraftServerAccessor) server).sc$getStorageSource();
		Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).sc$getLevels();

		ServerLevel arena;
		ServerLevel nether;
		ServerLevel end;
		// 4) 按原版 createLevels 的方式构造三个维度
		//    tickTime=false：DerivedLevelData 与真主世界共享时钟，避免双重推进 dayTime
		List<CustomSpawner> overworldSpawners = List.of(
				new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(),
				new VillageSiege(), new WanderingTraderSpawner(server.getDataStorage()));

		// 构造失败必须整体回滚：否则会留下"部分注册"的维度（例如只有主世界没有下界），
		// 后续传送/传送门都会指向空维度，且下一次开局会误以为维度已存在（第八十九批）
		try {
			arena = new ServerLevel(server, executor, storage,
					new DerivedLevelData(server.getWorldData(), overworldData),
					ARENA, owStem, false, obfSeed, overworldSpawners, false);
			levels.put(ARENA, arena);

			nether = new ServerLevel(server, executor, storage,
					new DerivedLevelData(server.getWorldData(), overworldData),
					ARENA_NETHER, netherStem, false, obfSeed, List.of(), false);
			levels.put(ARENA_NETHER, nether);

			end = new ServerLevel(server, executor, storage,
					new DerivedLevelData(server.getWorldData(), overworldData),
					ARENA_END, endStem, false, obfSeed, List.of(), false);
			levels.put(ARENA_END, end);
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("[Arena] Failed to construct arena levels, rolling back", e);
			removeArenaLevelsFromRegistryOnly(server);
			return false;
		}

		// 5) 龙战：ServerLevel 构造器检测到末地维度类型 hasEnderDragonFight() 已自动创建
		//    全新 EnderDragonFight（数据在竞技场末地自己的存档文件夹里，删档即重置）

		// 6) 世界边界上限 + 边界监听（原版 createLevels 同款收尾）
		for (ServerLevel lvl : List.of(arena, nether, end)) {
			lvl.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
			server.getPlayerList().addWorldborderListener(lvl);
		}

		SurvivalContract.LOGGER.info("[Arena] Arena worlds ready: {}, {}, {} (seed={})", ARENA, ARENA_NETHER, ARENA_END, seed);

		// 7) 注册校验：三维度必须全部可解析，否则调用方要中止开局（禁止退回原版主世界）
		boolean ok = server.getLevel(ARENA) != null && server.getLevel(ARENA_NETHER) != null
				&& server.getLevel(ARENA_END) != null;
		if (!ok) {
			SurvivalContract.LOGGER.error("[Arena] Arena worlds registration incomplete "
					+ "(arena={}, nether={}, end={})",
					server.getLevel(ARENA) != null, server.getLevel(ARENA_NETHER) != null,
					server.getLevel(ARENA_END) != null);
		}
		return ok;
	}

	/**
	 * 文明时代主世界 LevelStem：按自定义地形预设构建（第四十九批）。
	 * 全部为超平坦变体（FlatLevelSource，无结构）——世界规则（游戏规则/边界/维度类型）与主世界预设完全通用。
	 * 层序列【自底向上】：列表首层 = 最底部（基岩），末层 = 顶面方块——与原版超平坦预设
	 * "minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block" 的书写顺序一致。
	 * public 供自测断言（生成器类型/群系/层构成）。
	 */
	public static LevelStem terrainStem(MinecraftServer server, Registry<LevelStem> stems) {
		var preset = com.survivalcontract.game.GameStateManager.getInstance().getTerrainPreset();
		return switch (preset) {
			// 自底向上：基岩 → 石头 → 泥土 → 顶面 1 格雪块
			case SNOW -> customFlatStem(server, stems, "minecraft:snowy_plains", "雪之王国",
					layer(Blocks.BEDROCK, 1), layer(Blocks.STONE, 90),
					layer(Blocks.DIRT, 3), layer(Blocks.SNOW_BLOCK, 1));
			// 自底向上：基岩 → 石头 → 砂岩 → 顶面 4 格沙
			case DESERT -> customFlatStem(server, stems, "minecraft:desert", "沙漠",
					layer(Blocks.BEDROCK, 1), layer(Blocks.STONE, 90),
					layer(Blocks.SANDSTONE, 8), layer(Blocks.SAND, 4));
			// 自底向上：基岩 → 深层（绿宝石/钻石/红石/金/铜）→ 中层（铁/煤）→ 泥土 → 顶面草方块
			case MINER -> customFlatStem(server, stems, "minecraft:plains", "挖掘工的梦想",
					layer(Blocks.BEDROCK, 1), layer(Blocks.STONE, 10), layer(Blocks.EMERALD_ORE, 2),
					layer(Blocks.DIAMOND_ORE, 4), layer(Blocks.LAPIS_ORE, 3), layer(Blocks.REDSTONE_ORE, 4),
					layer(Blocks.STONE, 6), layer(Blocks.GOLD_ORE, 4), layer(Blocks.COPPER_ORE, 4),
					layer(Blocks.STONE, 8), layer(Blocks.IRON_ORE, 5), layer(Blocks.STONE, 8),
					layer(Blocks.COAL_ORE, 4), layer(Blocks.STONE, 20),
					layer(Blocks.DIRT, 3), layer(Blocks.GRASS_BLOCK, 1));
			default -> flatOverworldStem(server, stems); // 预设-主世界（默认）
		};
	}

	private static net.minecraft.world.level.levelgen.flat.FlatLayerInfo layer(Block block, int count) {
		return new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(count, block);
	}

	/**
	 * 自定义超平坦 LevelStem：指定单一群系 + 自定义层序列（克隆超平坦「主世界」预设，
	 * 仅替换群系与层厚，结构覆盖集置空 = 不生成任何结构，与文明时代规则一致）。
	 */
	private static LevelStem customFlatStem(MinecraftServer server, Registry<LevelStem> stems,
	                                        String biomeId, String presetName,
	                                        net.minecraft.world.level.levelgen.flat.FlatLayerInfo... layers) {
		var registries = server.registryAccess();
		var base = registries.lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
				.getOrThrow(net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets.OVERWORLD)
				.value().settings();
		var biome = registries.lookupOrThrow(Registries.BIOME)
				.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.BIOME,
						Identifier.parse(biomeId)));
		var settings = base.withBiomeAndLayers(
				java.util.List.of(layers),
				java.util.Optional.of(net.minecraft.core.HolderSet.empty()),
				biome);
		var generator = new net.minecraft.world.level.levelgen.FlatLevelSource(settings);
		SurvivalContract.LOGGER.info("[Arena] Civilization terrain preset: {} (biome={}, layers={})",
				presetName, biomeId, layers.length);
		return new LevelStem(stems.getValue(LevelStem.OVERWORLD).type(), generator);
	}

	/**
	 * 文明时代超平坦主世界 LevelStem：超平坦「主世界」（Overworld）预设
	 * （世界类型 超平坦 → 自定义 → 预设 → 主世界：层厚与主世界一致，无山丘/树木），
	 * 维度类型仍为主世界。public 供自测断言（无结构/层厚保留）。
	 */
	public static LevelStem flatOverworldStem(MinecraftServer server, Registry<LevelStem> stems) {
		var registries = server.registryAccess();
		var preset = registries.lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
				.getOrThrow(net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets.OVERWORLD)
				.value().settings();
		// 文明时代不生成任何结构：原版超平坦「主世界」预设默认携带 村庄+要塞 结构集覆盖
		// （FlatLevelGeneratorSettings.getDefault: HolderSet.direct(STRONGHOLDS, VILLAGES)），
		// 这里把结构覆盖集替换为空 HolderSet —— 层厚/生物群系/装饰（花草）/湖泊全部原样保留
		// （withBiomeAndLayers 会复制 lakes/decoration/addLakes，仅结构集不同）
		var noStructures = preset.withBiomeAndLayers(
				preset.getLayersInfo(),
				java.util.Optional.of(net.minecraft.core.HolderSet.empty()),
				preset.getBiome());
		var generator = new net.minecraft.world.level.levelgen.FlatLevelSource(noStructures);
		SurvivalContract.LOGGER.info("[Arena] Civilization mode: arena overworld uses SUPERFLAT 'Overworld' preset (no structures)");
		return new LevelStem(stems.getValue(LevelStem.OVERWORLD).type(), generator);
	}

	/** 只把竞技场维度从 levels 注册表移除（关闭句柄但不删档），用于退出重进的维度重建 */
	private static void removeArenaLevelsFromRegistryOnly(MinecraftServer server) {
		Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).sc$getLevels();
		for (ResourceKey<Level> key : ARENA_KEYS) {
			ServerLevel lvl = levels.remove(key);
			if (lvl == null) continue;
			try {
				lvl.close();
			} catch (IOException e) {
				SurvivalContract.LOGGER.warn("[Arena] Failed to close arena level {} during restore: {}", key, e.getMessage());
			}
		}
	}

	/**
	 * 游戏结束：拆除竞技场维度并删除全部存档。
	 * 必须在所有玩家离开竞技场维度之后调用（performResetForLobby 末尾）。
	 */
	public static void teardownArenaWorlds(MinecraftServer server) {
		removeArenaLevels(server);
		currentSeed = 0;
		SurvivalContract.LOGGER.info("[Arena] Arena worlds torn down and saves deleted.");
	}

	/** 关闭并从 levels 注册表中移除竞技场维度 + 递归删除存档文件夹（Windows 句柄重试） */
	private static void removeArenaLevels(MinecraftServer server) {
		Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).sc$getLevels();
		boolean any = false;
		for (ResourceKey<Level> key : ARENA_KEYS) {
			ServerLevel lvl = levels.get(key);
			if (lvl != null) {
				try {
					lvl.close(); // 关闭区块缓存/区域文件句柄（不落盘保存——反正要删）
				} catch (IOException e) {
					SurvivalContract.LOGGER.warn("[Arena] Failed to close arena level {}: {}", key, e.getMessage());
				}
				levels.remove(key);
				any = true;
			}
		}
		if (any) SurvivalContract.LOGGER.info("[Arena] Existing arena levels closed and removed from registry.");

		// 删除存档文件夹（含上一局崩溃残留）。Windows 下文件句柄释放有延迟 → 重试
		LevelStorageSource.LevelStorageAccess storage = ((MinecraftServerAccessor) server).sc$getStorageSource();
		for (ResourceKey<Level> key : ARENA_KEYS) {
			Path dir = storage.getDimensionPath(key);
			if (!Files.exists(dir)) continue;
			deleteRecursiveWithRetry(dir);
		}
	}

	private static void deleteRecursiveWithRetry(Path dir) {
		for (int attempt = 1; attempt <= 10; attempt++) {
			try (Stream<Path> walk = Files.walk(dir)) {
				walk.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
				SurvivalContract.LOGGER.info("[Arena] Deleted arena save folder: {}", dir);
				return;
			} catch (Exception e) {
				if (attempt == 10) {
					SurvivalContract.LOGGER.error("[Arena] Failed to delete arena save folder {} after 10 attempts", dir, e);
				} else {
					try {
						Thread.sleep(200);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
	}
}
