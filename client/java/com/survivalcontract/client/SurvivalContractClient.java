package com.survivalcontract.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurvivalContractClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("survival-contract-client");

	private static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("survival-contract", "main"));

	private static KeyMapping openBookKey;
	private static KeyMapping openTeamChestKey;
	private static KeyMapping rulePreviewKey;
	private static int clientTickCounter = 0;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Survival Contract client initializing...");
            // 锁定背包格：客户端预测实现（与服务端同规则——双端一致点击无反馈）
            // 注意：必须用独立 clientChecker，服务端注册的 serverChecker 在单机集成服务器下共用 JVM 互不覆盖
            com.survivalcontract.lock.LockedSlots.clientChecker = (player, slot) -> {
                var cs = ClientState.INSTANCE;
                if (!"PLAYING".equalsIgnoreCase(cs.currentPhase)) return false;
                for (var e : cs.challengeEntries) {
                    if ("inv_slot_lock".equals(e.challengeId())) {
                        if (!e.enabled()) return false;
                        // 三档：27格=锁9槽(9-17) / 18格=锁18槽(9-26) / 9格=锁27槽(9-35)
                        int v = e.customValue();
                        int lockCount = v == 9 ? 27 : v == 27 ? 9 : 18;
                        return slot >= 9 && slot < 9 + lockCount;
                    }
                }
                return false;
            };

		// 注册网络 handlers（ChallengeSyncPayload / ScoreSyncPayload → ClientState）
		ClientState.INSTANCE.registerNetworkHandlers();

		// Xaero 小地图/世界地图适配：竞技场维度存点与死亡点清理（客户端 tick 调度）
		XaeroWaypointCleaner.register();

		// 注册挑战书打开器
		com.survivalcontract.client.gui.ChallengeBookOpener.register();

		// 客户端自动化自测（仅当 SC_SELFTEST 环境变量存在时启用）
		ClientSelfTest.register();

		// 世界创建 UI 自动化验证（仅当 SC_UITEST 环境变量存在时启用）
		CreateWorldUiTest.register();

		// 注册按键 B 打开挑战书
		openBookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.survival-contract.open_book",
				InputConstants.KEY_B,
				KEY_CATEGORY));

		// 注册按键 G 打开团队箱
		openTeamChestKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.survival-contract.open_team_chest",
				InputConstants.KEY_G,
				KEY_CATEGORY));

		// 注册按键 O 打开/关闭规则总览预览（像 Bingo 棋盘预览）
		rulePreviewKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.survival-contract.rule_preview",
				InputConstants.KEY_O,
				KEY_CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openBookKey != null && openBookKey.consumeClick()) {
				com.survivalcontract.client.gui.ChallengeBookOpener.openChallengeBook();
			}
			// 按键 G → 请求服务端打开团队箱
			while (openTeamChestKey != null && openTeamChestKey.consumeClick()) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.survivalcontract.network.NetworkRegistry.OpenTeamChestPayload());
			}
			// 按键 O → 打开规则总览预览（关闭由预览屏自身的 keyPressed 处理）
			while (rulePreviewKey != null && rulePreviewKey.consumeClick()) {
				if (!ClientState.INSTANCE.rulePreviewOpen
						&& "PLAYING".equals(ClientState.INSTANCE.currentPhase)) {
					client.setScreenAndShow(new com.survivalcontract.client.gui.RulePreviewScreen());
				}
			}
			// PLAYING 阶段客户端倒计时（每 20 tick ≈ 1 秒 -1）
			// 开局冻结期（freezeSeconds > 0）不递减 → 顶部时间栏静止满值，倒计时结束才开始流动
			if ("PLAYING".equals(ClientState.INSTANCE.currentPhase)
					&& ClientState.INSTANCE.freezeSeconds <= 0) {
				clientTickCounter++;
				if (clientTickCounter >= 20) {
					clientTickCounter = 0;
					if (ClientState.INSTANCE.remainingSeconds > 0) {
						ClientState.INSTANCE.remainingSeconds--;
					}
				}
			} else {
				clientTickCounter = 0;
			}
		});

		// HUD 渲染（左侧 bingo + 右侧计分板 + 顶部倒计时）
		HudRenderer.register();

		LOGGER.info("Survival Contract client initialized successfully!");
	}
}
