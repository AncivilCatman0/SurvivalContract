package com.survivalcontract;

import com.survivalcontract.game.GameStateManager;
import com.survivalcontract.dimension.LobbyDimensionSetup;
import com.survivalcontract.dimension.LobbyManager;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.event.RandomEventManager;
import com.survivalcontract.task.TaskManager;
import com.survivalcontract.profession.ProfessionManager;
import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurvivalContract implements ModInitializer {
	public static final String MOD_ID = "survival-contract";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Survival Contract mod initializing...");

		// 信雅互联（Sinytra Connector，Fabric 模组在 NeoForge 上的兼容层）环境检测——
		// 只记录环境信息，不做逻辑分支（Connector + Forgified Fabric API 下本模组正常工作）
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("connector")) {
			LOGGER.info("Sinytra Connector detected: running on NeoForge through Connector compatibility layer.");
		}

		// 自测模式强制大厅模式开启（自动化测试使用旧存档，没有 sc大厅 标记文件）
		if ("1".equals(System.getenv("SC_SELFTEST")) || "1".equals(System.getenv("SC_SERVERTEST"))) {
			com.survivalcontract.dimension.LobbyMode.forceActiveForTests();
		}

		// 附属玩法入口点采集（自定义 entrypoint "survival-contract:playstyle"）
		// 未安装任何附属模组时列表为空，行为与以往完全一致
		com.survivalcontract.api.PlayStyleRegistry.loadEntrypoints();

		// 文明时代专属占领羊毛方块（5 倍硬度 / 破坏不掉落）
		com.survivalcontract.civilization.CivilBlocks.register();

		// Register networking
		NetworkRegistry.register();

		// Set up custom lobby dimension (inject region files + get ServerLevel)
		LobbyDimensionSetup.register();

		// Register events
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Server started, initializing Survival Contract managers...");
			// 先清会话级静态缓存（退出重进不换 JVM，残留集合会把参战者锁成旁观者等）
			com.survivalcontract.MainEventListener.resetSessionStatics();
			// 背包格锁定：服务端权威 checker（玩家挑战集 + 游戏阶段，与客户端预测同规则）
			com.survivalcontract.lock.LockedSlots.serverChecker = (player, slot) -> {
				if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return false;
				if (com.survivalcontract.game.GameStateManager.getInstance().getCurrentPhase()
						!= com.survivalcontract.game.GameStateManager.GamePhase.PLAYING) return false;
				var cs = com.survivalcontract.challenge.ChallengeManager.getInstance().getPlayerChallenges(sp.getUUID());
				if (!cs.contains("inv_slot_lock")) return false;
				var ic = com.survivalcontract.challenge.ChallengeManager.getInstance().getChallenge("inv_slot_lock");
				// 三档：27格=锁9槽 / 18格=锁18槽 / 9格=锁27槽（与客户端同规则）
				int v = ic != null ? ic.getCustomValue() : 18;
				int lockCount = v == 9 ? 27 : v == 27 ? 9 : 18;
				return slot >= 9 && slot < 9 + lockCount;
			};
			GameStateManager.getInstance().onServerStart(server);
			LobbyManager.getInstance().onServerStart(server);
			ChallengeManager.getInstance().onServerStart(server);
			RandomEventManager.getInstance().onServerStart(server);
			TaskManager.getInstance().onServerStart(server);
			ProfessionManager.getInstance().onServerStart(server);
			// 单人存档退出重进：用存档里的快照恢复进行中的对局（阶段/分数/队伍/竞技场世界）
			// 自测模式跳过：上次崩溃残留的会话文件会污染假人测试的断言
			// 大厅模式关闭（普通世界）同样跳过：没有进行过对局，旧快照不应在普通世界生效
			if (!ServerSelfTest.isEnabled() && com.survivalcontract.dimension.LobbyMode.isActive()) {
				com.survivalcontract.game.SessionPersistence.restore(server);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// 先落盘进行中的对局状态，再清理管理器
			if (com.survivalcontract.dimension.LobbyMode.isActive()) {
				com.survivalcontract.game.SessionPersistence.save(server);
			}
			GameStateManager.getInstance().onServerStop(server);
			LobbyManager.getInstance().onServerStop(server);
			com.survivalcontract.dimension.LobbyMode.reset();
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			GameStateManager.getInstance().onPlayerRespawn(newPlayer, alive);
		});

		// Register all game event listeners
		MainEventListener.register();

		// Register commands (/sc tp, /sc endgame)
		com.survivalcontract.command.ScCommands.register();

		// Server-side fake-player self test (enabled with SC_SERVERTEST=1)
		ServerSelfTest.maybeRegister();

		LOGGER.info("Survival Contract mod initialized successfully!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
