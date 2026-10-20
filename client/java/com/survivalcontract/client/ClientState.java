package com.survivalcontract.client;

import com.survivalcontract.network.NetworkRegistry;
import com.survivalcontract.network.NetworkRegistry.ChallengeSyncPayload;
import com.survivalcontract.network.NetworkRegistry.ScoreSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端侧的 HUD 状态缓存。
 * 服务端通过 ChallengeSyncPayload / ScoreSyncPayload 更新。
 */
public class ClientState {

	public static final ClientState INSTANCE = new ClientState();

	/** 服务端发来的所有挑战条目（包含 enabled 状态和自定义值） */
	public List<ChallengeSyncPayload.Entry> challengeEntries = new ArrayList<>();

	/** 已启用的遭遇事件（sync 自服务端）——挑战书 GUI 打开时恢复用 */
	public final Set<String> enabledEvents = new HashSet<>();

	/** 游戏模式 / 最终目标 / 职业 / 初始分数（sync 自服务端，GUI 重建时恢复用） */
	public String gameModeKey = "solo";
	public String finalGoalKey = "ENDER_DRAGON";
	public String professionKey = "CHEF";
	public int initialScore = 0;

	/** 游戏时长选择（分钟）；-1 = 无限制。客户端本地选择，由时长按钮更新 */
	public int gameDurationMinutes = 120;

	/** 玩家分数列表（已按分数降序） */
	public List<ScoreSyncPayload.ScoreEntry> scores = new ArrayList<>();

	/** 当前游戏阶段（LOBBY / PLAYING / FINISHED） */
	public String currentPhase = "LOBBY";
	/** 规则总览预览屏打开中 — 左侧 HUD 图标临时隐藏（"移到中央"） */
	public boolean rulePreviewOpen = false;

	/** 上一次 sync 的阶段（检测阶段切换：游戏结束回大厅 → 触发 Xaero 存点清理） */
	private String lastPhase = null;

	/** 游戏总时长（秒），PLAYING 阶段第一次 sync 时校准 */
	public int totalDurationSeconds = 120 * 60;

	/** 剩余时间（秒）——服务端只在 PLAYING 阶段发非 0 值，0 表示不显示计时器 */
	public int remainingSeconds = 0;

	/** 开局冻结剩余秒数（>0 = 冻结期：顶部时间栏静止、暂停本地递减） */
	public int freezeSeconds = 0;

	/** 我方队伍已双禁的挑战 id（"" = 未选），sync 自服务端（按接收者队伍换算） */
	public String myTeamBan = "";

	/** 对面队伍已双禁的挑战 id（"" = 未选） */
	public String enemyBan = "";

	/** 死亡扣分（0/1/2），全局设置 sync 自服务端 */
	public int deathPenalty = 0;

	/** 遭遇事件刷新间隔（分钟）sync 自服务端：eventId -> [minM, maxM] */
	public final java.util.Map<String, int[]> eventIntervals = new java.util.HashMap<>();

	/** 获取事件间隔（未 sync 到的事件回退默认 7-10） */
	public int[] getEventInterval(String eventId) {
		int[] r = eventIntervals.get(eventId);
		return r != null ? r : new int[]{7, 10};
	}

	/** 已启用的挑战数量 */
	public int enabledCount() {
		int n = 0;
		for (var e : challengeEntries) if (e.enabled()) n++;
		return n;
	}

	/** 获取已启用条目的 ID 列表 */
	public List<String> enabledChallengeIds() {
		List<String> out = new ArrayList<>();
		for (var e : challengeEntries) if (e.enabled()) out.add(e.challengeId());
		return out;
	}

	/** 专属任务显示组件（服务端拼好：[S] 名称 进度/所需 剩余时间；名称随客户端语言渲染） */
		public net.minecraft.network.chat.Component taskText =
				net.minecraft.network.chat.Component.translatable("survival-contract.gui.hud.no_task");

	// ============ 大厅挑战书初始菜单状态（LobbyStatePayload sync） ============
	/** 0 = 初始菜单（模式选择/玩家列表/"我决定好了"）；1 = 规则菜单（当前书界面） */
	public int lobbyStage = 0;
	public int lobbyConfirmCount = 0;
	public int lobbyConfirmNeed = 2;
	public int lobbyStartCount = 0;
	public int lobbyStartNeed = 2;
	/** 玩法（初始菜单四选一）：NORMAL / GODS_LIES / STOWAWAY / CIVILIZATION */
	public String playStyleKey = "NORMAL";
	/** 神的谎言：使者数量（1~4） */
	public int envoyCount = 1;
	/** 文明时代：自定义地形预设 OVERWORLD / MINER / SNOW / DESERT（第四十九批） */
	public String terrainPreset = "OVERWORLD";

	/** 附属玩法：二级菜单选项值（key -> value；playStyleKey 为附属玩法 id 时有效） */
	public final java.util.Map<String, Integer> styleOptions = new java.util.HashMap<>();

	/** 当前玩法是否为已注册的附属玩法（由 playStyleKey 是否命中注册表推导） */
	public boolean isAddonStyle() {
		return com.survivalcontract.api.PlayStyleRegistry.byId(playStyleKey) != null;
	}

	/** 附属玩法选项值（未同步到回退 defaultValue） */
	public int styleOption(String key, int defaultValue) {
		return styleOptions.getOrDefault(key, defaultValue);
	}

	/** 走私犯与检察官：己方价值物品清单（itemId 空字符串=未知，score 仅己方有效） */
	public List<NetworkRegistry.SmuggleSyncPayload.SmuggleEntry> smuggleOwn = new ArrayList<>();
	/** 团队模式：对方清单（仅一件可见，无分值） */
	public List<NetworkRegistry.SmuggleSyncPayload.SmuggleEntry> smuggleEnemy = new ArrayList<>();
	/** 走私犯与检察官：当前批次剩余秒数（sync 时刻的快照） */
	public int smuggleBatchRemain = 0;
	/** 价值物品清单 sync 到达时的系统毫秒（HUD 本地递减换批倒计时用） */
	public long smuggleSyncAtMillis = 0L;

	public void registerNetworkHandlers() {
		ClientPlayNetworking.registerGlobalReceiver(ChallengeSyncPayload.TYPE, (payload, context) -> {
			this.challengeEntries = new ArrayList<>(payload.entries());
			this.currentPhase = payload.phase();
			this.remainingSeconds = payload.remainingSeconds();
			this.freezeSeconds = payload.freezeSeconds();
			this.enabledEvents.clear();
			this.enabledEvents.addAll(payload.enabledEvents());
			this.gameModeKey = payload.currentGameMode();
			this.finalGoalKey = payload.finalGoalKey().toUpperCase();
			this.professionKey = payload.professionKey();
			this.initialScore = payload.initialScore();
			this.myTeamBan = payload.myTeamBan() == null ? "" : payload.myTeamBan();
			this.enemyBan = payload.enemyBan() == null ? "" : payload.enemyBan();
			// 时长是全局设置：以服务端为准（任何玩家切换后全员同步显示）
			this.gameDurationMinutes = payload.gameDurationMinutes();
			// 死亡扣分 + 事件间隔（全局设置，以服务端为准）
			this.deathPenalty = payload.deathPenalty();
			this.eventIntervals.clear();
			for (String s : payload.eventIntervals()) {
				String[] parts = s.split(":");
				if (parts.length == 3) {
					try {
						this.eventIntervals.put(parts[0],
								new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
					} catch (NumberFormatException ignored) {}
				}
			}
			if ("PLAYING".equals(this.currentPhase)) {
				this.totalDurationSeconds = Math.max(this.totalDurationSeconds, payload.remainingSeconds() + 1);
			}
			// Xaero 适配：阶段切换时清理竞技场维度的存点/死亡点
			//  结束回大厅：只要进入 LOBBY 即触发（上一步可能是 PLAYING，也可能是 FINISHED——
			//  游戏结束实际序列是 PLAYING→FINISHED(5秒结算)→LOBBY，旧代码只认 PLAYING→LOBBY
			//  导致回大厅清扫从未注册，是"清理一直不生效"的根因）；延迟多轮等 Xaero flush 后再删
			//  新一局开始：进入 PLAYING 立即再清一次，防止任何残留被带进新局
			if (lastPhase != null && !lastPhase.equals(this.currentPhase)) {
				if ("LOBBY".equals(this.currentPhase)) {
					XaeroWaypointCleaner.scheduleCleanAfterGameEnd();
				} else if ("PLAYING".equals(this.currentPhase)) {
					XaeroWaypointCleaner.cleanArenaWaypoints();
				}
			}
			boolean justStarted = "PLAYING".equals(this.currentPhase) && !"PLAYING".equals(lastPhase);
			lastPhase = this.currentPhase;
			// 游戏开始的瞬间关闭挑战书（跨维度传送中开着 GUI 重建有风险，
			// 且开始后本来就不需要书）——恢复旧版"点开始即关书"的行为
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (justStarted && mc.gui.screen() instanceof com.survivalcontract.client.gui.ChallengeBookScreen) {
				mc.setScreenAndShow(null);
				return;
			}
			// 同步刷新打开中的挑战书 GUI（重建 widgets 以反映最新模式/双禁/规则状态）
			// 注意：这里必须放在 ClientState 里 —— Fabric API 对同一 payload 类型只允许注册一个
			// global receiver（后注册的静默返回 false），ChallengeBookOpener 里的注册从未生效过！
			if (mc.gui.screen() instanceof com.survivalcontract.client.gui.ChallengeBookScreen screen) {
				screen.rebuildWidgets();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(ScoreSyncPayload.TYPE, (payload, context) -> {
			this.scores = new ArrayList<>(payload.scores());
			this.scores.sort((a, b) -> Integer.compare(b.score(), a.score()));
		});
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.TaskSyncPayload.TYPE, (payload, context) -> {
			this.taskText = payload.taskText();
		});
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.LobbyStatePayload.TYPE, (payload, context) -> {
			this.lobbyStage = payload.stage();
			this.lobbyConfirmCount = payload.confirmCount();
			this.lobbyConfirmNeed = payload.confirmNeed();
			this.lobbyStartCount = payload.startCount();
			this.lobbyStartNeed = payload.startNeed();
			this.playStyleKey = payload.playStyle() == null ? "NORMAL" : payload.playStyle();
			this.envoyCount = payload.envoyCount();
			this.terrainPreset = payload.terrainPreset() == null ? "OVERWORLD" : payload.terrainPreset();
			this.styleOptions.clear();
			if (payload.styleOptions() != null) this.styleOptions.putAll(payload.styleOptions());
			// 阶段切换/投票变化 → 刷新打开中的挑战书
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.gui.screen() instanceof com.survivalcontract.client.gui.ChallengeBookScreen screen) {
				screen.rebuildWidgets();
			}
		});
		// 指认书状态（神的谎言）：服务端校验后下发存活玩家清单 → 打开指认书界面
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.AccuseBookStatePayload.TYPE, (payload, context) -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			mc.execute(() -> mc.setScreenAndShow(new com.survivalcontract.client.gui.AccuseBookScreen(payload)));
		});
		// 神谕纸符链接选人清单（第五十九批）：服务端校验后下发存活玩家清单 → 打开链接界面
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.RelicLinkStatePayload.TYPE, (payload, context) -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			mc.execute(() -> mc.setScreenAndShow(new com.survivalcontract.client.gui.RelicLinkScreen(payload)));
		});
		// 走私犯与检察官：价值物品清单 sync（HUD 显示用）
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.SmuggleSyncPayload.TYPE, (payload, context) -> {
			this.smuggleOwn = new ArrayList<>(payload.own());
			this.smuggleEnemy = new ArrayList<>(payload.enemy());
			this.smuggleBatchRemain = payload.batchRemainSeconds();
			this.smuggleSyncAtMillis = System.currentTimeMillis();
		});
		// 走私犯与检察官：收纳袋界面旗标（第六十六批）——记录/清除双袋菜单引用，
		// 贿赂袋禁放走私品 + 袋子互存禁放的客户端识别依据（服务端按容器标记类识别）。
		// 服务端先发界面包再发本包（同连接 FIFO），此刻 containerMenu 已是刚打开的袋菜单
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.BagOpenPayload.TYPE, (payload, context) -> {
			var menu = context.player() == null ? null : context.player().containerMenu;
			com.survivalcontract.smuggle.SmuggleManager.clientBribeBagMenu = payload.bribe() ? menu : null;
			com.survivalcontract.smuggle.SmuggleManager.clientValueBagMenu = payload.bribe() ? null : menu;
		});
		// 走私犯与检察官：检查发起书状态（玩家清单）→ 打开检查书界面
		ClientPlayNetworking.registerGlobalReceiver(NetworkRegistry.ProsecuteStatePayload.TYPE, (payload, context) -> {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			mc.execute(() -> mc.setScreenAndShow(new com.survivalcontract.client.gui.ProsecuteScreen(payload)));
		});
	}
}
