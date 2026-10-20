package com.survivalcontract.client;

import com.survivalcontract.client.gui.ChallengeBookOpener;
import com.survivalcontract.client.gui.ChallengeBookScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端自动化自测（只在设置环境变量 SC_SELFTEST=1 时启用）。
 * <p>
 * 流程（加入存档后按 client tick 计时）：
 * <ol>
 *   <li>t=100s/5s：截图 1 —— 验证大厅地图是否加载</li>
 *   <li>t=120：打开挑战书 GUI</li>
 *   <li>t=140：截图 2 —— 验证卡片文字是否可见</li>
 *   <li>t=150-170：依次真实点击 职业/模式/目标 按钮（走 Screen.mouseClicked 完整派发链）</li>
 *   <li>t=180：点击第一张卡片（开关挑战）</li>
 *   <li>t=195：截图 3 —— 验证按钮/开关生效</li>
 *   <li>t=220：点击 开始游戏</li>
 *   <li>t=340：截图 4 —— 验证 PLAYING 世界 + 计时器 HUD</li>
 *   <li>t=360：输出 SELFTEST COMPLETE</li>
 * </ol>
 */
public class ClientSelfTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("survival-contract-selftest");
	private static final boolean ENABLED = System.getenv("SC_SELFTEST") != null;

	private static int tickCounter = 0;
	private static long joinTick = -1;

	public static void register() {
		if (!ENABLED) return;
		LOGGER.info("[SELFTEST] enabled (SC_SELFTEST env var present)");

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			joinTick = tickCounter;
			LOGGER.info("[SELFTEST] joined world at t=0");
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			joinTick = -1;
			LOGGER.info("[SELFTEST] disconnected");
		});

		ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
	}

	private static void tick(Minecraft client) {
		tickCounter++;
		if (joinTick < 0) return;
		int t = tickCounter - (int) joinTick;

		if (t == 100) {
			screenshot("1-lobby");
		} else if (t == 120) {
			LOGGER.info("[SELFTEST] opening challenge book GUI");
			ChallengeBookOpener.openChallengeBook();
		} else if (t == 140) {
			screenshot("2-gui-initial");
		} else if (t == 150) {
			// 点击职业 → 应打开二级菜单 ProfessionSelectScreen
			clickButton(client, "职业");
		} else if (t == 158) {
			// 职业二级菜单截图（延后几帧确保已渲染新屏幕）：验证面板背景/标题字号/布局
			screenshot("2b-profession");
		} else if (t == 160) {
			// 在二级菜单里点第一个职业按钮（非"返回"）→ 应发送选择并回到挑战书
			clickButtonNotContaining(client, "返回");
		} else if (t == 170) {
			clickButton(client, "个人模式");
		} else if (t == 180) {
				clickButton(client, "目标");
			} else if (t == 185) {
				clickCard(client);
			} else if (t == 186) {
				// 锁定背包格专项：直接发开关 payload 启用 inv_slot_lock（18格档=默认值）
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.survivalcontract.network.NetworkRegistry.ChallengeTogglePayload("inv_slot_lock", true));
				LOGGER.info("[SELFTEST] inv_slot_lock toggled on");
			} else if (t == 187) {
				// 再点两张卡片（第2、3行）→ PLAYING 后可验证左侧双列 HUD
				clickCardAt(client, 136, 104);
			} else if (t == 189) {
				clickCardAt(client, 136, 144);
			} else if (t == 190) {
				scroll(client, -6); // 滚到属性削弱区，露出可自定义数值卡片（削弱 XX% [-][+]）
			} else if (t == 191) {
				screenshot("3a-custom-cards");
			} else if (t == 192) {
				scroll(client, -44); // 一路滚到内容底部，露出遭遇事件列表
			} else if (t == 200) {
				// 滚动后截图：验证遭遇事件列表可见 + 顶部标题栏不被覆盖
				screenshot("3-gui-scrolled");
			} else if (t == 205) {
				// 关闭 GUI：在大厅直接验证左侧 HUD 紧凑行（图标+真实中文名，无灰色底板）
				client.setScreenAndShow(null);
				LOGGER.info("[SELFTEST] GUI closed for HUD check");
			} else if (t == 215) {
				screenshot("3b-hud-lobby");
			} else if (t == 225) {
				// 重开 GUI 继续流程
				ChallengeBookOpener.openChallengeBook();
			} else if (t == 230) {
				scroll(client, -50); // 滚到内容底部，露出遭遇事件列表
			} else if (t == 235) {
				// 点击最后可见的事件行（饥饿炸弹）→ 勾选启用遭遇事件（只点这一次）
				clickEventRow(client);
			} else if (t == 240) {
				screenshot("3c-event-checked");
			} else if (t == 250) {
				scroll(client, 3);
			} else if (t == 255) {
				// 单人 1 票确认初始菜单（确保 StartGame 时已在规则菜单阶段）
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.survivalcontract.network.NetworkRegistry.LobbyVotePayload(0));
				LOGGER.info("[SELFTEST] lobby confirm vote sent");
			} else if (t == 260) {
				// 直接发开始游戏 payload（GUI 按钮布局变化导致 clickButton 找不到文本，走网络包最稳）
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.survivalcontract.network.NetworkRegistry.StartGamePayload());
				LOGGER.info("[SELFTEST] StartGamePayload sent");
			} else if (t == 280) {
				// 测试 /sc tp（单人生效无队友 → 应有反馈消息；验证指令注册与队伍判定）
				sendCommand(client, "sc tp");
			} else if (t == 345) {
				// 截图 4a：开局冻结期 —— 大字倒计时 + 顶部时间栏静止满值 + 状态栏上方专属任务文本
				screenshot("4a-freeze-countdown");
			} else if (t == 390) {
				// 截图 4b：冻结结束后 —— 时间栏开始流动（约 01:59），隐身解除可移动
				screenshot("4b-after-start");
			} else if (t == 410) {
				// 锁定背包格专项：打开玩家背包（容器屏幕）→ 触发 extractSlot 覆盖渲染 + LOCKDBG 日志
				client.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
				LOGGER.info("[SELFTEST] inventory opened for lock overlay check");
			} else if (t == 415) {
				// 锁定专项：转储客户端同步状态，验证 checker 输入
				StringBuilder sb = new StringBuilder();
				for (var e : ClientState.INSTANCE.challengeEntries) {
					if ("inv_slot_lock".equals(e.challengeId())) {
						sb.append("enabled=").append(e.enabled()).append(" cv=").append(e.customValue()).append(';');
					}
				}
				LOGGER.info("[SELFTEST] phase={} lock-entry=[{}] entries-size={}",
						ClientState.INSTANCE.currentPhase, sb, ClientState.INSTANCE.challengeEntries.size());
			} else if (t == 430) {
				screenshot("5-inventory-lock");
			} else if (t == 450) {
				client.setScreenAndShow(null);
				LOGGER.info("[SELFTEST] COMPLETE");
			}

			// === Xaero 联动专项（相位驱动，避免硬编码 tick 受开局建维度耗时影响） ===
			// 进入 PLAYING 后让 Xaero 在 arena 里写足数据（~20s）→ endGame 走真实
			// PLAYING→FINISHED(5s)→LOBBY 链路 → 回大厅多轮清扫后断言目录归零、lobby 保留
			if ("PLAYING".equals(ClientState.INSTANCE.currentPhase) && xaeroPlayingSince < 0) {
				xaeroPlayingSince = t;
				LOGGER.info("[SELFTEST] XAERO phase PLAYING first seen at t={}", t);
			}
			if (xaeroPlayingSince >= 0 && !xaeroSampled && t >= xaeroPlayingSince + 200) {
				xaeroSampled = true;
				xaeroBeforeDirs = countArenaDirs();
				LOGGER.info("[SELFTEST] XAERO arena dirs BEFORE endgame = {}", xaeroBeforeDirs);
			}
			if (xaeroPlayingSince >= 0 && xaeroEndAt < 0 && t >= xaeroPlayingSince + 400) {
				xaeroEndAt = t;
				var srv = client.getSingleplayerServer();
				if (srv != null) {
					srv.execute(() -> {
						var gsm = com.survivalcontract.game.GameStateManager.getInstance();
						if (gsm.getCurrentPhase() == com.survivalcontract.game.GameStateManager.GamePhase.PLAYING) {
							gsm.endGame(gsm.getCurrentFinalGoal());
							LOGGER.info("[SELFTEST] endGame invoked on server thread");
						} else {
							LOGGER.warn("[SELFTEST] skip endGame, phase={}", gsm.getCurrentPhase());
						}
					});
				} else {
					LOGGER.warn("[SELFTEST] singleplayer server is null, Xaero end-to-end test skipped");
				}
			}
			if (xaeroEndAt >= 0 && !xaeroShotDone && t >= xaeroEndAt + 160) {
				xaeroShotDone = true;
				screenshot("6-back-to-lobby");
			}
			if (xaeroEndAt >= 0 && !xaeroChecked1 && t >= xaeroEndAt + 260) {
				xaeroChecked1 = true;
				xaeroAfterDirs = countArenaDirs();
				LOGGER.info("[SELFTEST] XAERO arena dirs AFTER lobby sweep r1 = {}", xaeroAfterDirs);
			}
			if (xaeroEndAt >= 0 && !xaeroVerified && t >= xaeroEndAt + 460) {
				xaeroVerified = true;
				xaeroAfterDirs = countArenaDirs();
				int lobbyDirs = countLobbyDirs();
				LOGGER.info("[SELFTEST] XAERO arena dirs FINAL = {} (before={}), lobby dirs kept = {}",
						xaeroAfterDirs, xaeroBeforeDirs, lobbyDirs);
				if (xaeroBeforeDirs > 0 && xaeroAfterDirs == 0 && lobbyDirs > 0) {
					LOGGER.info("[SELFTEST] XAERO SWEEP VERIFIED");
				} else {
					LOGGER.warn("[SELFTEST] XAERO SWEEP NOT VERIFIED (before={} after={} lobby={})",
							xaeroBeforeDirs, xaeroAfterDirs, lobbyDirs);
				}
			}
	}

	/** Xaero 专项自测状态（相位驱动） */
	private static int xaeroPlayingSince = -1;
	private static boolean xaeroSampled = false;
	private static int xaeroEndAt = -1;
	private static boolean xaeroShotDone = false;
	private static boolean xaeroChecked1 = false;
	private static boolean xaeroVerified = false;
	private static int xaeroBeforeDirs = -1;
	private static int xaeroAfterDirs = -1;

	private static int countDirs(boolean arena) {
		java.nio.file.Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
		int[] count = {0};
		for (String rootName : new String[]{"xaero", "XaeroWaypoints", "XaeroWorldMap"}) {
			java.nio.file.Path root = gameDir.resolve(rootName);
			if (!java.nio.file.Files.isDirectory(root)) continue;
			try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root, 8)) {
				walk.filter(java.nio.file.Files::isDirectory).forEach(d -> {
					String norm = d.getFileName().toString()
							.replace('$', ':').replace('%', ':').replace('.', ':').toLowerCase();
					if (arena) {
						if (norm.contains("survival-contract:arena") && !norm.contains("survival-contract:lobby")) count[0]++;
					} else if (norm.contains("survival-contract:lobby")) {
						count[0]++;
					}
				});
			} catch (java.io.IOException ignored) {}
		}
		return count[0];
	}
	private static int countArenaDirs() { return countDirs(true); }
	private static int countLobbyDirs() { return countDirs(false); }

	private static void screenshot(String tag) {
		Minecraft client = Minecraft.getInstance();
		try {
			// 保存到 <gameDir>/screenshots/，文件名带时间戳
			net.minecraft.client.Screenshot.grab(client, false);
			LOGGER.info("[SELFTEST] screenshot taken: {}", tag);
		} catch (Throwable e) {
			LOGGER.warn("[SELFTEST] screenshot {} failed: {}", tag, e.toString());
		}
	}

	private static List<Button> findButtons(Minecraft client) {
		List<Button> out = new ArrayList<>();
		if (client.gui.screen() instanceof net.minecraft.client.gui.screens.Screen screen) {
			for (var child : screen.children()) {
				if (child instanceof Button b) out.add(b);
			}
		}
		return out;
	}

	private static void clickButton(Minecraft client, String labelPart) {
		for (Button b : findButtons(client)) {
			if (b.getMessage().getString().contains(labelPart)) {
				click(client, b);
				return;
			}
		}
		LOGGER.warn("[SELFTEST] button containing '{}' NOT FOUND (screen={})", labelPart, client.gui.screen());
	}

	private static void clickButtonNotContaining(Minecraft client, String excludePart) {
		for (Button b : findButtons(client)) {
			if (!b.getMessage().getString().contains(excludePart)) {
				click(client, b);
				return;
			}
		}
		LOGGER.warn("[SELFTEST] no button excluding '{}' FOUND (screen={})", excludePart, client.gui.screen());
	}

	private static void click(Minecraft client, Button b) {
		String before = b.getMessage().getString();
		double cx = b.getX() + b.getWidth() / 2.0;
		double cy = b.getY() + b.getHeight() / 2.0;
		LOGGER.info("[SELFTEST] clicking '{}' at ({},{}) screen={}", before, (int) cx, (int) cy,
				client.gui.screen().getClass().getSimpleName());
		MouseButtonEvent press = new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0));
		boolean consumed = b.mouseClicked(press, false);
		LOGGER.info("[SELFTEST] click consumed={} after='{}' screen={}", consumed, b.getMessage().getString(),
				client.gui.screen() == null ? "null" : client.gui.screen().getClass().getSimpleName());
	}

	private static void clickCard(Minecraft client) {
		clickCardAt(client, 136, 64);
	}

	private static void clickCardAt(Minecraft client, int x, int y) {
		if (client.gui.screen() instanceof ChallengeBookScreen screen) {
			LOGGER.info("[SELFTEST] clicking card at ({},{})", x, y);
			MouseButtonEvent press = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
			boolean consumed = screen.mouseClicked(press, false);
			LOGGER.info("[SELFTEST] card click consumed={}", consumed);
		} else {
			LOGGER.warn("[SELFTEST] card click skipped, screen={}", client.gui.screen());
		}
	}

	private static void clickEventRow(Minecraft client) {
		if (client.gui.screen() instanceof ChallengeBookScreen screen) {
			// 滚到底后，可视区最底部一行 = 最后一个事件（饥饿炸弹）。
			// gridTop=44，gridHeight≈104 → 点击 gridBottom 上方 14px 即最后一行中部。
			double cx = 16 + 50;
			double cy = 44 + screenGridHeight(client) - 14;
			LOGGER.info("[SELFTEST] clicking event row at ({},{})", (int) cx, (int) cy);
			MouseButtonEvent press = new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0));
			boolean consumed = screen.mouseClicked(press, false);
			LOGGER.info("[SELFTEST] event row click consumed={}", consumed);
		} else {
			LOGGER.warn("[SELFTEST] event row click skipped, screen={}", client.gui.screen());
		}
	}

	private static int screenGridHeight(Minecraft client) {
		// gridHeight = height - HEADER_HEIGHT(44) - FOOTER_HEIGHT(76) - padding(16)
		return client.gui.screen().height - 44 - 76 - 16;
	}

	private static void sendCommand(Minecraft client, String command) {
		try {
			if (client.player != null) {
				client.player.connection.sendCommand(command);
				LOGGER.info("[SELFTEST] sent command /{}", command);
			}
		} catch (Throwable e) {
			LOGGER.warn("[SELFTEST] sendCommand {} failed: {}", command, e.toString());
		}
	}

	private static void scroll(Minecraft client, double notches) {
		if (client.gui.screen() instanceof ChallengeBookScreen screen) {
			// y=100 在网格可视区内（gridTop=44, gridTop+gridHeight≈184）
			boolean consumed = screen.mouseScrolled(200, 100, 0, notches);
			LOGGER.info("[SELFTEST] scrolled {} notches, consumed={}", notches, consumed);
		} else {
			LOGGER.warn("[SELFTEST] scroll skipped, screen={}", client.gui.screen());
		}
	}
}
