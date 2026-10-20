package com.survivalcontract;

import com.mojang.authlib.GameProfile;
import com.survivalcontract.challenge.Challenge;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.dimension.ArenaWorldManager;
import com.survivalcontract.dimension.LobbyManager;
import com.survivalcontract.game.GameStateManager;
import com.survivalcontract.game.SessionPersistence;
import com.survivalcontract.lock.PickupGuard;
import com.survivalcontract.network.NetworkRegistry;
import com.survivalcontract.profession.ProfessionManager;
import com.survivalcontract.team.TeamManager;
import com.survivalcontract.task.TaskManager;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端假人自测（SC_SERVERTEST=1 时启用，runServer 环境跑）：
 * 用 FakeConnection 造 3 个假人（2 红 1 蓝期望），完整走一遍多人团队流程：
 * <ol>
 *   <li>切团队模式 → 随机平分红蓝队 + 原版计分板染色</li>
 *   <li>团队挑战规则互通（红队点亮 → 红队另一人也点亮；蓝队不受影响）</li>
 *   <li>双禁：禁入对面队 → 强制启用 + 锁定不能关 + 重复禁被拒</li>
 *   <li>开始游戏 → 团队分数累加（初始分 × 同色队友数）、违规仅扣当事人</li>
 *   <li>专属任务：开局首个任务强制 B 级</li>
 *   <li>/sc tp：同队可传送、跨队被拒</li>
 *   <li>/sc spectate：观战开启清分出队、不参与分队与投票、解除恢复冒险并重新归队</li>
 * </ol>
 * 全部断言结果输出到日志（[SC-TEST][PASS]/[FAIL]），结束后自动停服。
 */
public class ServerSelfTest {

	private static MinecraftServerRef server;
	private static int tick = 0;
	private static int step = 0;
	private static ServerPlayer red1, red2, blue1;
	private static final List<String> FAILS = new ArrayList<>();
	private static int passes = 0;

	/** 简单包装避免与 net.minecraft.server.MinecraftServer 混淆 */
	private record MinecraftServerRef(net.minecraft.server.MinecraftServer get) {}

	/** 自测模式是否启用（SC_SERVERTEST=1）——主类用它跳过会话恢复，防止上次崩溃残留污染测试 */
	public static boolean isEnabled() {
		return "1".equals(System.getenv("SC_SERVERTEST"));
	}

	public static void maybeRegister() {
		if (!isEnabled()) return;
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = new MinecraftServerRef(s);
			SurvivalContract.LOGGER.info("[SC-TEST] Server started, fake-player team test scheduled.");
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> tick());
	}

	private static void tick() {
		if (server == null) return;
		tick++;
		try {
			switch (step) {
				case 0 -> { if (tick >= 100) { spawnFakes(); step = 1; } }
				case 1 -> { if (tick >= 240) { testSoloEventIndependence(); testRuleAndTaskData(); testTeamAssign(); testSpectatorMode(); testNewRulesAndTeamChest(); testRound4Rules(); step = 2; } }
				case 2 -> { testTeamRuleSync(); testPickupLock(); step = 3; }
				case 3 -> { testDoubleBan(); step = 4; }
				case 4 -> { ensureTestArena(); testGodsLies(); testGodsLiesRelic(); testStowaway(); testCivilization(); testBatch52Features(); testBatch53Features(); testBatch54Features(); testBatch57Features(); testBatch60Features(); testBatch61Features(); testBatch62Features(); testBatch63Features(); testBatch64Features(); testBatch66Features(); testBatch67Features(); testBatch68Features(); testBatch69Features(); testBatch70Features(); testBatch71Features(); testBatch72Features(); testBatch74Features(); testBatch75Features(); testBatch90Features(); testTaskConflictFiltering(); step = 41; }
				// 尘肺病矿工免疫的行为验证必须跨越真实整秒 tick（perSecond 门控），完成后再开局
				case 41 -> dustLungImmunityStep();
				case 5 -> { if (tick >= startTick + 240) { testScoresAndTask(); spawnMidGameSpectator(); step = 55; } }
				case 55 -> { if (tick >= midGameJoinTick + 40) { testMidGameSpectatorJoin(); step = 56; } }
				case 56 -> { if (tick >= selfHealTick + 80) { testSpectatorSelfHeal(); step = 6; } }
				case 6 -> { testRestartScenario(); testTeammateTp(); testLobbyInventoryClear(); testLobbyConfig(); testBookReturn(); step = 7; }
				case 7 -> { testSessionPersistence(); setupBossBarScenario(); step = 8; }
				case 8 -> { if (tick >= bossBarAssertTick) { testBossBarCleanup(); step = 9; } }
				case 9 -> { testLobbyResetClearsAllSpectators(); finish(); step = 99; }
				default -> {}
			}
		} catch (Throwable t) {
			SurvivalContract.LOGGER.error("[SC-TEST][CRASH] step={} tick={}", step, tick, t);
			FAILS.add("CRASH at step " + step + ": " + t);
			finish();
			step = 99;
		}
	}

	private static int startTick = 0;

	// 第五十六批：中途观战 / 回大厅解除观战 自测状态
	private static int midGameJoinTick = 0;
	private static int selfHealTick = 0;
	private static ServerPlayer fakeLate = null;

	// ==================== 测试步骤 ====================

	private static void spawnFakes() {
		var s = server.get();
		// 清理上次异常中断遗留的会话文件（防旧局状态/旧假人 UUID 污染本轮断言）
		com.survivalcontract.game.SessionPersistence.clear(s);
		GameStateManager.getInstance().setCurrentPhase(GameStateManager.GamePhase.LOBBY);
		GameStateManager.getInstance().resetScores();
		ServerLevel overworld = s.overworld();
		red1 = spawnFake(s, overworld, "FakeRed1");
		red2 = spawnFake(s, overworld, "FakeRed2");
		blue1 = spawnFake(s, overworld, "FakeBlue1");
		check(red1 != null && red2 != null && blue1 != null,
				"假人生成", "3/3 在线 (ServerPlayer + placeNewPlayer)");
	}

	private static ServerPlayer spawnFake(net.minecraft.server.MinecraftServer s, ServerLevel level, String name) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		ServerPlayer player = new ServerPlayer(s, level, profile, ClientInformation.createDefault());
		s.getPlayerList().placeNewPlayer(new FakeConnection(), player,
				net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
		markFakeClientLoaded(player);
		return player;
	}

	/**
	 * 让假人视为"客户端就绪"：26.2 的 ServerPlayer.isInvulnerableTo 在两处把
	 * 未就绪的玩家变成全程免疫——① !connection.hasClientLoaded()（FakeConnection
	 * 永远不发加载确认包）；② isChangingDimension()（teleportTo 换维度后等待
	 * 客户端确认，假人永不确认 → 对非末影珍珠伤害一律免疫）。伤害链路测试必须手动清零。
	 */
	private static void markFakeClientLoaded(ServerPlayer p) {
		try {
			var cls = net.minecraft.server.network.ServerGamePacketListenerImpl.class;
			var timer = cls.getDeclaredField("clientLoadedTimeoutTimer");
			timer.setAccessible(true);
			timer.setInt(p.connection, 0);
			var respawn = cls.getDeclaredField("waitingForRespawn");
			respawn.setAccessible(true);
			respawn.setBoolean(p.connection, false);
			var changing = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("isChangingDimension");
			changing.setAccessible(true);
			changing.setBoolean(p, false);
		} catch (Exception ignored) {}
	}

	/** 诊断用：读取假人连接的 clientLoadedTimeoutTimer（-1 = 反射失败） */
	private static int fakeClientLoadedTimer(ServerPlayer p) {
		try {
			var f = net.minecraft.server.network.ServerGamePacketListenerImpl.class
					.getDeclaredField("clientLoadedTimeoutTimer");
			f.setAccessible(true);
			return f.getInt(p.connection);
		} catch (Exception e) {
			return -1;
		}
	}

	/** 规则细分/新增怪物规则/任务冲突键：纯数据断言（阶段无关，不改变游戏状态） */
	private static void testRuleAndTaskData() {
		var cm = ChallengeManager.getInstance();
		cm.ensureRegistered();
		// 受到伤害增加 → 细分为 5 类，全部可自定义百分比
		for (String id : new String[]{"reduced_defense_fire", "reduced_defense_fall",
				"reduced_defense_wither", "reduced_defense_lightning", "reduced_defense_freeze"}) {
			var c = cm.getChallenge(id);
			check(c != null && c.isCustomizable() && c.getCustomValue() > 0,
					"伤害增加细分规则", id + (c != null ? " (默认" + c.getCustomValue() + "%)" : " 缺失"));
		}
		check(cm.getChallenge("reduced_defense") == null, "旧规则已拆分", "reduced_defense 已被 5 类伤害规则取代");
		// 新增"不能被 XX 杀死"
		for (String id : new String[]{"no_killed_by_witch", "no_killed_by_enderman", "no_killed_by_blaze",
				"no_killed_by_drowned", "no_killed_by_magma_cube"}) {
			check(cm.getChallenge(id) != null, "新增怪物击杀规则", id);
		}
		// 原有死亡限制规则仍在（防误删回归）
		for (String id : new String[]{"no_fall_damage", "no_fire_damage", "no_lava_damage",
				"no_drowning", "no_cactus", "no_fall_in_void"}) {
			check(cm.getChallenge(id) != null, "原有死亡限制保留", id);
		}
		// 任务冲突键：吃牛排任务 ↔ 不能吃牛排规则；挖掘钻石矿任务 ↔ 不能获得钻石规则
		var beefRule = cm.getChallenge("no_eat_beef");
		var beefTask = new TaskManager.PlayerTask("t_eat_beef", "吃牛排 x3",
				TaskManager.TaskTier.B, TaskManager.TaskType.USE_ITEM, "minecraft:cooked_beef", 3);
		check(beefRule != null && beefTask.conflictKeys().stream().anyMatch(beefRule.getConflictKeys()::contains),
				"任务冲突检测(吃牛排)", "USE_ITEM cooked_beef ↔ no_eat_beef 冲突");
		var diaRule = cm.getChallenge("no_diamond");
		var diaTask = new TaskManager.PlayerTask("t_mine_dia", "挖掘 3 个钻石矿石",
				TaskManager.TaskTier.S, TaskManager.TaskType.MINE_BLOCK, "minecraft:diamond_ore", 3);
		check(diaRule != null && diaTask.conflictKeys().stream().anyMatch(diaRule.getConflictKeys()::contains),
				"任务冲突检测(挖钻石)", "MINE_BLOCK diamond_ore ↔ no_diamond 冲突");
		// 击杀类任务与"不能被 XX 杀死"不冲突（击杀≠被杀）
		var zombieRule = cm.getChallenge("no_killed_by_zombie");
		var killTask = new TaskManager.PlayerTask("t_kill_zombie", "击杀僵尸 x3",
				TaskManager.TaskTier.B, TaskManager.TaskType.KILL_MOB, "minecraft:zombie", 3);
		check(killTask.conflictKeys().isEmpty() || killTask.conflictKeys().stream().noneMatch(zombieRule.getConflictKeys()::contains),
				"击杀任务不误伤", "KILL_MOB zombie ↔ no_killed_by_zombie 不冲突");
	}

	/**
	 * 专属任务与已选规则冲突过滤（行为级）：逐条开启每条规则，连续多轮发放任务，
	 * 任何发出的任务都不得与该规则的冲突键相交；再用"全部食物禁令 + 低血糖"组合验证
	 * USE_ITEM 任务只剩放置类（石头/火把）。用户实例：不能吃烤马铃薯 → 永不刷出吃烤马铃薯。
	 */
	private static void testTaskConflictFiltering() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var tm = TaskManager.getInstance();
		ServerPlayer p = red1;
		var savedStyle = gsm.getPlayStyle();
		var savedPhase = gsm.getCurrentPhase();
		var savedCh = new java.util.HashSet<>(cm.getPlayerChallenges(p.getUUID()));
		cm.ensureRegistered();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (var ch : cm.getAllChallenges()) {
				// 每轮 resetAll 清近期列表，避免候选被去重光后走兜底（兜底也永远无冲突，但要压满候选池）
				for (int round = 0; round < 3; round++) {
					tm.resetAll();
					cm.clearPlayerChallenges(p.getUUID());
					cm.enableChallenge(p.getUUID(), ch.getId());
					for (int i = 0; i < 12; i++) {
						tm.issueNewTask(p);
						var t = tm.getActiveTask(p.getUUID());
						check(t != null, "任务冲突过滤发任务(" + ch.getId() + ")", "issueNewTask 有任务");
						if (t != null) {
							check(java.util.Collections.disjoint(t.conflictKeys(), ch.getConflictKeys()),
									"任务不与规则冲突：" + ch.getId(),
									"任务 " + t.id + " 键 " + t.conflictKeys()
											+ " 撞上规则键 " + ch.getConflictKeys());
						}
					}
				}
			}
			// 用户实例 + 组合：全部食物禁令（含低血糖）同时开启
			tm.resetAll();
			cm.clearPlayerChallenges(p.getUUID());
			for (String id : new String[]{"no_eat_beef", "no_eat_pork", "no_eat_chicken", "no_eat_mutton",
					"no_eat_fish", "no_eat_apple", "no_eat_golden_apple", "no_eat_bread",
					"no_eat_potato", "hypoglycemia"}) {
				cm.enableChallenge(p.getUUID(), id);
			}
			boolean sawPotato = false;
			for (int round = 0; round < 8; round++) {
				tm.resetAll();
				for (int i = 0; i < 12; i++) {
					tm.issueNewTask(p);
					var t = tm.getActiveTask(p.getUUID());
					if (t == null) continue;
					if (t.type == TaskManager.TaskType.USE_ITEM) {
						check(t.targetId.equals("minecraft:stone") || t.targetId.equals("minecraft:torch"),
								"全食物禁令后只剩放置类使用任务", "USE_ITEM 目标=" + t.targetId);
					}
					if (t.targetId.equals("minecraft:baked_potato")) sawPotato = true;
				}
			}
			check(!sawPotato, "不能吃烤马铃薯→不刷烤马铃薯任务", "96 次发任务内无 baked_potato 目标");
		} finally {
			tm.resetAll();
			cm.clearPlayerChallenges(p.getUUID());
			for (String id : savedCh) cm.enableChallenge(p.getUUID(), id);
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
		}
	}

	/** 个人模式：每玩家遭遇事件独立选择、独立加分、互不影响（团队模式的全局行为见 testTeamRuleSync） */
	private static void testSoloEventIndependence() {
		GameStateManager gsm = GameStateManager.getInstance();
		gsm.setGameMode(GameStateManager.GameMode.SOLO);
		NetworkRegistry.applyEventToggle(red1, "LIGHTNING", true);
		NetworkRegistry.applyEventToggle(red2, "HUNGER_BOMB", true);
		check(gsm.isPlayerEventEnabled(red1.getUUID(), "LIGHTNING"),
				"个人事件独立开启", "Red1 开 LIGHTNING 只对自己生效");
		check(!gsm.isPlayerEventEnabled(red2.getUUID(), "LIGHTNING"),
				"个人事件隔离", "Red2 未受 Red1 的影响");
		check(gsm.isPlayerEventEnabled(red2.getUUID(), "HUNGER_BOMB"),
				"个人事件独立开启2", "Red2 开 HUNGER_BOMB 互不干扰");
		check(!gsm.isPlayerEventEnabled(blue1.getUUID(), "LIGHTNING")
						&& !gsm.isPlayerEventEnabled(blue1.getUUID(), "HUNGER_BOMB"),
				"个人事件不影响第三人", "Blue1 无任何事件");
		check(!gsm.isEventEnabled("LIGHTNING") && !gsm.isEventEnabled("HUNGER_BOMB"),
				"个人事件不进全局", "单人模式不污染全局 enabledEvents");
		check(gsm.getEventScoreBonus(red1.getUUID()) == 5
						&& gsm.getEventScoreBonus(red2.getUUID()) == 15
						&& gsm.getEventScoreBonus(blue1.getUUID()) == 0,
				"个人事件加分独立", "Red1 +5 / Red2 +15(HUNGER_BOMB S级) / Blue1 +0");
		// 清理：还原为无事件（testTeamAssign 随后自行切 TEAMS）
		NetworkRegistry.applyEventToggle(red1, "LIGHTNING", false);
		NetworkRegistry.applyEventToggle(red2, "HUNGER_BOMB", false);
	}

	private static void testTeamAssign() {
		var s = server.get();
		GameStateManager gsm = GameStateManager.getInstance();
		gsm.setGameMode(GameStateManager.GameMode.TEAMS);
		TeamManager.getInstance().randomizeTeams(s);

		int red = gsm.getTeamMembers(GameStateManager.Team.RED).size();
		int blue = gsm.getTeamMembers(GameStateManager.Team.BLUE).size();
		check(red == 2 && blue == 1, "平分红蓝队", "3 人 → 红 " + red + " 蓝 " + blue);

		// 计分板队伍染色（原版 TeamColor）—— getPlayersTeam 按玩家名查所属队伍
		Scoreboard sb = s.getScoreboard();
		String redName = gsm.getPlayerTeam(red1.getUUID()) == GameStateManager.Team.RED ? "FakeRed1"
				: gsm.getPlayerTeam(red2.getUUID()) == GameStateManager.Team.RED ? "FakeRed2" : "FakeBlue1";
		var redSb = sb.getPlayersTeam(redName);
		check(redSb != null && "survival_red".equals(redSb.getName()),
				"计分板队伍存在", redName + " → " + (redSb != null ? redSb.getName() : "null"));
		String blueName = null;
		for (ServerPlayer q : new ServerPlayer[]{red1, red2, blue1}) {
			if (gsm.getPlayerTeam(q.getUUID()) == GameStateManager.Team.BLUE) blueName = q.getName().getString();
		}
		var blueSb = blueName == null ? null : sb.getPlayersTeam(blueName);
		check(blueSb != null && "survival_blue".equals(blueSb.getName()),
				"计分板蓝队染色", blueName + " → " + (blueSb != null ? blueSb.getName() : "null"));
	}

	/** 新规则注册 + 团队箱数据层（同队互通/异队独立/SOLO 独立/NBT 往返） */
	private static void testNewRulesAndTeamChest() {
		ChallengeManager cm = ChallengeManager.getInstance();
		GameStateManager gsm = GameStateManager.getInstance();

		// ---- 5 条新规则已注册 ----
		String[][] newRules = {
				{"no_lapis", "青金石"}, {"no_kill_iron_golem", "铁傀儡"},
				{"no_villager_trade", "村民交易"}, {"no_fishing_rod", "钓鱼竿"}, {"no_boat", "任意船"}};
		for (String[] r : newRules) {
			var c = cm.getChallenge(r[0]);
			check(c != null, "新规则注册: " + r[1], r[0] + " = " + (c == null ? "缺失" : c.getDisplayName()));
		}
		var lapis = cm.getChallenge("no_lapis");
		check(lapis != null && lapis.getConflictKeys().contains("item:minecraft:lapis_lazuli"),
				"no_lapis 冲突键", String.valueOf(lapis == null ? null : lapis.getConflictKeys()));

		// ---- 团队箱（TEAMS：同队互通 / 异队独立）----
		var tcm = com.survivalcontract.game.TeamChestManager.getInstance();
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer redMate = teammateOf(redMember);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		var redChest = tcm.getContainerFor(redMember);
		check(redChest == tcm.getContainerFor(redMate), "团队箱同队互通", "红队两人共享同一容器");
		check(redChest != tcm.getContainerFor(blueMember), "团队箱异队独立", "红蓝队容器不同");
		redChest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
		check(tcm.getContainerFor(redMate).getItem(0).is(net.minecraft.world.item.Items.DIAMOND),
				"团队箱物品互通", "红1 放钻石 → 红2 可见");

		// ---- NBT round-trip（单人存档退出重进恢复路径）----
		var saved = tcm.saveToNbt();
		tcm.clearAll();
		check(tcm.getContainerFor(redMember).isEmpty(), "团队箱开局清空", "clearAll 后为空");
		tcm.loadFromNbt(saved);
		check(tcm.getContainerFor(redMember).getItem(0).is(net.minecraft.world.item.Items.DIAMOND),
				"团队箱 NBT 恢复", "save→clear→load 后钻石还在原槽位");

		// ---- SOLO 模式：每玩家独立 ----
		gsm.setGameMode(GameStateManager.GameMode.SOLO);
		var soloA = tcm.getContainerFor(redMember);
		var soloB = tcm.getContainerFor(blueMember);
		check(soloA != soloB && soloA != redChest, "个人团队箱独立", "SOLO 下两人容器不同且不与队箱混用");
		gsm.setGameMode(GameStateManager.GameMode.TEAMS);
		tcm.clearAll(); // 清理，不污染后续步骤
	}

	/** 本轮新增：贫瘠铁矿 / 善魂置换（红字规则）/ 猩红入侵事件的数据层断言 */
	private static void testRound4Rules() {
		ChallengeManager cm = ChallengeManager.getInstance();

		// ---- 贫瘠铁矿：数值固定不可自定义 ----
		var iron = cm.getChallenge("iron_ore_risk");
		check(iron != null, "规则注册: 贫瘠铁矿",
				"iron_ore_risk = " + (iron == null ? "缺失" : iron.getDisplayName()));
		if (iron != null) {
			check(!iron.isCustomizable(), "贫瘠铁矿数值固定", "customizable=" + iron.isCustomizable());
			check(iron.getBaseScore() > 0, "贫瘠铁矿为正向规则", "baseScore=" + iron.getBaseScore());
			check(iron.getPenaltyScore() == 0, "贫瘠铁矿无违规扣分", "penaltyScore=" + iron.getPenaltyScore());
		}

		// ---- 恶魂庇佑：红字规则（baseScore 为负 → 选择即扣分，取消后初始分自动恢复）----
		var ghast = cm.getChallenge("ghast_replacement");
		check(ghast != null, "规则注册: 恶魂庇佑",
				"ghast_replacement = " + (ghast == null ? "缺失" : ghast.getDisplayName()));
		if (ghast != null) {
			check(ghast.getBaseScore() < 0, "恶魂庇佑为红字扣分规则", "baseScore=" + ghast.getBaseScore());
			check(ghast.getCategory() == com.survivalcontract.challenge.Challenge.Category.RED_TEXT,
					"恶魂庇佑分类 RED_TEXT", String.valueOf(ghast.getCategory()));
		}

		// ---- 初始分动态验证：选择恶魂庇佑扣 6 分，取消恢复 ----
		if (red1 != null && ghast != null) {
			cm.enableChallenge(red1.getUUID(), "ghast_replacement");
			int with = cm.calculateInitialScore(red1.getUUID());
			cm.disableChallenge(red1.getUUID(), "ghast_replacement");
			int without = cm.calculateInitialScore(red1.getUUID());
			check(without - with == 6, "恶魂庇佑选择扣6分/取消恢复",
					"with=" + with + " without=" + without + " diff=" + (without - with));
		}

		// ---- 猩红入侵事件（S 级 15 分 — 用户 10-04 调整）----
		try {
			var ev = com.survivalcontract.event.RandomEventManager.EventType.valueOf("CRIMSON_INVASION");
			check(ev.tier == com.survivalcontract.event.RandomEventManager.EventTier.S
					&& ev.score == 15, "猩红入侵事件注册", "tier=" + ev.tier + " score=" + ev.score);
		} catch (IllegalArgumentException e) {
			check(false, "猩红入侵事件注册", "CRIMSON_INVASION 枚举缺失");
		}
	}

	private static void testTeamRuleSync() {
		GameStateManager gsm = GameStateManager.getInstance();
		ChallengeManager cm = ChallengeManager.getInstance();
		// 红队某人点亮 no_chest → 全队互通（randomizeTeams 随机洗牌，动态取队伍成员）
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer redMate = teammateOf(redMember);
		NetworkRegistry.applyChallengeToggle(redMember, "no_chest", true);
		check(cm.getPlayerChallenges(redMate.getUUID()).contains("no_chest"),
				"团队规则互通", "红队一人点亮 → 队友同步点亮");
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		NetworkRegistry.applyChallengeToggle(blueMember, "no_furnace", true);
		check(!cm.getPlayerChallenges(redMember.getUUID()).contains("no_furnace")
						&& cm.getPlayerChallenges(blueMember.getUUID()).contains("no_furnace"),
				"团队规则隔离", "蓝队点亮不影响红队");
	}

	private static void testDoubleBan() {
		GameStateManager gsm = GameStateManager.getInstance();
		ChallengeManager cm = ChallengeManager.getInstance();
		// 红队把 no_beacon 双禁进蓝队
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		NetworkRegistry.applyDoubleBan(redMember, "no_beacon");
		check(gsm.getTeamBans().getOrDefault(GameStateManager.Team.RED, "").equals("no_beacon"),
				"双禁记录", "红队 → 禁入蓝队 no_beacon");
		check(cm.getPlayerChallenges(blueMember.getUUID()).contains("no_beacon"),
				"双禁强制启用", "蓝队成员被强制点亮 no_beacon");
		// 被禁方尝试关闭 → 被锁定拒绝
		NetworkRegistry.applyChallengeToggle(blueMember, "no_beacon", false);
		check(cm.getPlayerChallenges(blueMember.getUUID()).contains("no_beacon"),
				"双禁锁定", "蓝队尝试关闭 no_beacon → 被拒绝");
		// 重复双禁 → 拒绝
		NetworkRegistry.applyDoubleBan(blueMember, "no_beacon");
		check(!gsm.getTeamBans().containsKey(GameStateManager.Team.BLUE),
				"重复双禁拒绝", "no_beacon 已被红队选中 → 蓝队不能再选");
		// 被双禁进来的规则不加分：排除双禁后的初始分数 = 原始点亮分数 - no_beacon 分值 + 无职业加成 10
		// 注意：必须在事件双禁之前断言 —— 每队仅一条，红队选事件后会覆盖规则 ban 的记录位
		int noBeaconScore = cm.getChallenge("no_beacon").getBaseScore();
		check(gsm.calculateInitialScoreExcludingBans(blueMember.getUUID())
						== cm.calculateInitialScore(blueMember.getUUID()) - noBeaconScore + 10,
				"双禁规则不加分", "蓝队初始分数排除被禁入的 no_beacon（含无职业 +10）");

		// ===== 事件双禁：红队把 LIGHTNING 定向禁给蓝队（无分数、不进全局、不可手动开关）=====
		// 先移除 no_beacon 记录（每队仅一条，模拟"红队重新选了事件"）
		gsm.getTeamBans().remove(GameStateManager.Team.RED);
		String evBan = "event:LIGHTNING";
		NetworkRegistry.applyDoubleBan(redMember, evBan);
		check(gsm.getTeamBans().getOrDefault(GameStateManager.Team.RED, "").equals(evBan),
				"事件双禁记录", "红队 → 禁入蓝队 event:LIGHTNING");
		check(gsm.getEventBanTeam("LIGHTNING") == GameStateManager.Team.BLUE,
				"事件定向队伍", "LIGHTNING 定向触发给蓝队");
		check(!gsm.isEventEnabled("LIGHTNING"),
				"事件双禁不进全局", "LIGHTNING 不在全局 enabledEvents（避免误伤红队）");
		NetworkRegistry.applyEventToggle(blueMember, "LIGHTNING", true);
		check(!gsm.isEventEnabled("LIGHTNING"),
				"双禁事件锁定", "双禁事件无法手动开启");
		NetworkRegistry.applyDoubleBan(blueMember, evBan);
		check(!gsm.getTeamBans().containsKey(GameStateManager.Team.BLUE),
				"事件重复双禁拒绝", "event:LIGHTNING 已被红队选中 → 蓝队不能再选");

		// ===== 全局规则开关同步：任何人切换 rule_*，服务端全局状态变化（全员 sync 显示一致）=====
		NetworkRegistry.applyChallengeToggle(redMember, "rule_pvp", true);
		check(gsm.isRuleActive("rule_pvp"),
				"全局规则开启", "红队成员开启 PVP → 服务端全局 activeRules 生效");
		NetworkRegistry.applyChallengeToggle(blueMember, "rule_pvp", false);
		check(!gsm.isRuleActive("rule_pvp"),
				"全局规则关闭", "蓝队成员关闭 PVP → 全局同步关闭（等同模式/目标的全局语义）");
		gsm.setGameDurationMinutes(90);
		check(gsm.getGameDurationMinutes() == 90,
				"时长全局设置", "90 分钟写入 GameStateManager 并随 sync 全员下发");
		gsm.setGameDurationMinutes(120); // 还原默认，避免影响后续 startGame
	}

	/** 大厅观战（/sc spectate）：真实指令路径（LOBBY 阶段，须在 testDoubleBan 之前跑——双禁会给假人留下非对称状态，重洗队伍会破坏后续分数断言） */
	private static void testSpectatorMode() {
		var s = server.get();
		GameStateManager gsm = GameStateManager.getInstance();
		var commands = s.getCommands();

		// 前置：blue1 带分数（5），验证开启观战后清零
		gsm.setScore(blue1.getUUID(), 5);
		commands.performPrefixedCommand(blue1.createCommandSourceStack(), "sc spectate");
		check(gsm.isLobbySpectator(blue1.getUUID()), "观战开启", "blue1 进入观战集合");
		check(gsm.getScore(blue1.getUUID()) == 0, "观战分数清零", "blue1 5 分 → 0");
		check(gsm.getPlayerTeam(blue1.getUUID()) == GameStateManager.Team.NONE,
				"观战移出队伍", "blue1 队别归 NONE");
		check(s.getScoreboard().getPlayersTeam("FakeBlue1") == null,
				"观战计分板染色移除", "FakeBlue1 已脱离原版计分板队伍");
		check(blue1.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR,
				"观战进入旁观者模式", "blue1 gamemode = SPECTATOR");

		// 分队排除：randomizeTeams 跳过观战者（red1/red2 平分，blue1 保持 NONE）
		TeamManager.getInstance().randomizeTeams(s);
		check(gsm.getPlayerTeam(blue1.getUUID()) == GameStateManager.Team.NONE
						&& gsm.getTeamMembers(GameStateManager.Team.RED).size() == 1
						&& gsm.getTeamMembers(GameStateManager.Team.BLUE).size() == 1,
				"观战者不参与分队", "blue1 仍 NONE，red1/red2 平分 1v1");

		// 投票需求人数排除观战者：3 人参战需 2 票 → 仅 blue1 参战只需 1 票
		int needAll = gsm.lobbyVoteNeeded(s);
		commands.performPrefixedCommand(red1.createCommandSourceStack(), "sc spectate");
		commands.performPrefixedCommand(red2.createCommandSourceStack(), "sc spectate");
		int needOnlyBlue = gsm.lobbyVoteNeeded(s);
		check(needAll == 2 && needOnlyBlue == 1, "投票需求人数排除观战者",
				"3 人参战需 " + needAll + " 票 → 仅剩 blue1 参战需 " + needOnlyBlue + " 票");

		// 解除观战：全部退出 → 恢复冒险模式 + 重新分队
		commands.performPrefixedCommand(blue1.createCommandSourceStack(), "sc spectate");
		commands.performPrefixedCommand(red1.createCommandSourceStack(), "sc spectate");
		commands.performPrefixedCommand(red2.createCommandSourceStack(), "sc spectate");
		check(!gsm.isLobbySpectator(blue1.getUUID()) && !gsm.isLobbySpectator(red1.getUUID())
						&& !gsm.isLobbySpectator(red2.getUUID()),
				"观战解除", "3 名假人全部移出观战集合");
		check(blue1.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.ADVENTURE,
				"解除恢复冒险模式", "blue1 gamemode = ADVENTURE");
		int redN = gsm.getTeamMembers(GameStateManager.Team.RED).size();
		int blueN = gsm.getTeamMembers(GameStateManager.Team.BLUE).size();
		check(gsm.getPlayerTeam(blue1.getUUID()) != GameStateManager.Team.NONE
						&& redN + blueN == 3 && Math.abs(redN - blueN) <= 1,
				"解除后重新分队", "全员归队 红 " + redN + " 蓝 " + blueN);

		// 还原 2v1 基线（与 testTeamAssign 后状态一致，供 startGame 正常开局）
		TeamManager.getInstance().randomizeTeams(s);
		check(gsm.getTeamMembers(GameStateManager.Team.RED).size() == 2
						&& gsm.getTeamMembers(GameStateManager.Team.BLUE).size() == 1,
				"观战测试后恢复基线", "3 人 → 红 2 蓝 1");
	}

	// ==================== 第五十六批：中途进入自动观战 / 回大厅自动解除观战 ====================

	/** PLAYING 阶段生成新假人并注入"中途观战"待处理队列（与真实玩家 JOIN 分类后的链路一致）。 */
	private static void spawnMidGameSpectator() {
		var s = server.get();
		fakeLate = spawnFake(s, s.overworld(), "FakeLate");
		midGameJoinTick = tick;
		try {
			var f = com.survivalcontract.MainEventListener.class.getDeclaredField("PENDING_SPECTATOR_JOIN");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<ServerPlayer> q = (List<ServerPlayer>) f.get(null);
			q.add(fakeLate);
		} catch (Exception e) {
			check(false, "中途观战队列注入", "反射失败: " + e);
		}
		check(fakeLate != null, "中途加入假人生成", "FakeLate 已注入旁观队列（PLAYING 阶段）");
	}

	/** 中途加入处理结果：旁观者模式 + 竞技场维度 + 观战追踪；随后人为移出竞技场制造"传送丢失"。 */
	private static void testMidGameSpectatorJoin() {
		var s = server.get();
		GameStateManager gsm = GameStateManager.getInstance();
		var arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(s);
		check(fakeLate != null && !gsm.hasPlayerState(fakeLate.getUUID()),
				"中途加入无参战状态", "FakeLate 不在分数表（非参战者）");
		check(fakeLate.isSpectator(), "中途加入自动观战", "FakeLate 已切换旁观者模式");
		check(arena != null && fakeLate.level() == arena,
				"中途加入传送竞技场", "FakeLate 位于竞技场维度观战");
		check(isMidGameTracked(fakeLate.getUUID()), "中途加入观战追踪",
				"MID_GAME_SPECTATORS 已登记（结束时随全员回大厅）");
		// 制造"初次传送丢失"竞态：把观战者扔出竞技场 → 旁观者保持循环应自愈拉回
		selfHealTick = tick;
		fakeLate.teleportTo(s.overworld(), 0.5, 100, 0.5, java.util.Set.of(), 0f, 0f, false);
	}

	/** 自愈验证：被移出竞技场的观战者在数 tick 内被自动拉回竞技场观战。 */
	private static void testSpectatorSelfHeal() {
		var s = server.get();
		var arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(s);
		check(arena != null && fakeLate.level() == arena && fakeLate.isSpectator(),
				"观战者位置自愈", "被移出竞技场后自动拉回观战（旁观者保持循环兜底）");
		// 自愈后移出玩家列表（避免干扰后续重开局/血条步骤的参战者计数），
		// MID_GAME_SPECTATORS 保留其标记 → 用于验证回大厅时连离线观战者一并清空
		s.getPlayerList().remove(fakeLate);
	}

	/** 回大厅重置：所有观战者（大厅观战 + 中途观战，含离线）自动解除观战、全员冒险模式。 */
	private static void testLobbyResetClearsAllSpectators() {
		var s = server.get();
		GameStateManager gsm = GameStateManager.getInstance();
		gsm.addLobbySpectator(blue1.getUUID()); // 模拟一名大厅观战者
		check(isMidGameTracked(fakeLate.getUUID()), "离线中途观战者标记保留",
				"移出服务器不影响观战追踪（等待回大厅统一解除）");
		try {
			var m = com.survivalcontract.MainEventListener.class.getDeclaredMethod("performResetForLobby",
					net.minecraft.server.MinecraftServer.class, GameStateManager.class);
			m.setAccessible(true);
			m.invoke(null, s, gsm);
		} catch (Exception e) {
			check(false, "回大厅重置调用", "反射失败: " + e);
			return;
		}
		check(gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY, "回大厅阶段", "LOBBY");
		check(!gsm.isLobbySpectator(blue1.getUUID()), "大厅观战者自动解除", "blue1 观战标记已清除");
		check(!isMidGameTracked(fakeLate.getUUID()), "中途观战者自动解除（含离线）",
				"MID_GAME_SPECTATORS 已清空，离线观战者下一局恢复正常身份");
		boolean allAdventure = s.getPlayerList().getPlayers().stream()
				.allMatch(p -> p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.ADVENTURE);
		check(allAdventure, "回大厅全员冒险模式", "无残留旁观者（下一局想观战需重新 /sc spectate）");
	}

	/** 反射读取 MainEventListener.MID_GAME_SPECTATORS。 */
	private static boolean isMidGameTracked(java.util.UUID id) {
		try {
			var f = com.survivalcontract.MainEventListener.class.getDeclaredField("MID_GAME_SPECTATORS");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			java.util.Set<java.util.UUID> set = (java.util.Set<java.util.UUID>) f.get(null);
			return set.contains(id);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 神的谎言：随机规则总分恰好 100 + 使者任命免疫 + 降罪扣分 + 指认失败淘汰 + 使者胜利判定。
	 * 直接调用底层方法（真指令/网络路径无法在自测中覆盖），结束后还原常规模式与 LOBBY 阶段。
	 */
	private static void testGodsLies() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var savedPhase = gsm.getCurrentPhase();
		// 快照各假人挑战规则集：使者任命会 clearPlayerChallenges，不还原会污染
		// 后续 startGame 的初始分计算（使者 10 分 vs 队友 13 分 → 团队分数断言全崩）
		java.util.Map<UUID, java.util.Set<String>> savedChallenges = new java.util.HashMap<>();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			savedChallenges.put(p.getUUID(), new java.util.HashSet<>(cm.getPlayerChallenges(p.getUUID())));
		}
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
			gsm.resetGodsLies();

			// 随机规则总分：10 次随机抽样全部恰好 100（第五十四批：120 → 100）
			java.util.Random rnd = new java.util.Random(42);
			boolean allExact = true;
			for (int i = 0; i < 10 && allExact; i++) {
				int sum = 0;
				for (String id : cm.randomRuleSetForScore(100, rnd)) {
					var c = cm.getChallenge(id);
					sum += c.isCustomizable() && c.getCustomValue() > 0
							? Math.round(c.getBaseScore() * c.getCustomValue() / 50.0f) : c.getBaseScore();
				}
				if (sum != 100) allExact = false;
			}
			check(allExact, "神的谎言随机规则总分", "10 次随机分配全部恰好 100 分");

			// 模拟开局计分后的状态（每人 100 分）+ 切 PLAYING（胜负判定需要）
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);

			// 任命 1 名使者：恰好 1 名，且其规则集被清空（= 免疫所有规则）
			gsm.setEnvoyCount(1);
			gsm.setupGodsLiesEnvoys(server.get());
			ServerPlayer envoy = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) envoy = p;
			}
			check(envoy != null && gsm.getGodEnvoysCount() == 1, "使者任命", "3 人任命 1 名使者");
			check(envoy != null && cm.getPlayerChallenges(envoy.getUUID()).isEmpty(),
					"使者免疫所有规则", "使者规则集已清空（违规/没收/死限判定全部跳过）");

			// 使者降罪：完成任务 → 降罪权 +1 → 指定一名玩家扣 55 分（100 → 55，未淘汰；第五十四批：60 → 55）
			gsm.grantGodsLiesTaskReward(envoy);
			ServerPlayer victim = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (!gsm.isGodEnvoy(p.getUUID())) { victim = p; break; }
			}
			gsm.envoyDeduct(envoy.getUUID(), victim.getUUID());
			check(gsm.getScore(victim.getUUID()) == 45 && !gsm.isEliminated(victim.getUUID()),
					"使者降罪扣分", victim.getName().getString() + " 100 → 45（未淘汰）");

			// 指认书死亡不掉落：拦截暂存 → 重生返还（神的谎言 PLAYING 阶段，无复制）
			var invAb = victim.getInventory();
			for (int i = 0; i < invAb.getContainerSize(); i++) {
				if (invAb.getItem(i).is(net.minecraft.world.item.Items.BOOK))
					invAb.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
			var accuseBook = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK, 1);
			accuseBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§5指认书"));
			float savedHpAb = victim.getHealth();
			victim.setHealth(0.0f); // 仅置 isDeadOrDying（同步执行并立即恢复，不走 tickDeath）
			check(gsm.handleModeBookDeathDrop(victim, accuseBook),
					"指认书死亡不掉落：拦截暂存", "handleModeBookDeathDrop=true（1 本暂存）");
			check(!gsm.handleModeBookDeathDrop(victim, new net.minecraft.world.item.ItemStack(
							net.minecraft.world.item.Items.BREAD, 1)),
					"指认书死亡不掉落：普通物品照常掉落", "面包不受保护");
			victim.setHealth(Math.max(1.0f, savedHpAb));
			gsm.returnKeptBooksOnRespawn(victim, false);
			check(countItem(victim, net.minecraft.world.item.Items.BOOK) == 1,
					"指认书重生返还", "背包返还 1 本");
			gsm.returnKeptBooksOnRespawn(victim, false); // 暂存已清零 → 不重复返还
			check(countItem(victim, net.minecraft.world.item.Items.BOOK) == 1,
					"指认书暂存清零（无复制）", "重复返还不叠加");

			// 多道具同帧暂存 → 重生全部返还（修复"多种道具被合并成一种、只返还一种/其余消失"根因）
			var invAb2 = victim.getInventory();
			for (int i = 0; i < invAb2.getContainerSize(); i++) {
				var st = invAb2.getItem(i);
				if (st.is(net.minecraft.world.item.Items.BOOK) || st.is(net.minecraft.world.item.Items.PAPER)
						|| st.is(net.minecraft.world.item.Items.CLOCK))
					invAb2.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
			var accuse2 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK, 1);
			accuse2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§5指认书"));
			var relic2 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 1);
			relic2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal(GameStateManager.RELIC_ITEM_NAME));
			var token3 = GameStateManager.createTaskRefreshToken();
			float savedHpMulti = victim.getHealth();
			victim.setHealth(0.0f);
			check(gsm.handleModeBookDeathDrop(victim, accuse2)
							&& gsm.handleModeBookDeathDrop(victim, relic2)
							&& gsm.handleModeBookDeathDrop(victim, token3),
					"多道具死亡暂存：三类模式道具同时暂存", "指认书 + 神谕纸符 + 任务刷新券");
			victim.setHealth(Math.max(1.0f, savedHpMulti));
			gsm.returnKeptBooksOnRespawn(victim, false);
			check(countItem(victim, net.minecraft.world.item.Items.BOOK) == 1
							&& countItem(victim, net.minecraft.world.item.Items.PAPER) == 1
							&& countItem(victim, net.minecraft.world.item.Items.CLOCK) == 1,
					"多道具重生全部返还（无合并丢失）",
					"指认书/纸符/刷新券各返还 1 件——修复多种道具被压成一种的根因");
			// 清掉本段返还的道具，避免残留纸符/刷新券泄漏到后续 testGodsLiesRelic（否则计件断言失真）
			for (int i = 0; i < invAb2.getContainerSize(); i++) {
				var st2 = invAb2.getItem(i);
				if (st2.is(net.minecraft.world.item.Items.BOOK) || st2.is(net.minecraft.world.item.Items.PAPER)
						|| st2.is(net.minecraft.world.item.Items.CLOCK))
					invAb2.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}

			// 平民指认失败：指认另一名平民 → 自己 -200 分淘汰（分数 ≤0，显示 0）→ 剩 1 平民 = 1 使者 → 使者获胜
			ServerPlayer otherCiv = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (!gsm.isGodEnvoy(p.getUUID()) && p != victim) { otherCiv = p; break; }
			}
			gsm.grantGodsLiesTaskReward(victim); // 指认权 +1
			gsm.resolveAccusation(victim.getUUID(), otherCiv.getUUID());
			check(gsm.isEliminated(victim.getUUID()) && gsm.getScore(victim.getUUID()) == 0,
					"指认失败淘汰", victim.getName().getString() + " 指认平民 → 自己 -200 分淘汰（分数 ≤0 显示为 0）");
			check("envoys_victory".equals(gsm.godsLiesEndReason())
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED
							&& gsm.getWinnersByScore().size() == 1,
					"使者胜利判定", "剩余平民 1 = 剩余使者 1 → 使者获胜并结束游戏");
		} finally {
			// 还原：常规模式 + 原阶段 + 生存模式 + 各假人规则集（不影响后续 startGame）
			gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(savedPhase);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				cm.clearPlayerChallenges(p.getUUID());
				for (String id : savedChallenges.get(p.getUUID())) cm.enableChallenge(p.getUUID(), id);
			}
		}
	}

	/**
	 * 神的谎言：神谕纸符（第九十一批按用户规则重做结算）——首件消耗道具全链路。
	 * 局1（2 使者 + 1 平民）：B 级不发放 / A/S 级首次获得 / 重复不发放 / 使者链接消耗纸符 /
	 * 平民武装神罚免疫与一次性使用 / 至少两名使者时被指认 → 使者与链接者一同出局（已武装纸符照常出局）。
	 * 局2（1 使者 + 2 平民）：纸符免疫挡降罪（一次性消耗）/ 纸符死亡保留（普通纸照常掉落）/
	 * 仅剩一名使者被指认 → 抵挡一次指认出局，链接者受神罚（免疫可挡）；第二次指认正常淘汰。
	 * 局3（1 使者 + 2 平民）：最后使者链接未武装平民 → 抵挡出局 + 链接者自动神罚 -55；第二次指认正常淘汰。
	 * 局4（1 使者 + 2 平民）：平民纸符只挡神罚，不挡指认失败 -200。
	 * 直接调用底层方法（真指令/网络路径无法在自测中覆盖），结束后还原常规模式与原阶段。
	 */
	private static void testGodsLiesRelic() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var savedPhase = gsm.getCurrentPhase();
		java.util.Map<UUID, java.util.Set<String>> savedChallenges = new java.util.HashMap<>();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			savedChallenges.put(p.getUUID(), new java.util.HashSet<>(cm.getPlayerChallenges(p.getUUID())));
		}
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);

			// ===== 局1：2 使者 + 1 平民 =====
			gsm.resetGodsLies();
			gsm.setEnvoyCount(2);
			gsm.setupGodsLiesEnvoys(server.get());
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);
			ServerPlayer env1 = null, env2 = null, civ = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) { if (env1 == null) env1 = p; else env2 = p; }
				else civ = p;
			}
			check(env1 != null && env2 != null && civ != null, "局1 使者任命", "3 人任命 2 名使者 + 1 名平民");

			// 发放规则：B 级不发 / A/S 级首次发 / 重复不重复发
			gsm.grantGodsLiesRelic(env1, false);
			check(!gsm.hasRelicObtained(env1.getUUID()) && countItem(env1, net.minecraft.world.item.Items.PAPER) == 0,
					"纸符发放：B 级不发放", "完成 B 级专属任务不获得神谕纸符");
			gsm.grantGodsLiesRelic(env1, true);
			check(gsm.hasRelicObtained(env1.getUUID()) && countItem(env1, net.minecraft.world.item.Items.PAPER) == 1,
					"纸符发放：A/S 级首次获得", "背包获得 1 张神谕纸符（材质为纸）");
			gsm.grantGodsLiesRelic(env1, true);
			check(countItem(env1, net.minecraft.world.item.Items.PAPER) == 1,
					"纸符发放：每人每局最多一次", "重复完成 A/S 级不重复发放");

			// 使者链接：消耗纸符并记录；一次性
			check(gsm.confirmRelicLink(env1, civ.getUUID()), "使者链接", "右键使用纸符 → 与平民建立链接（消耗纸符）");
			check(gsm.getLinkedPlayer(env1.getUUID()).equals(civ.getUUID()) && gsm.isRelicUsed(env1.getUUID())
							&& countItem(env1, net.minecraft.world.item.Items.PAPER) == 0,
					"使者链接：消耗与记录", "链接已记录且背包纸符已消耗");
			check(!gsm.confirmRelicLink(env1, env2.getUUID()), "使者链接：一次性", "已使用过 → 再次链接失败");

			// 平民武装神罚免疫：一次性
			gsm.grantGodsLiesRelic(civ, true);
			check(gsm.useRelicCivilian(civ) && gsm.hasRelicImmunity(civ.getUUID())
							&& gsm.isRelicUsed(civ.getUUID())
							&& countItem(civ, net.minecraft.world.item.Items.PAPER) == 0,
					"平民武装神罚免疫", "右键使用纸符 → 武装 1 次神罚免疫（不挡指认失败）");
			check(!gsm.useRelicCivilian(civ), "平民使用：一次性", "已使用过 → 再次使用失败");

			// 多使者被指认（第九十一批）：使者与链接者一同出局（已武装纸符的平民也照常出局）；
			// 存活平民 0、存活使者 1 → 使者获胜
			gsm.grantGodsLiesTaskReward(civ); // 指认权 +1
			var res1 = gsm.resolveAccusation(civ.getUUID(), env1.getUUID());
			check(res1 == GameStateManager.AccuseResult.BOTH_OUT && gsm.isEliminated(env1.getUUID())
							&& gsm.isEliminated(civ.getUUID()) && gsm.getScore(env1.getUUID()) == 100
							&& gsm.getScore(civ.getUUID()) == 100
							&& gsm.getLinkedPlayer(env1.getUUID()) == null,
					"双使者：使者与链接者一同出局",
					"契约触发 → 使者与已武装纸符的链接者强制出局（分数不变、纸符免疫不能挡）");
			check("envoys_victory".equals(gsm.godsLiesEndReason())
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED,
					"双使者一同出局：使者获胜判定", "存活平民 0 → 余下使者获胜");

			// ===== 局2：1 使者 + 2 平民 =====
			gsm.resetGodsLies();
			gsm.setEnvoyCount(1);
			gsm.setupGodsLiesEnvoys(server.get());
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);
			ServerPlayer envB = null, civA = null, civB = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) envB = p;
				else if (civA == null) civA = p;
				else civB = p;
			}
			// 最后使者链接已武装平民：先做需要 PLAYING 阶段的测试，指认（会结束游戏）放到本局最后
			gsm.grantGodsLiesRelic(envB, true);
			gsm.confirmRelicLink(envB, civA.getUUID());
			gsm.grantGodsLiesRelic(civA, true);
			gsm.useRelicCivilian(civA);
			// 纸符免疫挡降罪（civB 武装 → 一次性消耗）
			gsm.grantGodsLiesRelic(civB, true);
			gsm.useRelicCivilian(civB);
			gsm.grantGodsLiesTaskReward(envB); // 降罪权 +1
			gsm.envoyDeduct(envB.getUUID(), civB.getUUID());
			check(gsm.getScore(civB.getUUID()) == 100 && !gsm.hasRelicImmunity(civB.getUUID())
							&& !gsm.isEliminated(civB.getUUID()),
					"纸符免疫挡降罪", "已武装平民被降罪 → 神罚被挡（免疫消耗，分数不变）");
			// 纸符死亡保留（神的谎言 PLAYING 阶段：神谕纸符死亡不掉落，普通纸照常掉落）
			var relicPaper = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 1);
			relicPaper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal(GameStateManager.RELIC_ITEM_NAME));
			float savedHp2 = civB.getHealth();
			civB.setHealth(0.0f); // 仅置 isDeadOrDying（同步执行并立即恢复，不走 tickDeath）
			check(gsm.handleModeBookDeathDrop(civB, relicPaper),
					"纸符死亡保留：拦截暂存", "handleModeBookDeathDrop=true（神谕纸符暂存）");
			check(!gsm.handleModeBookDeathDrop(civB, new net.minecraft.world.item.ItemStack(
							net.minecraft.world.item.Items.PAPER, 1)),
					"纸符死亡保留：普通纸照常掉落", "无自定义名的纸不受保护");
			civB.setHealth(Math.max(1.0f, savedHp2));
			gsm.returnKeptBooksOnRespawn(civB, false);
			check(countItem(civB, net.minecraft.world.item.Items.PAPER) == 1,
					"纸符重生返还", "背包返还 1 张神谕纸符");

			// 任务刷新券（神的谎言开局道具）：时钟材质 + 自定义名 → 消耗后立即刷新当前任务
			var refreshToken = GameStateManager.createTaskRefreshToken();
			var tokenName = refreshToken.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
			check(refreshToken.is(net.minecraft.world.item.Items.CLOCK)
							&& tokenName != null && tokenName.getString().contains("任务刷新券"),
					"任务刷新券材质与名称", "时钟 + 自定义名「任务刷新券」");
			var tmRefresh = TaskManager.getInstance();
			tmRefresh.resetAll();
			tmRefresh.issueNewTask(civB);
			var taskBeforeRefresh = tmRefresh.getActiveTask(civB.getUUID());
			check(taskBeforeRefresh != null, "刷新券使用前有任务", "issueNewTask 已给 civB 发放任务");
			civB.getInventory().add(refreshToken);
			check(gsm.consumeTaskRefreshToken(civB) && countItem(civB, net.minecraft.world.item.Items.CLOCK) == 0,
					"任务刷新券消耗", "consumeTaskRefreshToken=true，背包时钟清零");
			check(!gsm.consumeTaskRefreshToken(civB), "任务刷新券一次性", "券已消耗 → 再次消耗失败");
			boolean refreshed = tmRefresh.refreshTaskNow(civB);
			var taskAfterRefresh = tmRefresh.getActiveTask(civB.getUUID());
			check(refreshed && taskAfterRefresh != null
							&& !taskAfterRefresh.id.equals(taskBeforeRefresh.id),
					"任务刷新券立即刷新任务",
					"旧任务 " + taskBeforeRefresh.id + " → 新任务 "
							+ (taskAfterRefresh == null ? "无" : taskAfterRefresh.id));
			// 无任务时刷新失败（网络层此时会返还券）
			tmRefresh.resetAll();
			check(!tmRefresh.refreshTaskNow(civB), "无任务不可刷新", "activeTasks 为空 → refreshTaskNow=false");
			// 刷新券死亡保留：与指认书/神谕纸符同链路
			var token2 = GameStateManager.createTaskRefreshToken();
			float savedHpToken = civB.getHealth();
			civB.setHealth(0.0f);
			check(gsm.handleModeBookDeathDrop(civB, token2),
					"刷新券死亡保留：拦截暂存", "handleModeBookDeathDrop=true（任务刷新券暂存）");
			check(!gsm.handleModeBookDeathDrop(civB, new net.minecraft.world.item.ItemStack(
							net.minecraft.world.item.Items.CLOCK, 1)),
					"刷新券死亡保留：普通时钟照常掉落", "无自定义名的时钟不受保护");
			civB.setHealth(Math.max(1.0f, savedHpToken));
			gsm.returnKeptBooksOnRespawn(civB, false);
			check(countItem(civB, net.minecraft.world.item.Items.CLOCK) == 1,
					"刷新券重生返还", "背包返还 1 个任务刷新券");
			// 清掉测试用券，避免后续流程干扰
			gsm.consumeTaskRefreshToken(civB);
			tmRefresh.resetAll();

			// 最后使者被指认（第九十一批）：纸符抵挡一次指认出局（不扣分、不淘汰、不公开），链接者受神罚；
			// 链接者已武装纸符 → 神罚被挡下（免疫消耗）
			gsm.grantGodsLiesTaskReward(civB); // 指认权 +1
			var res2 = gsm.resolveAccusation(civB.getUUID(), envB.getUUID());
			check(res2 == GameStateManager.AccuseResult.NULLIFIED && !gsm.isEliminated(envB.getUUID())
							&& gsm.getScore(envB.getUUID()) == 100 && !gsm.isEliminated(civA.getUUID())
							&& gsm.getScore(civA.getUUID()) == 100 && !gsm.hasRelicImmunity(civA.getUUID())
							&& gsm.getLinkedPlayer(envB.getUUID()) == null
							&& gsm.godsLiesEndReason() == null
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING,
					"最后使者纸符：抵挡一次指认出局",
					"使者 100 分不变、未淘汰；链接者神罚被纸符免疫挡下（免疫消耗）；对局继续");
			// 契约一次性：第二次指认恢复正常 -200 → 淘汰 → 平民获胜
			gsm.grantGodsLiesTaskReward(civB);
			var res2b = gsm.resolveAccusation(civB.getUUID(), envB.getUUID());
			check(res2b == GameStateManager.AccuseResult.HIT && gsm.isEliminated(envB.getUUID())
							&& gsm.getScore(envB.getUUID()) == 0
							&& "envoys_eliminated".equals(gsm.godsLiesEndReason())
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED,
					"最后使者纸符：仅抵挡一次",
					"第二次指认无契约 → -200 淘汰 → 平民获胜");

			// ===== 局3：1 使者 + 2 平民（链接者未武装）=====
			gsm.resetGodsLies();
			gsm.setEnvoyCount(1);
			gsm.setupGodsLiesEnvoys(server.get());
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);
			ServerPlayer envC = null, civC = null, civD = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) envC = p;
				else if (civC == null) civC = p;
				else civD = p;
			}
			gsm.grantGodsLiesRelic(envC, true);
			gsm.confirmRelicLink(envC, civC.getUUID());
			gsm.grantGodsLiesTaskReward(civD); // 指认权 +1
			var res3 = gsm.resolveAccusation(civD.getUUID(), envC.getUUID());
			check(res3 == GameStateManager.AccuseResult.NULLIFIED && !gsm.isEliminated(envC.getUUID())
							&& gsm.getScore(envC.getUUID()) == 100 && gsm.getScore(civC.getUUID()) == 45
							&& !gsm.isEliminated(civC.getUUID())
							&& gsm.getLinkedPlayer(envC.getUUID()) == null
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING,
					"最后使者链接未武装平民 → 抵挡出局 + 链接者神罚",
					"使者 100 分不变未淘汰；链接者 100 → 45；契约一次性清除");
			// 契约已消耗：第二次指认恢复正常 -200 → 淘汰 → 平民获胜
			gsm.grantGodsLiesTaskReward(civD);
			var res3b = gsm.resolveAccusation(civD.getUUID(), envC.getUUID());
			check(res3b == GameStateManager.AccuseResult.HIT && gsm.isEliminated(envC.getUUID())
							&& gsm.getScore(envC.getUUID()) == 0
							&& "envoys_eliminated".equals(gsm.godsLiesEndReason())
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED,
					"最后使者：第二次指认正常淘汰",
					"无契约 → -200 淘汰 → 平民获胜");

			// ===== 局4：平民纸符不挡指认失败扣分 =====
			gsm.resetGodsLies();
			gsm.setEnvoyCount(1);
			gsm.setupGodsLiesEnvoys(server.get());
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);
			ServerPlayer civE = null, civF = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) continue;
				if (civE == null) civE = p;
				else civF = p;
			}
			gsm.grantGodsLiesRelic(civF, true);
			gsm.useRelicCivilian(civF);
			gsm.grantGodsLiesTaskReward(civF);
			var res4 = gsm.resolveAccusation(civF.getUUID(), civE.getUUID());
			check(civE != null && res4 == GameStateManager.AccuseResult.MISS
							&& gsm.getScore(civF.getUUID()) == 0 && gsm.isEliminated(civF.getUUID())
							&& gsm.hasRelicImmunity(civF.getUUID()),
					"平民纸符：不挡指认失败扣分",
					"指认平民失败 → 自己 -200（100 分 → 淘汰），纸符神罚免疫未被消耗");
		} finally {
			// 还原：常规模式 + 原阶段 + 生存模式 + 各假人规则集 + 清理背包纸符（不影响后续 startGame）
			gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(savedPhase);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (inv.getItem(i).is(net.minecraft.world.item.Items.PAPER))
						inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				}
				cm.clearPlayerChallenges(p.getUUID());
				for (String id : savedChallenges.get(p.getUUID())) cm.enableChallenge(p.getUUID(), id);
			}
		}
	}

	/**
	 * 走私犯与检察官：收集清单三档分值 + 容器放入即加分 + 检查室禁丢弃 + 检查发起书死亡保护
	 * （新规则分支断言见 testBatch61Features）。
	 * 直接调用 SmuggleManager 底层方法，结束后还原模式/阶段/规则集/背包。
	 */
	private static void testStowaway() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		java.util.Map<UUID, java.util.Set<String>> savedChallenges = new java.util.HashMap<>();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			savedChallenges.put(p.getUUID(), new java.util.HashSet<>(cm.getPlayerChallenges(p.getUUID())));
		}
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				gsm.setScore(p.getUUID(), 10);
				p.getInventory().clearContent();
			}

			sm.setup(srv);

			// 1) 检查室就绪
			check(sm.hasCabin(), "检查室构建", "cabinSpot A/B 已就位");

			// 2) 清单三件：每件随机 8/4/2 档（允许同批多个 4 / 多个 2），同批最多 1 个 8 分档，且三件互不重复
			String key = sm.groupKeyOf(red1);
			var batch = sm.batchFor(key);
			boolean tiersOk = batch != null && batch.length == 3;
			boolean distinctOk = batch != null
					&& batch[0] != batch[1] && batch[1] != batch[2] && batch[0] != batch[2];
			int eightCount = 0;
			if (batch != null) {
				for (net.minecraft.world.item.Item it : batch) {
					int v = com.survivalcontract.smuggle.SmuggleManager.publicValueOf(it);
					if (v != 8 && v != 4 && v != 2) tiersOk = false;
					if (v == 8) eightCount++;
				}
			}
			check(tiersOk, "价值物品随机档位", "三件均为 8/4/2 档（同档可多件）");
			check(eightCount <= 1, "每批最多 1 个 8 分物品", "本批 8 分档数量 = " + eightCount);
			check(distinctOk, "批内物品不重复", "三件互不相同");

			// 3) 价值收纳袋（服务端 27 格容器）：货物存入袋中，检查通行时自动清空兑换成分
			sm.initPlayerKit(red1); // 发放两个收纳袋钥匙道具
			var cont = sm.valueBagFor(red1.getUUID());
			cont.setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 3));
			check(!cont.isEmpty() && cont.getItem(0).getCount() == 3,
					"价值收纳袋可存放货物", "袋容器正常存取（检查通行时结算）");
			cont.clearContent();

			// 5) 检查室禁丢弃：检查阶段双方无法丢弃物品（原版取出→drop 时序模拟 Q 键，物品被退回背包）
			gsm.setScore(red2.getUUID(), 10);
			boolean initiated = sm.initiate(red1, red2);
			check(initiated && sm.isInInspection(red1.getUUID()) && sm.isInInspection(red2.getUUID()),
					"检查室判定", "检察官与被检查者均处于检查室");
			red2.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 5));
			int dirtBefore2 = countItem(red2, net.minecraft.world.item.Items.DIRT);
			net.minecraft.world.item.ItemStack tossed2 = takeFirstItem(red2, net.minecraft.world.item.Items.DIRT);
			com.survivalcontract.McCompat.drop(red2, tossed2, false, true);
			check(countItem(red2, net.minecraft.world.item.Items.DIRT) == dirtBefore2,
					"检查室禁丢弃（被检查者）", "丢弃被拦截，" + tossed2.getCount() + " 个泥土退回背包");
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 3));
			int dirtBefore1 = countItem(red1, net.minecraft.world.item.Items.DIRT);
			net.minecraft.world.item.ItemStack tossed1 = takeFirstItem(red1, net.minecraft.world.item.Items.DIRT);
			com.survivalcontract.McCompat.drop(red1, tossed1, false, true);
			check(countItem(red1, net.minecraft.world.item.Items.DIRT) == dirtBefore1,
					"检查室禁丢弃（检察官）", "丢弃被拦截，" + tossed1.getCount() + " 个泥土退回背包");

			// 5.5) 检查互斥：已有两人待检查（检查室使用中）→ 其余人不能发起，尤其不能对检查室内的两人发起
			//     （复用步骤 5 发起、仍在进行中的 red1→red2 检查）
			boolean busyToProsecutor = !sm.initiate(blue1, red1); // 对检查中的检察官发起
			boolean busyToTarget = !sm.initiate(blue1, red2);     // 对检查中的被检查者发起
			check(busyToProsecutor && busyToTarget,
					"检查互斥：两人待检查时禁止新发起", "blue1 对检查室内的 red1/red2 发起均被拒");
			sm.dropPending(red1.getUUID()); // 检查结束 → 检查室空出

			// 6) 检查发起书死亡不掉落：拦截暂存 → 重生返还（无复制）
			var invBk = red1.getInventory();
			for (int i = 0; i < invBk.getContainerSize(); i++) {
				if (invBk.getItem(i).is(net.minecraft.world.item.Items.BOOK))
					invBk.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
			var prosecuteBook = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK, 1);
			prosecuteBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§b检查发起书"));
			var wrongModeBook = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK, 1);
			wrongModeBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§5指认书"));
			float savedHpBk = red1.getHealth();
			red1.setHealth(0.0f); // 仅置 isDeadOrDying（同步执行并立即恢复，不走 tickDeath）
			check(gsm.handleModeBookDeathDrop(red1, prosecuteBook),
					"检查发起书死亡不掉落：拦截暂存", "handleModeBookDeathDrop=true（1 本暂存）");
			check(!gsm.handleModeBookDeathDrop(red1, wrongModeBook),
					"道具书按模式区分：走私模式指认书不受保护", "仅检查发起书受保护");
			check(!gsm.handleModeBookDeathDrop(red1, new net.minecraft.world.item.ItemStack(
							net.minecraft.world.item.Items.BREAD, 1)),
					"检查发起书死亡不掉落：普通物品照常掉落", "面包不受保护");
			red1.setHealth(Math.max(1.0f, savedHpBk));
			gsm.returnKeptBooksOnRespawn(red1, false);
			check(countItem(red1, net.minecraft.world.item.Items.BOOK) == 1,
					"检查发起书重生返还", "背包返还 1 本");
			gsm.returnKeptBooksOnRespawn(red1, false); // 暂存已清零 → 不重复返还
			check(countItem(red1, net.minecraft.world.item.Items.BOOK) == 1,
					"检查发起书暂存清零（无复制）", "重复返还不叠加");
			// 对照：非检查室玩家丢弃不受影响（生成掉落物，立即清除实体）
			blue1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 2));
			int dirtBeforeB = countItem(blue1, net.minecraft.world.item.Items.DIRT);
			net.minecraft.world.item.ItemStack tossedB = takeFirstItem(blue1, net.minecraft.world.item.Items.DIRT);
			var entity = com.survivalcontract.McCompat.drop(blue1, tossedB, false, true);
			if (entity != null) entity.discard();
			check(countItem(blue1, net.minecraft.world.item.Items.DIRT) == dirtBeforeB - tossedB.getCount(),
					"非检查室丢弃不受影响", "blue1 丢弃成功（无全局拦截）");
		} finally {
			// 还原：玩法/阶段/规则集/背包/分数（不影响后续 startGame）
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
				cm.clearPlayerChallenges(p.getUUID());
				for (String id : savedChallenges.get(p.getUUID())) cm.enableChallenge(p.getUUID(), id);
			}
		}
	}

	/**
	 * 第六十一批：走私犯与检察官新规则——开局双收纳袋 + 走私品固定 128 分 +
	 * 检查三分支（受贿 / 无走私品赔偿 1/4 / 有走私品没收且贿赂袋保留）+ 检查发起书无冷却。
	 * 第六十二批修订：通行（受贿 / 无走私品）时直接清空货物袋并兑换成分；开箱机会机制已移除。
	 */
	private static void testBatch61Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				gsm.setScore(p.getUUID(), 0);
				p.getInventory().clearContent();
			}
			sm.setup(srv);
			String key = sm.groupKeyOf(red1);
			sm.setBatchForTest(key, new net.minecraft.world.item.Item[]{
					net.minecraft.world.item.Items.IRON_INGOT,
					net.minecraft.world.item.Items.BREAD,
					net.minecraft.world.item.Items.GOLD_INGOT});
			sm.setSmuggleItemForTest(key, net.minecraft.world.item.Items.DIRT);

			// 1) 开局套装：两个不同的收纳袋钥匙道具 + 服务端双袋容器（第六十四批：原版收纳袋形态）
			var redBundle = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "red_bundle"));
			sm.initPlayerKit(red1);
			check(countItem(red1, net.minecraft.world.item.Items.BUNDLE) == 1
							&& countItem(red1, redBundle) == 1,
					"开局发放两个不同的收纳袋", "价值收纳袋（棕色收纳袋）+ 贿赂收纳袋（红色收纳袋）");

			// 2) 走私品固定 128 分且与公开商品池不相交（仅我方可见由 sendSync 保证，服务端不测）
			//    （本次修复：走私品池改为易生存获得的日常原材料，移除全部工具与武器）
			boolean smuggleValueOk = true;
			for (net.minecraft.world.item.Item it : new net.minecraft.world.item.Item[]{
					net.minecraft.world.item.Items.DIRT, net.minecraft.world.item.Items.COBBLESTONE,
					net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.OAK_PLANKS,
					net.minecraft.world.item.Items.STICK, net.minecraft.world.item.Items.SAND,
					net.minecraft.world.item.Items.GRAVEL, net.minecraft.world.item.Items.WHEAT,
					net.minecraft.world.item.Items.STRING, net.minecraft.world.item.Items.LEATHER,
					net.minecraft.world.item.Items.FLINT, net.minecraft.world.item.Items.BONE,
					net.minecraft.world.item.Items.FEATHER, net.minecraft.world.item.Items.SUGAR_CANE,
					net.minecraft.world.item.Items.CLAY_BALL, net.minecraft.world.item.Items.ROTTEN_FLESH}) {
				if (!com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(it)) smuggleValueOk = false;
			}
			// 工具与武器既不是公开价值物品、也不再是走私品（违禁品列表已移除工具与武器）
			boolean noToolsAsSmuggle = !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.WOODEN_SWORD)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(net.minecraft.world.item.Items.BOW)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.STONE_PICKAXE)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.IRON_SWORD);
			boolean poolDisjoint = !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.DIAMOND)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(net.minecraft.world.item.Items.BREAD);
			check(smuggleValueOk && noToolsAsSmuggle && poolDisjoint, "走私品固定 128 分（与公开商品池不相交）",
					"走私品池 16 件日常原材料全部 128 分；工具与武器已从违禁品列表移除（木剑/弓/石镐/铁剑皆非违禁品）；钻石/面包不属于走私品");

			// 3) 受贿分支（用户规则 ②）：贿赂袋清→检察官加分；价值袋清→被检查者加分；散装不动
			sm.setBatchForTest(sm.groupKeyOf(red1),
					new net.minecraft.world.item.Item[]{
							net.minecraft.world.item.Items.DIAMOND,
							net.minecraft.world.item.Items.GOLD_INGOT,
							net.minecraft.world.item.Items.IRON_INGOT}); // 批次分值 8/8/4（静态表）
			sm.setSmuggleItemForTest(sm.groupKeyOf(red1), net.minecraft.world.item.Items.DIRT);
			gsm.setScore(red1.getUUID(), 10);
			gsm.setScore(red2.getUUID(), 0);
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.GOLD_INGOT, 1)); // 散装金锭 → 不动
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 2)); // 价值袋铁锭×2 = 2*4 = 8 分
			sm.bribeBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIAMOND, 2)); // 贿赂袋钻石×2 = 2*8 = 16 分
			check(sm.initiate(red1, red2), "检查发起（首次无冷却限制）", "initiate=true（该玩家尚无可冷却/检察官记忆）");
			sm.decide(srv, red2.getUUID(), red1.getUUID(),
					com.survivalcontract.smuggle.SmuggleManager.ACTION_ACCEPT_BRIBE);
			check(gsm.getScore(red2.getUUID()) == 16, "受贿：检察官获得贿赂物品总价", "red2 +16（2 钻石×8）");
			check(sm.bribeBagFor(red1.getUUID()).isEmpty(), "受贿：贿赂收纳袋清空", "red1 贿赂袋已清空");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(), "受贿通行：货物袋清空", "价值收纳袋已清空");
			check(gsm.getScore(red1.getUUID()) == 18, "受贿通行：价值袋兑换入分",
					"价值袋铁锭×2 = +8 分（10 → 18）；散装金锭不动不计分");
			check(countItem(red1, net.minecraft.world.item.Items.GOLD_INGOT) == 1,
					"受贿：物品栏散装保留不动", "金锭 1 个在物品栏里不动（用户规则 ①）");
			check(!sm.isAwaitingDecision(red1.getUUID()), "受贿：检查结束", "pending 已清除");

			// 4) 检查·情况一（无走私品，用户规则 ③）：检察官赔 1/4 货品（价值袋内容）+ 价值袋清→被检查者加分
			red1.getInventory().clearContent();
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 3)); // 散装铁锭 → 不动
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 2)); // 价值袋铁锭 = 2*4 = 8 分（赔偿按 8/4=2）
			sm.bribeBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 1)); // 贿赂袋里的走私品不算走私证据
			sm.bribeBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.BREAD, 2));
			gsm.setScore(red1.getUUID(), 10);
			gsm.setScore(red2.getUUID(), 20);
			sm.clearProsecuteLimitsForTest(); // 跳过冷却 + 检察官记忆（脱离第 3 步的发起记录）
			sm.initiate(red1, red2);
			sm.decide(srv, red2.getUUID(), red1.getUUID(),
					com.survivalcontract.smuggle.SmuggleManager.ACTION_INSPECT);
			check(gsm.getScore(red2.getUUID()) == 18 && gsm.getScore(red1.getUUID()) == 20,
					"检查无走私品：赔偿 + 价值袋兑换",
					"货品 8 分 → red2 -2 赔偿；red1 +2 赔偿 +8 价值袋兑换（10+2+8=20）");
			check(sm.bribeBagFor(red1.getUUID()).getItem(0).getCount() == 1
							&& sm.bribeBagFor(red1.getUUID()).getItem(1).getCount() == 2,
					"贿赂袋不算走私证据且不受影响", "袋中泥土+面包保留");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(),
					"无走私品放行：价值袋清空兑换", "价值收纳袋已清空");
			check(countItem(red1, net.minecraft.world.item.Items.IRON_INGOT) == 3,
					"无走私品放行：散装货品保留", "铁锭 3 个在物品栏里不动（用户规则 ①）");

			// 5) 检查·情况二（有走私品，用户规则 ④）：没收价值袋给检察官加分，贿赂袋/物品栏不受影响
			red1.getInventory().clearContent();
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIAMOND, 1)); // 散装钻石 → 不动
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 1)); // 散装泥土 → 不动（走私证据只看价值袋）
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 2)); // 价值袋铁锭 = 2*4 = 8 分
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 1)); // 价值袋泥土（当前批次走私品）= 128 分
			sm.bribeBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.BREAD, 1)); // 应保留
			gsm.setScore(red1.getUUID(), 10);
			gsm.setScore(red2.getUUID(), 0);
			sm.clearProsecuteLimitsForTest(); // 跳过冷却 + 检察官记忆（脱离第 4 步的发起记录）
			sm.initiate(red1, red2);
			sm.decide(srv, red2.getUUID(), red1.getUUID(),
					com.survivalcontract.smuggle.SmuggleManager.ACTION_INSPECT);
			check(gsm.getScore(red2.getUUID()) == 136, "检查有走私品：没收价值袋给检察官",
					"red2 +136（价值袋铁锭 8 + 走私品 128）");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(),
					"没收：价值收纳袋清空", "价值袋里的铁锭+泥土都被没收");
			check(countItem(red1, net.minecraft.world.item.Items.DIAMOND) == 1
							&& countItem(red1, net.minecraft.world.item.Items.DIRT) == 1,
					"没收：物品栏不受影响", "散装钻石+泥土都在物品栏里不动（用户规则 ①）");
			check(sm.bribeBagFor(red1.getUUID()).getItem(0).getCount() == 1,
					"没收：贿赂袋不受影响", "袋中 1 个面包保留");

			// 6) 检查互斥 + 发起书 2 分钟冷却 + 不能连续选同一位检察官
			sm.clearProsecuteLimitsForTest(); // 清掉前几步的发起记录，专注互斥/冷却/去重
			sm.initiate(red2, blue1);
			check(!sm.initiate(red1, blue1) && !sm.initiate(blue1, red2),
					"检查互斥：检查室使用中禁止新发起", "对检查中双方发起均被拒");
			sm.dropPending(red2.getUUID());
			// 冷却：同一玩家 2 分钟内不得再次发起（刚发起过的 red2）
			check(!sm.initiate(red2, blue1), "发起书 2 分钟冷却",
					"刚发起过（red2）→ 冷却期内再次发起被拒");
			// 跳过冷却计时，仍"不能连续两次选择同一位玩家作为自己的检察官"
			sm.clearProsecuteCooldownForTest();
			check(!sm.initiate(red2, blue1), "不能连续选同一位检察官",
					"上次检察官=blue1 → 再次选 blue1 被拒");
			// 清除检察官记忆后可再发起
			sm.clearProsecuteLimitsForTest();
			check(sm.initiate(red2, blue1), "清除发起限制后可再发起",
					"冷却计时与检察官记忆均清空后立即再发起成功");
			sm.dropPending(red2.getUUID());
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十二批：检查通行（受贿 / 检查无走私品）直接清空货物袋并兑换成分（核心结算由
	 * testBatch61 步骤 3/4 覆盖）+ 开箱机会机制移除后拾取恢复正常 + 检查超时自动视为受贿（第六十三批）。
	 */
	private static void testBatch62Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				gsm.setScore(p.getUUID(), 0);
				p.getInventory().clearContent();
			}
			sm.setup(srv);
			sm.groupKeyOf(red1);
			sm.setBatchForTest(sm.groupKeyOf(red1), new net.minecraft.world.item.Item[]{
					net.minecraft.world.item.Items.IRON_INGOT,
					net.minecraft.world.item.Items.BREAD,
					net.minecraft.world.item.Items.GOLD_INGOT});
			sm.setSmuggleItemForTest(sm.groupKeyOf(red1), net.minecraft.world.item.Items.DIRT);
			sm.initPlayerKit(red1);

			// 1) 开箱机会机制移除：掉落物拾取恢复正常（ItemEntity.playerTouch 无拦截）
			var dropEntity62 = new net.minecraft.world.entity.item.ItemEntity(
					red1.level(), red1.getX(), red1.getY(), red1.getZ(),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 1));
			dropEntity62.playerTouch(red1);
			check(countItem(red1, net.minecraft.world.item.Items.IRON_INGOT) == 1,
					"开箱机会机制移除：拾取恢复正常", "playerTouch 直接进背包（无拦截）");

			// 2) 通行兑换：只有当前批次公开商品有分，非本批次/非价值物品都 0 分但仍随袋清空
			red1.getInventory().clearContent();
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIAMOND, 1)); // 钻石不在当前批次 → 0 分（用户规则 ⑤）
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.STONE, 3)); // 非价值杂物：清空不加分
			sm.bribeBagFor(red1.getUUID()).clearContent();
			gsm.setScore(red1.getUUID(), 5);
			gsm.setScore(red2.getUUID(), 0);
			sm.initiate(red1, red2);
			sm.decide(srv, red2.getUUID(), red1.getUUID(),
					com.survivalcontract.smuggle.SmuggleManager.ACTION_ACCEPT_BRIBE);
			check(gsm.getScore(red1.getUUID()) == 5
							&& sm.valueBagFor(red1.getUUID()).isEmpty(),
					"通行兑换：非本批次物品 0 分但价值袋仍清空", "钻石（非当前批次）+ 石块均 0 分 → 分数仍 5；价值袋清空（袋子语义）");

			// 3) 超时自动视为受贿（第六十三批）：检察官未定夺 → 系统自动执行受贿结算（双袋清空 + 回原位）
			red1.getInventory().clearContent();
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_INGOT, 2)); // 货物袋 8 分 → 受贿通行兑换
			sm.bribeBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.GOLD_INGOT, 1)); // 贿赂袋 4 分 → 检察官 +4
			gsm.setScore(red1.getUUID(), 0);
			gsm.setScore(red2.getUUID(), 0);
			sm.clearProsecuteLimitsForTest(); // 跳过第 2 步的发起记录（冷却 + 检察官记忆）
			sm.initiate(red1, red2);
			var pp62 = sm.awaitingRecord(red1.getUUID());
			var from62 = pp62.fromInitiator();
			sm.dropPending(red1.getUUID()); // 与真实超时路径一致：tick 先行移除记录
			sm.timeoutAutoAcceptBribe(srv, pp62);
			check(gsm.getScore(red2.getUUID()) == 4 && gsm.getScore(red1.getUUID()) == 8
							&& sm.bribeBagFor(red1.getUUID()).isEmpty()
							&& sm.valueBagFor(red1.getUUID()).isEmpty()
							&& red1.blockPosition().equals(from62),
					"超时自动视为受贿（含回原位）", "检察官 +4（贿赂袋）+ 被检查者 +8（货物袋兑换），双袋清空，回原位");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十三批：检查室扩建（内部 5×7×3：石英墙壁 + 白桦木板底板/天花板 + 顶部蛙明灯 +
	 * 两侧杜鹃花树苗盆栽）+ 红蓝地毯站位（被检查者=红 / 检察官=蓝，长方向相隔 3 方块）+
	 * 进室各就地毯位 + 进室后不能移动（tick 钳制拉回）。
	 */
	private static void testBatch63Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2}) {
				p.getInventory().clearContent();
			}
			sm.setup(srv);
			var level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(srv);
			if (level == null) level = srv.overworld(); // startGame 前竞技场未建（与 buildCabin fallback 一致）
			var minC = sm.cabinMinForTest();
			var maxC = sm.cabinMaxForTest();
			var spotA = sm.cabinSpotAForTest();
			var spotB = sm.cabinSpotBForTest();
			int mx = (minC.getX() + maxC.getX()) / 2;
			int mz = (minC.getZ() + maxC.getZ()) / 2;

			// 1) 检查室外壳尺寸：内部 5×7×3（外壳含双墙 7×5×9）
			check(maxC.getX() - minC.getX() == 6 && maxC.getY() - minC.getY() == 4
							&& maxC.getZ() - minC.getZ() == 8,
					"检查室扩建：内部 5×7×3", "外壳 7×5×9（长5+双墙 × 高3+底板天花板 × 宽7+双墙）");

			// 2) 材料：石英墙壁 + 白桦木板底板/天花板
			check(level.getBlockState(new net.minecraft.core.BlockPos(minC.getX(), minC.getY() + 2, mz))
							.is(net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK)
							&& level.getBlockState(new net.minecraft.core.BlockPos(maxC.getX(), minC.getY() + 2, mz))
							.is(net.minecraft.world.level.block.Blocks.QUARTZ_BLOCK),
					"检查室石英墙壁", "两侧长墙为石英块");
			check(level.getBlockState(new net.minecraft.core.BlockPos(mx, minC.getY(), spotA.getZ()))
							.is(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS)
							&& level.getBlockState(new net.minecraft.core.BlockPos(mx, maxC.getY(), minC.getZ() + 1))
							.is(net.minecraft.world.level.block.Blocks.BIRCH_PLANKS),
					"检查室白桦木板底板/天花板", "底板与天花板均为白桦木板（天花板采样避开中央蛙明灯）");

			// 3) 顶部蛙明灯 + 两侧杜鹃花树苗盆栽
			check(level.getBlockState(new net.minecraft.core.BlockPos(mx, maxC.getY(), mz))
							.is(net.minecraft.world.level.block.Blocks.OCHRE_FROGLIGHT),
					"检查室顶部蛙明灯", "天花板中央为蛙明灯");
			check(level.getBlockState(new net.minecraft.core.BlockPos(mx, spotA.getY(), minC.getZ() + 1))
							.is(net.minecraft.world.level.block.Blocks.POTTED_AZALEA)
							&& level.getBlockState(new net.minecraft.core.BlockPos(mx, spotA.getY(), maxC.getZ() - 1))
							.is(net.minecraft.world.level.block.Blocks.POTTED_AZALEA),
					"检查室两侧杜鹃花树苗盆栽", "两条长墙内侧中央各一盆");

			// 4) 红蓝地毯站位：被检查者=红地毯 / 检察官=蓝地毯，长方向中间相隔 3 方块
			check(net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getKey(level.getBlockState(spotA).getBlock()).toString().equals("minecraft:red_carpet")
							&& net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getKey(level.getBlockState(spotB).getBlock()).toString().equals("minecraft:blue_carpet")
							&& spotB.getX() - spotA.getX() == 4,
					"检查室红蓝地毯站位", "被检查者=红 / 检察官=蓝，地毯间相隔 3 方块");

			// 5) 进室传送：被检查者至红地毯 / 检察官至蓝地毯
			sm.initiate(red1, red2);
			var pp63 = sm.awaitingRecord(red1.getUUID());
			sm.enterInspectionRoom(srv, pp63);
			check(red1.blockPosition().equals(spotA) && red2.blockPosition().equals(spotB),
					"进室各就地毯站位", "被检查者→红地毯 / 检察官→蓝地毯");

			// 6) 进室后不能移动：偏离地毯站位 → tick 钳制拉回
			red1.teleportTo(level, spotA.getX() + 2.5, spotA.getY(), spotA.getZ() + 0.5,
					java.util.Set.of(), red1.getYRot(), red1.getXRot(), false);
			sm.tick(srv);
			check(red1.blockPosition().equals(spotA),
					"检查室内不能移动（tick 钳制）", "偏离红地毯 2 格 → 被 tick 拉回原位");
			sm.dropPending(red1.getUUID());
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十四批：收纳袋改用原版收纳袋形态（价值袋=棕色 bundle / 贿赂袋=红色 bundle）+
	 * 公开商品池删除全部工具与武器（走私品池可出现）+ 走私品池换为易生存获得的日常物品与工具武器
	 * + 防原版夹带（收纳袋道具内嵌的 BundleContents 在开袋/进检查时倒回物品栏）。
	 */
	private static void testBatch64Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
			sm.setup(srv);
			sm.initPlayerKit(red1);
			var redBundle = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "red_bundle"));

			// 1) 收纳袋形态：价值袋=棕色 bundle / 贿赂袋=红色 bundle（注册表直查，自定义名区分）
			check(countItem(red1, net.minecraft.world.item.Items.BUNDLE) == 1 && countItem(red1, redBundle) == 1,
					"收纳袋改用原版收纳袋形态", "价值袋=棕色收纳袋 / 贿赂袋=红色收纳袋（各 1）");

			// 2) 公开商品池与违禁品池均不再含工具与武器（本次修复：移除违禁品列表中的工具与武器）
			boolean noToolsInPublic = com.survivalcontract.smuggle.SmuggleManager.publicValueOf(
					net.minecraft.world.item.Items.DIAMOND_SWORD) == 0
					&& com.survivalcontract.smuggle.SmuggleManager.publicValueOf(
					net.minecraft.world.item.Items.IRON_PICKAXE) == 0
					&& com.survivalcontract.smuggle.SmuggleManager.publicValueOf(
					net.minecraft.world.item.Items.CROSSBOW) == 0
					&& com.survivalcontract.smuggle.SmuggleManager.publicValueOf(
					net.minecraft.world.item.Items.IRON_SWORD) == 0;
			boolean noToolsInSmuggle = !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.WOODEN_SWORD)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(net.minecraft.world.item.Items.BOW)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.STONE_PICKAXE)
					&& !com.survivalcontract.smuggle.SmuggleManager.isSmuggleItem(
					net.minecraft.world.item.Items.IRON_PICKAXE);
			check(noToolsInPublic && noToolsInSmuggle, "工具与武器不在价值池也不在违禁品池",
					"钻石剑/铁镐/弩/铁剑非价值物品；木剑/弓/石镐/铁镐也不再是违禁品（已移除违禁品列表中的工具与武器）");

			// 3) 防原版夹带：收纳袋道具内嵌的原版 BundleContents 在开袋时倒入对应服务端袋容器（价值袋）并清空内嵌存储。
			//    —— 左键点选塞入收纳袋的货品借此计入袋容器，与右键开袋放入一致计分（修复"左键放入无分数"）
			var stuffed = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE);
			stuffed.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§6价值收纳袋"));
			var mutContents = com.survivalcontract.McCompat.newMutableBundle(
					net.minecraft.world.item.component.BundleContents.EMPTY);
			mutContents.tryInsert(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 4));
			stuffed.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS, mutContents.toImmutable());
			red1.getInventory().clearContent();
			red1.getInventory().add(stuffed);
			sm.openBag(red1, false);
			red1.closeContainer();
			var bcAfter = stuffed.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
			var valueBag = sm.valueBagFor(red1.getUUID());
			boolean dirtInBag = false;
			for (int i = 0; i < valueBag.getContainerSize(); i++) {
				var s = valueBag.getItem(i);
				if (s.is(net.minecraft.world.item.Items.DIRT) && s.getCount() == 4) dirtInBag = true;
			}
			check(dirtInBag && countItem(red1, net.minecraft.world.item.Items.DIRT) == 0
							&& (bcAfter == null || bcAfter.isEmpty()),
					"防原版夹带：内嵌货品倒入服务端袋容器",
					"内嵌泥土×4 转入价值袋容器（与右键放入一致计分），物品栏不再散装、内嵌存储清空");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十六批：贿赂袋兼容两边的价值物品（走私品除外）+ 检察书显示对方贿赂袋价值。
	 * ① 贿赂袋计价排除走私品；② blockBribeDeposit 守卫直测（放置/换位/shift 存入/拖拽拦截，拿取放行）。
	 */
	private static void testBatch66Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			sm.setup(server.get());
			sm.initPlayerKit(red1);
			red1.getInventory().clearContent();
			gsm.setScore(red1.getUUID(), 0);
			var DIRT = net.minecraft.world.item.Items.DIRT;
			var IRON = net.minecraft.world.item.Items.IRON_INGOT;

			// 1) 贿赂袋计价兼容两边价值物品且排除走私品：铁锭×2（8 分）+ 泥土×3（走私品 128 分 → 不计）
			var bribeBag = sm.bribeBagFor(red1.getUUID());
			bribeBag.clearContent();
			bribeBag.setItem(0, new net.minecraft.world.item.ItemStack(IRON, 2));
			bribeBag.setItem(1, new net.minecraft.world.item.ItemStack(DIRT, 3));
			check(sm.bribeBagTotal(red1.getUUID()) == 8,
					"贿赂袋计价排除走私品（价值物品兼容）", "铁锭×2=8 分；泥土（走私品 128 分）不计入");

			// 2) 守卫直测：ChestMenu.threeRows 绑定贿赂袋标记容器（服务端识别路径）
			var bribeContainer = (com.survivalcontract.smuggle.SmuggleManager.BribeBagContainer) bribeBag;
			var menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, red1.getInventory(), bribeContainer);
			menu.setCarried(new net.minecraft.world.item.ItemStack(DIRT, 1));
			check(sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"禁放走私品：手持泥土点袋格拦截", "PICKUP 放置被取消（点击无反馈）");
			menu.setCarried(new net.minecraft.world.item.ItemStack(IRON, 1));
			check(!sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"价值物品可入贿赂袋", "手持铁锭点袋格放行（两边价值物品兼容）");
			menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
			check(!sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"从贿赂袋拿取始终放行", "空手 PICKUP 不拦截");
			check(!sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE),
					"袋区 shift 取出放行", "QUICK_MOVE 袋格=取出不拦截");
			red1.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(DIRT, 1)); // 菜单槽 27 ↔ 物品栏 9
			check(sm.blockBribeDeposit(menu, red1, 27, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE),
					"背包区 shift 存入走私品拦截", "shift 点背包里的泥土 = 存入被取消");
			red1.getInventory().setItem(9, net.minecraft.world.item.ItemStack.EMPTY);
			red1.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(IRON, 1));
			red1.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(DIRT, 1));
			check(!sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.SWAP),
					"数字键换位放行（目标=价值物品）", "快捷栏铁锭与袋格交换不拦截");
			check(sm.blockBribeDeposit(menu, red1, 0, 1, net.minecraft.world.inventory.ContainerInput.SWAP),
					"数字键换位拦截（目标=走私品）", "快捷栏泥土会换进贿赂袋 → 取消");
			menu.setCarried(new net.minecraft.world.item.ItemStack(DIRT, 1));
			check(sm.blockBribeDeposit(menu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT),
					"拖拽放置走私品拦截", "QUICK_CRAFT 手持泥土一律取消");
			menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);

			// 3) 价值收纳袋（非标记容器）不受守卫影响：泥土放价值袋=走私证据，原规则允许
			var valueMenu = net.minecraft.world.inventory.ChestMenu.threeRows(2, red1.getInventory(),
					sm.valueBagFor(red1.getUUID()));
			valueMenu.setCarried(new net.minecraft.world.item.ItemStack(DIRT, 1));
			check(!sm.blockBribeDeposit(valueMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"价值收纳袋不受贿赂袋守卫影响", "泥土放价值袋=携带证据（没收风险自负），规则不变");
			valueMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十七批：走私犯结算修复——散装价值物品兑换入分 / 走私品兑换名额（每场结算 1 件 128 分，
	 * 袋与散装共享）/ 过轮走私品不计价且保留 / 双队走私品独立 / 收纳袋互存禁放。
	 */
	private static void testBatch67Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			sm.setup(srv);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}

			// 1) 价值袋批次感知：本批=泥土（走私品）+ 金锭（8 分）+ 铁锭（4 分）+ 钻石（8 分）
			var GOLD = net.minecraft.world.item.Items.GOLD_INGOT;
			var IRON = net.minecraft.world.item.Items.IRON_INGOT;
			var DIAMOND = net.minecraft.world.item.Items.DIAMOND;
			var DIRT = net.minecraft.world.item.Items.DIRT;
			var COBBLE = net.minecraft.world.item.Items.COBBLESTONE;
			String key = sm.groupKeyOf(red1);
			sm.setBatchForTest(key, new net.minecraft.world.item.Item[]{DIAMOND, GOLD, IRON});
			sm.setSmuggleItemForTest(key, DIRT); // 本批走私品 = 泥土

			// 价值袋：钻石×2 + 金锭×3 + 走私品×1 → 2*8 + 3*4 + 128 = 156 分
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(DIAMOND, 2));
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(GOLD, 3));
			sm.valueBagFor(red1.getUUID()).setItem(2, new net.minecraft.world.item.ItemStack(DIRT, 1));
			check(sm.redeemValueBagForTest(red1) == 156,
					"价值袋兑换（批次感知）", "钻石 2×8 + 金锭 3×4 + 走私品 1×128 = 156 分");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(),
					"价值袋兑换后清空", "价值袋清空");

			// 2) 散装不动：物品栏里的价值物品全程不受影响（用户规则 ①）
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(GOLD, 5));
			red1.getInventory().add(new net.minecraft.world.item.ItemStack(DIRT, 10)); // 散装走私品
			int countBefore = countItem(red1, GOLD);
			check(countItem(red1, DIRT) == 10,
					"散装价值物品：兑换前物品栏泥土×10", "散装走私品还在物品栏里");
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(IRON, 1));
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(COBBLE, 1)); // 非价值物品
			check(sm.redeemValueBagForTest(red1) == 4,
					"散装不受影响：只有价值袋计 4 分", "铁锭 4 分；圆石不计分；物品栏散装不动");
			check(countItem(red1, GOLD) == countBefore,
					"散装不受影响：物品栏金锭保留", "redeemValueBag 不碰物品栏");
			check(countItem(red1, DIRT) == 10,
					"散装不受影响：物品栏泥土×10 保留", "散装走私品不计入 hasSmuggleGood 也不计入兑换");
			// hasSmuggleGood 只看价值袋
			check(!sm.hasSmuggleGood(red1),
					"hasSmuggleGood 只看价值袋", "价值袋已清，物品栏里 10 个泥土不算走私证据");

			// 3) 走私品批次上限：同一玩家本批两次结算，走私品只计一次（128 分）
			// 第一次结算已经把本批走私品名额用了（redeemed set 有 red1）
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(DIRT, 3));
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(IRON, 1));
			check(sm.redeemValueBagForTest(red1) == 4,
					"走私品批次上限：第二次 0 分", "泥土 3×128 但名额已用 → 0 分；铁锭 4 分 = 4");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(),
					"走私品批次上限：第二次价值袋仍清空", "袋子语义：不管计不计分都清");

			// 4) 批次更换后，上批货物 0 分（用户规则 ⑤）
			red1.getInventory().clearContent();
			sm.valueBagFor(red1.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(GOLD, 10)); // 上批公开商品
			sm.valueBagFor(red1.getUUID()).setItem(1, new net.minecraft.world.item.ItemStack(DIRT, 20)); // 上批走私品
			// 换批：走私品改成圆石，公开商品换成新的三件
			sm.setBatchForTest(key, new net.minecraft.world.item.Item[]{COBBLE, COBBLE, COBBLE}); // 新公开商品（实际分值静态 1，只用来换 key）
			sm.setSmuggleItemForTest(key, COBBLE);
			check(sm.redeemValueBagForTest(red1) == 0,
					"批次更换后旧货物 0 分", "上批金锭（8 分/个）+ 泥土（128 分/个）全变 0 分");
			check(sm.valueBagFor(red1.getUUID()).isEmpty(),
					"批次更换后旧货物：价值袋仍清空", "袋子语义：不管计不计分都清");

			// 4) 双队走私品独立：RED 先定泥土 → BLUE 重掷必避开泥土
			sm.setSmuggleItemForTest("RED", DIRT);
			sm.refreshBatchForTest("BLUE");
			check(sm.smuggleItemForTest("BLUE") != DIRT,
					"双队走私品独立随机（互不重复）", "BLUE 重掷后 ≠ RED 的泥土");

			// 5) 收纳袋互存禁放：任何收纳袋道具不能放进任一收纳袋界面（直测守卫）
			var valueContainer = (com.survivalcontract.smuggle.SmuggleManager.ValueBagContainer)
					sm.valueBagFor(red1.getUUID());
			var bagMenu = net.minecraft.world.inventory.ChestMenu.threeRows(3, red1.getInventory(), valueContainer);
			var redBundle = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "red_bundle"));
			bagMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE, 1));
			check(sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"收纳袋互存禁放：棕袋点袋格拦截", "PICKUP 放置被取消（点击无反馈）");
			bagMenu.setCarried(new net.minecraft.world.item.ItemStack(redBundle, 1));
			check(sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"收纳袋互存禁放：红袋同样拦截", "红袋也是收纳袋道具");
			bagMenu.setCarried(new net.minecraft.world.item.ItemStack(IRON, 1));
			check(!sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"收纳袋界面非收纳袋道具放行", "手持铁锭点袋格不拦截");
			bagMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
			check(!sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP),
					"收纳袋界面拿取放行", "空手 PICKUP 不拦截");
			red1.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.BUNDLE, 1)); // 菜单槽 27 ↔ 物品栏 9
			check(sm.blockBagKeyDeposit(bagMenu, red1, 27, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE),
					"收纳袋互存禁放：背包 shift 存入拦截", "shift 点背包里的收纳袋 = 存入被取消");
			red1.getInventory().setItem(9, net.minecraft.world.item.ItemStack.EMPTY);
			red1.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.BUNDLE, 1));
			check(sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.SWAP),
					"收纳袋互存禁放：数字键换位拦截", "快捷栏收纳袋会换进袋界面 → 取消");
			red1.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(IRON, 1));
			check(!sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.SWAP),
					"数字键换位放行（目标=普通物品）", "快捷栏铁锭与袋格交换不拦截");
			bagMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE, 1));
			check(sm.blockBagKeyDeposit(bagMenu, red1, 0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT),
					"收纳袋互存禁放：拖拽放置拦截", "QUICK_CRAFT 手持收纳袋一律取消");
			bagMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第六十八批：文明产出修复——任意放置方块都登记为设施（前置无需挨着/无先后顺序），
	 * 石头区端到端：无橡木木头不产出 → 放置后产出圆石；木头区产出橡木原木。
	 */
	private static void testBatch68Features() {
		var gsm = GameStateManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		ServerLevel level = srv.overworld();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var center = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());

			// 石头区端到端：前置=任意 *_wood 六面皮木块（橡木木头），放置在区块内任意位置即登记生效
			net.minecraft.world.level.ChunkPos stoneChunk = null;
			for (int cx = -7; cx <= 7 && stoneChunk == null; cx++) {
				for (int cz = -7; cz <= 7; cz++) {
					var zz = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
					if (zz != null && zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE) {
						stoneChunk = new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz);
						break;
					}
				}
			}
			check(stoneChunk != null, "石头区端到端：区块存在", stoneChunk == null ? "无" : "OK");
			if (stoneChunk != null) {
				var zone = cv.zoneAt(stoneChunk);
				var savedOwner = zone.owner; // 精确还原（防跨局世界残留）
				zone.owner = GameStateManager.Team.RED;
				var chestPos = new net.minecraft.core.BlockPos(stoneChunk.getMinBlockX() + 8,
						level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
								stoneChunk.getMinBlockX() + 8, stoneChunk.getMinBlockZ() + 8) + 1,
						stoneChunk.getMinBlockZ() + 8);
				var woodPos = chestPos.offset(3, 0, 3); // 与箱子不挨着（前置无需相邻）
				var savedChest = level.getBlockState(chestPos);   // 保存原方块（可能是羊毛/玻璃基座）
				var savedWood = level.getBlockState(woodPos);
				level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
				cv.registerFacility(chestPos);
				cv.produceZoneForTest(level, stoneChunk);
				int stone0 = countInContainer(level, chestPos, net.minecraft.world.item.Items.COBBLESTONE);
				check(stone0 == 0, "石头区无橡木木头不产出", "未放置前置 → 圆石 " + stone0 + " 个");
				level.setBlock(woodPos, net.minecraft.world.level.block.Blocks.OAK_WOOD.defaultBlockState(), 3);
				cv.registerFacility(woodPos); // 第六十八批：任意放置方块都登记为设施
				cv.produceZoneForTest(level, stoneChunk);
				int stone1 = countInContainer(level, chestPos, net.minecraft.world.item.Items.COBBLESTONE);
				check(stone1 == 8, "放置橡木木头后产出圆石×8（无需挨着）",
						"前置登记后圆石 " + stone1 + " 个（应 8）");
				level.setBlock(woodPos, savedWood, 3); // 精确还原原方块状态
				level.setBlock(chestPos, savedChest, 3);
				zone.owner = savedOwner;
			}

			// 木头区端到端：主产物橡木原木×6（无前置；苹果/蔬果副产品概率性，不计入断言）
			net.minecraft.world.level.ChunkPos woodChunk = null;
			for (int cx = -7; cx <= 7 && woodChunk == null; cx++) {
				for (int cz = -7; cz <= 7; cz++) {
					var zz = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
					if (zz != null && zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD) {
						woodChunk = new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz);
						break;
					}
				}
			}
			check(woodChunk != null, "木头区端到端：区块存在", woodChunk == null ? "无" : "OK");
			if (woodChunk != null) {
				var zone = cv.zoneAt(woodChunk);
				var savedOwner = zone.owner; // 精确还原（防跨局世界残留）
				zone.owner = GameStateManager.Team.RED;
				var chestPos = new net.minecraft.core.BlockPos(woodChunk.getMinBlockX() + 8,
						level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
								woodChunk.getMinBlockX() + 8, woodChunk.getMinBlockZ() + 8) + 1,
						woodChunk.getMinBlockZ() + 8);
				var savedChest = level.getBlockState(chestPos); // 保存原方块（可能是羊毛）
				level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
				cv.registerFacility(chestPos);
				cv.produceZoneForTest(level, woodChunk);
				int logs = countInContainer(level, chestPos, net.minecraft.world.item.Items.OAK_LOG);
				check(logs == 8, "木头区产出橡木原木×8", "oak_log " + logs + " 个（应 8，不再产出橡木木块）");
				level.setBlock(chestPos, savedChest, 3); // 精确还原原方块状态
				zone.owner = savedOwner;
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.setup(srv); // 还原默认产出配置与疆域分配（清空设施登记）
		}
	}

	/** 统计方块实体容器内某物品总数（第六十八批产出实测用） */
	private static int countInContainer(ServerLevel level, net.minecraft.core.BlockPos pos,
			net.minecraft.world.item.Item item) {
		if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
			int n = 0;
			for (int i = 0; i < c.getContainerSize(); i++) {
				var s = c.getItem(i);
				if (s.is(item)) n += s.getCount();
			}
			return n;
		}
		return 0;
	}

	/**
	 * 第六十九批：文明永昼（ADVANCE_TIME=false + time set day，cleanup 恢复）/
	 * 猪灵回收计分修复（9 圆石 → 提交者全额 +9，原全队平分摊薄为 2~3 分）/
	 * 基地商人默认商品（1 附魔之瓶 30 积分固定价，不再售卖附魔书）。
	 * 神的谎言指认 -200 与分数显示钳制见 testGodsLies / testGodsLiesRelic。
	 */
	private static void testBatch69Features() {
		var gsm = GameStateManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		ServerLevel level = srv.overworld();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 0);
			cv.setup(srv);

			// 1) 永昼：setup 关闭昼夜推进（26.2 的 doDaylightCycle = advance_time 规则）
			check(!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME),
					"文明永昼：关闭昼夜推进", "ADVANCE_TIME=false（时间停在白天不推进）");

			// 2) 猪灵回收计分：9 圆石（1 分/个）→ 全额记给开启回收箱的提交者（red1），队友不得分
			//    （分队随机，不按变量名假设队伍——其余假人逐一比对分数不变）
			var box = cv.recycleBoxForTest(GameStateManager.Team.RED);
			box.clearContent();
			int before1 = gsm.getScore(red1.getUUID());
			var beforeOthers = new java.util.HashMap<UUID, Integer>();
			for (ServerPlayer p : new ServerPlayer[]{red2, blue1}) {
				beforeOthers.put(p.getUUID(), gsm.getScore(p.getUUID()));
			}
			cv.setRecycleOpenerForTest(GameStateManager.Team.RED, red1.getUUID());
			box.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 9));
			boolean othersUnchanged = true;
			for (var e : beforeOthers.entrySet()) {
				if (gsm.getScore(e.getKey()) != e.getValue()) othersUnchanged = false;
			}
			check(gsm.getScore(red1.getUUID()) == before1 + 9 && othersUnchanged,
					"回收计分：提交者全额得分", "9 圆石 → 提交者 +" + (gsm.getScore(red1.getUUID()) - before1)
							+ " 分（应 9，原全队平分会摊薄为 2~3 分），其余玩家分数不变");

			// 3) 基地商人：默认商品含 1 附魔之瓶（30 积分固定价），不再售卖附魔书
			boolean hasBottle = false, hasBook = false;
			for (var o : cv.merchantOffersForTest(GameStateManager.Team.RED)) {
				if (o.product().is(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE)) {
					hasBottle = o.cost() == 30 && o.product().getCount() == 1;
				}
				if (o.product().is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) hasBook = true;
			}
			check(hasBottle && !hasBook, "基地商人：附魔之瓶 30 分（无附魔书）",
					"默认商品 1 附魔之瓶 cost=30（固定价不涨价）" + (hasBook ? "，但仍售附魔书！" : "，不再售卖附魔书"));

			// 4) 永昼还原：cleanup（reset）恢复进入文明前的昼夜推进
			cv.reset();
			check(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME),
					"文明永昼：cleanup 恢复昼夜推进", "reset 后 ADVANCE_TIME 恢复为进入前状态（true）");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
		}
	}

	/**
	 * 第七十批：神的谎言次数上限——平民指认权 / 使者降罪权同一时刻最多持有 1 次（不累积，
	 * 用掉后完成任务可再次获得）。直接调用底层方法，结束后还原模式与阶段。
	 */
	private static void testBatch70Features() {
		var gsm = GameStateManager.getInstance();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
			gsm.resetGodsLies();
			gsm.setEnvoyCount(1);
			gsm.setupGodsLiesEnvoys(server.get());
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 100);
			ServerPlayer env = null, civA = null;
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				if (gsm.isGodEnvoy(p.getUUID())) env = p;
				else if (civA == null) civA = p;
			}
			check(env != null && civA != null, "局准备：1 使者 + 2 平民",
					env != null && civA != null ? "任命完成" : "任命失败");

			// 使者降罪权上限 1：连续两次任务奖励不累积；降罪消耗后可再次获得
			gsm.grantGodsLiesTaskReward(env);
			gsm.grantGodsLiesTaskReward(env);
			check(gsm.getDeductCharges(env.getUUID()) == 1, "降罪权上限 1：不累积",
					"连续两次任务奖励 → 降罪权 " + gsm.getDeductCharges(env.getUUID()) + "（应 1）");
			gsm.envoyDeduct(env.getUUID(), civA.getUUID());
			check(gsm.getDeductCharges(env.getUUID()) == 0 && gsm.getScore(civA.getUUID()) == 45
							&& !gsm.isEliminated(civA.getUUID()),
					"降罪权消耗", "指定平民扣 55 分（100 → 45 不淘汰），降罪权归 0");
			gsm.grantGodsLiesTaskReward(env);
			check(gsm.getDeductCharges(env.getUUID()) == 1, "降罪权：用掉后可再次获得",
					"消耗后完成任务 → 降罪权恢复 1");

			// 平民指认权上限 1：连续两次任务奖励不累积；指认（命中）消耗后归 0
			gsm.grantGodsLiesTaskReward(civA);
			gsm.grantGodsLiesTaskReward(civA);
			check(gsm.getAccuseCharges(civA.getUUID()) == 1, "指认权上限 1：不累积",
					"连续两次任务奖励 → 指认权 " + gsm.getAccuseCharges(civA.getUUID()) + "（应 1）");
			var res = gsm.resolveAccusation(civA.getUUID(), env.getUUID());
			check(res == GameStateManager.AccuseResult.HIT && gsm.getAccuseCharges(civA.getUUID()) == 0
							&& gsm.isEliminated(env.getUUID()) && gsm.getScore(env.getUUID()) == 0
							&& "envoys_eliminated".equals(gsm.godsLiesEndReason())
							&& gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED,
					"指认权消耗与结算", "指认使者命中 → 对方 -200 淘汰（显示 0 分），指认权归 0 → 平民获胜");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(savedPhase);
			// 第七十批修复：case4 链在本测试后立即 startGame——此前各测试给三假人启用的挑战规则若不清空，
			// 会被 calculateInitialScore 计入开局初始分（每人残留不同 → 「同队初始分一致」断言随机失败，
			// 属固有测试泄漏而非本批回归）。清空后 startGame 按「干净大厅」计分：0 规则分 + 无职业 +10。
			var cmCleanup = ChallengeManager.getInstance();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) cmCleanup.clearPlayerChallenges(p.getUUID());
		}
	}

	/**
	 * 第九十批回归：
	 * ①受贿结算不再漏计——结算前再倒一次收纳袋内嵌内容 + 计入菜单光标上的物品，并关闭界面；
	 * ②走私犯与检察官达标胜利：率先达到「参战人数 × 300」分的个人/队伍直接获胜。
	 */
	private static void testBatch90Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		var savedMode = gsm.getGameMode();
		var savedStart = gsm.getGameStartTime();
		var savedTeams = gsm.getAllTeams();
		var DIAMOND = net.minecraft.world.item.Items.DIAMOND;
		var GOLD = net.minecraft.world.item.Items.GOLD_INGOT;
		var EMERALD = net.minecraft.world.item.Items.EMERALD;
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setGameMode(GameStateManager.GameMode.SOLO);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
			sm.setup(srv);

			// ---- ① 受贿结算：袋内 2 钻石（16）+ 光标 2 金锭（8）+ 袋道具内嵌 1 绿宝石（8）= 32 ----
			red1.getInventory().clearContent();
			var bribe90 = sm.bribeBagFor(red1.getUUID());
			bribe90.clearContent();
			bribe90.setItem(0, new net.minecraft.world.item.ItemStack(DIAMOND, 2));
			sm.openBag(red1, true); // 打开贿赂袋界面（结算时光标物品的漏计路径）
			if (red1.containerMenu instanceof net.minecraft.world.inventory.ChestMenu cm90) {
				cm90.setCarried(new net.minecraft.world.item.ItemStack(GOLD, 2));
			}
			// 打开界面后再往袋道具里塞物品（结算前才 spill 的漏计路径）
			var stuffed90 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE);
			stuffed90.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal("§c贿赂收纳袋"));
			var mut90 = com.survivalcontract.McCompat.newMutableBundle(
					net.minecraft.world.item.component.BundleContents.EMPTY);
			mut90.tryInsert(new net.minecraft.world.item.ItemStack(EMERALD, 1));
			stuffed90.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS, mut90.toImmutable());
			red1.getInventory().add(stuffed90);

			sm.clearProsecuteLimitsForTest();
			boolean initiated90 = sm.initiate(red1, red2);
			sm.decide(srv, red2.getUUID(), red1.getUUID(),
					com.survivalcontract.smuggle.SmuggleManager.ACTION_ACCEPT_BRIBE);
			check(initiated90 && gsm.getScore(red2.getUUID()) == 32 && bribe90.isEmpty()
							&& !(red1.containerMenu instanceof net.minecraft.world.inventory.ChestMenu)
							&& countItem(red1, GOLD) == 0 && countItem(red1, EMERALD) == 0,
					"受贿结算不漏计：内嵌物品 + 光标物品全部计入并清空",
					"检察官 +32（袋内钻石16 + 内嵌绿宝石8 + 光标金锭8），贿赂袋清空、界面关闭、光标物品收走");

			// ---- ② 达标胜利（个人）：3 人参赛 → 900 分，899 不结束、900 直接获胜 ----
			gsm.setForcedWinners(java.util.List.of());
			gsm.resetScores();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 0);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			gsm.addScore(red1.getUUID(), 899);
			boolean soloNotYet = gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING;
			gsm.addScore(red1.getUUID(), 1);
			check(soloNotYet && gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED
							&& gsm.getWinnersByScore().equals(java.util.List.of(red1.getUUID())),
					"达标胜利（个人）：3 人参赛 = 900 分",
					"899 分仍在进行；900 分立即结束，唯一胜者 = red1");

			// ---- ② 达标胜利（团队）：红队合计 900 分（500+400）→ 全队获胜 ----
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			gsm.setForcedWinners(java.util.List.of());
			gsm.setGameMode(GameStateManager.GameMode.TEAMS);
			gsm.resetPlayerTeams();
			gsm.setPlayerTeam(red1.getUUID(), GameStateManager.Team.RED);
			gsm.setPlayerTeam(red2.getUUID(), GameStateManager.Team.RED);
			gsm.setPlayerTeam(blue1.getUUID(), GameStateManager.Team.BLUE);
			gsm.resetScores();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 0);
			gsm.addScore(red1.getUUID(), 500);
			gsm.addScore(red2.getUUID(), 399);
			boolean teamNotYet = gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING;
			gsm.addScore(red2.getUUID(), 1);
			var teamWinners = gsm.getWinnersByScore();
			check(teamNotYet && gsm.getCurrentPhase() == GameStateManager.GamePhase.FINISHED
							&& teamWinners.size() == 2 && teamWinners.contains(red1.getUUID())
							&& teamWinners.contains(red2.getUUID()),
					"达标胜利（团队）：红队合计 900 分",
					"899 分仍在进行；900 分立即结束，红队两人同时获胜（蓝队不出现在胜者列表）");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			gsm.setGameMode(savedMode);
			gsm.setGameStartTime(savedStart);
			gsm.resetPlayerTeams();
			savedTeams.forEach(gsm::setPlayerTeam);
			gsm.setForcedWinners(java.util.List.of());
			sm.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第七十一批回归：
	 * ①神的谎言结算/大厅阶段的加减分不得淘汰玩家或改旁观者（修复"结束扣分污染下一局"根因）；
	 * ②袋内多个违禁品（整组塞入）清空后只按 1 个计 128 分（不再按数量翻倍）；
	 * ③文明僵尸马（非守护，如事件骑兵）纳入火焰抑制登记：登记后每 tick 扑灭（白天不自燃）。
	 */
	private static void testBatch71Features() {
		var gsm = GameStateManager.getInstance();
		var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			// ---- ① 神的谎言：非 PLAYING 阶段的分数变动不淘汰（修复跨局污染） ----
			gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
			red1.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			gsm.setScore(red1.getUUID(), 0);
			gsm.addScore(red1.getUUID(), 0); // 大厅/结算阶段的分数变动（如大厅关规则扣分）
			check(!gsm.isEliminated(red1.getUUID())
							&& red1.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SPECTATOR,
					"非 PLAYING 阶段加减分不淘汰玩家", "大厅 0 分不触发淘汰、不改旁观者（避免污染下一局）");

			// ---- ② 袋内多个违禁品只按 1 个计 128 分 ----
			gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			sm.setup(srv);
			red2.getInventory().clearContent();
			gsm.setScore(red2.getUUID(), 0);
			String key2 = sm.groupKeyOf(red2);
			sm.setBatchForTest(key2, new net.minecraft.world.item.Item[]{
					net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.GOLD_INGOT,
					net.minecraft.world.item.Items.IRON_INGOT});
			sm.setSmuggleItemForTest(key2, net.minecraft.world.item.Items.DIRT);
			sm.valueBagFor(red2.getUUID()).clearContent();
			sm.valueBagFor(red2.getUUID()).setItem(0, new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIRT, 64));
			check(sm.redeemValueBagForTest(red2) == com.survivalcontract.smuggle.SmuggleManager.SMUGGLE_VALUE,
					"袋内多个违禁品只按 1 个计分",
					"整组泥土×64 塞入 → 清空后只计 1 个×128 = 128 分（不再按 64 计翻倍）");
			check(sm.valueBagFor(red2.getUUID()).isEmpty(), "违禁品整组清空", "袋子语义：不管计多少都清");

			// ---- ③ 文明僵尸马（非守护）火焰抑制登记 ----
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var civLevel = ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();
			var center71 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var horse = net.minecraft.world.entity.EntityTypes.ZOMBIE_HORSE.create(civLevel,
					net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (horse != null) {
				horse.setPos(center71.getMinBlockX() + 0.5, gsm.getPlayingSpawn().getY(),
						center71.getMinBlockZ() + 0.5);
				civLevel.addFreshEntity(horse);
				cv.trackCivZombieHorseForTest(horse);
				horse.setRemainingFireTicks(60);
				check(horse.isOnFire(), "前置：文明僵尸马已点燃", "fireTicks=" + horse.getRemainingFireTicks());
				cv.sweepGuardianFireForTest(civLevel);
				check(!horse.isOnFire(), "文明僵尸马白天不自燃（每 tick 扑灭）",
						"fireTicks=" + horse.getRemainingFireTicks());
				horse.discard();
			} else {
				check(false, "文明僵尸马生成", "EntityTypes.ZOMBIE_HORSE.create 返回 null");
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(savedPhase);
			sm.reset();
			cv.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				p.getInventory().clearContent();
				gsm.setScore(p.getUUID(), 0);
			}
		}
	}

	/**
	 * 第七十四批：附属玩法（Addon play style）宿主扩展点。
	 * ①自定义入口点注册表（注册/按 id 查找/去重）；
	 * ②PlayStyle.ADDON 分支：getPlayStyleKey 返回 addon id、isGodsLies/isStowaway/isCivilization 恒 false、getAddonProvider 命中；
	 * ③整数选项同步与范围夹取（1~6 默认 3）与循环切换；
	 * ④抑制生效：附属玩法下不发放专属任务；
	 * ⑤FixedMapInjector 复用：大厅地图仍能正常注入（三级优先保留）。
	 */
	private static int addonTestTickCount = 0;

	private static void testBatch74Features() {
		var gsm = GameStateManager.getInstance();
		var savedStyle = gsm.getPlayStyle();
		var savedPhase = gsm.getCurrentPhase();
		final String addonId = "selftest-style";
		// 观战分流委派用例：附属玩法是否被宿主征询、是否接管观战落点
		final java.util.concurrent.atomic.AtomicInteger specJoinCalls = new java.util.concurrent.atomic.AtomicInteger();
		final boolean[] specTakeOver = {true};
		try {
			// ---- ① 注册表 ----
			com.survivalcontract.api.ScPlayStyleProvider provider = new com.survivalcontract.api.ScPlayStyleProvider() {
				@Override public String id() { return addonId; }
				@Override public String displayNameKey() { return "selftest.style.name"; }
				@Override public String displayNameFallback() { return "自测玩法"; }
				@Override public boolean forceTeamMode() { return true; }
				@Override public com.survivalcontract.api.StyleUi ui() {
					return new com.survivalcontract.api.StyleUi(true, true, true, true, true);
				}
				@Override public java.util.List<com.survivalcontract.api.StyleOption> options() {
					return java.util.List.of(new com.survivalcontract.api.StyleOption(
							"groupsToWin", "selftest.opt.groups", "需要完成的套餐组数：%s", 1, 6, 3, 1));
				}
				@Override public void onServerTick(net.minecraft.server.MinecraftServer s) { addonTestTickCount++; }
				@Override public boolean onSpectatorJoin(net.minecraft.server.MinecraftServer s,
						net.minecraft.server.level.ServerPlayer p) {
					specJoinCalls.incrementAndGet();
					return specTakeOver[0];
				}
			};
			com.survivalcontract.api.PlayStyleRegistry.register(provider);
			check(com.survivalcontract.api.PlayStyleRegistry.byId(addonId) == provider,
					"附属玩法注册表", "register 后 byId 命中同一实例");
			com.survivalcontract.api.PlayStyleRegistry.register(provider);
			check(com.survivalcontract.api.PlayStyleRegistry.all().size() == 1,
					"附属玩法注册去重", "重复注册同一 id 被忽略（size=1）");

			// ---- ② ADDON 分支 ----
			gsm.selectAddonStyle(addonId);
			check(gsm.isAddon() && addonId.equals(gsm.getPlayStyleKey()),
					"附属玩法选中态", "isAddon=true, getPlayStyleKey=" + gsm.getPlayStyleKey());
			check(!gsm.isGodsLies() && !gsm.isStowaway() && !gsm.isCivilization(),
					"附属玩法不落入内置玩法分支", "isGodsLies/isStowaway/isCivilization 恒 false");
			check(gsm.getAddonProvider() == provider && com.survivalcontract.api.PlayStyleRegistry.selected() == provider,
					"附属玩法 provider 解析", "getAddonProvider / registry.selected 命中注册项");
			check(provider.ui().suppressRuleCards() && provider.ui().lockProfession()
							&& provider.ui().disableTeamChest() && provider.ui().noFinalGoal()
							&& provider.ui().noTasks() && provider.forceTeamMode(),
					"附属玩法抑制开关", "规则卡隐藏 + 职业锁定 + 团队箱关闭 + 无最终目标/任务 + 仅团队模式");

			// ---- ③ 选项同步 ----
			gsm.clearStyleOptions();
			for (var o : provider.options()) gsm.setStyleOption(o.key(), o.defaultValue());
			check(gsm.getStyleOption("groupsToWin", -1) == 3, "选项默认值 1~6 默认 3", "groupsToWin=3");
			var opt = provider.options().get(0);
			check(opt.clamp(9) == 6 && opt.clamp(0) == 1 && opt.next(6) == 1 && opt.next(3) == 4,
					"选项范围夹取与循环", "clamp(9)=6, clamp(0)=1, next(6)=1, next(3)=4");

			// ---- ③b 选项取值名（附属玩法据此渲染「地图类型：街角」这类文案） ----
			var named = new com.survivalcontract.api.StyleOption("mapType", "selftest.opt.map", "地图类型：%s",
					0, 1, 0, 1, java.util.List.of("selftest.map.street", "selftest.map.aquarium"));
			check("selftest.map.street".equals(named.valueLabelKey(0))
							&& "selftest.map.aquarium".equals(named.valueLabelKey(1))
							&& named.next(1) == 0 && named.clamp(7) == 1,
					"选项取值名映射", "valueLabelKey(0/1) 命中，next(1)=0 回落，clamp(7)=1");
			check(opt.valueLabels().isEmpty() && opt.valueLabelKey(3) == null,
					"无取值名时回退数字显示", "7 参构造 valueLabels 为空且 valueLabelKey 返回 null");

			// ---- ④ 抑制生效：附属玩法不发放专属任务 ----
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			var tm = TaskManager.getInstance();
			tm.resetAll();
			tm.issueNewTask(red1);
			check(tm.getActiveTask(red1.getUUID()) == null,
					"附属玩法：不发放专属任务", "issueNewTask 对 addon 直接返回（无任务）");

			// ---- ⑤ 钩子契约：onServerTick 可被调用（宿主在 END_SERVER_TICK 转发） ----
			int before = addonTestTickCount;
			provider.onServerTick(server.get());
			check(addonTestTickCount == before + 1, "附属玩法 tick 钩子契约", "onServerTick 调用生效");

			// ---- ⑥ FixedMapInjector 复用：大厅地图注入仍正常 ----
			com.survivalcontract.dimension.LobbyDimensionSetup.injectRegionFiles(server.get());
			java.nio.file.Path lobbyDir = server.get()
					.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
					.resolve("dimensions/survival-contract/lobby".replace("/", java.io.File.separator));
			boolean regionOk = java.nio.file.Files.isDirectory(lobbyDir.resolve("region"))
					&& hasMca(lobbyDir.resolve("region"));
			check(regionOk, "FixedMapInjector 复用（大厅地图注入）",
					regionOk ? "region/*.mca 注入到位" : "region 目录或 .mca 缺失: " + lobbyDir);

			// ---- ⑦ 观战分流委派：中途加入的玩家由附属玩法接管观战落点（不再送竞技场） ----
			// 根因：附属玩法没有竞技场，宿主默认落点会把人送到 ArenaWorldManager.getArenaOverworld
			// （≈普通主世界），与内置玩法"进本游戏地图观战"不一致 → 新增 onSpectatorJoin 钩子委派。
			var specA = spawnFake(server.get(), server.get().overworld(), "FakeSpec74A");
			var specB = spawnFake(server.get(), server.get().overworld(), "FakeSpec74B");
			try {
				check(!gsm.hasPlayerState(specA.getUUID()) && !gsm.hasPlayerState(specB.getUUID()),
						"观战委派前置：新加入者非参战者", "两名假人均不在分数表");

				// 附属玩法接管（返回 true）→ 宿主不再执行默认的"送竞技场 + 切旁观者"
				specTakeOver[0] = true;
				int callsBefore = specJoinCalls.get();
				com.survivalcontract.MainEventListener.teleportSpectatorToArena(server.get(), specA);
				check(specJoinCalls.get() == callsBefore + 1 && !specA.isSpectator(),
						"观战分流委派：附属玩法接管落点",
						"onSpectatorJoin 被调用，宿主默认落点（竞技场 + 旁观者）不再执行");

				// 附属玩法拒绝接管（返回 false）→ 回退宿主默认：旁观者 + 竞技场观战
				specTakeOver[0] = false;
				com.survivalcontract.MainEventListener.teleportSpectatorToArena(server.get(), specB);
				check(specB.isSpectator(), "观战分流回退：拒绝接管时走宿主默认",
						"onSpectatorJoin=false → 玩家被置为旁观者");
			} finally {
				try { server.get().getPlayerList().remove(specA); } catch (Exception ignored) {}
				try { server.get().getPlayerList().remove(specB); } catch (Exception ignored) {}
			}
		} catch (Throwable t) {
			check(false, "附属玩法扩展点用例", "异常: " + t);
		} finally {
			com.survivalcontract.api.PlayStyleRegistry.clear();
			gsm.clearStyleOptions();
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			TaskManager.getInstance().resetAll();
		}
	}

	/** 目录内是否存在至少一个 .mca 文件 */
	private static boolean hasMca(java.nio.file.Path dir) {
		try (var s = java.nio.file.Files.list(dir)) {
			return s.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".mca"));
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 第七十五批：管理员在对局进行中切换玩法。
	 * ①阶段门控：大厅任何人可切；对局中按管理员权限判定（控制台/命令源由指令 .requires 保证）；结算阶段不可切；
	 * ②中局切到附属玩法 → 立即调用 onGameStart（参与者 = 在线非旁观者）+ 无最终目标；
	 * ③非法切换被拒绝时**不会**清理当前附属玩法（先把校验做在前面）；
	 * ④切离附属玩法 → 调用 onGameEnd 清理运行时状态。
	 */
	private static int addonSwitchStartCalls = 0;
	private static int addonSwitchEndCalls = 0;
	private static int addonSwitchParticipants = 0;

	private static void testBatch75Features() {
		var gsm = GameStateManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		var savedMode = gsm.getGameMode();
		var savedGoal = gsm.getCurrentFinalGoal();
		var savedOptions = gsm.getStyleOptions();
		var savedProfs = new java.util.HashMap<java.util.UUID,
				com.survivalcontract.profession.ProfessionManager.Profession>();
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
			savedProfs.put(p.getUUID(),
					com.survivalcontract.profession.ProfessionManager.getInstance().getPlayerProfession(p.getUUID()));
		}
		final String id = "selftest-switch";
		try {
			com.survivalcontract.api.PlayStyleRegistry.register(new com.survivalcontract.api.ScPlayStyleProvider() {
				@Override public String id() { return id; }
				@Override public String displayNameKey() { return "selftest.switch.name"; }
				@Override public String displayNameFallback() { return "切服玩法"; }
				@Override public boolean forceTeamMode() { return true; }
				@Override public void onGameStart(net.minecraft.server.MinecraftServer s,
				                                   java.util.List<ServerPlayer> players) {
					addonSwitchStartCalls++;
					addonSwitchParticipants = players.size();
				}
				@Override public void onGameEnd(net.minecraft.server.MinecraftServer s) { addonSwitchEndCalls++; }
			});

			// ---- ① 阶段门控 ----
			gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
			check(com.survivalcontract.network.NetworkRegistry.isPlayStyleSwitchPhase()
							&& com.survivalcontract.network.NetworkRegistry.canSwitchPlayStyle(red1),
					"大厅阶段任何人可切换玩法", "isPlayStyleSwitchPhase=true, canSwitch(假人)=true");
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			check(com.survivalcontract.network.NetworkRegistry.canSwitchPlayStyle(null),
					"对局中控制台/命令源可切换玩法", "canSwitch(null)=true（权限由指令 .requires 校验）");
			boolean perm = red1.permissions()
					.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
			check(com.survivalcontract.network.NetworkRegistry.canSwitchPlayStyle(red1) == perm,
					"对局中切换玩法按管理员权限判定",
					"hasPermission=" + perm + " → canSwitch=" + com.survivalcontract.network.NetworkRegistry
							.canSwitchPlayStyle(red1));
			gsm.setCurrentPhase(GameStateManager.GamePhase.FINISHED);
			check(!com.survivalcontract.network.NetworkRegistry.isPlayStyleSwitchPhase(),
					"结算阶段不可切换玩法", "isPlayStyleSwitchPhase=false");

			// ---- ② 中局切到附属玩法 ----
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			gsm.setGameMode(GameStateManager.GameMode.TEAMS);
			gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			boolean ok = com.survivalcontract.network.NetworkRegistry.applyPlayStyle(srv, null, id);
			check(ok && gsm.isAddon() && id.equals(gsm.getPlayStyleKey()),
					"对局中切到附属玩法", "isAddon=true, key=" + gsm.getPlayStyleKey());
			int expect = 0;
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (!p.isSpectator()) expect++;
			check(addonSwitchStartCalls == 1 && addonSwitchParticipants == expect,
					"中局切换立即初始化附属玩法",
					"onGameStart × " + addonSwitchStartCalls + "，参与者 " + addonSwitchParticipants + "（应 " + expect + "）");
			check(gsm.getCurrentFinalGoal() == null, "中局切到附属玩法后无最终目标",
					"finalGoal=" + gsm.getCurrentFinalGoal());

			// ---- ③ 非法切换被拒绝 → 不清理当前附属玩法 ----
			boolean rejected = com.survivalcontract.network.NetworkRegistry.applyPlayStyle(srv, null, "GODS_LIES");
			check(!rejected && gsm.isAddon() && addonSwitchEndCalls == 0,
					"非法切换被拒绝且不清清理旧玩法",
					"返回 false，仍是附属玩法，onGameEnd 未被调用（" + addonSwitchEndCalls + "）");

			// ---- ④ 切离附属玩法 → onGameEnd ----
			gsm.setGameMode(GameStateManager.GameMode.SOLO);
			boolean back = com.survivalcontract.network.NetworkRegistry.applyPlayStyle(srv, null, "NORMAL");
			check(back && !gsm.isAddon() && addonSwitchEndCalls == 1,
					"切离附属玩法调用 onGameEnd 清理",
					"isAddon=false, onGameEnd × " + addonSwitchEndCalls);
		} catch (Throwable t) {
			check(false, "管理员切换玩法用例", "异常: " + t);
		} finally {
			com.survivalcontract.api.PlayStyleRegistry.clear();
			gsm.setPlayStyle(savedStyle);
			gsm.setGameMode(savedMode);
			gsm.setCurrentPhase(savedPhase);
			gsm.setCurrentFinalGoal(savedGoal);
			gsm.clearStyleOptions();
			for (var e : savedOptions.entrySet()) gsm.setStyleOption(e.getKey(), e.getValue());
			for (var e : savedProfs.entrySet()) {
				com.survivalcontract.profession.ProfessionManager.getInstance()
						.setPlayerProfession(e.getKey(), e.getValue());
			}
		}
	}

	/**
	 * 第七十二批：盗贼职业。
	 * ①免疫「不能被 XX 敌对生物杀死」系列 9 条死亡规则，且不免疫其他规则；
	 * ②初始铁斧附带抢夺 3 + 经验修补；
	 * ③靠近生物右键夺取其主手/副手/四件护甲（以卫道士铁斧为代表），并进入 2 分钟冷却；
	 * ④可掉落头颅的生物（以僵尸为代表）被一击必杀且头颅入手；冷却期内再次右键不接管。
	 */
	private static void testBatch72Features() {
		var gsm = GameStateManager.getInstance();
		var pm = ProfessionManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		ServerPlayer thief = red1;
		var savedProf = pm.getPlayerProfession(thief.getUUID());
		ServerLevel level = srv.overworld();
		try {
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			pm.setPlayerProfession(thief.getUUID(), ProfessionManager.Profession.THIEF);
			pm.clearThiefCooldown();

			// ---- ① 免疫「不能被 XX 杀死」系列 ----
			String[] deathRules = {"no_killed_by_zombie", "no_killed_by_skeleton", "no_killed_by_spider",
					"no_killed_by_creeper", "no_killed_by_witch", "no_killed_by_enderman",
					"no_killed_by_blaze", "no_killed_by_drowned", "no_killed_by_magma_cube"};
			boolean allImmune = true;
			for (String r : deathRules) if (!pm.isImmuneToChallenge(thief.getUUID(), r)) allImmune = false;
			check(allImmune, "盗贼免疫全部「不能被敌对生物杀死」规则",
					"9 条 no_killed_by_* 全部豁免扣分");
			check(!pm.isImmuneToChallenge(thief.getUUID(), "no_kill_iron_golem")
							&& !pm.isImmuneToChallenge(thief.getUUID(), "no_drowning")
							&& !pm.isImmuneToChallenge(thief.getUUID(), "no_lava_damage")
							&& !pm.isImmuneToChallenge(thief.getUUID(), "no_fall_in_void"),
					"盗贼不免疫非生物击杀类规则",
					"no_kill_iron_golem / no_drowning / no_lava_damage / no_fall_in_void 仍照常扣分");

			// ---- ② 初始铁斧：抢夺 3 + 经验修补 ----
			thief.getInventory().clearContent();
			pm.giveStartingItems(thief);
			net.minecraft.world.item.ItemStack axe = net.minecraft.world.item.ItemStack.EMPTY;
			for (int i = 0; i < thief.getInventory().getContainerSize(); i++) {
				if (thief.getInventory().getItem(i).is(net.minecraft.world.item.Items.IRON_AXE)) {
					axe = thief.getInventory().getItem(i);
					break;
				}
			}
			var enchLookup = srv.registryAccess()
					.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			int looting = 0, mending = 0;
			if (!axe.isEmpty()) {
				var enc = axe.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
				if (enc != null) {
					looting = enc.getLevel(enchLookup.getOrThrow(
							net.minecraft.world.item.enchantment.Enchantments.LOOTING));
					mending = enc.getLevel(enchLookup.getOrThrow(
							net.minecraft.world.item.enchantment.Enchantments.MENDING));
				}
			}
			check(looting == 3 && mending == 1, "盗贼初始铁斧：抢夺3 + 经验修补",
					"looting=" + looting + ", mending=" + mending);

			// ---- ③ 夺取装备：卫道士的铁斧 ----
			pm.clearThiefCooldown();
			thief.getInventory().clearContent();
			var vindicator = net.minecraft.world.entity.EntityTypes.VINDICATOR.create(level,
					net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (vindicator == null) {
				check(false, "盗贼夺取装备（卫道士生成）", "EntityTypes.VINDICATOR.create 返回 null");
			} else {
				vindicator.setPos(thief.getX(), thief.getY(), thief.getZ());
				vindicator.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
				level.addFreshEntity(vindicator);
				boolean handled = pm.tryThiefSteal(thief, vindicator);
				check(handled
								&& vindicator.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty()
								&& countPlayerItem(thief, net.minecraft.world.item.Items.IRON_AXE) == 1,
						"盗贼右键夺取卫道士铁斧",
						"已夺取（铁斧进背包）+ 目标主手清空");
				long until = pm.getThiefCooldownUntilForTest(thief.getUUID());
				check(until > level.getGameTime() && until - level.getGameTime() <= ProfessionManager.THIEF_COOLDOWN_TICKS,
						"盗贼夺取后进入 2 分钟冷却",
						"冷却 " + (until - level.getGameTime()) + " tick（应 ≈2400）");
				// 冷却期内再次右键 → 不接管、不夺取
				vindicator.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
				check(!pm.tryThiefSteal(thief, vindicator)
								&& !vindicator.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(),
						"盗贼冷却期内不触发第二次夺取", "仍保留铁斧，未接管交互");
				vindicator.discard();

				// ---- ④ 一击必杀并夺取头颅：僵尸 ----
				pm.clearThiefCooldown();
				var zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level,
						net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
				if (zombie == null) {
					check(false, "盗贼头颅斩杀（僵尸生成）", "EntityTypes.ZOMBIE.create 返回 null");
				} else {
					zombie.setPos(thief.getX() + 1, thief.getY(), thief.getZ());
					level.addFreshEntity(zombie);
					boolean handled2 = pm.tryThiefSteal(thief, zombie);
					check(handled2 && !zombie.isAlive()
									&& countPlayerItem(thief, net.minecraft.world.item.Items.ZOMBIE_HEAD) == 1,
							"盗贼一击必杀僵尸并夺取头颅",
							"僵尸死亡=" + !zombie.isAlive() + "，头颅入手="
									+ (countPlayerItem(thief, net.minecraft.world.item.Items.ZOMBIE_HEAD) == 1));
					zombie.discard();
				}
			}
		} finally {
			pm.clearThiefCooldown();
			pm.setPlayerProfession(thief.getUUID(), savedProf);
			thief.getInventory().clearContent();
			gsm.setCurrentPhase(savedPhase);
		}
	}

	/** 统计玩家背包（含快捷栏）内某物品总数（自测用） */
	private static int countPlayerItem(ServerPlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			var s = p.getInventory().getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	/**
	 * 文明时代：疆域分配 / 初始占领 / 邻接规则 / 羊毛无主化 / 回收积分。
	 * 直接调用 CivilizationManager 底层方法，结束后还原模式/阶段/分数/边界。
	 */
	private static void testCivilization() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 0);

			cv.setup(srv);

			// 1.0) 第八十七批：文明时代重生点必须落在 240×240 疆域（WorldBorder）内，且为大本营中心地表——
			//      旧实现在团队模式用 ±120/±240 偏移，红蓝点正好贴在/越过边界（开局限界外/虚空问题根因）
			ServerLevel civLv0 = ArenaWorldManager.getArenaOverworld(srv);
			check(civLv0 != null, "文明时代竞技场存在（禁止退回主世界）",
					civLv0 != null ? "arena 已创建" : "arena=null（setup 会直接中止）");
			if (civLv0 != null) {
				var border0 = civLv0.getWorldBorder();
				double half0 = border0.getSize() / 2.0;
				int outside0 = 0;
				StringBuilder sb0 = new StringBuilder();
				for (ServerPlayer pp : new ServerPlayer[]{red1, red2, blue1}) {
					BlockPos rp = gsm.getRespawnPoint(pp.getUUID());
					double dx = Math.abs(rp.getX() + 0.5 - border0.getCenterX());
					double dz = Math.abs(rp.getZ() + 0.5 - border0.getCenterZ());
					boolean bad = rp.getY() <= civLv0.getMinY() + 1 || dx > half0 || dz > half0;
					if (bad) {
						outside0++;
						sb0.append(pp.getName().getString()).append("=").append(rp).append(" ");
					}
				}
				check(outside0 == 0, "文明时代重生点在疆域边界内",
						"border=" + border0.getSize() + " 越界/埋地=" + outside0 + " " + sb0);
			}

			// 1) 疆域：15×15 = 225 区块全部登记
			check(cv.zoneCount() == 15 * 15, "疆域区块分配", cv.zoneCount() + " 区块（应 225）");

			// 1.5) 文明时代超平坦主世界不生成任何结构：结构覆盖集必须为「存在且为空」的 HolderSet
			//      （原版预设默认携带 村庄+要塞 结构集；FlatLevelSource.createState 只从覆盖集取结构集，
			//       空集 → 0 个结构集 → 任何结构都不会放置）
			var presetSettings = srv.registryAccess()
					.lookupOrThrow(net.minecraft.core.registries.Registries.FLAT_LEVEL_GENERATOR_PRESET)
					.getOrThrow(net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets.OVERWORLD)
					.value().settings();
			var flatStem = ArenaWorldManager.flatOverworldStem(srv,
					srv.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM));
			if (flatStem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource flatGen) {
				var overrides = flatGen.settings().structureOverrides();
				check(overrides.isPresent() && overrides.get().stream().findAny().isEmpty(),
						"文明时代超平坦无结构",
						overrides.isPresent() ? "结构覆盖集为空（0 个结构集）" : "结构覆盖集缺失（将回退全部结构集）");
				check(flatGen.settings().getLayersInfo().equals(presetSettings.getLayersInfo()),
						"文明时代超平坦层厚保留", "层厚与原版「主世界」预设一致");
			} else {
				check(false, "文明时代超平坦无结构", "生成器不是 FlatLevelSource");
			}

			// 2) 初始占领：两队大本营 3×3 贴疆域对角（红西北 x,z-6 中心 / 蓝东南 x,z+6 中心——第四十八批）
			var center = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var redHq = new net.minecraft.world.level.ChunkPos(center.x() - 6, center.z() - 6);
			var blueHq = new net.minecraft.world.level.ChunkPos(center.x() + 6, center.z() + 6);
			var rz = cv.zoneAt(redHq);
			var bz = cv.zoneAt(blueHq);
			check(rz != null && rz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ
							&& rz.owner == GameStateManager.Team.RED,
					"红队大本营初始占领", "中心区块 HQ/RED");
			check(bz != null && bz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ
							&& bz.owner == GameStateManager.Team.BLUE,
					"蓝队大本营初始占领", "中心区块 HQ/BLUE");
			// 2.01) 第四十九批：大本营周围八区块 = 初始资源区（仅木头/石头随机，归属本队；
			//       照常放置占领羊毛，仅悬浮图标显示资源类型不显示羊毛）
			int hqNeighborZones = 0, hqNeighborWool = 0, hqNeighborWoolBlock = 0;
			var civLevel = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					var rn = cv.zoneAt(new net.minecraft.world.level.ChunkPos(redHq.x() + dx, redHq.z() + dz));
					var bn = cv.zoneAt(new net.minecraft.world.level.ChunkPos(blueHq.x() + dx, blueHq.z() + dz));
					if (rn != null && (rn.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD
							|| rn.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE)
							&& rn.owner == GameStateManager.Team.RED) hqNeighborZones++;
					if (bn != null && (bn.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD
							|| bn.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE)
							&& bn.owner == GameStateManager.Team.BLUE) hqNeighborZones++;
					var rcp = new net.minecraft.world.level.ChunkPos(redHq.x() + dx, redHq.z() + dz);
					var bcp = new net.minecraft.world.level.ChunkPos(blueHq.x() + dx, blueHq.z() + dz);
					if (rn != null && cv.chunkHasWool(rcp)) {
						hqNeighborWool++;
						if (com.survivalcontract.civilization.CivilizationManager.isTeamWool(
								civLevel.getBlockState(rn.woolPos), GameStateManager.Team.RED)) hqNeighborWoolBlock++;
					}
					if (bn != null && cv.chunkHasWool(bcp)) {
						hqNeighborWool++;
						if (com.survivalcontract.civilization.CivilizationManager.isTeamWool(
								civLevel.getBlockState(bn.woolPos), GameStateManager.Team.BLUE)) hqNeighborWoolBlock++;
					}
				}
			}
			check(hqNeighborZones == 16, "大本营周围八区块变初始资源区",
					hqNeighborZones + "/16 区块为木头/石头且归属本队");
			check(hqNeighborWool == 16 && hqNeighborWoolBlock == 16, "初始资源区羊毛照常放置",
					"周围八区块羊毛登记+实体 " + hqNeighborWool + "/" + hqNeighborWoolBlock + "/16（悬浮图标显示资源类型）");
			check(!cv.chunkHasWool(redHq), "大本营中心玻璃基座", "中心区块无占领羊毛（染色玻璃 + 信标）");

			// 2.01b) 第五十批：羊毛被挖掘过程中发光高亮 + 大本营受击警报
			// 找一个红队邻居羊毛作为挖掘目标
			BlockPos glowWool = null;
			for (int dx = -1; dx <= 1 && glowWool == null; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) continue;
					BlockPos p = cv.zoneAt(new net.minecraft.world.level.ChunkPos(redHq.x() + dx, redHq.z() + dz)) != null
							? java.util.Objects.requireNonNullElse(cv.zoneAt(
									new net.minecraft.world.level.ChunkPos(redHq.x() + dx, redHq.z() + dz)).woolPos, null)
							: null;
					if (p != null && civLevel.getBlockState(p).getBlock()
							== com.survivalcontract.civilization.CivilizationManager.woolBlock(GameStateManager.Team.RED)) {
						glowWool = p;
						break;
					}
				}
			}
			long gnow = srv.getTickCount();
			var red1Mode = (com.survivalcontract.mixin.ServerPlayerGameModeAccessor) red1.gameMode;
			// 模拟红队玩家正在挖这格羊毛 → 高亮点亮（发光 BlockDisplay）
			red1Mode.sc$setDestroying(true);
			red1Mode.sc$setDestroyPos(glowWool);
			cv.updateWoolGlowForTest(civLevel, gnow);
			java.util.UUID glowId1 = glowWool != null ? cv.woolGlowIdForTest(glowWool) : null;
			var glowEnt = glowId1 != null ? civLevel.getEntity(glowId1) : null;
			check(glowWool != null && glowEnt instanceof net.minecraft.world.entity.Display.BlockDisplay,
					"羊毛被挖高亮点亮", glowWool == null ? "未找到红队邻居羊毛"
							: glowEnt instanceof net.minecraft.world.entity.Display.BlockDisplay
							? "羊毛位置生成发光 BlockDisplay" : "实体类型不符: " + glowEnt);
			if (glowEnt != null) {
				var sb1 = srv.getScoreboard();
				check(sb1.getPlayerTeam("sc_wool_glow_red") != null
								&& sb1.getPlayerTeam("sc_wool_glow_red").getPlayers().contains(glowId1.toString()),
						"高亮按队伍上色", "发光实体已加入 sc_wool_glow_red 队伍");
			} else {
				check(false, "高亮按队伍上色", "高亮实体未生成");
			}
			// 持续挖掘 → 高亮保持（同一实体，不重复创建）
			cv.updateWoolGlowForTest(civLevel, gnow + 2);
			java.util.UUID glowId2 = glowWool != null ? cv.woolGlowIdForTest(glowWool) : null;
			check(glowId1 != null && glowId1.equals(glowId2), "持续挖掘高亮保持",
					"再次扫描实体 UUID 不变（仅续期）");
			// 挖掘中断 → 超时后高亮移除
			red1Mode.sc$setDestroying(false);
			red1Mode.sc$setDestroyPos(null);
			cv.updateWoolGlowForTest(civLevel, gnow + 4);
			check(cv.woolGlowIdForTest(glowWool) != null, "挖掘中断高亮短暂保留",
					"超时前（+4 tick）仍高亮（防抖）");
			cv.updateWoolGlowForTest(civLevel, gnow + 4 + 11);
			check(cv.woolGlowIdForTest(glowWool) == null, "挖掘中断高亮超时移除",
					"超时后（+15 tick）高亮消失");
			// 羊毛被挖掉（方块消失）→ 高亮立即移除
			red1Mode.sc$setDestroying(true);
			red1Mode.sc$setDestroyPos(glowWool);
			cv.updateWoolGlowForTest(civLevel, gnow + 20);
			net.minecraft.world.level.block.state.BlockState woolState = civLevel.getBlockState(glowWool);
			civLevel.removeBlock(glowWool, false);
			cv.updateWoolGlowForTest(civLevel, gnow + 22);
			check(cv.woolGlowIdForTest(glowWool) == null, "羊毛被挖掉高亮立即移除",
					"方块消失后高亮移除（不等超时）");
			civLevel.setBlock(glowWool, woolState, 3); // 恢复原状，避免污染后续断言
			red1Mode.sc$setDestroying(false);
			red1Mode.sc$setDestroyPos(null);
			cv.updateWoolGlowForTest(civLevel, gnow + 24);
			// 大本营受击警报：触发 / 5 秒冷却 / 冷却结束再触发
			cv.hqWarnLastTickForTest(GameStateManager.Team.RED, gnow - 1000);
			boolean warn1 = cv.tryWarnHqUnderAttack(GameStateManager.Team.RED);
			boolean warn2 = cv.tryWarnHqUnderAttack(GameStateManager.Team.RED);
			check(warn1 && !warn2, "大本营受击警报触发 + 5 秒冷却",
					warn1 ? (warn2 ? "两次都触发（冷却失效）" : "首次触发，冷却期内不重复") : "未触发");
			cv.hqWarnLastTickForTest(GameStateManager.Team.RED, gnow - 100);
			boolean warn3 = cv.tryWarnHqUnderAttack(GameStateManager.Team.RED);
			check(warn3, "警报冷却结束可再触发", "距上次 100 tick（5 秒）后再次触发");
			cv.hqWarnLastTickForTest(GameStateManager.Team.RED, gnow); // 收尾归位，防污染后续断言


			// 2.5) 信标光柱 + 物资桶设施：每队 12 个受保护方块（1 玻璃基座 + 1 信标 + 3×3 铁块 + 1 物资桶）
			check(cv.beaconProtectedCount() == 24, "大本营信标光柱与物资桶设施",
					cv.beaconProtectedCount() + " 个受保护方块（应 24）");
			// 物资桶刷新间隔：初始占领 9 区块 → 10 分钟
			check(cv.barrelIntervalSecondsForTest(GameStateManager.Team.RED) == 600,
					"物资桶初始刷新间隔", cv.barrelIntervalSecondsForTest(GameStateManager.Team.RED) + " 秒（应 600）");

			// 2.1) 第四十八批：沙区默认配置 + 资源区数量上限默认 + setup 分配遵守上限
			var cfg0 = com.survivalcontract.civilization.CivilizationProductionConfig.get();
			var sandT = com.survivalcontract.civilization.CivilizationManager.ZoneType.SAND;
			check(cfg0.zoneIntervalTicks(sandT) == 400 && cfg0.zoneCount(sandT) == 8
							&& cfg0.zoneOutput(sandT) == net.minecraft.world.item.Items.SAND,
					"沙区默认产出", "20 秒 ×8 沙子");
			var sandBonus = cfg0.zoneBonuses(sandT);
			check(sandBonus.size() == 3 && sandBonus.get(0).chancePercent() == 10.0 && sandBonus.get(0).count() == 1
							&& sandBonus.get(0).items().length == 1
							&& sandBonus.get(0).items()[0] == net.minecraft.world.item.Items.GUNPOWDER
							&& sandBonus.get(1).chancePercent() == 10.0 && sandBonus.get(1).count() == 1
							&& sandBonus.get(1).items().length == 1
							&& sandBonus.get(1).items()[0] == net.minecraft.world.item.Items.SUGAR_CANE
							&& sandBonus.get(2).chancePercent() == 15.0 && sandBonus.get(2).count() == 1
							&& sandBonus.get(2).items().length == 1
							&& sandBonus.get(2).items()[0] == net.minecraft.world.item.Items.GLASS,
					"沙区默认副产物", "火药 10% ×1 + 甘蔗 10% ×1 + 玻璃 15% ×1");
			var sandReq = cfg0.zoneRequireBlocks(sandT);
			check(sandReq.length == 1 && net.minecraft.core.registries.BuiltInRegistries.BLOCK
							.getKey(sandReq[0]).toString().equals("minecraft:ender_chest"),
					"沙区默认前置方块", "末影箱（放置后才产出）");
			check(Integer.valueOf(3).equals(cfg0.zoneLimit(com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY))
							&& Integer.valueOf(15).equals(cfg0.zoneLimit(com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND))
							&& cfg0.zoneLimit(com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD) == null,
					"资源区数量上限默认", "合金 ≤3 / 钻石 ≤15 / 木头无限制");
			int sandZones = 0, alloyZones = 0, diamondZones = 0;
			for (int cx = -7; cx <= 7; cx++) {
				for (int cz = -7; cz <= 7; cz++) {
					var zz = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
					if (zz == null || zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ) continue;
					if (zz.type == sandT) sandZones++;
					if (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY) alloyZones++;
					if (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND) diamondZones++;
				}
			}
			check(sandZones >= 1, "沙区生成", "setup 随机分配含沙区（" + sandZones + " 个）");
			check(alloyZones <= 3, "合金区数量上限生效", alloyZones + " ≤ 3");
			check(diamondZones <= 15, "钻石区数量上限生效", diamondZones + " ≤ 15");

			// 2.2) 第四十九批：概率调整默认值 + 大本营物资间隔公式
			// （配置版本迁移：旧版本文件自动备份 .bak 并重新生成 → get() 必为新默认值）
			var woodT = com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD;
			var stoneT = com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE;
			var lapisT = com.survivalcontract.civilization.CivilizationManager.ZoneType.LAPIS;
			var diamondT = com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND;
			var quartzT = com.survivalcontract.civilization.CivilizationManager.ZoneType.QUARTZ;
			var woodBonus = cfg0.zoneBonuses(woodT);
			check(woodBonus.size() == 2 && woodBonus.get(0).chancePercent() == 30.0
							&& woodBonus.get(0).items().length == 1
							&& woodBonus.get(0).items()[0] == net.minecraft.world.item.Items.APPLE
							&& woodBonus.get(1).chancePercent() == 10.0
							&& woodBonus.get(1).items().length == 5,
					"木头区副产物概率默认", "苹果 30% + 随机蔬果 10%（5 项）");
			var stoneBonus = cfg0.zoneBonuses(stoneT);
			check(stoneBonus.size() == 2 && stoneBonus.get(0).chancePercent() == 20.0
							&& stoneBonus.get(0).items().length == 1
							&& stoneBonus.get(0).items()[0] == net.minecraft.world.item.Items.COAL
							&& stoneBonus.get(1).chancePercent() == 15.0
							&& stoneBonus.get(1).items().length == 1
							&& stoneBonus.get(1).items()[0] == net.minecraft.world.item.Items.GRAVEL,
					"石头区副产物概率默认", "煤炭 20% + 沙砾 15%");
			var lapisBonus = cfg0.zoneBonuses(lapisT);
			check(lapisBonus.size() == 2 && lapisBonus.get(1).chancePercent() == 15.0
							&& lapisBonus.get(1).items().length == 1
							&& lapisBonus.get(1).items()[0] == net.minecraft.world.item.Items.EXPERIENCE_BOTTLE,
					"青金石区经验瓶概率默认", "15%");
			var diaBonus = cfg0.zoneBonuses(diamondT);
			check(diaBonus.size() == 1 && diaBonus.get(0).chancePercent() == 20.0
							&& diaBonus.get(0).items().length == 1
							&& diaBonus.get(0).items()[0] == net.minecraft.world.item.Items.OBSIDIAN
							&& diaBonus.get(0).totalCap() == 30,
					"钻石区黑曜石概率/单区上限默认", "20% + 每区 ≤30");
			var quartzBonus = cfg0.zoneBonuses(quartzT);
			check(quartzBonus.size() == 1 && quartzBonus.get(0).chancePercent() == 15.0
							&& quartzBonus.get(0).items().length == 1
							&& quartzBonus.get(0).items()[0] == net.minecraft.world.item.Items.NETHER_WART,
					"石英区副产物概率默认", "地狱疣 15%（烈焰棒已删除）");
			var hc0 = cfg0.hqSupplies;
			check(hc0.intervalSeconds == 300 && hc0.claimsPerStep == 5
							&& hc0.reducePerStepSeconds == 15 && hc0.minSeconds == 60,
					"大本营物资间隔配置默认", "300s / 每 5 区块 -15s / 最低 60s");
			check(com.survivalcontract.civilization.CivilizationManager.hqSupplyIntervalSeconds(hc0, 2) == 300
							&& com.survivalcontract.civilization.CivilizationManager.hqSupplyIntervalSeconds(hc0, 5) == 285
							&& com.survivalcontract.civilization.CivilizationManager.hqSupplyIntervalSeconds(hc0, 9) == 285
							&& com.survivalcontract.civilization.CivilizationManager.hqSupplyIntervalSeconds(hc0, 80) == 60
							&& com.survivalcontract.civilization.CivilizationManager.hqSupplyIntervalSeconds(hc0, 200) == 60,
					"大本营物资间隔公式", "2 占领=300s / 9 占领=285s / 80+ 占领=60s 下限");

			// 2.4) 第四十九批：自定义地形预设（生成器按预设构建：群系/矿层断言）
			var stems0 = srv.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM);
			var savedPreset = gsm.getTerrainPreset();
			gsm.setTerrainPreset(GameStateManager.TerrainPreset.SNOW);
			var snowStem = com.survivalcontract.dimension.ArenaWorldManager.terrainStem(srv, stems0);
			check(snowStem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource,
					"雪之王国预设生成器", "FlatLevelSource（无结构超平坦）");
			if (snowStem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource sg) {
				var bKey = sg.settings().getBiome().unwrapKey();
				check(bKey.isPresent() && bKey.get().identifier().toString().equals("minecraft:snowy_plains"),
						"雪之王国群系", bKey.map(k -> k.identifier().toString()).orElse("?"));
				var sLayers = sg.settings().getLayersInfo();
				check(!sLayers.isEmpty() && sLayers.get(sLayers.size() - 1).getBlockState().getBlock()
								== net.minecraft.world.level.block.Blocks.SNOW_BLOCK,
						"雪之王国顶层雪块", "层序列自底向上：末层 = 雪块（地表是雪）");
			}
			gsm.setTerrainPreset(GameStateManager.TerrainPreset.DESERT);
			var desertStem = com.survivalcontract.dimension.ArenaWorldManager.terrainStem(srv, stems0);
			if (desertStem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource dg) {
				var bKey = dg.settings().getBiome().unwrapKey();
				check(bKey.isPresent() && bKey.get().identifier().toString().equals("minecraft:desert"),
						"沙漠预设群系", bKey.map(k -> k.identifier().toString()).orElse("?"));
				var dLayers = dg.settings().getLayersInfo();
				check(dLayers.size() == 4
								&& dLayers.get(0).getBlockState().getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK
								&& dLayers.get(2).getBlockState().getBlock() == net.minecraft.world.level.block.Blocks.SANDSTONE
								&& dLayers.get(3).getBlockState().getBlock() == net.minecraft.world.level.block.Blocks.SAND,
						"沙漠预设层构成（地表有沙）", "自底向上：基岩→石头→砂岩→顶面 4 格沙");
			} else {
				check(false, "沙漠预设群系", "生成器不是 FlatLevelSource");
			}
			gsm.setTerrainPreset(GameStateManager.TerrainPreset.MINER);
			var minerStem = com.survivalcontract.dimension.ArenaWorldManager.terrainStem(srv, stems0);
			boolean minerDiamond = false, minerCoal = false, minerGrassTop = false;
				if (minerStem.generator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource mg) {
					var mLayers = mg.settings().getLayersInfo();
					minerGrassTop = !mLayers.isEmpty()
							&& mLayers.get(mLayers.size() - 1).getBlockState().getBlock()
							== net.minecraft.world.level.block.Blocks.GRASS_BLOCK;
					for (var li : mLayers) {
						if (li.getBlockState().getBlock() == net.minecraft.world.level.block.Blocks.DIAMOND_ORE) minerDiamond = true;
						if (li.getBlockState().getBlock() == net.minecraft.world.level.block.Blocks.COAL_ORE) minerCoal = true;
					}
				}
				check(minerDiamond && minerCoal && minerGrassTop, "挖掘工的梦想矿层",
						"含煤矿层 + 钻石矿层 + 顶层草方块（地表是草地）");
			gsm.setTerrainPreset(savedPreset);
			check(gsm.getTerrainPreset() == GameStateManager.TerrainPreset.OVERWORLD,
					"地形预设状态存取", "setTerrainPreset/getTerrainPreset 生效（回默认主世界）");

			// 2.02) 第五十一批：开局发放 8 个本队羊毛（放置正常消耗——取消返还）
			GameStateManager.Team buyerTeam = gsm.getPlayerTeam(red1.getUUID());
			var ownWool = com.survivalcontract.civilization.CivilizationManager.woolBlock(buyerTeam).asItem();
			{
				var kitInv = red1.getInventory();
				for (int i = 0; i < kitInv.getContainerSize(); i++) {
					if (kitInv.getItem(i).is(ownWool)) kitInv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				}
				com.survivalcontract.dimension.LobbyManager.getInstance().giveCivStartKit(red1, gsm);
				check(countItem(red1, ownWool) == 8, "开局发放 8 个本队羊毛",
						"背包本队羊毛 = " + countItem(red1, ownWool) + "（应 8）");
			}

			// 2.3) 第五十一批：基地商人（红蓝大本营各一：不能行动 + 永久抗性提升5 + 站在地表 +
			//      商品栏第一位 = 本队羊毛动态价 80/+4/320 封顶 + 商品五条 + 积分兑换）
			for (GameStateManager.Team mt : new GameStateManager.Team[]{
					GameStateManager.Team.RED, GameStateManager.Team.BLUE}) {
				String cn = mt == GameStateManager.Team.RED ? "红队" : "蓝队";
				var mEnt = cv.merchantEntity(mt);
				check(mEnt instanceof net.minecraft.world.entity.npc.villager.Villager && mEnt.isAlive(),
						cn + "基地商人生成", mEnt == null ? "未找到"
								: mEnt.getType() + " @ " + cv.merchantPosForTest(mt));
				if (mEnt instanceof net.minecraft.world.entity.Mob mv) {
					check(mv.isNoAi(), cn + "商人不能行动", "NoAI=true");
					var eff = mv.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
					check(eff != null && eff.getAmplifier() == 4 && eff.isInfiniteDuration(),
							cn + "商人永久抗性提升5", "amplifier=4 无限时长");
					// 脚下"有地必落地"：向下探到本列最深处分层；虚空列（测试环境未生成地形）无可判定地表
					int solidY = -1;
					for (int yy = mv.blockPosition().getY() - 1; yy >= mv.level().getMinY(); yy--) {
						var bs = mv.level().getBlockState(new net.minecraft.core.BlockPos(
								mv.blockPosition().getX(), yy, mv.blockPosition().getZ()));
						if (!bs.isAir()) { solidY = yy; break; }
					}
					var below = mv.level().getBlockState(mv.blockPosition().below());
					boolean grounded = solidY < 0 || !below.isAir();
					check(grounded, cn + "商人立于地表不悬空",
							solidY < 0 ? "虚空列（无地表可判定）" : "脚下方块 " + below.getBlock());
				}
				check(cv.woolCostForTest(mt) == 80, cn + "羊毛初始价 80 积分",
						"当前 " + cv.woolCostForTest(mt) + " 分");
			}
			var offers = cv.merchantOffersForTest(GameStateManager.Team.RED);
			check(offers.size() == 5, "商人商品五条", offers.size() + " 条");
			int bottleCnt = 0, gearCnt = 0, potionCnt = 0, scrollCnt = 0;
			for (var o : offers) {
				if (o.product().is(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE)) { // 第六十九批：附魔之瓶（固定价不涨价）
					bottleCnt++;
					check(o.cost() == 30 && o.product().getCount() == 1,
							"商人附魔之瓶 30 积分", "cost=" + o.cost() + " count=" + o.product().getCount());
					continue;
				}
				switch (o.category()) {
					case "武器装备" -> {
						gearCnt++;
						var ench = o.product().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
						check(ench != null && !ench.isEmpty(),
								"商人武器装备随机附魔", "ENCHANTMENTS 非空");
					}
					case "药水" -> {
						potionCnt++;
						check(o.product().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) != null,
								"商人药水", "POTION_CONTENTS 非空");
					}
					case "回城卷轴" -> { // 第七十八批：三号位固定商品
						scrollCnt++;
						check(o.product().is(net.minecraft.world.item.Items.PAPER)
										&& com.survivalcontract.civilization.CivilizationManager.isReturnScroll(o.product()),
								"商人回城卷轴", "PAPER + 自定义名含回城卷轴");
					}
					default -> check(false, "商人商品类别", "未知类别: " + o.category());
				}
			}
			check(bottleCnt == 1 && scrollCnt == 1 && gearCnt == 2 && potionCnt == 1, "商人商品构成",
					bottleCnt + " 附魔之瓶 / " + scrollCnt + " 回城卷轴 / " + gearCnt + " 武器装备 / " + potionCnt + " 药水");
			check(offers.size() == 5 && offers.get(0).cost() == 30 && offers.get(1).cost() == 500
							&& "回城卷轴".equals(offers.get(1).category()) && offers.get(4).cost() == 300,
					"商人商品价格默认（第七十八批）",
					"瓶 " + offers.get(0).cost() + " / 卷轴(三号位) " + offers.get(1).cost()
							+ " / 药水 " + offers.get(4).cost());
			// 羊毛兑换：积分不足拒绝；动态价 80 → 每买 +4 → 320 封顶；封顶后不再上涨
			var buyer = red1;
			{
				var wi = buyer.getInventory();
				for (int i = 0; i < wi.getContainerSize(); i++) {
					if (wi.getItem(i).is(ownWool)) wi.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				}
			}
			int woolBefore = 0; // 已清空本队羊毛：61 次兑换共 61 个（单格 64 可容纳，防背包溢出掉落）
			gsm.setScore(buyer.getUUID(), 10);
			check(cv.buyFromMerchant(buyer, buyerTeam, 0)
							&& gsm.getScore(buyer.getUUID()) == 10
							&& countItem(buyer, ownWool) == woolBefore,
					"积分不足拒绝兑换羊毛", "10 分 < 80 分 → 不扣分不发货（点击被消费）");
			int woolBuys = 0;
			for (int expect = 80; expect <= 88; expect += 4) { // 前三次逐次验证 +4 涨价
				gsm.setScore(buyer.getUUID(), 100000);
				check(cv.buyFromMerchant(buyer, buyerTeam, 0), "羊毛兑换成功", expect + " 积分兑换 1 个");
				check(gsm.getScore(buyer.getUUID()) == 100000 - expect, "羊毛兑换扣分",
						"扣 " + expect + " → 剩 " + gsm.getScore(buyer.getUUID()));
				woolBuys++;
				check(cv.woolCostForTest(buyerTeam) == expect + 4, "羊毛动态涨价 +4",
						expect + " → " + cv.woolCostForTest(buyerTeam));
			}
			gsm.setScore(buyer.getUUID(), 100000);
			while (cv.woolCostForTest(buyerTeam) < 320) { // 连续买到封顶
				cv.buyFromMerchant(buyer, buyerTeam, 0);
				woolBuys++;
			}
			check(cv.woolCostForTest(buyerTeam) == 320, "羊毛价封顶 320",
					"80 起步每买 +4 → " + cv.woolCostForTest(buyerTeam));
			cv.buyFromMerchant(buyer, buyerTeam, 0); // 封顶后再买
			woolBuys++;
			check(cv.woolCostForTest(buyerTeam) == 320, "封顶后价格不再上涨", "仍 320 分");
			check(countItem(buyer, ownWool) == woolBefore + woolBuys, "羊毛兑换发放",
					"背包新增本队羊毛 ×" + woolBuys);
			// 商品兑换：积分足够 → 扣分 + 发货；积分不足 → 拒绝；商品本局固定（兑换后商品列表不变）
			int cost0 = offers.get(0).cost();
			gsm.setScore(buyer.getUUID(), 1000);
			int invBefore = countItem(buyer, offers.get(0).product().getItem());
			check(cv.buyFromMerchant(buyer, buyerTeam, 1), "商人兑换成功", "buyFromMerchant 消费点击");
			check(gsm.getScore(buyer.getUUID()) == 1000 - cost0, "兑换扣除个人积分",
					1000 + " - " + cost0 + " → " + gsm.getScore(buyer.getUUID()));
			check(countItem(buyer, offers.get(0).product().getItem()) == invBefore + 1,
					"兑换发放商品", "背包新增商品本体 ×1");
			gsm.setScore(buyer.getUUID(), 10);
			int invBefore2 = countItem(buyer, offers.get(2).product().getItem());
			cv.buyFromMerchant(buyer, buyerTeam, 3);
			check(gsm.getScore(buyer.getUUID()) == 10
							&& countItem(buyer, offers.get(2).product().getItem()) == invBefore2,
					"积分不足拒绝兑换", "10 分 < " + offers.get(2).cost() + " 分 → 不扣分不发货");
			check(cv.merchantOffersForTest(buyerTeam).equals(offers), "商人物资本局固定", "兑换后商品列表不变");
			check(cv.merchantOffersForTest(GameStateManager.Team.BLUE)
							.equals(cv.merchantOffersForTest(GameStateManager.Team.RED)),
					"红蓝商人商品一致", "同一次掷定的商品列表");

			// 回城卷轴（第七十八批）：三号位固定商品（500 积分）→ 兑换得带标记本体 → 右键消耗传送到队伍出生点
			check(com.survivalcontract.civilization.CivilizationManager.isReturnScroll(
							com.survivalcontract.civilization.CivilizationManager.createReturnScroll())
							&& !com.survivalcontract.civilization.CivilizationManager.isReturnScroll(
									new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER)),
					"回城卷轴物品判定", "卷轴（纸+自定义名）true / 普通纸 false");
			gsm.setScore(buyer.getUUID(), 1000);
			check(cv.buyFromMerchant(buyer, buyerTeam, 2), "兑换回城卷轴成功", "三号位槽消费点击");
			check(gsm.getScore(buyer.getUUID()) == 500, "兑换回城卷轴扣 500 分",
					"1000 - 500 → " + gsm.getScore(buyer.getUUID()));
			{
				var home = gsm.getRespawnPoint(buyer.getUUID());
				// 主手槽位不可预知 → 铺满整条快捷栏（0-8），消耗后总纸数 -1；测毕还原快捷栏
				var savedHotbar = new net.minecraft.world.item.ItemStack[9];
				for (int i = 0; i < 9; i++) savedHotbar[i] = buyer.getInventory().getItem(i).copy();
				for (int i = 0; i < 9; i++) buyer.getInventory().setItem(i,
						com.survivalcontract.civilization.CivilizationManager.createReturnScroll());
				int paperBefore = countItem(buyer, net.minecraft.world.item.Items.PAPER);
				boolean used = cv.useReturnScroll(buyer, net.minecraft.world.InteractionHand.MAIN_HAND);
				int paperAfter = countItem(buyer, net.minecraft.world.item.Items.PAPER);
				boolean nearHome = Math.abs(buyer.getX() - (home.getX() + 0.5)) < 0.6
						&& Math.abs(buyer.getZ() - (home.getZ() + 0.5)) < 0.6;
				check(used && paperAfter == paperBefore - 1 && nearHome,
						"回城卷轴：右键消耗并传送回队伍出生点",
						"used=" + used + " 纸 " + paperBefore + "→" + paperAfter
								+ " 落点 (" + (int) buyer.getX() + "," + (int) buyer.getZ()
								+ ") 期望 (" + home.getX() + "," + home.getZ() + ")");
				for (int i = 0; i < 9; i++) buyer.getInventory().setItem(i, savedHotbar[i]);
			}
			gsm.setScore(buyer.getUUID(), 0); // 清除商人兑换测试的分数污染（后续回收积分断言从 0 起算）

			// 3) WorldBorder 240 围住 15×15 疆域
			ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(srv);
			if (level == null) level = srv.overworld();
			check(level.getWorldBorder().getSize() <= 241.0, "世界边界围住疆域",
					"border=" + level.getWorldBorder().getSize());

			// 3.5) 合金碎片区固定三格（第五十三批固定布局：(0,14)/(7,7)/(14,0)）
			check(alloyZones == 3, "合金区固定三格", "alloyZones=" + alloyZones + "（应 3：(0,14)/(7,7)/(14,0)）");

			// 4) 邻接规则：大本营相邻区块可占，纵深区块不可占
			var near = new net.minecraft.world.level.ChunkPos(redHq.x() + 1, redHq.z());
			var far = new net.minecraft.world.level.ChunkPos(redHq.x() + 5, redHq.z());
			check(cv.canClaim(GameStateManager.Team.RED, near), "邻接区块可占领", "与大本营相邻");
			check(!cv.canClaim(GameStateManager.Team.RED, far), "跨越敌方纵深被拒", "不相邻区块不可占");

			// 4.5) 容器队伍归属：登记归属的箱子仅归属队可开（敌方锁定；区块归属队反占后也打不开）
			ServerPlayer redTester = null;
			for (UUID rid : gsm.getTeamMembers(GameStateManager.Team.RED)) {
				var rp = srv.getPlayerList().getPlayer(rid);
				if (rp != null) { redTester = rp; break; }
			}
			var chestState = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
			var chestPos = redHq.getWorldPosition().offset(2, 0, 2);
			cv.registerContainer(chestPos, GameStateManager.Team.RED);
			check(redTester != null && cv.canInteract(redTester, chestPos, chestState),
					"归属容器本队可开", redTester != null ? "本队成员可打开本队箱子" : "无红队在线成员");
			ServerPlayer enemyTester = null;
			for (UUID eid : gsm.getTeamMembers(GameStateManager.Team.BLUE)) {
				var ep = srv.getPlayerList().getPlayer(eid);
				if (ep != null && ep != redTester) { enemyTester = ep; break; }
			}
			check(enemyTester != null && !cv.canInteract(enemyTester, chestPos, chestState),
					"归属容器敌方锁定", enemyTester != null ? "敌方成员无法打开红队箱子" : "无敌方在线成员");

			// 5) 羊毛破坏 → 无主 → 锁定后可重新占领（但不可跨越疆域直接跳占对方 HQ——规则 8）
			cv.onWoolBroken(srv, level, redHq);
			check(cv.zoneAt(redHq).owner == null, "羊毛破坏回归无主", "HQ 区块 owner=null");
			check(!cv.chunkHasWool(redHq), "每区块羊毛索引（破坏后清除）",
					"羊毛破坏后可重新放置");
			check(!cv.canClaim(GameStateManager.Team.BLUE, redHq), "跨越疆域跳占对方 HQ 被拒",
					"HQ 在蓝队领土纵深外（不相邻不可占）——邻接规则正确");

			// 6) 回收积分：red1 所在队伍的回收箱放入 10 原木（1 分/个）→ 无提交者记录走兜底全队平分
			//    （分队随机，不能按变量名假设队伍——动态取 red1 实际队伍与人数；
			//     有提交者记录时的主路径"全额记给提交者"见 testBatch69Features）
			GameStateManager.Team recycleTeam = gsm.getPlayerTeam(red1.getUUID());
			var box = cv.recycleBoxForTest(recycleTeam);
			box.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 10));
			int recycleMembers = Math.max(1, gsm.getTeamMembers(recycleTeam).size());
			int recycleExpect = 10 / recycleMembers;
			check(gsm.getScore(red1.getUUID()) == recycleExpect, "回收资源换积分",
					"10 原木回收平分 → red1 " + gsm.getScore(red1.getUUID()) + " 分（应 " + recycleExpect
							+ "，本队 " + recycleMembers + " 人）");

			// 7) 队伍羊毛方块：专属占领羊毛注册正确（红=claim_wool_red——5 倍硬度/不掉落；放置正常消耗——第五十一批取消返还）
			String redWoolId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
					.getKey(com.survivalcontract.civilization.CivilizationManager.woolBlock(GameStateManager.Team.RED)).toString();
			check(redWoolId.equals("survival-contract:claim_wool_red"), "队伍羊毛方块映射",
					"woolBlock(RED) = " + redWoolId);

			// 8) 放置羊毛链路：蓝队成员对大本营 3×3 外一格（无主、与本方领土相邻）放置 → pending 登记
			//    （分队随机，不能按变量名假设队伍——动态取蓝队在线成员）
			ServerPlayer blueTester = null;
			for (UUID bid : gsm.getTeamMembers(GameStateManager.Team.BLUE)) {
				var bp = srv.getPlayerList().getPlayer(bid);
				if (bp != null) { blueTester = bp; break; }
			}
			check(blueTester != null, "蓝队测试成员在线", blueTester != null ? blueTester.getName().getString() : "无");
			var claimPosB = new net.minecraft.world.level.ChunkPos(blueHq.x() - 2, blueHq.z());
			cv.tryPlaceWool(blueTester, claimPosB, blueHq.getWorldPosition());
			check(cv.isPendingClaim(claimPosB), "放置羊毛登记占领",
					"3×3 外相邻无主区块 pending 已登记（15 秒后自动占领）");

			// 9) 服务端兜底放置（原版拒绝放置——如点击脚边草丛/花草，落点与玩家碰撞箱重叠时原版
			//    isUnobstructed 静默失败）：队列校验时羊毛未落地 → 服务端在可替换/空气候选格兜底
			//    放置并登记占领——往草丛/花位置放羊毛同样进入占领进度
			// 疆域为 15×15（中心 ±7）：blueHq=(cx+6,cz+6) → (cx+4,cz+5) 在疆域内、HQ 3×3 外、
			// 与蓝队初始区块 (cx+5,cz+5) 相邻、无主且未被测试 8 pending（第四十八批大本营改东南角）
			var claimChunkC = new net.minecraft.world.level.ChunkPos(blueHq.x() - 2, blueHq.z() - 1);
			var fbPos = new net.minecraft.core.BlockPos(
					claimChunkC.getMinBlockX() + 8, 200, claimChunkC.getMinBlockZ() + 8);
			var testLevel = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(srv);
			if (testLevel == null) testLevel = srv.overworld();
			check(testLevel.getBlockState(fbPos).isAir(), "兜底放置前置：候选格为空气", "Y=200 高空格");
			int fbWoolBefore = countItem(blueTester, com.survivalcontract.civilization.CivilizationManager
					.woolBlock(GameStateManager.Team.BLUE).asItem());
			cv.queueWoolCheck(blueTester, fbPos, fbPos, true,
					com.survivalcontract.civilization.CivilizationManager.woolBlock(GameStateManager.Team.BLUE));
			cv.processWoolChecksForTest(srv, testLevel, srv.getTickCount() + 1);
			check(testLevel.getBlockState(fbPos).is(com.survivalcontract.civilization.CivilizationManager
							.woolBlock(GameStateManager.Team.BLUE)),
					"羊毛服务端兜底放置", "原版未放置成功时服务端在可替换/空气候选格放置成功");
			check(cv.isPendingClaim(claimChunkC), "兜底放置登记占领",
					"往草丛/花位置放置（原版拒绝落地）同样进入占领进度");
			check(countItem(blueTester, com.survivalcontract.civilization.CivilizationManager
							.woolBlock(GameStateManager.Team.BLUE).asItem()) == fbWoolBefore,
					"羊毛放置正常消耗不返还", "第五十一批取消返还——兜底放置前后背包羊毛数不变");
			testLevel.removeBlock(fbPos, false); // 清理测试羊毛（pending 由 finally 的 reset 清除）

			// 10) 产出配置自定义：写临时配置 → reload → 断言新字段访问器 → 删文件还原默认
			//    （覆盖第四十六批新字段：主产物概率、副产物数量、刷怪间隔/生命值/上限、大本营产出、稀有物池；
			//      第四十八批新字段：沙区产出、收获前置方块、资源区数量上限）
			var cfgFile = com.survivalcontract.civilization.CivilizationProductionConfig.file();
			try {
				java.nio.file.Files.writeString(cfgFile, """
						{
						  "configVersion": 6,
						  "zones": {
						    "QUARTZ": {
						      "intervalSeconds": 30, "output": "quartz", "count": 4, "outputChancePercent": 50,
						      "bonuses": [ { "chancePercent": 10, "count": 3, "items": ["nether_wart"] } ],
						      "spawnMob": "magma_cube", "spawnChancePercent": 30, "spawnCount": 2,
						      "spawnIntervalSeconds": 15, "spawnMaxHealth": 5, "spawnCap": 7
						    },
						    "SAND": {
						      "intervalSeconds": 25, "output": "red_sand", "count": 3,
						      "bonuses": [ { "chancePercent": 7, "count": 2, "items": ["gunpowder"] } ],
						      "requireBlocks": ["furnace"]
						    },
						    "COPPER": { "requireBlocks": ["iron_block"] },
						    "WOOD": {
						      "intervalSeconds": 1, "output": "oak_log", "count": 0, "requireBlocks": [],
						      "bonuses": [ { "chancePercent": 100, "count": 10, "totalCap": 30, "items": ["obsidian"] } ]
						    },
						    "STONE": {
						      "intervalSeconds": 1, "output": "cobblestone", "count": 0, "requireBlocks": [],
						      "bonuses": [ { "chancePercent": 100, "count": 10, "totalCap": 30, "items": ["obsidian"] } ]
						    }
						  },
						  "zoneLimits": { "limits": { "DIAMOND": 3, "ALLOY": 2, "QUARTZ": 1 } },
						  "hqSupplies": { "intervalSeconds": 45, "chancePercent": 60, "items": ["diamond"] },
						  "supplyBarrel": { "rareItems": ["diamond"] }
						}
						""");
				var cfg = com.survivalcontract.civilization.CivilizationProductionConfig.reload();
				var qt = com.survivalcontract.civilization.CivilizationManager.ZoneType.QUARTZ;
				check(cfg.zoneIntervalTicks(qt) == 600, "配置：主产物产出时间",
						"intervalTicks=" + cfg.zoneIntervalTicks(qt) + "（应 600 = 30 秒）");
				check(cfg.zoneCount(qt) == 4, "配置：主产物数量",
						"count=" + cfg.zoneCount(qt) + "（应 4）");
				check(cfg.zoneOutput(qt) == net.minecraft.world.item.Items.QUARTZ, "配置：主产物种类",
						"output=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cfg.zoneOutput(qt)));
				check(cfg.zoneOutputChancePercent(qt) == 50.0, "配置：主产物产出概率", "outputChancePercent=50");
				check(cfg.zoneSpawnIntervalTicks(qt) == 300 && cfg.zoneSpawnCount(qt) == 2
								&& cfg.zoneSpawnChancePercent(qt) == 30,
						"配置：刷怪间隔/数量/概率", "15 秒 ×2 只 @30%");
				check(cfg.zoneSpawnMaxHealth(qt) == 5.0 && cfg.zoneSpawnCap(qt) == 7,
						"配置：生物生命值/区块生成上限", "5 点血 / cap 7");
				var cfgBonus = cfg.zoneBonuses(qt);
				check(cfgBonus.size() == 1 && cfgBonus.get(0).count() == 3 && cfgBonus.get(0).chancePercent() == 10.0,
						"配置：副产物数量/概率", "地狱疣 10% ×3");
				check(cfg.hqChancePercent() == 60.0 && cfg.hqItems().size() == 1
								&& cfg.hqItems().get(0) == net.minecraft.world.item.Items.DIAMOND,
						"配置：大本营产出概率/物品池", "60% 概率产钻石");
				check(cfg.supplyBarrelRareItems().size() == 1
								&& cfg.supplyBarrelRareItems().get(0) == net.minecraft.world.item.Items.DIAMOND,
						"配置：物资桶稀有物池", "稀有物=钻石");
				// 第四十八批：沙区产出自定义 + 前置方块可自定义 + 数量上限可自定义
				check(cfg.zoneIntervalTicks(sandT) == 500 && cfg.zoneCount(sandT) == 3
								&& cfg.zoneOutput(sandT) == net.minecraft.world.item.Items.RED_SAND,
						"配置：沙区产出自定义", "25 秒 ×3 红沙");
				var sandBonus2 = cfg.zoneBonuses(sandT);
				check(sandBonus2.size() == 1 && sandBonus2.get(0).chancePercent() == 7.0
								&& sandBonus2.get(0).count() == 2
								&& sandBonus2.get(0).items().length == 1
								&& sandBonus2.get(0).items()[0] == net.minecraft.world.item.Items.GUNPOWDER,
						"配置：沙区副产物自定义", "火药 7% ×2");
				var sandReq2 = cfg.zoneRequireBlocks(sandT);
				check(sandReq2.length == 1 && sandReq2[0] == net.minecraft.world.level.block.Blocks.FURNACE,
						"配置：沙区前置方块自定义", "铜箱子 → 熔炉");
				var cuReq = cfg.zoneRequireBlocks(com.survivalcontract.civilization.CivilizationManager.ZoneType.COPPER);
				check(cuReq.length == 1 && cuReq[0] == net.minecraft.world.level.block.Blocks.IRON_BLOCK,
						"配置：收获前置方块可自定义", "铜区熔炉 → 铁块");
				var dmReq = cfg.zoneRequireBlocks(com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND);
				check(dmReq.length == 3 && dmReq[0] == net.minecraft.world.level.block.Blocks.BLAST_FURNACE
								&& net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dmReq[2])
										.toString().equals("minecraft:waxed_copper_chest"),
						"配置：未配置前置回退默认", "钻石区默认 [高炉, 金块, 涂蜡铜箱子]");
				check(Integer.valueOf(3).equals(cfg.zoneLimit(com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND))
								&& Integer.valueOf(2).equals(cfg.zoneLimit(com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY))
								&& Integer.valueOf(1).equals(cfg.zoneLimit(quartzT)),
						"配置：资源区数量上限自定义", "钻石 ≤3 / 合金 ≤2 / 石英 ≤1");
				// 上限自定义重新分配生效（第五十三批起钻石/合金为固定布局，上限仅作用于随机池区块）：
				// 按临时配置重新 setup → 石英区上限 1 → 全图石英区 ≤1
				cv.setup(srv);
				int diamondCnt = 0, alloyCnt = 0, quartzCnt = 0;
				for (int cx = -7; cx <= 7; cx++) {
					for (int cz = -7; cz <= 7; cz++) {
						var zz = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
						if (zz == null) continue;
						if (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND) diamondCnt++;
						if (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY) alloyCnt++;
						if (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.QUARTZ) quartzCnt++;
					}
				}
				check(quartzCnt <= 1 && diamondCnt == 14 && alloyCnt == 3, "配置：数量上限重新分配生效",
						"石英 " + quartzCnt + " ≤1 / 钻石 " + diamondCnt + " / 合金 " + alloyCnt + " 固定布局");

				// 黑曜石单区累计上限实测（totalCap）：临时配置把木头/石头区改成 100% ×10 黑曜石（cap 30）无前置
				// → 手动触发 5 次产出：前 3 次各 10 个（累计 30），第 4/5 次不再产出 → 箱内恰好 30 个
				net.minecraft.world.level.ChunkPos capChunk = null;
				for (int cx = -7; cx <= 7 && capChunk == null; cx++) {
					for (int cz = -7; cz <= 7; cz++) {
						var zz = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
						if (zz != null && (zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD
								|| zz.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE)) {
							capChunk = new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz);
							break;
						}
					}
				}
				check(capChunk != null, "上限实测：木头/石头区存在", capChunk == null ? "无" : "OK");
				if (capChunk != null) {
					var capZone = cv.zoneAt(capChunk);
					capZone.owner = GameStateManager.Team.RED; // injectZone 仅进有主区块
					var capChest = new net.minecraft.core.BlockPos(capChunk.getMinBlockX() + 8,
							level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
									capChunk.getMinBlockX() + 8, capChunk.getMinBlockZ() + 8) + 1,
							capChunk.getMinBlockZ() + 8);
					level.setBlock(capChest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
					cv.registerFacility(capChest);
					for (int i = 0; i < 5; i++) cv.produceZoneForTest(level, capChunk);
					int obsidian = 0;
					if (level.getBlockEntity(capChest) instanceof net.minecraft.world.Container c) {
						for (int i = 0; i < c.getContainerSize(); i++) {
							var s = c.getItem(i);
							if (s.is(net.minecraft.world.item.Items.OBSIDIAN)) obsidian += s.getCount();
						}
					}
					check(obsidian == 30, "副产品单区累计上限（黑曜石 30 个/区）",
							"5 次产出共 " + obsidian + " 个黑曜石（应恰好 30 = 3×10）");
					level.removeBlock(capChest, false);
					capZone.owner = null;
				}
			} catch (Exception e) {
				check(false, "配置：临时配置读写", e.getClass().getSimpleName() + ": " + e.getMessage());
			} finally {
				// 删除临时配置并还原默认（该路径为本模组专属配置，reload 缺文件时重新生成默认）；
				// 再按默认配置重新 setup，恢复管理器内的产出参数与疆域分配
				try { java.nio.file.Files.delete(cfgFile); } catch (Exception ignored) {}
				com.survivalcontract.civilization.CivilizationProductionConfig.reload();
				cv.setup(srv);
			}

			// 10.5) 第五十一批：交易配置自定义（civilization-trade.json：基地商人货物 + 猪灵回收价表）
			var tradeFile = com.survivalcontract.civilization.CivTradeConfig.file();
			var tradeDef = com.survivalcontract.civilization.CivTradeConfig.get();
			check(tradeDef.merchant.woolInitialCost == 80 && tradeDef.merchant.woolCostStep == 4
							&& tradeDef.merchant.woolMaxCost == 320 && tradeDef.merchant.goods.size() == 5
							&& !tradeDef.recyclePrices.isEmpty(),
					"交易配置默认值", "羊毛 80/+4/320，商品 " + tradeDef.merchant.goods.size()
							+ " 条（第七十八批默认 5 条：附魔之瓶+回城卷轴+2 装备+药水），回收价目 " + tradeDef.recyclePrices.size() + " 项");
			try {
				java.nio.file.Files.writeString(tradeFile, """
						{
						  "configVersion": 4,
						  "merchant": {
						    "woolInitialCost": 50, "woolCostStep": 10, "woolMaxCost": 200,
						    "goods": [
						      { "type": "随机附魔书", "count": 1, "cost": 100 },
						      { "type": "diamond", "count": 3, "cost": 90 }
						    ]
						  },
						  "recyclePrices": { "oak_log": 2.5, "diamond": 25 }
						}
						""");
				var tc = com.survivalcontract.civilization.CivTradeConfig.reload();
				check(tc.merchant.woolInitialCost == 50 && tc.merchant.woolCostStep == 10
								&& tc.merchant.woolMaxCost == 200,
						"交易配置：羊毛价可自定义", "50 起步 / 每买 +10 / 上限 200");
				check(tc.merchant.goods.size() == 2
								&& tc.merchant.goods.get(1).type.equals("diamond")
								&& tc.merchant.goods.get(1).count == 3 && tc.merchant.goods.get(1).cost == 90,
						"交易配置：货物类型/数量/价格可自定义",
						"goods " + tc.merchant.goods.size() + " 条（diamond ×3 @90）");
				check(com.survivalcontract.civilization.CivTradeConfig.item("diamond",
								net.minecraft.world.item.Items.AIR) == net.minecraft.world.item.Items.DIAMOND,
						"交易配置：物品 id 解析", "diamond → minecraft:diamond");
				check(tc.recyclePrices.size() == 2 && tc.recyclePrices.get("oak_log") == 2.5
								&& tc.recyclePrices.get("diamond") == 25.0,
						"交易配置：猪灵回收价目可自定义", "oak_log=2.5 / diamond=25");
			} catch (Exception e) {
				check(false, "交易配置：临时配置读写", e.getClass().getSimpleName() + ": " + e.getMessage());
			} finally {
				try { java.nio.file.Files.delete(tradeFile); } catch (Exception ignored) {}
				com.survivalcontract.civilization.CivTradeConfig.reload(); // 缺文件 → 重新生成默认
			}
			check(com.survivalcontract.civilization.CivTradeConfig.get().merchant.woolInitialCost == 80,
					"交易配置：删除文件还原默认", "羊毛价回到 80");

			// 11) 死亡羊毛保留：本队颜色羊毛死亡必定不掉落（计数暂存）→ 重生返还背包
			var woolTeam = buyerTeam; // 与 2.02/2.3 一致（red1 实际队伍，ownWool 已声明）
			var enemyTeam = woolTeam == GameStateManager.Team.RED ? GameStateManager.Team.BLUE : GameStateManager.Team.RED;
			var enemyWool = com.survivalcontract.civilization.CivilizationManager.woolBlock(enemyTeam).asItem();
			// 清空 red1 背包已有的本队羊毛，保证返还计数从 0 开始
			var invW = red1.getInventory();
			for (int i = 0; i < invW.getContainerSize(); i++) {
				if (invW.getItem(i).is(ownWool)) invW.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
			float savedHp = red1.getHealth();
			red1.setHealth(0.0f); // 仅置 isDeadOrDying（本测试块同步执行并立即恢复，不会走 tickDeath）
			check(cv.handleDeathDrop(red1, new net.minecraft.world.item.ItemStack(ownWool, 3)),
					"死亡羊毛不掉落：本队羊毛拦截", "handleDeathDrop=true（3 个暂存）");
			check(!cv.handleDeathDrop(red1, new net.minecraft.world.item.ItemStack(enemyWool, 5)),
					"死亡羊毛正常掉落：异队羊毛", "异色羊毛不受保护");
			check(!cv.handleDeathDrop(red1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1)),
					"死亡羊毛正常掉落：普通物品", "钻石不受保护");
			red1.setHealth(Math.max(1.0f, savedHp));
			cv.onRespawn(red1, true);
			check(countItem(red1, ownWool) == 3, "死亡羊毛重生返还", "背包返还 ×3");
			cv.onRespawn(red1, true); // 暂存已清零 → 不重复返还
			check(countItem(red1, ownWool) == 3, "死亡羊毛暂存清零（无复制）", "重复 onRespawn 不再返还");

			// 11.5) 第五十一批：玩家造傀儡不索敌大本营回收猪灵（Mob.setTarget mixin 拦截）
			var pigIds = cv.recyclePiglinIdsForTest();
			check(!pigIds.isEmpty(), "回收猪灵已生成", pigIds.size() + " 只");
			if (!pigIds.isEmpty()) {
				var pigEnt = civLevel.getEntity(pigIds.get(0));
				check(pigEnt != null, "回收猪灵实体可查", pigEnt == null ? "UUID 查无实体" : "OK");
				if (pigEnt instanceof net.minecraft.world.entity.LivingEntity le) {
					var golem = new net.minecraft.world.entity.animal.golem.IronGolem(
							net.minecraft.world.entity.EntityTypes.IRON_GOLEM, civLevel);
					golem.setPlayerCreated(true); // 玩家造傀儡
					golem.setTarget(le);
					check(golem.getTarget() == null, "傀儡不索敌回收猪灵",
							"setTarget(猪灵) 被 mixin 拦截 → target=null");
					// 对照组用普通村民：26.2 Mob.setTarget 内部经 asValidTarget 过滤创造/旁观玩家
					// （测试假人为创造模式 → 字段写入 null），非实体类目标不受该过滤影响
					var probeTarget = new net.minecraft.world.entity.npc.villager.Villager(
							net.minecraft.world.entity.EntityTypes.VILLAGER, civLevel);
					golem.setTarget(probeTarget);
					check(golem.getTargetUnchecked() == probeTarget, "傀儡索敌普通目标不受影响",
							"target=对照村民（mixin 未拦截）");
					probeTarget.discard();
					golem.discard();
				}
			}

			// 12) 石英区岩浆怪：小型化（size=1 → 血量 1）+ 战利品表覆盖（原版表 size≥2 才掉岩浆奶油）
			net.minecraft.world.level.ChunkPos quartzChunk = null;
			for (int cx = -7; cx <= 7 && quartzChunk == null; cx++) {
				for (int cz = -7; cz <= 7; cz++) {
					var z = cv.zoneAt(new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz));
					if (z != null && z.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.QUARTZ) {
						quartzChunk = new net.minecraft.world.level.ChunkPos(center.x() + cx, center.z() + cz);
						break;
					}
				}
			}
			check(quartzChunk != null, "石英区存在", "setup 随机分配含石英区");
			if (quartzChunk != null) {
				var magmaType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(
						net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "magma_cube"));
				check(magmaType != null, "岩浆怪实体类型", magmaType == null ? "注册表无 magma_cube" : "OK");
				var probe = magmaType == null ? null
						: magmaType.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
				check(probe != null, "岩浆怪实体创建", probe == null ? "create 返回 null" : "OK");
				// 入世界前直接执行生成后初始化并断言（自测环境 arena 无玩家 → 同步生成的区块无
				// TICKING 状态，addFreshEntity 后实体不被 startTracking、getEntitiesOfClass 查不到）
				if (probe != null && probe instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube cube0) {
					com.survivalcontract.civilization.CivilizationManager.prepareSpawn(level,
							new net.minecraft.core.BlockPos(quartzChunk.getMinBlockX() + 8, 64,
									quartzChunk.getMinBlockZ() + 8), probe, 0);
					check(cube0.getSize() == 1, "岩浆怪小型化", "size=" + cube0.getSize() + "（应 1）");
					var hpAttr = cube0.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
					check(hpAttr != null && hpAttr.getBaseValue() == 1.0,
							"岩浆怪血量=1（修复血量异常高）",
							"maxHealth=" + (hpAttr == null ? "无属性" : hpAttr.getBaseValue()));
					var loot = cube0.getLootTable();
					check(loot.isPresent() && loot.get().identifier().toString()
									.equals("survival-contract:civilization/zone_mob"),
							"岩浆怪战利品表覆盖（修复无掉落）",
							loot.map(k -> k.identifier().toString()).orElse("原版表（size≥2 才掉）"));
					// 配置覆盖血量路径：spawnMaxHealth=5 → MAX_HEALTH 属性被覆盖
					com.survivalcontract.civilization.CivilizationManager.prepareSpawn(level,
							new net.minecraft.core.BlockPos(quartzChunk.getMinBlockX() + 8, 64,
									quartzChunk.getMinBlockZ() + 8), probe, 5.0);
					check(hpAttr != null && hpAttr.getBaseValue() == 5.0,
							"岩浆怪配置覆盖血量", "maxHealth=" + (hpAttr == null ? "无属性" : hpAttr.getBaseValue()));
					probe.discard(); // 未入世界，直接丢弃
				}
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) gsm.setScore(p.getUUID(), 0);
			// 还原 WorldBorder（civilization setup 设了 240，正式局需要默认边界）
			try {
				var border = srv.overworld().getWorldBorder();
				border.setCenter(0.5, 0.5);
				border.setSize(59999968);
			} catch (Exception ignored) {}
		}
	}

	// ==================== 第五十二批 ====================

	/**
	 * 第五十二批特性：
	 * ①文明模式自然刷怪降至 1/8 + 世界怪物总量上限（第六十五批：1/4 再砍半） ②大本营盔甲架每 4s 最多受一次伤害 ③己方 HQ 区块无限生命回复1
	 * ④文明模式禁用愚者 ⑤工匠附魔改挂装配结果（修复不易触发） ⑥箭矢补伤后不再弹开 ⑦低血糖白名单扩展
	 */
	private static void testBatch52Features() {
		var gsm = GameStateManager.getInstance();
		var pm = ProfessionManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		// 环境确定性：先跑的"第二局重开"测试会 resetPlayerTeams + startGame 重排队伍映射，
		// 假人名字与队伍不再保证对应 → 按当前映射取红/蓝队员（与 zoneAt/getPlayerTeam 同源自洽）；
		// 假人也可能残留在旁观等不可受伤模式 → 强制生存模式 + 满血，保证 ②③⑥ 可交互/可受伤
		ServerPlayer redP52 = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer blueP52 = firstInTeam(GameStateManager.Team.BLUE);
		check(redP52 != null && blueP52 != null, "第五十二批红蓝队员就位",
				"red=" + (redP52 == null ? "无" : redP52.getName().getString())
						+ " blue=" + (blueP52 == null ? "无" : blueP52.getName().getString()));
		boolean envOk52 = redP52 != null && blueP52 != null;
		if (envOk52) {
			redP52.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			blueP52.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			redP52.setHealth(redP52.getMaxHealth());
			blueP52.setHealth(blueP52.getMaxHealth());
			// 前面测试段的重生流程可能重新拉高 clientLoadedTimeoutTimer → 26.2 会把
			// 未加载客户端的玩家视为无敌，⑥ 伤害链路测试前必须再次置零
			markFakeClientLoaded(redP52);
			markFakeClientLoaded(blueP52);
		}
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var civLevel = ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();

			// ---- ⑦ 低血糖白名单（纯静态断言） ----
			check(MainEventListener.isHypoglycemiaAllowedFood("minecraft:apple")
							&& MainEventListener.isHypoglycemiaAllowedFood("minecraft:sweet_berries")
							&& MainEventListener.isHypoglycemiaAllowedFood("minecraft:glow_berries")
							&& MainEventListener.isHypoglycemiaAllowedFood("minecraft:bread"),
					"低血糖白名单扩展", "苹果/甜浆果/发光浆果/面包均放行");
			check(!MainEventListener.isHypoglycemiaAllowedFood("minecraft:cooked_beef"),
					"低血糖白名单仍拦截肉类", "熟牛肉被阻止");

			// ---- ③ 己方基地区块无限生命回复1（离开消失） ----
			cv.tick(srv); // 先 tick 一次：serverRef 就绪 + 产出计时初始化
			var center52 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var redHq52 = new net.minecraft.world.level.ChunkPos(center52.x() - 6, center52.z() - 6);
			var blueHq52 = new net.minecraft.world.level.ChunkPos(center52.x() + 6, center52.z() + 6);
			var rz52 = cv.zoneAt(redHq52);
			check(rz52 != null && rz52.woolPos != null, "红队大本营区块定位",
					rz52 == null || rz52.woolPos == null ? "HQ 区块/woolPos 缺失" : "HQ 中心 " + rz52.woolPos.toShortString());
			if (envOk52 && rz52 != null && rz52.woolPos != null) {
				BlockPos wp = rz52.woolPos;
				redP52.teleportTo(civLevel, wp.getX() + 0.5, wp.getY() + 1.0, wp.getZ() + 0.5,
						java.util.Set.of(), 0, 0, false);
				cv.tick(srv);
				var reg = redP52.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
				check(reg != null && reg.getDuration() == net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION
								&& reg.getAmplifier() == 0,
						"己方基地区块获得无限生命回复1",
						reg == null ? "无效果" : "duration=" + reg.getDuration() + " amp=" + reg.getAmplifier());
				var bz52 = cv.zoneAt(blueHq52);
				if (bz52 != null && bz52.woolPos != null) {
					BlockPos bp2 = bz52.woolPos;
					redP52.teleportTo(civLevel, bp2.getX() + 0.5, bp2.getY() + 1.0, bp2.getZ() + 0.5,
							java.util.Set.of(), 0, 0, false);
					cv.tick(srv);
					var reg2 = redP52.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
					check(reg2 == null, "离开基地区块生命回复消失",
							reg2 == null ? "已移除" : "残留 duration=" + reg2.getDuration());
				} else {
					check(false, "离开基地区块生命回复消失", "蓝队 HQ 区块缺失");
				}
			}

			// ---- ② 大本营盔甲架每 4s 最多受一次伤害 ----
			var stand52 = cv.hqStandForTest(GameStateManager.Team.RED);
			check(stand52 != null, "大本营盔甲架引用", stand52 == null ? "缺失" : "RED 架子已生成");
			if (envOk52 && stand52 != null) {
				int hp0 = cv.hqHealth(GameStateManager.Team.RED);
				boolean hit1 = cv.onHqStandAttacked(blueP52, stand52);
				int hp1 = cv.hqHealth(GameStateManager.Team.RED);
				check(hit1 && hp1 == hp0 - 5, "敌方首击扣 5 血", hp0 + " → " + hp1 + " intercepted=" + hit1);
				cv.onHqStandAttacked(blueP52, stand52);
				int hp2 = cv.hqHealth(GameStateManager.Team.RED);
				check(hp2 == hp1, "4s 冷却内再击不扣血", hp1 + " → " + hp2 + "（应不变）");
				cv.hqStandLastHitForTest(stand52.getUUID(), srv.getTickCount() - 81);
				cv.onHqStandAttacked(blueP52, stand52);
				int hp3 = cv.hqHealth(GameStateManager.Team.RED);
				check(hp3 == hp1 - 5, "冷却流逝后可再次受击", hp2 + " → " + hp3);
			}

			// ---- ⑤ 工匠附魔挂在装配结果（工作台 3x3） ----
			var savedProfRed1 = pm.getPlayerProfession(red1.getUUID());
			try {
				red1.getInventory().clearContent();
				pm.setPlayerProfession(red1.getUUID(), ProfessionManager.Profession.ARTISAN);
				var accessPos = new BlockPos(0, 80, 0);
				var menuA = new net.minecraft.world.inventory.CraftingMenu(77, red1.getInventory(),
						net.minecraft.world.inventory.ContainerLevelAccess.create(civLevel, accessPos));
				var craftA = (net.minecraft.world.inventory.CraftingContainer) menuA.getSlot(1).container;
				craftA.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftA.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftA.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftA.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
				craftA.setItem(7, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
				int enchanted52 = 0;
				for (int i = 0; i < 20; i++) {
					menuA.slotsChanged(craftA);
					var enc = menuA.getSlot(0).getItem().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
					if (enc != null && !enc.isEmpty()) enchanted52++;
				}
				check(enchanted52 > 0 && enchanted52 < 20, "工匠装配结果概率附魔（20 次重掷）",
						enchanted52 + "/20 次带附魔（概率型，期望约 60%）");
				// 重掷至结果带附魔（保证后续断言确定性），再 shift-click 取出——附魔随物品走
				for (int i = 0; i < 200; i++) {
					menuA.slotsChanged(craftA);
					var enc = menuA.getSlot(0).getItem().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
					if (enc != null && !enc.isEmpty()) break;
				}
				red1.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE));
				menuA.quickMoveStack(red1, 0);
				boolean invEnchanted52 = false;
				for (int i = 0; i < red1.getInventory().getContainerSize(); i++) {
					var s = red1.getInventory().getItem(i);
					if (s.is(net.minecraft.world.item.Items.WOODEN_PICKAXE)) {
						var enc = s.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
						if (enc != null && !enc.isEmpty()) invEnchanted52 = true;
					}
				}
				check(invEnchanted52, "工匠附魔随 shift-click 保留", "背包木镐带附魔=" + invEnchanted52);

				// 非工匠 20 次全无附魔
				pm.setPlayerProfession(red2.getUUID(), ProfessionManager.Profession.NONE);
				var menuB = new net.minecraft.world.inventory.CraftingMenu(78, red2.getInventory(),
						net.minecraft.world.inventory.ContainerLevelAccess.create(civLevel, accessPos));
				var craftB = (net.minecraft.world.inventory.CraftingContainer) menuB.getSlot(1).container;
				craftB.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftB.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftB.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
				craftB.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
				craftB.setItem(7, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
				int plain52 = 0;
				for (int i = 0; i < 20; i++) {
					menuB.slotsChanged(craftB);
					var enc = menuB.getSlot(0).getItem().get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
					if (enc != null && !enc.isEmpty()) plain52++;
				}
				check(plain52 == 0, "无职业合成不附魔", plain52 + "/20 次带附魔（应 0）");
			} finally {
				pm.setPlayerProfession(red1.getUUID(), savedProfRed1);
				red1.getInventory().clearContent();
			}

			// ---- ④ 文明模式禁用愚者（服务端强制重置） ----
			var savedProfRed1b = pm.getPlayerProfession(red1.getUUID());
			var savedPhase52 = gsm.getCurrentPhase();
			try {
				gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
				pm.setPlayerProfession(red1.getUUID(), ProfessionManager.Profession.NONE);
				NetworkRegistry.applyProfessionSelect(red1, "JESTER");
				check(pm.getPlayerProfession(red1.getUUID()) == ProfessionManager.Profession.NONE,
						"文明模式愚者被禁用", "选择 JESTER → " + pm.getPlayerProfession(red1.getUUID()));
				NetworkRegistry.applyProfessionSelect(red1, "ARCHER");
				check(pm.getPlayerProfession(red1.getUUID()) == ProfessionManager.Profession.ARCHER,
						"文明模式其他职业正常可选", "选择 ARCHER → " + pm.getPlayerProfession(red1.getUUID()));
			} finally {
				pm.setPlayerProfession(red1.getUUID(), savedProfRed1b);
				gsm.setCurrentPhase(savedPhase52);
			}

			// ---- ⑥ 弓箭命中目标后不再弹开（补伤场景，箭矢应消耗） ----
			// 26.2 中 ServerLevel.isPvpAllowed() 只看 GameRules.PVP（不看 server.properties），
			// 而前面 rule_pvp 切换用例把它关成了 false → 箭矢 owner 是玩家时，
			// ServerPlayer.hurtServer 第一步 canHarmPlayer 就返回 false（零伤害、无无敌帧特征），
			// 补伤与箭矢消耗链路全部被拦。这里恢复 PVP（NetworkRegistry 同款 API），
			// startGame 会按 gsm 状态复位，不会泄漏到后续测试段。
			for (var lvl52 : srv.getAllLevels()) {
				lvl52.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.PVP, true, srv);
			}
			// 蓝方队员传送进竞技场维度（红队 HQ 旁已加载区块），避免残留大厅维度干扰伤害链路
			var rz52b = cv.zoneAt(redHq52);
			if (envOk52 && rz52b != null && rz52b.woolPos != null) {
				BlockPos wpb = rz52b.woolPos;
				blueP52.teleportTo(civLevel, wpb.getX() + 3.5, wpb.getY() + 1.0, wpb.getZ() + 0.5,
						java.util.Set.of(), 0, 0, false);
			}
			pm.setPlayerProfession(redP52.getUUID(), ProfessionManager.Profession.NONE);
			// 传送会再次置位 clientLoadedTimeoutTimer（等待客户端加载确认）→ 必须在传送后再置零，
			// 否则 26.2 的 ServerPlayer.isInvulnerableTo 会把假人当"未加载客户端"全程免疫
			markFakeClientLoaded(redP52);
			markFakeClientLoaded(blueP52);
			var arrow52 = net.minecraft.world.entity.EntityTypes.ARROW.create(civLevel,
					net.minecraft.world.entity.EntitySpawnReason.EVENT);
			check(arrow52 != null, "箭矢实体创建", arrow52 == null ? "create 返回 null" : "OK");
			if (envOk52 && arrow52 != null) {
				arrow52.setOwner(redP52);
				arrow52.setPos(blueP52.getX(), blueP52.getY() + 1.0, blueP52.getZ());
				arrow52.setDeltaMovement(0, -1, 0);
				civLevel.addFreshEntity(arrow52);
				var srcProbe = civLevel.damageSources().arrow(arrow52, redP52);
				// 探针 A：无职业直伤——隔离"实体层可受伤性"（附全套拦截点诊断）
				boolean pvpOk52 = civLevel.isPvpAllowed();
				boolean invulTo52 = blueP52.isInvulnerableTo((net.minecraft.server.level.ServerLevel) civLevel, srcProbe);
				boolean canHarm52 = blueP52.canHarmPlayer(redP52);
				int loadTimer52 = fakeClientLoadedTimer(blueP52);
				var sb52 = civLevel.getScoreboard();
				var redT52 = sb52.getPlayersTeam(redP52.getScoreboardName());
				var blueT52 = sb52.getPlayersTeam(blueP52.getScoreboardName());
				// 26.2 isInvulnerableTo = base || 附魔伤害免疫 → 枚举蓝方装备附魔找出免疫来源
				StringBuilder eq52 = new StringBuilder();
				for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
					var st = blueP52.getItemBySlot(slot);
					if (!st.isEmpty()) {
						eq52.append(slot).append('=').append(st.getItem())
								.append(st.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS)).append(';');
					}
				}
				float hpA52 = blueP52.getHealth();
				boolean retA = blueP52.hurtOrSimulate(srcProbe, 2.0f);
				check(blueP52.getHealth() < hpA52, "⑥A 蓝方实体层可受伤（直伤）",
						"ret=" + retA + " hp " + hpA52 + "→" + blueP52.getHealth()
								+ " pvp=" + pvpOk52 + " diff=" + civLevel.getDifficulty()
								+ " invulTo=" + invulTo52 + " canHarm=" + canHarm52
								+ " redSB=" + (redT52 == null ? "null" : redT52.getName() + "/ff=" + redT52.isAllowFriendlyFire())
								+ " blueSB=" + (blueT52 == null ? "null" : blueT52.getName() + "/ff=" + blueT52.isAllowFriendlyFire())
								+ " infMat(red)=" + redP52.hasInfiniteMaterials()
								+ " infMat(blue)=" + blueP52.hasInfiniteMaterials()
								+ " loadTimer=" + loadTimer52
								+ " eq=[" + eq52 + "]"
								+ " mode=" + blueP52.gameMode.getGameModeForPlayer()
								+ " invulTime=" + com.survivalcontract.McCompat.getInvulnerableTime(blueP52));
				blueP52.setHealth(blueP52.getMaxHealth());
				com.survivalcontract.McCompat.setInvulnerableTime(blueP52, 0);
				// 探针 B：弓手补伤事件链（不经 onHitEntity）
				pm.setPlayerProfession(redP52.getUUID(), ProfessionManager.Profession.ARCHER);
				float hpB52 = blueP52.getHealth();
				boolean retB = blueP52.hurtOrSimulate(srcProbe, 2.0f);
				check(blueP52.getHealth() < hpB52, "⑥B 弓手补伤事件链",
						"ret=" + retB + " hp " + hpB52 + "→" + blueP52.getHealth());
				blueP52.setHealth(blueP52.getMaxHealth());
				com.survivalcontract.McCompat.setInvulnerableTime(blueP52, 0);
				// 探针 B 的补伤标记只在 redirect mixin 里清除，正式断言前手动清掉，
				// 确保 onHitEntity 的改判只能由本次命中的新鲜 redeal 触发
				MainEventListener.REDEALT_TARGET.remove();
				float hpB = blueP52.getHealth();
				try {
					var m = net.minecraft.world.entity.projectile.arrow.AbstractArrow.class
							.getDeclaredMethod("onHitEntity", net.minecraft.world.phys.EntityHitResult.class);
					m.setAccessible(true);
					m.invoke(arrow52, new net.minecraft.world.phys.EntityHitResult(blueP52));
				} catch (Exception e) {
					check(false, "箭矢 onHitEntity 反射调用", e.toString());
				}
				check(blueP52.getHealth() < hpB && arrow52.isRemoved(),
						"弓箭命中后不弹开（箭矢消耗）",
						"blue1 " + hpB + " → " + blueP52.getHealth() + "，arrow.isRemoved=" + arrow52.isRemoved());
				blueP52.setHealth(blueP52.getMaxHealth());
				pm.setPlayerProfession(redP52.getUUID(), ProfessionManager.Profession.NONE);
			}

			// ---- ① 自然刷怪 1/8 概率闸 + 世界怪物总量上限（第六十五批：1/4 再砍半） ----
			int pass52 = 0;
			for (int i = 0; i < 10000; i++) {
				if (com.survivalcontract.civilization.CivilizationManager.spawnChanceAllowed(civLevel)) pass52++;
			}
			check(pass52 >= 800 && pass52 <= 1800, "文明模式自然刷怪降至 1/8（第五十二批 1/4 再砍半）",
					pass52 + "/10000 放行（期望约 1250）");
			int chunks65 = civLevel.getChunkSource().getLoadedChunksCount();
			int rawCap65 = (int) (70L * chunks65 / 289L);
			int cap65 = com.survivalcontract.civilization.CivilizationManager.worldMonsterCap(civLevel);
			int cnt65 = com.survivalcontract.civilization.CivilizationManager.countWorldMonsters(civLevel);
			check(cap65 == Math.max(1, rawCap65 / 2),
					"世界怪物总量上限 = 原版刷怪容量的一半",
					"已加载区块 " + chunks65 + " → 原版容量 " + rawCap65 + " → 上限 " + cap65
							+ "（当前敌对生物 " + cnt65 + "）");
			gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			int pass52b = 0;
			for (int i = 0; i < 1000; i++) {
				if (com.survivalcontract.civilization.CivilizationManager.naturalSpawnAllowed(civLevel)) pass52b++;
			}
			check(pass52b == 1000, "非文明模式自然刷怪不受影响", pass52b + "/1000 放行（应 1000）");
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				gsm.setScore(p.getUUID(), 0);
				p.getInventory().clearContent();
				p.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
			}
			try {
				var border = srv.overworld().getWorldBorder();
				border.setCenter(0.5, 0.5);
				border.setSize(59999968);
			} catch (Exception ignored) {}
		}
	}

	// ==================== 第五十三批 ====================
	/**
	 * 第五十三批特性（图1 布局 + 图2 设施产出 + 资源区守护怪）：
	 * ①图1 固定布局：HQ2 / 木16 / 石54 / 钻14 / 合金3 / 空白136（第五十五批修订：空白按配额精确放置）；
	 * ②配额表 沙8/铜34/铁24/金20 + 剩余随机池 56 项（石英24/青金石16/野生16），不含配额四类与木/石/钻/合金/大本营；
	 * ③守护怪：钻石区=已驯服僵尸马+骑乘僵尸（铁头盔、铁长矛）、合金区=骆驼尸壳（固定双乘客：金长矛尸壳+焦骸），
	 *   全体 civil_guardian 标记 + 抗性提升2（无限）+ 区块守护登记全覆盖；
	 * ④守护存在 → 拒绝放置占领羊毛；击败守护 → 登记清理、拦截解除；
	 * ⑤图2 配置：各区产出/前置（木块非原木、粗铁/粗金、铜箱子家族互认、木头家族匹配、末影箱前置、
	 *   青金石区熔炉 Y≤0）+ 功能方块锁定扩展（铜/铁/金块、末影箱）。
	 */
	private static void testBatch53Features() {
		var gsm = GameStateManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		ServerPlayer redP53 = firstInTeam(GameStateManager.Team.RED);
		check(redP53 != null, "第五十三批红队队员就位",
				redP53 == null ? "无" : redP53.getName().getString());
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var civLevel = ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();

			// ---- ①a 图1 布局表（静态） ----
			var lc53 = new java.util.HashMap<String, Integer>();
			for (int r = 0; r < 15; r++) {
				for (int c = 0; c < 15; c++) {
					var t = com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(r, c);
					lc53.merge(t == null ? "BLANK" : t.name(), 1, Integer::sum);
				}
			}
			check(lc53.getOrDefault("WOOD", 0) == 16 && lc53.getOrDefault("STONE", 0) == 54
							&& lc53.getOrDefault("DIAMOND", 0) == 14 && lc53.getOrDefault("ALLOY", 0) == 3
							&& lc53.getOrDefault("HQ", 0) == 2 && lc53.getOrDefault("BLANK", 0) == 136,
					"图1 布局表固定格数量（木16/石54/钻14/合金3/家2/空白136）", lc53.toString());
			check(com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(0, 14)
							== com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY
							&& com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(7, 7)
							== com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY
							&& com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(14, 0)
							== com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY,
					"合金区固定三格 (0,14)/(7,7)/(14,0)", "OK");
			check(com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(1, 1)
							== com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ
							&& com.survivalcontract.civilization.CivilizationManager.fixedLayoutForTest(13, 13)
							== com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ,
					"两家大本营固定 (1,1)/(13,13)（家=基地）", "OK");

			// ---- ② 空白区配额表 + 剩余随机池（第五十五批修订） ----
			var qt53 = com.survivalcontract.civilization.CivilizationManager.zoneQuotasForTest();
			check(qt53.size() == 4
							&& Integer.valueOf(8).equals(qt53.get(com.survivalcontract.civilization.CivilizationManager.ZoneType.SAND))
							&& Integer.valueOf(34).equals(qt53.get(com.survivalcontract.civilization.CivilizationManager.ZoneType.COPPER))
							&& Integer.valueOf(24).equals(qt53.get(com.survivalcontract.civilization.CivilizationManager.ZoneType.IRON))
							&& Integer.valueOf(20).equals(qt53.get(com.survivalcontract.civilization.CivilizationManager.ZoneType.GOLD)),
					"空白区配额表=沙8/铜34/铁24/金20（精确数量）", qt53.toString());
			var pool53 = com.survivalcontract.civilization.CivilizationManager.zoneFreightPoolForTest();
			var pc53 = new java.util.HashMap<String, Integer>();
			for (var t : pool53) pc53.merge(t.name(), 1, Integer::sum);
			check(pool53.size() == 56 && pc53.getOrDefault("QUARTZ", 0) == 24
							&& pc53.getOrDefault("LAPIS", 0) == 16 && pc53.getOrDefault("WILDLIFE", 0) == 16,
					"剩余空白随机池=图2 权重 56 项（石英24/青金石16/野生16）", pc53.toString());
			check(!pc53.containsKey("SAND") && !pc53.containsKey("COPPER") && !pc53.containsKey("IRON")
							&& !pc53.containsKey("GOLD") && !pc53.containsKey("WOOD") && !pc53.containsKey("STONE")
							&& !pc53.containsKey("DIAMOND") && !pc53.containsKey("ALLOY") && !pc53.containsKey("HQ"),
					"随机池不含沙/铜/铁/金/木头/石头/钻石/合金/大本营", "OK");

			// ---- ①b 开局实际区块类型分布 + 守护登记全覆盖 ----
			var center53 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var live53 = new java.util.HashMap<String, Integer>();
			var randTypes53 = new java.util.HashSet<String>();
			var guarded53 = new ArrayList<net.minecraft.world.level.ChunkPos>();
			for (int cx = -7; cx <= 7; cx++) {
				for (int cz = -7; cz <= 7; cz++) {
					var cp53 = new net.minecraft.world.level.ChunkPos(center53.x() + cx, center53.z() + cz);
					var z53 = cv.zoneAt(cp53);
					if (z53 == null) continue;
					live53.merge(z53.type.name(), 1, Integer::sum);
					if (z53.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.DIAMOND
							|| z53.type == com.survivalcontract.civilization.CivilizationManager.ZoneType.ALLOY) {
						guarded53.add(cp53);
					} else if (z53.type != com.survivalcontract.civilization.CivilizationManager.ZoneType.HQ
							&& z53.type != com.survivalcontract.civilization.CivilizationManager.ZoneType.WOOD
							&& z53.type != com.survivalcontract.civilization.CivilizationManager.ZoneType.STONE) {
						randTypes53.add(z53.type.name());
					}
				}
			}
			check(live53.getOrDefault("HQ", 0) == 2 && live53.getOrDefault("WOOD", 0) == 16
							&& live53.getOrDefault("STONE", 0) == 54 && live53.getOrDefault("DIAMOND", 0) == 14
							&& live53.getOrDefault("ALLOY", 0) == 3
							&& live53.getOrDefault("SAND", 0) == 8 && live53.getOrDefault("COPPER", 0) == 34
							&& live53.getOrDefault("IRON", 0) == 24 && live53.getOrDefault("GOLD", 0) == 20,
					"开局实际分布符合图1+第五十五批配额（沙8/铜34/铁24/金20 精确）", live53.toString());
			int freightCnt53 = live53.getOrDefault("QUARTZ", 0) + live53.getOrDefault("LAPIS", 0)
					+ live53.getOrDefault("WILDLIFE", 0);
			check(freightCnt53 == 50, "剩余空白随机区（石英/青金石/野生）共 50 格", "实际 " + freightCnt53);
			var allowedRand53 = Set.of("SAND", "QUARTZ", "COPPER", "IRON", "GOLD", "LAPIS", "WILDLIFE");
			check(allowedRand53.containsAll(randTypes53) && !randTypes53.isEmpty(),
					"空白区只出现图2 的 7 种类型", randTypes53.toString());
			int missingGuard53 = 0;
			for (var g53 : guarded53) {
				var ids53 = cv.guardiansForTest(g53);
				if (ids53 == null || ids53.isEmpty()) missingGuard53++;
			}
			check(guarded53.size() == 17 && missingGuard53 == 0,
					"全部钻石/合金区块（17 个）已登记守护怪", "缺 " + missingGuard53 + "/" + guarded53.size());

			// ---- ③ 僵尸马骑兵（钻石区守护，图1 (8,8) → 中心偏移 (+1,+1)） ----
			var dcp53 = new net.minecraft.world.level.ChunkPos(center53.x() + 1, center53.z() + 1);
			var zr53 = cv.spawnGuardian(civLevel, dcp53, true);
			check(zr53.size() == 2, "僵尸马骑兵=坐骑+骑手", "spawned=" + zr53.size());
			if (zr53.size() == 2) {
				var mount53 = zr53.get(0);
				var rider53 = zr53.get(1);
				check(mount53 instanceof net.minecraft.world.entity.animal.equine.ZombieHorse
								&& mount53 instanceof net.minecraft.world.entity.animal.equine.AbstractHorse ah53
								&& ah53.isTamed(),
						"坐骑=已驯服僵尸马（原版骑兵构成）",
						mount53 instanceof net.minecraft.world.entity.animal.equine.ZombieHorse
								&& mount53 instanceof net.minecraft.world.entity.animal.equine.AbstractHorse ah53b
								&& ah53b.isTamed() ? "OK" : "type=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mount53.getType())
								+ " tamed=" + (mount53 instanceof net.minecraft.world.entity.animal.equine.AbstractHorse ah53c && ah53c.isTamed()));
				check(rider53 instanceof net.minecraft.world.entity.monster.zombie.Zombie
								&& mount53.getFirstPassenger() == rider53,
						"僵尸骑手骑乘中", "OK");
				check(rider53.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
								.is(net.minecraft.world.item.Items.IRON_HELMET),
						"骑手戴铁头盔", "head=" + rider53.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem());
				check(rider53.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
								.is(net.minecraft.world.item.Items.IRON_SPEAR),
						"僵尸骑手手持铁长矛（第五十七批）",
						"main=" + rider53.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).getItem());
			}
			for (var m53 : zr53) {
				var eff53 = m53.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
				check(m53.entityTags().contains(com.survivalcontract.civilization.CivilizationManager.GUARDIAN_TAG)
								&& eff53 != null && eff53.getAmplifier() == 1
								&& eff53.getDuration() == net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION,
						"守护实体（僵尸马骑兵）带 civil_guardian+抗性提升2（无限）",
						"tag=" + m53.entityTags().contains(com.survivalcontract.civilization.CivilizationManager.GUARDIAN_TAG)
								+ " eff=" + (eff53 == null ? "无" : "amp=" + eff53.getAmplifier() + " dur=" + eff53.getDuration()));
			}

			// ---- ③b 骆驼尸壳骑兵（合金区守护，图1 (7,7) = 出生区块；第五十七批修订：固定双乘客） ----
			var acp53 = new net.minecraft.world.level.ChunkPos(center53.x(), center53.z());
			var ch53 = cv.spawnGuardian(civLevel, acp53, false);
			check(ch53.size() == 3, "骆驼尸壳骑兵=坐骑+金长矛尸壳+焦骸（3 实体）", "spawned=" + ch53.size());
			if (!ch53.isEmpty()) {
				var camel53 = ch53.get(0);
				check(camel53 instanceof net.minecraft.world.entity.animal.camel.CamelHusk,
						"坐骑=骆驼尸壳（原版生成方式）",
						camel53 instanceof net.minecraft.world.entity.animal.camel.CamelHusk ? "OK"
								: "type=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(camel53.getType()));
				check(camel53.getFirstPassenger() instanceof net.minecraft.world.entity.monster.zombie.Husk,
						"尸壳骑手骑乘中（手持金长矛）",
						camel53.getFirstPassenger() == null ? "无骑手"
								: camel53.getFirstPassenger().getType().toString());
				check(camel53.getPassengers().size() == 2
								&& camel53.getPassengers().stream().anyMatch(px -> px instanceof
										net.minecraft.world.entity.monster.skeleton.Parched),
						"骆驼尸壳背上固定两个怪物（金长矛尸壳+焦骸）",
						"passengers=" + camel53.getPassengers().size());
				for (var m53 : ch53) {
					var eff53 = m53.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
					check(m53.entityTags().contains(com.survivalcontract.civilization.CivilizationManager.GUARDIAN_TAG)
									&& eff53 != null && eff53.getAmplifier() == 1
									&& eff53.getDuration() == net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION,
							"守护实体（骆驼尸壳骑兵）带 civil_guardian+抗性提升2（无限）",
							"tag=" + m53.entityTags().contains(com.survivalcontract.civilization.CivilizationManager.GUARDIAN_TAG)
									+ " eff=" + (eff53 == null ? "无" : "amp=" + eff53.getAmplifier()));
				}
			}

			// ---- ④ 守护拦截羊毛放置 + 击败后解除（区块内登记由 contains 驱动） ----
			if (redP53 != null && zr53.size() == 2) {
				var savedGuard53 = cv.guardiansForTest(dcp53) == null ? null : new HashSet<>(cv.guardiansForTest(dcp53));
				var mine53 = new HashSet<UUID>();
				for (var m53 : zr53) mine53.add(m53.getUUID());
				cv.setGuardiansForTest(dcp53, mine53);
				String deny53 = cv.validateWoolPlace(redP53, dcp53);
				check(deny53 != null && deny53.contains("守护"),
						"守护存在时拒绝放置占领羊毛", deny53 == null ? "null（未拦截）" : deny53);
				for (var m53 : zr53) {
					m53.kill(civLevel);
				}
				cv.containMobsForTest(civLevel); // 测试阶段未开局（tick 直接返回）——直接驱动死亡清理与解锁
				var after53 = cv.guardiansForTest(dcp53);
				check(after53 == null || after53.isEmpty(),
						"击败守护后区块守护登记清理", after53 == null ? "无登记" : "剩 " + after53.size() + " 个");
				String deny53b = cv.validateWoolPlace(redP53, dcp53);
				check(deny53b == null || !deny53b.contains("守护"),
						"击败守护后守护拦截解除", deny53b == null ? "null（可放置）" : deny53b);
				cv.setGuardiansForTest(dcp53, savedGuard53); // 还原开局守护登记
			}

			// ---- ⑤a 前置家族匹配：铜箱子家族互认 + 木头家族（原木不算） ----
			var chestFam53 = net.minecraft.world.level.block.Blocks.COPPER_CHEST.asList();
			var copperFam53 = net.minecraft.world.level.block.Blocks.COPPER_BLOCK.asList();
			try {
				var mPBM53 = com.survivalcontract.civilization.CivilizationManager.class.getDeclaredMethod(
						"prereqBlockMatches", net.minecraft.world.level.block.state.BlockState.class,
						net.minecraft.world.level.block.Block.class);
				mPBM53.setAccessible(true);
				boolean fam53a = (Boolean) mPBM53.invoke(null, chestFam53.get(0).defaultBlockState(),
						chestFam53.get(chestFam53.size() - 1));
				boolean fam53b = (Boolean) mPBM53.invoke(null, chestFam53.get(chestFam53.size() - 1).defaultBlockState(),
						chestFam53.get(0));
				check(chestFam53.size() == 8 && fam53a && fam53b,
						"铜箱子家族互认（4 氧化阶段 × 涂蜡 全兼容）",
						"family=" + chestFam53.size() + " match=" + fam53a + "/" + fam53b);
				boolean log53 = (Boolean) mPBM53.invoke(null,
						net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(),
						net.minecraft.world.level.block.Blocks.OAK_WOOD);
				boolean spruce53 = (Boolean) mPBM53.invoke(null,
						net.minecraft.world.level.block.Blocks.SPRUCE_WOOD.defaultBlockState(),
						net.minecraft.world.level.block.Blocks.OAK_WOOD);
				check(!log53 && spruce53, "木头家族匹配（任意 *_wood 兼容，原木不算）",
						"oak_log=" + log53 + " spruce_wood=" + spruce53);
			} catch (Exception e53) {
				check(false, "前置家族匹配反射测试", e53.toString());
			}

			// ---- ⑤b 功能方块锁定扩展：铜/铁/金块、末影箱、铜箱子家族 ----
			boolean func53 = com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							net.minecraft.world.level.block.Blocks.ENDER_CHEST.defaultBlockState())
					&& com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState())
					&& com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState())
					&& com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							copperFam53.get(0).defaultBlockState())
					&& com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							chestFam53.get(0).defaultBlockState())
					&& !com.survivalcontract.civilization.CivilizationManager.isFunctionalBlock(
							net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
			check(func53, "功能方块锁定：末影箱/铁块/金块/铜块家族/铜箱子家族（木板不锁）", "OK");

			// ---- ⑤c 图2 产出配置 ----
			var cfg53 = com.survivalcontract.civilization.CivilizationProductionConfig.reload();
			check(cfg53.configVersion == 6, "产出配置版本 v6", "configVersion=" + cfg53.configVersion);
			var wood53 = cfg53.zones.get("WOOD");
			check(wood53 != null && "oak_log".equals(wood53.output) && wood53.count == 8
							&& (wood53.requireBlocks == null || wood53.requireBlocks.isEmpty()),
					"木头区产出 oak_log×8 无前置（橡木原木）",
					wood53 == null ? "缺失" : wood53.output + "×" + wood53.count);
			var stone53 = cfg53.zones.get("STONE");
			var stoneReq53 = reqNorm53(stone53);
			check(stone53 != null && "cobblestone".equals(stone53.output) && stone53.count == 8
							&& stoneReq53.equals(List.of("oak_wood")),
					"石头区圆石×8 前置橡木木头（六面皮木块）", stone53 == null ? "缺失" : stone53.output + " req=" + stoneReq53);
			check(reqNorm53(cfg53.zones.get("COPPER")).equals(List.of("furnace", "coal_block")),
					"铜区前置=熔炉+煤炭块", reqNorm53(cfg53.zones.get("COPPER")).toString());
			var iron53 = cfg53.zones.get("IRON");
			check(iron53 != null && "iron_ingot".equals(iron53.output) && iron53.count == 8
							&& reqNorm53(iron53).equals(List.of("furnace", "copper_block", "waxed_copper_chest")),
					"铁区铁锭×8 前置=熔炉+铜块+涂蜡的铜箱子", iron53 == null ? "缺失" : iron53.output + " req=" + reqNorm53(iron53));
			var gold53 = cfg53.zones.get("GOLD");
			check(gold53 != null && "gold_ingot".equals(gold53.output) && gold53.count == 8
							&& reqNorm53(gold53).equals(List.of("blast_furnace", "iron_block", "waxed_copper_chest")),
					"金区金锭×8 前置=高炉+铁块+涂蜡的铜箱子", gold53 == null ? "缺失" : gold53.output + " req=" + reqNorm53(gold53));
			check(reqNorm53(cfg53.zones.get("DIAMOND")).equals(List.of("blast_furnace", "gold_block", "waxed_copper_chest")),
					"钻石区前置=高炉+金块+涂蜡的铜箱子", reqNorm53(cfg53.zones.get("DIAMOND")).toString());
			var quartz53 = cfg53.zones.get("QUARTZ");
			boolean quartzBlaze53 = quartz53 != null && quartz53.bonuses.stream()
					.flatMap(b -> b.items.stream()).anyMatch(s -> s.contains("blaze_rod"));
			check(quartz53 != null && reqNorm53(quartz53).equals(List.of("furnace")) && !quartzBlaze53,
					"石英区前置=熔炉（无烈焰棒奖励）", quartz53 == null ? "缺失" : "req=" + reqNorm53(quartz53) + " blaze=" + quartzBlaze53);
			var lapis53 = cfg53.zones.get("LAPIS");
			var lapisReq53 = reqNorm53(lapis53);
			check(lapis53 != null && lapisReq53.contains("furnace") && lapisReq53.contains("ender_chest"),
					"青金石区前置含熔炉（Y≤0）+末影箱", lapis53 == null ? "缺失" : lapisReq53.toString());
			var alloyItems53 = new java.util.HashSet<String>();
			var alloy53 = cfg53.zones.get("ALLOY");
			if (alloy53 != null) for (var b53 : alloy53.bonuses) alloyItems53.addAll(b53.items);
			check(reqNorm53(alloy53).equals(List.of("blast_furnace", "diamond_block", "waxed_copper_chest"))
							&& alloyItems53.contains("blaze_rod")
							&& alloyItems53.contains("netherite_upgrade_smithing_template")
							&& alloyItems53.contains("netherrack"),
					"合金区前置=高炉+钻石块+涂蜡的铜箱子，产出烈焰棒+下界合金升级模板+下界岩",
					alloy53 == null ? "缺失" : "req=" + reqNorm53(alloy53) + " bonus=" + alloyItems53);
			check(reqNorm53(cfg53.zones.get("SAND")).equals(List.of("ender_chest")),
					"沙区前置=末影箱", reqNorm53(cfg53.zones.get("SAND")).toString());
			boolean sandGlass53 = false;
			var sand53 = cfg53.zones.get("SAND");
			if (sand53 != null) for (var b53 : sand53.bonuses) if (b53.items.contains("glass")) sandGlass53 = true;
			check(sandGlass53, "沙区副产品含玻璃", "glass=" + sandGlass53);

			// ---- ⑤d 青金石区熔炉 Y≤0 规则（prereqMet 反射：注入设施+等级） ----
			try {
				var hqCp53 = new net.minecraft.world.level.ChunkPos(center53.x() - 6, center53.z() - 6);
				int sx53 = hqCp53.getMinBlockX() + 8, sz53 = hqCp53.getMinBlockZ() + 8;
				int sy53 = civLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						sx53, sz53);
				var furHi53 = new BlockPos(sx53, sy53 + 1, sz53); // 地表（Y>0）
				var endHi53 = new BlockPos(sx53 + 1, sy53 + 1, sz53);
				var furLow53 = new BlockPos(sx53, -10, sz53); // 深层（Y≤0）
				civLevel.setBlock(furHi53, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(), 3);
				civLevel.setBlock(endHi53, net.minecraft.world.level.block.Blocks.ENDER_CHEST.defaultBlockState(), 3);
				civLevel.setBlock(furLow53, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(), 3);
				var fFac53 = com.survivalcontract.civilization.CivilizationManager.class.getDeclaredField("facilities");
				fFac53.setAccessible(true);
				@SuppressWarnings("unchecked")
				var fac53 = (java.util.Map<net.minecraft.world.level.ChunkPos, java.util.Set<BlockPos>>) fFac53.get(cv);
				var savedFac53 = fac53.put(hqCp53, java.util.Set.of(furHi53, endHi53)); // 第一轮只放地表熔炉+末影箱（深层熔炉会误满足 Y≤0 匹配）
				var fCached53 = com.survivalcontract.civilization.CivilizationManager.class.getDeclaredField("cachedLevel");
				fCached53.setAccessible(true);
				fCached53.set(cv, civLevel);
				var mPM53 = com.survivalcontract.civilization.CivilizationManager.class.getDeclaredMethod(
						"prereqMet", net.minecraft.world.level.ChunkPos.class,
						com.survivalcontract.civilization.CivilizationManager.ZoneType.class);
				mPM53.setAccessible(true);
				boolean deep53a = (Boolean) mPM53.invoke(cv, hqCp53,
						com.survivalcontract.civilization.CivilizationManager.ZoneType.LAPIS);
				check(!deep53a, "青金石区地表熔炉不满足 Y≤0 前置（不产出）", "prereqMet=" + deep53a);
				fac53.put(hqCp53, java.util.Set.of(furLow53, endHi53));
				boolean deep53b = (Boolean) mPM53.invoke(cv, hqCp53,
						com.survivalcontract.civilization.CivilizationManager.ZoneType.LAPIS);
				check(deep53b, "青金石区 Y≤0 熔炉+末影箱满足前置", "prereqMet=" + deep53b);
				if (savedFac53 == null) fac53.remove(hqCp53);
				else fac53.put(hqCp53, savedFac53);
				civLevel.setBlock(furHi53, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				civLevel.setBlock(endHi53, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				civLevel.setBlock(furLow53, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			} catch (Exception e53) {
				check(false, "青金石熔炉 Y≤0 规则反射测试", e53.toString());
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
		}
	}

	// ==================== 第五十七批 ====================

	/**
	 * 第五十七批特性（守护者构成修订 + 占领解锁修复 + 作弊加分指令）：
	 * ①僵尸骑兵=骑僵尸马+手持铁长矛；②骆驼尸壳背上固定两个怪物——持金长矛的尸壳+焦骸（Parched）；
	 * ③全体守护怪无限时长抗性提升2+迅捷2；④守护者越出守护区块即被拉回；
	 * ⑤守护实体彻底消失（尸体已移除/kill）后守护登记自愈清理——修复"击败后仍无法占领"的永久锁死；
	 * ⑥/sc addscore 作弊加分核心：常规模式拒绝执行（分数不变），文明模式生效。
	 */
	private static void testBatch57Features() {
		var gsm = GameStateManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		ServerPlayer redP57 = firstInTeam(GameStateManager.Team.RED);
		check(redP57 != null, "第五十七批红队队员就位",
				redP57 == null ? "无" : redP57.getName().getString());
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var civLevel = ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();
			var center57 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var dcp57 = new net.minecraft.world.level.ChunkPos(center57.x() + 1, center57.z() + 1);

			// ---- ① 僵尸骑兵：骑僵尸马 + 手持铁长矛（沿用第五十三批 (8,8) 钻石格） ----
			var zr57 = cv.spawnGuardian(civLevel, dcp57, true);
			check(zr57.size() == 2, "第五十七批 僵尸骑兵=坐骑+骑手", "spawned=" + zr57.size());
			if (zr57.size() == 2) {
				var rider57 = zr57.get(1);
				check(rider57.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
								.is(net.minecraft.world.item.Items.IRON_SPEAR),
						"僵尸骑兵手持铁长矛（骑僵尸马）",
						"main=" + rider57.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).getItem());
			}

			// ---- ② 骆驼尸壳：背上固定两个怪物（金长矛尸壳 + 焦骸） ----
			var acp57 = new net.minecraft.world.level.ChunkPos(center57.x(), center57.z());
			var ch57 = cv.spawnGuardian(civLevel, acp57, false);
			check(ch57.size() == 3, "第五十七批 骆驼尸壳=坐骑+金长矛尸壳+焦骸", "spawned=" + ch57.size());
			if (ch57.size() == 3) {
				var camel57 = ch57.get(0);
				var husk57 = ch57.get(1);
				var parched57 = ch57.get(2);
				check(camel57 instanceof net.minecraft.world.entity.animal.camel.CamelHusk
								&& husk57 instanceof net.minecraft.world.entity.monster.zombie.Husk
								&& parched57 instanceof net.minecraft.world.entity.monster.skeleton.Parched,
						"骆驼尸壳+持金长矛尸壳+焦骸 实体类型",
						"types=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(camel57.getType())
								+ "/" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(husk57.getType())
								+ "/" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(parched57.getType()));
				check(camel57.getPassengers().size() == 2 && camel57.getFirstPassenger() == husk57,
						"骆驼尸壳背上固定两个怪物（尸壳骑手+焦骸同骑）",
						"passengers=" + camel57.getPassengers().size());
				check(husk57.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
								.is(net.minecraft.world.item.Items.GOLDEN_SPEAR),
						"尸壳骑手手持金长矛",
						"main=" + husk57.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).getItem());
			}

			// ---- ③ 全体守护怪：无限时长抗性提升2 + 迅捷2 ----
			boolean allRes57 = true;
			boolean allSpd57 = true;
			for (var m57 : zr57) {
				var res57 = m57.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
				var spd57 = m57.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
				if (res57 == null || res57.getAmplifier() != 1
						|| res57.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) allRes57 = false;
				if (spd57 == null || spd57.getAmplifier() != 1
						|| spd57.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) allSpd57 = false;
			}
			for (var m57 : ch57) {
				var res57 = m57.getEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
				var spd57 = m57.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
				if (res57 == null || res57.getAmplifier() != 1
						|| res57.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) allRes57 = false;
				if (spd57 == null || spd57.getAmplifier() != 1
						|| spd57.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) allSpd57 = false;
			}
			check(allRes57 && !zr57.isEmpty() && !ch57.isEmpty(),
					"守护怪全员无限时长抗性提升2", "ok=" + allRes57);
			check(allSpd57 && !zr57.isEmpty() && !ch57.isEmpty(),
					"守护怪全员无限时长迅捷2（第五十七批）", "ok=" + allSpd57);

			// ---- ④ 守护者禁足：越出守护区块 → 拉回 ----
			if (zr57.size() == 2) {
				var mount57 = zr57.get(0);
				int ox57 = dcp57.getMinBlockX() + 2, oz57 = dcp57.getMinBlockZ() + 2;
				mount57.absSnapTo(ox57 + 20, mount57.getY(), oz57 + 20, 0, 0); // 移出 16 格区块边界
				check(!net.minecraft.world.level.ChunkPos.containing(mount57.blockPosition()).equals(dcp57),
						"前置：守护坐骑已被移出所属区块", "at=" + mount57.blockPosition());
				cv.containMobsForTest(civLevel); // 测试阶段未开局（tick 直接返回）——手动驱动拉回
				check(net.minecraft.world.level.ChunkPos.containing(mount57.blockPosition()).equals(dcp57),
						"守护者越界后被拉回守护区块", "at=" + mount57.blockPosition());
			}

			// ---- ⑤ 击败后可占领（第五十七批修复"永久锁死"） ----
			if (redP57 != null && zr57.size() == 2) {
				var savedGuard57 = cv.guardiansForTest(dcp57) == null ? null : new HashSet<>(cv.guardiansForTest(dcp57));
				var mine57 = new HashSet<UUID>();
				for (var m57 : zr57) mine57.add(m57.getUUID());
				cv.setGuardiansForTest(dcp57, mine57);
				String deny57a = cv.validateWoolPlace(redP57, dcp57);
				check(deny57a != null && deny57a.contains("守护"),
						"前置：守护登记在位时拒绝放置占领羊毛", deny57a == null ? "null（未拦截）" : deny57a);
				for (var m57 : zr57) m57.discard(); // 模拟守护实体彻底消失（尸体已移除/kill，containMobs 未来得及观察）
				cv.containMobsForTest(civLevel);
				var after57 = cv.guardiansForTest(dcp57);
				check(after57 == null || after57.isEmpty(),
						"守护实体彻底消失后守护登记自愈清理（修复永久锁死根因）",
						after57 == null ? "无登记" : "剩 " + after57.size() + " 个");
				String deny57b = cv.validateWoolPlace(redP57, dcp57);
				check(deny57b == null || !deny57b.contains("守护"),
						"击败守护后守护拦截解除（可占领）", deny57b == null ? "null（可放置）" : deny57b);
				// ⑤b 陈旧 UUID（实体早已不存在）直接复现老 bug 场景
				var stale57 = new HashSet<UUID>();
				stale57.add(java.util.UUID.randomUUID());
				cv.setGuardiansForTest(dcp57, stale57);
				String deny57c = cv.validateWoolPlace(redP57, dcp57);
				check(deny57c == null || !deny57c.contains("守护"),
						"陈旧守护登记（实体早已消失）不再永久锁死区块", deny57c == null ? "null（可放置）" : deny57c);
				cv.setGuardiansForTest(dcp57, savedGuard57); // 还原开局守护登记
			}

			// ---- ⑥ /sc addscore 作弊加分：常规模式拒绝 / 文明模式生效 ----
			if (redP57 != null) {
				int savedScore57 = gsm.getScore(redP57.getUUID());
				gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
				int denyR57 = com.survivalcontract.command.ScCommands.addScoreCore(redP57, redP57, 50, srv);
				check(denyR57 == 0 && gsm.getScore(redP57.getUUID()) == savedScore57,
						"常规模式下作弊加分无效（拒绝执行、分数不变）",
						"r=" + denyR57 + " score=" + gsm.getScore(redP57.getUUID()) + "/应为 " + savedScore57);
				gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
				int okR57 = com.survivalcontract.command.ScCommands.addScoreCore(redP57, redP57, 50, srv);
				check(okR57 == 1 && gsm.getScore(redP57.getUUID()) == savedScore57 + 50,
						"文明模式下 OP 作弊加分 +50 生效",
						"r=" + okR57 + " score=" + gsm.getScore(redP57.getUUID()));
				gsm.setScore(redP57.getUUID(), savedScore57); // 还原分数
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
		}
	}

	// ==================== 第六十批 ====================

	/**
	 * 第六十批特性：文明守护怪白天不自燃——
	 * ①僵尸马骑兵等守护怪生成即挂无限时长火焰免疫；②点燃守护坐骑 → 每 tick 火焰抑制立即扑灭
	 * （白天自燃视觉与伤害双消除，无限火抗兜底）；③骆驼尸壳守护同款火焰免疫。
	 */
	private static void testBatch60Features() {
		var gsm = GameStateManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		try {
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			var civLevel = ArenaWorldManager.getArenaOverworld(srv);
			if (civLevel == null) civLevel = srv.overworld();
			var center60 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
			var dcp60 = new net.minecraft.world.level.ChunkPos(center60.x() + 2, center60.z() + 2);

			// ---- ① 僵尸马骑兵：坐骑+骑手生成即带无限火焰免疫 ----
			var zr60 = cv.spawnGuardian(civLevel, dcp60, true);
			check(zr60.size() == 2, "前置：僵尸马骑兵生成", "spawned=" + zr60.size());
			boolean allFr60 = true;
			for (var m60 : zr60) {
				var fr60 = m60.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
				if (fr60 == null
						|| fr60.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) allFr60 = false;
			}
			check(allFr60 && !zr60.isEmpty(), "守护怪全员无限时长火焰免疫（生成即挂）", "ok=" + allFr60);

			// ---- ② 点燃守护坐骑 → 每 tick 火焰抑制立即扑灭 ----
			if (zr60.size() == 2) {
				var mount60 = zr60.get(0);
				mount60.setRemainingFireTicks(60); // 模拟阳光点燃（3 秒）
				check(mount60.isOnFire(), "前置：守护坐骑已点燃", "fireTicks=" + mount60.getRemainingFireTicks());
				cv.sweepGuardianFireForTest(civLevel);
				check(!mount60.isOnFire(), "守护怪白天自燃被抑制（每 tick 扑灭）",
						"fireTicks=" + mount60.getRemainingFireTicks());
			}

			// ---- ③ 骆驼尸壳守护同款火焰免疫 ----
			var acp60 = new net.minecraft.world.level.ChunkPos(center60.x(), center60.z() + 2);
			var ch60 = cv.spawnGuardian(civLevel, acp60, false);
			boolean camelFr60 = true;
			for (var m60 : ch60) {
				var fr60 = m60.getEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
				if (fr60 == null
						|| fr60.getDuration() != net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION) camelFr60 = false;
			}
			check(camelFr60 && !ch60.isEmpty(), "骆驼尸壳守护全员火焰免疫",
					"spawned=" + ch60.size() + " ok=" + camelFr60);

			// 清理本批测试守护实体（cv.reset 清登记，实体显式丢弃防残留）
			for (var m60 : zr60) m60.discard();
			for (var m60 : ch60) m60.discard();
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
		}
	}

	// ==================== 第五十四批 ====================

	/**
	 * 第五十四批特性：
	 * ①神的谎言：初始总分 100 / 使者神罚 55（数值断言已并入 testGodsLies）/ 单人最多三种疾病
	 * （随机分配 ≤3 抽样 + 手动开启上限校验）；②乐师音波攻击半径 16 格 + 队友免伤；
	 * ③文明模式全局队内友伤关闭（记分板队伍 ff=false + canHarmPlayer 探针）。
	 */
	private static void testBatch54Features() {
		var gsm = GameStateManager.getInstance();
		var cm = ChallengeManager.getInstance();
		var cv = com.survivalcontract.civilization.CivilizationManager.getInstance();
		var pm54 = ProfessionManager.getInstance();
		var srv = server.get();
		var savedPhase = gsm.getCurrentPhase();
		var savedStyle = gsm.getPlayStyle();
		// 快照各假人规则集：疾病开关测试会改写规则集，finally 整体还原（不影响后续 startGame）
		java.util.Map<UUID, java.util.Set<String>> savedChallenges = new java.util.HashMap<>();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			savedChallenges.put(p.getUUID(), new java.util.HashSet<>(cm.getPlayerChallenges(p.getUUID())));
		}
		try {
			// ---- ① 神的谎言：随机分配总分恰好 100 + 疾病 ≤3（50 次抽样） ----
			gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
			gsm.resetGodsLies();
			java.util.Random rnd54 = new java.util.Random(42);
			boolean sumOk54 = true, disOk54 = true;
			for (int i = 0; i < 50 && sumOk54 && disOk54; i++) {
				int sum54 = 0, disCnt54 = 0;
				for (String id : cm.randomRuleSetForScore(100, rnd54)) {
					var c = cm.getChallenge(id);
					sum54 += c.isCustomizable() && c.getCustomValue() > 0
							? Math.round(c.getBaseScore() * c.getCustomValue() / 50.0f) : c.getBaseScore();
					if ("disease".equals(c.getSection())) disCnt54++;
				}
				if (sum54 != 100) sumOk54 = false;
				if (disCnt54 > 3) disOk54 = false;
			}
			check(sumOk54, "神的谎言随机规则总分 100", "50 次随机分配全部恰好 100 分");
			check(disOk54, "神的谎言随机分配疾病上限", "50 次抽样疾病均 ≤3 种");

			// ---- ① 神的谎言：手动开启疾病上限（NetworkRegistry.applyChallengeToggle 校验） ----
			ServerPlayer redP54 = firstInTeam(GameStateManager.Team.RED);
			if (redP54 != null) {
				cm.clearPlayerChallenges(redP54.getUUID());
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "insomnia", true);
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "hypothermia", true);
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "heatstroke", true);
				int cnt54 = 0;
				for (String rid : cm.getPlayerChallenges(redP54.getUUID())) {
					var dc = cm.getChallenge(rid);
					if (dc != null && "disease".equals(dc.getSection())) cnt54++;
				}
				check(cnt54 == 3, "手动开启三种疾病成功", "失眠/失温/热射全部点亮（疾病数=" + cnt54 + "）");
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "dust_lung", true);
				check(!cm.getPlayerChallenges(redP54.getUUID()).contains("dust_lung"),
						"第四种疾病被拒绝", "尘肺病未进入规则集（上限 3）");
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "heatstroke", false);
				com.survivalcontract.network.NetworkRegistry.applyChallengeToggle(redP54, "dust_lung", true);
				check(cm.getPlayerChallenges(redP54.getUUID()).contains("dust_lung"),
						"关闭一种疾病后可再开新疾病", "热射病关闭 → 尘肺病点亮成功");

				// ---- 职业免疫：斗士免疫低血糖/脆骨症、矿工免疫尘肺病（映射 + 板块tick行为）----
				var profImmSaved = pm54.getPlayerProfession(redP54.getUUID());
				check(!pm54.isImmuneToChallenge(redP54.getUUID(), "hypoglycemia")
								&& !pm54.isImmuneToChallenge(redP54.getUUID(), "brittle_bones")
								&& !pm54.isImmuneToChallenge(redP54.getUUID(), "dust_lung"),
						"职业免疫前置：无职业三项均不免疫", "NONE 职业不免疫低血糖/脆骨症/尘肺病");
				pm54.setPlayerProfession(redP54.getUUID(), ProfessionManager.Profession.WARRIOR);
				check(pm54.isImmuneToChallenge(redP54.getUUID(), "hypoglycemia")
								&& pm54.isImmuneToChallenge(redP54.getUUID(), "brittle_bones")
								&& !pm54.isImmuneToChallenge(redP54.getUUID(), "dust_lung"),
						"斗士免疫低血糖+脆骨症", "斗士：低血糖/脆骨症免疫，尘肺病不免疫");
				pm54.setPlayerProfession(redP54.getUUID(), ProfessionManager.Profession.MINER);
				check(pm54.isImmuneToChallenge(redP54.getUUID(), "dust_lung")
								&& !pm54.isImmuneToChallenge(redP54.getUUID(), "hypoglycemia")
								&& !pm54.isImmuneToChallenge(redP54.getUUID(), "brittle_bones"),
						"矿工免疫尘肺病", "矿工：尘肺病免疫，低血糖/脆骨症不免疫");

				// 低血糖每 tick 加倍消耗在 perSecond 门控之前，可直接反射板块方法做行为对照；
				// 尘肺病逻辑在 perSecond 门控之后（tickSectionRules L2117），反射连调无法推进
				// 服务器 tickCount，故行为验证改到跨真实整秒 tick 的 step 41（dustLungImmunityStep）
				try {
					var mTickRules54 = MainEventListener.class.getDeclaredMethod("tickSectionRules",
							net.minecraft.server.MinecraftServer.class, ServerPlayer.class, java.util.Set.class);
					mTickRules54.setAccessible(true);
					// 低血糖饱食消耗翻倍：斗士免疫不追加，无职业追加 0.0025（exhaustionLevel 私有字段，反射读取）
					var fExh54 = net.minecraft.world.food.FoodData.class.getDeclaredField("exhaustionLevel");
					fExh54.setAccessible(true);
					pm54.setPlayerProfession(redP54.getUUID(), ProfessionManager.Profession.WARRIOR);
					fExh54.setFloat(redP54.getFoodData(), 0.0f);
					mTickRules54.invoke(null, srv, redP54, java.util.Set.of("hypoglycemia"));
					float exhWarrior54 = fExh54.getFloat(redP54.getFoodData());
					pm54.setPlayerProfession(redP54.getUUID(), ProfessionManager.Profession.NONE);
					fExh54.setFloat(redP54.getFoodData(), 0.0f);
					mTickRules54.invoke(null, srv, redP54, java.util.Set.of("hypoglycemia"));
					float exhNone54 = fExh54.getFloat(redP54.getFoodData());
					check(exhWarrior54 == 0.0f && exhNone54 > 0.0f,
							"斗士不触发低血糖加倍消耗",
							"斗士 exhaustion=" + exhWarrior54 + "，无职业=" + exhNone54);
					fExh54.setFloat(redP54.getFoodData(), 0.0f);
				} catch (Exception e54) {
					check(false, "职业免疫反射驱动板块tick", "异常：" + e54);
				}
				pm54.setPlayerProfession(redP54.getUUID(), profImmSaved);
			} else {
				check(false, "手动疾病上限测试环境", "红队假人缺失");
			}

			// ---- ③ 文明模式：队内友伤关闭 + ② 乐师音波（半径 16 + 队友免伤） ----
			gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cv.setup(srv);
			// applyTeamColors 按当前模式重设记分板队伍友伤（文明模式 ff=false）
			com.survivalcontract.team.TeamManager.getInstance().applyTeamColors(srv);
			var sb54 = srv.getScoreboard();
			var redSB54 = sb54.getPlayerTeam("survival_red");
			var blueSB54 = sb54.getPlayerTeam("survival_blue");
			check(redSB54 != null && blueSB54 != null
							&& !redSB54.isAllowFriendlyFire() && !blueSB54.isAllowFriendlyFire(),
					"文明模式记分板队伍友伤关闭",
					"survival_red ff=" + (redSB54 == null ? "无" : redSB54.isAllowFriendlyFire())
							+ " survival_blue ff=" + (blueSB54 == null ? "无" : blueSB54.isAllowFriendlyFire()));
			ServerPlayer mateP54 = redP54 == null ? null : teammateOf(redP54);
			ServerPlayer foeP54 = redP54 == null ? null : firstInTeam(GameStateManager.Team.BLUE);
			check(redP54 != null && mateP54 != null && foeP54 != null, "第五十四批红蓝队员就位",
					"红 " + (redP54 == null ? "无" : redP54.getName().getString())
							+ " 队友 " + (mateP54 == null ? "无" : mateP54.getName().getString())
							+ " 敌 " + (foeP54 == null ? "无" : foeP54.getName().getString()));
			if (redP54 != null && mateP54 != null && foeP54 != null) {
				for (ServerPlayer p : new ServerPlayer[]{redP54, mateP54, foeP54}) {
					p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
					p.setHealth(p.getMaxHealth());
					pm54.setPlayerProfession(p.getUUID(), ProfessionManager.Profession.NONE);
					cm.disableChallenge(p.getUUID(), "pain_lover"); // 防止嗜痛人改写音波伤害判定
				}
				check(!redP54.canHarmPlayer(mateP54), "文明模式同队 canHarmPlayer=false",
						redP54.getName().getString() + " 无法伤害队友 " + mateP54.getName().getString());
				check(redP54.canHarmPlayer(foeP54), "文明模式跨队 canHarmPlayer=true",
						redP54.getName().getString() + " 可伤害对手 " + foeP54.getName().getString());

				// 乐师音波：反射调用 musicianPerform（底鼓 → 力量），每次清 3s 冷却后逐响验证
				var civLevel54 = ArenaWorldManager.getArenaOverworld(srv);
				if (civLevel54 == null) civLevel54 = srv.overworld();
				var center54 = net.minecraft.world.level.ChunkPos.containing(gsm.getPlayingSpawn());
				int bx54 = center54.getMinBlockX() + 8, bz54 = center54.getMinBlockZ() + 8;
				int by54 = civLevel54.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						bx54, bz54);
				var notePos54 = new BlockPos(bx54, by54 + 1, bz54);
				redP54.teleportTo(civLevel54, bx54 + 2.5, by54 + 1.0, bz54 + 0.5, java.util.Set.of(), 0, 0, false);
				mateP54.teleportTo(civLevel54, bx54 + 3.5, by54 + 1.0, bz54 + 0.5, java.util.Set.of(), 0, 0, false);
				foeP54.teleportTo(civLevel54, bx54 + 17.5, by54 + 1.0, bz54 + 0.5, java.util.Set.of(), 0, 0, false);
				for (ServerPlayer p : new ServerPlayer[]{redP54, mateP54, foeP54}) markFakeClientLoaded(p);
				try {
					var mPerf54 = MainEventListener.class.getDeclaredMethod("musicianPerform",
							ServerPlayer.class, ServerLevel.class, BlockPos.class,
							net.minecraft.world.level.block.state.properties.NoteBlockInstrument.class,
							net.minecraft.core.Holder.class, boolean.class);
					mPerf54.setAccessible(true);
					var mFx54 = MainEventListener.class.getDeclaredMethod("musicianInstrumentEffect",
							net.minecraft.world.level.block.state.properties.NoteBlockInstrument.class);
					mFx54.setAccessible(true);
					var fSonic54 = MainEventListener.class.getDeclaredField("MUSICIAN_NEXT_SONIC");
					fSonic54.setAccessible(true);
					@SuppressWarnings("unchecked")
					var sonicMap54 = (java.util.Map<UUID, Long>) fSonic54.get(null);
					@SuppressWarnings("unchecked")
					var fx54 = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) mFx54.invoke(null,
							net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM);
					// 第一响：敌人 17 格外（半径外）+ 队友 3 格内（半径内但免伤）
					sonicMap54.clear();
					mPerf54.invoke(null, redP54, civLevel54, notePos54,
							net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM, fx54, false);
					check(foeP54.getHealth() == foeP54.getMaxHealth() && mateP54.getHealth() == mateP54.getMaxHealth(),
							"乐师音波 17 格外无伤 + 队友免伤",
							"敌 " + foeP54.getHealth() + " 队友 " + mateP54.getHealth());
					// 第二响：敌人传进 5 格 → 受 2 点真实伤害
					foeP54.teleportTo(civLevel54, bx54 + 5.5, by54 + 1.0, bz54 + 0.5, java.util.Set.of(), 0, 0, false);
					markFakeClientLoaded(foeP54);
					com.survivalcontract.McCompat.setInvulnerableTime(foeP54, 0);
					sonicMap54.clear();
					mPerf54.invoke(null, redP54, civLevel54, notePos54,
							net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM, fx54, false);
					check(foeP54.getHealth() < foeP54.getMaxHealth(),
							"乐师音波半径内敌人受 2 点伤害",
							"敌 hp " + foeP54.getMaxHealth() + " → " + foeP54.getHealth());
					check(mateP54.getHealth() == mateP54.getMaxHealth(),
							"乐师音波队友仍免伤", "队友 hp=" + mateP54.getHealth());
				} catch (Exception e54) {
					check(false, "乐师音波反射测试", e54.toString());
				} finally {
					for (ServerPlayer p : new ServerPlayer[]{redP54, mateP54, foeP54}) p.setHealth(p.getMaxHealth());
				}
			}
		} finally {
			gsm.setPlayStyle(savedStyle);
			gsm.resetGodsLies();
			gsm.setCurrentPhase(savedPhase);
			cv.reset();
			for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
				cm.clearPlayerChallenges(p.getUUID());
				for (String id : savedChallenges.get(p.getUUID())) cm.enableChallenge(p.getUUID(), id);
			}
		}
	}

	// ---- 尘肺病矿工免疫：跨真实整秒 tick 行为验证状态（step 41）----
	private static int dustImmPhase = 0;
	private static int dustImmStartTick = -1;
	private static ServerPlayer dustImmP;
	private static double dustImmX, dustImmY, dustImmZ;
	private static ProfessionManager.Profession dustImmSavedProf;

	@SuppressWarnings("unchecked")
	private static java.util.Map<UUID, Integer> dustLungTickMapForTest() throws Exception {
		var f = MainEventListener.class.getDeclaredField("dustLungTicks");
		f.setAccessible(true);
		return (java.util.Map<UUID, Integer>) f.get(null);
	}

	/**
	 * 尘肺病逻辑在 tickSectionRules 的 perSecond 门控（tickCount%20==0）之后，反射连调无法
	 * 推进 tickCount，所以改为跨真实 tick 状态机：反射把 dustLungTicks 预置 59（压缩 60 秒
	 * 等待），让生产 tick 处理器在真实整秒自然触发。阶段1：MINER 在 Y<0 不中毒；
	 * 阶段2：切 NONE 后下一个整秒中毒（对照）。完成后清理并 startGame 进入 step 5。
	 */
	private static void dustLungImmunityStep() {
		try {
			if (dustImmPhase == 0) {
				dustImmP = firstInTeam(GameStateManager.Team.RED);
				if (dustImmP == null) {
					check(false, "尘肺职业免疫跨tick环境", "红队假人缺失");
					startGame();
					step = 5;
					return;
				}
				var pm = ProfessionManager.getInstance();
				var cm = ChallengeManager.getInstance();
				var gsm = GameStateManager.getInstance();
				var u = dustImmP.getUUID();
				dustImmSavedProf = pm.getPlayerProfession(u);
				var pos = dustImmP.position();
				dustImmX = pos.x; dustImmY = pos.y; dustImmZ = pos.z;
				// 生产 tick 链路有 PLAYING 门控（MainEventListener L514）
				gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
				cm.clearPlayerChallenges(u);
				cm.enableChallenge(u, "dust_lung");
				// 竞技场已由 case4 建好：PLAYING 的防逃跑扫描会把竞技场外的玩家拉回去，
				// 因此本测试直接在竞技场内负层进行（否则 2 秒扫描会打断 y<0 断言）
				ServerLevel dustLevel = ensureTestArena();
				if (dustLevel != null) {
					dustImmP.teleportTo(dustLevel, dustImmX, -10, dustImmZ, Set.of(),
							dustImmP.getYRot(), dustImmP.getXRot(), false);
				} else {
					dustImmP.snapTo(dustImmX, -10, dustImmZ);
				}
				dustImmP.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
				pm.setPlayerProfession(u, ProfessionManager.Profession.MINER);
				// 预置 59：下一个真实整秒 merge→60 即触发（矿工应被免疫门控挡住，不触发）
				dustLungTickMapForTest().put(u, 59);
				dustImmStartTick = tick;
				dustImmPhase = 1;
			} else if (dustImmPhase == 1 && tick - dustImmStartTick >= 21) {
				check(dustImmP.getY() < 0, "尘肺跨tick位置在负层", "getY=" + dustImmP.getY());
				check(!dustImmP.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
						"矿工负层不患尘肺病（跨整秒tick）",
						"MINER 在 Y<0 经历真实整秒 tick 后仍无中毒；dustLungTicks="
								+ dustLungTickMapForTest().get(dustImmP.getUUID()));
				// 切无职业并重新预置 59，等下一个真实整秒（应触发中毒）
				ProfessionManager.getInstance().setPlayerProfession(dustImmP.getUUID(),
						ProfessionManager.Profession.NONE);
				dustImmP.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
				dustLungTickMapForTest().put(dustImmP.getUUID(), 59);
				dustImmStartTick = tick;
				dustImmPhase = 2;
			} else if (dustImmPhase == 2 && tick - dustImmStartTick >= 21) {
				check(dustImmP.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
						"无职业负层患尘肺病（对照，跨整秒tick）",
						"NONE 在 Y<0 经历真实整秒 tick 后获得中毒；dustLungTicks="
								+ dustLungTickMapForTest().get(dustImmP.getUUID()));
				// 清理还原（startGame 会统一开局，挑战/职业无需还原到大厅态）
				var u = dustImmP.getUUID();
				dustImmP.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
				dustLungTickMapForTest().remove(u);
				dustImmP.snapTo(dustImmX, dustImmY, dustImmZ);
				ProfessionManager.getInstance().setPlayerProfession(u, dustImmSavedProf);
				ChallengeManager.getInstance().clearPlayerChallenges(u);
				dustImmPhase = 0;
				// startGame 断言当前为 LOBBY
				GameStateManager.getInstance().setCurrentPhase(GameStateManager.GamePhase.LOBBY);
				startGame();
				step = 5;
			}
		} catch (Throwable t) {
			check(false, "尘肺职业免疫跨tick", "异常：" + t);
			dustImmPhase = 0;
			GameStateManager.getInstance().setCurrentPhase(GameStateManager.GamePhase.LOBBY);
			startGame();
			step = 5;
		}
	}

	/** 自测辅助：产出配置 requireBlocks 归一化（去 minecraft: 前缀；null → 空表） */
	private static List<String> reqNorm53(com.survivalcontract.civilization.CivilizationProductionConfig.ZoneCfg z) {
		if (z == null || z.requireBlocks == null) return List.of();
		var out = new ArrayList<String>();
		for (String s : z.requireBlocks) out.add(s.startsWith("minecraft:") ? s.substring(10) : s);
		return out;
	}

	/** 统计玩家背包中某物品数量（自测辅助） */
	private static int countItem(ServerPlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	/** 原版 Q 键时序：先从背包取出该物品（首个含它的槽位整组），再交由 drop 调用（丢弃拦截测试用） */
	private static net.minecraft.world.item.ItemStack takeFirstItem(ServerPlayer p, net.minecraft.world.item.Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (s.is(item)) return inv.removeItemNoUpdate(i);
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	/**
	 * 自测辅助：确保竞技场维度已创建。
	 * 第八十七批起 CivilizationManager.setup 在竞技场缺失时拒绝退回原版主世界（防止给大厅套边界/刷实体），
	 * 而文明时代相关断言都跑在开局之前，因此测试必须先显式把竞技场建出来。
	 */
	private static ServerLevel ensureTestArena() {
		var srv = server.get();
		ServerLevel arena = ArenaWorldManager.getArenaOverworld(srv);
		if (arena == null) {
			ArenaWorldManager.createArenaWorlds(srv);
			arena = ArenaWorldManager.getArenaOverworld(srv);
			SurvivalContract.LOGGER.info("[SC-TEST] civ test arena created: {}", arena != null);
		}
		return arena;
	}

	private static void startGame() {
		GameStateManager gsm = GameStateManager.getInstance();
		check(gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY, "开始前阶段", "LOBBY");
		LobbyManager.getInstance().startGame(server.get());
		check(gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING, "开始后阶段", "PLAYING");
		int freeze = gsm.getFreezeSecondsRemaining();
		check(freeze >= 1 && freeze <= 3, "3 秒倒计时冻结", "freezeSeconds=" + freeze);
		startTick = tick;
		testRespawnPoints(gsm);
		testArenaWorlds();
	}

	/** 第一局竞技场随机种子（用于第二局种子对比） */
	private static long firstGameSeed = 0;

	/** 竞技场世界：维度组已创建、玩家已进入、重生配置指向竞技场 */
	private static void testArenaWorlds() {
		var s = server.get();
		ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(s);
		ServerLevel nether = com.survivalcontract.dimension.ArenaWorldManager.getArenaNether(s);
		ServerLevel end = com.survivalcontract.dimension.ArenaWorldManager.getArenaEnd(s);
		check(arena != null && nether != null && end != null, "竞技场维度组创建",
				"arena/arena_nether/arena_end " + (arena != null && nether != null && end != null ? "3/3" : "缺失"));
		if (arena == null) return;
		check(arena != s.overworld(), "竞技场独立于原版主世界", "Arena 实例 != vanilla overworld");
		if (firstGameSeed == 0) firstGameSeed = com.survivalcontract.dimension.ArenaWorldManager.currentSeed();
		// getSeed mixin 生效性：竞技场维度的 getSeed 必须返回本局随机种子（而非世界种子）
		check(arena.getSeed() == com.survivalcontract.dimension.ArenaWorldManager.currentSeed()
						&& arena.getSeed() != s.overworld().getSeed(),
				"竞技场随机种子拦截生效", "arenaSeed=" + com.survivalcontract.dimension.ArenaWorldManager.currentSeed());
		ServerPlayer p = firstInTeam(GameStateManager.Team.RED);
		check(p.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA,
				"玩家进入竞技场主世界", "dimension=" + p.level().dimension().identifier());
		var rc = p.getRespawnConfig();
		check(rc != null && rc.respawnData().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA,
				"重生配置指向竞技场", rc != null ? "respawnData.dimension=" + rc.respawnData().dimension().identifier() : "null");
	}

	/** 固定重生点：同队共享、红蓝间隔 15 区块（240 格，允许安全落点微调）、RespawnConfig 已设置 */
	private static void testRespawnPoints(GameStateManager gsm) {
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer redMate = teammateOf(redMember);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		var r1 = gsm.getRespawnPoint(redMember.getUUID());
		var r2 = gsm.getRespawnPoint(redMate.getUUID());
		var rb = gsm.getRespawnPoint(blueMember.getUUID());
		check(r1.equals(r2), "同队共享重生点", "红队两人同一点");
		double d = Math.sqrt(r1.distSqr(rb));
		check(d >= 232 && d <= 600, "红蓝重生点间隔15区块",
				String.format("%.0f 格 (蓝队点保底间距 >= 240)", d));
		check(redMember.getRespawnConfig() != null, "固定重生点已设置", "RespawnConfig(forced=true) 非空");
	}

	/**
	 * 第二局重开场景：游戏结束重置会清空队伍映射（resetPlayerTeams），
	 * 重开时若不先补队伍就分配队点，全员队别为 NONE → 全部塞进红队点（出生点挤在一起的老 bug）。
	 */
	private static void testRestartScenario() {
		GameStateManager gsm = GameStateManager.getInstance();
		// 完整模拟 performResetForLobby 的队伍重置（清 TeamManager 内部成员列表 + 清 gsm 映射）
		com.survivalcontract.team.TeamManager.getInstance().clearTeamColors(server.get());
		gsm.resetPlayerTeams();
		LobbyManager.getInstance().startGame(server.get()); // 直接重开（不经过大厅切模式）
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		var rp = gsm.getRespawnPoint(redMember.getUUID());
		var bp = gsm.getRespawnPoint(blueMember.getUUID());
		double d = Math.sqrt(rp.distSqr(bp));
		check(d >= 232, "重开局队点仍间隔15区块",
				String.format("%.0f 格（队伍重置后直接重开，红=%s 蓝=%s）", d, rp.toShortString(), bp.toShortString()));

		// 竞技场世界：第二局种子不同 + 维度组已重建（旧的在重开时被拆除删档）
		long seed2 = com.survivalcontract.dimension.ArenaWorldManager.currentSeed();
		check(seed2 != firstGameSeed, "第二局竞技场随机种子不同",
				"seed1=" + firstGameSeed + " seed2=" + seed2);
		ServerLevel arena2 = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server.get());
		check(arena2 != null, "第二局竞技场维度组已重建",
				arena2 != null ? "dimension=" + arena2.dimension().identifier() : "缺失");
	}

	private static void testScoresAndTask() {
		GameStateManager gsm = GameStateManager.getInstance();
		ChallengeManager cm = ChallengeManager.getInstance();
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer redMate = teammateOf(redMember);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);

		// 初始分：同队成员规则互通 → 初始分一致，团队总分 = 初始分 × 队友数
		// （与游戏实际口径一致：calculateInitialScoreExcludingBans 含"无职业 +10"加成）
		int perRed = gsm.calculateInitialScoreExcludingBans(redMember.getUUID()) + gsm.getEventScoreBonus(redMember.getUUID());
		check(gsm.getScore(redMember.getUUID()) == perRed && gsm.getScore(redMate.getUUID()) == perRed,
				"同队初始分一致", "每人 " + perRed + " 分");
		check(gsm.getTeamScore(GameStateManager.Team.RED) == perRed * 2,
				"团队总分累加", "红队 " + gsm.getTeamScore(GameStateManager.Team.RED) + " = 初始分×2 (" + perRed * 2 + ")");

		// 违规扣分：每人单独计算（只扣当事人，队友不动，团队总分随个人扣减）
		gsm.deductScore(redMember.getUUID(), 1); // 模拟一次 1 分违规
		check(gsm.getScore(redMember.getUUID()) == perRed - 1 && gsm.getScore(redMate.getUUID()) == perRed,
				"违规仅扣当事人", redMember.getName().getString() + " -1，队友不变");
		check(gsm.getTeamScore(GameStateManager.Team.RED) == perRed * 2 - 1,
				"团队总分随个人扣分", "红队总分 " + gsm.getTeamScore(GameStateManager.Team.RED) + " = " + (perRed * 2 - 1));

		// N7：开局首个专属任务强制 B 级
		TaskManager tm = TaskManager.getInstance();
		var t1 = tm.getActiveTask(redMember.getUUID());
		var t2 = tm.getActiveTask(blueMember.getUUID());
		check(t1 != null && t1.tier == TaskManager.TaskTier.B,
				"首任务 B 级(红)", t1 != null ? "[" + t1.tier + "] " + t1.displayName : "无任务");
		check(t2 != null && t2.tier == TaskManager.TaskTier.B,
				"首任务 B 级(蓝)", t2 != null ? "[" + t2.tier + "] " + t2.displayName : "无任务");
		// 第二个任务也强制 B 级（直接再发一次验证 issuedCount=2 时仍强制）
		tm.issueNewTask(redMember);
		var t1b = tm.getActiveTask(redMember.getUUID());
		check(t1b != null && t1b.tier == TaskManager.TaskTier.B,
				"第 2 个任务仍 B 级", t1b != null ? "[" + t1b.tier + "] " + t1b.displayName : "无任务");
	}

	private static void testTeammateTp() {
		GameStateManager gsm = GameStateManager.getInstance();
		ServerPlayer redMember = firstInTeam(GameStateManager.Team.RED);
		ServerPlayer redMate = teammateOf(redMember);
		ServerPlayer blueMember = firstInTeam(GameStateManager.Team.BLUE);
		var overworld = redMate.level(); // PLAYING 中玩家在竞技场主世界（勿用 vanilla overworld，防逃出维度组）

		// 把队友挪远（初始同队扎堆在同一偏移点）
		redMate.teleportTo(overworld, redMate.getX() + 150, redMate.getY() + 1, redMate.getZ() + 150,
				java.util.Set.of(), 0f, 0f, false);
		double before = redMember.distanceTo(redMate);
		server.get().getCommands().performPrefixedCommand(
				redMember.createCommandSourceStack(), "sc tp " + redMate.getName().getString());
		double after = redMember.distanceTo(redMate);
		check(after < 3.0, "同队 /sc tp", "传送前 " + String.format("%.0f", before) + " 格 → 传送后 " + String.format("%.1f", after) + " 格");

		// 跨队传送 → 被拒，位置不变
		double bx = blueMember.getX(), bz = blueMember.getZ();
		server.get().getCommands().performPrefixedCommand(
				blueMember.createCommandSourceStack(), "sc tp " + redMember.getName().getString());
		check(Math.abs(blueMember.getX() - bx) < 0.5 && Math.abs(blueMember.getZ() - bz) < 0.5,
				"跨队 /sc tp 被拒", "蓝队传送红队成员 → 位置未变化");
	}

	/** 回大厅清空物品：职业初始物品/杂物全清，仅保留挑战书 */
	private static void testLobbyInventoryClear() {
		LobbyManager dm = LobbyManager.getInstance();
		ServerPlayer p = firstInTeam(GameStateManager.Team.RED);
		// 开局时挑战书已被移除：先模拟大厅状态（有书 + 职业物品残留 + 杂物）
		dm.giveChallengeBook(p);
		p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
		p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
		// 模拟对局中攒下的经验槽（等级 / 进度 / 总经验）
		p.experienceLevel = 12;
		p.experienceProgress = 0.5f;
		p.totalExperience = 350;
		dm.teleportToLobby(p);
		int nonEmpty = 0;
		boolean bookFound = false;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (!s.isEmpty()) {
				nonEmpty++;
				if (com.survivalcontract.MainEventListener.isChallengeBook(s)) bookFound = true;
			}
		}
		check(nonEmpty == 1 && bookFound, "回大厅清空物品",
				"物品栏仅剩挑战书 (非空 " + nonEmpty + " 件, 书=" + bookFound + ")");
		check(p.experienceLevel == 0 && p.experienceProgress == 0.0f && p.totalExperience == 0,
				"回大厅清空经验槽",
				"等级 12→" + p.experienceLevel + " 进度 0.50→" + p.experienceProgress
						+ " 总经验 350→" + p.totalExperience);
	}

	/** 大厅配置：config 文件夹/配置文件生成、自定义出生点、自定义存档 zip 替换大厅地图 */
	private static void testLobbyConfig() {
		var cfg = com.survivalcontract.dimension.LobbyConfig.configFile();
		var dir = com.survivalcontract.dimension.LobbyConfig.lobbyDir();
		check(java.nio.file.Files.exists(cfg), "大厅配置文件生成", cfg.toString());
		check(java.nio.file.Files.isDirectory(dir), "大厅地图文件夹生成", dir.toString());

		try {
			// 自定义出生点：写配置 → 重新加载 → 生效
			java.nio.file.Files.writeString(cfg, "{\"spawnX\":11,\"spawnY\":-62,\"spawnZ\":13}");
			com.survivalcontract.dimension.LobbyConfig.load();
			var sp = LobbyManager.LOBBY_SPAWN;
			check(sp.getX() == 11 && sp.getY() == -62 && sp.getZ() == 13,
					"大厅出生点自定义", "LOBBY_SPAWN = " + sp.toShortString());
			// 还原默认，避免影响后续手动测试
			java.nio.file.Files.writeString(cfg, "{\"spawnX\":9,\"spawnY\":-63,\"spawnZ\":7}");
			com.survivalcontract.dimension.LobbyConfig.load();

			// 自定义存档 zip 替换大厅地图（嵌套一层文件夹，模拟玩家直接压缩存档目录）
			java.nio.file.Path zip = dir.resolve("test_lobby.zip");
			try (var zos = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
				zos.putNextEntry(new java.util.zip.ZipEntry("MyLobbySave/region/r.99.99.mca"));
				zos.write(new byte[]{1, 2, 3, 4});
				zos.closeEntry();
			}
			com.survivalcontract.dimension.LobbyDimensionSetup.injectRegionFiles(server.get());
			java.nio.file.Path injected = server.get().getWorldPath(
					net.minecraft.world.level.storage.LevelResource.ROOT)
					.resolve("dimensions").resolve("survival-contract").resolve("lobby")
					.resolve("region").resolve("r.99.99.mca");
			boolean ok = java.nio.file.Files.exists(injected)
					&& java.nio.file.Files.readAllBytes(injected).length == 4;
			check(ok, "自定义存档zip替换大厅地图", "region/r.99.99.mca 从 config zip 提取 = " + ok);
			// 清理测试 zip 与测试产物，再重灌内置地图
			java.nio.file.Files.deleteIfExists(zip);
			java.nio.file.Files.deleteIfExists(injected);
			com.survivalcontract.dimension.LobbyDimensionSetup.injectRegionFiles(server.get());
		} catch (Exception e) {
			check(false, "大厅配置测试", "异常: " + e);
		}
	}

	/** 丢书返还：走真实 drop 路径，丢出者本人拿回挑战书（不管别的玩家是否更近/在列表更前） */
	private static void testBookReturn() {
		var dm = LobbyManager.getInstance();
		var lobby = dm.getLobbyLevel();
		ServerPlayer thrower = firstInTeam(GameStateManager.Team.BLUE);
		ServerPlayer red = firstInTeam(GameStateManager.Team.RED);
		// 红队身上已有的书先移除（上一项测试发的），避免"未错进红队背包"误报
		var redInv = red.getInventory();
		for (int i = 0; i < redInv.getContainerSize(); i++) {
			if (com.survivalcontract.MainEventListener.isChallengeBook(redInv.getItem(i))) {
				redInv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
		}
		// 蓝队丢出者传回大厅（丢书只发生在大厅）并拿一本书真实丢出（与玩家按 Q 相同的 vanilla 路径）
		dm.teleportToLobby(thrower);
		dm.giveChallengeBook(thrower);
		var inv = thrower.getInventory();
		net.minecraft.world.item.ItemStack book = null;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (com.survivalcontract.MainEventListener.isChallengeBook(inv.getItem(i))) {
				book = inv.getItem(i).copy();
				inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				break;
			}
		}
		// 冒险模式可能被 canDropItems() 拒绝：临时切生存再丢
		thrower.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		var droppedEntity = com.survivalcontract.McCompat.drop(thrower, book, true);
		check(droppedEntity != null, "丢书生成掉落物", droppedEntity == null ? "drop 返回 null" : "生成于 " + droppedEntity.blockPosition().toShortString());
		if (droppedEntity != null) {
			droppedEntity.setNeverPickUp(); // 防假人捡走
		}
		// 新实体要到下一 tick 才进入实体索引（getEntitiesOfClass 查不到新实体）：
		// 下一 tick 直接用实体引用调用返还逻辑（与生产 10-tick 扫描共用同一段代码）
		var redRef = red;
		var throwerRef = thrower;
		var entityRef = droppedEntity;
		server.get().execute(() -> {
			boolean returned = entityRef != null
					&& com.survivalcontract.MainEventListener.returnChallengeBook(server.get(), entityRef);
			boolean backToThrower = false;
			for (int i = 0; i < throwerRef.getInventory().getContainerSize(); i++) {
				if (com.survivalcontract.MainEventListener.isChallengeBook(throwerRef.getInventory().getItem(i))) {
					backToThrower = true;
					break;
				}
			}
			boolean notInRed = true;
			for (int i = 0; i < redRef.getInventory().getContainerSize(); i++) {
				if (com.survivalcontract.MainEventListener.isChallengeBook(redRef.getInventory().getItem(i))) {
					notInRed = false;
					break;
				}
			}
			check(returned && backToThrower && notInRed, "丢书返还丢出者本人",
					"返还成功=" + returned + ", 蓝队丢出者拿回=" + backToThrower + ", 未错进红队背包=" + notInRed);
			throwerRef.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
		});
	}

	/**
	 * 退出重进保留局内状态：保存快照 → 模拟服务器重启清空内存 → 恢复，
	 * 断言阶段回到 PLAYING（而非大厅）、竞技场用同一存档种子重建（不删档）、
	 * 分数/队伍/挑战/职业/出生点全部还原。
	 */
	private static void testSessionPersistence() {
		var s = server.get();
		GameStateManager gsm = GameStateManager.getInstance();
		long seedBefore = ArenaWorldManager.currentSeed();
		BlockPos spawnBefore = gsm.getPlayingSpawn();
		int radiusBefore = gsm.getPlayingBorderRadius();
		ServerPlayer p = firstInTeam(GameStateManager.Team.RED);
		int scoreBefore = gsm.getScore(p.getUUID());
		GameStateManager.Team teamBefore = gsm.getPlayerTeam(p.getUUID());
		Set<String> chBefore = new HashSet<>(ChallengeManager.getInstance().getPlayerChallenges(p.getUUID()));
		ProfessionManager.Profession profBefore = ProfessionManager.getInstance().getPlayerProfession(p.getUUID());
		// 个人模式事件选择也纳入快照（团队模式下该映射为空不影响）
		gsm.setPlayerEnabledEvents(p.getUUID(), new HashSet<>(Set.of("LIGHTNING")));

		// 玩家先撤离竞技场（真实流程：先回大厅，再拆/重建维度）
		for (ServerPlayer pl : s.getPlayerList().getPlayers()) {
			LobbyManager.getInstance().teleportToLobby(pl);
		}

		SessionPersistence.save(s);
		java.nio.file.Path file = s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
				.resolve("survival-contract-session.nbt");
		check(java.nio.file.Files.exists(file), "会话快照落盘", file.getFileName().toString());

		// 模拟服务器重启后的内存状态（GameStateManager.onServerStart 的重置行为）
		gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
		gsm.clearRuntimeState();
		gsm.resetFlags();
		ChallengeManager.getInstance().resetAllPlayers();
		ProfessionManager.getInstance().clearAll();
		TaskManager.getInstance().resetAll();

		boolean restored = SessionPersistence.restore(s);
		check(restored && gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING,
				"退出重进恢复为 PLAYING（不回大厅）", "restored=" + restored + " phase=" + gsm.getCurrentPhase());
		check(ArenaWorldManager.currentSeed() == seedBefore, "竞技场沿用同一局种子",
				"seed=" + ArenaWorldManager.currentSeed() + " (原 " + seedBefore + ")");
		check(gsm.getPlayingSpawn().equals(spawnBefore) && gsm.getPlayingBorderRadius() == radiusBefore,
				"出生点/边界恢复", "spawn=" + gsm.getPlayingSpawn().toShortString() + " radius=" + gsm.getPlayingBorderRadius());
		check(gsm.getScore(p.getUUID()) == scoreBefore && gsm.getPlayerTeam(p.getUUID()) == teamBefore,
				"分数/队伍恢复", "score=" + gsm.getScore(p.getUUID()) + " team=" + gsm.getPlayerTeam(p.getUUID()));
		check(new HashSet<>(ChallengeManager.getInstance().getPlayerChallenges(p.getUUID())).equals(chBefore),
				"已选挑战恢复", chBefore.size() + " 条");
		check(ProfessionManager.getInstance().getPlayerProfession(p.getUUID()) == profBefore,
				"职业恢复", profBefore.name());
		check(gsm.getAllPlayerEnabledEvents().getOrDefault(p.getUUID(), Set.of()).contains("LIGHTNING"),
				"个人事件选择恢复", gsm.getAllPlayerEnabledEvents().getOrDefault(p.getUUID(), Set.of()).toString());
		ServerLevel arenaRe = ArenaWorldManager.getArenaOverworld(s);
		check(arenaRe != null, "竞技场维度组按原种子重建", arenaRe != null ? arenaRe.dimension().identifier().toString() : "缺失");

		// 出生点坐标必须落在千位以内（本轮修复项）
		BlockPos sp = gsm.getPlayingSpawn();
		check(Math.abs(sp.getX()) < 1000 && Math.abs(sp.getZ()) < 1000,
				"出生点坐标在千位以内", sp.toShortString());

		// 第八十七批回归：文明时代/神的谎言/走私犯/附属玩法没有最终目标（开局置 null），
		// 旧实现直接 name() 抛 NPE → 快照永远写不出 → 重启后玩法退回 NORMAL → 四种地形预设全部失效。
		gsm.setCurrentFinalGoal(null);
		gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
		gsm.setTerrainPreset(GameStateManager.TerrainPreset.DESERT);
		SessionPersistence.save(s);
		check(java.nio.file.Files.exists(file), "finalGoal=null 时快照仍能落盘", file.getFileName().toString());
		// 模拟重启后的默认内存态：玩法/地形预设必须从快照读回，且必须在重建竞技场之前生效
		gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
		gsm.setTerrainPreset(GameStateManager.TerrainPreset.OVERWORLD);
		gsm.setCurrentFinalGoal(GameStateManager.FinalGoal.WITHER);
		boolean restoredNullGoal = SessionPersistence.restore(s);
		check(restoredNullGoal && gsm.getCurrentFinalGoal() == null,
				"finalGoal=null 恢复", "restored=" + restoredNullGoal + " goal=" + gsm.getCurrentFinalGoal());
		check(gsm.getPlayStyle() == GameStateManager.PlayStyle.GODS_LIES
						&& gsm.getTerrainPreset() == GameStateManager.TerrainPreset.DESERT,
				"玩法/地形预设随快照恢复（重建竞技场前生效）",
				"playStyle=" + gsm.getPlayStyle() + " preset=" + gsm.getTerrainPreset());

		// 文明时代/附属玩法快照不跨重启恢复：疆域/大本营实体等运行时状态没有持久化，
		// 强行恢复只会得到"没有疆域和边界"的假对局（玩家在边界外/虚空）→ 清快照回大厅
		gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
		gsm.setCurrentFinalGoal(null);
		SessionPersistence.save(s);
		boolean restoredCiv = SessionPersistence.restore(s);
		check(!restoredCiv && !java.nio.file.Files.exists(file),
				"文明时代快照不跨重启恢复（回大厅并清快照）",
				"restored=" + restoredCiv + " fileExists=" + java.nio.file.Files.exists(file));

		// 清理，避免影响后续手动测试
		SessionPersistence.clear(s);
		gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
	}

	/** Boss 血条清理场景准备：在观察者所在维度（区块已加载）生成凋灵。
	 *  新加入实体要经过一次维度 tick 才会进入"被追踪/可见"集合，故与断言分两 tick 执行。 */
	private static net.minecraft.world.entity.boss.wither.WitherBoss testWither;
	private static ServerPlayer testWitherObserver;
	private static String witherSpawnDiag = "n/a";
	private static int bossBarAssertTick = 0;

	private static void setupBossBarScenario() {
		ServerPlayer observer = firstInTeam(GameStateManager.Team.RED);
		try {
			ServerLevel obsLevel = observer.level();
			var wither = new net.minecraft.world.entity.boss.wither.WitherBoss(
					net.minecraft.world.entity.EntityTypes.WITHER, obsLevel);
			wither.setPos(observer.getX(), observer.getY(), observer.getZ());
			wither.setNoAi(true); // 避免测试凋灵攻击/破坏场景；血条追踪不依赖 AI
			boolean added = obsLevel.addFreshEntity(wither);
			testWither = wither;
			testWitherObserver = observer;
			bossBarAssertTick = tick + 2;
			witherSpawnDiag = "生成=" + added + " 维度=" + obsLevel.dimension().identifier()
					+ " 坐标=" + wither.getBlockX() + "," + wither.getBlockY() + "," + wither.getBlockZ();
		} catch (Throwable t) {
			testWither = null;
			witherSpawnDiag = "异常: " + t;
			SurvivalContract.LOGGER.warn("[SC-TEST] wither spawn for boss bar check failed", t);
		}
	}

	/** Boss 血条清理：凋灵与末影龙的原版血条都能被显式移除（防跨局残留叠加） */
	private static void testBossBarCleanup() {
		var s = server.get();
		ServerPlayer observer = firstInTeam(GameStateManager.Team.RED);
		boolean dragonOk = false;

		String witherDiag = witherSpawnDiag;
		try {
			if (testWither != null) {
				ServerLevel obsLevel = testWitherObserver.level();
				int seen = 0;
				for (var e : obsLevel.getAllEntities()) if (e == testWither) seen++;
				boolean byId = obsLevel.getEntity(testWither.getId()) != null;
				var boss = ((com.survivalcontract.mixin.WitherBossAccessor) testWither).sc$getBossEvent();
				boss.addPlayer(testWitherObserver);
				int before = boss.getPlayers().size();
				if (byId) {
					com.survivalcontract.MainEventListener.clearVanillaBossBars(s);
					int after = boss.getPlayers().size();
					check(before > 0 && after == 0, "凋灵血条被清理",
							witherDiag + " 被追踪=" + seen + " 挂载=" + before + " 清理后=" + after);
				} else {
					// 假人环境：没有真实玩家的区块追踪，实体不会进入"可见/被追踪"查找表
					// （ServerPlayer#isAlwaysTicking 掩盖了这一点），该分支无法在自测中覆盖。
					// 生产环境：凋灵对玩家可见即被 LivingEntity#startSeenByPlayer 挂上血条，
					// 因而必然在查找表内，clearVanillaBossBars 可找到并清除 —— 留待实机验证。
					SurvivalContract.LOGGER.info(
							"[SC-TEST][SKIP] 凋灵血条被清理 — 假人环境实体无法进入查找表，需实机验证。诊断: {}",
							witherDiag + " 被追踪=" + seen + " 挂载=" + before);
				}
				testWither.discard();
			}
		} catch (Throwable t) {
			witherDiag += " 异常: " + t;
			SurvivalContract.LOGGER.warn("[SC-TEST] wither boss bar check failed", t);
		}

		// 末影龙：龙战血条（SavedData）同样清理
		try {
			ServerLevel end = ArenaWorldManager.getArenaEnd(s);
			var fight = end != null ? end.getDragonFight() : null;
			if (fight != null) {
				var dragon = ((com.survivalcontract.mixin.EnderDragonFightAccessor) fight).sc$getDragonEvent();
				dragon.addPlayer(observer);
				int before = dragon.getPlayers().size();
				com.survivalcontract.MainEventListener.clearVanillaBossBars(s);
				dragonOk = before > 0 && dragon.getPlayers().isEmpty();
			}
		} catch (Throwable t) {
			SurvivalContract.LOGGER.warn("[SC-TEST] dragon boss bar check failed", t);
		}
		check(dragonOk, "末影龙血条被清理", dragonOk ? "挂载后清理 → 玩家数归零" : "未移除");
	}

	private static void finish() {
		SurvivalContract.LOGGER.info("[SC-TEST] ==================================");
		SurvivalContract.LOGGER.info("[SC-TEST] 结果: {} PASS / {} FAIL", passes, FAILS.size());
		for (String f : FAILS) SurvivalContract.LOGGER.error("[SC-TEST][FAIL-汇总] {}", f);
		if (FAILS.isEmpty()) {
			SurvivalContract.LOGGER.info("[SC-TEST] 多人团队功能（分队/染色/规则互通/双禁/团队分/违规单人扣/首任务B级/队友tp/回大厅清物品/大厅配置与自定义地图/丢书返还本人/会话持久化退出重进/血条清理(末影龙)/出生点坐标范围）全部通过 ✓");
		}
		SurvivalContract.LOGGER.info("[SC-TEST] ==================================");
		// 移除假人并停服
		var s = server.get();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1, fakeLate}) {
			if (p != null && p.connection != null) s.getPlayerList().remove(p);
		}
		red1 = red2 = blue1 = fakeLate = null;
		s.halt(false);
	}

	// ==================== 断言 + 假连接 ====================

	/** 取指定队伍中的某个假人（randomizeTeams 是随机洗牌，假人名不代表队伍） */
	private static ServerPlayer firstInTeam(GameStateManager.Team team) {
		GameStateManager gsm = GameStateManager.getInstance();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			if (gsm.getPlayerTeam(p.getUUID()) == team) return p;
		}
		return null;
	}

	/** p 的同队队友 */
	private static ServerPlayer teammateOf(ServerPlayer p) {
		return otherInTeam(GameStateManager.getInstance().getPlayerTeam(p.getUUID()), p);
	}

	private static ServerPlayer otherInTeam(GameStateManager.Team team, ServerPlayer exclude) {
		GameStateManager gsm = GameStateManager.getInstance();
		for (ServerPlayer p : new ServerPlayer[]{red1, red2, blue1}) {
			if (p != exclude && gsm.getPlayerTeam(p.getUUID()) == team) return p;
		}
		return null;
	}

	/** p 的对手（另一队成员） */
	private static ServerPlayer enemyOf(ServerPlayer p) {
		GameStateManager.Team mine = GameStateManager.getInstance().getPlayerTeam(p.getUUID());
		GameStateManager.Team other = mine == GameStateManager.Team.RED ? GameStateManager.Team.BLUE : GameStateManager.Team.RED;
		return firstInTeam(other);
	}

	/**
	 * 「禁用背包格子」拾取守卫：
	 * 可用槽占满 → 拒绝拾取（物品留在地上、不进锁定格）；可用槽有空位 → 正常拾取；
	 * 未锁定槽里同类未满栈 → 继续补堆（余量留在地上）；未开启规则 → 原版行为。
	 * 直接调 {@link PickupGuard#pickup}——mixin 重定向 ItemEntity.playerTouch 后走的就是这条路径。
	 */
	private static void testPickupLock() {
		ChallengeManager cm = ChallengeManager.getInstance();
		GameStateManager gsm = GameStateManager.getInstance();
		ServerPlayer p = red1;
		check(p != null, "拾取锁定：假人就绪", p == null ? "无假人可用" : p.getName().getString());
		if (p == null) return;

		Inventory inv = p.getInventory();
		GameStateManager.GamePhase prevPhase = gsm.getCurrentPhase();
		Set<String> prevChallenges = new HashSet<>(cm.getPlayerChallenges(p.getUUID()));
		Challenge ic = cm.getChallenge("inv_slot_lock");
		int prevCustom = ic != null ? ic.getCustomValue() : -1;
		List<ItemStack> prevInv = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) prevInv.add(inv.getItem(i).copy());
		try {
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
			cm.setPlayerChallenges(p.getUUID(), new HashSet<>(Set.of("inv_slot_lock")));
			if (ic != null) ic.setCustomValue(9); // 9 → 锁 27 格（9~35）：可用区只剩快捷栏 0~8

			Item[] fillers = {Items.STONE, Items.DIRT, Items.GRAVEL, Items.SAND, Items.COBBLESTONE,
					Items.OAK_LOG, Items.SANDSTONE, Items.CLAY, Items.BRICK};

			// ① 可用区（快捷栏 0~8）占满 → 拾取失败，物品不落到锁定格
			inv.clearContent();
			for (int i = 0; i < 9; i++) inv.setItem(i, new ItemStack(fillers[i], 64));
			ItemStack incoming = new ItemStack(Items.DIAMOND, 5);
			boolean ok1 = PickupGuard.pickup(inv, incoming);
			boolean leakedToLocked = false;
			for (int i = 9; i < 36; i++) if (!inv.getItem(i).isEmpty()) leakedToLocked = true;
			check(!ok1 && incoming.getCount() == 5 && !leakedToLocked,
					"拾取锁定：可用格占满 → 拒绝拾取（不进锁定格）",
					"收下=" + ok1 + "，地上剩余=" + incoming.getCount() + "，锁定格全空=" + !leakedToLocked);

			// ② 可用区出现空位 → 正常拾取
			inv.setItem(4, ItemStack.EMPTY);
			boolean ok2 = PickupGuard.pickup(inv, incoming);
			check(ok2 && incoming.isEmpty() && inv.getItem(4).getItem() == Items.DIAMOND
							&& inv.getItem(4).getCount() == 5,
					"拾取锁定：可用格有空位 → 正常拾取",
					"收下=" + ok2 + "，快捷栏第 5 格=" + inv.getItem(4).getCount() + " 个钻石");

			// ③ 同类未达堆叠上限 → 继续补堆，余量仍留在地上
			inv.clearContent();
			inv.setItem(0, new ItemStack(Items.DIAMOND, 60));
			for (int i = 1; i < 9; i++) inv.setItem(i, new ItemStack(fillers[i], 64));
			ItemStack more = new ItemStack(Items.DIAMOND, 12);
			boolean ok3 = PickupGuard.pickup(inv, more);
			check(ok3 && inv.getItem(0).getCount() == 64 && more.getCount() == 8,
					"拾取锁定：同类未满栈继续补堆（余量留在地上）",
					"第 1 格=" + inv.getItem(0).getCount() + "，地上剩余=" + more.getCount());

			// ④ 未开启「禁用背包格子」→ 原版行为（允许落入 9~35）
			cm.disableChallenge(p.getUUID(), "inv_slot_lock");
			inv.clearContent();
			for (int i = 0; i < 9; i++) inv.setItem(i, new ItemStack(fillers[i], 64));
			ItemStack plain = new ItemStack(Items.DIAMOND, 3);
			boolean ok4 = PickupGuard.pickup(inv, plain);
			boolean fellIntoMain = false;
			for (int i = 9; i < 36; i++) if (inv.getItem(i).getItem() == Items.DIAMOND) fellIntoMain = true;
			check(ok4 && plain.isEmpty() && fellIntoMain,
					"拾取锁定：未开启规则 → 原版行为（可进 9~35）",
					"收下=" + ok4 + "，落入主背包=" + fellIntoMain);
		} finally {
			for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, prevInv.get(i));
			inv.setChanged();
			if (ic != null && prevCustom >= 0) ic.setCustomValue(prevCustom);
			cm.setPlayerChallenges(p.getUUID(), prevChallenges);
			gsm.setCurrentPhase(prevPhase);
		}
	}

	private static void check(boolean ok, String name, String detail) {
		if (ok) {
			passes++;
			SurvivalContract.LOGGER.info("[SC-TEST][PASS] {} — {}", name, detail);
		} else {
			FAILS.add(name + " — " + detail);
			SurvivalContract.LOGGER.error("[SC-TEST][FAIL] {} — {}", name, detail);
		}
	}

	/**
	 * 假连接：所有发包/协议切换为 no-op。
	 * 26.2 的 Connection.send 在 isConnected() 为 true 时会走 channel.eventLoop()（channel 为 null 时 NPE），
	 * 所以重写 isConnected 与全部 send 变体。
	 */
	static class FakeConnection extends Connection {
		FakeConnection() {
			super(PacketFlow.SERVERBOUND);
		}

		@Override
		public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T packetListener) {
			// no-op：不接入 netty 管线
		}

		@Override
		public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
			// no-op：不接入 netty 管线
		}

		@Override
		public boolean isConnected() {
			return true;
		}

		@Override
		public void send(Packet<?> packet) {
			// no-op：假人不收包
		}

		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener) {
			// no-op
		}

		@Override
		public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
			// no-op
		}

		@Override
		public void flushChannel() {
			// no-op：placeNewPlayer 结束时 resumeFlushing 会调 flushChannel → channel.eventLoop()
		}

		@Override
		public void disconnect(net.minecraft.network.chat.Component reason) {
			// no-op：默认实现 isConnected()=true 时会 channel.close()
		}

		@Override
		public void disconnect(net.minecraft.network.DisconnectionDetails details) {
			// no-op：同上
		}
	}
}
