package com.survivalcontract.client.gui;

import com.survivalcontract.client.ClientL10n;
import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 神谕纸符 · 链接（第五十九批，神的谎言使者用）：
 * 使者右键纸符 → 服务端校验后下发存活玩家清单 → 点选一名玩家确认链接（确认后才消耗纸符）。
 * 链接效果（首次被指认时触发）：还有队友存活 = 使者与链接者一同淘汰；
 * 仅剩自己 = 使者不受影响、链接者自动获得一次神罚（对方纸符可挡下）。
 * 数据来自服务端 RelicLinkStatePayload（当前未被淘汰的所有玩家），本界面不做本地判断。
 */
public class RelicLinkScreen extends Screen {

	private record LinkRow(java.util.UUID id, String name) {}

	private static final int HEADER_HEIGHT = 44;
	private static final int PANEL_W = 240;
	private static final int ROW_H = 24;
	private static final int SCROLL_SPEED = 24;

	private final NetworkRegistry.RelicLinkStatePayload state;
	private final List<LinkRow> rows = new ArrayList<>();
	private LinkRow selected = null;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private int listTop, listHeight;

	public RelicLinkScreen(NetworkRegistry.RelicLinkStatePayload state) {
		super(com.survivalcontract.client.ClientL10n.gui("relic.title"));
		this.state = state;
		for (var e : state.players()) rows.add(new LinkRow(e.id(), e.name()));
	}

	@Override
	protected void init() {
		listTop = HEADER_HEIGHT;
		listHeight = this.height - HEADER_HEIGHT - 64;
		int contentHeight = rows.size() * ROW_H;
		maxScroll = Math.max(0, contentHeight - listHeight);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		int btnY = listTop + listHeight + 8;
		int bw = (PANEL_W - 12) / 2;
		addRenderableWidget(Button.builder(
				Component.literal(ClientL10n.tr("survival-contract.gui.relic.confirm", "确认链接")),
				btn -> confirm()).bounds((this.width - PANEL_W) / 2 + 4, btnY, bw, 20).build());
		addRenderableWidget(Button.builder(
				Component.literal(ClientL10n.tr("survival-contract.gui.accuse.cancel", "取消")),
				btn -> onClose()).bounds((this.width - PANEL_W) / 2 + bw + 8, btnY, bw, 20).build());
	}

	private void confirm() {
		if (selected == null) return;
		ClientPlayNetworking.send(new NetworkRegistry.ConfirmRelicLinkPayload(selected.id()));
		this.minecraft.setScreenAndShow(null);
	}

	private int rowX() { return (this.width - PANEL_W) / 2; }

	private int rowY(int index) { return listTop + index * ROW_H - scrollOffset; }

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean active) {
		int mx = (int) event.x(), my = (int) event.y();
		if (mx >= rowX() && mx <= rowX() + PANEL_W && my >= listTop && my <= listTop + listHeight) {
			for (int i = 0; i < rows.size(); i++) {
				int y = rowY(i);
				if (my >= y && my <= y + ROW_H - 2) {
					selected = selected == rows.get(i) ? null : rows.get(i); // 再点一次取消选择
					return true;
				}
			}
		}
		return super.mouseClicked(event, active);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (maxScroll > 0) {
			scrollOffset -= (int) (vertical * SCROLL_SPEED);
			scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 && selected != null) { confirm(); return true; } // Enter 确认
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.setScreenAndShow(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		var font = this.font;
		int left = rowX();

		// 半透明压暗背景
		g.fillGradient(0, 0, this.width, this.height, 0x8C16100A, 0xB416100A);

		// 标题条
		g.fill(left - 8, 6, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xE01A140A);
		g.fill(left - 8, HEADER_HEIGHT - 8, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xFFBF8A4A);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(this.width / 2.0f, 14.0f);
		pose.scale(1.3f, 1.3f);
		g.centeredText(font, com.survivalcontract.client.ClientL10n.gui("relic.header"), 0, 0, 0xFFFFFFFF);
		pose.popMatrix();

		// 说明行
		String roleLine = ClientL10n.tr("survival-contract.gui.relic.role_line", "§7选择一名玩家建立链接（每局一次）");
		g.centeredText(font, Component.literal(roleLine), this.width / 2, HEADER_HEIGHT - 16, 0xFFFFFFFF);

		// 存活玩家列表（滚动裁剪）
		g.enableScissor(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight);
		g.fill(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight, 0xC014100A);
		if (rows.isEmpty()) {
			g.centeredText(font, Component.literal(
							ClientL10n.tr("survival-contract.gui.accuse.no_players", "§7当前没有其他存活玩家")),
					this.width / 2, listTop + 12, 0xFFFFFFFF);
		}
		for (int i = 0; i < rows.size(); i++) {
			int y = rowY(i);
			if (y + ROW_H < listTop || y > listTop + listHeight) continue;
			boolean sel = selected == rows.get(i);
			boolean hovered = !sel && mouseX >= left + 4 && mouseX <= left + PANEL_W - 4
					&& mouseY >= y && mouseY <= y + ROW_H - 2;
			int bg = sel ? 0xFF5A4218 : hovered ? 0xFF443620 : 0xFF2C2416;
			g.fill(left + 4, y, left + PANEL_W - 4, y + ROW_H - 2, bg);
			int border = sel ? 0xFFD0A040 : hovered ? 0xFFA08850 : 0xFF665533;
			g.fill(left + 4, y, left + PANEL_W - 4, y + 1, border);
			g.fill(left + 4, y + ROW_H - 3, left + PANEL_W - 4, y + ROW_H - 2, border);
			String mark = sel ? "§6▶ " : "§f";
			g.text(font, Component.literal(mark + rows.get(i).name()), left + 12, y + 8, 0xFFFFFFFF, false);
		}
		g.disableScissor();

		// 滚动条
		if (maxScroll > 0) {
			int barX = left + PANEL_W + 2;
			int thumbH = Math.max(20, listHeight * listHeight / (maxScroll + listHeight));
			int thumbY = listTop + scrollOffset * (listHeight - thumbH) / maxScroll;
			g.fill(barX, listTop, barX + 5, listTop + listHeight, 0x80555555);
			g.fill(barX, thumbY, barX + 5, thumbY + thumbH, 0xFFAAAAAA);
		}

		// 底部提示
		String hint = ClientL10n.tr("survival-contract.gui.relic.hint",
				"§7点选一名玩家后点击确认：首次被指认时，链接效果触发");
		g.centeredText(font, Component.literal(hint), this.width / 2, this.height - 22, 0xFFC0C0C0);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
