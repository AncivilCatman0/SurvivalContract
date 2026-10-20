package com.survivalcontract.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 世界创建 UI 端到端自动化验证（仅环境变量 SC_UITEST=1 启用，不使用 quickPlay，从标题界面起步）。
 * <ol>
 *   <li>标题 → 单人游戏 → 创建新世界：断言世界类型默认选中「sc大厅」，以 uitest-lobby 建图，
 *       断言进大厅维度、背包有挑战书、存档有 marker。</li>
 *   <li>退回标题 → 再次创建：把世界类型切回原版「默认」，以 uitest-normal 建图，
 *       断言留在主世界、无挑战书、无 marker。</li>
 * </ol>
 * 全程输出 [UITEST] 日志，结束打印 UITEST COMPLETE / UITEST FAILED。
 */
public class CreateWorldUiTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("survival-contract-uitest");
	private static final boolean ENABLED = System.getenv("SC_UITEST") != null;

	private static final String WORLD_LOBBY = "uitest-lobby";
	private static final String WORLD_NORMAL = "uitest-normal";

	private enum Stage {
		TITLE, SELECT1, CREATE1, LOADING1, VERIFY1,
		BACK_TITLE, SELECT2, CREATE2_CYCLE, LOADING2, VERIFY2, DONE, FAILED
	}

	private static Stage stage = Stage.TITLE;
	private static int stageTick = 0;
	private static CycleButton<?> typeButton;
	private static int cyclePresses = 0;

	public static void register() {
		if (!ENABLED) return;
		LOGGER.info("[UITEST] enabled (SC_UITEST env var present)");
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				LOGGER.info("[UITEST] joined world, stage={}", stage));
		ClientTickEvents.END_CLIENT_TICK.register(CreateWorldUiTest::tick);
	}

	private static void fail(String msg) {
		LOGGER.error("[UITEST] FAILED at {}: {}", stage, msg);
		stage = Stage.FAILED;
	}

	private static void gotoStage(Stage s) {
		stage = s;
		stageTick = 0;
		LOGGER.info("[UITEST] -> {}", s);
	}

	private static void tick(Minecraft client) {
		stageTick++;
		if (stage == Stage.FAILED) {
			if (stageTick > 60) client.stop();
			return;
		}
		if (stage == Stage.DONE) {
			if (stageTick > 60) client.stop();
			return;
		}
		try {
			tickStage(client);
		} catch (Exception e) {
			LOGGER.error("[UITEST] exception at {}", stage, e);
			fail(e.toString());
		}
	}

	private static void tickStage(Minecraft client) {
		Screen screen = client.gui.screen();
		switch (stage) {
			case TITLE -> {
				if (screen instanceof TitleScreen) {
					if (!clickKey(client, "menu.singleplayer")) fail("title: no singleplayer button");
					else gotoStage(Stage.SELECT1);
				} else if (stageTick > 400) fail("title screen timeout");
			}
			case SELECT1 -> {
				if (screen instanceof SelectWorldScreen) {
					if (!clickKey(client, "selectWorld.create")) fail("select: no create button");
					else gotoStage(Stage.CREATE1);
				} else if (stageTick > 200) fail("select world screen timeout");
			}
			case CREATE1 -> {
				if (screen instanceof CreateWorldScreen cws) {
					if (stageTick == 1) {
						String label = cws.getUiState().getWorldType().describePreset().getString();
						String expect = Component.translatable("generator.survival-contract.sc_lobby").getString();
						LOGGER.info("[UITEST] default world type label='{}' expect='{}'", label, expect);
						if (!expect.equals(label)) {
							fail("默认世界类型不是 sc大厅，实际=" + label);
							return;
						}
						cws.getUiState().setName(WORLD_LOBBY);
					}
					// 给名称同步与界面刷新几帧
					if (stageTick > 8) {
						if (!clickKey(client, "selectWorld.create")) fail("create1: create button");
						else gotoStage(Stage.LOADING1);
					}
				} else if (stageTick > 400) fail("create world screen 1 timeout");
			}
			case LOADING1 -> {
				if (client.player != null && client.level != null) {
					gotoStage(Stage.VERIFY1);
				} else if (stageTick > 600) fail("loading world 1 timeout");
			}
			case VERIFY1 -> {
				if (stageTick == 60) {
					ResourceKey<Level> dim = client.level.dimension();
					boolean hasBook = hasChallengeBook(client);
					boolean marker = markerExists(WORLD_LOBBY);
					LOGGER.info("[UITEST] world1: dim={} hasBook={} marker={}", dim.identifier(), hasBook, marker);
					boolean dimOk = dim.identifier().equals(Identifier.fromNamespaceAndPath("survival-contract", "lobby"));
					if (dimOk && hasBook && marker) {
						LOGGER.info("[UITEST] VERIFIED 1/2: sc大厅建图 → 大厅 + 挑战书 + marker");
						client.disconnectFromWorld(Component.literal("uitest"));
						gotoStage(Stage.BACK_TITLE);
					} else {
						fail("world1 断言失败 dimOk=" + dimOk + " hasBook=" + hasBook + " marker=" + marker);
					}
				}
			}
			case BACK_TITLE -> {
				if (screen instanceof TitleScreen) {
					if (!clickKey(client, "menu.singleplayer")) fail("back title: no button");
					else gotoStage(Stage.SELECT2);
				} else if (stageTick > 600) fail("back to title timeout");
			}
			case SELECT2 -> {
				if (screen instanceof SelectWorldScreen) {
					if (!clickKey(client, "selectWorld.create")) fail("select2: no create button");
					else { typeButton = null; cyclePresses = 0; gotoStage(Stage.CREATE2_CYCLE); }
				} else if (stageTick > 200) fail("select world screen 2 timeout");
			}
			case CREATE2_CYCLE -> {
				if (screen instanceof CreateWorldScreen cws) {
					String normal = Component.translatable("generator.minecraft.normal").getString();
					String current = cws.getUiState().getWorldType().describePreset().getString();
					if (normal.equals(current)) {
						cws.getUiState().setName(WORLD_NORMAL);
						if (stageTick > 6) {
							if (!clickKey(client, "selectWorld.create")) fail("create2: create button");
							else gotoStage(Stage.LOADING2);
						}
						return;
					}
					// 世界类型按钮在「世界」标签页内，自动化直接走 uiState 公开 API
					// （与点击循环按钮同一条 setWorldType → updateDimensions 链路）
					if (stageTick == 2) {
						var registry = cws.getUiState().getSettings().worldgenLoadContext()
								.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET);
						var normalHolder = registry.get(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL);
						if (normalHolder.isEmpty()) {
							fail("注册表中找不到 minecraft:normal 预设");
							return;
						}
						cws.getUiState().setWorldType(
								new net.minecraft.client.gui.screens.worldselection.WorldCreationUiState
										.WorldTypeEntry(normalHolder.get()));
						LOGGER.info("[UITEST] 第二个世界世界类型程序化切为：{}",
								cws.getUiState().getWorldType().describePreset().getString());
					}
				} else if (stageTick > 400) fail("create world screen 2 timeout");
			}
			case LOADING2 -> {
				if (client.player != null && client.level != null) {
					gotoStage(Stage.VERIFY2);
				} else if (stageTick > 600) fail("loading world 2 timeout");
			}
			case VERIFY2 -> {
				if (stageTick == 60) {
					ResourceKey<Level> dim = client.level.dimension();
					boolean hasBook = hasChallengeBook(client);
					boolean marker = markerExists(WORLD_NORMAL);
					LOGGER.info("[UITEST] world2: dim={} hasBook={} marker={}", dim.identifier(), hasBook, marker);
					boolean dimOk = dim.identifier().equals(Identifier.withDefaultNamespace("overworld"));
					if (dimOk && !hasBook && !marker) {
						LOGGER.info("[UITEST] VERIFIED 2/2: 默认类型建图 → 主世界 + 无挑战书 + 无 marker");
						LOGGER.info("[UITEST] UITEST COMPLETE");
						client.disconnectFromWorld(Component.literal("uitest"));
						gotoStage(Stage.DONE);
					} else {
						fail("world2 断言失败 dimOk=" + dimOk + " hasBook=" + hasBook + " marker=" + marker);
					}
				}
			}
			default -> { }
		}
	}

	private static boolean hasChallengeBook(Minecraft client) {
		var inv = client.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var stack = inv.getItem(i);
			if (stack != null && !stack.isEmpty()
					&& stack.is(Items.ENCHANTED_BOOK)
					&& stack.getHoverName().getString().contains("挑战书")) {
				return true;
			}
		}
		return false;
	}

	private static boolean markerExists(String worldName) {
		Path p = Minecraft.getInstance().gameDirectory.toPath()
				.resolve("saves").resolve(worldName)
				.resolve("survival-contract").resolve("lobby-world.marker");
		return Files.exists(p);
	}

	/** 递归遍历界面（26.2 的世界类型按钮嵌在标签页子布局里）。 */
	private static void collectCycleButtons(Object node, java.util.List<String> texts) {
		if (node instanceof CycleButton<?> cb) {
			texts.add("['" + cb.getMessage().getString() + "']");
		}
		if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler ceh) {
			for (var child : ceh.children()) collectCycleButtons(child, texts);
		}
	}

	private static CycleButton<?> findTypeButton(Object root, String currentLabel) {
		if (root instanceof CycleButton<?> cb
				&& cb.visible && cb.getMessage().getString().contains(currentLabel)) {
			return cb;
		}
		if (root instanceof net.minecraft.client.gui.components.events.ContainerEventHandler ceh) {
			for (var child : ceh.children()) {
				CycleButton<?> found = findTypeButton(child, currentLabel);
				if (found != null) return found;
			}
		}
		return null;
	}

	/** 递归查找文本完全匹配的控件并真实点击。 */
	private static boolean clickWidgetByText(Minecraft client, Object root, String text) {
		if (root instanceof AbstractWidget w && w.visible && w.active
				&& text.equals(w.getMessage().getString())) {
			clickWidget(client, w);
			return true;
		}
		if (root instanceof net.minecraft.client.gui.components.events.ContainerEventHandler ceh) {
			for (var child : ceh.children()) {
				if (clickWidgetByText(client, child, text)) return true;
			}
		}
		return false;
	}

	private static boolean clickKey(Minecraft client, String translationKey) {
		Screen screen = client.gui.screen();
		if (screen == null) return false;
		String expect = Component.translatable(translationKey).getString();
		for (var child : screen.children()) {
			if (child instanceof Button b && b.visible && b.active
					&& expect.equals(b.getMessage().getString())) {
				clickWidget(client, b);
				return true;
			}
		}
		LOGGER.warn("[UITEST] button '{}' ({}) not found on {}", translationKey, expect,
				screen.getClass().getSimpleName());
		return false;
	}

	private static void clickWidget(Minecraft client, AbstractWidget w) {
		double cx = w.getX() + w.getWidth() / 2.0;
		double cy = w.getY() + w.getHeight() / 2.0;
		MouseButtonEvent press = new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0));
		boolean consumed = w.mouseClicked(press, false);
		LOGGER.info("[UITEST] click '{}' at ({},{}) consumed={} screen={}",
				w.getMessage().getString(), (int) cx, (int) cy, consumed,
				client.gui.screen() == null ? "null" : client.gui.screen().getClass().getSimpleName());
	}
}
