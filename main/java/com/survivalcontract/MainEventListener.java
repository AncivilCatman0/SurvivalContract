package com.survivalcontract;

import com.survivalcontract.challenge.Challenge;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.dimension.LobbyManager;
import com.survivalcontract.event.RandomEventManager;
import com.survivalcontract.game.GameStateManager;
import com.survivalcontract.network.NetworkRegistry;
import com.survivalcontract.profession.ProfessionManager;
import com.survivalcontract.task.TaskManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 生存契约的所有游戏事件监听器。
 */
public class MainEventListener {

	public static final String CHALLENGE_BOOK_CUSTOM_NAME = "挑战书";

	// 延迟传送大厅队列
	private static final List<ServerPlayer> PENDING_LOBBY_TELEPORT = new ArrayList<>();

	// 游戏进行中新加入的玩家 → 旁观者观战队列（结束后随全员回大厅）
	private static final List<ServerPlayer> PENDING_SPECTATOR_JOIN = new ArrayList<>();

	// 游戏进行中以旁观者身份加入的玩家（游戏结束前锁定旁观，管理员切模式会被拉回）
	private static final Set<UUID> MID_GAME_SPECTATORS = new HashSet<>();

	// 观战者位置自愈冷却（uuid → 可再次重试传送的 tick）：防止每 tick 反复传送刷屏
	private static final Map<UUID, Long> SPECTATOR_REJOIN_COOLDOWN = new HashMap<>();

	// 分数 sync 每 2 秒
	private static int SCORE_SYNC_COUNTER = 0;

	// PLAYING 阶段完整 sync 每 5 秒
	private static int CHALLENGE_SYNC_COUNTER = 0;

	// LOBBY 阶段状态 sync 每 2 秒（每 tick 发会导致客户端 GUI 每秒重建 20 次）
	private static int LOBBY_SYNC_COUNTER = 0;

	// 任务倒计时文本 sync 每 1 秒（轻量 TaskSync）
	private static int TASK_SYNC_COUNTER = 0;

	// FINISHED 后等待多少 tick 才回大厅（5 秒 = 100 tick）
	private static int FINISHED_COUNTDOWN = -1;

	// tick 异常限流：子系统名 → 上次打印异常的服务端 tick（同类异常 200 tick 内不重复刷屏）
	private static final Map<String, Long> TICK_ERROR_LOGGED_AT = new HashMap<>();

	/**
	 * 单个子系统隔离：子系统内部抛异常只记日志，不击穿整个服务端 tick。
	 * （之前任一子系统异常都会让服务端 tick 直接抛出 → 服务器卡死/玩家被踢，整局无法继续）
	 */
	private static void tickSafely(String name, Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			logTickError(name, t);
		}
	}

	private static void logTickError(String name, Throwable t) {
		MinecraftServer server = GameStateManager.getInstance().getServer();
		long now = server == null ? 0L : server.getTickCount();
		Long last = TICK_ERROR_LOGGED_AT.get(name);
		if (last != null && now >= last && now - last < 200) return;
		TICK_ERROR_LOGGED_AT.put(name, now);
		SurvivalContract.LOGGER.error("[TickGuard] {} failed at tick {} (further identical errors suppressed for 200 ticks)",
				name, now, t);
	}

	// 已见过的 WitherBoss 实体（避免重复触发凋零山庄 WIN）
	private static final Set<UUID> seenWithers = new HashSet<>();
	// 一本万利：猪灵交易冷却（piglin UUID → 该猪灵下次可交易的游戏刻），防长按右键连刷金锭
	private static final Map<UUID, Long> PENDING_PIGLIN_DEALS = new java.util.HashMap<>();

	// ============ 板块规则状态（10-06：与神合约/久病成患/环境恶化） ============
	private static final Map<UUID, Float> regenPrevHealth = new java.util.HashMap<>();   // 无法自然回血：上一tick血量
	private static final Map<UUID, Boolean> prevOnGround = new java.util.HashMap<>();    // 脆骨症：上一tick是否在地面
	private static final Map<UUID, Long> insomniaSleepTicks = new java.util.HashMap<>(); // 失眠症：当晚累计睡眠 tick
	private static final Set<UUID> insomniaCuredTonight = new java.util.HashSet<>();     // 失眠症：当晚已通过睡眠解除
	private static final Set<UUID> insomniaApplied = new java.util.HashSet<>();          // 失眠症：当夜已施加效果
	private static final Map<UUID, Integer> hypothermiaTicks = new java.util.HashMap<>();// 失温症：寒冷环境累计秒
	private static final Map<UUID, Integer> hypothermiaAcc = new java.util.HashMap<>();  // 失温症：冻结伤害节拍
	private static final Map<UUID, Integer> heatstrokeTicks = new java.util.HashMap<>(); // 热射病：炎热环境累计秒
	private record BrittleReflect(ServerPlayer attacker, float amount) {}
	private static final List<BrittleReflect> PENDING_BRITTLE_REFLECTS = new ArrayList<>(); // 脆骨症：攻击反馈延迟结算
	private static final Set<UUID> PAIN_PROCESSING = new java.util.HashSet<>();          // 嗜痛人：重入保护
	private static final Map<UUID, net.minecraft.world.damagesource.DamageType> painLastType = new java.util.HashMap<>(); // 嗜痛人：上次伤害类型
	private static final Map<UUID, Integer> painCount = new java.util.HashMap<>();       // 嗜痛人：连续同类型次数
	private static final Set<UUID> camoSeen = new java.util.HashSet<>();                 // 迷彩：已施加隐身的实体
	private static final Set<UUID> processedBarterDrops = new java.util.HashSet<>();     // 行商：已处理的猪灵交易掉落
	private static final Set<UUID> moonClones = new java.util.HashSet<>();               // 白月/赤月：月相克隆体（不链式克隆/分裂）
	// 久病成患新增疾病状态（10-07 第三批）
	private static final Map<UUID, Integer> dustLungTicks = new java.util.HashMap<>();   // 尘肺病：负层累计秒
	private static final Map<UUID, Integer> altitudeTicks = new java.util.HashMap<>();   // 高原水肿：高海拔累计秒
	private static final Map<UUID, Boolean> decompressionUnderwater = new java.util.HashMap<>(); // 减压症：上一秒是否完全入水
	private static final Map<UUID, Integer> decompressionTicks = new java.util.HashMap<>();      // 减压症：本次入水累计秒
	// 失重：连续跳跃检测状态
	private static final Map<UUID, Boolean> zeroGWasGround = new java.util.HashMap<>();
	private static final Map<UUID, Long> zeroGLastJump = new java.util.HashMap<>();
	// 午夜凶铃：冷却与上次受害者
	private static long midnightBellNextTick = 0L;
	private static UUID midnightBellLastVictim = null;
	// 开局冻结：上一 tick 是否处于冻结期（用于检测冻结结束瞬间）
	private static boolean FREEZE_WAS_ACTIVE = false;
	// 最后一个放置凋零骷髅头的玩家（"凋零山庄" +10 分归属）
	private static UUID lastSkullPlacer = null;
	// 弓手伤害加成回环保护：正在补 1.5 倍伤害的玩家 UUID
	private static final Set<UUID> ARCHER_DAMAGE_BOOSTING = new HashSet<>();
	private static final Set<UUID> DEFENSE_PENALIZING = new HashSet<>();
	// ⑥ 箭矢弹开修复（第五十二批）：补伤目标标记——ArrowHurtRedirectMixin 据此把
	// "被规则取消的箭矢伤害"按命中成功处理（返回 true → 箭矢 discard，不再弹开+可捡回）
	public static final ThreadLocal<String> REDEALT_TARGET = new ThreadLocal<>();

	/**
	 * 补伤入口：打目标标记后重新造成伤害（标记不主动清除）。
	 * 弓手特权/嗜痛人/受到伤害增加等"取消原始伤害+重打"流程一律经由本方法，
	 * 使箭矢/三叉戟 onHitEntity 中的 hurtOrSimulate 被取消时能被 redirect mixin 识别为命中。
	 */
	static void redealHurt(net.minecraft.world.entity.LivingEntity target,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		REDEALT_TARGET.set(target.getStringUUID());
		// 26.3 伤害冷却闸清零：本方法是"取消原伤 + 按规则重打"，原伤被取消时目标未留下冷却/无敌帧；
		// 若不清闸，26.3 下"≤上次伤害"的重打会被静默忽略（26.2 无此字段，调用无副作用）
		McCompat.clearHurtCooldown(target);
		target.hurt(source, amount);
	}
	// 贫瘠铁矿随机数
	private static final java.util.Random ORE_RANDOM = new java.util.Random();

	/** 贫瘠铁矿延迟结算队列：破坏时记录，本 tick 末尾处理（兼容掉落实体生成时序） */
	private record OreRoll(ServerLevel level, BlockPos pos, java.util.UUID playerId) {}
	private static final List<OreRoll> PENDING_ORE_ROLLS = new ArrayList<>();

	// ⑦ 低血糖规则允许的食物（第五十二批：追加苹果/甜浆果/发光浆果）
	private static final Set<String> HYPOGLYCEMIA_ALLOWED_FOOD = Set.of(
			"minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:cake",
			"minecraft:baked_potato", "minecraft:bread",
			"minecraft:apple", "minecraft:sweet_berries", "minecraft:glow_berries");

	/** 低血糖规则下该食物是否允许食用（回调与自测共用） */
	public static boolean isHypoglycemiaAllowedFood(String itemId) {
		return HYPOGLYCEMIA_ALLOWED_FOOD.contains(itemId);
	}

	public static void register() {
		// 走私犯与检察官：检查室不可破坏/不可炸毁
		com.survivalcontract.smuggle.SmuggleManager.getInstance().registerEvents();
		// 文明时代：锁定拦截 / 羊毛无主化 / 回收猪灵
		com.survivalcontract.civilization.CivilizationManager.getInstance().registerEvents();
		// ============ 挖掘方块（任务进度 MINE_BLOCK + 挖铁矿概率掉落规则） ============
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && !world.isClientSide()) {
				String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
				TaskManager.getInstance().onBlockMined(sp, blockId);

				// 贫瘠铁矿：挖掘铁矿石/深层铁矿石时 50% 不掉落粗铁、10% 掉落双倍（数值固定）
				if (GameStateManager.getInstance().getCurrentPhase() == GameStateManager.GamePhase.PLAYING
						&& (blockId.equals("minecraft:iron_ore") || blockId.equals("minecraft:deepslate_iron_ore"))) {
					var minedChallenges = ChallengeManager.getInstance().getPlayerChallenges(sp.getUUID());
					if (minedChallenges.contains("iron_ore_risk")) {
						// 延迟到本 tick 末尾结算：AFTER 回调时掉落物可能尚未生成（26.2 时序）
						PENDING_ORE_ROLLS.add(new OreRoll((ServerLevel) world, pos.immutable(), sp.getUUID()));
					}
				}
			}
		});

		// ============ 玩家连接 ============
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// 大厅模式关闭（普通单人世界/服务器关配置）：玩家按原版方式进入世界，
			// 不传送大厅、不发挑战书、不做任何观战分流
			if (!com.survivalcontract.dimension.LobbyMode.isActive()) return;
			ServerPlayer player = handler.getPlayer();
			var gsm = GameStateManager.getInstance();
			// 附属玩法：加入/重连钩子（重连后重新施加冒险模式/饱和效果等）
			var addonJoin = gsm.getAddonProvider();
			if (addonJoin != null) addonJoin.onPlayerJoin(player);
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY) {
				PENDING_LOBBY_TELEPORT.add(player);
			} else if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING) {
				if (gsm.hasPlayerState(player.getUUID())) {
					// 中途掉线重连的参战者 → 回到自己的重生点继续游戏
					PENDING_PLAYING_RESPAWN.add(player);
					// 走私犯与检察官：重连立即补发价值物品清单 sync（第六十七批）——
					// 否则重进后客户端 HUD 收不到 sync，看不到物品价值
					if (gsm.isStowaway()) {
						com.survivalcontract.smuggle.SmuggleManager.getInstance().sendSync(player);
					}
				} else {
					// 游戏进行中新玩家加入 → 旁观者模式观战，游戏结束后随全员回大厅
					PENDING_SPECTATOR_JOIN.add(player);
				}
			} else if (gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED) {
				// 结束后还有人加入 → 直接回大厅
				PENDING_LOBBY_TELEPORT.add(player);
			}
		});

		// ============ 玩家重生 ============
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// 大厅模式关闭：完全不接管重生/改道（普通世界走原版逻辑）
			if (!com.survivalcontract.dimension.LobbyMode.isActive()) return;
			GameStateManager gsm = GameStateManager.getInstance();
			// 道具书死亡保留：重生返还死亡时暂存的检查发起书/指认书（无暂存则无事发生）
			gsm.returnKeptBooksOnRespawn(newPlayer, alive);
			// 走私犯与检察官：重生补发缺失的收纳袋钥匙（袋内容物在服务端，不随死亡丢失）
			com.survivalcontract.smuggle.SmuggleManager.getInstance().grantKitIfMissing(newPlayer);
			if (alive) {
				// 真正加入服务器
				if (gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY) {
					PENDING_LOBBY_TELEPORT.add(newPlayer);
				} else if (gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED) {
					PENDING_LOBBY_TELEPORT.add(newPlayer);
				}
				// PLAYING 阶段：参战者重连/重生 → 传送回固定重生点；新加入玩家 → 旁观者观战
				else if (gsm.hasPlayerState(newPlayer.getUUID())) {
					PENDING_PLAYING_RESPAWN.add(newPlayer);
				} else {
					PENDING_SPECTATOR_JOIN.add(newPlayer);
				}
			} else {
				// 死亡重生
				if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING) {
					// 神的谎言：已淘汰玩家重生 → 保持旁观者模式（不回生存）
					if (gsm.isGodsLies() && gsm.isEliminated(newPlayer.getUUID())) {
						newPlayer.setGameMode(GameType.SPECTATOR);
						PENDING_PLAYING_RESPAWN.add(newPlayer);
						newPlayer.sendSystemMessage(Component.translatable("survival-contract.msg.eliminated_respawn_spectator"));
					} else {
						newPlayer.setGameMode(GameType.SURVIVAL);
						PENDING_PLAYING_RESPAWN.add(newPlayer);
						newPlayer.sendSystemMessage(Component.translatable("survival-contract.msg.death_respawn_survival"));
					}
				} else {
					PENDING_LOBBY_TELEPORT.add(newPlayer);
				}
			}
		});

		// ============ 玩家从末地回主世界 → 结束了 目标 ============
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// 这个不能放在上面的 if-else 里，因为 AFTER_RESPAWN 也会触发末地→主世界
			if (alive) {
				GameStateManager gsm = GameStateManager.getInstance();
				if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING
					&& gsm.isEnderDragonKilled()
					&& gsm.getCurrentFinalGoal() == GameStateManager.FinalGoal.ENDER_END
					&& oldPlayer.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA_END
					&& newPlayer.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA) {
					// 末影龙已死 + 玩家从末地传送门回主世界 = "结束了"
					// 限时内完成最终目标：回归者直接 +10 分
					awardFinalGoalBonus(newPlayer.level().getServer(), newPlayer);
					gsm.endGame(GameStateManager.FinalGoal.ENDER_END);
				}
			}
		});

		// ============ 实体死亡（只负责 ENDER_DRAGON 最终目标） ============
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// 挑战书死亡不掉落：清掉尸体背包中的挑战书 + 清扫死亡点刚生成的挑战书掉落物
			//（26.2 死亡掉落物没有 owner 归属，大厅"按丢出者返还"的防丢扫描扫不回 → 必须在源头阻止掉落；
			//  玩家死亡重生/回大厅时会自动补发挑战书，这里清除不会导致丢书）
			if (entity instanceof ServerPlayer deadBook) {
				var inv = deadBook.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (isChallengeBook(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
				}
				for (ItemEntity ie : ((ServerLevel) entity.level()).getEntitiesOfClass(ItemEntity.class,
						deadBook.getBoundingBox().inflate(4),
						e -> e.isAlive() && e.getAge() <= 4 && isChallengeBook(e.getItem()))) {
					ie.discard();
				}
			}
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;

			// 末影龙击杀 = 解放末地
			if (entity instanceof EnderDragon) {
				SurvivalContract.LOGGER.info("Ender Dragon killed!");
				((ServerLevel) entity.level()).players().forEach(p ->
						p.sendSystemMessage(Component.translatable("survival-contract.msg.ender_dragon_defeated")));
				if (gsm.getCurrentFinalGoal() == GameStateManager.FinalGoal.ENDER_DRAGON) {
					// 限时内完成最终目标：击杀者直接 +10 分
					awardFinalGoalBonus(entity.level().getServer(),
							source.getEntity() instanceof ServerPlayer sp ? sp : null);
					gsm.endGame(GameStateManager.FinalGoal.ENDER_DRAGON);
				}
			}

			// 任务进度
			if (entity instanceof LivingEntity && source.getEntity() instanceof ServerPlayer player) {
				String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				TaskManager.getInstance().onMobKilled(player, entityId);
			}

			// 死亡扣分（全局设置 0/1/2，PLAYING 阶段每次死亡都扣，可重复触发；
			// 死亡不属于"违反规则"，不消耗胜利者免罚特权）
			if (entity instanceof ServerPlayer dead) {
				int dp = GameStateManager.getInstance().getDeathPenalty();
				if (dp > 0) {
					GameStateManager.getInstance().deductScore(dead.getUUID(), dp);
					dead.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
							"survival-contract.msg.death_penalty", dp));
					NetworkRegistry.broadcastScores(((ServerLevel) dead.level()).getServer());
				}

				// 死限规则：「不能被xx杀死」= 被对应方式杀死时才扣分 — 不回血不保护，
				// 死亡照常结算；每玩家每规则一局仅扣一次（recordViolation 内部处理）
				String deathRule = resolveDeathRule(dead, source);
				if (deathRule != null) {
					String detail = switch (deathRule) {
						case "no_drowning" -> "你被淹死了！";
						case "no_fall_damage" -> "你摔死了！";
						case "no_lava_damage" -> "你被岩浆烧死了！";
						case "no_fire_damage" -> "你被火烧死了！";
						case "no_cactus" -> "你被仙人掌扎死了！";
						default -> {
							var k = source.getEntity();
							yield "被 " + (k != null ? k.getName().getString() : "未知生物") + " 杀死了！";
						}
					};
					deductAndWarn(dead, deathRule, detail);
				}

				// 猎人游戏（团队模式专属）：主动击杀其他玩家 → 夺取其 2 分；误杀队友 → 全队每人 -2
				//（仅 PVP 开启后有效——PVP 关闭时玩家本就无法互相伤害，这里双重兜底）
				ServerPlayer hunter = source.getEntity() instanceof ServerPlayer h && h != dead ? h : null;
				if (gsm.isTeamMode()
						&& hunter != null
						&& gsm.isRuleActive("rule_hunter_game")
						&& gsm.isRuleActive("rule_pvp")) {
					var killerTeam = gsm.getPlayerTeam(hunter.getUUID());
					if (killerTeam == GameStateManager.Team.RED || killerTeam == GameStateManager.Team.BLUE) {
						var victimTeam = gsm.getPlayerTeam(dead.getUUID());
						if (killerTeam != victimTeam) {
							// 异队猎杀：凶手 +2，受害者 -2（夺取）
							gsm.deductScore(dead.getUUID(), 2);
							gsm.addScore(hunter.getUUID(), 2);
							for (ServerPlayer p : dead.level().getServer().getPlayerList().getPlayers()) {
								p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
										"survival-contract.msg.hunter_kill",
										hunter.getName().getString(), dead.getName().getString()));
							}
						} else {
							// 误杀队友：凶手所在全队每人 -2
							for (UUID mid : gsm.getTeamMembers(killerTeam)) {
								gsm.deductScore(mid, 2);
							}
							for (ServerPlayer p : dead.level().getServer().getPlayerList().getPlayers()) {
								p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
										"survival-contract.msg.hunter_friendly", hunter.getName().getString()));
							}
						}
					}
				}
			}

			// 不能击杀铁傀儡
			if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem
					&& source.getEntity() instanceof ServerPlayer killer) {
				var killerChallenges = ChallengeManager.getInstance().getPlayerChallenges(killer.getUUID());
				if (killerChallenges.contains("no_kill_iron_golem")) {
					deductAndWarn(killer, "no_kill_iron_golem", "不能击杀铁傀儡！");
				}
			}

			// 白月：夜间自然生成的敌对生物首次被杀死时，立即按原 NBT 生成一个相同个体
			//（克隆体不再链式复活；遭遇事件生物不受影响）
			if (entity instanceof Monster dying
					&& gsm.isRuleActive("white_moon")
					&& !moonClones.contains(entity.getUUID())
					&& !RandomEventManager.getInstance().isEventEntity(entity.getUUID())
					&& isNightNow(entity.level())) {
				Mob clone = cloneMobWithNbt(dying);
				if (clone != null) {
					moonClones.add(clone.getUUID());
					((ServerLevel) entity.level()).addFreshEntity(clone);
				}
			}
		});

		// ============ 新实体生成：检测凋零被召唤 ============
		// 用 tick 扫描新生成的 WitherBoss（迷彩/赤月走上方 ENTITY_LOAD 直挂）
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
			if (gsm.getCurrentFinalGoal() != GameStateManager.FinalGoal.WITHER) return;

			for (ServerLevel level : server.getAllLevels()) {
				for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class,
						new net.minecraft.world.phys.AABB(
								net.minecraft.world.phys.Vec3.ZERO,
								new net.minecraft.world.phys.Vec3(30000000D, 320D, 30000000D)))) {
					UUID id = wither.getUUID();
					if (!seenWithers.contains(id)) {
						seenWithers.add(id);
						SurvivalContract.LOGGER.info("Wither Boss summoned! WIN CONDITION MET.");
						level.players().forEach(p ->
								p.sendSystemMessage(Component.translatable("survival-contract.msg.wither_summoned")));
						// 限时内完成最终目标：召唤者直接 +10 分
						awardFinalGoalBonus(server, server.getPlayerList().getPlayer(lastSkullPlacer));
						gsm.endGame(GameStateManager.FinalGoal.WITHER);
						return;
					}
				}
			}
			} catch (Throwable t) {
				logTickError("wither-check", t);
			}
		});

		// ============ 实体进入世界直挂：迷彩隐身 + 赤月夜间分裂（生成即生效，无遗漏） ============
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
			if (!com.survivalcontract.dimension.ArenaWorldManager.isArenaLevel(level.dimension())) return;
			if (!(entity instanceof Mob mob) || !mob.isAlive()) return;
			boolean hostile = isHostile(mob);
			if (!hostile && !isNeutralMob(mob)) return;
			// 迷彩：敌对/中立生物一进入竞技场世界立即获得 30 秒隐身
			if (gsm.isRuleActive("camouflage") && camoSeen.add(mob.getUUID())) {
				mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0));
			}
			// 赤月：夜间生成的敌对生物立即分裂出一个相同个体（克隆体与遭遇事件生物不分裂）
			if (hostile
					&& gsm.isRuleActive("red_moon")
					&& isNightNow(level)
					&& !moonClones.contains(mob.getUUID())
					&& !RandomEventManager.getInstance().isEventEntity(mob.getUUID())) {
				Mob clone = cloneMobWithNbt(mob);
				if (clone != null) {
					moonClones.add(clone.getUUID());
					level.addFreshEntity(clone);
				}
			}
		});

	// ============ 玩家进入末地：结束了 目标 ============
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
			if (gsm.getCurrentFinalGoal() != GameStateManager.FinalGoal.ENDER_END) return;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA_END) {
					SurvivalContract.LOGGER.info("Player {} entered The End! WIN CONDITION MET.", player.getName().getString());
					// 完成最终目标：完成者（团队=同队全体）+15 分，随后立即结束游戏
					awardFinalGoalBonus(server, player);
					gsm.endGame(GameStateManager.FinalGoal.ENDER_END);
					break;
				}
			}
			} catch (Throwable t) {
				logTickError("ender-end-check", t);
			}
		});

		// ============ SERVER TICK ============
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// 整段兜底：任何一个未预料的异常都不允许抛回服务端主循环
			// （抛出去会让本次 tick 中断、后续子系统全部停摆，严重时服务端直接崩溃）
			try {
			GameStateManager gsm = GameStateManager.getInstance();

			// --- 贫瘠铁矿延迟结算（本 tick 末尾：掉落实体必已生成） ---
			if (!PENDING_ORE_ROLLS.isEmpty()) {
				List<OreRoll> rolls = new ArrayList<>(PENDING_ORE_ROLLS);
				PENDING_ORE_ROLLS.clear();
				processIronOreRolls(rolls);
			}

			// --- 脆骨症攻击反馈延迟结算（tick 末，防伤害递归） ---
			if (!PENDING_BRITTLE_REFLECTS.isEmpty()) {
				var reflects = new ArrayList<>(PENDING_BRITTLE_REFLECTS);
				PENDING_BRITTLE_REFLECTS.clear();
				for (var r : reflects) {
					if (r.attacker().isAlive() && r.amount() > 0) {
						r.attacker().hurt(r.attacker().damageSources().generic(), r.amount());
					}
				}
			}

			// --- FINISHED 阶段处理 ---
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED) {
				if (FINISHED_COUNTDOWN == -1) {
					// 第一次进入 FINISHED → 通知附属玩法（清理自己的运行时状态）
					com.survivalcontract.api.ScPlayStyleProvider addonEnding = gsm.getAddonProvider();
					if (addonEnding != null) tickSafely("addon-end", () -> addonEnding.onGameEnd(server));
					// 立即清掉 Boss 血条（凋灵/末影龙/事件预警条）
					clearVanillaBossBars(server);
					// 全体切观察者 + 广播 + 开始倒计时（经验升级提示音标记游戏结束）
					// 死亡界面中尚未重生的玩家先强制重生（必须在拆竞技场维度之前）——
					// 否则维度被拆除后其重生失败，卡在死亡界面：回不了大厅、也进不了下一局
					for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
						if (!p.isAlive()) {
							server.getPlayerList().respawn(p, false,
									net.minecraft.world.entity.Entity.RemovalReason.KILLED);
							continue;
						}
						p.setGameMode(GameType.SPECTATOR);
						p.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
					}
					FINISHED_COUNTDOWN = 100;
					broadcastGameEnd(server, gsm);
				} else if (FINISHED_COUNTDOWN > 0) {
					FINISHED_COUNTDOWN--;
				} else {
					// 倒计时归零 → reset + 回大厅
						tickSafely("reset-to-lobby", () -> performResetForLobby(server, gsm));
				}
				return; // FINISHED 阶段不跑下面的 PLAYING/LOBBY tick
			} else {
				FINISHED_COUNTDOWN = -1;
			}

			// --- 附属玩法：每 tick 钩子（套餐倒计时/交付检测等由附属模组实现） ---
			com.survivalcontract.api.ScPlayStyleProvider addonTick = gsm.getAddonProvider();
			if (addonTick != null) tickSafely("addon-tick", () -> addonTick.onServerTick(server));

			// --- PLAYING 阶段死亡挑战检测 ---
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Set<String> challenges = ChallengeManager.getInstance().getPlayerChallenges(player.getUUID());

					// --- 恶意感知（环境板块，全员共享）：中立生物主动攻击所有玩家（每 2 秒刷新一次仇恨）---
					if (GameStateManager.getInstance().isRuleActive("neutral_hostile") && server.getTickCount() % 40 == 0) {
						var nearby = player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
								player.getBoundingBox().inflate(24),
								e -> e.isAlive() && isNeutralMob(e));
						for (var e : nearby) {
							if (e.getTarget() == null) e.setTarget(player);
						}
					}

					// --- no_jump 限制（斥候豁免；非斥候：阻止跳跃 + 每秒扣分一次）---
					if (challenges.contains("no_jump") && player.isJumping()
							&& ProfessionManager.getInstance().getPlayerProfession(player.getUUID()) != ProfessionManager.Profession.SCOUT) {
						// 每 tick 重置跳跃速度 + 垂直速度
						var v = player.getDeltaMovement();
						player.setDeltaMovement(v.x, Math.min(v.y, 0.0), v.z);
						// 违规扣分（每秒最多一次，靠 recordViolation 的 tick 去重）
						if (server.getTickCount() % 20 == 0) {
							deductAndWarn(player, "no_jump", "不能跳跃！");
						}
					}

					// --- 属性削弱类（点亮即持续生效；注：ATTACK_DAMAGE 属性不影响箭/三叉戟的远程伤害，
					//     弓手的"远程攻击不受攻击伤害降低影响"由原版机制天然保证，仅近战受削弱）---
					if (server.getTickCount() % 40 == 0) {
						if (challenges.contains("reduced_attack")) {
							applyAttrModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
									ATTACK_REDUCE_ID, "sc_attack_reduce", -getCustomPct("reduced_attack"));
						} else {
							applyAttrModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
									ATTACK_REDUCE_ID, "sc_attack_reduce", 0.0);
						}
						if (challenges.contains("reduced_armor")) {
							applyAttrModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
									ARMOR_REDUCE_ID, "sc_armor_reduce", -getCustomPct("reduced_armor"));
						} else {
							applyAttrModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
									ARMOR_REDUCE_ID, "sc_armor_reduce", 0.0);
						}
					}

					// --- 获得禁止类：背包扫描没收（每 20 tick；有职业豁免的物品不没收）---
					if (server.getTickCount() % 20 == 0) {
						boolean noDia = challenges.contains("no_diamond");
						boolean noGold = challenges.contains("no_gold");
						boolean noNeth = challenges.contains("no_netherite");
						boolean noPot = challenges.contains("no_potions")
								&& ProfessionManager.getInstance().getPlayerProfession(player.getUUID()) != ProfessionManager.Profession.SCOUT;
						boolean noLapis = challenges.contains("no_lapis");
						if (noDia || noGold || noNeth || noPot || noLapis) {
							var inv = player.getInventory();
							for (int slot = 0; slot < inv.getContainerSize(); slot++) {
								ItemStack s = inv.getItem(slot);
								if (s.isEmpty()) continue;
								// 先判定命中的规则（矿工/斥候等职业豁免的物品直接跳过，不没收）
								String matched = null;
								if (noDia && (s.is(Items.DIAMOND) || s.is(Items.DIAMOND_ORE) || s.is(Items.DEEPSLATE_DIAMOND_ORE))) matched = "no_diamond";
								else if (noGold && (s.is(Items.GOLD_INGOT) || s.is(Items.RAW_GOLD))) matched = "no_gold";
								else if (noNeth && (s.is(Items.NETHERITE_INGOT) || s.is(Items.NETHERITE_SCRAP)
										|| s.is(Items.ANCIENT_DEBRIS))) matched = "no_netherite";
								else if (noPot && (s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION))) matched = "no_potions";
								else if (noLapis && (s.is(Items.LAPIS_LAZULI) || s.is(Items.LAPIS_BLOCK)
										|| s.is(Items.LAPIS_ORE) || s.is(Items.DEEPSLATE_LAPIS_ORE))) matched = "no_lapis";
								if (matched == null) continue;
								if (ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), matched)) continue;
								inv.removeItem(slot, s.getCount());
								// 没收提示：带数量参数的动态文案（warn.confiscated: "%s 个违规物品已被没收"）
								deductAndWarn(player, matched, Component.translatable(
										"survival-contract.warn.confiscated", s.getCount()));
							}
						}
					}

					// --- no_boat：乘坐船时强制下船并扣分（每 20 tick 扫描一次）---
					if (server.getTickCount() % 20 == 0
							&& challenges.contains("no_boat")
							&& player.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat) {
						player.stopRiding();
						deductAndWarn(player, "no_boat", "不能乘坐任意船！已强制下船");
					}

					// --- 死限规则（「不能被xx杀死」系）判定在 AFTER_DEATH 死亡事件中：被对应方式
					//     杀死时才扣分，不回血、不保护，死亡照常结算（与规则描述一致）---

					// 虚空：掉进虚空每次都扣分并拉回出生点（防死亡安全网，与描述一致）
					if (player.getY() < -64 && challenges.contains("no_fall_in_void")) {
						player.setHealth(Math.max(player.getMaxHealth(), 10));
						deductAndWarn(player, "no_fall_in_void", "掉进虚空！已扣分并传送回出生点");
						BlockPos spawn = gsm.getPlayingSpawn();
						player.teleportTo((ServerLevel) player.level(),
								spawn.getX() + 0.5, spawn.getY() + 10, spawn.getZ() + 0.5,
								Set.of(), player.getYRot(), player.getXRot(), false);
					}

					// --- 愚者彩蛋：身上出现"涂蜡的氧化切制铜楼梯" → 立刻爆炸身亡并清除该物品 ---
					if (player.isAlive()
							&& ProfessionManager.getInstance().getPlayerProfession(player.getUUID())
									== ProfessionManager.Profession.JESTER) {
						var inv = player.getInventory();
						boolean cursed = false;
						for (int i = 0; i < inv.getContainerSize(); i++) {
							if (inv.getItem(i).is(Items.CUT_COPPER_STAIRS.waxed().oxidized())) {
								inv.setItem(i, ItemStack.EMPTY);
								cursed = true;
							}
						}
						if (cursed) {
							// 先清物品再爆炸：以玩家为中心 TNT 威力爆炸，kill() 兜底确保身亡
							player.level().explode(null, player.getX(), player.getY(), player.getZ(),
									4.0f, Level.ExplosionInteraction.TNT);
							if (player.isAlive()) player.kill((ServerLevel) player.level());
							player.sendSystemMessage(Component.translatable("survival-contract.msg.jester_cursed"));
						}
					}

					// ============ 板块规则（与神合约/久病成患）逐tick与每秒效果 ============
					tickSectionRules(server, player, challenges);
				}

				// ============ 环境恶化板块（全局共享效果，每秒一次） ============
				if (server.getTickCount() % 20 == 0) {
					// 永夜：竞技场主世界时间锁定为午夜（time set midnight，每秒校正）
					// withSuppressedOutput：不向聊天栏广播"时间已设置"等系统消息（防刷屏）
					if (gsm.isRuleActive("eternal_night")) {
						var arenaOw = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
						if (arenaOw != null) {
							var src = server.createCommandSourceStack()
									.withSuppressedOutput()
									.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS)
									.withLevel(arenaOw);
							server.getCommands().performPrefixedCommand(src, "time set midnight");
						}
					}
					// 金月/赤月：夜间为竞技场中自然生成的敌对生物施加抗性提升2/力量2光环
					//（白天不施加，60t 效果自然到期即"白天解除"；遭遇事件生物不受影响）
					if (gsm.isRuleActive("golden_moon") || gsm.isRuleActive("red_moon")) {
						boolean goldMoon = gsm.isRuleActive("golden_moon");
						boolean redMoon = gsm.isRuleActive("red_moon");
						for (ServerLevel lvl : server.getAllLevels()) {
							if (!com.survivalcontract.dimension.ArenaWorldManager.isArenaLevel(lvl.dimension())) continue;
							if (!isNightNow(lvl)) continue;
							for (var e : lvl.getAllEntities()) {
								if (!(e instanceof Monster mob) || !mob.isAlive()) continue;
								if (RandomEventManager.getInstance().isEventEntity(mob.getUUID())) continue;
								if (goldMoon) mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1, false, true, true));
								if (redMoon) mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1, false, true, true));
							}
						}
					}
					// 午夜凶铃：游戏时刻零点（以竞技场主世界时钟为准），随机一名玩家受到一次无来源
					// 的音波尖啸伤害；每 20 分钟最多触发一次，连续两次不会选中同一位玩家
					if (gsm.isRuleActive("midnight_bell")) {
						var arenaOw = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
						if (arenaOw != null) {
							long tod = arenaOw.getOverworldClockTime() % 24000L;
							long now = server.getTickCount();
							// 零点窗口：18000 起 5 秒内（覆盖逐 tick 推进与永夜时间锁定两种情形）
							if (tod >= 18000L && tod < 18099L && now >= midnightBellNextTick) {
								var candidates = new ArrayList<>(server.getPlayerList().getPlayers());
								if (midnightBellLastVictim != null && candidates.size() > 1) {
									candidates.removeIf(p -> p.getUUID().equals(midnightBellLastVictim));
								}
								if (!candidates.isEmpty()) {
									ServerPlayer victim = candidates.get(arenaOw.getRandom().nextInt(candidates.size()));
									midnightBellLastVictim = victim.getUUID();
									midnightBellNextTick = now + 24000L; // 20 分钟冷却
									victim.hurt(victim.damageSources().sonicBoom(null), 10.0f); // 无来源音波尖啸
									for (ServerPlayer p : server.getPlayerList().getPlayers()) {
										p.sendSystemMessage(Component.translatable(
												"survival-contract.msg.midnight_bell", victim.getName().getString()));
									}
								}
							}
						}
					}
					// 愚者：扫描每位愚者身边刚丢出的钻石/绿宝石（4 格内、age≤40、归属本人）
					// → 消耗宝石并随机发放 6~8 件生存模式可非作弊获得的物品/方块
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						if (ProfessionManager.getInstance().getPlayerProfession(p.getUUID())
								!= ProfessionManager.Profession.JESTER) continue;
						ServerLevel pLevel = (ServerLevel) p.level();
						for (ItemEntity gem : pLevel.getEntitiesOfClass(ItemEntity.class,
								p.getBoundingBox().inflate(4),
								e -> e.isAlive() && e.getAge() <= 40
										&& (e.getItem().is(Items.DIAMOND) || e.getItem().is(Items.EMERALD))
										&& e.getOwner() != null
										&& e.getOwner().getUUID().equals(p.getUUID()))) {
							gem.discard();
							grantFoolGemLoot(p);
							// 0.5% 额外抽中"涂蜡的氧化切制铜楼梯"：直接入包，
							// 下一 tick 被愚者彩蛋扫描捕获 → 立刻爆炸身亡
							if (p.getRandom().nextFloat() < 0.005f) {
								ItemStack cursed = new ItemStack(Items.CUT_COPPER_STAIRS.waxed().oxidized());
								if (!p.getInventory().add(cursed)) com.survivalcontract.McCompat.drop(p, cursed, false);
								p.sendSystemMessage(Component.translatable("survival-contract.msg.jester_extra_curse", 
										cursed.getHoverName().getString()));
							}
						}
					}
					// 行商：每次猪灵交易结束时（检测附近的猪灵交易掉落物，每个掉落物只判定一次），
					// 15% 概率额外随机获得四种礼物之一——直接放入物品栏，不生成任何掉落物（杜绝复制卡服）
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						if (ProfessionManager.getInstance().getPlayerProfession(p.getUUID())
								!= ProfessionManager.Profession.FOOL) continue;
						ServerLevel pLevel = (ServerLevel) p.level();
						for (ItemEntity drop : pLevel.getEntitiesOfClass(ItemEntity.class,
								p.getBoundingBox().inflate(6),
								e -> e.isAlive() && e.getAge() <= 60
										&& !processedBarterDrops.contains(e.getUUID()))) {
							var piglins = pLevel.getEntitiesOfClass(
									net.minecraft.world.entity.monster.piglin.Piglin.class,
									drop.getBoundingBox().inflate(4),
									net.minecraft.world.entity.monster.piglin.Piglin::isAlive);
							if (piglins.isEmpty()) continue;
							processedBarterDrops.add(drop.getUUID());
							if (p.getRandom().nextFloat() < 0.15f) {
								ItemStack gift = randomTraderGift(pLevel, p.getRandom());
								if (!p.getInventory().add(gift)) com.survivalcontract.McCompat.drop(p, gift, false);
								p.sendSystemMessage(Component.translatable("survival-contract.msg.fool_barter_gift", 
										gift.getHoverName().getString()));
							}
						}
					}
				}
			}

			// --- SCORE SYNC ---
			if (++SCORE_SYNC_COUNTER >= 40) {
				SCORE_SYNC_COUNTER = 0;
				if (!server.getPlayerList().getPlayers().isEmpty()) {
					NetworkRegistry.broadcastScores(server);
				}
			}

			// --- Challenge sync 每 5 秒 (PLAYING) ---
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING
					&& ++CHALLENGE_SYNC_COUNTER >= 100) {
				CHALLENGE_SYNC_COUNTER = 0;
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					NetworkRegistry.sendSync(p);
				}
			} else if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
				CHALLENGE_SYNC_COUNTER = 0;
			}

			// --- 任务倒计时文本每 1 秒刷新（轻量 TaskSync，只发任务文本，不重建 GUI）---
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING
					&& ++TASK_SYNC_COUNTER >= 20) {
				TASK_SYNC_COUNTER = 0;
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					NetworkRegistry.sendTaskUpdate(p);
				}
			} else if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
				TASK_SYNC_COUNTER = 0;
			}

			// --- Pending teleport queues ---
			if (!PENDING_LOBBY_TELEPORT.isEmpty()) {
				List<ServerPlayer> snap = new ArrayList<>(PENDING_LOBBY_TELEPORT);
				PENDING_LOBBY_TELEPORT.clear();
				for (ServerPlayer p : snap) {
					if (p.isAlive()) tryTeleportToLobby(p);
				}
			}
			if (!PENDING_PLAYING_RESPAWN.isEmpty()) {
				List<ServerPlayer> snap = new ArrayList<>(PENDING_PLAYING_RESPAWN);
				PENDING_PLAYING_RESPAWN.clear();
				for (ServerPlayer p : snap) {
					if (p.isAlive()) teleportToPlayingSpawn(p);
				}
			}
			if (!PENDING_SPECTATOR_JOIN.isEmpty()) {
				List<ServerPlayer> snap = new ArrayList<>(PENDING_SPECTATOR_JOIN);
				PENDING_SPECTATOR_JOIN.clear();
				// JOIN 与首次 respawn 事件可能为同一玩家放入两个实例（首个实例在
				// placeNewPlayer 后已被原版替换）——只处理玩家列表里当前在线的那个，
				// 否则传送会落在已废弃的旧实体上，玩家永远停在原版出生点"进不来"
				Set<UUID> seen = new HashSet<>();
				for (ServerPlayer p : snap) {
					if (!seen.add(p.getUUID())) continue;
					ServerPlayer cur = server.getPlayerList().getPlayer(p.getUUID());
					if (cur == null || !cur.isAlive()) continue;
					try {
						teleportSpectatorToArena(server, cur);
					} catch (Exception e) {
						// 单个玩家失败不阻断队列；MID_GAME_SPECTATORS 已先登记，
						// 下方旁观者保持循环会自愈重试
						SurvivalContract.LOGGER.error("Mid-game spectator join failed for {} (will self-heal)",
								cur.getName().getString(), e);
					}
				}
			}

			// --- Lobby phase buffs ---
			if (com.survivalcontract.dimension.LobbyMode.isActive()
					&& gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY) {
				// 大厅虚空保护：仅自定义大厅维度，掉到 y < -64（掉出地图）→ 传送回大厅出生点
				// 回退主世界时不做（否则玩家在主世界正常挖矿到 -64 以下会被莫名拉回地表）
				var lobbyLevel = com.survivalcontract.dimension.LobbyManager.getInstance().getLobbyLevel();
				boolean customLobby = lobbyLevel != null && lobbyLevel != server.overworld();
				if (customLobby) {
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						if (player.level() == lobbyLevel && player.getY() < -64) {
							com.survivalcontract.dimension.LobbyManager.getInstance().teleportToLobby(player);
							player.sendSystemMessage(Component.translatable("survival-contract.msg.lobby_void_return"));
						}
					}
				}
				// 状态同步每 2 秒一次即可（每 tick 发会导致客户端 GUI 每秒重建 20 次，
				// 按钮交互被不断重建的组件打断）
				boolean syncThisTick = (++LOBBY_SYNC_COUNTER >= 40);
				if (syncThisTick) LOBBY_SYNC_COUNTER = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					applyLobbyBuffs(player);
					if (syncThisTick && hasChallengeBook(player)) NetworkRegistry.sendSync(player);
				}
				// 挑战书防丢（每 10 tick 扫一次）：返还给丢出者本人——
				// 旧实现按"每个玩家周围 20 格"扫描，大厅里大家站得近，
				// 谁先遍历到就塞给谁（房主在玩家列表最前）→ 书全进了房主背包
				if (server.getTickCount() % 10 == 0) {
					returnDroppedChallengeBooks(server);
				}
			}

			// ============ PLAYING phase: events, tasks, etc ============
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING) {

				// 排队中的全屏大字（神的谎言开局身份提示，延迟到倒计时后弹出）
				tickSafely("pending-titles",
						() -> com.survivalcontract.dimension.LobbyManager.getInstance().tickPendingTitles(server));

				// 旁观者保持（仅限游戏加入的旁观者）：
				// - 参战者在对局中可自由切换观察者模式——不再强制拉回生存/送回重生点（用户要求解除该限制）
				// - 游戏中加入的旁观者（无参战状态且在 MID_GAME_SPECTATORS 中）保持旁观者模式
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					// 神的谎言：已淘汰玩家保持旁观者
					if (gsm.isGodsLies() && gsm.isEliminated(player.getUUID())) continue;
					boolean tracked = MID_GAME_SPECTATORS.contains(player.getUUID());
					if (gsm.hasPlayerState(player.getUUID())) {
						// 参战者：仅清除可能残留的旁观者追踪标记，不干预其游戏模式
						if (tracked) MID_GAME_SPECTATORS.remove(player.getUUID());
					} else if (tracked) {
						// 位置自愈：观战者不在竞技场维度（加入时的初次传送被登录竞态/
						// 异常吞掉等）→ 冷却 2 秒后重新传送观战，保证"中途进入自动
						// 切换至观战状态"永远可达（直接对治"进不来，只有回大厅才行"）
						// 附属玩法例外：观战落点由附属模组自持（其自己维护维度兜底），
						// 这里若仍按竞技场维度判定会与附属落点互相打架（反复把观战者拉去主世界）
						ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
						if (!gsm.isAddon() && arena != null && player.level() != arena
								&& server.getTickCount() >= SPECTATOR_REJOIN_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
							SPECTATOR_REJOIN_COOLDOWN.put(player.getUUID(), server.getTickCount() + 40L);
							try {
								teleportSpectatorToArena(server, player);
							} catch (Exception e) {
								SurvivalContract.LOGGER.error("Spectator self-heal teleport failed for {}",
										player.getName().getString(), e);
							}
							continue;
						}
						if (!player.isSpectator()) {
							player.setGameMode(GameType.SPECTATOR);
							player.sendSystemMessage(Component.translatable("survival-contract.msg.spectator_locked"));
						}
					}
				}

				// ============ 开局冻结：3秒倒计时（全员隐身+无法移动），倒计时结束后计时才流动 ============
				boolean frozen = server.getTickCount() < gsm.getGameStartTime();
				if (frozen) {
					long remainFreeze = gsm.getGameStartTime() - server.getTickCount();
					// 每秒发一次大字倒计时（3,2,1）+ 升调提示音
					if (remainFreeze % 20 == 0) {
						int count = (int) (remainFreeze / 20);
						// 音调随倒计时升高：3→0.8，2→1.0，1→1.25
						float pitch = switch (count) { case 3 -> 0.8f; case 2 -> 1.0f; default -> 1.25f; };
						for (ServerPlayer p : server.getPlayerList().getPlayers()) {
							p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(2, 15, 2));
							p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
									Component.literal("§e§l" + count)));
							p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
								Component.translatable("survival-contract.msg.freeze_subtitle")));
						playSoundDirect(p, net.minecraft.sounds.SoundEvents.BELL_BLOCK, 1.0f, pitch);
						}
					}
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						// 隐身 + 高级缓慢（无法移动），无粒子无图标，短时长每 tick 续期
						player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
						player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 6, true, false, false));
						player.setDeltaMovement(0, 0, 0);
					}
				} else if (FREEZE_WAS_ACTIVE) {
					// 冻结结束瞬间：解除隐身/缓慢 + "开始!"提示 + 音效 + 立即 sync（时间栏开始流动）
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						player.removeEffect(MobEffects.INVISIBILITY);
						player.removeEffect(MobEffects.SLOWNESS);
						player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(2, 30, 10));
						player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
								Component.translatable("survival-contract.msg.freeze_start")));
						playSoundDirect(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
						NetworkRegistry.sendSync(player);
					}
				}
				FREEZE_WAS_ACTIVE = frozen;

				boolean nightVision = gsm.isRuleActive("rule_night_vision");
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					// 职业特权每 tick 结算：矿工急迫 / 斥候削弱反转（移速·跳跃·氧气）/ 大厨·斗士进食结算
					tickSafely("profession-perks",
							() -> ProfessionManager.getInstance().tickPerks(player, server.getTickCount()));
					// 全局夜视规则
					if (nightVision && !player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
					player.addEffect(new MobEffectInstance(
							net.minecraft.world.effect.MobEffects.NIGHT_VISION, -1, 0, // -1 = 无限时长
							true, false, false));
					}
				}
				tickSafely("random-events", () -> RandomEventManager.getInstance().tick(server));
				tickSafely("tasks", () -> TaskManager.getInstance().tick(server));
				// 走私犯与检察官：批次更换 / 待执行检查判定
				tickSafely("smuggle", () -> com.survivalcontract.smuggle.SmuggleManager.getInstance().tick(server));
				// 文明时代：占领计时 / 资源产出
				tickSafely("civilization",
						() -> com.survivalcontract.civilization.CivilizationManager.getInstance().tick(server));

				// 竞技场防逃跑扫描：PLAYING 中任何玩家落到竞技场维度组之外
				//（原版回退路径/传送门漏洞）→ 每 2 秒扫一次，送回竞技场
				if (server.getTickCount() % 40 == 0) {
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						if (!com.survivalcontract.dimension.ArenaWorldManager.isArenaLevel(player.level().dimension())) {
							teleportToPlayingSpawn(player);
						}
					}
				}

				// 时间限制超时（-1 = 无限制时长，永不超时）
				long remaining = gsm.getRemainingTimeTicks();
				if (remaining >= 0 && remaining <= 0) {
					if (gsm.isStowaway() || gsm.isCivilization() || gsm.isAddon()) {
						// 无最终目标玩法（含附属玩法）：时间结束分数最高者获胜（不设 endedByTimeout）
						SurvivalContract.LOGGER.info("{}: game time expired, highest score wins.",
								gsm.isStowaway() ? "Smuggle" : gsm.isCivilization() ? "Civilization" : "Addon");
						for (ServerPlayer p : server.getPlayerList().getPlayers()) {
							p.sendSystemMessage(Component.translatable("survival-contract.msg.time_up_highest_wins"));
						}
						NetworkRegistry.broadcastScores(server);
						gsm.endGame(GameStateManager.FinalGoal.WITHER); // timeout 用 WITHER 作终止标记
					} else {
						SurvivalContract.LOGGER.info("Game time expired, no winner! Resetting all scores to zero.");
						// 时间结束无人完成最终目标 → 无人获胜，所有玩家分数归零
						gsm.setEndedByTimeout(true);
						gsm.resetScores();
						for (ServerPlayer p : server.getPlayerList().getPlayers()) {
							p.sendSystemMessage(Component.translatable("survival-contract.msg.time_up_no_winner"));
						}
						NetworkRegistry.broadcastScores(server);
						gsm.endGame(GameStateManager.FinalGoal.WITHER); // timeout 用 WITHER 作默认终止标记
					}
				}
			}
			} catch (Throwable t) {
				logTickError("server-tick", t);
			}
		});

		// ============ 右键挑战书 ============
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (player instanceof ServerPlayer serverPlayer && isChallengeBook(player.getItemInHand(hand))) {
				NetworkRegistry.sendSync(serverPlayer);
			}
			return InteractionResult.PASS;
		});

		// ============ 攻击回调 ============
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			GameStateManager gsm = GameStateManager.getInstance();
			// 文明时代：大本营核心盔甲架（100 血，被击杀判负；原版盔甲架无敌 → 手动扣血拦截）
			if (gsm.isCivilization() && gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING
					&& entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand) {
				// 资源区悬浮图标：完全不可交互（不可打掉/换装备）
				if (com.survivalcontract.civilization.CivilizationManager.getInstance().isFloatStand(stand)) {
					return InteractionResult.SUCCESS;
				}
				if (com.survivalcontract.civilization.CivilizationManager.getInstance()
						.onHqStandAttacked(serverPlayer, stand)) {
					return InteractionResult.SUCCESS;
				}
			}
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			ChallengeManager cm = ChallengeManager.getInstance();
			var challenges = cm.getPlayerChallenges(player.getUUID());
			if (challenges.contains("friendly_counter_attack") && isCounterAttackable(entity)) {
				entity.hurt(entity.damageSources().playerAttack(serverPlayer), 1.0f);
				serverPlayer.hurt(serverPlayer.damageSources().generic(), 1.0f);
			}
			return InteractionResult.PASS;
		});

		// ============ 方块使用回调 ============
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			// 走私犯与检察官：手持收纳袋钥匙右键方块 → 服务端兜底阻断原版收纳袋行为（第六十七批），
			// 真袋界面由客户端 OpenBagPayload 打开（否则原版收纳袋 GUI 经方块路径可达）
			if (gsm.isStowaway()
					&& com.survivalcontract.smuggle.SmuggleManager.isBagKeyItem(serverPlayer.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			ChallengeManager cm = ChallengeManager.getInstance();
			String blockId = BuiltInRegistries.BLOCK.getKey(world.getBlockState(hitResult.getBlockPos()).getBlock()).toString();
			var challenges = cm.getPlayerChallenges(player.getUUID());
			if (challenges.contains("no_enchanting") && blockId.equals("minecraft:enchanting_table")
					&& !ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), "no_enchanting")) {
				deductAndWarn(serverPlayer, "no_enchanting", "不能使用附魔台！");
				return InteractionResult.FAIL;
			}
			if (challenges.contains("no_anvil") && blockId.equals("minecraft:anvil")
					&& !ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), "no_anvil")) {
				deductAndWarn(serverPlayer, "no_anvil", "不能使用铁砧！");
				return InteractionResult.FAIL;
			}
			if (challenges.contains("no_chest") && (blockId.equals("minecraft:chest") || blockId.equals("minecraft:barrel")
					|| blockId.equals("minecraft:trapped_chest"))) {
				deductAndWarn(serverPlayer, "no_chest", "不能使用箱子！");
				return InteractionResult.FAIL;
			}
			if (challenges.contains("no_furnace") &&
					(blockId.equals("minecraft:furnace") || blockId.equals("minecraft:blast_furnace") || blockId.equals("minecraft:smoker"))) {
				deductAndWarn(serverPlayer, "no_furnace", "不能使用熔炉！");
				return InteractionResult.FAIL;
			}
			if (challenges.contains("no_brewing_stand") && blockId.equals("minecraft:brewing_stand")) {
				deductAndWarn(serverPlayer, "no_brewing_stand", "不能使用酿造台！");
				return InteractionResult.FAIL;
			}
			if (challenges.contains("no_beacon") && blockId.equals("minecraft:respawn_anchor")) {
				deductAndWarn(serverPlayer, "no_beacon", "不能使用重生锚！");
				return InteractionResult.FAIL;
			}
			// 乐师：手持木棍/烈焰棒互动音符盒演奏 → 音色效果区分敌我（正面→友方含自身，负面→敌方），
			// 持续演奏叠加友方时长 / 5s 三音色合奏奖励 / 底鼓·颤音琴·小号音波攻击，详见 musicianPerform
			if (ProfessionManager.getInstance().getPlayerProfession(serverPlayer.getUUID())
					== ProfessionManager.Profession.MUSICIAN
					&& (serverPlayer.getItemInHand(hand).is(Items.STICK)
					|| serverPlayer.getItemInHand(hand).is(Items.BLAZE_ROD))) {
				var state = world.getBlockState(hitResult.getBlockPos());
				if (state.getBlock() instanceof net.minecraft.world.level.block.NoteBlock) {
					var instrument = state.getValue(net.minecraft.world.level.block.NoteBlock.INSTRUMENT);
					var effect = musicianInstrumentEffect(instrument);
					if (effect != null) {
						musicianPerform(serverPlayer, (ServerLevel) world, hitResult.getBlockPos(), instrument,
								effect, serverPlayer.getItemInHand(hand).is(Items.BLAZE_ROD));
					}
					// 返回 PASS：保留原版右键行为（调音+发声）作为听觉反馈；无映射的音色走纯原版
					return InteractionResult.PASS;
				}
			}
			// 乐师手鼓（获得成就「与火共舞」解锁）：主手持音符盒 + 副手持任意方块 → 右键已放置的音符盒，
			// 以副手方块对应的音色演奏（等同木棍效果，含音波攻击/合奏/时长叠加），并阻止放置新音符盒
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING
					&& serverPlayer.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.NOTE_BLOCK)
					&& world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof net.minecraft.world.level.block.NoteBlock
					&& hasDanceWithFireAdvancement(serverPlayer)) {
				var off = serverPlayer.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
				if (off.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
					var drumInst = blockItem.getBlock().defaultBlockState().instrument();
					var drumEffect = musicianInstrumentEffect(drumInst);
					if (drumEffect != null) {
						musicianPerform(serverPlayer, (ServerLevel) world, hitResult.getBlockPos(), drumInst, drumEffect, false);
						return InteractionResult.SUCCESS; // 消耗点击：不放置新音符盒
					}
				}
			}
			// ============ 文明时代 ============
			if (gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING && gsm.isCivilization()) {
				var civ = com.survivalcontract.civilization.CivilizationManager.getInstance();
				net.minecraft.world.level.block.state.BlockState clickedState =
						world.getBlockState(hitResult.getBlockPos());
				// 大本营信标不可交互（防止打开信标 GUI / 更改光柱）
				if (clickedState.is(net.minecraft.world.level.block.Blocks.BEACON)) {
					return InteractionResult.SUCCESS;
				}
				// 功能方块/容器使用锁定（无主/敌方区块；箱子/木桶按队伍归属判定）
			if (com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(clickedState)
					&& !civ.canInteract(serverPlayer, hitResult.getBlockPos(), clickedState)) {
					serverPlayer.sendSystemMessage(Component.translatable(
							"survival-contract.msg.chunk_locked"));
					return InteractionResult.FAIL;
				}
				// 主手持羊毛/功能方块放置：预测放置位置（点击面相邻格）
				var main = serverPlayer.getMainHandItem();
				if (main.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
					BlockPos placePos = hitResult.getBlockPos().relative(hitResult.getDirection());
					BlockPos clickedPos = hitResult.getBlockPos();
					net.minecraft.world.level.block.Block placed = bi.getBlock();
					// 原版落点判定：26.2 原版 BlockPlaceContext 用 canBeReplaced(放置上下文) 决定是否
					// 把方块放进被点击格（点击短草/花草——超平坦草地遍布——非潜行/潜行均替换被点击格；
					// 构造中已无潜行检查），必须与原版同源判定，否则预测位置与真实落点不一致
					boolean replaceTarget = clickedState.canBeReplaced(
							new net.minecraft.world.item.context.BlockPlaceContext(serverPlayer, hand, main, hitResult));
					BlockPos targetPos = replaceTarget ? clickedPos : placePos;
					// 红/蓝占领羊毛 → 每区块最多一个：已存在羊毛时取消放置并提示
					if (placed == com.survivalcontract.civilization.CivilizationManager.woolBlock(
							GameStateManager.Team.RED)
							|| placed == com.survivalcontract.civilization.CivilizationManager.woolBlock(
							GameStateManager.Team.BLUE)) {
						if (civ.chunkHasWool(net.minecraft.world.level.ChunkPos.containing(targetPos))) {
							serverPlayer.sendSystemMessage(Component.translatable(
									"survival-contract.msg.chunk_wool_exists"));
							return InteractionResult.SUCCESS; // 取消原版放置
						}
						// 占领规则前置校验：不可占领（不相邻/敌方/本方领土/疆域外等）的区块
						// **直接取消放置**——否则羊毛落地成孤儿羊毛，之后该区块被永久锁死
						Component woolErr = civ.validateWoolPlaceMsg(serverPlayer,
								net.minecraft.world.level.ChunkPos.containing(targetPos));
						if (woolErr != null) {
							serverPlayer.sendSystemMessage(woolErr);
							return InteractionResult.SUCCESS; // 取消原版放置
						}
						civ.queueWoolCheck(serverPlayer, placePos, clickedPos, replaceTarget, placed);
					}
					// 放置登记（第六十八批）：任意方块放置都登记设施位置——石头区前置是任意
					// *_wood（非功能方块），只登记功能方块会导致前置永不满足、石头区不产出；
					// 注入与前置校验都按方块状态过滤，非功能方块的登记无害。
					// 双位置登记：点击可替换方块时真实落点是被点击格（placePos 只是预测位置），
					// 只登记 placePos 会导致设施/容器登记错位 → 该区块不产出物资
					civ.registerFacility(targetPos);
					civ.registerFacility(placePos);
					// 箱子/木桶 → 登记队伍归属（敌方反占后依然锁定、不接收产出）
					if (placed instanceof net.minecraft.world.level.block.ChestBlock
							|| placed instanceof net.minecraft.world.level.block.BarrelBlock) {
						civ.registerContainer(targetPos, gsm.getPlayerTeam(player.getUUID()));
						civ.registerContainer(placePos, gsm.getPlayerTeam(player.getUUID()));
					}
				}
			}
			// 凋零召唤者追踪（"凋零山庄" 最终目标 +10 分归属）
			if (blockId.endsWith("wither_skeleton_skull") || blockId.endsWith("wither_skeleton_wall_skull")) {
				lastSkullPlacer = serverPlayer.getUUID();
			}
			return InteractionResult.PASS;
		});

		// ============ 乐师手鼓（成就「与火共舞」解锁）：主手音符盒+副手触发方块 → 右键空气演奏 ============
		// 以玩家自身位置为效果中心；头颅类音色无映射 → 走原版（空气右键本无行为）
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			if (!sp.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.NOTE_BLOCK)) return InteractionResult.PASS;
			var off = sp.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND);
			if (!(off.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return InteractionResult.PASS;
			if (!hasDanceWithFireAdvancement(sp)) return InteractionResult.PASS;
			var drumInst = blockItem.getBlock().defaultBlockState().instrument();
			var drumEffect = musicianInstrumentEffect(drumInst);
			if (drumEffect == null) return InteractionResult.PASS;
			musicianPerform(sp, (ServerLevel) world, sp.blockPosition(), drumInst, drumEffect, false);
			return InteractionResult.SUCCESS;
		});

		// ============ 不能与村民交易（右键村民/流浪商人拦截）+ 一本万利金粒交易 ============
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			// 盗贼：靠近生物右键 → 夺取其全部装备；对可掉落头颅的生物直接击杀并夺取头颅（2min 冷却，第七十二批）
			if (ProfessionManager.getInstance().isThief(sp.getUUID())
					&& ProfessionManager.getInstance().tryThiefSteal(sp, entity)) {
				return InteractionResult.SUCCESS;
			}
			// 走私犯与检察官：手持收纳袋钥匙右键实体 → 服务端兜底阻断原版收纳袋行为（第六十七批）
			if (GameStateManager.getInstance().isStowaway()
					&& com.survivalcontract.smuggle.SmuggleManager.isBagKeyItem(sp.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			// 一本万利：手持金粒右键猪灵 → 金粒折算成金锭丢在猪灵面前（拾取延迟极短），
			// 由猪灵原版 AI 拾取 → 触发完整的原版金锭交易流程（副手持握、欣赏动画、
			// 约 6 秒后丢出以物易物战利品）— 动画与真实金锭交易完全一致，零自定义逻辑。
			// 防连点：猪灵鉴赏中（副手持金锭）或冷却期内不允许再次交易，
			// 否则长按右键会反复丢金锭 → 猪灵逐枚拾取连续交易
			if (entity instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin
					&& player.getItemInHand(hand).is(Items.GOLD_NUGGET)) {
				var dealChallenges = ChallengeManager.getInstance().getPlayerChallenges(player.getUUID());
				if (dealChallenges.contains("piglin_deal")) {
					long now = world.getGameTime();
					long busyUntil = PENDING_PIGLIN_DEALS.getOrDefault(piglin.getUUID(), 0L);
					boolean admiring = !piglin.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).isEmpty();
					if (admiring || now < busyUntil) {
						sp.sendSystemMessage(Component.translatable("survival-contract.msg.piglin_busy"));
						return InteractionResult.FAIL; // 不消耗金粒
					}
					player.getItemInHand(hand).shrink(1);
					ItemEntity ingot = new ItemEntity((ServerLevel) world,
							piglin.getX(), piglin.getY() + 0.9, piglin.getZ(), new ItemStack(Items.GOLD_INGOT));
					ingot.setPickUpDelay(10); // 0.5 秒后可拾取：猪灵立即拿走，玩家来不及抢回
					((ServerLevel) world).addFreshEntity(ingot);
					// 冷却覆盖整个交易周期：拾取(10t) + 鉴赏(约120t) + 丢出战利品
					PENDING_PIGLIN_DEALS.put(piglin.getUUID(), now + 160);
					sp.sendSystemMessage(Component.translatable("survival-contract.msg.piglin_deal_start"));
					return InteractionResult.SUCCESS;
				}
			}
			if (!(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)) return InteractionResult.PASS;
			var challenges = ChallengeManager.getInstance().getPlayerChallenges(player.getUUID());
			if (challenges.contains("no_villager_trade")
					&& !ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), "no_villager_trade")) {
				deductAndWarn(sp, "no_villager_trade", "不能与村民交易！");
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// ============ 物品使用（扩展：食物限制 + 药水限制 + 进食结算记录） ============
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			ChallengeManager cm = ChallengeManager.getInstance();
			var challenges = cm.getPlayerChallenges(player.getUUID());
			ProfessionManager pm = ProfessionManager.getInstance();

			ItemStack stack = player.getItemInHand(hand);
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

			// 记录进食开始（大厨/斗士进食结算、食物削弱挑战都依赖这条记录）
			pm.onFoodUseStart(sp, stack);

			// no_potions - 不能使用药水（豁免：斥候职业——不扣分且允许使用）
			if (challenges.contains("no_potions") && !pm.isImmuneToChallenge(player.getUUID(), "no_potions")) {
				if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
					deductAndWarn(sp, "no_potions", "不能使用药水！");
					return InteractionResult.FAIL;
				}
			}
			// no_fishing_rod - 不能使用钓鱼竿
			if (challenges.contains("no_fishing_rod") && stack.is(Items.FISHING_ROD)) {
				deductAndWarn(sp, "no_fishing_rod", "不能使用钓鱼竿！");
				return InteractionResult.FAIL;
			}
			// no_ender_pearl - 不使用末影珍珠（投掷前拦截）
			if (challenges.contains("no_ender_pearl") && stack.is(Items.ENDER_PEARL)) {
				deductAndWarn(sp, "no_ender_pearl", "不能使用末影珍珠！");
				return InteractionResult.FAIL;
			}
			// no_boat - 不能使用任意船（禁止手中放置船/木筏/运输船）
			if (challenges.contains("no_boat") && stack.getItem() instanceof net.minecraft.world.item.BoatItem) {
				deductAndWarn(sp, "no_boat", "不能使用任意船！");
				return InteractionResult.FAIL;
			}
			// 食物限制（职业豁免：大厨面包/烤马铃薯、斗士熟肉——豁免时不扣分且允许食用）
			// 肉类规则判定范围：生肉与熟肉都算（牛/猪/鸡/羊）
			// 低血糖：仅允许南瓜派/曲奇饼/蛋糕/烤马铃薯/面包/苹果/甜浆果/发光浆果恢复饱食度，其余食物阻止食用（不扣分）
			// 斗士免疫低血糖：不限制食用
			if (challenges.contains("hypoglycemia")
					&& !pm.isImmuneToChallenge(player.getUUID(), "hypoglycemia")
					&& stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
				String fid = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
				if (!isHypoglycemiaAllowedFood(fid)) {
					sp.sendSystemMessage(Component.translatable(
							"survival-contract.msg.hypoglycemia_food"));
					return InteractionResult.FAIL;
				}
			}
			if ((stack.is(Items.BEEF) || stack.is(Items.COOKED_BEEF)) && challenges.contains("no_eat_beef") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_beef")) { deductAndWarn(sp, "no_eat_beef", "不能吃牛肉（生熟都算）！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.PORKCHOP) || stack.is(Items.COOKED_PORKCHOP)) && challenges.contains("no_eat_pork") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_pork")) { deductAndWarn(sp, "no_eat_pork", "不能吃猪肉（生熟都算）！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.CHICKEN) || stack.is(Items.COOKED_CHICKEN)) && challenges.contains("no_eat_chicken") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_chicken")) { deductAndWarn(sp, "no_eat_chicken", "不能吃鸡肉（生熟都算）！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.MUTTON) || stack.is(Items.COOKED_MUTTON)) && challenges.contains("no_eat_mutton") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_mutton")) { deductAndWarn(sp, "no_eat_mutton", "不能吃羊肉（生熟都算）！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON) || stack.is(Items.COD)
					|| stack.is(Items.SALMON) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PUFFERFISH))
					&& challenges.contains("no_eat_fish") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_fish")) { deductAndWarn(sp, "no_eat_fish", "不能吃鱼！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE)) && challenges.contains("no_eat_apple") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_apple")) { deductAndWarn(sp, "no_eat_apple", "不能吃苹果！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) && challenges.contains("no_eat_golden_apple") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_golden_apple")) { deductAndWarn(sp, "no_eat_golden_apple", "不能吃金苹果！"); return InteractionResult.FAIL; }
			if (stack.is(Items.BREAD) && challenges.contains("no_eat_bread") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_bread")) { deductAndWarn(sp, "no_eat_bread", "不能吃面包！"); return InteractionResult.FAIL; }
			if ((stack.is(Items.BAKED_POTATO) || stack.is(Items.POTATO)) && challenges.contains("no_eat_potato") && !pm.isImmuneToChallenge(player.getUUID(), "no_eat_potato")) { deductAndWarn(sp, "no_eat_potato", "不能吃马铃薯！"); return InteractionResult.FAIL; }

			// USE_ITEM 任务进度：由 TaskManager tick 用原版使用统计基线差值统计（精确、防双计），
			// 此处不再直接计数（use 回调在进食开始时就会触发，会早计/多计）
			return InteractionResult.PASS;
		});

		// ============ 文明时代 · 回城卷轴（第七十八批） ============
		// 右键回城卷轴 → 立刻传送到本队队伍出生点并消耗 1 枚（仅服务端处理）
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return InteractionResult.PASS;
			if (!com.survivalcontract.civilization.CivilizationManager.isReturnScroll(sp.getItemInHand(hand))) return InteractionResult.PASS;
			boolean used = com.survivalcontract.civilization.CivilizationManager.getInstance().useReturnScroll(sp, hand);
			return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});

		// ============ 弓手特权：弓箭/弩/三叉戟伤害提升 50% ============
		// ALLOW_DAMAGE 无法直接改伤害值 → 用"回环保护 + 重伤害"实现：首次命中按 1.5 倍重打，第二次放行
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
			if (!(source.getEntity() instanceof ServerPlayer sp)) return true;
			ProfessionManager pm = ProfessionManager.getInstance();
			if (pm.getPlayerProfession(sp.getUUID()) != ProfessionManager.Profession.ARCHER) return true;
			Entity direct = source.getDirectEntity();
			boolean ranged = direct instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow
					|| direct instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident;
			if (!ranged) return true;
			if (ARCHER_DAMAGE_BOOSTING.contains(sp.getUUID())) return true; // 放行我们自己补的 1.5 倍伤害
			ARCHER_DAMAGE_BOOSTING.add(sp.getUUID());
			try {
				redealHurt(target, source, amount * 1.5f);
			} finally {
				ARCHER_DAMAGE_BOOSTING.remove(sp.getUUID());
			}
			return false; // 原始伤害作废，用加成后的伤害代替
		});

		// ============ 嗜痛人（红字）：连续同类型伤害每次-2最低1；脆骨症：攻击30%反馈自身一半伤害 ============
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return true;

			// 脆骨症：规则持有者发起攻击（目标不限）时，30% 概率反馈自身一半伤害
			// （必须放在 target 玩家检查之前——打怪物时 target 不是玩家，之前的写法永远不触发）
			if (source.getEntity() instanceof ServerPlayer atk && target != atk
					&& ChallengeManager.getInstance().getPlayerChallenges(atk.getUUID()).contains("brittle_bones")
					&& !ProfessionManager.getInstance().isImmuneToChallenge(atk.getUUID(), "brittle_bones")) {
				if (atk.getRandom().nextFloat() < 0.3f) {
					PENDING_BRITTLE_REFLECTS.add(new BrittleReflect(atk, amount / 2.0f));
				}
			}

			if (!(target instanceof ServerPlayer player)) return true;
			var ch = ChallengeManager.getInstance().getPlayerChallenges(player.getUUID());

			// 嗜痛人：连续同类型伤害每次下降 2 点（最低 1）；DamageType 注册表单例 → 用对象同一性比较
			if (ch.contains("pain_lover") && !PAIN_PROCESSING.contains(player.getUUID())) {
				net.minecraft.world.damagesource.DamageType type = source.type();
				net.minecraft.world.damagesource.DamageType last = painLastType.get(player.getUUID());
				int cnt = type == last ? painCount.getOrDefault(player.getUUID(), 0) + 1 : 0;
				painLastType.put(player.getUUID(), type);
				painCount.put(player.getUUID(), cnt);
				int reduced = Math.max(1, (int) amount - 2 * cnt);
				if (reduced < amount) {
					PAIN_PROCESSING.add(player.getUUID());
					try {
						redealHurt(player, source, reduced);
					} finally {
						PAIN_PROCESSING.remove(player.getUUID());
					}
					return false; // 原始伤害作废，用递减后的伤害代替
				}
			}
			return true;
		});

		// ============ 受到伤害增加（细分为火焰/摔落/凋零/雷击/细雪，按自定义百分比放大）============
		// 同样用"回环保护 + 重伤害"实现：命中玩家 → 按规则匹配伤害类型 → 放大后重打
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
			if (!(target instanceof ServerPlayer player)) return true;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return true;

			// 饥饿炸弹：被事件 TNT 爆炸波及的玩家获得 30 秒饥饿（死亡者效果随实体消失，无意义）
			if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
					&& source.getDirectEntity() != null
					&& player instanceof ServerPlayer hungerVictim
					&& RandomEventManager.getInstance().isHungerBombTnt(source.getDirectEntity().getUUID())) {
				hungerVictim.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0));
				hungerVictim.sendSystemMessage(Component.translatable("survival-contract.msg.hunger_bomb_hit"));
				return true;
			}

			// 恶魂庇佑（红字规则）：恶魂造成的任何伤害（直接命中/爆炸波及均算）都免疫，
			// 并获得瞬间治疗II + 30秒抗火。按伤害来源实体判定（恶魂只会用火球造成伤害），
			// 覆盖 26.2 中直接命中与爆炸两种伤害源形态 — 每次被攻击都触发，不限次数
			if (source.getEntity() instanceof net.minecraft.world.entity.monster.Ghast
					&& ChallengeManager.getInstance().getPlayerChallenges(player.getUUID()).contains("ghast_replacement")) {
				player.addEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 1));
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 600, 0));
				return false; // 免伤
			}

			// 一本万利（红字规则）：猪灵与猪灵蛮兵不会对自己造成伤害
			if (source.getEntity() instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) {
				if (ChallengeManager.getInstance().getPlayerChallenges(player.getUUID()).contains("piglin_deal")) {
					return false;
				}
			}

			String rule = null;
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) rule = "reduced_defense_fire";
			else if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) rule = "reduced_defense_fall";
			else if (source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) rule = "reduced_defense_wither";
			else if (source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)) rule = "reduced_defense_lightning";
			else if (source.is(net.minecraft.world.damagesource.DamageTypes.FREEZE)) rule = "reduced_defense_freeze";
			if (rule == null) return true;
			if (!ChallengeManager.getInstance().getPlayerChallenges(player.getUUID()).contains(rule)) return true;
			if (DEFENSE_PENALIZING.contains(player.getUUID())) return true; // 放行我们自己补的二次伤害
			if (ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), rule)) return true;
			float mult = 1.0f + (float) (getCustomPct(rule) / 100.0);
			DEFENSE_PENALIZING.add(player.getUUID());
			try {
				redealHurt(target, source, amount * mult);
			} finally {
				DEFENSE_PENALIZING.remove(player.getUUID());
			}
			return false;
		});

		// ============ 乐师特权：免疫负面状态效果（凋零除外） ============
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) -> {
			if (!(entity instanceof ServerPlayer sp)) return true;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return true;
			if (ProfessionManager.getInstance().getPlayerProfession(sp.getUUID())
					!= ProfessionManager.Profession.MUSICIAN) return true;
			if (effect.getEffect().value().isBeneficial()) return true;
			// 凋零除外：乐师仍会受到凋零效果
			if (effect.getEffect() == MobEffects.WITHER) return true;
			// 开局冻结期不拦截：冻结用的缓慢6/隐身必须正常生效（否则乐师冻结期可移动）
			var server = sp.level().getServer();
			if (server != null && server.getTickCount() < GameStateManager.getInstance().getGameStartTime()) return true;
			return false; // 拦截其余一切负面效果
		});

		// ============ 合成事件（CRAFT_ITEM 任务） ============
		// Fabric 25.x 中 PlayerCraftItemCallback 可能不存在，先注释，之后再补
		/*
		net.fabricmc.fabric.api.event.player.PlayerCraftItemCallback.EVENT.register((player, world, recipe, inv, amount) -> {
			if (!(player instanceof ServerPlayer sp)) return;
			if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
			String itemId = BuiltInRegistries.ITEM.getKey(recipe.getResultItem(world.registryAccess()).getItem()).toString();
			for (int i = 0; i < amount; i++) {
				TaskManager.getInstance().onItemAction(sp, itemId, TaskManager.TaskType.CRAFT_ITEM);
			}
		});
		*/
	}

	// ============ 辅助常量 & 队列 ============
	private static final List<ServerPlayer> PENDING_PLAYING_RESPAWN = new ArrayList<>();

	public static boolean isChallengeBook(ItemStack stack) {
		if (stack == null || stack.isEmpty()
				|| !(stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK))) return false;
		var name = stack.get(DataComponents.CUSTOM_NAME);
		return name != null && name.getString().contains(CHALLENGE_BOOK_CUSTOM_NAME);
	}

	/**
	 * 挑战书防丢：大厅里被丢弃的挑战书按 thrower UUID 返还给<b>丢出者本人</b>（所有玩家一致）。
	 * 丢出者不在线或背包满时留在地上（可手动拾取/下轮再试），不强制 discard。
	 */
	public static void returnDroppedChallengeBooks(MinecraftServer server) {
		ServerLevel lobby = LobbyManager.getInstance().getLobbyLevel();
		if (lobby == null) return;
		var spawn = LobbyManager.LOBBY_SPAWN;
		var area = new net.minecraft.world.phys.AABB(
				spawn.getX() - 300, spawn.getY() - 300, spawn.getZ() - 300,
				spawn.getX() + 300, spawn.getY() + 300, spawn.getZ() + 300);
		for (ItemEntity item : lobby.getEntitiesOfClass(ItemEntity.class, area)) {
			returnChallengeBook(server, item);
		}
	}

	/** 返还单个挑战书掉落物给丢出者本人；成功（已入包并清除实体）返回 true。 */
	public static boolean returnChallengeBook(MinecraftServer server, ItemEntity item) {
		if (!isChallengeBook(item.getItem())) return false;
		// 26.2：getOwner() 返回 Entity（非 UUID）；vanilla Q 丢出会写入该归属
		var throwerEntity = item.getOwner();
		UUID thrower = throwerEntity == null ? null : throwerEntity.getUUID();
		ServerPlayer owner = thrower == null ? null : server.getPlayerList().getPlayer(thrower);
		if (owner == null) return false; // 丢出者不在线：留在地上可手动拾取
		if (owner.getInventory().add(item.getItem())) {
			item.discard();
			owner.sendSystemMessage(Component.translatable("survival-contract.msg.book_returned"));
			return true;
		}
		return false;
	}

	private static boolean hasChallengeBook(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++)
			if (isChallengeBook(player.getInventory().getItem(i))) return true;
		return false;
	}

	private static void tryTeleportToLobby(ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
		LobbyManager dm = LobbyManager.getInstance();
		if (!dm.isLobbyReady()) return;
		dm.teleportToLobby(player);
		// 大厅观战者回大厅后保持旁观者模式（观战标记仍在，直到其再次输入 /sc spectate 解除）
		player.setGameMode(gsm.isLobbySpectator(player.getUUID())
				? GameType.SPECTATOR : GameType.ADVENTURE);
		if (!hasChallengeBook(player)) dm.giveChallengeBook(player);
		// 大厅阶段（含规则菜单）加入：若已选团队模式，立即自动分队并染色，不等开局
		if (gsm.isTeamMode()) {
			com.survivalcontract.team.TeamManager.getInstance().assignMissing(server(player));
			com.survivalcontract.team.TeamManager.getInstance().applyTeamColors(server(player));
		}
		player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
				"survival-contract.msg.welcome"));
		NetworkRegistry.sendSync(player);
		SurvivalContract.LOGGER.info("Teleported {} to lobby", player.getName().getString());
	}

	private static MinecraftServer server(ServerPlayer player) {
		return player.level().getServer();
	}

	/** 游戏进行中新加入的玩家 → 旁观者模式观战（传送到竞技场出生点上空），
	 *  游戏结束后随全员一起回到大厅（performResetForLobby 对所有在线玩家生效）。
	 *  大厅观战者（/sc spectate）开局时也走此路径进入竞技场观战。 */
	public static void teleportSpectatorToArena(MinecraftServer server, ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		// 防御：有参战者状态（分数表里有）= 掉线重连的参战者，绝不能被设为旁观者
		// （JOIN 分类与状态恢复之间的竞态会导致误判，这里兜底改道回重生点）
		if (gsm.hasPlayerState(player.getUUID())) {
			teleportToPlayingSpawn(player);
			return;
		}
		// 先登记追踪再传送：即使传送被登录竞态/异常打断，旁观者保持循环也能
		// 凭此标记自愈重试（标记后置会在失败时彻底丢失"观战者"身份）
		MID_GAME_SPECTATORS.add(player.getUUID());
		// 附属玩法：竞技场不存在（固定地图由附属模组自持）→ 观战落点交给附属模组，
		// 返回 true 表示已接管（自行设旁观者模式并传送到本玩法地图的观战点）。
		// 与 teleportToPlayingSpawn 的 "isAddon 短路" 同一思路：附属玩法的落点永远由自己决定，
		// 否则中途加入的玩家会被送到竞技场兜底世界（≈普通主世界），与内置玩法的观战体验不一致。
		if (gsm.isAddon()) {
			com.survivalcontract.api.ScPlayStyleProvider addon = gsm.getAddonProvider();
			if (addon != null && addon.onSpectatorJoin(server, player)) {
				ChallengeManager.getInstance().clearPlayerChallenges(player.getUUID());
				player.sendSystemMessage(Component.translatable("survival-contract.msg.spectator_joined"));
				NetworkRegistry.sendSync(player);
				return;
			}
		}
		ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (arena == null) {
			// 竞技场缺失时绝不退回原版主世界：否则玩家会被丢进"其他世界"，
			// 配合每 2 秒的防逃跑扫描还会反复传送。保持原地并报错（开局失败提示引导重开）
			SurvivalContract.LOGGER.error("Arena teleport skipped for {}: arena overworld missing",
					player.getName().getString());
			return;
		}
		BlockPos spawn = gsm.getPlayingSpawn();
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(arena, spawn.getX() + 0.5, spawn.getY() + 2.0, spawn.getZ() + 0.5,
				Set.of(), player.getYRot(), player.getXRot(), false);
		// 锁定旁观：游戏结束回大厅前，管理员切模式会被自动拉回旁观者
		// 旁观者无规则：清空规则与事件（防止残留点亮状态/默认职业干扰）
		ChallengeManager.getInstance().clearPlayerChallenges(player.getUUID());
		player.sendSystemMessage(Component.translatable("survival-contract.msg.spectator_joined"));
		NetworkRegistry.sendSync(player);
	}

	private static void teleportToPlayingSpawn(ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		// 附属玩法：竞技场不存在（固定地图由附属模组自持）→ 出生点由附属模组的
		// onServerTick/onPlayerJoin 兜底处理，这里直接短路，避免被传送到竞技场兜底世界
		if (gsm.isAddon()) return;
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (arena == null) {
			// 竞技场缺失时绝不退回原版主世界：否则玩家会被丢进"其他世界"，
			// 配合每 2 秒的防逃跑扫描还会反复传送。保持原地并报错（开局失败提示引导重开）
			SurvivalContract.LOGGER.error("Arena teleport skipped for {}: arena overworld missing",
					player.getName().getString());
			return;
		}

		// 神的谎言：已被淘汰的玩家保持旁观者模式（重生/防逃跑流程不得拉回生存）
		if (gsm.isGodsLies() && gsm.isEliminated(player.getUUID())) {
			if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
				player.setGameMode(GameType.SPECTATOR);
				player.sendSystemMessage(Component.translatable("survival-contract.msg.eliminated_spectator_kept"));
			}
			return;
		}

		// 防逃跑护栏：重生落到竞技场维度组之外（床被破坏后原版回退到真主世界出生点等）
		// → 强制送回竞技场固定重生点并重设重生配置。
		// 注意：在竞技场下界/末地重生（睡床）是合法行为，不干预。
		if (!com.survivalcontract.dimension.ArenaWorldManager.isArenaLevel(player.level().dimension())) {
			BlockPos spawn = gsm.getRespawnPoint(player.getUUID());
			player.teleportTo(arena, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
					Set.of(), player.getYRot(), player.getXRot(), false);
			player.setRespawnPosition(new net.minecraft.server.level.ServerPlayer.RespawnConfig(
					net.minecraft.world.level.storage.LevelData.RespawnData.of(
							com.survivalcontract.dimension.ArenaWorldManager.ARENA, spawn, player.getYRot(), player.getXRot()),
					true), false);
			player.setGameMode(GameType.SURVIVAL);
			player.setHealth(player.getMaxHealth());
			player.sendSystemMessage(Component.translatable("survival-contract.msg.sent_to_survival"));
			NetworkRegistry.sendSync(player);
			return;
		}

		// 玩家已有重生点（床/重生锚/开局固定点）→ vanilla 已按其重生，不干预（床可重设重生点）
		if (player.getRespawnConfig() != null) return;

		// 无任何重生点（床被破坏等）→ 传回固定重生点，坐标不波动
		BlockPos spawn = gsm.getRespawnPoint(player.getUUID());
		player.teleportTo(arena, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
				Set.of(), player.getYRot(), player.getXRot(), false);
		player.setGameMode(GameType.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		player.sendSystemMessage(Component.translatable("survival-contract.msg.respawned_survival_time", gsm.getRemainingTimeTicks() / 20));
		NetworkRegistry.sendSync(player);
	}

	private static boolean isHostile(Entity entity) {
		return entity instanceof Monster || entity instanceof Enemy;
	}

	/** 是否处于夜间（必须用该维度自身时钟——竞技场维度与主世界时钟不同步，旧实现误判白天） */
	private static boolean isNightNow(Level level) {
		return level.getOverworldClockTime() % 24000L >= 12000L;
	}

	/** 按 NBT 完整克隆一只生物（白月复活/赤月分裂）：清掉死亡标记/UUID/乘客、回满血后重新载入 */
	private static Mob cloneMobWithNbt(Mob source) {
		try {
			var out = net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(
					net.minecraft.util.ProblemReporter.DISCARDING);
			source.save(out);
			net.minecraft.nbt.CompoundTag tag = out.buildResult();
			tag.remove(LivingEntity.TAG_DEATH_TIME);
			tag.remove(Entity.TAG_UUID);
			tag.remove(Entity.TAG_PASSENGERS);
			tag.putFloat(LivingEntity.TAG_HEALTH, source.getMaxHealth());
			var loaded = net.minecraft.world.entity.EntityType.loadEntityRecursive(
					source.getType(), tag, source.level(),
					net.minecraft.world.entity.EntitySpawnReason.LOAD, e -> e);
			return loaded instanceof Mob clone ? clone : null;
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to clone mob {}: {}",
					BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()), e.getMessage());
			return null;
		}
	}

	/** 行商随机礼物：哭泣黑曜石2-4 / 末影珍珠2-4 / 灵魂急行I铁靴 / 抗火药水 */
	private static ItemStack randomTraderGift(ServerLevel level, net.minecraft.util.RandomSource r) {
		return switch (r.nextInt(4)) {
			case 0 -> new ItemStack(Items.CRYING_OBSIDIAN, 2 + r.nextInt(3));
			case 1 -> new ItemStack(Items.ENDER_PEARL, 2 + r.nextInt(3));
			case 2 -> {
				ItemStack boots = new ItemStack(Items.IRON_BOOTS);
				var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
						.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SOUL_SPEED);
				var mut = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
						boots.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
								net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
				mut.set(holder, 1);
				boots.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, mut.toImmutable());
				yield boots;
			}
			default -> {
				ItemStack potion = new ItemStack(Items.POTION);
				potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
						new net.minecraft.world.item.alchemy.PotionContents(Potions.FIRE_RESISTANCE));
				yield potion;
			}
		};
	}

	// ============ 愚者职业：丢出钻石/绿宝石随机获得生存模式可非作弊获得的物品/方块 ============

	/** 愚者宝石奖池（懒加载：类初始化时机早于数据组件绑定，ItemStack 不能在 static 初始化时创建）。
	 *  全部为生存模式可非作弊获得的物品/方块（含稀有战利品、建筑/自然方块、红石组件与酿造药水） */
	private static ItemStack[] foolGemPool;

	private static ItemStack[] foolGemPool() {
		if (foolGemPool == null) {
			foolGemPool = new ItemStack[] {
			// 矿物与矿物块
			new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT),
			new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.DIAMOND),
			new ItemStack(Items.EMERALD), new ItemStack(Items.LAPIS_LAZULI),
			new ItemStack(Items.REDSTONE), new ItemStack(Items.COAL), new ItemStack(Items.QUARTZ),
			new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.NETHERITE_SCRAP),
			new ItemStack(Items.NETHERITE_INGOT),
			new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.GOLD_BLOCK),
			new ItemStack(Items.DIAMOND_BLOCK), new ItemStack(Items.EMERALD_BLOCK),
			new ItemStack(Items.OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN),
			new ItemStack(Items.SLIME_BALL), new ItemStack(Items.SPONGE),
			// 掉落物与下界/末地战利品
			new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.MAGMA_CREAM),
			new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.BLAZE_POWDER),
			new ItemStack(Items.PHANTOM_MEMBRANE), new ItemStack(Items.ENDER_PEARL),
			new ItemStack(Items.ENDER_EYE), new ItemStack(Items.NETHER_STAR),
			new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.ELYTRA),
			new ItemStack(Items.SHULKER_SHELL), new ItemStack(Items.DRAGON_BREATH),
			new ItemStack(Items.DRAGON_HEAD), new ItemStack(Items.WITHER_SKELETON_SKULL),
			new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
			new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.NAUTILUS_SHELL),
			new ItemStack(Items.PRISMARINE_CRYSTALS), new ItemStack(Items.ECHO_SHARD),
			new ItemStack(Items.NETHER_WART),
			// 武器装备（不可堆叠 → 每次至多 1 个）
			new ItemStack(Items.TRIDENT), new ItemStack(Items.MACE), new ItemStack(Items.BREEZE_ROD),
			new ItemStack(Items.WIND_CHARGE), new ItemStack(Items.HEAVY_CORE),
			new ItemStack(Items.TRIAL_KEY), new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW),
			new ItemStack(Items.SHIELD), new ItemStack(Items.IRON_SWORD),
			new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.DIAMOND_SWORD),
			new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.IRON_CHESTPLATE),
			new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.GOLDEN_HORSE_ARMOR),
			new ItemStack(Items.DIAMOND_HORSE_ARMOR), new ItemStack(Items.SADDLE),
			new ItemStack(Items.NAME_TAG), new ItemStack(Items.LEAD),
			// 建筑方块
			new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.COBBLESTONE),
			new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.CHISELED_STONE_BRICKS),
			new ItemStack(Items.GLASS), new ItemStack(Items.BRICKS), new ItemStack(Items.GLOWSTONE),
			new ItemStack(Items.SEA_LANTERN), new ItemStack(Items.IRON_BARS),
			new ItemStack(Items.SANDSTONE), new ItemStack(Items.DEEPSLATE_BRICKS),
			new ItemStack(Items.QUARTZ_BLOCK), new ItemStack(Items.PRISMARINE),
			new ItemStack(Items.PURPUR_BLOCK), new ItemStack(Items.LADDER),
			new ItemStack(Items.SCAFFOLDING), new ItemStack(Items.TORCH), new ItemStack(Items.LANTERN),
			new ItemStack(Items.GLASS_PANE),
			// 自然方块
			new ItemStack(Items.DIRT), new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.OAK_LOG),
			new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.BIRCH_LOG), new ItemStack(Items.SAND),
			new ItemStack(Items.GRAVEL), new ItemStack(Items.CLAY), new ItemStack(Items.SNOW_BLOCK),
			new ItemStack(Items.ICE), new ItemStack(Items.PACKED_ICE), new ItemStack(Items.BLUE_ICE),
			new ItemStack(Items.MOSS_BLOCK), new ItemStack(Items.CALCITE), new ItemStack(Items.TUFF),
			new ItemStack(Items.DRIPSTONE_BLOCK), new ItemStack(Items.MOSSY_COBBLESTONE),
			new ItemStack(Items.PUMPKIN), new ItemStack(Items.COBWEB), new ItemStack(Items.CACTUS),
			new ItemStack(Items.SUGAR_CANE), new ItemStack(Items.BAMBOO),
			// 红石组件
			new ItemStack(Items.REDSTONE_BLOCK), new ItemStack(Items.REDSTONE_TORCH),
			new ItemStack(Items.REDSTONE_LAMP), new ItemStack(Items.PISTON),
			new ItemStack(Items.STICKY_PISTON), new ItemStack(Items.OBSERVER),
			new ItemStack(Items.HOPPER), new ItemStack(Items.DROPPER), new ItemStack(Items.DISPENSER),
			new ItemStack(Items.REPEATER), new ItemStack(Items.COMPARATOR), new ItemStack(Items.LEVER),
			new ItemStack(Items.TARGET), new ItemStack(Items.NOTE_BLOCK),
			new ItemStack(Items.DAYLIGHT_DETECTOR), new ItemStack(Items.TRIPWIRE_HOOK),
			new ItemStack(Items.RAIL), new ItemStack(Items.POWERED_RAIL),
			new ItemStack(Items.DETECTOR_RAIL), new ItemStack(Items.ACTIVATOR_RAIL),
			new ItemStack(Items.TNT), new ItemStack(Items.SLIME_BLOCK),
			new ItemStack(Items.HONEY_BLOCK), new ItemStack(Items.SCULK_SENSOR),
			new ItemStack(Items.CALIBRATED_SCULK_SENSOR), new ItemStack(Items.CRAFTER),
			// 实用道具
			new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET),
			new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.BED.red()), new ItemStack(Items.BEACON),
			new ItemStack(Items.RESPAWN_ANCHOR), new ItemStack(Items.CONDUIT),
			new ItemStack(Items.GRINDSTONE), new ItemStack(Items.ANVIL),
			new ItemStack(Items.ENCHANTING_TABLE), new ItemStack(Items.EXPERIENCE_BOTTLE),
			new ItemStack(Items.ENCHANTED_BOOK), new ItemStack(Items.GOLDEN_CARROT),
			// 药水（酿造可获得，不可堆叠 → 至多 1 个）
			PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE),
			PotionContents.createItemStack(Items.POTION, Potions.HEALING),
			PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING),
			PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS),
			PotionContents.createItemStack(Items.POTION, Potions.STRENGTH),
			PotionContents.createItemStack(Items.POTION, Potions.REGENERATION),
			PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION),
			PotionContents.createItemStack(Items.POTION, Potions.WATER_BREATHING),
			PotionContents.createItemStack(Items.POTION, Potions.SLOW_FALLING),
			PotionContents.createItemStack(Items.POTION, Potions.INVISIBILITY),
			PotionContents.createItemStack(Items.POTION, Potions.LEAPING)
			};
		}
		return foolGemPool;
	}

	/** 愚者宝石结算：随机 6~8 件奖池物品（每件恰好 1 个），放不下直接丢在脚下。 */
	private static void grantFoolGemLoot(ServerPlayer player) {
		ItemStack[] pool = foolGemPool();
		int count = 6 + player.getRandom().nextInt(3); // 每次 6~8 件
		for (int i = 0; i < count; i++) {
			ItemStack gift = pool[player.getRandom().nextInt(pool.length)].copy();
			gift.setCount(1); // 每件恰好 1 个
			if (!player.getInventory().add(gift)) com.survivalcontract.McCompat.drop(player, gift, false);
		}
		player.sendSystemMessage(Component.translatable("survival-contract.msg.jester_gem_loot", count));
		player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
	}

	// ============ 乐师职业：音符盒音色 → 药水效果 ============

	/** 乐师音色映射：木棍/烈焰棒互动音符盒时，按音色为周围玩家施加对应药水效果。
	 *  未列出的音色（生物头颅类等）返回 null，走纯原版行为；铜小号的氧化变体与小号同映射。 */
	private static net.minecraft.core.Holder<MobEffect> musicianInstrumentEffect(
			net.minecraft.world.level.block.state.properties.NoteBlockInstrument instrument) {
		return switch (instrument) {
			case HARP -> MobEffects.REGENERATION;           // 竖琴（默认）→ 生命恢复
			case BASS -> MobEffects.RESISTANCE;             // 贝斯 → 抗性提升
			case BASEDRUM -> MobEffects.STRENGTH;           // 底鼓 → 力量
			case SNARE -> MobEffects.HASTE;                 // 小军鼓 → 急迫
			case HAT -> MobEffects.SPEED;                   // 踩镲 → 速度
			case BELL -> MobEffects.LUCK;                   // 铃铛 → 幸运
			case FLUTE -> MobEffects.NIGHT_VISION;          // 长笛 → 夜视
			case XYLOPHONE -> MobEffects.JUMP_BOOST;        // 木琴 → 跳跃提升
			case IRON_XYLOPHONE -> MobEffects.SLOW_FALLING; // 颤音琴 → 缓慢降落
			case BIT -> MobEffects.SATURATION;              // 方波 → 饱和
			case PLING -> MobEffects.WATER_BREATHING;       // 电钢琴 → 水下呼吸
			case CHIME -> MobEffects.CONDUIT_POWER;         // 管钟 → 潮涌能量
			case DIDGERIDOO -> MobEffects.DARKNESS;         // 迪吉里杜管 → 黑暗
			case COW_BELL -> MobEffects.BAD_OMEN;           // 牛铃 → 不祥之兆
			case GUITAR -> MobEffects.INVISIBILITY;         // 吉他 → 隐身
			case BANJO -> MobEffects.DOLPHINS_GRACE;        // 班卓琴 → 海豚的恩惠
			case TRUMPET, TRUMPET_EXPOSED, TRUMPET_OXIDIZED, TRUMPET_WEATHERED -> MobEffects.WEAKNESS; // 小号 → 虚弱
			default -> null;
		};
	}

	// ============ 乐师演奏状态（跨局清理见 resetSessionStatics / performResetForLobby） ============
	/** 乐师上次演奏时刻（gameTime）：与上次间隔 ≤2s 视为持续演奏，友方状态时长可叠加 */
	private static final Map<UUID, Long> MUSICIAN_LAST_PLAY = new HashMap<>();
	/** 乐师连奏历史 [音色 ordinal, gameTime]：5 秒内凑齐 3 种不同音色触发合奏奖励 */
	private static final Map<UUID, java.util.ArrayDeque<long[]>> MUSICIAN_TONE_HISTORY = new HashMap<>();
	/** 乐师音波攻击（底鼓/颤音琴/小号）下次可触发时刻（gameTime，每 3 秒最多一次） */
	private static final Map<UUID, Long> MUSICIAN_NEXT_SONIC = new HashMap<>();

	/**
	 * 乐师演奏结算：
	 * <ul>
	 *   <li>正面音色为友方（含自身）提供增益，自身时长仅刷新基础值；敌方吃负面音色（黑暗/不祥之兆/虚弱）</li>
	 *   <li>持续演奏（间隔≤2s）：友方每次演奏在剩余时长上 +15s（可叠加，至多 10 分钟）；非连续演奏不缩短已有时长</li>
	 *   <li>5s 内演奏 3 种不同音色：全体友方获得 15s 随机正面状态（从所奏音色的正面效果中随机，不限距离）</li>
	 *   <li>底鼓/颤音琴/小号（含氧化变体）：半径 16 格内除自身与队友外所有生物受 2 点真实伤害 + 小幅击退（每 3s 一次，第五十四批：范围 32→16 + 队友免伤）</li>
	 * </ul>
	 */
	private static void musicianPerform(ServerPlayer musician, ServerLevel level, BlockPos notePos,
			net.minecraft.world.level.block.state.properties.NoteBlockInstrument instrument,
			net.minecraft.core.Holder<MobEffect> effect, boolean blaze) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
		long now = level.getGameTime();
		int amplifier = blaze ? 1 : 0;       // 木棍=1级，烈焰棒=2级
		int baseTicks = (blaze ? 30 : 15) * 20; // 木棍=15秒，烈焰棒=30秒
		var center = net.minecraft.world.phys.Vec3.atCenterOf(notePos);
		var myTeam = gsm.getPlayerTeam(musician.getUUID());

		Long last = MUSICIAN_LAST_PLAY.get(musician.getUUID());
		boolean chain = last != null && now - last <= 40; // 持续演奏：间隔 ≤2s
		boolean beneficial = effect.value().isBeneficial();

		for (ServerPlayer target : level.players()) {
			if (!target.isAlive() || target.isSpectator()) continue;
			boolean self = target == musician;
			// 敌我判定：团队模式同队为友方（NONE 观战者除外）；个人模式其余玩家均为敌方
			boolean friendly = self || (gsm.isTeamMode()
					&& myTeam != GameStateManager.Team.NONE && myTeam == gsm.getPlayerTeam(target.getUUID()));
			if (friendly != beneficial) continue; // 友方只受正面、敌方只受负面
			if (!self && target.distanceToSqr(center) > 16 * 16) continue; // 以音符盒为中心，半径16格
			if (self) {
				target.addEffect(new MobEffectInstance(effect, baseTicks, amplifier)); // 自身：时长仅刷新
			} else if (friendly) {
				var cur = target.getEffect(effect);
				int ticks = baseTicks;
				if (cur != null && cur.getAmplifier() == amplifier) {
					ticks = chain ? Math.min(cur.getDuration() + 15 * 20, 600 * 20)  // 持续演奏叠加，至多10min
							: Math.max(cur.getDuration(), baseTicks);                 // 非连续演奏不缩短已有时长
				}
				target.addEffect(new MobEffectInstance(effect, ticks, amplifier));
			} else {
				target.addEffect(new MobEffectInstance(effect, baseTicks, amplifier)); // 敌方：基础时长
			}
		}
		MUSICIAN_LAST_PLAY.put(musician.getUUID(), now);

		// 合奏奖励：5s 内演奏 3 种不同音色 → 全体友方 15s 随机正面状态（仅取所奏音色中的正面效果）
		var history = MUSICIAN_TONE_HISTORY.computeIfAbsent(musician.getUUID(), k -> new java.util.ArrayDeque<>());
		history.addLast(new long[]{instrument.ordinal(), now});
		while (!history.isEmpty() && now - history.peekFirst()[1] > 100) history.pollFirst(); // 保留 5s 窗口
		Set<Long> distinct = new HashSet<>();
		for (long[] e : history) distinct.add(e[0]);
		if (distinct.size() >= 3) {
			List<net.minecraft.core.Holder<MobEffect>> rewardPool = new ArrayList<>();
			for (long[] e : history) {
				var played = net.minecraft.world.level.block.state.properties.NoteBlockInstrument.values()[(int) e[0]];
				var fx = musicianInstrumentEffect(played);
				if (fx != null && fx.value().isBeneficial() && !rewardPool.contains(fx)) rewardPool.add(fx);
			}
			if (!rewardPool.isEmpty()) {
				var reward = rewardPool.get(musician.getRandom().nextInt(rewardPool.size()));
				for (ServerPlayer target : level.players()) {
					if (!target.isAlive() || target.isSpectator()) continue;
					boolean friendly = target == musician || (gsm.isTeamMode()
							&& myTeam != GameStateManager.Team.NONE && myTeam == gsm.getPlayerTeam(target.getUUID()));
					if (friendly) target.addEffect(new MobEffectInstance(reward, 15 * 20, 0)); // 全体友方（不限距离）
				}
				history.clear(); // 触发后重置，需重新凑齐 3 种音色
			}
		}

		// 音波攻击：底鼓/颤音琴/小号（含氧化变体）→ 半径16格内除自身与队友外所有生物 2 点真实伤害
		// + 小幅击退（每3s一次；第五十四批：范围 32→16 格，友伤关闭——同队玩家不受音波攻击）
		if (instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM
				|| instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.IRON_XYLOPHONE
				|| instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.TRUMPET
				|| instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.TRUMPET_EXPOSED
				|| instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.TRUMPET_OXIDIZED
				|| instrument == net.minecraft.world.level.block.state.properties.NoteBlockInstrument.TRUMPET_WEATHERED) {
			Long next = MUSICIAN_NEXT_SONIC.get(musician.getUUID());
			if (next == null || now >= next) {
				MUSICIAN_NEXT_SONIC.put(musician.getUUID(), now + 60);
				var box = net.minecraft.world.phys.AABB.ofSize(center, 32, 32, 32);
				for (var ent : level.getEntitiesOfClass(LivingEntity.class, box,
						e -> e != musician && e.isAlive() && e.distanceToSqr(center) <= 16 * 16
								&& !(e instanceof ServerPlayer victim
										&& gsm.isTeamMode()
										&& myTeam != GameStateManager.Team.NONE
										&& myTeam == gsm.getPlayerTeam(victim.getUUID())))) {
					ent.hurt(ent.damageSources().magic(), 2.0f); // 真实伤害（无视护甲）
					// 小幅击退：自音符盒向外的水平脉冲 + 轻微抬升（26.2 的 knockback 签名需 DamageSource，直接推速度更稳）
					double dx = ent.getX() - center.x, dz = ent.getZ() - center.z;
					double len = Math.sqrt(dx * dx + dz * dz);
					if (len < 1.0E-4) { dx = 0; dz = 0; len = 1; }
					ent.push(dx / len * 0.35, 0.2, dz / len * 0.35);
				}
			}
		}
	}

	/** 手鼓解锁条件：获得原版成就「与火共舞」（Into Fire = nether/obtain_blaze_rod，获得烈焰棒） */
	private static boolean hasDanceWithFireAdvancement(ServerPlayer player) {
		var server = player.level().getServer();
		if (server == null) return false;
		var holder = server.getAdvancements().get(
				Identifier.fromNamespaceAndPath("minecraft", "nether/obtain_blaze_rod"));
		if (holder == null) return false;
		return player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** 中立生物判定（neutral_hostile 规则用）：非本性敌对的可移动生物主动攻击规则持有者。
	 *  僵尸猪灵类是 Monster 子类但行为中立，单独纳入。 */
	private static boolean isNeutralMob(Entity entity) {
		if (entity instanceof net.minecraft.world.entity.monster.zombie.ZombifiedPiglin) return true;
		return entity instanceof net.minecraft.world.entity.PathfinderMob
				&& !(entity instanceof Monster);
	}

	/** 可反伤的"友好生物"判定：排除载具（所有类型船与矿车）— 打船/矿车不应触发反击 */
	private static boolean isCounterAttackable(Entity entity) {
		return !isHostile(entity)
				&& !(entity instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat)
				&& !(entity instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart);
	}

	private static void applyLobbyBuffs(ServerPlayer player) {
		// 有限时长（5 秒，大厅阶段每 tick 重施、等效无限）——离开大厅（开局/死亡重回）后
		// 效果自然到期，修复隐藏的无限饱和/生命恢复残留进对局的问题（此前为 -1 无限时长且从不清除）
		player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 100, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100, 0, false, false, false));
	}

	// 属性修饰符固定 id（保证重复应用时替换而非叠加）
	private static final net.minecraft.resources.Identifier ATTACK_REDUCE_ID =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("survival-contract", "attack_reduce");
	private static final net.minecraft.resources.Identifier ARMOR_REDUCE_ID =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("survival-contract", "armor_reduce");

	/** 读取可自定义挑战的百分比值（默认 50） */
	private static double getCustomPct(String challengeId) {
		Challenge c = ChallengeManager.getInstance().getChallenge(challengeId);
		return c != null ? Math.max(5, Math.min(90, c.getCustomValue())) : 50.0;
	}

	/** 设置/移除玩家的乘法属性修饰符（pct=0 时移除） */
	private static void applyAttrModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			net.minecraft.resources.Identifier id, String name, double pct) {
		var inst = player.getAttribute(attr);
		if (inst == null) return;
		var existing = inst.getModifier(id);
		if (pct == 0.0) {
			if (existing != null) inst.removeModifier(id);
			return;
		}
		var mod = new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, pct / 100.0,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (existing != null) {
			if (Math.abs(existing.amount() - mod.amount()) < 0.0001) return; // 无变化不重设
			inst.removeModifier(id);
		}
		inst.addTransientModifier(mod);
	}

	/**
	 * 贫瘠铁矿结算（本 tick 末尾执行，掉落实体必已生成）——连锁采掘版：
	 * 先把本 tick 全部新生粗铁掉落物按维度分组、按"最近矿块中心"唯一归属（距离 ≤1.0），
	 * 再对每个矿块独立掷骰：50% 空矿 / 10% 双倍 / 40% 正常（静默）——每块互不影响。
	 * 纯掉落概率规则，不涉及扣分。数值固定，不可自定义。
	 */
	private static void processIronOreRolls(List<OreRoll> rolls) {
		if (rolls.isEmpty()) return;
		// 按维度分组（跨维度同时挖掘时判定互不污染）
		Map<ServerLevel, List<OreRoll>> byLevel = new java.util.IdentityHashMap<>();
		for (OreRoll r : rolls) byLevel.computeIfAbsent(r.level(), k -> new ArrayList<>()).add(r);
		for (var entry : byLevel.entrySet()) {
			ServerLevel level = entry.getKey();
			List<OreRoll> levelRolls = entry.getValue();
			// 联合判定框：本 tick 所有矿块的整体包围盒，一次收集全部新生粗铁掉落物
			double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (OreRoll r : levelRolls) {
				minX = Math.min(minX, r.pos().getX() - 1.0);
				minY = Math.min(minY, r.pos().getY() - 1.0);
				minZ = Math.min(minZ, r.pos().getZ() - 1.0);
				maxX = Math.max(maxX, r.pos().getX() + 2.0);
				maxY = Math.max(maxY, r.pos().getY() + 2.0);
				maxZ = Math.max(maxZ, r.pos().getZ() + 2.0);
			}
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
					new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ),
					ie -> ie.getItem().is(Items.RAW_IRON) && ie.getAge() <= 2);
			// 每个掉落物归属最近的矿块中心（手算欧氏距离平方，≤1.0 才算命中该块，
			// 修正旧判定框 [pos-0.5, pos+1.5] 正向多出 1 格导致的相邻误收）
			Map<BlockPos, List<ItemEntity>> assigned = new HashMap<>();
			for (ItemEntity ie : drops) {
				BlockPos best = null;
				double bestD = Double.MAX_VALUE;
				for (OreRoll r : levelRolls) {
					BlockPos c = r.pos();
					double dx = ie.getX() - (c.getX() + 0.5);
					double dy = ie.getY() - (c.getY() + 0.5);
					double dz = ie.getZ() - (c.getZ() + 0.5);
					double d = dx * dx + dy * dy + dz * dz;
					if (d < bestD) { bestD = d; best = c; }
				}
				if (best != null && bestD <= 1.0) {
					assigned.computeIfAbsent(best, k -> new ArrayList<>()).add(ie);
				}
			}
			// 逐块独立掷骰（50% 空矿 / 10% 双倍 / 40% 正常）
			for (OreRoll r : levelRolls) {
				List<ItemEntity> mine = assigned.get(r.pos());
				if (mine == null || mine.isEmpty()) continue; // 掉落已归属别块/已被拾取 → 静默
				int rollNum = ORE_RANDOM.nextInt(100);
				if (rollNum >= 60) continue; // 40% 正常掉落
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(r.playerId());
				if (rollNum < 50) {
					// 50% 不掉落
					for (ItemEntity ie : mine) ie.discard();
					if (player != null) {
						player.sendSystemMessage(Component.translatable("survival-contract.msg.iron_ore_empty"));
						player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
					}
				} else {
					// 10% 双倍
					for (ItemEntity ie : mine) {
						ItemStack stack = ie.getItem();
						stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() * 2));
					}
					if (player != null) {
						player.sendSystemMessage(Component.translatable("survival-contract.msg.iron_ore_double"));
						player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
					}
				}
			}
		}
	}

	/** 直发音效包给指定客户端（绕过服务端广播/范围过滤）：
	 *  Entity.playSound 在跨维度切换后同一 tick 内会被客户端丢弃，直发包保证送达。 */
	private static void playSoundDirect(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		var holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				holder, net.minecraft.sounds.SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	// ============ 板块规则效果（10-06：与神合约/久病成患/环境恶化） ============

	/** 愚者猪灵交易目标物品：抗火药水 / 末影珍珠 / 灵魂疾行铁靴 / 哭泣黑曜石 */
	private static boolean isFoolBarterTarget(ItemStack s) {
		if (s.is(Items.ENDER_PEARL) || s.is(Items.CRYING_OBSIDIAN)) return true;
		if (s.is(Items.POTION)) {
			var pc = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
			if (pc != null && (pc.is(Potions.FIRE_RESISTANCE) || pc.is(Potions.LONG_FIRE_RESISTANCE))) return true;
		}
		if (s.is(Items.IRON_BOOTS)) {
			var ench = s.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
			if (ench != null && ench.keySet().stream().anyMatch(h ->
					h.unwrapKey().map(k -> k == net.minecraft.world.item.enchantment.Enchantments.SOUL_SPEED).orElse(false))) {
				return true;
			}
		}
		return false;
	}

	/** 板块规则逐tick/每秒效果：无法自然回血、脆骨症、背包锁定、属性削弱、疾病、低血糖、暴露狂 */
	private static void tickSectionRules(MinecraftServer server, ServerPlayer player, Set<String> challenges) {
		UUID u = player.getUUID();
		boolean perSecond = server.getTickCount() % 20 == 0;
		var cm = ChallengeManager.getInstance();
		var pm = ProfessionManager.getInstance();

		// --- 无法自然回血：饱食度>=18 时的原版自然回复回退（药水/再生/瞬间治疗不受影响）---
		float prev = regenPrevHealth.getOrDefault(u, player.getHealth());
		float cur = player.getHealth();
		if (challenges.contains("no_natural_regen") && cur > prev
				&& prev > 0.0f && !player.isDeadOrDying()
				&& player.getFoodData().getFoodLevel() >= 18
				&& !player.hasEffect(MobEffects.REGENERATION)
				&& !player.hasEffect(MobEffects.INSTANT_HEALTH)) {
			player.setHealth(prev);
			cur = prev;
		}
		regenPrevHealth.put(u, cur);

		// --- 脆骨症：摔落伤害起始判定 1 格（>3 格由原版结算 fd-3，这里补 2 点凑成 fd-1）---
		boolean onGround = player.onGround();
		boolean wasOnGround = prevOnGround.getOrDefault(u, true);
		float fall = (float) player.fallDistance;
		// 斗士免疫脆骨症：不补摔落伤害
		if (challenges.contains("brittle_bones") && !pm.isImmuneToChallenge(u, "brittle_bones")
				&& onGround && !wasOnGround && fall > 1.0f) {
			float extra = fall <= 3.0f ? fall - 1.0f : 2.0f;
			if (extra > 0) player.hurt(player.damageSources().fall(), extra);
		}
		prevOnGround.put(u, onGround);

		// --- 低血糖：饱食度消耗速度翻倍（每 tick 追加消耗；斗士免疫低血糖）---
		if (challenges.contains("hypoglycemia") && !pm.isImmuneToChallenge(u, "hypoglycemia")) {
			player.getFoodData().addExhaustion(0.0025f);
		}

		// --- 背包格子锁定：快捷栏(0-8)永不禁用；点击路径由 LockedSlots 双端拦截（无屏障占位物品），
		//     这里每 tick 兜底清扫非点击途径进入锁定格的物品（移回可用区，放不下丢地上）---
		if (challenges.contains("inv_slot_lock")) {
			Challenge ic = cm.getChallenge("inv_slot_lock");
			int v = ic != null ? ic.getCustomValue() : 18;
			int lockCount = v == 9 ? 27 : v == 27 ? 9 : 18;
			var inv = player.getInventory();
			for (int i = 9; i < 9 + lockCount; i++) {
				ItemStack locked = inv.getItem(i);
				if (locked.isEmpty()) continue;
				inv.setItem(i, ItemStack.EMPTY);
				if (!inv.add(locked)) com.survivalcontract.McCompat.drop(player, locked, false); // 可用区放不下 → 丢地上
			}
		}

		// --- 失重：1.5 秒内连续两次起跳 → 10 秒漂浮（触发后重新计数）---
		if (challenges.contains("zero_gravity")) {
			boolean wasGround = zeroGWasGround.getOrDefault(u, true);
			boolean onGroundNow = player.onGround();
			if (wasGround && !onGroundNow && player.getDeltaMovement().y > 0.1) {
				long nowTick = player.level().getGameTime();
				long last = zeroGLastJump.getOrDefault(u, -1000L);
				if (nowTick - last <= 30) {
					player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 0)); // 漂浮 10 秒
					player.sendSystemMessage(Component.translatable("survival-contract.msg.weightlessness"));
					zeroGLastJump.remove(u); // 重新计数，需再次连跳两次
				} else {
					zeroGLastJump.put(u, nowTick);
				}
			}
			zeroGWasGround.put(u, onGroundNow);
		}

		// --- 失眠症修复：夜间上床睡觉时立即移除残留凋零（上床前施加的凋零最长残留 3 秒，
		//     会在睡觉中结算伤害并触发原版"受伤强制醒来"）---
		if (challenges.contains("insomnia") && player.isSleeping() && player.hasEffect(MobEffects.WITHER)) {
			player.removeEffect(MobEffects.WITHER);
		}

		if (!perSecond) return;

		// --- 血量上限下降 / 挖掘速度下降（可自定义%，属性修饰符，每秒校正）---
		applyPctModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
				"max_health", challenges.contains("max_health_down")
						? -cm.getChallenge("max_health_down").getCustomValue() / 100.0 : 0);
		if (challenges.contains("max_health_down") && player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		applyPctModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED,
				"mining_slow", challenges.contains("mining_slow")
						? -cm.getChallenge("mining_slow").getCustomValue() / 100.0 : 0);

		// --- 失眠症：22:00-6:00 迟缓1+凋零1；睡眠累计15秒提前解除 ---
		if (challenges.contains("insomnia")) {
			// 26.2 新时钟系统：getOverworldClockTime = 主世界时钟总 tick，%24000 = 当日时间（0 = 6:00）
			long dayTime = player.level().getOverworldClockTime() % 24000L;
			boolean night = dayTime >= 16000L;
			if (!night) {
				insomniaCuredTonight.remove(u);
				insomniaSleepTicks.remove(u);
				if (insomniaApplied.remove(u)) {
					player.removeEffect(MobEffects.SLOWNESS);
					player.removeEffect(MobEffects.WITHER);
				}
			} else {
				// 累计制：当天分段睡觉也能凑满 15 秒（不在此处清零，白天统一重置）
				if (player.isSleeping()) {
					long slept = insomniaSleepTicks.merge(u, 20L, Long::sum);
					if (slept >= 300) insomniaCuredTonight.add(u);
				}
				if (!player.isSleeping() && !insomniaCuredTonight.contains(u)) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
					player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
					insomniaApplied.add(u);
					// 失眠扣血：不睡觉时每秒扣 1 滴（26.2 凋零效果本身不扣血，这里手动结算）
					player.hurt(player.damageSources().wither(), 1.0f);
				} else if (insomniaCuredTonight.contains(u) && insomniaApplied.remove(u)) {
					player.removeEffect(MobEffects.SLOWNESS);
					player.removeEffect(MobEffects.WITHER);
				}
			}
		}

		// --- 尘肺病：负层（Y<0）连续 1 分钟 → 无限时间中毒，离开负层清除（进出均有系统消息；矿工免疫）---
		if (challenges.contains("dust_lung") && !pm.isImmuneToChallenge(u, "dust_lung")) {
			if (player.getY() < 0) {
				if (dustLungTicks.merge(u, 1, Integer::sum) == 60) {
					player.addEffect(new MobEffectInstance(MobEffects.POISON, -1, 0, false, false, true));
					player.sendSystemMessage(Component.translatable("survival-contract.msg.dust_lung_on"));
				}
			} else if (dustLungTicks.remove(u) != null && player.hasEffect(MobEffects.POISON)) {
				player.removeEffect(MobEffects.POISON);
				player.sendSystemMessage(Component.translatable("survival-contract.msg.dust_lung_off"));
			}
		}

		// --- 高原水肿：高于 128 层连续 1 分钟 → 无限时间中毒，低于 128 层清除（进出均有系统消息）---
		if (challenges.contains("altitude_edema")) {
			if (player.getY() > 128) {
				if (altitudeTicks.merge(u, 1, Integer::sum) == 60) {
					player.addEffect(new MobEffectInstance(MobEffects.POISON, -1, 0, false, false, true));
					player.sendSystemMessage(Component.translatable("survival-contract.msg.altitude_edema_on"));
				}
			} else if (altitudeTicks.remove(u) != null && player.hasEffect(MobEffects.POISON)) {
				player.removeEffect(MobEffects.POISON);
				player.sendSystemMessage(Component.translatable("survival-contract.msg.altitude_edema_off"));
			}
		}

		// --- 减压症：完全入水后 5 秒内就离开水面 → 15 秒缓慢2+虚弱2+挖掘疲劳1（满 5 秒再出水无碍）---
		if (challenges.contains("decompression")) {
			boolean under = player.isUnderWater();
			if (under) {
				decompressionTicks.merge(u, 1, Integer::sum);
			} else if (decompressionUnderwater.getOrDefault(u, false)) {
				// 刚离开水面的瞬间结算
				int secs = decompressionTicks.getOrDefault(u, 0);
				decompressionTicks.remove(u);
				if (secs < 5) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 300, 1));
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
					player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 300, 0));
					player.sendSystemMessage(Component.translatable("survival-contract.msg.decompression"));
				}
			}
			decompressionUnderwater.put(u, under);
		}

		// --- 失温症：覆雪/冰刺地形 5 秒后开始冻结伤害（全套皮革免疫）---
		if (challenges.contains("hypothermia")) {
			String biomeId = biomeIdAt(player);
			boolean cold = biomeId.startsWith("minecraft:snowy_") || biomeId.equals("minecraft:ice_spikes")
					|| biomeId.equals("minecraft:frozen_peaks");
			if (cold && !isWearingFullLeather(player)) {
				int secs = hypothermiaTicks.merge(u, 1, Integer::sum);
				if (secs >= 5 && hypothermiaAcc.merge(u, 1, Integer::sum) % 2 == 0) {
					player.hurt(player.damageSources().freeze(), 1.0f); // 每2秒1点（细雪冻结速率）
				}
			} else {
				hypothermiaTicks.remove(u);
				hypothermiaAcc.remove(u);
			}
		}

		// --- 热射病：沙漠(5秒后)/岩浆2格内持续灼伤（无护甲+带水桶免疫）---
		if (challenges.contains("heatstroke")) {
			String biomeId = biomeIdAt(player);
			boolean hotBiome = biomeId.equals("minecraft:desert") || biomeId.startsWith("minecraft:desert_")
					|| biomeId.contains("badlands");
			boolean nearLava = false;
			var bp = player.blockPosition();
			for (var pos : net.minecraft.core.BlockPos.betweenClosed(bp.offset(-2, -1, -2), bp.offset(2, 2, 2))) {
				if (player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.LAVA)) {
					nearLava = true;
					break;
				}
			}
			if ((hotBiome || nearLava) && !(armorCount(player) == 0 && hasWaterBucket(player))) {
				int secs = heatstrokeTicks.merge(u, 1, Integer::sum);
				if (nearLava || secs >= 5) {
					player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40)); // 持续灼伤
				}
			} else {
				heatstrokeTicks.remove(u);
			}
		}

		// --- 低血糖：仅剩3格饱食度（6点）→ 无限失明 + 缓慢3（斗士免疫低血糖）---
		if (challenges.contains("hypoglycemia") && !pm.isImmuneToChallenge(u, "hypoglycemia")) {
			if (player.getFoodData().getFoodLevel() <= 6) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, -1, 0));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, -1, 2));
			} else {
				player.removeEffect(MobEffects.BLINDNESS);
				player.removeEffect(MobEffects.SLOWNESS);
			}
		}

		// --- 暴露狂：装备超过 2 件 → 自燃 ---
		if (challenges.contains("exhibitionist") && armorCount(player) > 2) {
			player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 40));
		}
	}

	/** 百分比属性修饰符（ADD_MULTIPLIED_BASE，每秒校正：先移除旧值再按需添加） */
	private static void applyPctModifier(ServerPlayer player,
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			String modId, double amount) {
		var inst = player.getAttribute(attr);
		if (inst == null) return;
		var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("survival-contract", modId);
		inst.removeModifier(id);
		if (amount != 0) {
			inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** 玩家脚下方块群系 id（如 minecraft:snowy_plains） */
	private static String biomeIdAt(ServerPlayer player) {
		var key = player.level().getBiome(player.blockPosition()).unwrapKey();
		return key.map(k -> k.identifier().toString()).orElse("");
	}

	/** 四件护甲栏非空计数 */
	private static int armorCount(ServerPlayer p) {
		int n = 0;
		for (int i = 36; i <= 39; i++) if (!p.getInventory().getItem(i).isEmpty()) n++;
		return n;
	}

	/** 是否穿着全套皮革护甲 */
	private static boolean isWearingFullLeather(ServerPlayer p) {
		for (int i = 36; i <= 39; i++) {
			Item s = p.getInventory().getItem(i).getItem();
			if (s != Items.LEATHER_HELMET && s != Items.LEATHER_CHESTPLATE
					&& s != Items.LEATHER_LEGGINGS && s != Items.LEATHER_BOOTS) return false;
		}
		return true;
	}

	/** 是否为盔甲（26.2 无 ArmorItem：用 EQUIPPABLE 组件的槽位判断） */
	private static boolean isArmorStack(ItemStack s) {
		var eq = s.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		if (eq == null) return false;
		return eq.slot() == net.minecraft.world.entity.EquipmentSlot.HEAD
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.CHEST
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.LEGS
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.FEET;
	}

	/** 背包里是否带着水桶 */
	private static boolean hasWaterBucket(ServerPlayer p) {
		for (int i = 0; i <= 40; i++) {
			if (p.getInventory().getItem(i).is(Items.WATER_BUCKET)) return true;
		}
		return false;
	}

	/** 死亡原因 → 死限规则 id（无匹配返回 null）。生物击杀优先于环境致死 */
	private static String resolveDeathRule(ServerPlayer dead,
			net.minecraft.world.damagesource.DamageSource source) {
		var challenges = ChallengeManager.getInstance().getPlayerChallenges(dead.getUUID());

		// 生物击杀：按伤害来源实体匹配「不能被xx杀死」系列
		if (source.getEntity() instanceof net.minecraft.world.entity.Mob killer) {
			var type = killer.getType();
			String killRule = null;
			if (type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK) killRule = "no_killed_by_zombie";
			else if (type == EntityTypes.DROWNED) killRule = "no_killed_by_drowned";
			else if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY
					|| type == EntityTypes.BOGGED || type == EntityTypes.PARCHED) killRule = "no_killed_by_skeleton";
			else if (type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER) killRule = "no_killed_by_spider";
			else if (type == EntityTypes.CREEPER) killRule = "no_killed_by_creeper";
			else if (type == EntityTypes.WITCH) killRule = "no_killed_by_witch";
			else if (type == EntityTypes.ENDERMAN) killRule = "no_killed_by_enderman";
			else if (type == EntityTypes.BLAZE) killRule = "no_killed_by_blaze";
			else if (type == EntityTypes.MAGMA_CUBE) killRule = "no_killed_by_magma_cube";
			if (killRule != null && challenges.contains(killRule)) return killRule;
		}

		// 环境致死（溺/摔/岩浆/火/仙人掌）
		if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
				&& challenges.contains("no_drowning")) return "no_drowning";
		if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
				&& challenges.contains("no_fall_damage")) return "no_fall_damage";
		if (source.is(net.minecraft.world.damagesource.DamageTypes.LAVA)
				&& challenges.contains("no_lava_damage")) return "no_lava_damage";
		if ((source.is(net.minecraft.world.damagesource.DamageTypes.IN_FIRE)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR))
				&& challenges.contains("no_fire_damage")) return "no_fire_damage";
		if (source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
				&& challenges.contains("no_cactus")) return "no_cactus";
		return null;
	}

	private static void deductAndWarn(ServerPlayer player, String challengeId, String warning) {
		// warning 参数按规则 id 解析翻译键（survival-contract.warn.<id>，客户端按自身语言渲染）；
		// 字符串参数仅作为无法翻译时的兜底数据源，不再直接展示
		deductAndWarn(player, challengeId, Component.translatable("survival-contract.warn." + challengeId));
	}

	private static void deductAndWarn(ServerPlayer player, String challengeId, Component warning) {
		// 职业豁免统一收口（如斥候豁免药水/跳跃、大厨豁免食物类削弱）
		if (ProfessionManager.getInstance().isImmuneToChallenge(player.getUUID(), challengeId)) return;
		// 胜利者免罚特权：上一局胜利者违反规则免罚（2 次一局，用完为止；可跨局累加）
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getVictoryImmunity(player.getUUID()) > 0) {
			gsm.consumeVictoryImmunity(player.getUUID());
			player.sendSystemMessage(Component.translatable("survival-contract.msg.victory_immunity_used",
					gsm.getVictoryImmunity(player.getUUID())));
			return;
		}
		int penalty = ChallengeManager.getInstance().recordViolation(player.getUUID(), challengeId);
		if (penalty > 0) {
			GameStateManager.getInstance().deductScore(player.getUUID(), penalty);
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.violation", warning, penalty));
			// 警告提醒音效：音符盒 pling（直发包保证送达）
			playSoundDirect(player, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
		}
	}

	// ============ FINISHED 阶段广播 + 重置 ============

	/** 限时内完成最终目标：完成者（团队模式=同队全体）立即 +15 分（调用方随后立即结束游戏） */
	private static void awardFinalGoalBonus(MinecraftServer server, ServerPlayer winner) {
		if (server == null || winner == null) return;
		GameStateManager gsm = GameStateManager.getInstance();
		List<UUID> rewardedIds = new ArrayList<>();
		if (gsm.isTeamMode()) {
			GameStateManager.Team team = gsm.getPlayerTeam(winner.getUUID());
			if (team == GameStateManager.Team.RED || team == GameStateManager.Team.BLUE) {
				for (UUID mid : gsm.getTeamMembers(team)) {
					gsm.addScore(mid, 15);
					rewardedIds.add(mid);
				}
			}
		}
		if (rewardedIds.isEmpty()) {
			gsm.addScore(winner.getUUID(), 15);
			rewardedIds.add(winner.getUUID());
		}
		boolean teamReward = rewardedIds.size() > 1;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable(teamReward
					? "survival-contract.msg.final_goal_bonus_team"
					: "survival-contract.msg.final_goal_bonus", winner.getName().getString()));
			p.playSound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
		}
		NetworkRegistry.broadcastScores(server);
		SurvivalContract.LOGGER.info("Final goal bonus +15 awarded ({} player(s))", rewardedIds.size());
	}

	private static void broadcastGameEnd(MinecraftServer server, GameStateManager gsm) {
		var goal = gsm.winningGoal() != null ? gsm.winningGoal() : gsm.getCurrentFinalGoal();
		var players = server.getPlayerList().getPlayers();
		// 神的谎言：按身份博弈结果输出专属文案（不走"达成最终目标"模板）
		boolean godslies = gsm.isGodsLies() && gsm.godsLiesEndReason() != null;
		// 走私犯与检察官：无最终目标，按最终得分判定（个人最高分 / 团队总分最高的队伍）
		boolean stowaway = gsm.isStowaway();

		// 胜利者结算：完成最终目标结束 → 按最终得分取胜利者并授予免罚特权；超时 → 无人获胜
		boolean timeout = gsm.isEndedByTimeout();
		List<UUID> winners = timeout ? new ArrayList<>() : gsm.getWinnersByScore();
		if (!timeout) gsm.grantVictoryImmunity(winners, 2);
		StringBuilder winnerNames = new StringBuilder();
		for (UUID wid : winners) {
			var wp = server.getPlayerList().getPlayer(wid);
			if (winnerNames.length() > 0) winnerNames.append("§6, ");
			winnerNames.append("§e§l").append(wp != null ? wp.getName().getString() : wid.toString().substring(0, 8));
		}

		for (ServerPlayer p : players) {
			p.sendSystemMessage(Component.literal("§6═══════════════════════════════"));
			p.sendSystemMessage(Component.translatable("survival-contract.msg.game_end_header"));
			if (godslies) {
				boolean envoysWin = "envoys_victory".equals(gsm.godsLiesEndReason());
				p.sendSystemMessage(Component.translatable("survival-contract.msg.game_end_godslies_header"));
				p.sendSystemMessage(Component.translatable(envoysWin
						? "survival-contract.msg.godslies.envoys_win"
						: "survival-contract.msg.godslies.civilians_win"));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victors", winnerNames.toString()));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victor_immunity_note"));
			} else if (stowaway) {
				// 走私犯与检察官：分数最高者获胜（个人最高分 / 团队总分最高的队伍），与神的谎言胜负条件不同
				p.sendSystemMessage(Component.translatable("survival-contract.msg.game_end_stowaway_header"));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victors", winnerNames.toString()));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victor_immunity_note"));
			} else if (timeout) {
				p.sendSystemMessage(Component.translatable("survival-contract.msg.time_up_header"));
			} else {
				// goal 可能为 null：无最终目标玩法（神的谎言/走私犯与检察官）被指令强制结束
				p.sendSystemMessage(goal != null
						? Component.translatable("survival-contract.msg.goal_achieved",
								Component.translatable("survival-contract.gui.goal." + goal.name()))
						: Component.translatable("survival-contract.msg.no_final_goal_forced"));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victors_by_score", winnerNames.toString()));
				p.sendSystemMessage(Component.translatable("survival-contract.msg.victor_immunity_note"));
			}
			p.sendSystemMessage(Component.translatable("survival-contract.msg.final_score", gsm.getScore(p.getUUID())));
			if (gsm.isTeamMode()) {
				int redTotal = gsm.getTeamScore(GameStateManager.Team.RED);
				int blueTotal = gsm.getTeamScore(GameStateManager.Team.BLUE);
				p.sendSystemMessage(Component.translatable("survival-contract.msg.team_scores", redTotal, blueTotal));
				p.sendSystemMessage(Component.translatable(redTotal > blueTotal
						? "survival-contract.msg.team_winner_red"
						: blueTotal > redTotal ? "survival-contract.msg.team_winner_blue"
						: "survival-contract.msg.team_draw"));
			}
			p.sendSystemMessage(Component.literal("§6═══════════════════════════════"));
			p.sendSystemMessage(Component.translatable("survival-contract.msg.back_to_lobby_5s"));

			// 游戏结束音效：经验升级提示音（直发包保证送达）
			playSoundDirect(p, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);

			NetworkRegistry.broadcastScores(server);
			NetworkRegistry.sendSync(p);
		}
	}

	/**
	 * 清理所有"原版来源"的 Boss 血条：凋灵（WitherBoss 私有 bossEvent）、
	 * 末影龙（EnderDragonFight.dragonEvent）以及随机事件的预警条。
	 * 必须在拆除竞技场维度之前调用，否则血条会残留在客户端，并在后续每局不断叠加。
	 */
	public static void clearVanillaBossBars(MinecraftServer server) {
		if (server == null) return;
		for (ServerLevel level : server.getAllLevels()) {
			// 凋灵：遍历实体，显式移除血条
			for (var entity : level.getAllEntities()) {
				if (entity instanceof WitherBoss wither) {
					try {
						((com.survivalcontract.mixin.WitherBossAccessor) wither).sc$getBossEvent().removeAllPlayers();
					} catch (Exception ignored) {}
				}
			}
			// 末影龙：龙战的血条（SavedData，可空）
			try {
				var fight = level.getDragonFight();
				if (fight != null) {
					((com.survivalcontract.mixin.EnderDragonFightAccessor) fight).sc$getDragonEvent().removeAllPlayers();
				}
			} catch (Exception ignored) {}
		}
		// 随机事件预警条
		RandomEventManager.getInstance().clearAllBossBars();
	}

	/** 服务器启动时清理会话级静态缓存（单人存档退出重进不换 JVM，静态集合会跨世界残留） */
	public static void resetSessionStatics() {
		MID_GAME_SPECTATORS.clear();
		PENDING_PIGLIN_DEALS.clear();
		seenWithers.clear();
		ARCHER_DAMAGE_BOOSTING.clear();
		PENDING_LOBBY_TELEPORT.clear();
		PENDING_PLAYING_RESPAWN.clear();
		PENDING_SPECTATOR_JOIN.clear();
		FINISHED_COUNTDOWN = -1;
		lastSkullPlacer = null;
		FREEZE_WAS_ACTIVE = false;
		regenPrevHealth.clear();
		prevOnGround.clear();
		insomniaSleepTicks.clear();
		insomniaCuredTonight.clear();
		insomniaApplied.clear();
		hypothermiaTicks.clear();
		hypothermiaAcc.clear();
		heatstrokeTicks.clear();
		PENDING_BRITTLE_REFLECTS.clear();
		PAIN_PROCESSING.clear();
		painLastType.clear();
		painCount.clear();
		camoSeen.clear();
		processedBarterDrops.clear();
		moonClones.clear();
		dustLungTicks.clear();
		altitudeTicks.clear();
		decompressionUnderwater.clear();
		decompressionTicks.clear();
		zeroGWasGround.clear();
		zeroGLastJump.clear();
		midnightBellNextTick = 0L;
		midnightBellLastVictim = null;
		MUSICIAN_LAST_PLAY.clear();
		MUSICIAN_TONE_HISTORY.clear();
		MUSICIAN_NEXT_SONIC.clear();
		com.survivalcontract.smuggle.SmuggleManager.getInstance().reset();
		com.survivalcontract.civilization.CivilizationManager.getInstance().reset();
		GameStateManager.getInstance().resetGodsLies();
	}

	private static void performResetForLobby(MinecraftServer server, GameStateManager gsm) {
		// 切阶段 + 清空所有 flag 和分数
		gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
		gsm.resetFlags();
		gsm.resetScores();
		gsm.resetGodsLies(); // 神的谎言：使者/淘汰/指认权逐局重置
		// 清空所有玩家已选规则与违规记录（每局结束重置，避免旧点亮规则残留到下一局）
		ChallengeManager.getInstance().resetAllPlayers();
		// 广播大厅初始菜单状态（服务端 lobbyStage 已归 0）— 客户端 Singleton 需要这条
		// 消息才能把 stage 从 1 刷回 0，否则下一局开书直接是规则菜单（初始菜单只生效一次的根因）
		NetworkRegistry.broadcastLobbyState(server);
		// 移除原版计分板红蓝队染色 + 清空队伍分配（下一局重新分队）
		com.survivalcontract.team.TeamManager.getInstance().clearTeamColors(server);
		gsm.resetPlayerTeams();

		// 清临时缓存
			seenWithers.clear();
			PENDING_PIGLIN_DEALS.clear();
			regenPrevHealth.clear();
			prevOnGround.clear();
			insomniaSleepTicks.clear();
			insomniaCuredTonight.clear();
			insomniaApplied.clear();
			hypothermiaTicks.clear();
			hypothermiaAcc.clear();
			heatstrokeTicks.clear();
			PENDING_BRITTLE_REFLECTS.clear();
			PAIN_PROCESSING.clear();
			painLastType.clear();
			painCount.clear();
			camoSeen.clear();
			processedBarterDrops.clear();
			moonClones.clear();
			dustLungTicks.clear();
			altitudeTicks.clear();
			decompressionUnderwater.clear();
			decompressionTicks.clear();
			zeroGWasGround.clear();
			zeroGLastJump.clear();
			midnightBellNextTick = 0L;
			midnightBellLastVictim = null;
			FREEZE_WAS_ACTIVE = false;
			lastSkullPlacer = null;
			MID_GAME_SPECTATORS.clear();
			SPECTATOR_REJOIN_COOLDOWN.clear();
			// 回大厅即全员自动解除观战：清空大厅观战标记（含离线观战者），
			// 下一局所有人恢复普通参战者身份（想观战需重新 /sc spectate）
			gsm.clearLobbySpectators();
			MUSICIAN_LAST_PLAY.clear();
			MUSICIAN_TONE_HISTORY.clear();
			MUSICIAN_NEXT_SONIC.clear();
			com.survivalcontract.smuggle.SmuggleManager.getInstance().reset();
			com.survivalcontract.civilization.CivilizationManager.getInstance().reset();

		// 重置 WorldBorder
		try {
			var border = server.overworld().getWorldBorder();
			border.setCenter(0.5, 0.5);
			border.setSize(59999968);
			SurvivalContract.LOGGER.info("WorldBorder reset to default");
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("Failed to reset WorldBorder: {}", e.getMessage());
		}

		// 重置 RandomEventManager 计时器
		RandomEventManager.getInstance().resetTimers();

		// 重置任务
		TaskManager.getInstance().resetAll();

		// 重新应用大厅规则（关闭 SPAWN_MOBS + 锁晴天）— 开局曾为竞技场重新打开过刷怪，
		// 不关回去的话回到大厅后会开始生成史莱姆等敌对生物
		com.survivalcontract.dimension.LobbyManager.getInstance().reapplyLobbyRules();

		// 先清掉所有 Boss 血条（凋灵/末影龙/事件预警条），防止残留到大厅和下一局
		clearVanillaBossBars(server);

		// 送所有玩家回大厅（先清空局内物品/药水/成就）
		var players = new ArrayList<>(server.getPlayerList().getPlayers());
		for (ServerPlayer p : players) {
			// 兜底：仍处于死亡界面未重生的玩家先强制重生（拆竞技场维度前必须完成），
			// 重生后由 AFTER_RESPAWN 放入大厅传送队列，下一 tick 送回大厅——避免卡死进不了下一局
			if (!p.isAlive()) {
				server.getPlayerList().respawn(p, false,
						net.minecraft.world.entity.Entity.RemovalReason.KILLED);
				continue;
			}
			clearPlayerState(server, p);
			LobbyManager.getInstance().teleportToLobby(p);
			p.setGameMode(GameType.ADVENTURE);
			LobbyManager.getInstance().giveChallengeBook(p);
			p.sendSystemMessage(Component.translatable("survival-contract.msg.next_round_ready"));
			NetworkRegistry.sendSync(p);
		}
		// 全员离开竞技场后 → 拆除竞技场维度组并删除全部存档（下一局全新随机种子世界）
		com.survivalcontract.dimension.ArenaWorldManager.teardownArenaWorlds(server);
		// 对局已正常结束 → 清除进行中会话快照，避免下次启动误恢复旧局
		com.survivalcontract.game.SessionPersistence.clear(server);
		SurvivalContract.LOGGER.info("All players reset to LOBBY");
	}

	/** 游戏开始/结束边界的玩家清理：物品栏、全部药水效果、成就（全部撤销）、灭火——所有玩家一致。 */
	public static void clearPlayerState(MinecraftServer server, ServerPlayer player) {
		// 物品栏 + 光标/盔甲/副手一并清空
		player.getInventory().clearContent();

		// 所有药水效果（buff）
		player.removeAllEffects();

		// 经验槽（等级 / 进度 / 总经验）一并清零：对局中获得的等级与经验不随物品栏清空，
		// 否则回大厅后经验槽仍残留上一局进度（第九十一批）
		player.experienceLevel = 0;
		player.experienceProgress = 0.0f;
		player.totalExperience = 0;

		// 成就（进度）全部撤销：以提升权限的命令源执行原版 advancement 指令
		try {
			var elevatedSource = player.createCommandSourceStack()
					.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
			server.getCommands().performPrefixedCommand(
					elevatedSource, "advancement revoke @s everything");
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to revoke advancements for {}: {}",
					player.getName().getString(), e.getMessage());
		}

		// 熄灭身上的火
		player.extinguishFire();
	}
}
