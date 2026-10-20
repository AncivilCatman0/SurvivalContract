package com.survivalcontract.client.gui;

import com.survivalcontract.client.ClientState;
import com.survivalcontract.network.NetworkRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则总览预览屏（像 Bingo 的棋盘预览）：
 * 游戏开始后按 O 打开 — 左侧规则图标移到屏幕中央棋盘排列，
 * 背景高斯模糊，鼠标悬停图标显示规则具体说明；再按 O 或 ESC 复原并继续游戏。
 * 注意：isPauseScreen=false，单人模式下游戏不会暂停（继续跑计时/事件）。
 */
public class RulePreviewScreen extends Screen {

	private static final int CELL = 26;      // 每格尺寸（16px 图标居中，留悬停容差）
	private static final int CELL_GAP = 4;
	private static final int HEADER_H = 26;
	private static final int FOOTER_H = 20;

	private record Cell(int col, int row, NetworkRegistry.ChallengeSyncPayload.Entry entry,
			com.survivalcontract.event.RandomEventManager.EventType event) {
		static Cell of(int col, int row, NetworkRegistry.ChallengeSyncPayload.Entry e) { return new Cell(col, row, e, null); }
		static Cell event(int col, int row, com.survivalcontract.event.RandomEventManager.EventType ev) { return new Cell(col, row, null, ev); }
		boolean isEvent() { return event != null; }
	}

	private final List<Cell> cells = new ArrayList<>();
	private int panelX, panelY, panelW, panelH;
	private int viewRows;      // 可见行数（受屏高限制）
	private int maxScroll = 0; // 滚动上限（像素）
	private int scrollOffset = 0;

	/** 网格顶部的屏幕 Y（含标题条），渲染与命中检测统一用这个基准 */
	private int gridTop() { return panelY + HEADER_H + 2; }
	/** 网格底部（滚动裁剪区下沿） */
	private int gridBottom() { return panelY + panelH - FOOTER_H - 2; }

	public RulePreviewScreen() {
		super(com.survivalcontract.client.ClientL10n.gui("rule_preview.title"));
	}

	@Override
	protected void init() {
		cells.clear();
		ClientState.INSTANCE.rulePreviewOpen = true; // 左侧 HUD 图标隐藏（"移到中央"）

		List<NetworkRegistry.ChallengeSyncPayload.Entry> enabled = new ArrayList<>();
		for (var e : ClientState.INSTANCE.challengeEntries) {
			if (e.enabled()) enabled.add(e);
		}
		// 已启用的事件也进棋盘（与左侧 HUD 一致：规则图标后追加事件图标）
		List<com.survivalcontract.event.RandomEventManager.EventType> events = new ArrayList<>();
		for (String evId : ClientState.INSTANCE.enabledEvents) {
			try {
				events.add(com.survivalcontract.event.RandomEventManager.EventType.valueOf(evId));
			} catch (Exception ignored) {}
		}
		int total = enabled.size() + events.size();

		// 列数按数量自适应（像 Bingo 棋盘：少→3列，多→4/5列）
		int cols = total <= 9 ? 3 : (total <= 20 ? 4 : 5);
		int neededRows = Math.max(1, (total + cols - 1) / cols);
		// 可见行数按屏高限制，放不下的行通过滚动查看（带滚动条）
		viewRows = Math.min(neededRows, Math.max(1, (this.height - 120) / (CELL + CELL_GAP)));
		int contentH = neededRows * (CELL + CELL_GAP) - CELL_GAP;
		int viewH = viewRows * (CELL + CELL_GAP) - CELL_GAP;
		maxScroll = Math.max(0, contentH - viewH);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		int gridW = cols * CELL + (cols - 1) * CELL_GAP;
		panelW = gridW + 24; // 右侧留滚动条空间
		panelH = viewH + HEADER_H + FOOTER_H + 4;
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		for (int i = 0; i < total; i++) {
			int col = i % cols;
			int row = i / cols;
			if (i < enabled.size()) {
				cells.add(Cell.of(col, row, enabled.get(i)));
			} else {
				cells.add(Cell.event(col, row, events.get(i - enabled.size())));
			}
		}
	}

	/** 格子的实时屏幕坐标（row 叠加滚动偏移） */
	private int cellX(Cell c) { return panelX + 8 + c.col() * (CELL + CELL_GAP); }
	private int cellY(Cell c) { return gridTop() + c.row() * (CELL + CELL_GAP) - scrollOffset; }

	@Override
	public void removed() {
		ClientState.INSTANCE.rulePreviewOpen = false; // 复原左侧 HUD 图标
		super.removed();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// === 高斯模糊背景（原版 blur 后处理）+ 轻度压暗保证可读 ===
		// isInGameUi()=true 时基类模板只画透明背景，本帧唯一一次 blur 由这里调用
		try {
			this.extractBlurredBackground(graphics);
		} catch (IllegalStateException ignored) {
			// 每帧仅允许一次 blur：被占用时跳过（降级为仅压暗层，不崩）
		}
		graphics.fill(0, 0, this.width, this.height, 0x660C0C12);

		// === 居中棋盘面板 ===
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF01A1A20);
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF5A5A5A);
		graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF3A3A3A);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF4A4A4A);
		graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF3A3A3A);

		// 标题条 + 金色分隔线
		graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER_H, 0xFF202020);
		graphics.fill(panelX + 1, panelY + HEADER_H - 1, panelX + panelW - 1, panelY + HEADER_H, 0xFF8A6D1B);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(panelX + panelW / 2.0f, panelY + 8.0f);
		pose.scale(1.4f, 1.4f);
		graphics.centeredText(this.font, com.survivalcontract.client.ClientL10n.gui("rule_preview.header"), 0, 0, 0xFFFFFFFF);
		pose.popMatrix();

		// === 棋盘网格（滚动裁剪区：只画网格区域内的格子） ===
		graphics.enableScissor(panelX + 1, gridTop(), panelX + panelW - 1, gridBottom());
		for (Cell c : cells) {
			int cx = cellX(c);
			int cy = cellY(c);
			if (cy + CELL < gridTop() || cy > gridBottom()) continue; // 视口外跳过
			boolean hovered = mouseX >= cx && mouseX < cx + CELL
					&& mouseY >= cy && mouseY < cy + CELL;
			// 格底：悬停高亮
			graphics.fill(cx, cy, cx + CELL, cy + CELL,
					hovered ? 0xFF3E5E3E : 0xFF262630);
			if (hovered) {
				graphics.fill(cx, cy, cx + CELL, cy + 1, 0xFF81C784);
				graphics.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, 0xFF81C784);
				graphics.fill(cx, cy, cx + 1, cy + CELL, 0xFF81C784);
				graphics.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, 0xFF81C784);
			}

			// 图标（16px 居中）；解析失败画灰色"?"占位（绝不用屏障方块）
			ItemStack icon = c.isEvent()
					? iconForId(ChallengeBookScreen.eventIconItem(c.event()))
					: iconFor(c.entry());
			if (!icon.isEmpty()) {
				graphics.item(icon, cx + (CELL - 16) / 2, cy + (CELL - 16) / 2, 0);
			} else {
				String q = "§8?";
				graphics.text(this.font, Component.literal(q),
						cx + (CELL - this.font.width(q)) / 2,
						cy + (CELL - 8) / 2, 0xFFFFFFFF, false);
			}

			// 计分类挑战 → 左上角小五角星（与 HUD 一致；事件格不加星）
			if (!c.isEvent() && isScoring(c.entry())) {
				pose.pushMatrix();
				pose.translate(cx + 1, cy + 1);
				pose.scale(0.45f, 0.45f);
				graphics.item(starStack(), 0, 0, 0);
				pose.popMatrix();
			}
		}
		graphics.disableScissor();

		// === 滚动条（内容超出一屏时显示，右侧竖条） ===
		if (maxScroll > 0) {
			int trackX = panelX + panelW - 7;
			int trackTop = gridTop() + 2;
			int trackBottom = gridBottom() - 2;
			int trackH = trackBottom - trackTop;
			graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0xFF2A2A34);
			int contentH = maxScroll + (gridBottom() - gridTop());
			int thumbH = Math.max(14, (int) ((float) (gridBottom() - gridTop()) * (gridBottom() - gridTop()) / contentH));
			int thumbY = trackTop + (int) ((float) (trackH - thumbH) * scrollOffset / maxScroll);
			graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF81C784);
		}

		// 底部提示行
		String hint = com.survivalcontract.client.ClientL10n.guiText("rule_preview.hint");
		graphics.text(this.font, Component.literal(hint),
				panelX + (panelW - this.font.width(hint)) / 2, panelY + panelH - 14, 0xFFA0A0A0, false);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// === 悬停 tooltip：规则名 + 描述 + 分值 / 事件名 + 等级 + 描述（数据以服务端 sync 为准）===
		for (Cell c : cells) {
			int cx = cellX(c);
			int cy = cellY(c);
			if (mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL) {
				List<Component> lines = new ArrayList<>();
				if (c.isEvent()) {
					var ev = c.event();
					lines.add(Component.literal("§b" + ev.icon + " "
							+ com.survivalcontract.client.ClientL10n.eventName(ev.name(), ev.displayName)
							+ " " + com.survivalcontract.client.ClientL10n.guiText(
									"rule_preview.event_score", ev.tier, ev.score)));
					String evDesc = com.survivalcontract.client.ClientL10n.eventDesc(ev.name(), ev.description);
					for (String raw : evDesc.split("\n")) {
						for (Component line : wrapText(raw, 160)) {
							lines.add(Component.literal("§7" + line.getString()));
						}
					}
				} else {
					var e = c.entry();
					boolean red = e.baseScore() < 0;
					lines.add(Component.literal((red ? "§c" : "§e")
							+ com.survivalcontract.client.ClientL10n.challengeName(e.challengeId(), e.displayName())));
					String desc = e.description() == null ? ""
							: com.survivalcontract.client.ClientL10n.challengeDesc(e.challengeId(), e.description());
					for (String raw : desc.split("\n")) {
						for (Component line : wrapText(raw, 160)) {
							lines.add(Component.literal("§7" + line.getString()));
						}
					}
					if (red) {
						lines.add(com.survivalcontract.client.ClientL10n.gui(
								"rule_preview.red_rule", -e.baseScore()));
					} else if (!e.challengeId().startsWith("rule_") && e.penaltyScore() > 0) {
						lines.add(com.survivalcontract.client.ClientL10n.gui(
								"rule_preview.score_penalty", e.baseScore(), e.penaltyScore()));
					} else if (!e.challengeId().startsWith("rule_")) {
						lines.add(com.survivalcontract.client.ClientL10n.gui(
								"rule_preview.score_only", e.baseScore()));
					}
				}
				graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
				break;
			}
		}
	}

	/** 按物品 id 字符串解析图标（解析失败返回空栈 → 画灰色"?"占位） */
	private ItemStack iconForId(String id) {
		if (id == null || id.isEmpty()) return ItemStack.EMPTY;
		try {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(
					id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
					id.contains(":") ? id.substring(id.indexOf(':') + 1) : id));
			return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		} catch (Exception ex) {
			return ItemStack.EMPTY;
		}
	}

	/** 从 sync 数据的 iconItemId 解析图标（纯客户端安全，不依赖本地注册表） */
	private ItemStack iconFor(NetworkRegistry.ChallengeSyncPayload.Entry e) {
		try {
			String id = e.iconItemId();
			if (id == null || id.isEmpty()) return ItemStack.EMPTY;
			Identifier ident = Identifier.fromNamespaceAndPath(
					id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
					id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
			Item item = BuiltInRegistries.ITEM.getValue(ident);
			return item == null ? ItemStack.EMPTY : new ItemStack(item);
		} catch (Exception ex) {
			return ItemStack.EMPTY;
		}
	}

	/** 是否为计分类挑战（非 rule_ 规则开关且分值非 0） */
	private boolean isScoring(NetworkRegistry.ChallengeSyncPayload.Entry e) {
		if (e.challengeId().startsWith("rule_")) return false;
		return e.baseScore() != 0 || e.penaltyScore() != 0;
	}

	private ItemStack starStack;

	private ItemStack starStack() {
		if (starStack == null) starStack = new ItemStack(Items.NETHER_STAR);
		return starStack;
	}

	/** 按像素宽度把长文本折行（照抄职业菜单逻辑，按中文标点分句） */
	private List<Component> wrapText(String text, int maxWidth) {
		List<Component> out = new ArrayList<>();
		if (text == null || text.isEmpty()) return out;
		StringBuilder line = new StringBuilder();
		for (String seg : text.split("(?<=，|。|；|、)")) {
			if (line.length() > 0 && this.font.width(line + seg) > maxWidth) {
				out.add(Component.literal(line.toString()));
				line = new StringBuilder();
			}
			line.append(seg);
		}
		if (line.length() > 0) out.add(Component.literal(line.toString()));
		return out;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// 再次按 O 或 ESC → 复原位置并继续游戏
		if (event.key() == InputConstants.KEY_O || event.key() == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		// 方向键上下滚动棋盘
		if (event.key() == InputConstants.KEY_DOWN && maxScroll > 0) {
			scrollOffset = Math.min(maxScroll, scrollOffset + (CELL + CELL_GAP));
			return true;
		}
		if (event.key() == InputConstants.KEY_UP && maxScroll > 0) {
			scrollOffset = Math.max(0, scrollOffset - (CELL + CELL_GAP));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		// 滚轮滚动棋盘（鼠标在面板内时）
		if (maxScroll > 0 && mouseX >= panelX && mouseX <= panelX + panelW
				&& mouseY >= panelY && mouseY <= panelY + panelH) {
			scrollOffset -= (int) (vertical * (CELL + CELL_GAP));
			scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(null); // 直接回游戏（HUD 图标复原）
		}
	}

	@Override
	public boolean isInGameUi() {
		// 游戏内 UI：基类模板走透明背景分支（不调 blur），blur 由本屏手动调用一次
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false; // 单人模式下不暂停游戏，继续计时与事件
	}
}
