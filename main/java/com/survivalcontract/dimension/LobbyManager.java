package com.survivalcontract.dimension;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the lobby dimension - imports from 大厅.zip and handles lobby logic.
 */
public class LobbyManager {
	public static final LobbyManager INSTANCE = new LobbyManager();

	/** 大厅出生点（可由 config/survival-contract/lobby-config.json 覆盖，LobbyConfig.load 启动时加载） */
	public static BlockPos LOBBY_SPAWN = LobbyConfig.DEFAULT_SPAWN;

	private ServerLevel lobbyLevel;
	private boolean lobbyReady = false;

	/** 待发送的全屏大字（神的谎言身份提示）：延迟到开局 3 秒倒计时结束后弹出，避免被倒计时覆盖 */
	private record PendingTitle(net.minecraft.network.chat.Component title, long sendAtTick) {}
	private static final Map<UUID, PendingTitle> PENDING_TITLES = new HashMap<>();

	private LobbyManager() {}

	public static LobbyManager getInstance() { return INSTANCE; }

	/** 排队一条全屏大字（sendAtTick 到达时由 tickPendingTitles 发送） */
	public static void queueTitle(ServerPlayer player, net.minecraft.network.chat.Component title, long sendAtTick) {
		PENDING_TITLES.put(player.getUUID(), new PendingTitle(title, sendAtTick));
	}

	/** 每 tick 调用：发送到期的全屏大字（白色大字 + 短暂停留后淡出） */
	public void tickPendingTitles(MinecraftServer server) {
		if (PENDING_TITLES.isEmpty()) return;
		long now = server.getTickCount();
		for (var it = PENDING_TITLES.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			if (now < e.getValue().sendAtTick()) continue;
			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) continue;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 10));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(e.getValue().title()));
		}
	}

	public static void clearPendingTitles() { PENDING_TITLES.clear(); }

	public void onServerStart(MinecraftServer server) {
		// 大厅模式关闭（普通单人世界/服务器关配置）：不绑定大厅维度，也绝不能回退主世界，
		// 否则 LOBBY 阶段的虚空保护会误伤主世界玩家
		if (!LobbyMode.isActive()) {
			this.lobbyLevel = null;
			this.lobbyReady = false;
			return;
		}
		// Lobby dimension is injected by LobbyDimensionSetup in SERVER_STARTING,
		// and set back to us in SERVER_STARTED. If not set, fall back to overworld.
		if (this.lobbyLevel == null) {
			this.lobbyLevel = server.overworld();
			SurvivalContract.LOGGER.warn("Custom lobby dimension not found, using overworld as fallback.");
		}
		lobbyReady = this.lobbyLevel != null;
	}

	public void onServerStop(MinecraftServer server) {
		lobbyLevel = null;
		lobbyReady = false;
		clearPendingTitles();
	}

	public boolean isLobbyReady() { return lobbyReady && lobbyLevel != null; }
	public ServerLevel getLobbyLevel() { return lobbyLevel; }

	/** 重新应用大厅维度规则（禁刷怪+锁晴天）：
	 *  游戏结束回大厅时必须调用 — 开局曾为竞技场重新打开过 SPAWN_MOBS（共享游戏规则），
	 *  结束流程若不关回去，大厅就会开始生成史莱姆等敌对生物。 */
	public void reapplyLobbyRules() {
		if (lobbyLevel != null) applyLobbyRules(lobbyLevel);
	}

	/** Called by LobbyDimensionSetup after the custom dimension is created. */
	public void setLobbyLevel(ServerLevel level) {
		this.lobbyLevel = level;
		this.lobbyReady = level != null;
		applyLobbyRules(level);
	}

	/** 大厅维度规则：禁止生成生物；天气锁定为晴天；时间锁定为白天由 dimension_type 的 fixed_time 保证。
	 *  public：附属玩法（固定地图维度）开局时复用同一套世界规则。 */
	public void applyLobbyRules(ServerLevel level) {
		try {
			MinecraftServer server = level.getServer();
			level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
			// 锁定天气为晴天：关闭天气推进 + 强制清除雨/雷（26.2 天气数据为按维度的 WeatherData）
			level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, server);
			try {
				var weather = level.getWeatherData();
				weather.setRaining(false);
				weather.setThundering(false);
				weather.setClearWeatherTime(Integer.MAX_VALUE);
				weather.setRainTime(0);
				weather.setThunderTime(0);
			} catch (Exception we) {
				SurvivalContract.LOGGER.warn("Failed to lock lobby weather", we);
			}
			SurvivalContract.LOGGER.info("Lobby rules applied: mob spawning disabled, weather locked to clear (time locked via fixed_time).");
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to apply lobby rules", e);
		}
	}

	public void teleportToLobby(ServerPlayer player) {
		if (lobbyLevel == null) return;
		// 回大厅必须清空除挑战书外的全部物品（防止职业初始物品/局内物品被带进大厅）
		clearInventoryExceptChallengeBook(player);
		// 经验槽一并清零：任何路径回大厅都不残留上一局的等级/进度（第九十一批）
		player.experienceLevel = 0;
		player.experienceProgress = 0.0f;
		player.totalExperience = 0;
		BlockPos target = safeLobbySpawn();
		player.teleportTo(lobbyLevel,
			target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
			Set.of(), player.getYRot(), player.getXRot(), false);
		player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
		// Simplified: skip advancement revocation (requires 2-arg API)
	}

	/**
	 * 大厅落点：自定义大厅维度直接用配置坐标；
	 * 若因数据包缺失回退到主世界（{@link #onServerStart} 的兜底），
	 * 配置里的 y=-63 会把玩家送进虚空 —— 此时用高度图重新找一个安全地面。
	 */
	private BlockPos safeLobbySpawn() {
		if (lobbyLevel == null) return LOBBY_SPAWN;
		boolean fallbackOverworld = lobbyLevel == lobbyLevel.getServer().overworld();
		if (!fallbackOverworld) return LOBBY_SPAWN;
		try {
			int y = lobbyLevel.getHeight(
					net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					LOBBY_SPAWN.getX(), LOBBY_SPAWN.getZ());
			return y > lobbyLevel.getMinY()
					? new BlockPos(LOBBY_SPAWN.getX(), y, LOBBY_SPAWN.getZ())
					: LOBBY_SPAWN;
		} catch (Exception e) {
			return LOBBY_SPAWN;
		}
	}

	/**
	 * 清空玩家全部物品（背包 + 盔甲 + 副手 + 光标），仅保留挑战书。
	 * 不补发挑战书——由调用方决定（tryTeleportToLobby 缺书才发，performResetForLobby 统一重发）。
	 */
	private void clearInventoryExceptChallengeBook(ServerPlayer player) {
		var inv = player.getInventory();
		ItemStack book = null;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && com.survivalcontract.MainEventListener.isChallengeBook(s)) {
				book = s.copy();
				break;
			}
		}
		inv.clearContent();
		// 光标上拿着的物品也要清掉
		try { player.containerMenu.setCarried(ItemStack.EMPTY); } catch (Exception ignored) {}
		if (book != null) inv.add(book);
	}

	public void teleportToPlayingWorld(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		// 传送到竞技场主世界（每局新建的随机种子维度）
		// 竞技场缺失时绝不能退回原版主世界（会把玩家丢进"其他世界"）——直接报错并保留原地
		ServerLevel arena = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (arena == null) {
			SurvivalContract.LOGGER.error("teleportToPlayingWorld skipped for {}: arena overworld missing",
					player.getName().getString());
			return;
		}

		// 传送到该玩家的固定重生点（团队=队点，单人=个人点），坐标固定不波动
		com.survivalcontract.game.GameStateManager gsm = com.survivalcontract.game.GameStateManager.getInstance();
		var spawn = gsm.getRespawnPoint(player.getUUID());

		player.teleportTo(arena, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
			Set.of(), player.getYRot(), player.getXRot(), false);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		// 清除大厅 buff（饱和/生命恢复/夜视）——防止残留效果隐藏地跟随玩家进入对局
		player.removeEffect(net.minecraft.world.effect.MobEffects.SATURATION);
		player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
		player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);

		// 设置固定重生点（forced=true：非床方块也可直接站立重生；玩家睡床可覆盖）
		player.setRespawnPosition(new net.minecraft.server.level.ServerPlayer.RespawnConfig(
				net.minecraft.world.level.storage.LevelData.RespawnData.of(
						com.survivalcontract.dimension.ArenaWorldManager.ARENA, spawn, player.getYRot(), player.getXRot()),
				true), false);
	}

	public void teleportAllToLobby(MinecraftServer server) {
		// 回大厅：重新应用大厅规则（关闭 SPAWN_MOBS + 锁晴天）— 开局时曾为竞技场重新打开过
		if (lobbyLevel != null) applyLobbyRules(lobbyLevel);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			teleportToLobby(player);
		}
	}

	public void startGame(MinecraftServer server) {
		// 大厅模式关闭时不允许开局（普通世界下 /sc 等管理指令也不得触发竞技场创建）
		if (!LobbyMode.isActive()) {
			SurvivalContract.LOGGER.warn("startGame ignored: lobby mode is disabled for this server.");
			return;
		}
		GameStateManager gsm = GameStateManager.getInstance();

		// ===== 附属玩法：不走竞技场/规则/职业流程，直接进入 PLAYING 并把控制权交给附属模组 =====
		if (gsm.isAddon()) {
			var provider = gsm.getAddonProvider();
			if (provider == null) {
				SurvivalContract.LOGGER.warn("Addon play style '{}' not found, falling back to NORMAL",
						gsm.getAddonStyleId());
				gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
			} else {
				// 团队模式：先补齐队伍分配（红/蓝）——出生点由附属模组自行设置
				if (gsm.isTeamMode()) {
					com.survivalcontract.team.TeamManager.getInstance().assignMissing(server);
				}
				// 收集参战者（大厅观战者除外）
				java.util.List<ServerPlayer> participants = new java.util.ArrayList<>();
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (!gsm.isLobbySpectator(p.getUUID())) participants.add(p);
				}
				gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
				gsm.setCurrentFinalGoal(null); // 无最终目标
				gsm.setGameStartTime(server.getTickCount() + 60); // 3 秒开局冻结（与内置玩法一致）
				gsm.setGameDurationMinutes(gsm.getGameDurationMinutes()); // 重算倒计时 tick
				// 无规则无职业 → 初始分 0；职业强制为「无」；清空大厅残留状态
				for (ServerPlayer p : participants) {
					gsm.setScore(p.getUUID(), 0);
					com.survivalcontract.profession.ProfessionManager.getInstance()
							.setPlayerProfession(p.getUUID(),
									com.survivalcontract.profession.ProfessionManager.Profession.NONE);
					com.survivalcontract.MainEventListener.clearPlayerState(server, p);
				}
				// 大厅观战者：旁观者模式（留在维度内观战，不参战）
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (gsm.isLobbySpectator(p.getUUID())) {
						p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
					}
				}
				com.survivalcontract.challenge.ChallengeManager.getInstance().clearPenaltyFlags();
				com.survivalcontract.game.TeamChestManager.getInstance().clearAll();
				// 交给附属模组：注入世界规则 / 分队出生 / 套餐 / HUD 等
				// 附属钩子异常必须整体回大厅：否则阶段已置 PLAYING 却没有对应地图/规则，
				// 玩家会卡在一个"假对局"里（整局玩不了，第八十九批）
				try {
					provider.onGameStart(server, participants);
				} catch (Exception e) {
					SurvivalContract.LOGGER.error("[Addon] onGameStart failed for '{}', aborting to LOBBY",
							provider.id(), e);
					gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
					gsm.clearRuntimeState();
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
								"survival-contract.msg.arena_create_failed"));
						teleportToLobby(p);
					}
					com.survivalcontract.network.NetworkRegistry.broadcastLobbyState(server);
					return;
				}
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					com.survivalcontract.network.NetworkRegistry.sendSync(p);
				}
				SurvivalContract.LOGGER.info("Addon play style '{}' started with {} participants.",
						provider.id(), participants.size());
				return;
			}
		}

		// 0.9 开启生物生成：大厅曾关闭 SPAWN_MOBS，而游戏规则存于主世界、
		//     竞技场三维度（DerivedLevelData）共享同一份 GameRules — 不重新打开则
		//     竞技场世界不生成任何生物/实体（刷怪、动物、事件实体除外的事件规则）
		try {
			server.overworld().getGameRules()
					.set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, true, server);
			SurvivalContract.LOGGER.info("Game rules: SPAWN_MOBS re-enabled for arena worlds");
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to re-enable SPAWN_MOBS", e);
		}

		// 0. ★ 必须在准备世界之前补齐队伍分配！
		//    队点分配（setupNewRandomWorld → assignRespawnPoints）依赖队伍映射；
		//    上一局结束时 resetPlayerTeams 清空了映射，若此时才补齐，
		//    所有玩家队别为 NONE → 全部塞进红队点（出生点挤在一起的根因）
		if (gsm.isTeamMode()) {
			com.survivalcontract.team.TeamManager.getInstance().assignMissing(server);
		}

		// 0.7 神的谎言：清空环境板块共享规则 + 给每位参战者随机分配总分恰好 100 的规则
		//     （必须在 setupNewRandomWorld 计分之前写入规则集；无职业 +10 在该模式下停用）
		// 0.7 走私犯与检察官：所有玩家规则共享 → 开局把全部参战者规则集合并为并集，应用到每个人
		if (gsm.isGodsLies()) {
			gsm.resetGodsLies();
			var cmL = com.survivalcontract.challenge.ChallengeManager.getInstance();
			for (var c : cmL.getAllChallenges()) {
				if ("environment".equals(c.getSection())) gsm.setRuleActive(c.getId(), false);
			}
			java.util.Random rndL = new java.util.Random(server.overworld().getRandom().nextLong());
			// 遭遇事件：清空大厅勾选，系统随机发放 1 个遭遇事件（团队模式全员同一事件），
			// 事件分计入 100 分预算 → 规则目标分 = 100 - 事件分（开局总分仍恰好 100）
			gsm.clearPlayerEnabledEvents();
			gsm.getEnabledEvents().clear();
			var eventTypes = com.survivalcontract.event.RandomEventManager.EventType.values();
			var globalEvent = gsm.isTeamMode() ? eventTypes[rndL.nextInt(eventTypes.length)] : null;
			if (globalEvent != null) {
				gsm.setEventEnabled(globalEvent.name(), true);
				SurvivalContract.LOGGER.info("[GodsLies] Team mode event assigned: {} (+{} per player)",
						globalEvent.name(), globalEvent.score);
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (gsm.isLobbySpectator(p.getUUID())) continue;
				cmL.clearPlayerChallenges(p.getUUID());
				int ruleTarget = 100;
				if (globalEvent == null) {
					var ev = eventTypes[rndL.nextInt(eventTypes.length)];
					gsm.applyPlayerEventToggle(p.getUUID(), ev.name(), true);
					ruleTarget -= ev.score;
				} else {
					ruleTarget -= globalEvent.score;
				}
				for (String ruleId : cmL.randomRuleSetForScore(ruleTarget, rndL)) {
					cmL.enableChallenge(p.getUUID(), ruleId);
				}
			}
			SurvivalContract.LOGGER.info("[GodsLies] Random rules + encounter event assigned (target 100 per player)");
		} else if (gsm.isStowaway()) {
			var cmL = com.survivalcontract.challenge.ChallengeManager.getInstance();
			java.util.Set<String> merged = new java.util.HashSet<>();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (gsm.isLobbySpectator(p.getUUID())) continue;
				merged.addAll(cmL.getPlayerChallenges(p.getUUID()));
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (gsm.isLobbySpectator(p.getUUID())) continue;
				cmL.setPlayerChallenges(p.getUUID(), merged);
			}
			SurvivalContract.LOGGER.info("[Smuggle] Rule sets merged ({} rules, shared by all)", merged.size());
		}

		// 0.5 创建全新随机种子的竞技场维度组（模仿 Yet Another Bingo：每局独立世界，
		//     结束后删档）。必须在 setupNewRandomWorld 之前（出生点搜索需要竞技场主世界）
		// 失败（旧档删不掉/维度注册失败）必须中止开局：否则会退化成原版主世界地形，
		// 文明时代的四种地形预设全部失效，玩家还会被丢进"其他世界"
		if (!com.survivalcontract.dimension.ArenaWorldManager.createArenaWorlds(server)) {
			SurvivalContract.LOGGER.error("startGame aborted: arena worlds could not be created.");
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.arena_create_failed"));
			}
			com.survivalcontract.dimension.ArenaWorldManager.teardownArenaWorlds(server);
			return;
		}

		// 1. 先准备 PLAYING 世界（随机坐标 + 初始分数）
		if (!gsm.setupNewRandomWorld(server)) {
			SurvivalContract.LOGGER.error("startGame aborted: PLAYING world setup failed (arena missing).");
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.arena_create_failed"));
			}
			com.survivalcontract.dimension.ArenaWorldManager.teardownArenaWorlds(server);
			return;
		}

		// 1.2 神的谎言：随机任命使者（清空其规则集 = 免疫所有规则；开局分与平民一致 = 100）
		if (gsm.isGodsLies()) {
			gsm.setupGodsLiesEnvoys(server);
			for (var p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.godslies.intro", gsm.getGodEnvoysCount()));
			}
		}

		// 1.25 走私犯与检察官：初始化收集清单 + 检查室
		if (gsm.isStowaway()) {
			com.survivalcontract.smuggle.SmuggleManager.getInstance().setup(server);
		}

		// 1.26 文明时代：强制 PVP 开启（gamerule 由 1.5 段 applyAllRuleGamerules 统一应用）+ 疆域初始化
		if (gsm.isCivilization()) {
			gsm.setRuleActive("rule_pvp", true);
			// 队内友伤关闭（第五十四批）：原版记分板队伍关闭友伤——同队玩家无法互相伤害（跨队伍伤害不受影响）
			var civSb = server.getScoreboard();
			for (String tn : new String[]{"survival_red", "survival_blue"}) {
				var civPt = civSb.getPlayerTeam(tn);
				if (civPt != null) civPt.setAllowFriendlyFire(false);
			}
			com.survivalcontract.civilization.CivilizationManager.getInstance().setup(server);
		}

		// 1.5 新世界维度规则是默认值：按挑战书全局规则开关（PVP/保留物品栏）重新应用 gamerule
		com.survivalcontract.network.NetworkRegistry.applyAllRuleGamerules(server);

		// 1.55 开局时间设为白天（竞技场主世界）
		try {
			var arenaOverworld = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
			if (arenaOverworld != null) {
				var src = server.createCommandSourceStack()
						.withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS)
						.withLevel(arenaOverworld);
				server.getCommands().performPrefixedCommand(src, "time set day");
				SurvivalContract.LOGGER.info("Arena time set to day");
			}
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to set arena time to day", e);
		}

		// 2. 切换阶段，记录开始时间
		gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);
		// 2.2 最终目标「随机」：开局揭晓（神的谎言/走私犯/文明时代无最终目标）
		if (gsm.isGodsLies() || gsm.isStowaway() || gsm.isCivilization()) {
			gsm.setCurrentFinalGoal(null);
		} else {
			gsm.resolveRandomFinalGoal();
		}
		// 开局冻结：开始时间延后 60 ticks（3 秒倒计时，每秒有升调音效）——倒计时结束后顶部时间栏才开始流动
		gsm.setGameStartTime(server.getTickCount() + 60);

		// 3.5 重置遭遇事件计时器（仅触发玩家勾选启用的事件）
		com.survivalcontract.event.RandomEventManager.getInstance().onGameStarted(server);

		// 3.55 新一局清空违规扣分记录（每玩家每条规则的"仅扣一次"名额重置）
		com.survivalcontract.challenge.ChallengeManager.getInstance().clearPenaltyFlags();

		// 3.6 新一局清空全部团队箱（个人箱与红/蓝队箱）
		com.survivalcontract.game.TeamChestManager.getInstance().clearAll();

		// 4. 处理每个玩家
		clearPendingTitles();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// 大厅观战者：不参战 → 以旁观者模式传送到竞技场出生点观战（无参战者状态、不参与计分）
			if (gsm.isLobbySpectator(player.getUUID())) {
				com.survivalcontract.MainEventListener.teleportSpectatorToArena(server, player);
				continue;
			}
			// 开始前统一清理：物品栏（含挑战书/大厅杂物）、全部药水效果（大厅 buff 等）、成就
			// ——所有玩家一致执行
			com.survivalcontract.MainEventListener.clearPlayerState(server, player);

			// 给职业起始物品
			// 走私犯与检察官：锁定无职业（开局强制 NONE，覆盖大厅里选的职业）
			if (gsm.isStowaway()) {
				com.survivalcontract.profession.ProfessionManager.getInstance().setPlayerProfession(
						player.getUUID(), com.survivalcontract.profession.ProfessionManager.Profession.NONE);
			}
			// 文明模式：愚者职业被禁用（第五十二批，兜底重置——大厅里选的愚者开局一律无效）
			else if (gsm.isCivilization()
					&& com.survivalcontract.profession.ProfessionManager.getInstance().getPlayerProfession(player.getUUID())
					== com.survivalcontract.profession.ProfessionManager.Profession.JESTER) {
				com.survivalcontract.profession.ProfessionManager.getInstance().setPlayerProfession(
						player.getUUID(), com.survivalcontract.profession.ProfessionManager.Profession.NONE);
			}
			com.survivalcontract.profession.ProfessionManager.getInstance().giveStartingItems(player);

			// 神的谎言：发放指认书（右键查看存活玩家；平民指认使者 / 使者降罪扣分）
			// + 每人 1 个任务刷新券（右键一次性立即刷新当前专属任务）
			if (gsm.isGodsLies() && !gsm.isLobbySpectator(player.getUUID())) {
				var accuseBook = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
				accuseBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
						net.minecraft.network.chat.Component.literal("§5指认书"));
				if (!player.getInventory().add(accuseBook)) com.survivalcontract.McCompat.drop(player, accuseBook, false);
				var refreshToken = GameStateManager.createTaskRefreshToken();
				if (!player.getInventory().add(refreshToken)) com.survivalcontract.McCompat.drop(player, refreshToken, false);
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.godslies.task_refresh_grant"));
			}

			// 走私犯与检察官：发放检查发起书（右键选非友方检察官 → 3 秒倒计时进检查室）
			// + 开局套装（价值收纳袋 / 贿赂收纳袋）
			if (gsm.isStowaway() && !gsm.isLobbySpectator(player.getUUID())) {
				var prosecuteBook = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
				prosecuteBook.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
						net.minecraft.network.chat.Component.literal("§b检查发起书"));
				if (!player.getInventory().add(prosecuteBook)) com.survivalcontract.McCompat.drop(player, prosecuteBook, false);
				com.survivalcontract.smuggle.SmuggleManager.getInstance().initPlayerKit(player);
			}

			// 文明时代：初始发放 8 个本队颜色羊毛（占领区块用，放置消耗——第五十一批）+ 1 个箱子（存放物资）
			if (gsm.isCivilization() && !gsm.isLobbySpectator(player.getUUID())) {
				giveCivStartKit(player, gsm);
			}

			// 重置生命和饥饿（正式开始前满血满饱）
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(5f);

			// 传送
			teleportToPlayingWorld(player);

			// 发提示
			net.minecraft.network.chat.Component teamTag = gsm.isTeamMode()
					? switch (gsm.getPlayerTeam(player.getUUID())) {
						case RED -> net.minecraft.network.chat.Component.translatable(
								"survival-contract.msg.team_tag_red");
						case BLUE -> net.minecraft.network.chat.Component.translatable(
								"survival-contract.msg.team_tag_blue");
						default -> net.minecraft.network.chat.Component.empty();
					}
					: net.minecraft.network.chat.Component.empty();
			String goalText = gsm.getCurrentFinalGoal() != null ? gsm.getCurrentFinalGoal().displayName : null;
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"§6═══════════════════════════════"));
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.game_start").append(teamTag));
			if (goalText != null) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.game_goal", goalText));
			} else if (gsm.isStowaway()) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.stowaway.no_goal"));
			} else if (gsm.isCivilization()) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.civ.no_goal"));
			} else {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.godslies.no_goal"));
			}
			if (gsm.getGameDurationMinutes() < 0) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.game_duration_unlimited"));
			} else {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.game_duration", gsm.getGameDurationMinutes()));
			}
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.game_initial_score", gsm.getScore(player.getUUID())));

			// 神的谎言：私发身份（使者免疫所有规则、可降罪；平民完成任务获得指认权）
			if (gsm.isGodsLies() && !gsm.isLobbySpectator(player.getUUID())) {
				boolean envoy = gsm.isGodEnvoy(player.getUUID());
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						envoy ? "survival-contract.msg.godslies.envoy_you"
								: "survival-contract.msg.godslies.civilian_you"));
				// 开局全屏身份提示白色大字：延迟到 3 秒倒计时结束后弹出（避免被倒计时覆盖）
				queueTitle(player, net.minecraft.network.chat.Component.translatable(
						envoy ? "survival-contract.msg.godslies.title_envoy"
								: "survival-contract.msg.godslies.title_civilian"),
						gsm.getGameStartTime() + 30);
			}

			// 走私犯与检察官：私发玩法说明
			if (gsm.isStowaway() && !gsm.isLobbySpectator(player.getUUID())) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.stowaway.intro1"));
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.stowaway.intro2"));
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.stowaway.intro3"));
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.stowaway.intro4"));
			}

			// 文明时代：私发玩法说明
			if (gsm.isCivilization() && !gsm.isLobbySpectator(player.getUUID())) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.civ.intro1"));
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.civ.intro2"));
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
						"survival-contract.msg.civ.intro3"));
			}
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"§6═══════════════════════════════"));

			// 发同步（让客户端 HUD 更新倒计时/阶段）
			com.survivalcontract.network.NetworkRegistry.sendSync(player);

			// 游戏开始音效不再在此播放：跨维度传送同一 tick 播放的音效包会被客户端
			// 切换世界时丢弃（一直听不到的根因）。改由 MainEventListener 在 3 秒冻结
			// 倒计时结束的"开始!"瞬间播放经验升级音效（客户端已加载完毕，必然生效）。
		}

		SurvivalContract.LOGGER.info("Game started! WorldSpawn={}", gsm.getPlayingSpawn());
	}

	/** 文明时代开局物资：8 个本队颜色羊毛（放置正常消耗——第五十一批取消返还）+ 1 个箱子。
	 *  包内可见供自测直调 */
	public static void giveCivStartKit(ServerPlayer player, GameStateManager gsm) {
		var wool = com.survivalcontract.civilization.CivilizationManager.teamWoolStack(
				gsm.getPlayerTeam(player.getUUID()), 8);
		if (!player.getInventory().add(wool)) com.survivalcontract.McCompat.drop(player, wool, false);
		var chest = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST, 1);
		if (!player.getInventory().add(chest)) com.survivalcontract.McCompat.drop(player, chest, false);
	}

	public void giveChallengeBook(ServerPlayer player) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("§6挑战书"));
		player.getInventory().add(book);
	}
}
