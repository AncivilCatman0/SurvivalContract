package com.survivalcontract.civilization;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** 文明时代核心系统（详见类尾注释） */
public class CivilizationManager {

	private static final CivilizationManager INSTANCE = new CivilizationManager();

	public static CivilizationManager getInstance() { return INSTANCE; }

	private CivilizationManager() {}

	// ==================== 26.2 API 辅助 ====================
	/** 队伍羊毛方块（专属占领羊毛：5 倍硬度 / 破坏不掉落；放置正常消耗——第五十一批取消返还） */
	public static Block woolBlock(GameStateManager.Team team) {
		return team == GameStateManager.Team.BLUE ? CivilBlocks.CLAIM_WOOL_BLUE : CivilBlocks.CLAIM_WOOL_RED;
	}

	public static boolean isTeamWool(BlockState st, GameStateManager.Team team) {
		return st.getBlock() == woolBlock(team);
	}

	public static boolean isAnyTeamWool(BlockState st) {
		return st.getBlock() == woolBlock(GameStateManager.Team.RED)
				|| st.getBlock() == woolBlock(GameStateManager.Team.BLUE);
	}

	private static ItemStack woolStack(GameStateManager.Team team, int n) {
		return new ItemStack(woolBlock(team).asItem(), n);
	}

	/** 初始羊毛物品（开局发放一组本队颜色羊毛，LobbyManager 调用） */
	public static ItemStack teamWoolStack(GameStateManager.Team team, int n) { return woolStack(team, n); }

	/** 该物品是否为玩家本队颜色的占领羊毛 */
	public static boolean isOwnWoolItem(GameStateManager.Team team, net.minecraft.world.item.Item item) {
		return team != GameStateManager.Team.NONE && item == woolBlock(team).asItem();
	}

	// ==================== 死亡羊毛保留（死亡不掉落，重生返还） ====================
	/** 玩家 → 死亡时暂存的羊毛数量（对应颜色的羊毛死亡必定不掉落） */
	private final Map<UUID, Integer> deathWoolCount = new HashMap<>();

	/**
	 * 死亡掉落拦截（ServerPlayerDropMixin 在玩家死亡掉落路径调用）：本队颜色羊毛不掉落，
	 * 数量暂存，重生时 {@link #onRespawn} 返还。keepInventory 开启时不走掉落路径、不受影响。
	 * 返回是否已拦截（true = 取消该次掉落）。
	 */
	public boolean handleDeathDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || !player.isDeadOrDying()) return false;
		GameStateManager gsm = GameStateManager.getInstance();
		GameStateManager.Team team = gsm.getPlayerTeam(player.getUUID());
		if (!isOwnWoolItem(team, stack.getItem())) return false;
		deathWoolCount.merge(player.getUUID(), stack.getCount(), Integer::sum);
		return true;
	}

	/** 重生返还暂存的羊毛（数量清零；无暂存则无事发生） */
	public void onRespawn(ServerPlayer newPlayer, boolean alive) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (!gsm.isCivilization()) return;
		Integer n = deathWoolCount.remove(newPlayer.getUUID());
		if (n == null || n <= 0) return;
		GameStateManager.Team team = gsm.getPlayerTeam(newPlayer.getUUID());
		if (!isOwnWoolItem(team, woolBlock(team).asItem())) return;
		var wool = woolStack(team, n);
		if (!newPlayer.getInventory().add(wool) && !wool.isEmpty()) {
			com.survivalcontract.McCompat.drop(newPlayer, wool, false);
		}
		newPlayer.sendSystemMessage(Component.translatable("survival-contract.msg.civ_wool_returned", n));
	}

	/** 实体类型（26.2：EntityType 常量字段移除，走注册表） */
	@SuppressWarnings("unchecked")
	private static <T extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.EntityType<T>
	entityType(String name) {
		return (net.minecraft.world.entity.EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(
				Identifier.fromNamespaceAndPath("minecraft", name));
	}

	// ==================== 常量 ====================
	/** 边界半径（区块数）：15×15 区块以中心区块为原点 ±7 */
	public static final int BORDER_CHUNKS = 7;
	public static final long CLAIM_TIME_TICKS = 300L;      // 占领需 15 秒
	public static final int HQ_MAX_HP = 100;
	/** 每区块生成生物上限（本系统生成的生物） */
	public static final int MOB_CAP_PER_ZONE = 12;

	/** 资源区类型（红石区已取消——红石并入青金石区；沙区=第四十八批新增，前置铜箱子） */
	public enum ZoneType {
		HQ, WOOD, STONE, COPPER, IRON, GOLD, DIAMOND, QUARTZ, LAPIS, WILDLIFE, ALLOY, SAND
	}

	/** 区块状态 */
	public static class Zone {
		public GameStateManager.Team owner; // null = 无主
		public ZoneType type;
		public BlockPos woolPos; // 羊毛位置（HQ=开局自动放置；占领后=玩家放置位置）
		public UUID floatStandId; // 悬浮图标盔甲架

		Zone(ZoneType type) { this.type = type; }
	}

	/** 占领中 */
	private record PendingClaim(ChunkPos pos, GameStateManager.Team team,
	                            BlockPos woolPos, long startTick) {}

	private final Map<ChunkPos, Zone> zones = new HashMap<>();
	private final Map<ChunkPos, PendingClaim> pendingClaims = new HashMap<>();
	/** 玩家在区块内放置的功能方块/容器（熔炉/高炉/煤炭块/钻石块/箱子/木桶） */
	private final Map<ChunkPos, Set<BlockPos>> facilities = new HashMap<>();
	private final Map<GameStateManager.Team, Integer> hqHp = new HashMap<>();
	private final Map<UUID, BlockPos> hqStands = new HashMap<>(); // 盔甲架 → 所在大本营羊毛位置
	private final Map<UUID, BlockPos> piglins = new HashMap<>();  // 回收猪灵 → 位置
	private final Map<GameStateManager.Team, SimpleContainer> recycleBoxes = new HashMap<>();
	private MinecraftServer serverRef = null;
	/** 产出配置（每局 setup 时从 config/survival-contract/civilization-production.json 重新读取） */
	private CivilizationProductionConfig prodCfg = CivilizationProductionConfig.get();
	/** 基地商人交易/猪灵回收价配置（config/survival-contract/civilization-trade.json，每局开始重读——第五十一批） */
	private CivTradeConfig tradeCfg = CivTradeConfig.get();
	/** 每区块下次产出 tick（每区块独立计时，间隔由产出配置决定） */
	private final Map<ChunkPos, Long> zoneNextProduce = new HashMap<>();
	/** 每区块独立刷怪计时（tickZoneSpawn；产出间隔与刷怪间隔解耦） */
	private final Map<ChunkPos, Long> zoneNextSpawn = new HashMap<>();
	/** 各队大本营战斗物资下次产出 tick */
	private final Map<GameStateManager.Team, Long> hqNextProduce = new HashMap<>();

	// ==================== 第三十二批新增状态 ====================
	/** 本系统生成的区块生物（禁止跨区块）：实体 → 所属区块 */
	private final Map<UUID, ChunkPos> managedMobs = new HashMap<>();
	/** 文明模式下加载的僵尸马（含守护骑兵坐骑与遭遇事件骑兵）：每 tick 扑灭自身火焰，白天不自燃 */
	private final Set<UUID> civZombieHorses = new HashSet<>();
	/** 占领进度 bossbar：区块 → 进度条 */
	private final Map<ChunkPos, net.minecraft.server.level.ServerBossEvent> claimBars = new HashMap<>();
	/** 羊毛放置校验：右键 1 tick 后验证方块真实放置成功才登记占领（修复"脚下右键假占领"）；
	 *  wool = 玩家所放羊毛方块（原版拒绝放置时服务端兜底放置用） */
	private record WoolCheck(UUID playerId, BlockPos placePos, BlockPos clickedPos,
	                         boolean replaceTarget, net.minecraft.world.level.block.Block wool, long checkTick) {}
	private final List<WoolCheck> woolChecks = new ArrayList<>();
	/** 各队大本营中心区块（移动限制兜底传送点） */
	private final Map<GameStateManager.Team, ChunkPos> hqCenter = new HashMap<>();
	/** 大本营战斗物资产出累积器（每分钟 rate 个，累积取整发放） */
	private final Map<GameStateManager.Team, Double> hqSupplyAcc = new HashMap<>();
	/** 移动限制提示节流：玩家 → 上次提示 tick */
	private final Map<UUID, Long> moveWarnAt = new HashMap<>();
	/** 队伍归属容器（箱子/木桶）：位置 → 放置时所属队伍（敌方反占后仍锁定、不接收产出） */
	private final Map<BlockPos, GameStateManager.Team> containerOwners = new HashMap<>();
	/** 每区块羊毛索引：区块 → 该区块唯一的占领羊毛位置（每区块最多一个红/蓝羊毛） */
	private final Map<ChunkPos, BlockPos> chunkWool = new HashMap<>();
	/** 副产品单区累计产出计数（totalCap 上限用）：区块 → （副产品序号 → 已产出个数） */
	private final Map<ChunkPos, Map<Integer, Integer>> bonusProduced = new HashMap<>();

	// ==================== 羊毛挖掘高亮（第五十批） ====================
	/** 正在被挖掘的羊毛高亮实体：羊毛位置 → 发光 BlockDisplay 实体 UUID */
	private final Map<BlockPos, UUID> woolGlow = new HashMap<>();
	/** 高亮最后活跃 tick（挖掘中持续刷新，超时未续 → 移除高亮） */
	private final Map<BlockPos, Long> woolGlowActive = new HashMap<>();
	/** 高亮超时（tick）：挖掘中断后最多保留这么久（等待下一轮扫描确认） */
	private static final long WOOL_GLOW_TIMEOUT_TICKS = 10;

	// ==================== 大本营受击警报（第五十批） ====================
	/** 每队上次警报 tick（每五秒最多触发一次，按队伍独立计时） */
	private final Map<GameStateManager.Team, Long> hqWarnLastTick = new HashMap<>();
	/** 警报冷却：5 秒 = 100 tick */
	public static final long HQ_WARN_INTERVAL_TICKS = 100;

	// ==================== 大本营核心限伤（第五十二批） ====================
	/** 每个大本营盔甲架上一次实际承受伤害的 tick（每 4s 最多受一次伤害） */
	private final Map<UUID, Long> hqStandLastHit = new HashMap<>();
	/** 盔甲架受击冷却：4 秒 = 80 tick */
	public static final long HQ_STAND_DAMAGE_INTERVAL_TICKS = 80;
	/** 大本营盔甲架生成时引用（自测用：假人环境实体不进查找表，不能按 UUID 从 level 查） */
	private final Map<GameStateManager.Team, ArmorStand> hqStandRefs = new EnumMap<>(GameStateManager.Team.class);

	// ==================== 基地商人（第五十一批） ====================
	/** 商人商品：展示物品（带价格标注）+ 实际兑换产物 + 价格（积分） */
	public record MerchantOffer(ItemStack display, ItemStack product, int cost, String category) {}
	/** 基地商人实体（红蓝大本营各一，NoAI 不移动不消失）：队伍 → 实体 UUID */
	private final Map<GameStateManager.Team, UUID> merchantIds = new EnumMap<>(GameStateManager.Team.class);
	/** 基地商人位置：队伍 → 商人所在格（大本营中心区块地表） */
	private final Map<GameStateManager.Team, BlockPos> merchantPos = new EnumMap<>(GameStateManager.Team.class);
	/** 基地商人实体引用（假人环境查找表外仍可断言/清理） */
	private final Map<GameStateManager.Team, net.minecraft.world.entity.npc.villager.Villager> merchantRefs =
			new EnumMap<>(GameStateManager.Team.class);
	/** 兑换界面容器（9×1，0 号格 = 本队羊毛，1-5 号格 = 商品）：队伍 → 容器 */
	private final Map<GameStateManager.Team, SimpleContainer> merchantGuis = new EnumMap<>(GameStateManager.Team.class);
	/** 本局商品列表（每局开始掷定后固定；红蓝两队同一份）：队伍 → 商品（GUI 1-5 号格） */
	private final Map<GameStateManager.Team, List<MerchantOffer>> merchantOffers = new EnumMap<>(GameStateManager.Team.class);
	/** 羊毛动态价（按队伍计价：初始 80，每买一个 +4，上限 320——值来自 civilization-trade.json） */
	private final Map<GameStateManager.Team, Integer> woolCosts = new EnumMap<>(GameStateManager.Team.class);

	/** 记录箱子/木桶的队伍归属（放置时所属队） */
	public void registerContainer(BlockPos pos, GameStateManager.Team team) {
		if (team == null || team == GameStateManager.Team.NONE) return;
		containerOwners.put(pos.immutable(), team);
	}

	/** 该区块是否已存在占领羊毛（每区块最多一个红/蓝羊毛——已存在时无法再次放置） */
	public boolean chunkHasWool(ChunkPos cp) { return chunkWool.containsKey(cp); }

	/** 登记区块羊毛（放置成功 / 初始占领调用） */
	private void recordChunkWool(ChunkPos cp, BlockPos woolPos) {
		chunkWool.put(cp, woolPos.immutable());
	}

	// ==================== 配置：悬浮图标 ====================
	private static ItemCfg cfg(ZoneType t) { return CFG.get(t); }

	/** 资源区悬浮图标（产出间隔/数量/前置方块等参数已全部移至产出配置文件 civilization-production.json） */
	private record ItemCfg(net.minecraft.world.item.Item icon) {}

	private static final Map<ZoneType, ItemCfg> CFG = new HashMap<>();

	static {
		CFG.put(ZoneType.WOOD, new ItemCfg(Items.OAK_LOG));
		CFG.put(ZoneType.STONE, new ItemCfg(Items.STONE));
		CFG.put(ZoneType.COPPER, new ItemCfg(Items.RAW_COPPER));
		CFG.put(ZoneType.IRON, new ItemCfg(Items.RAW_IRON));
		CFG.put(ZoneType.GOLD, new ItemCfg(Items.RAW_GOLD));
		CFG.put(ZoneType.DIAMOND, new ItemCfg(Items.DIAMOND));
		CFG.put(ZoneType.QUARTZ, new ItemCfg(Items.QUARTZ));
		CFG.put(ZoneType.LAPIS, new ItemCfg(Items.LAPIS_LAZULI));
		CFG.put(ZoneType.WILDLIFE, new ItemCfg(Items.LEATHER));
		CFG.put(ZoneType.ALLOY, new ItemCfg(Items.NETHERITE_SCRAP));
		CFG.put(ZoneType.SAND, new ItemCfg(Items.SAND));
	}

	/** 回收价格表（config/survival-contract/civilization-trade.json recyclePrices——第五十一批由硬编码迁入配置）：
	 *  物品 → 每个积分（0.5 用 ×2 半分计；未列入的物品 0 分不回收） */
	private final Map<net.minecraft.world.item.Item, Double> recyclePrices = new HashMap<>();

	private double priceOf(net.minecraft.world.item.Item item) {
		return recyclePrices.getOrDefault(item, 0.0);
	}

	/** 解析交易配置的猪灵回收价目（物品 id 支持省略 minecraft: 前缀；无效 id 告警后忽略） */
	private void loadRecyclePrices(CivTradeConfig cfg) {
		recyclePrices.clear();
		if (cfg.recyclePrices == null) return;
		for (var e : cfg.recyclePrices.entrySet()) {
			net.minecraft.world.item.Item it = CivTradeConfig.item(e.getKey(), null);
			if (it == null) {
				SurvivalContract.LOGGER.warn("[Civilization] 回收价目中的物品 id 无效: {}（忽略）", e.getKey());
				continue;
			}
			double p = e.getValue() == null ? 0 : e.getValue();
			if (p > 0) recyclePrices.put(it, p);
		}
	}

	// ==================== 开局初始化 ====================
	/** 区块边界 dust 粒子（无主=白 / 红=红队 / 蓝=蓝队） */
	private static final net.minecraft.core.particles.DustParticleOptions DUST_WHITE =
			new net.minecraft.core.particles.DustParticleOptions(0xF0F0F0, 1.0f);
	private static final net.minecraft.core.particles.DustParticleOptions DUST_RED =
			new net.minecraft.core.particles.DustParticleOptions(0xFF3030, 1.0f);
	private static final net.minecraft.core.particles.DustParticleOptions DUST_BLUE =
			new net.minecraft.core.particles.DustParticleOptions(0x3A6CFF, 1.0f);

	/** 空白区配额表（第五十五批，图1 修订）：沙 8 / 铜 34 / 铁 24 / 金 20——精确数量放置。
	 *  剩余空白（136 − 86 = 50 格）按图2 权重随机（石英24/青金石16/野生动物16）；
	 *  木头/石头/钻石/合金仍由图1 布局表固定位置，不在空白填充范围。 */
	private static final Map<ZoneType, Integer> ZONE_QUOTAS = buildZoneQuotas();

	private static Map<ZoneType, Integer> buildZoneQuotas() {
		Map<ZoneType, Integer> q = new EnumMap<>(ZoneType.class);
		q.put(ZoneType.SAND, 8);
		q.put(ZoneType.COPPER, 34);
		q.put(ZoneType.IRON, 24);
		q.put(ZoneType.GOLD, 20);
		return java.util.Collections.unmodifiableMap(q);
	}

	/** 剩余空白随机池（图2 权重）：石英24 / 青金石16 / 野生动物16 */
	private static final List<ZoneType> ZONE_FREIGHT_POOL = buildFreightPool();

	private static List<ZoneType> buildFreightPool() {
		Map<ZoneType, Integer> weights = new LinkedHashMap<>();
		weights.put(ZoneType.QUARTZ, 24);
		weights.put(ZoneType.LAPIS, 16);
		weights.put(ZoneType.WILDLIFE, 16);
		List<ZoneType> pool = new ArrayList<>();
		weights.forEach((t, w) -> {
			for (int i = 0; i < w; i++) pool.add(t);
		});
		return List.copyOf(pool);
	}

	/** 图1 固定布局（第五十三批）：15×15，下标 [row][col] = [z 偏移+7][x 偏移+7]；null = 空白 → 随机池填充 */
	private static final ZoneType[][] FIXED_LAYOUT = buildFixedLayout();

	private static ZoneType[][] buildFixedLayout() {
		ZoneType[][] g = new ZoneType[15][15];
		// 家 = 大本营：(1,1) 红队 / (13,13) 蓝队
		g[1][1] = ZoneType.HQ;
		g[13][13] = ZoneType.HQ;
		// 木头区：两家各 3×3（大本营除外）
		for (int r = 0; r <= 2; r++) for (int c = 0; c <= 2; c++) if (g[r][c] == null) g[r][c] = ZoneType.WOOD;
		for (int r = 12; r <= 14; r++) for (int c = 12; c <= 14; c++) if (g[r][c] == null) g[r][c] = ZoneType.WOOD;
		// 石头区：左上 5×5 减木头 3×3（rows0-1 cols3-4 + rows2-4 cols0-4，木头已占的不覆盖）
		for (int r = 0; r <= 1; r++) for (int c = 3; c <= 4; c++) g[r][c] = ZoneType.STONE;
		for (int r = 2; r <= 4; r++) for (int c = 0; c <= 4; c++) if (g[r][c] == null) g[r][c] = ZoneType.STONE;
		// 石头区：中心 5×5（钻石/合金格随后覆盖）
		for (int r = 5; r <= 9; r++) for (int c = 5; c <= 9; c++) g[r][c] = ZoneType.STONE;
		// 石头区：右下 5×5 减木头 3×3
		for (int r = 10; r <= 11; r++) for (int c = 10; c <= 14; c++) g[r][c] = ZoneType.STONE;
		for (int r = 12; r <= 14; r++) for (int c = 10; c <= 11; c++) g[r][c] = ZoneType.STONE;
		// 石头区：两条斜线（右中、左下）
		g[2][12] = ZoneType.STONE;
		g[3][11] = ZoneType.STONE;
		g[4][10] = ZoneType.STONE;
		g[10][4] = ZoneType.STONE;
		g[11][3] = ZoneType.STONE;
		g[12][2] = ZoneType.STONE;
		// 钻石区（图1 共 14 格）
		int[][] diamonds = {{0, 13}, {1, 13}, {1, 14}, {5, 5}, {5, 6}, {6, 5}, {6, 6},
				{8, 8}, {8, 9}, {9, 8}, {9, 9}, {13, 0}, {13, 1}, {14, 1}};
		for (int[] p : diamonds) g[p[0]][p[1]] = ZoneType.DIAMOND;
		// 下界合金区（图1 共 3 格）
		g[0][14] = ZoneType.ALLOY;
		g[7][7] = ZoneType.ALLOY;
		g[14][0] = ZoneType.ALLOY;
		return g;
	}

	/**
	 * 随机抽一种剩余空白资源区类型（图2 权重池：石英/青金石/野生动物）：
	 * 数量上限（产出配置 zoneLimits）已达上限的类型重抽——重抽 32 次仍全部受限时兜底野生动物区。
	 * （第五十五批起沙/铜/铁/金由配额表精确放置，不再走本方法。）
	 */
	private ZoneType pickZoneType(Random rnd, int cx, int cz,
	                              Map<ZoneType, Integer> limits, Map<ZoneType, Integer> counts) {
		for (int tries = 0; tries < 32; tries++) {
			ZoneType t = ZONE_FREIGHT_POOL.get(rnd.nextInt(ZONE_FREIGHT_POOL.size()));
			Integer lim = limits.get(t);
			if (lim == null || lim <= 0 || counts.getOrDefault(t, 0) < lim) return t;
		}
		return ZoneType.WILDLIFE;
	}

	public void setup(MinecraftServer server) {
		reset();
		serverRef = server;
		// 每局开始重新读取产出配置（用户修改配置文件后下一局生效）
		prodCfg = CivilizationProductionConfig.reload();
		// 每局开始重新读取交易配置（基地商人货物/羊毛动态价/猪灵回收价目——修改后下一局生效）
		tradeCfg = CivTradeConfig.reload();
		loadRecyclePrices(tradeCfg);
		GameStateManager gsm = GameStateManager.getInstance();
		ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (level == null) {
			// 绝不允许退回原版主世界：否则会给大厅世界套上 240×240 边界、锁掉昼夜/生物破坏，
			// 并把大本营/商人/守护怪刷在主世界（玩家"在边界外面/掉进其他世界"的根因之一）
			SurvivalContract.LOGGER.error("[Civilization] Setup aborted: arena overworld missing "
					+ "(refusing to build the civilization domain in the vanilla overworld).");
			return;
		}

		ChunkPos center = ChunkPos.containing(gsm.getPlayingSpawn());

		// 大本营区块（红西北 / 蓝东南：3×3 各自贴住疆域对角——第四十八批由南北两侧中间改为对角）
		List<ChunkPos> hqRed = new ArrayList<>();
		List<ChunkPos> hqBlue = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				hqRed.add(new ChunkPos(center.x() - BORDER_CHUNKS + 1 + dx,
						center.z() - BORDER_CHUNKS + 1 + dz));  // 红队西北角 x,z ∈ [-7,-5]
				hqBlue.add(new ChunkPos(center.x() + BORDER_CHUNKS - 1 + dx,
						center.z() + BORDER_CHUNKS - 1 + dz)); // 蓝队东南角 x,z ∈ [5,7]
			}
		}
		hqCenter.put(GameStateManager.Team.RED, hqRed.get(4));
		hqCenter.put(GameStateManager.Team.BLUE, hqBlue.get(4));

		// 资源区类型分配（第五十三批）：图1 固定布局（木/石/钻/合金/家）。
		// 空白区两阶段填充（第五十五批）：先按配额表精确放置（沙8/铜34/铁24/金20，zoneLimits 可压缩），
		// 剩余空白按图2 权重随机（石英24/青金石16/野生动物16，仍受 zoneLimits 上限约束）。
		// 疆域网格以 spawn 区块为原点：cx/cz ∈ [-7, 7]（15×15）；布局表下标 [row=z+7][col=x+7]
		ChunkPos redHqC = hqRed.get(4), blueHqC = hqBlue.get(4);
		Map<ZoneType, Integer> zoneLimits = prodCfg.zoneLimitMap();
		Map<ZoneType, Integer> typeCounts = new EnumMap<>(ZoneType.class);
		Random rnd = new Random();
		List<ChunkPos> blanks = new ArrayList<>();
		for (int cx = -BORDER_CHUNKS; cx <= BORDER_CHUNKS; cx++) {
			for (int cz = -BORDER_CHUNKS; cz <= BORDER_CHUNKS; cz++) {
				ChunkPos cp = new ChunkPos(center.x() + cx, center.z() + cz);
				int row = cz + BORDER_CHUNKS, col = cx + BORDER_CHUNKS;
				ZoneType t = FIXED_LAYOUT[row][col];
				if (cp.equals(redHqC) || cp.equals(blueHqC)) {
					t = ZoneType.HQ; // 大本营中心（与布局表 (1,1)/(13,13) 一致，兜底防御）
				} else if (t == null) {
					blanks.add(cp); // 空白区 → 两阶段填充
					continue;
				}
				zones.put(cp, new Zone(t));
				typeCounts.merge(t, 1, Integer::sum);
			}
		}
		// 阶段1：配额类型精确数量（洗牌空白位 + 洗牌配额序列，保证恰好 8/34/24/20）
		java.util.Collections.shuffle(blanks, rnd);
		List<ZoneType> quotaQueue = new ArrayList<>();
		ZONE_QUOTAS.forEach((t, quota) -> {
			Integer lim = zoneLimits.get(t);
			int n = (lim == null || lim <= 0) ? quota : Math.min(quota, lim);
			for (int i = 0; i < n; i++) quotaQueue.add(t);
		});
		java.util.Collections.shuffle(quotaQueue, rnd);
		int qi = 0;
		for (; qi < quotaQueue.size() && qi < blanks.size(); qi++) {
			zones.put(blanks.get(qi), new Zone(quotaQueue.get(qi)));
			typeCounts.merge(quotaQueue.get(qi), 1, Integer::sum);
		}
		// 阶段2：剩余空白（含配额被上限压缩的余量）按权重随机
		for (; qi < blanks.size(); qi++) {
			ChunkPos cp = blanks.get(qi);
			ZoneType t = pickZoneType(rnd, cp.x(), cp.z(), zoneLimits, typeCounts);
			zones.put(cp, new Zone(t));
			typeCounts.merge(t, 1, Integer::sum);
		}

		// 初始占领（大本营 3×3：中心羊毛基座 + 悬浮图标 + 盔甲架 + 回收猪灵；周围八区块 = 木头资源区无羊毛）+ respawn 覆盖
		initClaim(level, gsm, hqRed, GameStateManager.Team.RED, center);
		initClaim(level, gsm, hqBlue, GameStateManager.Team.BLUE, center);

		// 守护怪物（第五十三批）：钻石区 = 僵尸马骑兵、合金区 = 骆驼尸壳骑兵——击败后才能占领
		spawnZoneGuardians(level);

		// 基地商人 ×2（红蓝大本营各一：NoAI 不移动不消失 + 永久抗性提升 5，右键积分兑换——第五十一批，
		// 取代第四十九批的中央文明商人）
		spawnBaseMerchants(level);

		// WorldBorder：15×15 区块 = 240×240 方块，边界严格贴合疆域区块边界
		double cxW = center.getMinBlockX(), czW = center.getMinBlockZ();
		var border = level.getWorldBorder();
		// 边界严格按区块边界：疆域为 center±7 共 15 区块（方块范围 [minX-112, minX+128)），
		// 边界中心必须取疆域几何中心（中心区块中央 +8）——此前设在区块西北角，
		// 导致北/西多出半个区块、南/东少了半个区块
		border.setCenter(cxW + 8, czW + 8);
		border.setSize(15 * 16);

		// 环境规则：火蔓延关闭 + 苦力怕等生物不破坏方块（也防末影人搬羊毛）。
		// 26.2 已移除 doFireTick（改为玩家半径限制）、规则 id 改为 snake_case——
		// 字符串指令 "gamerule doFireTick/doMobGriefing" 全部无效，必须走 API 设置
		savedMobGriefing = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
		savedFireSpread = level.getGameRules().get(
				net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING, false, server);
		level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,
				0, server);

		// 永昼（第六十九批）：关闭昼夜推进（26.2 的 doDaylightCycle = advance_time 规则）+ 时间设为白天
		savedAdvanceTime = level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME);
		level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
		try {
			var src = server.createCommandSourceStack()
					.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS)
					.withLevel(level);
			server.getCommands().performPrefixedCommand(src, "time set day");
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 永昼时间设置失败（不影响规则锁定）", e);
		}

		SurvivalContract.LOGGER.info("[Civilization] Setup: {} zones (15x15), border {} at ({},{})",
				zones.size(), 15 * 16, (int) cxW, (int) czW);
	}

	/** 初始占领一队大本营 3×3：羊毛 + 悬浮图标 + 盔甲架 + 回收猪灵 + respawn 覆盖 */
	private void initClaim(ServerLevel level, GameStateManager gsm, List<ChunkPos> chunks,
	                       GameStateManager.Team team, ChunkPos center) {
		// respawn → 大本营中心区块地表
		ChunkPos hq = chunks.get(4); // 3×3 中心
		int sx = hq.getMinBlockX() + 8, sz = hq.getMinBlockZ() + 8;
		// 大本营在疆域边缘（距 spawn 数百格）：区块可能未生成 → heightmap 返回虚空值（-63）
		// 必须先强制生成区块再取地表高度
		level.getChunk(hq.x(), hq.z());
		int sy = surfaceY(level, sx, sz);
		BlockPos spawnPos = new BlockPos(sx, sy, sz);
		for (UUID id : gsm.getTeamMembers(team)) {
			gsm.setRespawnPoint(id, spawnPos);
		}
		for (ChunkPos cp : chunks) {
			Zone z = zones.get(cp);
			z.owner = team;
			int wx = cp.getMinBlockX() + 8, wz = cp.getMinBlockZ() + 8;
			int wy = surfaceY(level, wx, wz);
			BlockPos woolPos = new BlockPos(wx, wy, wz);
			if (cp.equals(hq)) {
				// 大本营中心：盔甲架下方为队伍色染色玻璃（玻璃下方为激活信标）——不放占领羊毛
				Block glass = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft",
						team == GameStateManager.Team.RED ? "red_stained_glass" : "blue_stained_glass"));
				level.setBlock(woolPos, glass.defaultBlockState(), 3);
				beaconProtected.add(woolPos.immutable()); // 玻璃基座不可破坏
				z.woolPos = woolPos;
				spawnFloatIcon(level, z);
			} else {
				// 大本营周围八区块 = 初始资源区（第四十九批）：照常放置队伍占领羊毛（可被破坏/抢夺），
				// 仅悬浮图标不再显示羊毛——spawnFloatIcon 对非 HQ 区块显示资源配置图标（原木/石头）
				level.setBlock(woolPos, woolBlock(team).defaultBlockState(), 3);
				recordChunkWool(cp, woolPos);
				z.woolPos = woolPos;
				spawnFloatIcon(level, z);
			}
			if (cp.equals(hq)) {
				// 大本营：盔甲架（核心）+ 回收猪灵 + 信标光柱（玻璃正下方信标 + 队伍色玻璃金字塔）
				setupHqBeam(level, team, woolPos);
				// 物资桶：猪灵脚下（猪灵站在木桶上）
				BlockPos barrelPos = new BlockPos(wx + 2, wy, wz);
				level.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), 3);
				beaconProtected.add(barrelPos.immutable()); // 不可破坏
				supplyBarrels.put(team, barrelPos.immutable());
				barrelNextProduce.put(team, (serverRef != null ? serverRef.getTickCount() : 0L)
						+ barrelIntervalTicks(team));
				ArmorStand stand = new ArmorStand(entityType("armor_stand"), level);
				stand.absSnapTo(wx + 0.5, wy + 1.0, wz + 0.5, 0, 0);
				stand.setNoGravity(true);
				com.survivalcontract.McCompat.setInvulnerable(stand, true);
				stand.addTag("civil_hq_" + (team == GameStateManager.Team.RED ? "RED" : "BLUE"));
				level.addFreshEntity(stand);
				hqStands.put(stand.getUUID(), woolPos);
				hqStandRefs.put(team, stand);
				hqHp.put(team, HQ_MAX_HP);
				stand.setItemSlot(EquipmentSlot.HEAD, woolStack(team, 1));

				Piglin pig = new Piglin(entityType("piglin"), level);
				pig.absSnapTo(wx + 2.5, wy + 1.0, wz + 0.5, 90, 0);
				pig.setNoAi(true);
				pig.setPersistenceRequired();
				// 永久抗性提升 5（amplifier=4）：防止猪灵被击杀（大本营回收功能核心）
				pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.RESISTANCE,
						net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 4, true, false));
				pig.addTag("civil_recycle_" + (team == GameStateManager.Team.RED ? "RED" : "BLUE"));
				level.addFreshEntity(pig);
				piglins.put(pig.getUUID(), woolPos);
				recycleBoxes.put(team, newRecycleBox(team));
			}
		}
	}

	/** 地表高度（强制生成所在区块——未生成区块的 heightmap 会返回虚空值导致卡 -63） */
	private static int surfaceY(ServerLevel level, int x, int z) {
		ChunkPos cp = ChunkPos.containing(new BlockPos(x, 0, z));
		level.getChunk(cp.x(), cp.z());
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		// 兜底：高度异常（虚空/过深）时向上探到真实地表
		int minY = level.getMinY();
		if (y < minY + 1) y = minY + 1;
		// 高度图与实际方块可能不一致（陈旧高度图——历史拆除/测试残留导致）：若脚下是空气，
		// 向下探到真实地表（首个非空气方块之上），避免商人/野兽悬空；整列无实体方块（虚空列）时保持原值
		if (level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
			for (int yy = y - 2; yy >= minY; yy--) {
				if (!level.getBlockState(new BlockPos(x, yy, z)).isAir()) {
					return yy + 1;
				}
			}
		}
		return y;
	}

	/** 悬浮资源图标：**掉落物样式**的 ItemDisplay 渲染实体（GROUND 变换 = 掉落物旋转漂浮样式；
	 *  Display 实体无物理——不会被水/气泡柱/推力移走，稳定停在羊毛上方中央） */
	private void spawnFloatIcon(ServerLevel level, Zone z) {
		if (z.woolPos == null) return;
		ItemCfg c = cfg(z.type);
		net.minecraft.world.item.Item icon = z.type == ZoneType.HQ
				? woolBlock(z.owner).asItem()
				: c.icon();
		var display = new net.minecraft.world.entity.Display.ItemDisplay(
				entityType("item_display"), level);
		display.absSnapTo(z.woolPos.getX() + 0.5, z.woolPos.getY() + 1.1, z.woolPos.getZ() + 0.5, 0, 0);
		display.setNoGravity(true);
		com.survivalcontract.McCompat.setInvulnerable(display, true);
		setDisplayItem(display, new ItemStack(icon));
		level.addFreshEntity(display);
		z.floatStandId = display.getUUID();
	}

	/** ItemDisplay 物品与掉落物样式变换（26.2 无公开 setter → 反射 entity data；失败仅失去旋转样式不影响悬浮） */
	@SuppressWarnings("unchecked")
	private static void setDisplayItem(net.minecraft.world.entity.Display.ItemDisplay display, ItemStack stack) {
		try {
			var cls = net.minecraft.world.entity.Display.ItemDisplay.class;
			var fItem = cls.getDeclaredField("DATA_ITEM_STACK_ID");
			fItem.setAccessible(true);
			var accItem = (net.minecraft.network.syncher.EntityDataAccessor<ItemStack>) fItem.get(null);
			display.getEntityData().set(accItem, stack);
			var fTr = cls.getDeclaredField("DATA_ITEM_DISPLAY_ID");
			fTr.setAccessible(true);
			var accTr = (net.minecraft.network.syncher.EntityDataAccessor<Byte>) fTr.get(null);
			byte ground = (byte) net.minecraft.world.item.ItemDisplayContext.GROUND.ordinal();
			display.getEntityData().set(accTr, ground);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] ItemDisplay config failed: {}", e.toString());
		}
	}

	private void removeFloatIcon(ServerLevel level, Zone z) {
		if (z.floatStandId == null) return;
		var e = level.getEntity(z.floatStandId);
		if (e != null) e.discard();
		z.floatStandId = null;
	}

	// ==================== 每 tick ====================
	public void tick(MinecraftServer server) {
		GameStateManager gsm = GameStateManager.getInstance();
		boolean civPlaying = gsm.isCivilization() && gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING;
		// ③ 己方基地区块生命回复（第五十二批）：非对局阶段也要清理残留效果
		tickHqRegeneration(server, gsm, civPlaying);
		if (!civPlaying) return;
		serverRef = server;
		ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (level == null) {
			// 竞技场缺失：本 tick 不做事（不得把判定/清理作用到原版主世界）
			if (server.getTickCount() % 200 == 0) {
				SurvivalContract.LOGGER.warn("[Civilization] tick skipped: arena overworld missing");
			}
			return;
		}
		long now = server.getTickCount();

		// 守护怪火焰抑制（第六十批）：每 tick 扑灭守护怪身上的火（僵尸马坐骑白天不自燃）
		guardianFireSweep(level);
		// 生物禁跨区块：每 tick 拉回越界的守护/自产怪（本次改为每 tick，避免快速怪物在两次每秒检查间冲出区块）
		containMobs(level);

		// 羊毛放置校验（右键下一 tick 验证真实放置成功才登记占领——修复脚下右键假占领）
		if (!woolChecks.isEmpty()) processWoolChecks(server, level, now);

		// 每秒：占领计时 + bossbar 进度 + 羊毛完整性 + 生物禁跨区块 + 玩家移动限制 + 物资桶产出
		if (now % 20 == 0) {
			// 大本营物资桶：末影珍珠 + 风弹，随占领缩减刷新间隔
			for (var se : supplyBarrels.entrySet()) {
				long next = barrelNextProduce.getOrDefault(se.getKey(), 0L);
				if (now < next) continue;
				barrelNextProduce.put(se.getKey(), now + barrelIntervalTicks(se.getKey()));
				produceBarrel(level, se.getKey(), se.getValue());
			}
			// 资源产出：每区块独立计时（间隔/数量/概率/刷怪由产出配置文件决定）
			produceZones(level, now);
			Iterator<Map.Entry<ChunkPos, PendingClaim>> it = pendingClaims.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				PendingClaim pc = e.getValue();
				// 占领羊毛被破坏（爆炸等）→ 取消占领（并清除该区块羊毛索引，否则该区块永久无法再放置）
				if (!isTeamWool(level.getBlockState(pc.woolPos()), pc.team())) {
					it.remove();
					chunkWool.remove(e.getKey());
					removeClaimBar(e.getKey());
					broadcast(server, Component.translatable("survival-contract.msg.civ_wool_destroyed", e.getKey().x(), e.getKey().z()));
					continue;
				}
				if (now - pc.startTick() >= CLAIM_TIME_TICKS) {
					it.remove();
					removeClaimBar(e.getKey());
					completeClaim(server, level, pc);
				} else {
					updateClaimBar(e.getKey(), pc, now);
				}
			}
			checkWoolIntegrity(server, level);
			enforceTerritoryLimit(server, level, gsm);
		}

		// 每 2 tick：羊毛挖掘高亮（正在被挖的占领羊毛保持发光，第五十批）
		if (now % 2 == 0) updateWoolGlow(level, now);

		// 每 0.5 秒：区块边界颜色粒子
		if (now % 10 == 0) {
			sendBorderParticles(level);
		}

		// 每 5 秒：悬浮图标自愈（修复"部分区块占领后不显示悬浮物"）
		if (now % 100 == 0) {
			for (var e : zones.entrySet()) {
				Zone z = e.getValue();
				if (z.owner == null || z.woolPos == null) continue;
				boolean ok = z.floatStandId != null && level.getEntity(z.floatStandId) != null;
				if (!ok) {
					removeFloatIcon(level, z);
					spawnFloatIcon(level, z);
					SurvivalContract.LOGGER.info("[Civilization] Float icon respawned at ({},{}) ({})",
							e.getKey().x(), e.getKey().z(), z.type);
				}
			}
		}

		// 悬浮图标动画：自转 + 正弦上下浮动（每 tick 更新，Display 插值平滑同步）
		for (Zone z : zones.values()) {
			if (z.floatStandId == null || z.woolPos == null) continue;
			var e2 = level.getEntity(z.floatStandId);
			if (e2 instanceof net.minecraft.world.entity.Display.ItemDisplay d) {
				float baseY = z.woolPos.getY() + 1.1f;
				float bob = (float) Math.sin(now / 14.0) * 0.12f;
				d.setPos(z.woolPos.getX() + 0.5, baseY + bob, z.woolPos.getZ() + 0.5);
				d.setYRot((now * 2.0f) % 360.0f); // 每 tick 转 2°，约 3 秒一圈
			}
		}
	}

	/**
	 * ③ 己方基地区块生命回复（第五十二批）：
	 * 我方玩家处于己方 HQ 区块时获得无限时间生命回复 1，离开区块后消失。
	 * active=false（非文明对局）时也要清理残留的无限回血效果。
	 */
	private void tickHqRegeneration(MinecraftServer server, GameStateManager gsm, boolean active) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean inHq = false;
			if (active) {
				Zone z = zoneAt(ChunkPos.containing(p.blockPosition()));
				inHq = z != null && z.type == ZoneType.HQ
						&& z.owner != null && z.owner == gsm.getPlayerTeam(p.getUUID());
			}
			var cur = p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
			if (inHq) {
				if (cur == null || cur.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION
						|| cur.getAmplifier() != 0) {
					// -1 = 原版无限时长（INFINITE_DURATION）；ambient=false, 可见粒子=false, 显示图标=true
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
							net.minecraft.world.effect.MobEffects.REGENERATION,
							net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
				}
			} else if (cur != null
					&& cur.getDuration() == net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION
					&& cur.getAmplifier() == 0) {
				// 仅移除我们发放的无限 amp0 回血（药水等有限时长回血不受影响）
				p.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
			}
		}
	}

	/**
	 * ① 文明模式自然刷怪双闸（第五十二批 1/4 → 第六十五批再减半）：
	 * 返回 true 表示本次允许尝试自然刷怪（由 NaturalSpawnerMixin 调用）。
	 * <ul>
	 *   <li>概率闸：每次 spawnForChunk 有 7/8 概率被整体跳过（期望产出 = 原版的 1/8）</li>
	 *   <li>总量闸：世界敌对生物总量达到上限（原版怪物刷怪容量的一半）后拒绝自然刷怪</li>
	 * </ul>
	 */
	public static boolean naturalSpawnAllowed(ServerLevel level) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return true;
		return spawnChanceAllowed(level) && countWorldMonsters(level) < worldMonsterCap(level);
	}

	/** 概率闸：1/8 放行（原 1/4，第六十五批砍半） */
	public static boolean spawnChanceAllowed(ServerLevel level) {
		return level.getRandom().nextFloat() < 0.125f;
	}

	/** 文明模式世界怪物总量上限：原版怪物刷怪容量（70 × 已加载区块 / 289）的一半（第六十五批） */
	public static int worldMonsterCap(ServerLevel level) {
		int chunks = level.getChunkSource().getLoadedChunksCount();
		return Math.max(1, (int) (70L * chunks / 289L) / 2);
	}

	// 世界怪物总量缓存：spawnForChunk 每 tick 每区块调用，不能现场遍历——按维度每 100 tick（5 秒）重计一次
	private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> monsterCountDim = null;
	private static long monsterCountAt = Long.MIN_VALUE;
	private static int monsterCountCache = 0;

	/** 当前世界敌对生物总量（Enemy 实例，含幽灵/恶魂/史莱姆等，与 MainEventListener 敌对判定一致） */
	public static int countWorldMonsters(ServerLevel level) {
		long now = level.getGameTime();
		if (monsterCountDim == null || !level.dimension().equals(monsterCountDim)
				|| now - monsterCountAt >= 100L) {
			monsterCountDim = level.dimension();
			monsterCountAt = now;
			int n = 0;
			for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
				if (e instanceof net.minecraft.world.entity.monster.Enemy) n++;
			}
			monsterCountCache = n;
		}
		return monsterCountCache;
	}

	/** 大本营信标光柱保护方块（信标 + 隐藏玻璃金字塔 + 物资桶）：不可破坏/不可交互 */
	private final Set<BlockPos> beaconProtected = new HashSet<>();

	// ==================== 大本营物资桶（猪灵脚下） ====================
	/** 进入文明时代前保存的环境 gamerule（结束时恢复默认，避免影响其他模式） */
	private Boolean savedMobGriefing;
	private Integer savedFireSpread;
	/** 进入文明时代前保存的昼夜推进开关（永昼用——第六十九批） */
	private Boolean savedAdvanceTime;
	/** 队伍 → 物资桶位置 */
	private final Map<GameStateManager.Team, BlockPos> supplyBarrels = new HashMap<>();
	/** 队伍 → 下次产出 tick */
	private final Map<GameStateManager.Team, Long> barrelNextProduce = new HashMap<>();

	/** 木桶刷新间隔：初始 intervalSeconds 起步，每多占 1 区块 -reducePerClaimSeconds 秒，最低 minSeconds（产出配置） */
	private long barrelIntervalTicks(GameStateManager.Team team) {
		long claims = zones.values().stream().filter(zz -> zz.owner == team).count();
		CivilizationProductionConfig.SupplyBarrelCfg sb = prodCfg.supplyBarrel;
		long base = Math.max(0, sb.intervalSeconds) * 20L;
		long reduce = Math.max(0, claims - 9) * Math.max(0, sb.reducePerClaimSeconds) * 20L;
		return Math.max(Math.max(0, sb.minSeconds) * 20L, base - reduce);
	}

	/** 自测：木桶当前刷新间隔（秒） */
	public long barrelIntervalSecondsForTest(GameStateManager.Team team) {
		return barrelIntervalTicks(team) / 20;
	}

	/** 木桶产出：末影珍珠 + 风弹（数量/稀有概率/稀有物池由产出配置决定；稀有物资池随机取一） */
	private void produceBarrel(ServerLevel level, GameStateManager.Team team, BlockPos pos) {
		if (!(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)) return;
		if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container c)) return;
		CivilizationProductionConfig.SupplyBarrelCfg sb = prodCfg.supplyBarrel;
		if (sb.pearlCount > 0) inject(c, new ItemStack(Items.ENDER_PEARL, sb.pearlCount));
		if (sb.windChargeCount > 0) inject(c, new ItemStack(Items.WIND_CHARGE, sb.windChargeCount));
		Random rnd = new Random();
		var rarePool = prodCfg.supplyBarrelRareItems();
		if (sb.rareChancePercent > 0 && !rarePool.isEmpty() && rnd.nextInt(100) < sb.rareChancePercent) {
			ItemStack rare = new ItemStack(rarePool.get(rnd.nextInt(rarePool.size())), 1);
			inject(c, rare);
			String itemName = rare.getHoverName().getString();
			for (UUID id : GameStateManager.getInstance().getTeamMembers(team)) {
				ServerPlayer p = serverRef != null ? serverRef.getPlayerList().getPlayer(id) : null;
				if (p != null) p.sendSystemMessage(Component.translatable(
						"survival-contract.msg.civ_rare_produced", itemName));
			}
		}
	}

	/**
	 * 大本营信标光柱：盔甲架下方结构为 染色玻璃（队伍色）→ 信标 → 3×3 铁块（地下）。
	 * 信标激活基座仅限铁/金/绿宝石/钻石/下界合金块（染色玻璃无效——此前光柱不出现的根因），
	 * 队伍颜色光柱由信标正上方的染色玻璃基座对光束染色实现。
	 * 铁块、信标、染色玻璃全部列入保护（不可破坏）；信标不可右键交互（UseBlock 拦截）。
	 */
	private void setupHqBeam(ServerLevel level, GameStateManager.Team team, BlockPos woolPos) {
		BlockPos beaconPos = woolPos.below();
		// 3×3 铁块基座（信标正下方，激活光柱）
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p1 = beaconPos.below().offset(dx, 0, dz);
				level.setBlock(p1, Blocks.IRON_BLOCK.defaultBlockState(), 3);
				beaconProtected.add(p1.immutable());
			}
		}
		level.setBlock(beaconPos, Blocks.BEACON.defaultBlockState(), 3);
		beaconProtected.add(beaconPos.immutable());
		beaconProtected.add(woolPos.immutable()); // 染色玻璃基座（initClaim 已加，幂等）
	}

	/** 自测：受保护大本营设施数量（每队 12：1 染色玻璃基座 + 1 信标 + 3×3 铁块 + 1 物资桶） */
	public int beaconProtectedCount() { return beaconProtected.size(); }

	private void broadcast(MinecraftServer server, Component msg) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(msg);
		}
	}

	/** 右键放置羊毛 → 下一 tick 验证方块真实存在才登记占领。
	 *  placePos = 点击面相邻格（普通放置）；clickedPos = 被点击格（点击短草/花草等可替换方块时
	 *  原版会把方块放进该格本身——超平坦草地遍布短草，只查 placePos 会导致校验位置与真实
	 *  放置位置不一致而静默失败）；replaceTarget = 原版本会替换被点击方块（与原版同用
	 *  canBeReplaced(放置上下文) 判定）；wool = 羊毛方块（原版拒绝放置时服务端兜底放置用）。 */
	public void queueWoolCheck(ServerPlayer player, BlockPos placePos, BlockPos clickedPos, boolean replaceTarget,
			net.minecraft.world.level.block.Block wool) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
		if (gsm.getPlayerTeam(player.getUUID()) == GameStateManager.Team.NONE) return;
		woolChecks.add(new WoolCheck(player.getUUID(), placePos.immutable(), clickedPos.immutable(),
				replaceTarget, wool, player.level().getServer().getTickCount() + 1));
	}

	/** 立即处理羊毛校验队列（自测用；正常运行由 tick 驱动） */
	public void processWoolChecksForTest(MinecraftServer server, ServerLevel level, long now) {
		processWoolChecks(server, level, now);
	}

	private void processWoolChecks(MinecraftServer server, ServerLevel level, long now) {
		GameStateManager gsm = GameStateManager.getInstance();
		Iterator<WoolCheck> it = woolChecks.iterator();
		while (it.hasNext()) {
			WoolCheck wc = it.next();
			if (now < wc.checkTick()) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(wc.playerId());
			if (p == null) continue;
			// 原版实际落点：可替换目标 → 方块放进被点击格本身；否则放在点击面相邻格。
			// 两处都查（优先原版落点）双保险，避免校验位置与真实位置不一致静默失败。
			BlockPos primary = wc.replaceTarget() ? wc.clickedPos() : wc.placePos();
			BlockPos secondary = wc.replaceTarget() ? wc.placePos() : wc.clickedPos();
			BlockState st = level.getBlockState(primary);
			BlockPos actual = null;
			if (isAnyTeamWool(st)) {
				actual = primary;
			} else if (isAnyTeamWool(level.getBlockState(secondary))) {
				actual = secondary;
				st = level.getBlockState(secondary);
			}
			if (actual == null) {
				// 原版拒绝放置（典型：点击脚边草丛/花草——落点与玩家碰撞箱重叠，原版 isUnobstructed
				// 静默失败）→ 服务端兜底放置：仅限可替换/空气格且通过占领校验，成功即登记占领
				if (tryFallbackPlaceWool(level, p, wc)) continue;
				// 两处候选都放不下 → 明确反馈而非静默
				p.sendSystemMessage(Component.translatable("survival-contract.msg.civ_wool_place_failed"));
				continue;
			}
			// 每区块羊毛索引（每区块最多一个红/蓝羊毛）——按真实落点登记
			recordChunkWool(ChunkPos.containing(actual), actual);
			// 第五十一批：取消"放置不消耗"设置——羊毛放置正常消耗（原版扣物品），不再返还
			// 方块真实放置成功（本队羊毛）→ 登记占领；校验未通过 → 移除孤儿羊毛与索引
			if (isTeamWool(st, gsm.getPlayerTeam(wc.playerId()))) {
				if (!tryPlaceWool(p, ChunkPos.containing(actual), actual)) {
					level.removeBlock(actual, false);
					chunkWool.remove(ChunkPos.containing(actual));
				}
			}
		}
	}

	/** 服务端兜底放置羊毛（原版拒绝时）：点击草丛/花草等可替换方块时原版把落点定为被点击格，
	 *  若该格与玩家碰撞箱重叠（点脚边的草）则原版 isUnobstructed 静默失败——羊毛根本没落地，
	 *  占领也就无从发起。此方法按原版落点优先级在两处候选中兜底：仅当候选格可替换（短草/花草/
	 *  空气）且通过占领校验时用 setBlock 放置（绕过原版实体阻挡——玩家会被推到羊毛上）。
	 *  返回是否兜底放置成功（原版未消耗物品，故不返还羊毛）。 */
	private boolean tryFallbackPlaceWool(ServerLevel level, ServerPlayer p, WoolCheck wc) {
		BlockPos primary = wc.replaceTarget() ? wc.clickedPos() : wc.placePos();
		BlockPos secondary = wc.replaceTarget() ? wc.placePos() : wc.clickedPos();
		for (BlockPos candidate : new BlockPos[]{primary, secondary}) {
			BlockState cs = level.getBlockState(candidate);
			// 只允许替换装饰物/空气——绝不覆盖实体方块（点击实体方块侧面且相邻格为实体时两处都跳过）
			if (!cs.isAir() && !cs.canBeReplaced()) continue;
			if (validateWoolPlace(p, ChunkPos.containing(candidate)) != null) continue;
			level.setBlock(candidate, wc.wool().defaultBlockState(), 3);
			recordChunkWool(ChunkPos.containing(candidate), candidate);
			if (!tryPlaceWool(p, ChunkPos.containing(candidate), candidate)) {
				// 校验未受理 → 移除孤儿羊毛与索引（与真实放置路径同规则）
				level.removeBlock(candidate, false);
				chunkWool.remove(ChunkPos.containing(candidate));
			}
			return true;
		}
		return false;
	}

	// ==================== 占领进度 bossbar ====================
	private void createClaimBar(ChunkPos pos, PendingClaim pc, ServerPlayer claimer) {
		removeClaimBar(pos);
		var bar = new net.minecraft.server.level.ServerBossEvent(
				java.util.UUID.randomUUID(),
				Component.translatable("survival-contract.msg.civ_claiming_xy", pos.x(), pos.z()),
				pc.team() == GameStateManager.Team.RED
						? net.minecraft.world.BossEvent.BossBarColor.RED
						: net.minecraft.world.BossEvent.BossBarColor.BLUE,
				net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
		bar.setDarkenScreen(false);
		bar.setPlayBossMusic(false);
		// 进度条对全队可见（占领是团队事件，只有发起者能看到的进度条容易被当成"没有占领进度"）
		MinecraftServer srv = serverRef != null ? serverRef : claimer.level().getServer();
		for (ServerPlayer tp : srv.getPlayerList().getPlayers()) {
			if (GameStateManager.getInstance().getPlayerTeam(tp.getUUID()) == pc.team()) bar.addPlayer(tp);
		}
		claimBars.put(pos, bar);
	}

	private void updateClaimBar(ChunkPos pos, PendingClaim pc, long now) {
		var bar = claimBars.get(pos);
		if (bar == null) return;
		long remainTicks = Math.max(0, CLAIM_TIME_TICKS - (now - pc.startTick()));
		bar.setProgress(Math.min(1f, (float) (now - pc.startTick()) / CLAIM_TIME_TICKS));
		bar.setName(Component.translatable("survival-contract.msg.civ_claiming_progress",
				pos.x(), pos.z(), (int) Math.ceil(remainTicks / 20.0)));
	}

	private void removeClaimBar(ChunkPos pos) {
		var bar = claimBars.remove(pos);
		if (bar != null) {
			bar.removeAllPlayers();
			bar.setVisible(false);
		}
	}

	// ==================== 羊毛完整性（生物/爆炸破坏 → 立即回归无主） ====================
	private void checkWoolIntegrity(MinecraftServer server, ServerLevel level) {
		for (var e : zones.entrySet()) {
			Zone z = e.getValue();
			// 仅检查实际登记了占领羊毛的区块（大本营中心 = 玻璃基座无羊毛索引；
			// 大本营周围八区块 = 初始资源区，照常有占领羊毛，参与完整性检查）
			if (z.owner == null || !chunkWool.containsKey(e.getKey())) continue;
			if (!isTeamWool(level.getBlockState(z.woolPos), z.owner)) {
				SurvivalContract.LOGGER.info("[Civilization] Claim wool destroyed (mob/env) at ({},{})",
						e.getKey().x(), e.getKey().z());
				onWoolBroken(server, level, e.getKey());
			}
		}
	}

	// ==================== 羊毛挖掘高亮（第五十批） ====================
	/** 每 2 tick：扫描正在挖掘占领羊毛的玩家 → 点亮/刷新羊毛高亮；清理超时/方块已消失的高亮 */
	public void updateWoolGlow(ServerLevel level, long now) {
		if (serverRef == null) return;
		// 1) 挖掘中的玩家 → 对应占领羊毛点亮（不分敌我，正在被挖即高亮）
		for (ServerPlayer p : serverRef.getPlayerList().getPlayers()) {
			var acc = (com.survivalcontract.mixin.ServerPlayerGameModeAccessor) p.gameMode;
			if (!acc.sc$isDestroyingBlock() || acc.sc$getDestroyPos() == null) continue;
			BlockPos pos = acc.sc$getDestroyPos().immutable();
			BlockPos indexed = chunkWool.get(ChunkPos.containing(pos));
			if (indexed == null || !indexed.equals(pos)) continue;
			setWoolGlow(level, pos, now);
		}
		// 2) 超时未续（挖掘中断）或羊毛已消失（挖掉了）→ 移除高亮
		Iterator<Map.Entry<BlockPos, Long>> git = woolGlowActive.entrySet().iterator();
		while (git.hasNext()) {
			var e = git.next();
			BlockPos pos = e.getKey();
			if (now - e.getValue() <= WOOL_GLOW_TIMEOUT_TICKS && isAnyTeamWool(level.getBlockState(pos))) continue;
			git.remove();
			removeWoolGlow(level, pos);
		}
	}

	/** 点亮/刷新羊毛挖掘高亮：发光 BlockDisplay 覆盖在羊毛上（微下沉 0.005 防面重合闪烁） */
	private void setWoolGlow(ServerLevel level, BlockPos pos, long now) {
		woolGlowActive.put(pos, now);
		if (woolGlow.containsKey(pos)) return; // 已点亮，仅续期
		var display = new net.minecraft.world.entity.Display.BlockDisplay(entityType("block_display"), level);
		display.absSnapTo(pos.getX(), pos.getY() - 0.005, pos.getZ(), 0, 0);
		display.setNoGravity(true);
		com.survivalcontract.McCompat.setInvulnerable(display, true);
		display.setGlowingTag(true);
		setDisplayBlock(display, level.getBlockState(pos));
		var z = zones.get(ChunkPos.containing(pos));
		colorizeGlow(display, z != null ? z.owner : null);
		level.addFreshEntity(display);
		woolGlow.put(pos, display.getUUID());
	}

	private void removeWoolGlow(ServerLevel level, BlockPos pos) {
		UUID id = woolGlow.remove(pos);
		if (id == null) return;
		var e = level.getEntity(id);
		if (e != null) {
			removeFromGlowTeam(e);
			e.discard();
		}
	}

	/** BlockDisplay 显示指定方块状态（26.2 无公开 setter → 反射 entity data；失败仅显示默认方块，高亮仍在） */
	@SuppressWarnings("unchecked")
	private static void setDisplayBlock(net.minecraft.world.entity.Display.BlockDisplay display, BlockState st) {
		try {
			var f = net.minecraft.world.entity.Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
			f.setAccessible(true);
			var acc = (net.minecraft.network.syncher.EntityDataAccessor<BlockState>) f.get(null);
			display.getEntityData().set(acc, st);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] BlockDisplay state config failed: {}", e.toString());
		}
	}

	/** 发光轮廓按队伍上色（红/蓝 scoreboard team，仅含 display 实体；API 异常仅白光不影响高亮） */
	private void colorizeGlow(net.minecraft.world.entity.Entity e, GameStateManager.Team team) {
		if (team == null || team == GameStateManager.Team.NONE || serverRef == null) return;
		try {
			var sb = serverRef.getScoreboard();
			String name = "sc_wool_glow_" + (team == GameStateManager.Team.RED ? "red" : "blue");
			var pt = sb.getPlayerTeam(name);
			if (pt == null) {
				pt = sb.addPlayerTeam(name);
				pt.setColor(java.util.Optional.of(team == GameStateManager.Team.RED
						? net.minecraft.world.scores.TeamColor.RED : net.minecraft.world.scores.TeamColor.BLUE));
			}
			sb.addPlayerToTeam(e.getStringUUID(), pt);
		} catch (Exception ex) {
			SurvivalContract.LOGGER.warn("[Civilization] wool glow team color failed: {}", ex.toString());
		}
	}

	private void removeFromGlowTeam(net.minecraft.world.entity.Entity e) {
		if (serverRef == null) return;
		try {
			var sb = serverRef.getScoreboard();
			var pt = sb.getPlayersTeam(e.getStringUUID());
			if (pt != null) sb.removePlayerFromTeam(e.getStringUUID(), pt);
		} catch (Exception ignored) {}
	}

	/** 自测：指定羊毛位置的高亮实体 UUID（null = 未点亮） */
	public UUID woolGlowIdForTest(BlockPos pos) { return woolGlow.get(pos); }

	/** 自测：直接驱动挖掘高亮扫描（正常运行由 tick 驱动） */
	public void updateWoolGlowForTest(ServerLevel level, long now) { updateWoolGlow(level, now); }

	/** 自测：直接驱动生物禁跨区块扫描（正常运行由 tick 的每秒分支驱动；非对局阶段 tick 直接返回） */
	public void containMobsForTest(ServerLevel level) { containMobs(level); }

	// ==================== 生物管控：禁止跨区块 ====================
	private void containMobs(ServerLevel level) {
		if (managedMobs.isEmpty()) return;
		boolean refreshBuffs = level.getGameTime() % 40 == 0; // 每 2 秒为守护怪补挂无限增益
		Iterator<Map.Entry<UUID, ChunkPos>> it = managedMobs.entrySet().iterator();
		while (it.hasNext()) {
			var me = it.next();
			var ent = level.getEntity(me.getKey());
			ChunkPos home = me.getValue();
			if (ent == null) {
				// 实体检索不到：两种情形必须区分（否则要么逃逸、要么无法自愈）——
				// (a) 所属区块未加载：实体保存在区块中，此时擅自解除登记会让它在重载后不再被拉回 → 逃逸（本次修复）；
				// (b) 区块已加载但实体仍检索不到：确认已彻底消失（尸体移除/kill/重载丢失）→ 自愈清理登记（第五十七批）。
				if (level.getChunkSource().getChunkNow(home.x(), home.z()) == null) continue; // (a) 保留登记待重载后拉回
				it.remove();
				removeGuardianId(home, me.getKey()); // (b) 彻底消失 → 清理登记并解除占领封锁
				continue;
			}
			if (!ent.isAlive()) {
				it.remove();
				// 守护怪死亡/确认消失 → 解除占领封锁
				removeGuardianId(home, me.getKey());
				continue;
			}
			if (!ChunkPos.containing(ent.blockPosition()).equals(home)) {
				// 跨出所属区块 → 拉回区块中心地表（守护者不能走出自己守护的区块）
				int hx = home.getMinBlockX() + 8, hz = home.getMinBlockZ() + 8;
				int hy = surfaceY(level, hx, hz);
				ent.absSnapTo(hx + 0.5, hy + 1, hz + 0.5, ent.getYRot(), ent.getXRot());
				ent.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			}
			// 守护怪增益保鲜：无限时长的抗性提升 2 + 迅捷 2 + 火焰免疫（防牛奶/机制清除后失效）
			if (refreshBuffs && ent.entityTags().contains(GUARDIAN_TAG)
					&& ent instanceof net.minecraft.world.entity.Mob guardian) {
				guardian.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.RESISTANCE,
						net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 1, true, false));
				guardian.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.SPEED,
						net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 1, true, false));
				guardian.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
						net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, true, false)); // 火焰免疫（第六十批）
			}
		}
	}

	/**
	 * 守护怪火焰抑制（第六十批，本次扩展）：每 tick 扑灭守护怪与文明模式下全部僵尸马身上的火。
	 * 26.2 骑兵构成中僵尸马坐骑没有护甲槽，白天必然被阳光点燃（骑手戴头盔吸热不受影响）——
	 * 无限火抗（spawnGuardian/containMobs 补挂）兜底伤害，本方法消除燃烧视觉，白天完全不自燃。
	 * 本次扩展覆盖**非守护**的僵尸马（遭遇事件「僵尸马骑兵」等），它们此前未被登记 → 仍会着火。
	 */
	private void guardianFireSweep(ServerLevel level) {
		if (!managedMobs.isEmpty()) {
			for (UUID id : managedMobs.keySet()) {
				var ent = level.getEntity(id);
				if (ent != null && ent.isOnFire() && ent.entityTags().contains(GUARDIAN_TAG)) {
					ent.extinguishFire();
				}
			}
		}
		if (!civZombieHorses.isEmpty()) {
			civZombieHorses.removeIf(id -> {
				var ent = level.getEntity(id);
				if (ent == null || !ent.isAlive()) return true; // 卸载/死亡 → 移出跟踪（重载时 ENTITY_LOAD 会重新登记）
				if (ent.isOnFire()) ent.extinguishFire();
				return false;
			});
		}
	}

	/** 文明模式僵尸马加载登记（ENTITY_LOAD 调用）：补挂无限火焰免疫并纳入火焰抑制跟踪 */
	private void trackCivZombieHorse(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.animal.equine.ZombieHorse)) return;
		GameStateManager gsm = GameStateManager.getInstance();
		if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
		if (entity.level() != com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(
				entity.level().getServer())) return;
		if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
			le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, true, false));
		}
		civZombieHorses.add(entity.getUUID());
	}

	/** 测试钩子：手动驱动一次僵尸马火焰登记（自测直接调用，跳过 ENTITY_LOAD；自测阶段竞技场维度尚未创建，
	 *  故此处不校验 arena 世界归属，只做登记 + 补挂无限火焰免疫，等价于生产路径的效果）。 */
	public void trackCivZombieHorseForTest(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.animal.equine.ZombieHorse)) return;
		if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
			le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, true, false));
		}
		civZombieHorses.add(entity.getUUID());
	}

	/** 测试钩子：手动驱动一次火焰抑制（测试阶段 CivilizationManager.tick 直接返回） */
	public void sweepGuardianFireForTest(ServerLevel level) { guardianFireSweep(level); }

	// ==================== 资源区守护怪物（第五十三批，第五十七批修订构成） ====================
	/** 守护怪物实体标记：钻石区 = 僵尸马骑兵（骑手戴铁头盔、持铁长矛）、合金区 = 骆驼尸壳（固定双乘客：金长矛尸壳 + 焦骸） */
	public static final String GUARDIAN_TAG = "civil_guardian";
	/** 区块 → 守护怪物实体 UUID 集合（全部死亡后才能占领该区块） */
	private final Map<ChunkPos, Set<UUID>> zoneGuardians = new HashMap<>();

	/** 开局为全部钻石区/合金区生成守护怪物 */
	private void spawnZoneGuardians(ServerLevel level) {
		for (var e : zones.entrySet()) {
			if (e.getValue().type == ZoneType.DIAMOND) spawnGuardian(level, e.getKey(), true);
			else if (e.getValue().type == ZoneType.ALLOY) spawnGuardian(level, e.getKey(), false);
		}
	}

	/**
	 * 生成一个守护怪（public 供自测直接调用并断言实体构成，第五十七批修订）：
	 * 钻石区 = 僵尸马骑兵（僵尸马 + 骑乘的僵尸，骑手戴铁头盔、手持铁长矛）；
	 * 合金区 = 骆驼尸壳，背上固定两个怪物：手持金长矛的尸壳（骑手）+ 焦骸（Parched）。
	 * 全体自带无限时长的抗性提升 2、迅捷 2 与火焰免疫（白天不自燃，第六十批）、不消失、登记 managedMobs 禁止走出守护区块。
	 *
	 * @return 本次生成并加入世界的守护实体（坐骑 + 骑手）
	 */
	public List<net.minecraft.world.entity.Mob> spawnGuardian(ServerLevel level, ChunkPos cp, boolean zombieHorseman) {
		BlockPos p = randomGround(level, cp, new Random());
		if (p == null) {
			int gx = cp.getMinBlockX() + 8, gz = cp.getMinBlockZ() + 8;
			p = new BlockPos(gx, surfaceY(level, gx, gz), gz);
		}
		List<net.minecraft.world.entity.Mob> spawned = new ArrayList<>(2);
		Set<UUID> ids = zoneGuardians.computeIfAbsent(cp, k -> new HashSet<>());
		if (zombieHorseman) {
			var horseType = entityType("zombie_horse");
			var zombieType = entityType("zombie");
			if (horseType == null || zombieType == null) return spawned;
			var horse = horseType.create(level, EntitySpawnReason.EVENT);
			if (horse == null) return spawned;
			prepareSpawn(level, p, horse, 0); // 补原版初始化（属性）
			if (horse instanceof net.minecraft.world.entity.animal.equine.AbstractHorse ah) {
				ah.setTamed(true); // 原版骑兵马为已驯服（不会被甩下/不反抗）
			}
			var rider = zombieType.create(level, EntitySpawnReason.EVENT);
			if (!(rider instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie)) {
				if (rider != null) rider.discard();
				horse.discard();
				return spawned;
			}
			prepareSpawn(level, p, zombie, 0);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				zombie.setItemSlot(slot, ItemStack.EMPTY); // 清空随机装备
			}
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR)); // 手持铁长矛（26.2：spear = 长矛）
			zombie.startRiding(horse); // 26.2：Entity.startRiding(Entity) 单参（刚创建未入世界，可直接骑乘）
			spawned.add((net.minecraft.world.entity.Mob) horse);
			spawned.add(zombie);
		} else {
			var camelHuskType = entityType("camel_husk");
			if (camelHuskType == null) return spawned;
			var camel = camelHuskType.create(level, EntitySpawnReason.EVENT);
			if (camel == null) return spawned;
			prepareSpawn(level, p, camel, 0); // 原版生成方式：finalizeSpawn 可能自带随机骑手
			for (net.minecraft.world.entity.Entity vanilla : new ArrayList<>(camel.getPassengers())) {
				vanilla.discard(); // 固定构成：清除原版随机骑手，由下方固定补齐
			}
			var huskType = entityType("husk");
			if (huskType == null) {
				camel.discard();
				return spawned;
			}
			var huskEnt = huskType.create(level, EntitySpawnReason.EVENT);
			if (!(huskEnt instanceof net.minecraft.world.entity.monster.zombie.Husk husk)) {
				if (huskEnt != null) huskEnt.discard();
				camel.discard();
				return spawned;
			}
			prepareSpawn(level, p, husk, 0);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				husk.setItemSlot(slot, ItemStack.EMPTY); // 清空随机装备
			}
			husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SPEAR)); // 手持金长矛
			husk.startRiding(camel);
			spawned.add((net.minecraft.world.entity.Mob) camel);
			spawned.add(husk);
			// 第二个固定乘客：焦骸（Parched）
			var parchedType = entityType("parched");
			if (parchedType != null) {
				var parchedEnt = parchedType.create(level, EntitySpawnReason.EVENT);
				if (parchedEnt instanceof net.minecraft.world.entity.monster.skeleton.Parched parched) {
					prepareSpawn(level, p, parched, 0);
					parched.startRiding(camel);
					spawned.add(parched);
				} else if (parchedEnt != null) {
					parchedEnt.discard();
				}
			}
		}
		// 骑手一并登记守护（含 finalizeSpawn 自动生成的骑手）：击败全部守护实体才能占领
		java.util.ArrayDeque<net.minecraft.world.entity.Entity> queue = new java.util.ArrayDeque<>();
		for (net.minecraft.world.entity.Mob m : spawned) queue.add(m);
		Set<net.minecraft.world.entity.Entity> seen = new HashSet<>(spawned);
		while (!queue.isEmpty()) {
			net.minecraft.world.entity.Entity e = queue.poll();
			for (net.minecraft.world.entity.Entity pax : e.getPassengers()) {
				if (pax instanceof net.minecraft.world.entity.Mob mob && seen.add(pax)) {
					spawned.add(mob);
					queue.add(pax);
				}
			}
		}
		for (net.minecraft.world.entity.Mob m : spawned) {
			m.addTag(GUARDIAN_TAG);
			m.setPersistenceRequired(); // 不自然消失
			m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.RESISTANCE,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 1, true, false)); // 抗性提升 2（无限）
			m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.SPEED,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 1, true, false)); // 迅捷 2（无限）
			m.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, true, false)); // 火焰免疫（无限，第六十批白天不自燃）
			if (!m.isPassenger()) {
				m.absSnapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
			}
			level.addFreshEntity(m);
			managedMobs.put(m.getUUID(), cp); // 禁止离开本区块
			ids.add(m.getUUID());
		}
		return spawned;
	}

	/** 该区块是否仍有存活的守护怪（有 → 不可占领）。
	 *  区块未加载 → 实体可能处于卸载保存中，保守视为仍在守护；
	 *  区块已加载 → 实体检索不到即视为已彻底消失（尸体移除/kill/重载丢失，第五十七批修复击败后仍无法占领的永久锁死）。 */
	private boolean guardianAlive(ServerLevel level, ChunkPos cp) {
		Set<UUID> ids = zoneGuardians.get(cp);
		if (ids == null || ids.isEmpty()) return false;
		if (level.getChunkSource().getChunkNow(cp.x(), cp.z()) == null) return true;
		ids.removeIf(id -> {
			var e = level.getEntity(id);
			return e == null || !e.isAlive(); // 区块已加载：检索不到 = 已消失 → 确认解除封锁
		});
		return !ids.isEmpty();
	}

	/** 守护怪死亡清理（containMobs 检测到实体死亡时调用） */
	private void removeGuardianId(ChunkPos cp, UUID id) {
		Set<UUID> s = zoneGuardians.get(cp);
		if (s != null) s.remove(id);
	}

	/** 自测：查看/直接操纵区块守护登记（null = 移除登记） */
	public Set<UUID> guardiansForTest(ChunkPos cp) { return zoneGuardians.get(cp); }

	public void setGuardiansForTest(ChunkPos cp, Set<UUID> ids) {
		if (ids == null) zoneGuardians.remove(cp);
		else zoneGuardians.put(cp, ids);
	}

	/** 自测：只读空白区配额表（第五十五批）：沙8/铜34/铁24/金20（精确数量） */
	public static Map<ZoneType, Integer> zoneQuotasForTest() { return ZONE_QUOTAS; }

	/** 自测：只读剩余空白随机填充池（图2 权重：石英24/青金石16/野生16） */
	public static List<ZoneType> zoneFreightPoolForTest() { return ZONE_FREIGHT_POOL; }

	/** 自测：只读图1 固定布局格（[row][col] = [z 偏移+7][x 偏移+7]；null = 空白随机格） */
	public static ZoneType fixedLayoutForTest(int row, int col) { return FIXED_LAYOUT[row][col]; }

	// ==================== 玩家移动限制：不能跨越多个无主/敌方区块 ====================
	/** 玩家只能位于本方领土，或与本方领土相邻的无主/敌方区块（canClaim 的 4 邻接判定） */
	private void enforceTerritoryLimit(MinecraftServer server, ServerLevel level, GameStateManager gsm) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || p.isSpectator() || gsm.isLobbySpectator(p.getUUID())) continue;
			GameStateManager.Team team = gsm.getPlayerTeam(p.getUUID());
			if (team == GameStateManager.Team.NONE) continue;
			ChunkPos cur = ChunkPos.containing(p.blockPosition());
			Zone z = zones.get(cur);
			if (z != null && z.owner == team) continue;
			if (canClaim(team, cur)) continue; // 与本方领土相邻的无主/敌方区块 → 允许
			// 深入无主/敌方纵深 → 传回最近的己方区块（兜底本方大本营）
			ChunkPos home = nearestOwnedChunk(team, cur);
			if (home == null) home = hqCenter.get(team);
			if (home == null) continue;
			int hx = home.getMinBlockX() + 8, hz = home.getMinBlockZ() + 8;
			int hy = surfaceY(level, hx, hz);
			p.teleportTo(level, hx + 0.5, hy + 1.0, hz + 0.5,
					java.util.Set.of(), p.getYRot(), p.getXRot(), false);
			Long last = moveWarnAt.get(p.getUUID());
			if (last == null || p.level().getServer().getTickCount() - last > 60) {
				moveWarnAt.put(p.getUUID(), (long) p.level().getServer().getTickCount());
				p.sendSystemMessage(Component.translatable(
						"survival-contract.msg.civ_deep_chunk"));
			}
		}
	}

	/** 最近的己方区块（从 cur 螺旋外扩，最多 6 圈） */
	private ChunkPos nearestOwnedChunk(GameStateManager.Team team, ChunkPos cur) {
		for (int r = 1; r <= 6; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // 仅外圈
					Zone z = zones.get(new ChunkPos(cur.x() + dx, cur.z() + dz));
					if (z != null && z.owner == team) return new ChunkPos(cur.x() + dx, cur.z() + dz);
				}
			}
		}
		return null;
	}

	// ==================== 区块边界粒子 ====================
	/**
	 * 玩家附近区块边界用队伍颜色 dust 粒子圈出（仅地表；无主=白）。
	 * 每 4 格一段发 1 包（count=6 沿边界轴 ±2 格散布）→ 每区块 48 粒 = 原来的 6 倍密集；
	 * 高度 = 地表上方 0.5 格（较此前下降半格）。超平坦地形完全平坦，段内共用高度无落差。
	 */
	private void sendBorderParticles(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!(p instanceof ServerPlayer)) continue;
			ChunkPos pc = ChunkPos.containing(p.blockPosition());
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					ChunkPos cp = new ChunkPos(pc.x() + dx, pc.z() + dz);
					Zone z = zones.get(cp);
					if (z == null) continue;
					var dust = z.owner == null ? DUST_WHITE
							: z.owner == GameStateManager.Team.RED ? DUST_RED : DUST_BLUE;
					int minX = cp.getMinBlockX(), minZ = cp.getMinBlockZ();
					for (int i = 0; i < 16; i += 4) {
						// 北边（z=minZ）：沿 x 轴散布；西边（x=minX）：沿 z 轴散布
						sendDustLine(level, p, dust, minX + i + 2, minZ + 0.5, 2, 0, 0);
						sendDustLine(level, p, dust, minX + 0.5, minZ + i + 2, 0, 0, 2);
					}
				}
			}
		}
	}

	/** 一段边界（4 格）的粒子：count=6 沿边界轴散布；高度 = 地表上方 0.5 格 */
	private void sendDustLine(ServerLevel level, ServerPlayer p,
	                          net.minecraft.core.particles.DustParticleOptions dust,
	                          double x, double z, double ox, double oy, double oz) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
		level.sendParticles(p, dust, false, false, x, y + 0.5, z, 6, ox, oy, oz, 0);
	}

	// ==================== 占领 ====================
	/** 验证羊毛邻接规则：目标区块必须 4 邻接本队已占领区块 */
	public boolean canClaim(GameStateManager.Team team, ChunkPos pos) {
		for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			Zone n = zones.get(new ChunkPos(pos.x() + d[0], pos.z() + d[1]));
			if (n != null && n.owner == team) return true;
		}
		return false;
	}

	/** 羊毛放置拒绝原因（八种）——单一判定来源，供中文串与本地化 Component 两条输出共用 */
	private enum WoolDeny {
		OUT_OF_RANGE, NO_TEAM, ALREADY_OWNED, ENEMY_OWNED, HQ, GUARDIAN, PENDING, NOT_ADJACENT
	}

	/** 判定拒绝原因：null = 允许放置 */
	private WoolDeny woolDeny(ServerPlayer player, ChunkPos pos) {
		GameStateManager gsm = GameStateManager.getInstance();
		Zone z = zones.get(pos);
		if (z == null) return WoolDeny.OUT_OF_RANGE;
		GameStateManager.Team team = gsm.getPlayerTeam(player.getUUID());
		if (team == GameStateManager.Team.NONE) return WoolDeny.NO_TEAM;
		if (z.owner == team) return WoolDeny.ALREADY_OWNED;
		if (z.owner != null) return WoolDeny.ENEMY_OWNED;
		if (z.type == ZoneType.HQ) return WoolDeny.HQ;
		if ((z.type == ZoneType.DIAMOND || z.type == ZoneType.ALLOY)
				&& guardianAlive((ServerLevel) player.level(), pos)) {
			return WoolDeny.GUARDIAN;
		}
		if (pendingClaims.containsKey(pos)) return WoolDeny.PENDING;
		if (!canClaim(team, pos)) return WoolDeny.NOT_ADJACENT;
		return null;
	}

	/** 羊毛放置前置校验（原版放置发生前调用）：返回 null=允许放置；非 null=拒绝原因。
	 *  不可占领的区块必须**直接取消放置**——否则羊毛照常落地成为"孤儿羊毛"，
	 *  之后该区块被已存在羊毛索引锁死（"提示已占领/不回归无主"的根源）。
	 *  中文串版本（保持自测断言语义不变；面向玩家的广播请优先用 {@link #validateWoolPlaceMsg}） */
	public String validateWoolPlace(ServerPlayer player, ChunkPos pos) {
		WoolDeny d = woolDeny(player, pos);
		if (d == null) return null;
		return switch (d) {
			case OUT_OF_RANGE -> "§c该位置不在疆域范围内，无法放置占领羊毛！";
			case NO_TEAM -> "§c你没有队伍，无法放置占领羊毛！";
			case ALREADY_OWNED -> "§7该区块已是本方领土，无需占领。";
			case ENEMY_OWNED -> "§c该区块已被对方占领——必须先破坏其占领羊毛！";
			case HQ -> "§c大本营只能通过击破核心盔甲架攻陷，无需放置羊毛！";
			case GUARDIAN -> "§c该区块由强大的骑兵守护——先击败守护怪物（僵尸马骑兵/骆驼尸壳双骑手，自带无限抗性提升2与迅捷2）才能放置占领羊毛！";
			case PENDING -> "§7该区块已在占领中（每人同区块同时只能有一处占领）！";
			case NOT_ADJACENT -> "§c只能占领与本方领土相邻的区块——不相邻的区块无法放置占领羊毛！";
		};
	}

	/** 本地化版本（服务端下发 Component.translatable，客户端按自身语言解析）；null = 允许放置 */
	public Component validateWoolPlaceMsg(ServerPlayer player, ChunkPos pos) {
		WoolDeny d = woolDeny(player, pos);
		if (d == null) return null;
		return Component.translatable("survival-contract.msg.wool_deny."
				+ d.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** 玩家放置本队羊毛（方块真实放置成功后调用）：登记 pending，15 秒后验证占领。
	 *  返回是否受理——校验规则与 {@link #validateWoolPlace} 完全一致（防孤儿羊毛的最终兜底）。 */
	public boolean tryPlaceWool(ServerPlayer player, ChunkPos pos, BlockPos woolPos) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return false;
		Component err = validateWoolPlaceMsg(player, pos);
		if (err != null) {
			player.sendSystemMessage(err);
			return false;
		}
		PendingClaim pc = new PendingClaim(pos, gsm.getPlayerTeam(player.getUUID()), woolPos,
				player.level().getServer().getTickCount());
		pendingClaims.put(pos, pc);
		createClaimBar(pos, pc, player); // 占领进度 bossbar（倒计时）
		player.sendSystemMessage(Component.translatable(
				"survival-contract.msg.civ_wool_placing"));
		SurvivalContract.LOGGER.info("[Civilization] Pending claim: {} by {} at {}",
				pos.x() + "," + pos.z(), gsm.getPlayerTeam(player.getUUID()), woolPos.toShortString());
		return true;
	}

	/** 占领完成：验证羊毛仍为本队羊毛 → 更新归属 + 悬浮图标 + 判负检查 */
	private void completeClaim(MinecraftServer server, ServerLevel level, PendingClaim pc) {
		GameStateManager gsm = GameStateManager.getInstance();
		BlockState st = level.getBlockState(pc.woolPos());
		if (!isTeamWool(st, pc.team())) return; // 羊毛被破坏 → 放弃
		Zone z = zones.get(pc.pos());
		if (z == null) return;
		GameStateManager.Team old = z.owner;
		if (old == pc.team()) return;
		z.owner = pc.team();
		z.woolPos = pc.woolPos();
		removeFloatIcon(level, z);
		spawnFloatIcon(level, z);
		Component teamName = pc.team() == GameStateManager.Team.RED
				? Component.translatable("survival-contract.gui.team.red")
				: Component.translatable("survival-contract.gui.team.blue");
		Component typeName = z.type == ZoneType.HQ
				? Component.translatable("survival-contract.gui.civ.hq")
				: Component.translatable("survival-contract.gui.civ.zone", z.type.toString());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("survival-contract.msg.civ_claimed",
					teamName, typeName, pc.pos().x(), pc.pos().z()));
		}
		// 大本营被对方占领 → 对方直接判负
		if (z.type == ZoneType.HQ && old != null && old != pc.team()) {
			forfeit(server, old, pc.team());
		}
	}

	/** 羊毛被破坏（AFTER/生物摧毁）：区块回归无主 + 悬浮移除 + pending 取消 */
	public void onWoolBroken(MinecraftServer server, ServerLevel level, ChunkPos pos) {
		Zone z = zones.get(pos);
		if (z == null) return;
		chunkWool.remove(pos); // 每区块羊毛索引按区块键清除（破坏后可重新放置）
		z.owner = null;
		z.woolPos = null;
		removeFloatIcon(level, z);
		removeClaimBar(pos);
		pendingClaims.remove(pos);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable(
					"survival-contract.msg.civ_zone_unclaimed", pos.x(), pos.z()));
		}
	}

	// ==================== 锁定与权限 ====================
	/**
	 * 区块内功能方块/容器是否可交互/破坏。
	 * 箱子/木桶按**队伍归属**判定：归属队且区块归该队时才可开（敌方反占后依然锁定）；
	 * 其他功能方块按区块归属判定（无主 = 全锁定）。
	 */
	public boolean canInteract(ServerPlayer player, BlockPos pos, BlockState st) {
		GameStateManager gsm = GameStateManager.getInstance();
		Zone z = zones.get(ChunkPos.containing(pos));
		if (z == null) return true; // 边界外不归文明系统管
		GameStateManager.Team playerTeam = gsm.getPlayerTeam(player.getUUID());
		boolean isContainer = st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
				|| st.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock;
		if (isContainer) {
			GameStateManager.Team owner = containerOwners.get(pos.immutable());
			if (owner == null) return z.owner != null && playerTeam == z.owner; // 旧容器（无记录）→ 归当前占领队
			return owner == playerTeam && z.owner == playerTeam;
		}
		if (z.owner == null) return false; // 无主 → 锁定
		return playerTeam == z.owner;
	}

	/** 目标方块是否为功能方块/容器（锁定规则适用范围；铜箱子家族 = ChestBlock 子类自动覆盖） */
	public static boolean isFunctionalBlock(BlockState st) {
		Block b = st.getBlock();
		return b instanceof ChestBlock || b instanceof BarrelBlock
				|| b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE
				|| b == Blocks.COAL_BLOCK || b == Blocks.DIAMOND_BLOCK
				|| Blocks.COPPER_BLOCK.asList().contains(b) /* 26.2：COPPER_BLOCK 是氧化集合字段，按家族判定（含涂蜡变体） */
				|| b == Blocks.IRON_BLOCK || b == Blocks.GOLD_BLOCK
				|| b == Blocks.ENDER_CHEST;
	}

	// ==================== 资源产出 ====================
	/** 前置方块匹配：精确匹配注册名；铜箱子按家族兼容——放置后自然氧化/涂蜡不会中断收获
	 *  （minecraft:copper_chest 与 exposed/weathered/oxidized 及各涂蜡变体同属 CopperChestBlock）；
	 *  木头按家族兼容——oak_wood 为代表，任意树种的 *_wood 六面皮木块都算"木头"（原木不算） */
	private static boolean prereqBlockMatches(BlockState st, Block required) {
		if (st.is(required)) return true;
		if (required instanceof CopperChestBlock && st.getBlock() instanceof CopperChestBlock) return true;
		if (required == Blocks.OAK_WOOD) {
			return BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath().endsWith("_wood");
		}
		return false;
	}

	/**
	 * 收获前置校验：该区块放置齐产出配置 requireBlocks 要求的全部方块后才产出（第四十八批配置化）。
	 * 青金石区默认前置含熔炉/高炉时保留「熔炉类方块必须放置在 Y≤0 层」规则（图2：熔炉（Y≤0））；
	 * 自定义前置不含熔炉类方块则不受 Y 限制。
	 */
	private boolean prereqMet(ChunkPos cp, ZoneType type) {
		Block[] required = prodCfg.zoneRequireBlocks(type);
		if (required.length == 0) return true;
		Set<BlockPos> fac = facilities.getOrDefault(cp, Set.of());
		boolean deepFurnace = type == ZoneType.LAPIS;
		for (Block req : required) {
			boolean found = false;
			for (BlockPos p : fac) {
				BlockState st = blockStateAt(p);
				if (st == null || !prereqBlockMatches(st, req)) continue;
				if (deepFurnace && (req == Blocks.FURNACE || req == Blocks.BLAST_FURNACE) && p.getY() > 0) continue;
				found = true;
				break;
			}
			if (!found) return false;
		}
		return true;
	}

	private ServerLevel cachedLevel = null;
	private BlockState blockStateAt(BlockPos p) {
		if (cachedLevel == null) return null;
		return cachedLevel.getBlockState(p);
	}

	/** 每秒巡检：到期区块独立产出 + 各队大本营战斗物资（每区块/每队独立计时，参数由产出配置决定） */
	private void produceZones(ServerLevel level, long now) {
		cachedLevel = level;
		for (var e : zones.entrySet()) {
			Zone z = e.getValue();
			if (z.owner == null || z.type == ZoneType.HQ) continue;
			// 概率刷怪独立计时：每秒结算，不受产出节拍影响（间隔/概率/数量由产出配置决定）
			tickZoneSpawn(level, e.getKey(), z, now);
			long interval = z.type == ZoneType.WILDLIFE
					? prodCfg.wildlifeIntervalTicks()
					: prodCfg.zoneIntervalTicks(z.type);
			Long next = zoneNextProduce.get(e.getKey());
			if (next == null) { // 首次看到该区块归属 → 一个间隔后首次产出
				zoneNextProduce.put(e.getKey(), now + interval);
				continue;
			}
			if (now < next) continue;
			zoneNextProduce.put(e.getKey(), now + interval);
			produceZone(level, e.getKey(), z);
		}
		for (GameStateManager.Team team : new GameStateManager.Team[]{
				GameStateManager.Team.RED, GameStateManager.Team.BLUE}) {
			Long next = hqNextProduce.get(team);
			// 间隔随占领数动态变化（第四十九批：基础 5 分钟，每占领 5 区块 -15s，最低 1 分钟）
			long claims = zones.values().stream().filter(zz -> zz.owner == team).count();
			long interval = hqSupplyIntervalSeconds(prodCfg.hqSupplies, claims) * 20L;
			if (next == null) {
				hqNextProduce.put(team, now + interval);
				continue;
			}
			if (now < next) continue;
			hqNextProduce.put(team, now + interval);
			produceHqSupplies(level, team);
		}
		cachedLevel = null;
	}

	/**
	 * 大本营战斗物资产出间隔（秒）：基础 intervalSeconds，每占领 claimsPerStep 个区块
	 * 缩短 reducePerStepSeconds 秒，最低 minSeconds 秒（第四十九批公式，public 供自测断言）。
	 */
	public static long hqSupplyIntervalSeconds(CivilizationProductionConfig.HqSuppliesCfg hc, long claims) {
		int step = Math.max(1, hc.claimsPerStep);
		long reduced = (long) hc.intervalSeconds - (claims / step) * (long) hc.reducePerStepSeconds;
		return Math.max(hc.minSeconds, reduced);
	}

	/** 单个资源区块的一次产出（数量/概率副产品/概率刷怪由产出配置决定） */
	private void produceZone(ServerLevel level, ChunkPos cp, Zone z) {
		Random rnd = new Random();
		// 野生动物区：刷动物 + 概率村民；有村民时产绿宝石
		if (z.type == ZoneType.WILDLIFE) {
			spawnWildlife(level, cp, rnd);
			int emeralds = prodCfg.wildlife.emeraldCount;
			if (emeralds > 0 && hasVillager(level, cp)) {
				injectZone(level, cp, new ItemStack(Items.EMERALD, emeralds));
			}
			return;
		}
		if (!prereqMet(cp, z.type)) return;
		net.minecraft.world.item.Item out = prodCfg.zoneOutput(z.type);
		int n = prodCfg.zoneCount(z.type);
		// 主产物产出概率（outputChancePercent，默认 100 = 必定产出）
		if (out != null && out != Items.AIR && n > 0
				&& rnd.nextDouble() * 100 < prodCfg.zoneOutputChancePercent(z.type)) {
			injectZone(level, cp, new ItemStack(out, n));
		}
		// 概率产出（支持小数百分比；pool 随机取一；count 为本次命中产出数量，默认 1；
		// totalCap > 0 时受单区块累计上限约束——如钻石区黑曜石 30 个，产满后不再产出）
		List<CivilizationProductionConfig.RBonus> bonuses = prodCfg.zoneBonuses(z.type);
		for (int bi = 0; bi < bonuses.size(); bi++) {
			CivilizationProductionConfig.RBonus b = bonuses.get(bi);
			if (b.items().length == 0) continue;
			Map<Integer, Integer> counters = b.totalCap() > 0
					? bonusProduced.computeIfAbsent(cp, k -> new HashMap<>()) : null;
			int produced = counters == null ? 0 : counters.getOrDefault(bi, 0);
			if (b.totalCap() > 0 && produced >= b.totalCap()) continue;
			if (rnd.nextDouble() * 100 >= b.chancePercent()) continue;
			int give = Math.min(b.count(), b.totalCap() > 0 ? b.totalCap() - produced : b.count());
			injectZone(level, cp, new ItemStack(b.items()[rnd.nextInt(b.items().length)], give));
			if (counters != null) counters.put(bi, produced + give);
		}
	}

	/**
	 * 概率刷怪（石英区岩浆怪 / 合金区凋灵骷髅；种类/概率/数量/间隔/生命值/区块内上限由产出配置决定）。
	 * 独立于产出的刷怪计时：spawnIntervalSeconds=0 时跟随该区产出间隔（与旧版行为一致）。
	 */
	private void tickZoneSpawn(ServerLevel level, ChunkPos cp, Zone z, long now) {
		if (z.type == ZoneType.HQ || z.type == ZoneType.WILDLIFE) return; // 野生动物区有独立配置节
		String mob = prodCfg.zoneSpawnMob(z.type);
		if (mob.isEmpty()) return;
		Long next = zoneNextSpawn.get(cp);
		if (next == null) { // 首次看到该区块归属 → 一个刷怪间隔后首次结算
			zoneNextSpawn.put(cp, now + prodCfg.zoneSpawnIntervalTicks(z.type));
			return;
		}
		if (now < next) return;
		zoneNextSpawn.put(cp, now + prodCfg.zoneSpawnIntervalTicks(z.type));
		int chance = prodCfg.zoneSpawnChancePercent(z.type);
		if (chance > 0 && new Random().nextInt(100) < chance) {
			var et = entityType(mob);
			if (et != null) spawnHostile(level, cp, z.type, et, prodCfg.zoneSpawnCount(z.type));
		}
	}

	/**
	 * 大本营战斗物资：每组 = 产出配置 items 池里每种物品各 1 个（默认风弹 1 + 金苹果 1）。
	 * 间隔随占领数动态缩短（produceZones 调度时计算），基础数量/间隔/产出概率由产出配置决定，累积取整发放。
	 * 仅注入本队大本营的物资桶（用户要求：不再进入大本营其他箱子——此前遍历 facilities
	 * 里玩家放置的容器，导致产物进了其他区块的箱子而不是系统放置的物资桶）。
	 */
	private void produceHqSupplies(ServerLevel level, GameStateManager.Team team) {
		ChunkPos hq = hqCenter.get(team);
		if (hq == null) return;
		Zone hqZone = zones.get(hq);
		if (hqZone == null || hqZone.owner != team) return;
		CivilizationProductionConfig.HqSuppliesCfg hc = prodCfg.hqSupplies;
		// 产出概率（chancePercent，默认 100）：本次未命中则跳过（累积器不动）
		if (prodCfg.hqChancePercent() < 100.0
				&& new Random().nextDouble() * 100 >= prodCfg.hqChancePercent()) return;
		double rate = hc.baseCount;
		if (rate <= 0) return;
		double acc = hqSupplyAcc.merge(team, rate, Double::sum);
		int n = (int) acc;
		if (n <= 0) return;
		// 大本营没有可用物资桶 → 不发放（累积保留并封顶，防无限堆积）
		if (!hqHasBarrel(level, team)) {
			hqSupplyAcc.put(team, Math.min(acc, 10.0));
			return;
		}
		hqSupplyAcc.put(team, acc - n);
		var items = prodCfg.hqItems();
		for (int i = 0; i < n; i++) {
			for (net.minecraft.world.item.Item it : items) {
				injectBarrel(level, team, new ItemStack(it, 1));
			}
		}
	}

	/** 本队大本营物资桶是否可注入（木桶存在且未被破坏） */
	private boolean hqHasBarrel(ServerLevel level, GameStateManager.Team team) {
		BlockPos b = supplyBarrels.get(team);
		return b != null && level.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock
				&& level.getBlockEntity(b) instanceof net.minecraft.world.Container;
	}

	/** 战斗物资注入：仅本队物资桶（溢出丢弃——累积器封顶 10 防堆积） */
	private void injectBarrel(ServerLevel level, GameStateManager.Team team, ItemStack stack) {
		BlockPos b = supplyBarrels.get(team);
		if (b == null) return;
		if (!(level.getBlockState(b).getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)) return;
		if (level.getBlockEntity(b) instanceof net.minecraft.world.Container c) {
			inject(c, stack.copy());
		}
	}

	/** 注入产出：仅区内**箱子/木桶**，且只进**归当前占领队所有**的容器（敌方反占后旧队容器不再进新资源） */
	private void injectZone(ServerLevel level, ChunkPos cp, ItemStack stack) {
		Zone z = zones.get(cp);
		if (z == null || z.owner == null) return;
		Set<BlockPos> fac = facilities.getOrDefault(cp, Set.of());
		for (BlockPos p : fac) {
			if (stack.isEmpty()) return;
			var st = level.getBlockState(p);
			// 物资产出位置仅限箱子与木桶
			if (!(st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
					&& !(st.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock)) continue;
			// 归属过滤：上锁（敌方所有）的容器不接收产出
			GameStateManager.Team owner = containerOwners.get(p);
			if (owner != null && owner != z.owner) continue;
			var be = level.getBlockEntity(p);
			if (be instanceof net.minecraft.world.Container c) {
				inject(c, stack);
			}
		}
	}

	/** 简单注入：先合并再空槽（自测/运行通用） */
	public static void inject(net.minecraft.world.Container c, ItemStack stack) {
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
				int space = Math.min(c.getMaxStackSize(), s.getMaxStackSize()) - s.getCount();
				if (space > 0) {
					int move = Math.min(space, stack.getCount());
					s.grow(move);
					stack.shrink(move);
				}
			}
		}
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			if (c.getItem(i).isEmpty()) {
				ItemStack put = stack.copy();
				put.setCount(stack.getCount());
				c.setItem(i, put);
				stack.setCount(0);
			}
		}
	}

	private void spawnWildlife(ServerLevel level, ChunkPos cp, Random rnd) {
		CivilizationProductionConfig.WildlifeCfg wc = prodCfg.wildlife;
		AABB box = zoneBox(cp);
		int cap = Math.max(0, wc.animalCap);
		int count = level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, box).size();
		if (count >= cap) return;
		int n = Math.min(Math.max(0, wc.animalPerInterval), cap - count);
		@SuppressWarnings("unchecked")
		net.minecraft.world.entity.EntityType<net.minecraft.world.entity.Entity>[] types =
				new net.minecraft.world.entity.EntityType[]{
						entityType("cow"), entityType("pig"), entityType("sheep"), entityType("chicken")};
		for (int i = 0; i < n; i++) {
			BlockPos p = randomGround(level, cp, rnd);
			if (p == null) continue;
			var ent = types[rnd.nextInt(types.length)].create(level, EntitySpawnReason.EVENT);
			if (ent != null) {
				prepareSpawn(level, p, ent, 0); // 补原版初始化（幼崽概率/属性）
				ent.absSnapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
				level.addFreshEntity(ent);
				managedMobs.put(ent.getUUID(), cp); // 登记所属区块（禁止跨区块）
			}
		}
		// 概率村民（区域内无村民时；概率由产出配置决定）
		if (wc.villagerChancePercent > 0 && !hasVillager(level, cp)
				&& rnd.nextInt(100) < wc.villagerChancePercent) {
			BlockPos p = randomGround(level, cp, rnd);
			if (p != null) {
				var v = entityType("villager").create(level, EntitySpawnReason.EVENT);
				if (v != null) {
					prepareSpawn(level, p, v, 0); // 补 finalizeSpawn——否则村民无职业/外观异常
					v.absSnapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
					level.addFreshEntity(v);
					managedMobs.put(v.getUUID(), cp);
				}
			}
		}
	}

	private void spawnHostile(ServerLevel level, ChunkPos cp, CivilizationManager.ZoneType zt,
			net.minecraft.world.entity.EntityType<?> type, int n) {
		// 每区块生成生物上限（本系统生成的全部生物；上限由产出配置 spawnCap 决定，默认 12）
		int cap = prodCfg.zoneSpawnCap(zt);
		if (level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, zoneBox(cp)).size() >= cap) return;
		Random rnd = new Random();
		// 配置覆盖生命值（spawnMaxHealth，0 = 原版默认）
		double maxHp = prodCfg.zoneSpawnMaxHealth(zt);
		for (int i = 0; i < n; i++) {
			BlockPos p = randomGround(level, cp, rnd);
			if (p == null) continue;
			var ent = type.create(level, EntitySpawnReason.EVENT);
			if (ent != null) {
				prepareSpawn(level, p, ent, maxHp);
				ent.absSnapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
				level.addFreshEntity(ent);
				managedMobs.put(ent.getUUID(), cp); // 登记所属区块（禁止跨区块）
			}
		}
	}

	/**
	 * 生成后补原版初始化（26.2：EntityType.create 不调用 finalizeSpawn——
	 * 此前直接 addFreshEntity 的后果：岩浆怪尺寸停在默认 1 却保持 Monster 注册属性
	 * （血量 20 而非小型 size²=1），且其战利品表要求 size≥2 才掉岩浆奶油 → "小型岩浆怪
	 * 血量异常高且无掉落"；凋灵骷髅/村民不获得默认装备/职业）。
	 * 处理：cube 类固定 setSize(1, true)（完整应用小型血量/攻击/护甲/速度），
	 * 其余 Mob 调用 finalizeSpawn 补装备与属性；spawnMaxHealth>0 时再覆盖最大生命值。
	 * 另：小型岩浆怪（size=1）不满足原版战利品表 size≥2 条件 → 覆盖为自定义表
	 * survival-contract:civilization/zone_mob（岩浆奶油 0-1 + 抢夺加成，必掉落修复）。
	 * public 供自测直接调用（自测环境 arena 无玩家、区块无 TICKING 状态，addFreshEntity
	 * 后实体不被 startTracking、getEntitiesOfClass 查不到——只能入世界前验证）。
	 */
	public static void prepareSpawn(ServerLevel level, BlockPos p,
			net.minecraft.world.entity.Entity ent, double maxHp) {
		if (ent instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube) {
			cube.setSize(1, true);
			if (cube instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube) {
				((com.survivalcontract.mixin.MobAccessor) (Object) cube).sc$setLootTable(
						java.util.Optional.of(net.minecraft.resources.ResourceKey.create(
								net.minecraft.core.registries.Registries.LOOT_TABLE,
								net.minecraft.resources.Identifier.fromNamespaceAndPath(
										"survival-contract", "civilization/zone_mob"))));
			}
		} else if (ent instanceof net.minecraft.world.entity.Mob mob) {
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
		}
		if (maxHp > 0 && ent instanceof net.minecraft.world.entity.LivingEntity living) {
			var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			if (attr != null) {
				attr.setBaseValue(maxHp);
				living.setHealth((float) Math.max(1.0, maxHp));
			}
		}
	}

	private AABB zoneBox(ChunkPos cp) {
		return new AABB(cp.getMinBlockX(), -60, cp.getMinBlockZ(),
				cp.getMinBlockX() + 16, 320, cp.getMinBlockZ() + 16);
	}

	private boolean hasVillager(ServerLevel level, ChunkPos cp) {
		return !level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, zoneBox(cp)).isEmpty();
	}

	private BlockPos randomGround(ServerLevel level, ChunkPos cp, Random rnd) {
		int x = cp.getMinBlockX() + rnd.nextInt(16);
		int z = cp.getMinBlockZ() + rnd.nextInt(16);
		int y = surfaceY(level, x, z);
		return new BlockPos(x, y, z);
	}

	// ==================== 回收（大本营猪灵） ====================
	/** 右键回收猪灵 → 打开本队回收箱（放入价格表物品即结算积分） */
	public void openRecycle(ServerPlayer player, UUID piglinId) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
		BlockPos pigPos = piglins.get(piglinId);
		if (pigPos == null) return;
		ChunkPos cp = ChunkPos.containing(pigPos);
		Zone z = zones.get(cp);
		if (z == null || z.owner != gsm.getPlayerTeam(player.getUUID())) {
			player.sendSystemMessage(Component.translatable("survival-contract.msg.civ_recycle_only_own"));
			return;
		}
		GameStateManager.Team team = z.owner;
			recycleOpeners.put(team, player.getUUID()); // 记录提交者：回收积分全额记给开启回收箱的玩家（第六十九批）
			SimpleContainer box = recycleBoxes.computeIfAbsent(team, k -> newRecycleBox(team));
			boolean red = team == GameStateManager.Team.RED;
			player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, box, 1),
				Component.translatable(red ? "survival-contract.msg.civ_recycle_box_red"
						: "survival-contract.msg.civ_recycle_box_blue")));
	}

	/** 回收箱工厂：放入价格表物品即触发结算（setup 与自测共用，保证回调一致） */
	private SimpleContainer newRecycleBox(GameStateManager.Team team) {
		return new SimpleContainer(9) {
			@Override
			public void setChanged() {
				super.setChanged();
				settleRecycle(team, (SimpleContainer) this);
			}
		};
	}

	/** 回收结算：扫描价格表物品 → 加分 → 清空。
	 *  第六十九批修复计分：按价格表逐个累计（一个圆石 = 1 分），全额记给当前开启回收箱的
	 *  提交者——原实现"全队平分 + 四舍五入"会把得分按人数摊薄（提交 9 个只得 2~3 分）。 */
	private void settleRecycle(GameStateManager.Team team, SimpleContainer box) {
		if (settlingRecycle) return;
		settlingRecycle = true;
		try {
			GameStateManager gsm = GameStateManager.getInstance();
			double pts = 0;
			for (int i = 0; i < box.getContainerSize(); i++) {
				ItemStack s = box.getItem(i);
				if (s.isEmpty()) continue;
				double price = priceOf(s.getItem());
				if (price <= 0) continue;
				pts += price * s.getCount();
				box.removeItem(i, s.getCount());
			}
			SurvivalContract.LOGGER.info("[Civilization] Recycle settle: team={} pts={}", team, pts);
			if (pts > 0) {
				int add = (int) Math.round(pts);
				UUID opener = recycleOpeners.get(team);
				if (opener != null) {
					gsm.addScore(opener, add); // 全额给提交者
				} else {
					// 兜底：无开启者记录（理论不可达）→ 全队平分
					var members = gsm.getTeamMembers(team);
					if (!members.isEmpty()) {
						int share = Math.max(1, (int) Math.round(pts / members.size()));
						for (UUID id : members) gsm.addScore(id, share);
					}
				}
			}
		} finally {
			settlingRecycle = false;
		}
	}

	private boolean settlingRecycle = false;
	/** 队伍 → 当前开启回收箱的提交者（回收积分全额记给该玩家——第六十九批） */
	private final Map<GameStateManager.Team, UUID> recycleOpeners = new HashMap<>();

	// ==================== 基地商人（第五十一批） ====================

	/** 基地商人：红蓝大本营中心区块地表各生成一个固定商人（NoAI 不能行动 + 永久抗性提升 5 +
	 *  persistent 不消失，站在地表不悬空）。商品栏 0 号格 = 本队颜色羊毛（动态积分价：初始 80，
	 *  每买一个 +4，上限 320，按队伍计价——值来自 civilization-trade.json），1-5 号格 = 商品
	 *  （默认 1 附魔之瓶（30 积分不涨价）+ 2 随机武器装备 + 1 随机药水——第六十九批取消附魔书
	 *  售卖，每局开始后固定），右键打开积分兑换界面 */
	private void spawnBaseMerchants(ServerLevel level) {
		merchantIds.clear();
		merchantPos.clear();
		merchantRefs.clear();
		merchantGuis.clear();
		merchantOffers.clear();
		woolCosts.clear();
		List<MerchantOffer> goods = rollGoodsOffers();
		CivTradeConfig.MerchantCfg mc = tradeCfg.merchant;
		for (GameStateManager.Team team : new GameStateManager.Team[]{
				GameStateManager.Team.RED, GameStateManager.Team.BLUE}) {
			ChunkPos hq = hqCenter.get(team);
			if (hq == null) continue;
			// 商人站在大本营中心区块地表（x-2 偏移：避开中心染色玻璃基座/盔甲架与 +2 侧的猪灵物资桶）
			int mx = hq.getMinBlockX() + 6, mz = hq.getMinBlockZ() + 8;
			int my = surfaceY(level, mx, mz);
			net.minecraft.world.entity.npc.villager.Villager v =
					new net.minecraft.world.entity.npc.villager.Villager(entityType("villager"), level);
			// 直接站上地表高度（NoAI 商人无 AI 不受重力落块——原 +1.0 悬空一格靠重力归位，
			// 新世界首局同 tick 检查时会撞上未落地的瞬时态）
			v.absSnapTo(mx + 0.5, my, mz + 0.5, 90, 0);
			v.setNoAi(true);            // 不能行动
			v.setPersistenceRequired(); // 不消失
			v.setCustomName(Component.translatable(team == GameStateManager.Team.RED
					? "survival-contract.msg.civ_merchant_red" : "survival-contract.msg.civ_merchant_blue"));
			v.setCustomNameVisible(true);
			v.addTag("civil_merchant_" + (team == GameStateManager.Team.RED ? "RED" : "BLUE"));
			// 永久抗性提升 5（amplifier=4）：与回收猪灵同款，防止被击杀
			v.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.RESISTANCE,
					net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 4, true, false));
			level.addFreshEntity(v);
			merchantIds.put(team, v.getUUID());
			merchantRefs.put(team, v);
			merchantPos.put(team, new BlockPos(mx, my, mz));
			// 只读展示容器：canPlaceItem=false 防止 shift 快速移动把玩家物品塞进兑换界面
			SimpleContainer gui = new SimpleContainer(9) {
				@Override
				public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
			};
			gui.setItem(0, woolDisplay(team)); // 0 号格 = 本队羊毛（动态价）
			for (int i = 0; i < goods.size() && i < 8; i++) {
				gui.setItem(i + 1, goods.get(i).display().copy());
			}
			merchantGuis.put(team, gui);
			merchantOffers.put(team, goods);
			woolCosts.put(team, Math.max(0, Math.min(mc.woolInitialCost, mc.woolMaxCost)));
		}
	}

	/** 羊毛商品展示：本队颜色羊毛 + 当前动态价标注（每次购买后刷新 0 号格，打开中的界面自动同步） */
	private ItemStack woolDisplay(GameStateManager.Team team) {
		int cost = woolCosts.getOrDefault(team, tradeCfg.merchant.woolInitialCost);
		ItemStack wool = woolStack(team, 1);
		wool.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					Component.translatable(team == GameStateManager.Team.RED
							? "survival-contract.msg.civ_wool_display_red"
							: "survival-contract.msg.civ_wool_display_blue", cost));
		return wool;
	}

	/** 该 UUID 是否为大本营回收猪灵（傀儡索敌过滤——第五十一批） */
	public boolean isRecyclePiglin(UUID id) {
		return piglins.containsKey(id);
	}

	/** 掷出本局基地商人商品（配置 civilization-trade.json merchant.goods：type 支持 随机附魔书/
	 *  随机武器装备/随机药水/具体物品 id（可省略 minecraft: 前缀），count = 每次兑换数量，
	 *  cost = 兑换积分；默认 = 1 附魔之瓶 + 2 武器装备 + 1 药水（第六十九批取消附魔书售卖，
	 *  改售附魔之瓶 30 积分不涨价）；每局开始后掷定，红蓝两队商品一致） */
	private List<MerchantOffer> rollGoodsOffers() {
		List<MerchantOffer> offers = new ArrayList<>();
		Random rnd = new Random();
		var enchantLookup = serverRef != null
				? serverRef.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				: null;
		// 附魔书池：洗牌后顺序取——多条附魔书商品不重复（池内附魔最高等级均 ≥3，等级 1..3）
		List<net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment>> bookPicks = new ArrayList<>(List.of(
				net.minecraft.world.item.enchantment.Enchantments.SHARPNESS,
				net.minecraft.world.item.enchantment.Enchantments.PROTECTION,
				net.minecraft.world.item.enchantment.Enchantments.POWER,
				net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,
				net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY,
				net.minecraft.world.item.enchantment.Enchantments.FORTUNE,
				net.minecraft.world.item.enchantment.Enchantments.LOOTING,
				net.minecraft.world.item.enchantment.Enchantments.FEATHER_FALLING,
				net.minecraft.world.item.enchantment.Enchantments.PROJECTILE_PROTECTION,
				net.minecraft.world.item.enchantment.Enchantments.BLAST_PROTECTION,
				net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION,
				net.minecraft.world.item.enchantment.Enchantments.THORNS,
				net.minecraft.world.item.enchantment.Enchantments.DEPTH_STRIDER,
				net.minecraft.world.item.enchantment.Enchantments.SMITE,
				net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK,
				net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT));
		// 武器装备池：洗牌后顺序取（不重复），附 1 条随机对应类别附魔
		List<net.minecraft.world.item.Item> gearPicks = new ArrayList<>(List.of(
				Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
				Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.BOW, Items.CROSSBOW,
				Items.SHIELD, Items.MACE));
		java.util.Collections.shuffle(gearPicks, rnd);
		// 药水池
		List<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>> potionPool = List.of(
				net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE,
				net.minecraft.world.item.alchemy.Potions.STRONG_HEALING,
				net.minecraft.world.item.alchemy.Potions.SWIFTNESS,
				net.minecraft.world.item.alchemy.Potions.STRENGTH,
				net.minecraft.world.item.alchemy.Potions.REGENERATION,
				net.minecraft.world.item.alchemy.Potions.NIGHT_VISION,
				net.minecraft.world.item.alchemy.Potions.WATER_BREATHING,
				net.minecraft.world.item.alchemy.Potions.SLOW_FALLING,
				net.minecraft.world.item.alchemy.Potions.INVISIBILITY);
		if (tradeCfg.merchant.goods == null) return offers;
		int bookIdx = 0, gearIdx = 0;
		for (CivTradeConfig.GoodCfg g : tradeCfg.merchant.goods) {
			if (g == null || g.type == null || g.type.isBlank()) continue;
			int count = Math.max(1, g.count);
			int cost = Math.max(0, g.cost);
			String t = g.type.trim();
			ItemStack product = null;
			String category = null;
			switch (t) {
				case "随机附魔书" -> {
					if (!bookPicks.isEmpty()) {
						ItemStack book = new ItemStack(Items.ENCHANTED_BOOK, count);
						if (enchantLookup != null) {
							int level = 1 + rnd.nextInt(3);
							try {
								var mut = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
										net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
								mut.set(enchantLookup.getOrThrow(bookPicks.get(bookIdx++ % bookPicks.size())), level);
								book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, mut.toImmutable());
							} catch (Exception e) {
								SurvivalContract.LOGGER.warn("[Civilization] 商人附魔书生成失败: {}", e.toString());
							}
						}
						product = book;
						category = "附魔书";
					}
				}
				case "随机武器装备" -> {
					if (!gearPicks.isEmpty()) {
						net.minecraft.world.item.Item gp = gearPicks.get(gearIdx++ % gearPicks.size());
						ItemStack gear = new ItemStack(gp, count);
						if (enchantLookup != null) {
						try {
							net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key;
							int max;
							boolean isArmor = gp == Items.DIAMOND_HELMET || gp == Items.DIAMOND_CHESTPLATE
									|| gp == Items.DIAMOND_LEGGINGS || gp == Items.DIAMOND_BOOTS;
							if (isArmor) {
								key = net.minecraft.world.item.enchantment.Enchantments.PROTECTION;
								max = 4;
							} else if (gp == Items.BOW || gp == Items.CROSSBOW) {
								key = net.minecraft.world.item.enchantment.Enchantments.POWER;
								max = 5;
							} else if (gp == Items.SHIELD) {
								key = net.minecraft.world.item.enchantment.Enchantments.UNBREAKING;
								max = 3;
							} else {
								key = net.minecraft.world.item.enchantment.Enchantments.SHARPNESS;
								max = 5;
							}
							var mut = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
									gear.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
											net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
							mut.set(enchantLookup.getOrThrow(key), 1 + rnd.nextInt(max));
							gear.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, mut.toImmutable());
						} catch (Exception e) {
							SurvivalContract.LOGGER.warn("[Civilization] 商人装备附魔失败: {}", e.toString());
						}
						}
						product = gear;
						category = "武器装备";
					}
				}
				case "随机药水" -> {
					product = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
							Items.POTION, potionPool.get(rnd.nextInt(potionPool.size())));
					if (count > 1) product.setCount(count);
					category = "药水";
				}
				case "回城卷轴" -> {
					product = createReturnScroll();
					if (count > 1) product.setCount(count);
					category = "回城卷轴";
				}
				default -> {
					// 具体物品 id（可省略 minecraft: 前缀）
					net.minecraft.world.item.Item it = CivTradeConfig.item(t, null);
					if (it != null) {
						product = new ItemStack(it, count);
						category = product.getHoverName().getString();
					} else {
						SurvivalContract.LOGGER.warn("[Civilization] 基地商人货物类型无效: {}（跳过）", t);
					}
				}
			}
			if (product == null || category == null) continue;
			offers.add(new MerchantOffer(displayStack(product, category, cost), product, cost, category));
		}
		return offers;
	}

	/** 商品展示物品：本体 + 类别与价格标注名（兑换到手的是无标注本体） */
	private static ItemStack displayStack(ItemStack product, String category, int cost) {
		ItemStack display = product.copy();
		display.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.translatable("survival-contract.msg.civ_item_display",
						categoryComponent(category), cost));
		return display;
	}

	/** 商品类别本地化：内置类别走语言键；具体物品 id 的类别已是本地化显示名，原样使用 */
	private static Component categoryComponent(String category) {
		return switch (category) {
			case "附魔书" -> Component.translatable("survival-contract.msg.civ_cat.book");
			case "武器装备" -> Component.translatable("survival-contract.msg.civ_cat.gear");
			case "药水" -> Component.translatable("survival-contract.msg.civ_cat.potion");
			case "回城卷轴" -> Component.translatable("survival-contract.msg.civ_cat.scroll");
			default -> Component.literal(category);
		};
	}

	/** 右键基地商人 → 打开该队积分兑换界面 */
	public void openMerchant(ServerPlayer player, GameStateManager.Team team) {
		SimpleContainer gui = merchantGuis.get(team);
		if (gui == null) return;
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, gui, 1),
				Component.translatable(team == GameStateManager.Team.RED
						? "survival-contract.msg.civ_merchant_gui_red"
						: "survival-contract.msg.civ_merchant_gui_blue")));
	}

	/** 菜单点击拦截入口（doClick mixin 调用）：命中基地商人兑换界面 → 兑换/提示并取消原点击。
	 *  返回 true = 点击已被消费。仅服务端判定（客户端返回 false，瞬时预测由容器同步自动纠正）。 */
	public static boolean handleMerchantGuiClick(net.minecraft.world.inventory.AbstractContainerMenu menu,
	                                             net.minecraft.world.entity.player.Player player, int slotIndex) {
		if (!(player instanceof ServerPlayer sp) || player.level().isClientSide()) return false;
		CivilizationManager m = INSTANCE;
		if (!(menu instanceof ChestMenu cm)) return false;
		if (slotIndex < 0 || slotIndex >= cm.slots.size()) return false;
		for (var e : m.merchantGuis.entrySet()) {
			if (cm.getSlot(0).container == e.getValue()) return m.buyFromMerchant(sp, e.getKey(), slotIndex);
		}
		return false;
	}

	/** 兑换（public 供 doClick mixin 与自测）：0 号格 = 本队羊毛（动态价按队伍计价：初始 80，
	 *  每买一个 +4，上限 320——封顶后价格不再上涨）；1-5 号格 = 商品（扣积分 → 发放本体）；
	 *  积分不足拒绝。点击一律取消（返回 true） */
	public boolean buyFromMerchant(ServerPlayer sp, GameStateManager.Team team, int slotIndex) {
		if (slotIndex < 0 || slotIndex > 8) return false;
		SimpleContainer gui = merchantGuis.get(team);
		if (gui == null) return false;
		GameStateManager gsm = GameStateManager.getInstance();
		if (slotIndex == 0) {
			CivTradeConfig.MerchantCfg mc = tradeCfg.merchant;
			int cost = woolCosts.getOrDefault(team, mc.woolInitialCost);
			int score = gsm.getScore(sp.getUUID());
			if (score < cost) {
				sp.sendSystemMessage(Component.translatable(
						"survival-contract.msg.civ_not_enough_points", cost, score));
				return true;
			}
			gsm.addScore(sp.getUUID(), -cost);
			// 动态涨价（封顶）+ 刷新 0 号格价格标注（打开中的界面由容器同步自动更新）
			woolCosts.put(team, Math.min(cost + mc.woolCostStep, mc.woolMaxCost));
			gui.setItem(0, woolDisplay(team));
			ItemStack wool = woolStack(team, 1);
			if (!sp.getInventory().add(wool) && !wool.isEmpty()) com.survivalcontract.McCompat.drop(sp, wool, false);
			sp.sendSystemMessage(Component.translatable(
					"survival-contract.msg.civ_bought_wool", cost, gsm.getScore(sp.getUUID())));
			return true;
		}
		List<MerchantOffer> goods = merchantOffers.getOrDefault(team, List.of());
		int idx = slotIndex - 1;
		if (idx >= goods.size()) {
			sp.sendSystemMessage(Component.translatable("survival-contract.msg.civ_click_slot"));
			return true;
		}
		MerchantOffer offer = goods.get(idx);
		int score = gsm.getScore(sp.getUUID());
		if (score < offer.cost()) {
			sp.sendSystemMessage(Component.translatable(
					"survival-contract.msg.civ_not_enough_points", offer.cost(), score));
			return true;
		}
		gsm.addScore(sp.getUUID(), -offer.cost());
		ItemStack product = offer.product().copy();
		if (!sp.getInventory().add(product) && !product.isEmpty()) com.survivalcontract.McCompat.drop(sp, product, false);
		sp.sendSystemMessage(Component.translatable("survival-contract.msg.civ_bought_item",
				offer.product().getHoverName().getString(), offer.category(), offer.cost(),
				gsm.getScore(sp.getUUID())));
		return true;
	}

	// ==================== 回城卷轴（第七十八批） ====================
	/** 回城卷轴物品名（本体为纸 + 自定义名标记，沿用神谕纸符/任务刷新券范式） */
	public static final String RETURN_SCROLL_ITEM_NAME = "§d回城卷轴";

	/** 构造一枚回城卷轴（纸 + 自定义名） */
	public static ItemStack createReturnScroll() {
		ItemStack scroll = new ItemStack(Items.PAPER);
		scroll.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				Component.literal(RETURN_SCROLL_ITEM_NAME));
		return scroll;
	}

	/** 是否为回城卷轴（纸 + 自定义名含"回城卷轴"） */
	public static boolean isReturnScroll(ItemStack stack) {
		if (stack == null || !stack.is(Items.PAPER)) return false;
		var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		return name != null && name.getString().contains("回城卷轴");
	}

	/** 右键使用回城卷轴：立刻传送到本队队伍出生点并消耗 1 枚。成功返回 true。
	 *  仅文明时代 PLAYING 阶段、存活非旁观者可用（其余情况返回 false，不消耗）。 */
	public boolean useReturnScroll(ServerPlayer sp, net.minecraft.world.InteractionHand hand) {
		if (sp == null) return false;
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return false;
		if (!gsm.isCivilization()) return false;
		if (!sp.isAlive() || sp.isSpectator()) return false;
		MinecraftServer server = sp.level().getServer();
		if (server == null) return false;
		ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (arena == null) arena = (ServerLevel) sp.level(); // 兜底（正常不触发）
		BlockPos spawn = gsm.getRespawnPoint(sp.getUUID());
		sp.teleportTo(arena, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
				Set.of(), sp.getYRot(), sp.getXRot(), false);
		ItemStack held = sp.getItemInHand(hand);
		if (isReturnScroll(held)) held.shrink(1);
		sp.sendSystemMessage(Component.translatable("survival-contract.msg.civ_return_scroll_used"));
		return true;
	}

	// ==================== 大本营盔甲架 ====================
	/** 攻击大本营盔甲架：扣血（每次命中 5 点），归零判负。返回是否拦截了原版逻辑 */
	public boolean onHqStandAttacked(ServerPlayer attacker, ArmorStand stand) {
		GameStateManager gsm = GameStateManager.getInstance();
		GameStateManager.Team team = null;
		for (var e : hqStands.entrySet()) {
			if (!e.getKey().equals(stand.getUUID())) continue;
			ChunkPos cp = ChunkPos.containing(e.getValue());
			Zone z = zones.get(cp);
			team = z != null ? z.owner : null;
		}
		if (team == null) return false;
		int hp = hqHp.getOrDefault(team, HQ_MAX_HP) - 5;
		if (attacker != null && gsm.getPlayerTeam(attacker.getUUID()) == team) {
			attacker.sendSystemMessage(Component.translatable("survival-contract.msg.civ_no_attack_own_hq"));
			return true;
		}
		// 第五十二批：每 4s 最多受到一次伤害（80 tick 冷却，冷却期命中不扣血不触发警报）
		long nowTick = serverRef != null ? serverRef.getTickCount() : 0;
		Long lastHit = hqStandLastHit.get(stand.getUUID());
		if (lastHit != null && nowTick - lastHit < HQ_STAND_DAMAGE_INTERVAL_TICKS) {
			if (attacker != null) {
				attacker.sendSystemMessage(Component.translatable("survival-contract.msg.civ_hq_cooldown"));
			}
			return true;
		}
		hqStandLastHit.put(stand.getUUID(), nowTick);
		// 敌方进攻 → 我方全员屏幕中央醒目警报（持续 2 秒；每队每 5 秒最多一次，第五十批）
		tryWarnHqUnderAttack(team);
		if (hp <= 0) {
			// 盔甲架被击杀 → 队伍阵亡
			forfeit(serverRef, team, gsm.getPlayerTeam(attacker.getUUID()));
			hqHp.put(team, HQ_MAX_HP);
		} else {
			hqHp.put(team, hp);
			// 受击反馈：暴击粒子 + 受击音效（盔甲架无敌无法产生原版受伤动画，手动模拟）
			BlockPos standPos = stand.blockPosition();
			ServerLevel lvl = serverRef.overworld();
			if (stand.level() instanceof ServerLevel sl) lvl = sl;
			lvl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
					standPos.getX() + 0.5, standPos.getY() + 1.2, standPos.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.1);
			lvl.playSound(null, standPos, net.minecraft.sounds.SoundEvents.ARMOR_STAND_HIT,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
			for (ServerPlayer p : serverRef.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.translatable("survival-contract.msg.civ_hq_hit",
						team == GameStateManager.Team.RED
								? Component.translatable("survival-contract.gui.team.red")
								: Component.translatable("survival-contract.gui.team.blue"),
						hp, HQ_MAX_HP));
			}
		}
		return true;
	}

	/** 敌方攻击我方大本营核心 → 我方全员屏幕中央醒目警报（持续 2 秒 + 淡出；每队每 5 秒最多触发一次）。
	 *  返回是否实际触发（冷却外），自测断言用 */
	public boolean tryWarnHqUnderAttack(GameStateManager.Team team) {
		if (team == null || team == GameStateManager.Team.NONE || serverRef == null) return false;
		long now = serverRef.getTickCount();
		Long last = hqWarnLastTick.get(team);
		if (last != null && now - last < HQ_WARN_INTERVAL_TICKS) return false;
		hqWarnLastTick.put(team, now);
		Component title = Component.translatable("survival-contract.msg.civ_hq_under_attack",
				team == GameStateManager.Team.RED
						? Component.translatable("survival-contract.gui.team.red_plain")
						: Component.translatable("survival-contract.gui.team.blue_plain"));
		for (UUID id : GameStateManager.getInstance().getTeamMembers(team)) {
			ServerPlayer p = serverRef.getPlayerList().getPlayer(id);
			if (p == null || p.connection == null) continue;
			try {
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 40, 10));
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
			} catch (Exception ignored) {} // 连接已断开等异常不影响流程
		}
		return true;
	}

	/** 自测：直接设置队伍上次警报 tick（模拟冷却流逝） */
	public void hqWarnLastTickForTest(GameStateManager.Team team, long lastTick) {
		hqWarnLastTick.put(team, lastTick);
	}

	/** 自测：直接设置大本营盔甲架上次受击 tick（模拟 4s 限伤冷却流逝，第五十二批） */
	public void hqStandLastHitForTest(UUID standId, long lastTick) {
		hqStandLastHit.put(standId, lastTick);
	}

	/** 自测：指定队伍的大本营盔甲架实体引用（第五十二批；假人环境不能按 UUID 从 level 查） */
	public ArmorStand hqStandForTest(GameStateManager.Team team) {
		return hqStandRefs.get(team);
	}

	/** 判负：loser 大本营被攻破 → 立即结束，winners = 对方队伍 */
	private void forfeit(MinecraftServer server, GameStateManager.Team loser, GameStateManager.Team winner) {
		if (server == null) return;
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED) return;
		List<UUID> winners = new ArrayList<>(gsm.getTeamMembers(winner));
		gsm.setForcedWinners(winners);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("survival-contract.msg.civ_hq_broken",
					loser == GameStateManager.Team.RED
							? Component.translatable("survival-contract.gui.team.red")
							: Component.translatable("survival-contract.gui.team.blue")));
		}
		gsm.endGame(null);
	}

	// ==================== 设施登记 ====================
	/** 玩家在已占领区块放置功能方块/容器 → 登记位置（注入/前置校验用，验证制无需清理） */
	public void registerFacility(BlockPos pos) {
		ChunkPos cp = ChunkPos.containing(pos);
		if (!zones.containsKey(cp)) return;
		facilities.computeIfAbsent(cp, k -> new HashSet<>()).add(pos.immutable());
	}

	/** 实体是否为资源区悬浮图标（攻击/交互拦截判定；26.2 无 hasTag，按 UUID 查 zones） */
	public boolean isFloatStand(net.minecraft.world.entity.Entity entity) {
		if (entity == null) return false;
		UUID id = entity.getUUID();
		for (Zone z : zones.values()) {
			if (id.equals(z.floatStandId)) return true;
		}
		return false;
	}

	// ==================== 事件注册（mod 初始化调用一次） ====================
	public void registerEvents() {
		// 死亡羊毛保留：重生时返还死亡前暂存的占领羊毛
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
				(oldPlayer, newPlayer, alive) -> onRespawn(newPlayer, alive));
		// 无主/敌方区块的功能方块锁定（破坏拦截）+ 大本营羊毛不可破坏
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(world, player, pos, state, blockEntity) -> {
					if (!(player instanceof ServerPlayer sp)) return true;
					GameStateManager gsm = GameStateManager.getInstance();
					if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return true;
					if (isAnyTeamWool(state)) {
						Zone z = zones.get(ChunkPos.containing(pos));
						// 大本营羊毛无法破坏（大本营只能通过击破核心盔甲架攻陷）
						if (z != null && z.type == ZoneType.HQ && z.owner != null) {
							sp.sendSystemMessage(Component.translatable("survival-contract.msg.civ_hq_wool_unbreakable"));
							return false;
						}
						return true;
					}
					if (isFunctionalBlock(state) && !canInteract(sp, pos, state)) {
						sp.sendSystemMessage(Component.translatable(
								"survival-contract.msg.chunk_locked"));
						return false;
					}
					// 大本营设施（信标光柱 / 物资桶）不可破坏
					if (beaconProtected.contains(pos.immutable())) {
						sp.sendSystemMessage(Component.translatable("survival-contract.msg.civ_hq_facility_unbreakable"));
						return false;
					}
					return true;
				});
		// 文明时代：不自然生成史莱姆（超平坦地下 slime chunk 会刷——加载即清除；岩浆怪不受影响）
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof net.minecraft.world.entity.monster.cubemob.Slime)
					|| entity instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube) return;
			GameStateManager gsm = GameStateManager.getInstance();
			if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
			if (world != com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(
					entity.level().getServer())) return;
			entity.discard();
		});
		// 文明时代：僵尸马（守护骑兵坐骑 / 遭遇事件骑兵）加载即纳入火焰抑制，白天不自燃
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register(
				(entity, world) -> trackCivZombieHorse(entity));
		// **占领羊毛**被破坏 → 区块回归无主（同区块多余的同色羊毛被破坏不影响归属）；
		// 箱子/木桶被破坏 → 清除归属记录
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
				(world, player, pos, state, blockEntity) -> {
					if (!(player instanceof ServerPlayer)) return;
					if (world.isClientSide()) return;
					GameStateManager gsm = GameStateManager.getInstance();
					if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
					// 容器破坏 → 清除归属记录（重新放置时重新登记）
					if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
							|| state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock) {
						containerOwners.remove(pos.immutable());
					}
					// 羊毛破坏（含多余羊毛）→ 清除每区块羊毛索引（索引键是区块，仅破坏的正是被索引的那块羊毛时清除）
					if (isAnyTeamWool(state)) {
						ChunkPos cpKey = ChunkPos.containing(pos);
						BlockPos indexed = chunkWool.get(cpKey);
						if (indexed != null && indexed.equals(pos.immutable())) {
							chunkWool.remove(cpKey);
						}
					}
					if (!isAnyTeamWool(state)) return;
					ChunkPos cp = ChunkPos.containing(pos);
					Zone z = zones.get(cp);
					if (z == null) return;
					boolean isClaimWool = z.woolPos != null && z.woolPos.equals(pos);
					var pc = pendingClaims.get(cp);
					boolean isPendingWool = pc != null && pc.woolPos().equals(pos);
					if (isClaimWool || isPendingWool) {
						onWoolBroken(((ServerPlayer) player).level().getServer(), (ServerLevel) world, cp);
					}
				});
		// 右键回收猪灵 → 打开本队回收箱；右键悬浮图标 → 拦截（防换装备）
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
				(player, world, hand, entity, hitResult) -> {
					if (!(player instanceof ServerPlayer sp) || world.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
					GameStateManager gsm = GameStateManager.getInstance();
					if (!gsm.isCivilization() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
						return net.minecraft.world.InteractionResult.PASS;
					}
					if (entity instanceof ArmorStand stand && isFloatStand(stand)) {
						return net.minecraft.world.InteractionResult.SUCCESS; // 悬浮图标不可交互
					}
					if (entity instanceof Piglin pig && pig.getUUID() != null && piglins.containsKey(pig.getUUID())) {
						openRecycle(sp, pig.getUUID());
						return net.minecraft.world.InteractionResult.SUCCESS;
					}
					// 右键基地商人 → 打开本队积分兑换界面（第五十一批：取代第四十九批中央商人）
					if (entity instanceof net.minecraft.world.entity.npc.villager.Villager v) {
						for (var me : merchantIds.entrySet()) {
							if (v.getUUID() != null && v.getUUID().equals(me.getValue())) {
								openMerchant(sp, me.getKey());
								return net.minecraft.world.InteractionResult.SUCCESS;
							}
						}
					}
					return net.minecraft.world.InteractionResult.PASS;
				});
	}

	// ==================== 重置 ====================
	public void reset() {
		// 清理实体（悬浮图标/盔甲架/猪灵）
		if (serverRef != null) {
			ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(serverRef);
			// 竞技场已拆除（回大厅 teardown 之后）：实体随维度一起消失，无需清理；
			// 绝不能退回原版主世界去 discard 同 UUID 的实体
			if (level != null) {
				for (var z : zones.values()) removeFloatIcon(level, z);
				for (UUID id : hqStands.keySet()) {
					var e = level.getEntity(id);
					if (e != null) e.discard();
				}
				for (UUID id : piglins.keySet()) {
					var e = level.getEntity(id);
					if (e != null) e.discard();
				}
				// 清理区块生物（本系统生成的动物/怪物/村民）
				for (UUID id : managedMobs.keySet()) {
					var e = level.getEntity(id);
					if (e != null) e.discard();
				}
				// 清理基地商人（第五十一批；假人环境查找表外走引用兜底）
				for (var ref : merchantRefs.values()) {
					if (ref != null) {
						try { ref.discard(); } catch (Exception ignored) {}
					}
				}
				for (UUID id : merchantIds.values()) {
					var e = level.getEntity(id);
					if (e != null) e.discard();
				}
				// 清理羊毛挖掘高亮实体（第五十批）+ 退出 scoreboard 发光队伍
				for (UUID id : woolGlow.values()) {
					var e = level.getEntity(id);
					if (e != null) {
						removeFromGlowTeam(e);
						e.discard();
					}
				}
			}
			}
			// 占领进度 bossbar 清理
		for (var bar : claimBars.values()) {
			bar.removeAllPlayers();
			bar.setVisible(false);
		}
		zones.clear();
		pendingClaims.clear();
		facilities.clear();
		hqHp.clear();
		hqStands.clear();
		piglins.clear();
		recycleBoxes.clear();
		managedMobs.clear();
		zoneGuardians.clear();
		claimBars.clear();
		woolChecks.clear();
		containerOwners.clear();
		chunkWool.clear();
		beaconProtected.clear();
		supplyBarrels.clear();
		barrelNextProduce.clear();
		zoneNextProduce.clear();
		zoneNextSpawn.clear();
		deathWoolCount.clear();
		hqNextProduce.clear();
		hqCenter.clear();
		hqSupplyAcc.clear();
		moveWarnAt.clear();
		bonusProduced.clear();
		woolGlow.clear();
		woolGlowActive.clear();
		hqWarnLastTick.clear();
		hqStandLastHit.clear();
		hqStandRefs.clear();
		merchantIds.clear();
		merchantPos.clear();
		merchantRefs.clear();
		merchantGuis.clear();
		merchantOffers.clear();
		woolCosts.clear();
		recyclePrices.clear();
		recycleOpeners.clear();
		tradeCfg = CivTradeConfig.get();
		settlingRecycle = false;
		// 恢复进入文明时代前的环境 gamerule（生物破坏/火蔓延/昼夜推进），避免影响其他模式
		if (serverRef != null && savedMobGriefing != null) {
			ServerLevel lvl = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(serverRef);
			// 竞技场已拆除 → 规则随维度一起消失；绝不能把保存的规则写回原版主世界
			if (lvl != null) {
				lvl.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING,
						savedMobGriefing, serverRef);
				if (savedFireSpread != null) {
					lvl.getGameRules().set(
							net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,
							savedFireSpread, serverRef);
				}
				if (savedAdvanceTime != null) {
					lvl.getGameRules().set(
							net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME,
							savedAdvanceTime, serverRef);
				}
			}
		}
		savedMobGriefing = null;
		savedFireSpread = null;
		savedAdvanceTime = null;
		serverRef = null;
	}

	// 测试辅助
	public int zoneCount() { return zones.size(); }

	public Zone zoneAt(ChunkPos cp) { return zones.get(cp); }

	/** 自测：指定队伍的基地商人实体（返回实体引用——假人环境实体不进查找表，不能按 UUID 从 level 查） */
	public net.minecraft.world.entity.Entity merchantEntity(GameStateManager.Team team) { return merchantRefs.get(team); }

	/** 自测：指定队伍基地商人商品列表（display/product/cost/category；不含 0 号格羊毛） */
	public java.util.List<MerchantOffer> merchantOffersForTest(GameStateManager.Team team) {
		return merchantOffers.getOrDefault(team, List.of());
	}

	/** 自测：指定队伍基地商人所在位置（大本营中心区块地表，x-2 偏移） */
	public BlockPos merchantPosForTest(GameStateManager.Team team) { return merchantPos.get(team); }

	/** 自测：大本营回收猪灵 UUID 列表 */
	public java.util.List<UUID> recyclePiglinIdsForTest() {
		return new java.util.ArrayList<>(piglins.keySet());
	}

	/** 自测：指定队伍羊毛当前兑换价（动态价） */
	public int woolCostForTest(GameStateManager.Team team) {
		return woolCosts.getOrDefault(team, tradeCfg.merchant.woolInitialCost);
	}

	/** 自测：手动触发单个区块的一次产出（produceZone 私有，供副产品单区上限等实测桥接）。
	 *  第六十八批：前置检测 prereqMet→blockStateAt 依赖 cachedLevel（正常由每秒 produceZones 设置），
	 *  桥接调用需自行设置并在结束时清空，否则带前置的区块（如石头区）永远检测不到方块。 */
	public void produceZoneForTest(ServerLevel level, ChunkPos cp) {
		Zone z = zones.get(cp);
		if (z == null) return;
		cachedLevel = level;
		try {
			produceZone(level, cp, z);
		} finally {
			cachedLevel = null;
		}
	}

	public boolean isPendingClaim(ChunkPos cp) { return pendingClaims.containsKey(cp); }

	public int hqHealth(GameStateManager.Team team) { return hqHp.getOrDefault(team, HQ_MAX_HP); }

	/** 自测：直接访问本队回收箱（放入价格表物品即触发 settleRecycle 加分） */
	public SimpleContainer recycleBoxForTest(GameStateManager.Team team) {
		return recycleBoxes.computeIfAbsent(team, k -> newRecycleBox(team));
	}

	/** 自测：设置回收箱提交者（回收积分全额记给该玩家——第六十九批与 openRecycle 同口径） */
	public void setRecycleOpenerForTest(GameStateManager.Team team, UUID playerId) {
		recycleOpeners.put(team, playerId);
	}
}
