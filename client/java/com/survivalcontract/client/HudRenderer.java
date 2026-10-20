package com.survivalcontract.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Survival Contract HUD:
 *   - 左侧：已启用挑战的 bingo 图标栏 (3 列, 最多 9 格)
 *   - 右侧：实时计分板
 *   - 顶部：倒计时
 */
public class HudRenderer {

	private static final int ROW_H = 18;
	private static final int ROW_ICON = 16;
	private static final int ROW_MAX_W = 96;

	public static void register() {
		Identifier leftId = Identifier.fromNamespaceAndPath("survival-contract", "left_bingo");
		Identifier rightId = Identifier.fromNamespaceAndPath("survival-contract", "right_scoreboard");
		Identifier topId = Identifier.fromNamespaceAndPath("survival-contract", "top_timer");
		Identifier bottomId = Identifier.fromNamespaceAndPath("survival-contract", "bottom_prof_task");
		Identifier smuggleId = Identifier.fromNamespaceAndPath("survival-contract", "smuggle_items");

		HudElementRegistry.addLast(leftId, (ctx, tickCounter) -> renderBingoLeft(ctx));
		// 计分板渲染层级降到最底层（addFirst = FIRST root layer，最先渲染），
		// 使其位于 JEI 物品栏/InventoryHUD+ 等右侧叠加层之下（被其覆盖），而非屏幕位置下移。
		HudElementRegistry.addFirst(rightId, (ctx, tickCounter) -> renderScoreboardRight(ctx));
		HudElementRegistry.addLast(topId, (ctx, tickCounter) -> renderTimerTop(ctx));
		HudElementRegistry.addLast(bottomId, (ctx, tickCounter) -> renderBottomInfo(ctx));
		HudElementRegistry.addLast(smuggleId, (ctx, tickCounter) -> renderSmuggleList(ctx));
	}

	/**
	 * 走私犯与检察官：顶部时间栏下方的价值物品清单（居中横排：图标+名称+分值）。
	 * 团队模式额外显示对方清单（仅一件可见，其余问号，无分值）。
	 */
	private static void renderSmuggleList(GuiGraphicsExtractor ctx) {
		ClientState cs = ClientState.INSTANCE;
		if (!"PLAYING".equals(cs.currentPhase)) return;
		if (!"STOWAWAY".equalsIgnoreCase(cs.playStyleKey)) return;
		if (cs.smuggleOwn.isEmpty()) return;
		var font = net.minecraft.client.Minecraft.getInstance().font;

		int gap = 16;
		// === 己方三件：图标 + 名称 + 分值，居中横排 ===
		int count = cs.smuggleOwn.size();
		int[] widths = new int[count];
		int totalW = 0;
		for (int i = 0; i < count; i++) {
			var e = cs.smuggleOwn.get(i);
			String name = itemDisplayName(e.itemId());
			widths[i] = 20 + 4 + font.width(name) + 4 + font.width("§6+" + e.score());
			totalW += widths[i];
		}
		totalW += gap * (count - 1);
		int x = (ctx.guiWidth() - totalW) / 2;
		int y = 42;
		for (int i = 0; i < count; i++) {
			var e = cs.smuggleOwn.get(i);
			String name = itemDisplayName(e.itemId());
			ItemStack stack = iconForItemId(e.itemId());
			if (!stack.isEmpty()) ctx.item(stack, x, y, 0);
			int tx = x + 20;
			ctx.text(font, Component.literal(name), tx, y + 4, 0xFFFFFFFF, true);
			tx += 4 + font.width(name);
			ctx.text(font, Component.literal("§6+" + e.score()), tx, y + 4, 0xFFFFFFFF, true);
			x += widths[i] + gap;
		}

		// === 换批倒计时（居中，己方行下；本地按 sync 经过时间递减） ===
		int elapsed = (int) ((System.currentTimeMillis() - cs.smuggleSyncAtMillis) / 1000);
		int remain = Math.max(0, cs.smuggleBatchRemain - elapsed);
		String batch = com.survivalcontract.client.ClientL10n.guiText("smuggle.batch", remain / 60, remain % 60);
		ctx.text(font, Component.literal(batch), (ctx.guiWidth() - font.width(batch)) / 2, y + 18, 0xFFFFFFFF, true);

		// === 团队模式：对方清单（仅一件可见，其余问号，无分值） ===
		if (!cs.smuggleEnemy.isEmpty()) {
			int y2 = y + 32;
			int eCount = cs.smuggleEnemy.size();
			int[] eWidths = new int[eCount];
			int eTotal = 0;
			for (int i = 0; i < eCount; i++) {
				var e = cs.smuggleEnemy.get(i);
				String name = e.itemId().isEmpty() ? "§8???" : itemDisplayName(e.itemId());
				eWidths[i] = 20 + font.width(name);
				eTotal += eWidths[i];
			}
			String label = com.survivalcontract.client.ClientL10n.guiText("smuggle.enemy");
			eTotal += font.width(label) + 6 + gap * (eCount - 1);
			int ex = (ctx.guiWidth() - eTotal) / 2;
			ctx.text(font, Component.literal(label), ex, y2 + 4, 0xFFFFFFFF, true);
			ex += font.width(label) + 6;
			for (int i = 0; i < eCount; i++) {
				var e = cs.smuggleEnemy.get(i);
				if (!e.itemId().isEmpty()) {
					ItemStack stack = iconForItemId(e.itemId());
					if (!stack.isEmpty()) ctx.item(stack, ex, y2, 0);
				}
				ex += 20;
				String name = e.itemId().isEmpty() ? "§8???" : itemDisplayName(e.itemId());
				ctx.text(font, Component.literal(name), ex, y2 + 4, 0xFFFFFFFF, true);
				ex += font.width(name) + gap;
			}
		}
	}

	/** 物品注册 id → 本地化显示名（解析失败返回占位） */
	private static String itemDisplayName(String itemId) {
		ItemStack stack = iconForItemId(itemId);
		return stack.isEmpty() ? "???" : stack.getHoverName().getString();
	}

	/** 物品注册 id → ItemStack（走私犯清单图标用） */
	private static ItemStack iconForItemId(String itemId) {
		try {
			if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
			String ns = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : "minecraft";
			String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					Identifier.fromNamespaceAndPath(ns, path));
			return item != null ? new ItemStack(item) : ItemStack.EMPTY;
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	/**
	 * 状态栏上方两行：当前职业与不扣分特权 / 玩家专属任务（仅 PLAYING 阶段）。
	 */
	private static void renderBottomInfo(GuiGraphicsExtractor ctx) {
		ClientState state = ClientState.INSTANCE;
		if (!"PLAYING".equals(state.currentPhase)) return;
		var font = net.minecraft.client.Minecraft.getInstance().font;
		int sh = ctx.guiHeight();

		// 行1：职业 + 不扣分特权（HUD 精简描述 = 仅自身免疫扣分说明；完整描述只在职业选择界面显示）
		String profLine;
		try {
			var prof = com.survivalcontract.profession.ProfessionManager.Profession.valueOf(state.professionKey);
			profLine = com.survivalcontract.client.ClientL10n.guiText("hud.profession",
					com.survivalcontract.client.ClientL10n.professionName(state.professionKey, prof.chineseName),
					com.survivalcontract.client.ClientL10n.tr("survival-contract.profession."
							+ state.professionKey + ".hud", prof.hudDesc));
		} catch (Exception e) {
			profLine = com.survivalcontract.client.ClientL10n.guiText("hud.profession_raw", state.professionKey);
		}
		// 行2：专属任务（服务端拼好）
		String taskLine = com.survivalcontract.client.ClientL10n.guiText("hud.task", state.taskText);

		// 状态栏（血/饥饿）上方两行，水平居中
		int y1 = sh - 59;
		ctx.text(font, Component.literal(profLine), (ctx.guiWidth() - font.width(profLine)) / 2, y1, 0xFFFFFFFF, true);
		ctx.text(font, Component.literal(taskLine), (ctx.guiWidth() - font.width(taskLine)) / 2, y1 - 11, 0xFFFFFFFF, true);
	}

	/**
	 * 左侧活动挑战栏：仅 PLAYING 阶段显示。
	 * 竖向单列排列，仅显示图标；计分类挑战图标左上角带小号五角星（原版下界之星素材）标记。
	 */
	private static void renderBingoLeft(GuiGraphicsExtractor ctx) {
		ClientState state = ClientState.INSTANCE;
		if (!"PLAYING".equals(state.currentPhase)) return; // 只在游戏开始后显示
		if (state.rulePreviewOpen) return; // 规则总览预览中：图标已"移到"中央棋盘，左侧隐藏
		List<String> enabled = state.enabledChallengeIds();

		// 事件图标追加在规则图标之后（与挑战书事件区一致：仅显示该玩家已启用的事件）
		List<com.survivalcontract.event.RandomEventManager.EventType> events = new java.util.ArrayList<>();
		for (String evId : state.enabledEvents) {
			try {
				events.add(com.survivalcontract.event.RandomEventManager.EventType.valueOf(evId));
			} catch (Exception ignored) {}
		}
		int total = enabled.size() + events.size();
		if (total == 0) return;

		int sh = ctx.guiHeight();
		int cell = ROW_ICON + 2; // 图标 16px + 2px 间距

		// 竖向排列：行数按屏高自适应（顶部计时器下方到底部聊天区上方）
		int maxRows = Math.max(1, (sh - 100) / cell);
		int visible = Math.min(total, maxRows);

		int startX = 4;
		int startY = 56; // 顶部计时器下方，避开聊天区
		var mc = net.minecraft.client.Minecraft.getInstance();

		for (int i = 0; i < visible; i++) {
			int y = startY + i * cell;
			if (i < enabled.size()) {
				String id = enabled.get(i);
				ItemStack icon = iconForChallenge(id);
				if (!icon.isEmpty()) {
					ctx.item(icon, startX, y, 0);
				}
				// 计分类挑战（有基础分或扣分）→ 左上角小五角星标记
				if (isScoringChallenge(id)) {
					ctx.pose().pushMatrix();
					ctx.pose().translate(startX, y);
					ctx.pose().scale(0.45f);
					ctx.item(starStack(), 0, 0, 0);
					ctx.pose().popMatrix();
				}
			} else {
				// 事件图标
				var ev = events.get(i - enabled.size());
				ItemStack icon = iconForId(
						com.survivalcontract.client.gui.ChallengeBookScreen.eventIconItem(ev));
				if (!icon.isEmpty()) {
					ctx.item(icon, startX, y, 0);
				}
			}
		}

		// 放不下的条目数提示
		int hidden = total - visible;
		if (hidden > 0) {
			ctx.text(mc.font, Component.literal("§7+" + hidden),
					startX, startY + visible * cell, 0xFFFFFFFF, true);
		}
	}

	/** 按物品 id 字符串解析图标（解析失败返回空栈 → 不渲染） */
	private static ItemStack iconForId(String id) {
		if (id == null || id.isEmpty()) return ItemStack.EMPTY;
		try {
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					net.minecraft.resources.Identifier.fromNamespaceAndPath(
							id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
							id.contains(":") ? id.substring(id.indexOf(':') + 1) : id));
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	/** 下界之星物品栈（计分挑战的左上角小标记）——懒加载：entrypoint 阶段组件尚未绑定，静态初始化会崩溃 */
	private static ItemStack starStack;

	private static ItemStack starStack() {
		if (starStack == null) starStack = new ItemStack(Items.NETHER_STAR);
		return starStack;
	}

	/** 是否为计分类挑战（非 rule_ 规则开关且非 0 分） */
	private static boolean isScoringChallenge(String id) {
		if (id.startsWith("rule_")) return false;
		com.survivalcontract.challenge.Challenge c =
				com.survivalcontract.challenge.ChallengeManager.getInstance().getChallenge(id);
		return c != null && (c.getBaseScore() != 0 || c.getPenaltyScore() != 0);
	}

	private static void renderScoreboardRight(GuiGraphicsExtractor ctx) {
		var scores = ClientState.INSTANCE.scores;
		if (scores.isEmpty()) return;

		var font = net.minecraft.client.Minecraft.getInstance().font;
		int sw = ctx.guiWidth();
		int sh = ctx.guiHeight();
		int rowH = 12;

		// 每行 = 标签 + 分数文本 + 名称颜色（直接存 int，不要用字符串解析——
		// Integer.decode("0xFFFF5555") 超 Integer.MAX_VALUE 会抛异常导致渲染帧崩溃）
		record ScoreRow(String label, String scoreText, int color) {}
		java.util.List<ScoreRow> rows = new java.util.ArrayList<>();
		String title;
		if ("teams".equalsIgnoreCase(ClientState.INSTANCE.gameModeKey)) {
			title = com.survivalcontract.client.ClientL10n.guiText("scoreboard.team_title");
			// 两队明细互相可见：队伍总分表头 + 各队成员个人分数（每队最多 8 行，两队共 16 人）
			java.util.List<com.survivalcontract.network.NetworkRegistry.ScoreSyncPayload.ScoreEntry> reds = new java.util.ArrayList<>();
			java.util.List<com.survivalcontract.network.NetworkRegistry.ScoreSyncPayload.ScoreEntry> blues = new java.util.ArrayList<>();
			int red = 0, blue = 0;
			for (var e : scores) {
				if (e.team() == com.survivalcontract.game.GameStateManager.Team.RED) {
					reds.add(e);
					red += e.score();
				} else if (e.team() == com.survivalcontract.game.GameStateManager.Team.BLUE) {
					blues.add(e);
					blue += e.score();
				}
			}
			reds.sort((a, b) -> Integer.compare(b.score(), a.score()));
			blues.sort((a, b) -> Integer.compare(b.score(), a.score()));
			boolean redFirst = red >= blue; // 高分队在上，前缀 ★ 标记领先队
			// 头行 = 队伍总分，其下最多 8 行成员个人分数（两队明细互相可见，共 16 人）
			String redLabel = com.survivalcontract.client.ClientL10n.guiText("team.red");
			String blueLabel = com.survivalcontract.client.ClientL10n.guiText("team.blue");
			rows.add(new ScoreRow(redFirst ? "★ " + redLabel : "  " + redLabel, String.valueOf(red), 0xFFFF5555));
			int maxR = Math.min(8, reds.size());
			for (int i = 0; i < maxR; i++) {
				var e = reds.get(i);
				rows.add(new ScoreRow("  " + e.playerName(), String.valueOf(e.score()), 0xFFCCCCCC));
			}
			rows.add(new ScoreRow(redFirst ? "  " + blueLabel : "★ " + blueLabel, String.valueOf(blue), 0xFF5555FF));
			int maxB = Math.min(8, blues.size());
			for (int i = 0; i < maxB; i++) {
				var e = blues.get(i);
				rows.add(new ScoreRow("  " + e.playerName(), String.valueOf(e.score()), 0xFFCCCCCC));
			}
		} else {
			title = com.survivalcontract.client.ClientL10n.guiText("scoreboard.title");
			int maxRows = Math.min(16, scores.size());
			for (int i = 0; i < maxRows; i++) {
				var e = scores.get(i);
				int teamColor = switch (e.team().name()) {
					case "RED" -> 0xFFFF5555;
					case "BLUE" -> 0xFF5555FF;
					default -> 0xFFFFFFFF;
				};
				String medal = i == 0 ? "§6" : i == 1 ? "§e" : i == 2 ? "§a" : "§7";
				String prefix = i == 0 ? "★ " : i == 1 ? "♦ " : i == 2 ? "● " : "  ";
				rows.add(new ScoreRow(medal + prefix + e.playerName(), "§f" + e.score(), teamColor));
			}
		}

		// 面板宽度自适应最长名字（显示全称不截断），最窄 90
		int maxLabelW = 0;
		int maxScoreW = 0;
		for (ScoreRow row : rows) {
			maxLabelW = Math.max(maxLabelW, font.width(row.label()));
			maxScoreW = Math.max(maxScoreW, font.width(row.scoreText()));
		}
		int panelW = Math.max(90, maxLabelW + maxScoreW + 18);
		int panelH = 17 + rows.size() * rowH + 3;
		int right = sw - 4;
		int left = right - panelW;
		// 右侧垂直居中（渲染层级已降至 JEI/InventoryHUD+ 叠加层之下，无需再靠下移避让）
		int top = Math.max(56, (sh - panelH) / 2);

		// 底板半透明（0x66 ≈ 40% 不透明度），标题条保持深红
		ctx.fill(left, top, right, top + panelH, 0x66000000);
		ctx.fill(left, top, right, top + 14, 0x884a1a1a);
		ctx.text(font, Component.literal(title), left + 4, top + 2, 0xFFFFFFFF, true);

		int y = top + 17;
		for (ScoreRow row : rows) {
			ctx.text(font, Component.literal(row.label()), left + 4, y, row.color(), true);
			ctx.text(font, Component.literal(row.scoreText()), right - maxScoreW - 4, y, 0xFFFFFFFF, true);
			y += rowH;
		}
	}

	private static void renderTimerTop(GuiGraphicsExtractor ctx) {
		// 只在 PLAYING 阶段显示倒计时（大厅/结束阶段不显示）
		if (!"PLAYING".equals(ClientState.INSTANCE.currentPhase)) return;
		int secs = ClientState.INSTANCE.remainingSeconds;
		int total = ClientState.INSTANCE.totalDurationSeconds;
		// remainingSeconds == -1 表示无限制时长（仍然显示）；0 表示非游戏阶段
		if (secs == 0) return;

		var font = net.minecraft.client.Minecraft.getInstance().font;

		// 时间文本：-1 = 无限制
		String timeText;
		float progress = 1.0f;
		if (secs == -1) {
			timeText = com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.unlimited", "§6§l⏱ 无限制");
		} else {
			timeText = String.format("§6§l⏱ %02d:%02d", secs / 60, secs % 60);
			progress = total > 0 ? Math.min(1.0f, Math.max(0.0f, (float) secs / total)) : 1.0f;
		}

		// 最终目标行（居中显示在时间栏上方）；神的谎言无最终目标 → 隐藏该行
		String goalKey = ClientState.INSTANCE.finalGoalKey;
		boolean hasGoal = goalKey != null && !goalKey.isEmpty();
		String goalName = goalDisplayName(goalKey);
		String goalText = hasGoal ? com.survivalcontract.client.ClientL10n.guiText("hud.goal", goalName) : "";

		// 时间栏宽度拉长一倍（原为文本宽+16 → 现为文本宽*2+32，且不小于目标文本宽）
		int w = Math.max(font.width(timeText) * 2 + 32, hasGoal ? font.width(goalText) + 48 : 0);
		int x = (ctx.guiWidth() - w) / 2;
		int y = 4;
		int h = 34;

		// （无底板：文字自带阴影，直接画）

		// 行1：最终目标（居中）
		if (hasGoal) {
			ctx.text(font, Component.literal(goalText),
					x + (w - font.width(goalText)) / 2, y + 4, 0xFFFFFFFF, true);
		}
		// 行2：计时（居中）
		ctx.text(font, Component.literal(timeText),
				x + (w - font.width(timeText)) / 2, y + 15, 0xFFFFFFFF, true);
		// 行3：进度条（无限制时满格）
		int barW = w - 8;
		ctx.fill(x + 4, y + h - 6, x + 4 + barW, y + h - 3, 0xFF333333);
		int color = progress > 0.3f ? 0xFF00FF55 : progress > 0.1f ? 0xFFFFAA00 : 0xFFFF3333;
		ctx.fill(x + 4, y + h - 6, x + 4 + (int)(barW * progress), y + h - 3, color);
	}

	/** 最终目标显示名（语言文件优先，回退简中） */
	private static String goalDisplayName(String key) {
		String fallback = switch (key == null ? "" : key) {
			case "ENDER_DRAGON" -> "解放末地";
			case "ENDER_END" -> "结束了";
			case "WITHER" -> "凋零山庄";
			default -> key;
		};
		return com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.goal." + key, fallback);
	}

	/** 从挑战定义（唯一数据源）解析图标，避免硬编码映射表与注册表失配 */
	private static ItemStack iconForChallenge(String challengeId) {
		com.survivalcontract.challenge.ChallengeManager.getInstance().ensureRegistered();
		com.survivalcontract.challenge.Challenge c =
				com.survivalcontract.challenge.ChallengeManager.getInstance().getChallenge(challengeId);
		if (c == null) return new ItemStack(Items.BARRIER);
		try {
			String id = c.getIconItemId();
			Identifier ident = Identifier.fromNamespaceAndPath(
					id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
					id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
			net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(ident);
			return item != null ? new ItemStack(item) : new ItemStack(Items.BARRIER);
		} catch (Exception e) {
			return new ItemStack(Items.BARRIER);
		}
	}
}
