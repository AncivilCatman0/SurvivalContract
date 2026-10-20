package com.survivalcontract.game;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.profession.ProfessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the overall game state: lobby phase, playing phase, team mode, scores, etc.
 */
public class GameStateManager {
	public static final GameStateManager INSTANCE = new GameStateManager();

	public enum GamePhase {
		LOBBY,          // Waiting in lobby, picking challenges and profession
		PLAYING,        // Game has started, players are in random seed world
		FINISHED        // Someone won, transitioning back to lobby
	}

	public enum GameMode {
		SOLO,           // Personal mode, each player independent
		TEAMS           // Red vs Blue teams
	}

	// ============ 玩法（初始菜单四选一） ============
	public enum PlayStyle {
		NORMAL,        // 常规模式（默认）
		GODS_LIES,     // 神的谎言：隐藏规则随机分配 + 使者身份博弈 + 淘汰机制
		STOWAWAY,      // 走私犯与检察官
		CIVILIZATION,  // 文明时代（未开放）
		ADDON          // 附属模组玩法（具体玩法由 addonStyleId 指向的 ScPlayStyleProvider 实现）
	}

	/**
	 * 文明时代自定义地形预设（第四十九批：大厅挑战书「自定义地形」二级菜单选择，开局建竞技场时应用；
	 * 全部为超平坦变体——无结构，世界规则与主世界预设完全通用）
	 */
	public enum TerrainPreset {
		OVERWORLD, // 预设-主世界（默认，超平坦「主世界」）
		MINER,     // 预设-挖掘工的梦想（厚矿层：煤/铁/铜/金/红石/青金石/钻石/绿宝石）
		SNOW,      // 预设-雪之王国（雪原群系）
		DESERT     // 预设-沙漠（沙漠群系）
	}

	private GamePhase currentPhase = GamePhase.LOBBY;
	private GameMode gameMode = GameMode.SOLO;
	private PlayStyle playStyle = PlayStyle.NORMAL;
	/** 文明时代自定义地形预设（默认主世界） */
	private TerrainPreset terrainPreset = TerrainPreset.OVERWORLD;

	public TerrainPreset getTerrainPreset() { return terrainPreset; }
	public void setTerrainPreset(TerrainPreset preset) {
		this.terrainPreset = preset == null ? TerrainPreset.OVERWORLD : preset;
	}

	// Scores: player UUID -> score
	private final Map<UUID, Integer> scores = new HashMap<>();

	// Team assignments (for TEAMS mode)
	public enum Team { RED, BLUE, NONE }
	private final Map<UUID, Team> playerTeams = new HashMap<>();

	// Final goal options
	public enum FinalGoal {
		ENDER_DRAGON("解放末地", "ender_dragon"),
		ENDER_END("结束了", "end_finish"),
		WITHER("凋零山庄", "wither");

		public final String displayName;
		public final String id;
		FinalGoal(String name, String id) {
			this.displayName = name;
			this.id = id;
		}
	}
	private FinalGoal currentFinalGoal = FinalGoal.ENDER_DRAGON;
	/** 最终目标「随机」：大厅里选中后不显示具体目标，开局时才从三个目标中随机揭晓 */
	private boolean randomFinalGoal = false;

	/** 是否处于「随机最终目标」待揭晓状态 */
	public boolean isRandomFinalGoal() { return randomFinalGoal; }
	public void setRandomFinalGoal(boolean random) { this.randomFinalGoal = random; }

	/** 开局揭晓：从三个最终目标中随机抽一个（仅处于随机状态时生效，揭晓后清除标记） */
	public void resolveRandomFinalGoal() {
		if (!randomFinalGoal) return;
		FinalGoal[] goals = {FinalGoal.ENDER_DRAGON, FinalGoal.ENDER_END, FinalGoal.WITHER};
		this.currentFinalGoal = goals[java.util.concurrent.ThreadLocalRandom.current().nextInt(goals.length)];
		this.randomFinalGoal = false;
		SurvivalContract.LOGGER.info("Random final goal resolved: {}", currentFinalGoal);
	}

	// Time limits (in minutes)
	private int lobbyTimeLimit = 120;  // Challenge book says 120 min
	private long gameStartTime = 0;
	private long gameTimeLimitTicks = 120L * 60 * 20; // 120 minutes in ticks (20 ticks/sec)
	private int gameDurationMinutes = 120; // 当前选择的时长（分钟）；-1 = 无限制

	// 全局规则开关（挑战书里的 rule_* 项，不计分）；团队箱默认开启（关闭 = 禁用 G 键/团队箱功能）
	private final java.util.Set<String> activeRules = new java.util.HashSet<>(java.util.Set.of("rule_team_chest"));
	public boolean isRuleActive(String ruleId) { return activeRules.contains(ruleId); }
	public void setRuleActive(String ruleId, boolean active) {
		if (active) activeRules.add(ruleId); else activeRules.remove(ruleId);
	}

	// 自选遭遇事件（挑战书里开启的事件才会触发；开启即加对应分数）
	private final java.util.Set<String> enabledEvents = new java.util.HashSet<>();
	// 个人模式下的每玩家独立事件选择（团队模式仍用全局 enabledEvents，队伍内互通）
	private final Map<UUID, java.util.Set<String>> playerEnabledEvents = new HashMap<>();
	public boolean isEventEnabled(String eventName) { return enabledEvents.contains(eventName); }
	public void setEventEnabled(String eventName, boolean active) {
		if (active) enabledEvents.add(eventName); else enabledEvents.remove(eventName);
	}
	public java.util.Set<String> getEnabledEvents() { return enabledEvents; }

	/** 该玩家自己的事件开关（个人模式=每玩家独立；团队模式=全局集合） */
	public boolean isPlayerEventEnabled(UUID playerId, String eventName) {
		if (isTeamMode()) return enabledEvents.contains(eventName);
		return playerEnabledEvents.getOrDefault(playerId, java.util.Set.of()).contains(eventName);
	}

	/** 个人模式下写入该玩家自己的事件选择；团队模式写全局（原行为） */
	public void applyPlayerEventToggle(UUID playerId, String eventName, boolean active) {
		if (isTeamMode()) { setEventEnabled(eventName, active); return; }
		var set = playerEnabledEvents.computeIfAbsent(playerId, k -> new java.util.HashSet<>());
		if (active) set.add(eventName); else set.remove(eventName);
	}

	/** 该玩家视角下的启用事件集合（用于 sync 下发与触发判定） */
	public java.util.Set<String> getEnabledEventsFor(UUID playerId) {
		if (isTeamMode()) return enabledEvents;
		return playerEnabledEvents.getOrDefault(playerId, java.util.Set.of());
	}

	/** 已启用事件的分数加成（按事件难度 S=15 / A=10 / B=5）——按玩家视角计算 */
	public int getEventScoreBonus(UUID playerId) {
		if (isAddon()) return 0; // 附属玩法：无事件系统，事件不计分（初始分 / 实际分均不含）
		if (isCivilization()) return 0; // 文明时代：事件选择不计分（纯玩法选择）
		int total = 0;
		for (String name : getEnabledEventsFor(playerId)) {
			try {
				total += com.survivalcontract.event.RandomEventManager.EventType.valueOf(name).score;
			} catch (IllegalArgumentException ignored) {}
		}
		return total;
	}

	public void clearPlayerEnabledEvents() { playerEnabledEvents.clear(); }
	public Map<UUID, java.util.Set<String>> getAllPlayerEnabledEvents() {
		Map<UUID, java.util.Set<String>> copy = new HashMap<>();
		playerEnabledEvents.forEach((k, v) -> copy.put(k, new java.util.HashSet<>(v)));
		return copy;
	}
	public void setPlayerEnabledEvents(UUID playerId, java.util.Set<String> events) {
		playerEnabledEvents.put(playerId, events);
	}

	// 遭遇事件刷新间隔（分钟）：eventId -> [minM, maxM]，全局设置（任意玩家修改全员同步）
	private final Map<String, int[]> eventIntervals = new HashMap<>();

	// 死亡扣分（0/1/2）：全局设置，PLAYING 阶段玩家每次死亡扣对应分数（可重复触发）
	private int deathPenalty = 0;

	// 胜利者违规免罚特权（跨局保留，用完为止）：uuid -> 剩余次数
	private final Map<UUID, Integer> victoryImmunities = new HashMap<>();

	// PvP toggle
	private boolean pvpEnabled = false;

	// Keep inventory toggle
	private boolean keepInventory = false;

	// 最终目标检测辅助状态
	private boolean enderDragonKilled = false;      // "结束了" 需要末影龙先被击杀
	private boolean witherKilled = false;           // "凋零山庄" 需要凋零击杀标记

	// Win/Finish 状态
	private FinalGoal winningGoal = null;
	// 是否因时间耗尽而结束（无人完成最终目标 → 所有玩家分数归零）
	private boolean endedByTimeout = false;

	// Playing world spawn origin (set by setupNewRandomWorld)
	private BlockPos playingSpawn = new BlockPos(0, 64, 0);

	// 每人固定重生点（团队模式=同队共享队点，间隔 15 区块；单人=个人点，间隔 2 区块）。床/锚可被玩家覆盖
	private final Map<UUID, BlockPos> respawnPoints = new HashMap<>();

	// World border radius (blocks from playingSpawn center)
	private int playingBorderRadius = 5000;

	// Night vision toggle
	private boolean globalNightVision = false;

	private MinecraftServer server;

	private GameStateManager() {}

	public static GameStateManager getInstance() {
		return INSTANCE;
	}

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		this.currentPhase = GamePhase.LOBBY;
		this.scores.clear();
		this.playerTeams.clear();
		// 跨局免罚特权恢复（独立文件，大厅/新局均保留）
		loadVictoryImmunities(SessionPersistence.loadImmunities(server));
	}

	public void onServerStop(MinecraftServer server) {
		this.server = null;
	}

	public MinecraftServer getServer() {
		return server;
	}

	public GamePhase getCurrentPhase() {
		return currentPhase;
	}

	public void setCurrentPhase(GamePhase phase) {
		this.currentPhase = phase;
		if (phase == GamePhase.LOBBY) {
			// 回大厅/初始化：挑战书回到初始菜单，清空上一局的多确认投票
			this.lobbyStage = 0;
			resetLobbyVotes();
		}
		SurvivalContract.LOGGER.info("Game phase changed to: {}", phase);
	}

	public GameMode getGameMode() {
		return gameMode;
	}

	public void setGameMode(GameMode mode) {
		this.gameMode = mode;
		SurvivalContract.LOGGER.info("Game mode set to: {}", mode);
	}

	public boolean isTeamMode() {
		return gameMode == GameMode.TEAMS;
	}

	public FinalGoal getCurrentFinalGoal() {
		return currentFinalGoal;
	}

	public void setCurrentFinalGoal(FinalGoal goal) {
		this.currentFinalGoal = goal;
	}

	public int getScore(UUID playerId) {
		// 第六十九批：分数低于 0 时显示为 0（内部仍保留真实值，淘汰判定不受影响）
		return Math.max(0, scores.getOrDefault(playerId, 0));
	}

	public void addScore(UUID playerId, int amount) {
		scores.merge(playerId, amount, Integer::sum);
		broadcastScoreChange(playerId, amount); // 全员可见的加分系统消息
		checkElimination(playerId); // 神的谎言：分数变动后检查淘汰
	}

	// ============ 玩法：神的谎言（GodsLies） ============
	public PlayStyle getPlayStyle() { return playStyle; }
	public void setPlayStyle(PlayStyle style) {
		this.playStyle = style == null ? PlayStyle.NORMAL : style;
		if (this.playStyle != PlayStyle.ADDON) this.addonStyleId = null;
	}
	public boolean isGodsLies() { return playStyle == PlayStyle.GODS_LIES; }
	public boolean isStowaway() { return playStyle == PlayStyle.STOWAWAY; }
	public boolean isCivilization() { return playStyle == PlayStyle.CIVILIZATION; }

	// ============ 附属玩法（Addon play style） ============
	/** 当前选中的附属玩法 id（仅 playStyle == ADDON 时有效） */
	private String addonStyleId = null;
	/** 附属玩法二级菜单选项值：option.key -> value（单局仅一个附属玩法，键唯一） */
	private final Map<String, Integer> styleOptions = new HashMap<>();

	/** 是否为附属模组玩法 */
	public boolean isAddon() { return playStyle == PlayStyle.ADDON; }

	public String getAddonStyleId() { return addonStyleId; }
	public void setAddonStyleId(String id) { this.addonStyleId = id; }

	/** 选中附属玩法（同时把 playStyle 置为 ADDON 并写入 id） */
	public void selectAddonStyle(String id) {
		this.playStyle = PlayStyle.ADDON;
		this.addonStyleId = id;
	}

	/**
	 * 同步/下发用的玩法键：附属玩法返回其 id（如 "hell-kitchen"），内置玩法返回枚举名。
	 * 客户端挑战书据此匹配已选中的玩法按钮。
	 */
	public String getPlayStyleKey() {
		return isAddon() && addonStyleId != null ? addonStyleId : playStyle.name();
	}

	/** 当前选中的附属玩法 provider（未选中/未安装返回 null） */
	public com.survivalcontract.api.ScPlayStyleProvider getAddonProvider() {
		return isAddon() ? com.survivalcontract.api.PlayStyleRegistry.byId(addonStyleId) : null;
	}

	public Map<String, Integer> getStyleOptions() { return new HashMap<>(styleOptions); }
	public int getStyleOption(String key, int defaultValue) {
		return styleOptions.getOrDefault(key, defaultValue);
	}
	public void setStyleOption(String key, int value) { styleOptions.put(key, value); }
	public void clearStyleOptions() { styleOptions.clear(); }

	/** 强制胜负（通用化：神的谎言身份博弈 / 文明时代大本营判负共用） */
	public void setForcedWinners(List<UUID> winners) { this.forcedWinners = new ArrayList<>(winners); }

	/** 使者数量（1~4，神的谎言二级菜单设置，默认 1） */
	private int envoyCount = 1;
	/** 使者 / 已淘汰玩家 / 指认权（平民）与降罪权（使者，每完成一个专属任务 +1） */
	private final java.util.Set<UUID> godEnvoys = new java.util.HashSet<>();
	private final java.util.Set<UUID> eliminatedPlayers = new java.util.HashSet<>();
	private final Map<UUID, Integer> accuseCharges = new HashMap<>();
	private final Map<UUID, Integer> deductCharges = new HashMap<>();
	/** 神的谎言胜负结果（结束广播用） */
	private List<UUID> forcedWinners = new ArrayList<>();
	private String godsLiesEndReason = null;

	public int getEnvoyCount() { return envoyCount; }
	public void setEnvoyCount(int count) { this.envoyCount = Math.max(1, Math.min(4, count)); }
	public boolean isGodEnvoy(UUID playerId) { return godEnvoys.contains(playerId); }
	public boolean isEliminated(UUID playerId) { return eliminatedPlayers.contains(playerId); }
	public int getGodEnvoysCount() { return godEnvoys.size(); }
	public String godsLiesEndReason() { return godsLiesEndReason; }

	/** 新一局/切回常规模式时重置神的谎言状态 */
	public void resetGodsLies() {
		godEnvoys.clear();
		eliminatedPlayers.clear();
		accuseCharges.clear();
		deductCharges.clear();
		relicObtained.clear();
		relicUsed.clear();
		relicImmunityArmed.clear();
		envoyLinks.clear();
		forcedWinners = new ArrayList<>();
		godsLiesEndReason = null;
	}

	/**
	 * 开局任命使者：从参战玩家（非大厅观战）中随机抽取 envoyCount 名（至少留 1 名平民）。
	 * 使者免疫所有规则 → 任命后立刻清空其规则集（违规扣分/物品没收/死限判定全部依赖规则集，
	 * 清空即全面免疫）。必须在 setupNewRandomWorld 计分之后调用（使者开局分 = 规则总分 100，与平民一致）。
	 */
	public void setupGodsLiesEnvoys(net.minecraft.server.MinecraftServer srv) {
		godEnvoys.clear();
		List<net.minecraft.server.level.ServerPlayer> pool = new ArrayList<>();
		for (net.minecraft.server.level.ServerPlayer p : srv.getPlayerList().getPlayers()) {
			if (!isLobbySpectator(p.getUUID())) pool.add(p);
		}
		java.util.Collections.shuffle(pool);
		int n = Math.min(envoyCount, Math.max(0, pool.size() - 1));
		for (int i = 0; i < n; i++) {
			godEnvoys.add(pool.get(i).getUUID());
			com.survivalcontract.challenge.ChallengeManager.getInstance()
					.clearPlayerChallenges(pool.get(i).getUUID());
		}
		SurvivalContract.LOGGER.info("[GodsLies] Envoys assigned: {} (count={})", godEnvoys.size(), envoyCount);
	}

	/** 任务完成奖励（神的谎言）：平民获得指认权、使者获得降罪权
	 *  （第七十批：同一时刻最多持有 1 次——已有可用次数时不累积，仅提示；用掉后完成任务可再次获得） */
	public void grantGodsLiesTaskReward(net.minecraft.server.level.ServerPlayer player) {
		if (!isGodsLies() || isEliminated(player.getUUID())) return;
		if (isGodEnvoy(player.getUUID())) {
			if (deductCharges.getOrDefault(player.getUUID(), 0) >= 1) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.godslies.charge_capped"));
				return;
			}
			deductCharges.put(player.getUUID(), 1);
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.godslies.task_reward_deduct", deductCharges.get(player.getUUID())));
		} else {
			if (accuseCharges.getOrDefault(player.getUUID(), 0) >= 1) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.godslies.charge_capped"));
				return;
			}
			accuseCharges.put(player.getUUID(), 1);
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.godslies.task_reward_accuse", accuseCharges.get(player.getUUID())));
		}
	}

	public int getAccuseCharges(UUID playerId) { return accuseCharges.getOrDefault(playerId, 0); }
	public int getDeductCharges(UUID playerId) { return deductCharges.getOrDefault(playerId, 0); }

	// ============ 神谕纸符（第五十九批：首次完成 A/S 级任务发放的消耗道具，材质为纸） ============
	/** 纸符物品名（客户端右键拦截 / 服务端消耗匹配 / 死亡保留均按此名） */
	public static final String RELIC_ITEM_NAME = "§6神谕纸符";
	/** 已获得纸符的玩家（每局最多获得一次） */
	private final java.util.Set<UUID> relicObtained = new java.util.HashSet<>();
	/** 已使用纸符的玩家（每局最多使用一次） */
	private final java.util.Set<UUID> relicUsed = new java.util.HashSet<>();
	/** 纸符神罚免疫已武装的平民（免疫一次使者神罚；不免疫指认失败） */
	private final java.util.Set<UUID> relicImmunityArmed = new java.util.HashSet<>();
	/** 使者的链接：使者 → 被链接玩家（一次性，首次被指认时触发） */
	private final Map<UUID, UUID> envoyLinks = new HashMap<>();

	public boolean hasRelicObtained(UUID playerId) { return relicObtained.contains(playerId); }
	public boolean isRelicUsed(UUID playerId) { return relicUsed.contains(playerId); }
	public boolean hasRelicImmunity(UUID playerId) { return relicImmunityArmed.contains(playerId); }
	public UUID getLinkedPlayer(UUID envoyId) { return envoyLinks.get(envoyId); }

	/**
	 * 首次完成 A/S 级专属任务 → 发放神谕纸符（每人每局最多一次；仅神的谎言 PLAYING 阶段）。
	 * B 级任务不发放；重复完成 A/S 不重复发放。
	 */
	public void grantGodsLiesRelic(net.minecraft.server.level.ServerPlayer player, boolean highTier) {
		if (!isGodsLies() || getCurrentPhase() != GamePhase.PLAYING) return;
		if (!highTier || isEliminated(player.getUUID())) return;
		if (!relicObtained.add(player.getUUID())) return;
		var relic = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 1);
		relic.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				net.minecraft.network.chat.Component.literal(RELIC_ITEM_NAME));
		if (!player.getInventory().add(relic)) com.survivalcontract.McCompat.drop(player, relic, false);
		player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
				"survival-contract.msg.godslies.relic_grant"));
	}

	// ============ 任务刷新券（神的谎言开局每人 1 个的一次性道具，材质为时钟） ============
	/** 刷新券物品名（客户端右键探测 / 服务端消耗匹配 / 死亡保留均按此名） */
	public static final String TASK_REFRESH_ITEM_NAME = "§e任务刷新券";

	/** 构造一个任务刷新券（时钟 + 自定义名；一次性，右键立即刷新当前专属任务） */
	public static net.minecraft.world.item.ItemStack createTaskRefreshToken() {
		var token = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK, 1);
		token.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				net.minecraft.network.chat.Component.literal(TASK_REFRESH_ITEM_NAME));
		return token;
	}

	/** 从背包移除一个任务刷新券（时钟 + 自定义名匹配），返回是否找到并移除 */
	public boolean consumeTaskRefreshToken(net.minecraft.server.level.ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var st = inv.getItem(i);
			if (!st.is(net.minecraft.world.item.Items.CLOCK)) continue;
			var name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
			if (name == null || !name.getString().contains("任务刷新券")) continue;
			if (st.getCount() <= 1) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			else st.setCount(st.getCount() - 1);
			return true;
		}
		return false;
	}

	/** 从背包移除一张神谕纸符（纸 + 自定义名匹配），返回是否找到并移除 */
	private boolean consumeRelicItem(net.minecraft.server.level.ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var st = inv.getItem(i);
			if (!st.is(net.minecraft.world.item.Items.PAPER)) continue;
			var name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
			if (name == null || !name.getString().contains("神谕纸符")) continue;
			if (st.getCount() <= 1) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			else st.setCount(st.getCount() - 1);
			return true;
		}
		return false;
	}

	/** 平民使用纸符：武装一次神罚免疫（挡使者降罪与链接神罚；不挡指认失败）。返回是否成功 */
	public boolean useRelicCivilian(net.minecraft.server.level.ServerPlayer player) {
		UUID id = player.getUUID();
		if (!isGodsLies() || getCurrentPhase() != GamePhase.PLAYING) return false;
		if (isGodEnvoy(id) || isEliminated(id)) return false;
		if (!relicObtained.contains(id) || !relicUsed.add(id)) return false;
		consumeRelicItem(player);
		relicImmunityArmed.add(id);
		sendPrivate(id, "survival-contract.msg.godslies.relic_armed");
		return true;
	}

	/** 使者确认链接一名玩家：消耗纸符并记录链接（一次性，首次被指认时触发）。返回是否成功 */
	public boolean confirmRelicLink(net.minecraft.server.level.ServerPlayer envoy, UUID target) {
		UUID id = envoy.getUUID();
		if (!isGodsLies() || getCurrentPhase() != GamePhase.PLAYING) return false;
		if (!isGodEnvoy(id) || isEliminated(id)) return false;
		if (target == null || target.equals(id) || isEliminated(target)) return false;
		if (!relicObtained.contains(id) || !relicUsed.add(id)) return false;
		consumeRelicItem(envoy);
		envoyLinks.put(id, target);
		sendPrivate(id, "survival-contract.msg.godslies.relic_link_set", nameOf(target));
		return true;
	}

	/** 私发系统消息（translatable，仅目标玩家可见；离线跳过） */
	private void sendPrivate(UUID playerId, String key, Object... args) {
		if (server == null) return;
		var p = server.getPlayerList().getPlayer(playerId);
		if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, args));
	}

	/** 玩家名（离线返回占位符） */
	private String nameOf(UUID playerId) {
		var p = server != null ? server.getPlayerList().getPlayer(playerId) : null;
		return p != null ? p.getName().getString() : "?";
	}

	/** 指认结果：命中使者（对方 -200 分）/ 未命中（自己 -200 分）——第六十九批 */
	public enum AccuseResult { HIT, MISS }

	/**
	 * 平民指认：消耗一次指认权（第六十九批新规则——取消原"最后使者免死"特例，
	 * 被指认的使者一律 -200 分；分数 ≤ 0 由 checkElimination 自动淘汰出局，
	 * 分数低于 0 时显示为 0）。
	 * <ul>
	 *   <li>未命中（目标非使者）→ 指认者 -200 分</li>
	 *   <li>命中使者 → 该使者 -200 分；其链接对象自动获得一次神罚（-55 分，
	 *       已武装纸符神罚免疫的平民被挡下——整体仅消耗指认次数，不扣分）</li>
	 * </ul>
	 */
	public AccuseResult resolveAccusation(UUID accuser, UUID target) {
		accuseCharges.merge(accuser, -1, Integer::sum);
		accuseCharges.remove(accuser, 0);
		if (!isGodEnvoy(target)) {
			deductScore(accuser, 200); // 指认失败：自己 -200（原为分数清零）
			return AccuseResult.MISS;
		}
		UUID linked = envoyLinks.remove(target); // 链接一次性：触发即清除
		deductScore(target, 200); // 指认使者成功：对方 -200（不再有免死特例）
		if (linked != null && !eliminatedPlayers.contains(linked)) {
			if (relicImmunityArmed.remove(linked)) {
				// 平民纸符优先级高：神罚被挡下（仅消耗指认次数）
				sendPrivate(linked, "survival-contract.msg.godslies.link_block");
				sendPrivate(target, "survival-contract.msg.godslies.link_block_envoy", nameOf(linked));
			} else {
				sendPrivate(linked, "survival-contract.msg.godslies.link_punish");
				deductScore(linked, 55);
			}
		}
		return AccuseResult.HIT;
	}

	/** 使者降罪：消耗一次降罪权，指定一名玩家扣 55 分（第五十四批：60 → 55）；
	 *  目标已武装纸符神罚免疫 → 神罚被挡下（免疫消耗，降罪权照扣） */
	public void envoyDeduct(UUID envoy, UUID target) {
		deductCharges.merge(envoy, -1, Integer::sum);
		deductCharges.remove(envoy, 0);
		if (relicImmunityArmed.remove(target)) {
			sendPrivate(target, "survival-contract.msg.godslies.relic_block_deduct");
			sendPrivate(envoy, "survival-contract.msg.godslies.relic_block_deduct_envoy", nameOf(target));
			return;
		}
		deductScore(target, 55);
	}

	// ============ 道具书/纸符死亡保留（检查发起书 / 指认书 / 神谕纸符） ============
	/** 暂存的道具（数量 + 原名 + 物品种类，重生按原名原物品返还） */
	private record KeptBook(int count, String name, net.minecraft.world.item.Item item) {}

	/**
	 * 玩家 → 死亡时暂存的道具列表（同一次死亡可暂存多件不同道具：指认书 + 神谕纸符 + 任务刷新券）。
	 * 早期实现用单值 Map + merge 合并，多种道具会被错误合并成一种（后到的 name/item 覆盖前面的）→
	 * 只返还一种、其余凭空消失（"有的玩家返还指认书、有的消失"的根因）。改用列表逐个暂存/返还。
	 */
	private final Map<UUID, List<KeptBook>> deathKeptBooks = new HashMap<>();

	/**
	 * 死亡掉落拦截（ServerPlayerDropMixin 死亡分支调用）：当前模式的道具
	 * （走私犯与检察官 = 检查发起书；神的谎言 = 指认书 / 神谕纸符 / 任务刷新券）死亡必定不掉落，
	 * 数量暂存，重生时 {@link #returnKeptBooksOnRespawn} 返还（keepInventory 开启时不走掉落路径）。
	 * 返回是否已拦截（true = 取消该次掉落）。
	 */
	public boolean handleModeBookDeathDrop(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
		if (stack.isEmpty() || !player.isDeadOrDying()) return false;
		KeptBook info = modeBookOf(stack);
		if (info == null) return false;
		// 多件道具逐个追加，不再合并（合并会把不同道具压成一种，导致只返还一种）
		deathKeptBooks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(info);
		return true;
	}

	/** 当前模式道具判定：按自定义名称匹配（发放的道具即此名）；返回暂存模板，非道具返回 null */
	private KeptBook modeBookOf(net.minecraft.world.item.ItemStack stack) {
		if (currentPhase != GamePhase.PLAYING) return null;
		var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		if (name == null) return null;
		String text = name.getString();
		if (isStowaway() && text.contains("检查发起书"))
			return new KeptBook(stack.getCount(), "§b检查发起书", net.minecraft.world.item.Items.BOOK);
		if (isGodsLies() && text.contains("指认书"))
			return new KeptBook(stack.getCount(), "§5指认书", net.minecraft.world.item.Items.BOOK);
		if (isGodsLies() && text.contains("神谕纸符"))
			return new KeptBook(stack.getCount(), RELIC_ITEM_NAME, net.minecraft.world.item.Items.PAPER);
		if (isGodsLies() && text.contains("任务刷新券"))
			return new KeptBook(stack.getCount(), TASK_REFRESH_ITEM_NAME, net.minecraft.world.item.Items.CLOCK);
		return null;
	}

	/** 重生返还暂存的道具（全部逐个返还；无暂存则无事发生；按暂存原名原物品重铸，背包满则掉在脚边） */
	public void returnKeptBooksOnRespawn(ServerPlayer newPlayer, boolean alive) {
		List<KeptBook> kept = deathKeptBooks.remove(newPlayer.getUUID());
		if (kept == null || kept.isEmpty()) return;
		for (KeptBook k : kept) {
			if (k.count() <= 0) continue;
			var item = new net.minecraft.world.item.ItemStack(k.item(), k.count());
			item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					net.minecraft.network.chat.Component.literal(k.name()));
			if (!newPlayer.getInventory().add(item)) com.survivalcontract.McCompat.drop(newPlayer, item, false);
		}
	}

	/** 分数变动后检查淘汰（神的谎言）：分数 ≤ 0 → 淘汰进入旁观者模式，随后检查胜负 */
	private void checkElimination(UUID playerId) {
		eliminatePlayerNow(playerId);
		checkGodsLiesWinCondition();
	}

	/** 淘汰标记（分数 ≤ 0 → 进入旁观者模式并广播）；不触发胜负判定（同帧多淘汰场景由调用方统一判定） */
	private void eliminatePlayerNow(UUID playerId) {
		// 仅对局内淘汰：大厅/结束阶段的加减分（如大厅关规则扣分、结算扣分）不得改身份或旁观者模式，
		// 否则 playStyle 仍为神的谎言时会按 0 分误淘汰全员并设为旁观者 → 污染下一局（"扣分影响下一局"根因）
		if (currentPhase != GamePhase.PLAYING) return;
		if (!isGodsLies() || playerId == null || eliminatedPlayers.contains(playerId)) return;
		if (getScore(playerId) > 0) return;
		eliminatedPlayers.add(playerId);
		net.minecraft.server.level.ServerPlayer p = server != null
				? server.getPlayerList().getPlayer(playerId) : null;
		if (p != null) {
			p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			broadcastGodsLies("survival-contract.msg.godslies.eliminated", p.getName().getString());
		}
	}

	/** 全员可见的系统消息（translatable，客户端按各自语言渲染） */
	private void broadcastGodsLies(String key, Object... args) {
		if (server == null) return;
		var msg = net.minecraft.network.chat.Component.translatable(key, args);
		for (var op : server.getPlayerList().getPlayers()) op.sendSystemMessage(msg);
	}

	/**
	 * 胜负判定（每次淘汰后调用，开局平局不触发）：
	 * 使者全部淘汰 → 存活平民中分数最高的至多 2 人获胜；
	 * 存活平民数量与存活使者数量一致（或平民已全部淘汰）→ 使者获胜。
	 */
	public void checkGodsLiesWinCondition() {
		if (!isGodsLies() || currentPhase != GamePhase.PLAYING || godsLiesEndReason != null) return;
		List<UUID> aliveEnvoys = new ArrayList<>();
		for (UUID id2 : godEnvoys) if (!eliminatedPlayers.contains(id2)) aliveEnvoys.add(id2);
		List<UUID> aliveCivs = new ArrayList<>();
		for (var e : scores.entrySet()) {
			if (!godEnvoys.contains(e.getKey()) && !eliminatedPlayers.contains(e.getKey())) aliveCivs.add(e.getKey());
		}
		if (aliveEnvoys.isEmpty()) {
			aliveCivs.sort((a, b) -> Integer.compare(getScore(b), getScore(a)));
			forcedWinners = new ArrayList<>(aliveCivs.subList(0, Math.min(2, aliveCivs.size())));
			godsLiesEndReason = "envoys_eliminated";
			endGame(FinalGoal.WITHER); // 仅作结束标记，胜者文案由神的谎言分支输出
		} else if (aliveCivs.isEmpty() || aliveCivs.size() == aliveEnvoys.size()) {
			forcedWinners = new ArrayList<>(aliveEnvoys);
			godsLiesEndReason = "envoys_victory";
			endGame(FinalGoal.WITHER);
		}
	}

	// ============ 大厅挑战书初始菜单：阶段 + 多人确认投票 ============
	// stage 0 = 初始菜单（选模式/看玩家列表/"我决定好了"）；stage 1 = 规则菜单（当前书界面）
	// 多人模式下（大厅人数 ≥ 2）"我决定好了"与"开始游戏"各需 2 名不同玩家点击
	private int lobbyStage = 0;
	private final java.util.Set<UUID> lobbyConfirmVotes = new java.util.HashSet<>();
	private final java.util.Set<UUID> lobbyStartVotes = new java.util.HashSet<>();
	private final java.util.Set<UUID> lobbyBackVotes = new java.util.HashSet<>();

	public int getLobbyStage() { return lobbyStage; }
	public void setLobbyStage(int stage) { this.lobbyStage = stage; }

	/** 登记一次"我决定好了"点击（按玩家去重），返回当前累计人数 */
	public int registerLobbyConfirm(UUID playerId) {
		lobbyConfirmVotes.add(playerId);
		return lobbyConfirmVotes.size();
	}

	/** 登记一次"开始游戏"点击（按玩家去重），返回当前累计人数 */
	public int registerLobbyStart(UUID playerId) {
		lobbyStartVotes.add(playerId);
		return lobbyStartVotes.size();
	}

	/** 登记一次"返回上级菜单"点击（按玩家去重），返回当前累计人数 */
	public int registerLobbyBack(UUID playerId) {
		lobbyBackVotes.add(playerId);
		return lobbyBackVotes.size();
	}

	public void resetLobbyVotes() {
		lobbyConfirmVotes.clear();
		lobbyStartVotes.clear();
		lobbyBackVotes.clear();
	}

	public int getLobbyConfirmCount() { return lobbyConfirmVotes.size(); }
	public int getLobbyStartCount() { return lobbyStartVotes.size(); }

	/** 多人确认所需人数：大厅在线 ≥2 人时需 2 名不同玩家，单人只需自己（观战者不计入） */
	public int lobbyVoteNeeded(MinecraftServer server) {
		long online = server.getPlayerList().getPlayers().stream()
				.filter(p -> !isLobbySpectator(p.getUUID())).count();
		return (int) Math.min(2, Math.max(1, online));
	}

	public void deductScore(UUID playerId, int amount) {
		// 文明时代：环境/事件规则不计分，扣分同样跳过（分数只来自资源回收）
		if (isCivilization()) return;
		scores.merge(playerId, -amount, Integer::sum);
		broadcastScoreChange(playerId, -amount); // 全员可见的扣分系统消息
		checkElimination(playerId); // 神的谎言：分数变动后检查淘汰
	}

	/** 分数变动聊天广播：所有人都能看到某玩家的加分/扣分（translatable → 客户端按各自语言解析） */
	private void broadcastScoreChange(UUID playerId, int delta) {
		if (delta == 0 || server == null) return;
		var p = server.getPlayerList().getPlayer(playerId);
		if (p == null) return; // 离线玩家不广播
		net.minecraft.network.chat.Component msg = delta > 0
				? net.minecraft.network.chat.Component.translatable("survival-contract.msg.score_gain",
						p.getName().getString(), delta)
				: net.minecraft.network.chat.Component.translatable("survival-contract.msg.score_loss",
						p.getName().getString(), delta);
		for (var op : server.getPlayerList().getPlayers()) {
			op.sendSystemMessage(msg);
		}
	}

	public void resetScores() {
		scores.clear();
		deathKeptBooks.clear(); // 新一局：道具书死亡暂存清零（开局背包本就清空重建）
	}

	public void resetPlayerTeams() {
		playerTeams.clear();
	}

	public Map<UUID, Integer> getAllScores() {
		return new HashMap<>(scores);
	}

	public Team getPlayerTeam(UUID playerId) {
		return playerTeams.getOrDefault(playerId, Team.NONE);
	}

	/** 双禁记录：队伍 → 被禁入对面队的挑战 id（每队一条，不同队不能重复） */
	private final Map<Team, String> teamBans = new HashMap<>();

	public Map<Team, String> getTeamBans() { return teamBans; }

	public void setTeamBan(Team team, String challengeId) { teamBans.put(team, challengeId); }

	/** 双禁事件：该遭遇事件被定向禁给了哪个队伍（禁用方队伍的对面）；null = 非双禁事件 */
	public Team getEventBanTeam(String eventName) {
		String id = "event:" + eventName;
		for (var e : teamBans.entrySet()) {
			if (e.getValue().equals(id)) {
				return e.getKey() == Team.RED ? Team.BLUE
						: e.getKey() == Team.BLUE ? Team.RED : null;
			}
		}
		return null;
	}

	/**
	 * 计算玩家初始分数（排除被双禁进来的规则 —— 双禁规则/事件无分数，仅违规扣分）。
	 */
	public int calculateInitialScoreExcludingBans(UUID playerId) {
		com.survivalcontract.challenge.ChallengeManager cm = com.survivalcontract.challenge.ChallengeManager.getInstance();
		// 附属玩法：无自选规则/职业/双禁 → 初始分恒为 0（由玩法自身计分，与 startGame 中 setScore(0) 一致）
		if (isAddon()) return 0;
		// 文明时代：所有玩家 0 分起步，仅靠资源回收赚积分（环境/事件选项不计分）
		if (isCivilization()) return 0;
		int total = cm.calculateInitialScore(playerId);
		// 无职业加成：初始职业为"无职业"时额外 +10 分（其余职业不加分）
		// 神的谎言模式：无职业加成停用，保证系统随机分配的规则总分 = 每人初始分（100 分）
		if (!isGodsLies()
				&& com.survivalcontract.profession.ProfessionManager.getInstance().getPlayerProfession(playerId)
				== com.survivalcontract.profession.ProfessionManager.Profession.NONE) {
			total += 10;
		}
		Team t = getPlayerTeam(playerId);
		if (t == Team.RED || t == Team.BLUE) {
			Team enemy = t == Team.RED ? Team.BLUE : Team.RED;
			String ban = teamBans.getOrDefault(enemy, "");
			// ban 是"对面队伍禁给我们"的 id；事件不进计分（getEventScoreBonus 天然不含）
			if (!ban.isEmpty() && !ban.startsWith("event:")) {
				var c = cm.getChallenge(ban);
				if (c != null) {
					total -= c.isCustomizable() && c.getCustomValue() > 0
							? Math.round(c.getBaseScore() * c.getCustomValue() / 50.0f)
							: c.getBaseScore();
				}
			}
		}
		return total;
	}

	/** 获取某队全部成员 */
	public java.util.List<java.util.UUID> getTeamMembers(Team team) {
		java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
		for (var e : playerTeams.entrySet()) if (e.getValue() == team) out.add(e.getKey());
		return out;
	}

	public void setPlayerTeam(UUID playerId, Team team) {
		playerTeams.put(playerId, team);
	}

	public Map<UUID, Team> getAllTeams() {
		return new HashMap<>(playerTeams);
	}

	// ============ 大厅观战模式（/sc spectate）：不参与计分与分队 ============
	private final Set<UUID> lobbySpectators = new HashSet<>();
	public boolean isLobbySpectator(UUID playerId) { return lobbySpectators.contains(playerId); }
	public void addLobbySpectator(UUID playerId) { lobbySpectators.add(playerId); }
	public void removeLobbySpectator(UUID playerId) { lobbySpectators.remove(playerId); }

	/** 清空全部大厅观战标记（每局结束回大厅时调用——所有观战者自动解除观战，含离线者）。 */
	public void clearLobbySpectators() { lobbySpectators.clear(); }

	public java.util.Collection<Team> getPlayerTeams() {
		return playerTeams.values();
	}

	public FinalGoal winningGoal() { return winningGoal; }
	public void setWinningGoal(FinalGoal g) { this.winningGoal = g; }
	public boolean isEndedByTimeout() { return endedByTimeout; }
	public void setEndedByTimeout(boolean b) { this.endedByTimeout = b; }
	public void resetFlags() {
		this.enderDragonKilled = false;
		this.witherKilled = false;
		this.winningGoal = null;
		this.endedByTimeout = false;
		this.teamBans.clear(); // 双禁锁定随游戏结束一并解除
		this.respawnPoints.clear(); // 固定重生点随游戏结束一并清除
		this.clearStyleOptions(); // 附属玩法选项回默认（下一局由大厅菜单重新选择）
	}

	public int getTeamScore(Team team) {
		int total = 0;
		for (Map.Entry<UUID, Team> entry : playerTeams.entrySet()) {
			if (entry.getValue() == team) {
				total += getScore(entry.getKey());
			}
		}
		return total;
	}

	public long getGameStartTime() {
		return gameStartTime;
	}

	public void setGameStartTime(long time) {
		this.gameStartTime = time;
	}

	public long getGameTimeLimitTicks() {
		return gameTimeLimitTicks;
	}

	public void setGameTimeLimitMinutes(int minutes) {
		this.gameTimeLimitTicks = minutes * 60L * 20L;
	}

	/** 当前游戏时长设置（分钟）；-1 = 无限制 */
	public int getGameDurationMinutes() {
		return gameDurationMinutes;
	}

	/** 设置游戏时长（分钟）；-1 = 无限制。在开始游戏前由 GUI 选择 */
	public void setGameDurationMinutes(int minutes) {
		this.gameDurationMinutes = minutes;
		this.gameTimeLimitTicks = minutes < 0 ? Long.MAX_VALUE / 4 : minutes * 60L * 20L;
	}

	public boolean isPvpEnabled() {
		return pvpEnabled;
	}

	public void setPvpEnabled(boolean enabled) {
		this.pvpEnabled = enabled;
	}

	public boolean isKeepInventory() {
		return keepInventory;
	}

	public void setKeepInventory(boolean enabled) {
		this.keepInventory = enabled;
	}

	public boolean isGlobalNightVision() {
		return globalNightVision;
	}

	public void setGlobalNightVision(boolean enabled) {
		this.globalNightVision = enabled;
	}

	public int getLobbyTimeLimit() {
		return lobbyTimeLimit;
	}

	public void setLobbyTimeLimit(int minutes) {
		this.lobbyTimeLimit = minutes;
	}

	/** Called when a player respawns */
	public void onPlayerRespawn(ServerPlayer player, boolean alive) {
		// Respawn logic will be handled here
	}

	/** Get remaining time in ticks. 0 unless a game is currently PLAYING. 无限制时长返回 -1。 */
	public long getRemainingTimeTicks() {
		if (server == null || currentPhase != GamePhase.PLAYING) return 0;
		if (gameDurationMinutes < 0) return -1; // 无限制
		// 开局冻结期（gameStartTime 晚于当前 tick）不计入游戏时间 → 顶部时间栏静止满值不动
		long elapsed = Math.max(0, server.getTickCount() - gameStartTime);
		return Math.max(0, gameTimeLimitTicks - elapsed);
	}

	/** 开局冻结剩余秒数（向上取整）；0 = 未处于冻结期。客户端据此暂停本地倒计时递减 */
	public int getFreezeSecondsRemaining() {
		if (server == null || currentPhase != GamePhase.PLAYING) return 0;
		long remain = gameStartTime - server.getTickCount();
		return remain > 0 ? (int) ((remain + 19) / 20) : 0;
	}

	/**
	 * 为 PLAYING 阶段准备世界：
	 * 1. 生成随机远距离坐标（远离 spawn 点，避免玩家第一次进入就看到已加载的区块）
	 * 2. 用 ServerLevel.findBestSpawnPosition 找安全落脚 y 坐标
	 * 3. 画 WorldBorder 限制生存范围（radius 2000）
	 * 4. 初始化玩家初始分数（从 ChallengeManager.calculateInitialScore）
	 */
	public void setupNewRandomWorld(MinecraftServer server) {
		SurvivalContract.LOGGER.info("Setting up PLAYING world...");

		// PLAYING 世界 = 竞技场主世界（每局新建的随机种子维度组）
		ServerLevel overworld = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (overworld == null) {
			SurvivalContract.LOGGER.error("Arena overworld missing! Falling back to vanilla overworld.");
			overworld = server.overworld();
		}

		// 生成随机 X,Z：出生点 x/z 锁定在 [-300, 300] 区间内（用户要求）
		long seed = overworld.getSeed();
		java.util.Random rnd = new java.util.Random(seed ^ System.currentTimeMillis());
		int randomX = rnd.nextInt(601) - 300;
		int randomZ = rnd.nextInt(601) - 300;

		// === 地表安全出生点：不能在空中、水里、方块里 ===
		// 用高度图取地表 y，验证"脚下实心非水 + 身位头部都是空气"，不合格则螺旋向外搜索
		BlockPos spawnPos = findSafeSurfaceSpawn(overworld, randomX, randomZ);
		playingSpawn = spawnPos;
		assignRespawnPoints(server, overworld);
		SurvivalContract.LOGGER.info("PLAYING world spawn: {} (y={})", playingSpawn, spawnPos.getY());

		// WorldBorder 生存护栏已按需求取消：不再缩放边界（回大厅时仍会重置为默认尺寸，
		// 兼容此前版本画过的窄边界残留）

		// 初始化玩家分数（已启用挑战 baseScore + 该玩家自己启用的事件分数）
		// 大厅观战者开局前先清出分数表：进入观战时曾以 setScore(0) 写入（大厅计分板显示用），
		// 若不移除，hasPlayerState 会误判其为参战者——开局 teleportSpectatorToArena 的防御分支
		// 会把其当作"掉线重连的参战者"送回重生点（生存模式），旁观者观战失效的根因
		lobbySpectators.forEach(scores::remove);
		com.survivalcontract.challenge.ChallengeManager cm = com.survivalcontract.challenge.ChallengeManager.getInstance();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			// 大厅观战者：不参战 → 不初始化分数（保持无参战者状态，开局以旁观者跟随观战）
			if (isLobbySpectator(p.getUUID())) continue;
			int eventBonus = getEventScoreBonus(p.getUUID());
			int initial = calculateInitialScoreExcludingBans(p.getUUID()) + eventBonus;
			this.scores.put(p.getUUID(), initial);
			SurvivalContract.LOGGER.info("Player {} initial score: {} (事件加成 +{})", p.getName().getString(), initial, eventBonus);
		}

		SurvivalContract.LOGGER.info("PLAYING world ready!");
	}

	/**
	 * 在 (x,z) 附近找一个严格的地表安全出生点：
	 * 脚下方块实心且不是水/流体，脚和头两格都是纯空气。
	 * 第一轮螺旋搜索 160 格；失败后大范围随机重试 8 次（跳出海洋/湖面）；
	 * 全部失败才退回 heightmap 原点（兜底，正常不会走到）。
	 */
	private static BlockPos findSafeSurfaceSpawn(ServerLevel level, int x, int z) {
		BlockPos found = searchNear(level, x, z, 160, 8);
		if (found != null) return found;
		SurvivalContract.LOGGER.warn("No safe spawn near ({},{}), retrying with random offsets...", x, z);
		java.util.Random rnd = new java.util.Random();
		for (int i = 0; i < 8; i++) {
			// 重试偏移限制在小范围并钳制到 [-300, 300]，保证最终坐标不越界
			int rx = Math.max(-300, Math.min(300, x + rnd.nextInt(300) - 150));
			int rz = Math.max(-300, Math.min(300, z + rnd.nextInt(300) - 150));
			found = searchNear(level, rx, rz, 128, 16);
			if (found != null) {
				SurvivalContract.LOGGER.info("Safe spawn found at random retry #{}: ({},{})", i + 1, rx, rz);
				return found;
			}
		}
		SurvivalContract.LOGGER.warn("No perfect surface spawn found, using heightmap fallback (risk of water surface!)");
		forceLoadChunk(level, x, z);
		return new BlockPos(x, level.getHeight(SPAWN_HEIGHTMAP, x, z), z);
	}

	private static final net.minecraft.world.level.levelgen.Heightmap.Types SPAWN_HEIGHTMAP =
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;

	/** 环边螺旋搜索安全落点（每圈 step 格，最大 maxR 格），找到即返回，找不到返回 null */
	private static BlockPos searchNear(ServerLevel level, int x, int z, int maxR, int step) {
		for (int r = step; r <= maxR; r += step) {
			for (int dx = -r; dx <= r; dx += step) {
				for (int dz = -r; dz <= r; dz += step) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // 只搜环边
					int sx = x + dx, sz = z + dz;
					forceLoadChunk(level, sx, sz);
					int sy = level.getHeight(SPAWN_HEIGHTMAP, sx, sz);
					if (isSafeSurface(level, sx, sy, sz)) {
						SurvivalContract.LOGGER.info("Spawn offset search: moved ({},{}) to ({},{})", x, z, sx, sz);
						return new BlockPos(sx, sy, sz);
					}
				}
			}
		}
		return null;
	}

	/** 强制同步加载所在区块（保证高度图有效） */
	private static void forceLoadChunk(ServerLevel level, int x, int z) {
		try {
			level.getChunk(x >> 4, z >> 4);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Chunk load failed at ({},{}): {}", x, z, e.getMessage());
		}
	}

	/** 地表安全判定：脚下实心且无流体，脚+头两格均为纯空气（不在水里、不在方块里、不在空中悬空） */
	private static boolean isSafeSurface(ServerLevel level, int x, int y, int z) {
		if (y < level.getMinY() + 1 || y >= level.getMaxY() - 1) return false;
		var below = level.getBlockState(new BlockPos(x, y - 1, z));
		var feet = level.getBlockState(new BlockPos(x, y, z));
		var head = level.getBlockState(new BlockPos(x, y + 1, z));
		return below.isSolid() && below.getFluidState().isEmpty()
				&& feet.isAir() && head.isAir();
	}

	/**
	 * 分配每人固定重生点：
	 * <ul>
	 *   <li>团队模式：红/蓝各一个队点，两点间隔 15 区块（240 格，主出生点 z±120）；同队共享队点</li>
	 *   <li>单人模式：每人一个个人点，相邻间隔 2 区块（32 格）沿 x 轴排布；
	 *       走私犯与检察官模式间隔 5 区块（80 格，且逐点保证最小间距）</li>
	 * </ul>
	 * 每个点均做严格安全落点检查（脚下实心非流体+两格空气）。
	 */
	private void assignRespawnPoints(MinecraftServer server, ServerLevel level) {
		respawnPoints.clear();
		List<ServerPlayer> players = new java.util.ArrayList<>(server.getPlayerList().getPlayers());
		if (isTeamMode()) {
			BlockPos redBase = findSafeNear(level, playingSpawn.offset(0, 0, 120));
			// 蓝队点以红队点为锚：只接受与红队点间距 >= 240 的严格安全落点（防止安全搜索漂移缩短间距）
			BlockPos blueBase = findSafeAtMinDistance(level, redBase.getX(), redBase.getZ() - 240, redBase, 240);
			SurvivalContract.LOGGER.info("Team respawn points: red={} blue={} (>=240 blocks apart)",
					redBase, blueBase);
			for (ServerPlayer p : players) {
				if (isLobbySpectator(p.getUUID())) continue;
				Team t = getPlayerTeam(p.getUUID());
				respawnPoints.put(p.getUUID(), t == Team.BLUE ? blueBase : redBase);
				SurvivalContract.LOGGER.info("Respawn assign: {} team={} -> {}",
						p.getName().getString(), t, t == Team.BLUE ? blueBase : redBase);
			}
		} else {
			// 走私犯与检察官：个人出生点相距 5 区块（80 格）
			int spacing = isStowaway() ? 80 : 32;
			BlockPos prev = null;
			int i = 0;
			for (ServerPlayer p : players) {
				if (isLobbySpectator(p.getUUID())) continue;
				BlockPos base = playingSpawn.offset(i * spacing, 0, 0);
				BlockPos pt = prev == null
						? findSafeNear(level, base)
						: findSafeAtMinDistance(level, base.getX(), base.getZ(), prev, spacing);
				respawnPoints.put(p.getUUID(), pt);
				prev = pt;
				i++;
			}
		}
	}

	/** base 附近 64 格内找严格安全落点，找不到就取 base 的 heightmap 原点 */
	private static BlockPos findSafeNear(ServerLevel level, BlockPos base) {
		BlockPos found = searchNear(level, base.getX(), base.getZ(), 64, 8);
		if (found != null) return found;
		forceLoadChunk(level, base.getX(), base.getZ());
		return new BlockPos(base.getX(), level.getHeight(SPAWN_HEIGHTMAP, base.getX(), base.getZ()), base.getZ());
	}

	/**
	 * 环边搜索严格安全落点，且只接受与 {@code anchor} 二维距离 >= {@code minDist} 的点
	 * （团队重生点专用：安全搜索只向远离锚点的一侧漂移，保证队点间距不被缩短）。
	 * 第一轮 96 格、第二轮 192 格；全失败才退回理想点 heightmap（保间距，陆地世界几乎不会触发）。
	 */
	private static BlockPos findSafeAtMinDistance(ServerLevel level, int x, int z, BlockPos anchor, int minDist) {
		BlockPos found = searchNearMinDistance(level, x, z, 96, 8, anchor, minDist);
		if (found != null) return found;
		found = searchNearMinDistance(level, x, z, 192, 16, anchor, minDist);
		if (found != null) return found;
		SurvivalContract.LOGGER.warn("No safe team point >= {} from {} near ({},{}), heightmap fallback", minDist, anchor, x, z);
		forceLoadChunk(level, x, z);
		return new BlockPos(x, level.getHeight(SPAWN_HEIGHTMAP, x, z), z);
	}

	/** 同 {@link #searchNear}，额外要求候选点与 anchor 的二维距离 >= minDist */
	private static BlockPos searchNearMinDistance(ServerLevel level, int x, int z, int maxR, int step,
			BlockPos anchor, int minDist) {
		long minDistSqr = (long) minDist * minDist;
		for (int r = step; r <= maxR; r += step) {
			for (int dx = -r; dx <= r; dx += step) {
				for (int dz = -r; dz <= r; dz += step) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // 只搜环边
					int sx = x + dx, sz = z + dz;
					long ddx = (long) sx - anchor.getX(), ddz = (long) sz - anchor.getZ();
					if (ddx * ddx + ddz * ddz < minDistSqr) continue;
					forceLoadChunk(level, sx, sz);
					int sy = level.getHeight(SPAWN_HEIGHTMAP, sx, sz);
					if (isSafeSurface(level, sx, sy, sz)) {
						return new BlockPos(sx, sy, sz);
					}
				}
			}
		}
		return null;
	}

	/** 玩家固定重生点（无设置时回退主出生点） */
	public BlockPos getRespawnPoint(UUID playerId) {
		return respawnPoints.getOrDefault(playerId, playingSpawn);
	}

	/** 该玩家是否为本局参战者（开局时初始化过分数）— 区分"掉线重连的参战者"与"游戏进行中新加入的玩家" */
	public boolean hasPlayerState(UUID playerId) {
		return scores.containsKey(playerId);
	}

	public BlockPos getPlayingSpawn() { return playingSpawn; }
	public int getPlayingBorderRadius() { return playingBorderRadius; }
	public void setPlayingSpawn(BlockPos pos) { this.playingSpawn = pos; }
	public void setPlayingBorderRadius(int radius) { this.playingBorderRadius = radius; }

	// ============ 会话持久化（退出重进保留局内状态）所需的存取器 ============
	public java.util.Set<String> getActiveRules() { return activeRules; }

	// ============ 遭遇事件刷新间隔（全局可调，默认 7-10 分钟） ============
	/** 获取某事件的刷新间隔 [minM, maxM]（分钟）；未自定义时返回默认 7-10 */
	public int[] getEventInterval(String eventId) {
		int[] r = eventIntervals.get(eventId);
		return r != null ? new int[]{r[0], r[1]} : new int[]{7, 10};
	}

	/**
	 * 调整某事件的刷新间隔：A（下限）定义域 [0,15]、B（上限）定义域 [5,20]，均取整数，
	 * 且保证 A < B。全局设置，调用方负责全员 sendSync。
	 */
	public void adjustEventInterval(String eventId, boolean adjustMin, int delta) {
		int[] cur = getEventInterval(eventId);
		int minM = cur[0] + (adjustMin ? delta : 0);
		int maxM = cur[1] + (adjustMin ? 0 : delta);
		minM = Math.max(0, Math.min(15, minM));
		maxM = Math.max(5, Math.min(20, maxM));
		if (minM >= maxM) minM = maxM - 1;
		eventIntervals.put(eventId, new int[]{minM, maxM});
	}

	/** 全部已自定义的间隔（持久化用；未自定义的事件不在 map 中，读取侧回退默认 7-10） */
	public Map<String, int[]> getAllEventIntervals() {
		Map<String, int[]> copy = new HashMap<>();
		eventIntervals.forEach((k, v) -> copy.put(k, new int[]{v[0], v[1]}));
		return copy;
	}

	public void setEventIntervals(Map<String, int[]> map) {
		eventIntervals.clear();
		map.forEach((k, v) -> eventIntervals.put(k, new int[]{v[0], v[1]}));
	}

	// ============ 死亡扣分（全局可调 0/1/2） ============
	public int getDeathPenalty() { return deathPenalty; }
	public void setDeathPenalty(int v) { this.deathPenalty = Math.max(0, Math.min(2, v)); }

	// ============ 胜利者违规免罚特权（跨局保留，用完为止） ============
	/** 结算胜利者并授予免罚次数（每次胜利 +2，同一名玩家可累加） */
	public void grantVictoryImmunity(List<UUID> winners, int charges) {
		if (winners.isEmpty()) return;
		for (UUID id : winners) victoryImmunities.merge(id, charges, Integer::sum);
		SessionPersistence.saveImmunities(victoryImmunities);
	}

	public int getVictoryImmunity(UUID playerId) { return victoryImmunities.getOrDefault(playerId, 0); }

	/** 消耗一次免罚特权（用完自动移除） */
	public void consumeVictoryImmunity(UUID playerId) {
		victoryImmunities.computeIfPresent(playerId, (k, v) -> v <= 1 ? null : v - 1);
		SessionPersistence.saveImmunities(victoryImmunities);
	}

	/** 按最终得分取胜利者：单人 = 最高分玩家（并列全取）；团队 = 总分最高队伍全体成员；神的谎言 = 胜负判定给出的强制胜者 */
	public List<UUID> getWinnersByScore() {
		if (!forcedWinners.isEmpty()) return new ArrayList<>(forcedWinners);
		List<UUID> out = new ArrayList<>();
		if (isTeamMode()) {
			int red = getTeamScore(Team.RED), blue = getTeamScore(Team.BLUE);
			if (red == blue) {
				out.addAll(getTeamMembers(Team.RED));
				out.addAll(getTeamMembers(Team.BLUE));
			} else {
				out.addAll(getTeamMembers(red > blue ? Team.RED : Team.BLUE));
			}
		} else {
			int top = Integer.MIN_VALUE;
			for (int v : scores.values()) top = Math.max(top, v);
			for (var e : scores.entrySet()) if (e.getValue() == top) out.add(e.getKey());
		}
		return out;
	}

	/** 服务器启动时恢复跨局免罚特权 */
	public void loadVictoryImmunities(Map<UUID, Integer> map) {
		victoryImmunities.clear();
		victoryImmunities.putAll(map);
	}

	public void setScore(UUID playerId, int score) { scores.put(playerId, score); }
	public Map<UUID, BlockPos> getAllRespawnPoints() { return new HashMap<>(respawnPoints); }
	public void setRespawnPoint(UUID playerId, BlockPos pos) { respawnPoints.put(playerId, pos); }
	/** 恢复时清空全部运行时映射（分数/队伍/重生点/双禁），避免与旧数据混合 */
	public void clearRuntimeState() {
		scores.clear();
		playerTeams.clear();
		respawnPoints.clear();
		teamBans.clear();
		playerEnabledEvents.clear();
		deathKeptBooks.clear();
		styleOptions.clear();
		addonStyleId = null;
		terrainPreset = TerrainPreset.OVERWORLD; // 自定义地形回默认（新一局由大厅菜单重新选择）
		ProfessionManager.getInstance().clearPerkFlags();
	}

	// ============ 最终目标标记 ============

	public boolean isEnderDragonKilled() { return enderDragonKilled; }
	public void markEnderDragonKilled() { this.enderDragonKilled = true; }

	public boolean isWitherKilled() { return witherKilled; }
	public void markWitherKilled() { this.witherKilled = true; }

	/** 判断：当前状态是否达成指定最终目标 */
	public boolean checkWinCondition(FinalGoal goal) {
		if (goal == null) return false;
		return switch (goal) {
			case ENDER_DRAGON -> enderDragonKilled;
			case ENDER_END -> enderDragonKilled; // 龙已死 + 玩家从末地回主世界(由事件侧触发)
			case WITHER -> witherKilled;
		};
	}

	/**
	 * 结束游戏并标记胜者。
	 * 只设置阶段和胜者，实际广播/回大厅在 MainEventListener 的 tick 里处理（避免循环依赖）。
	 */
	public void endGame(FinalGoal goal) {
		if (currentPhase == GamePhase.FINISHED) return;
		this.winningGoal = goal;
		this.currentPhase = GamePhase.FINISHED;
		this.gameStartTime = 0; // 让 remainingTicks 归零
		// 神的谎言/走私犯与检察官无最终目标（goal == null）：指令强制结束走此路径
		SurvivalContract.LOGGER.info("=== GAME ENDED: winning goal = {} ===",
				goal == null ? "none (force-ended or goal-less mode)" : goal.displayName);
	}
}
