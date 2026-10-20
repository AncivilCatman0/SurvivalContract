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
 * 检查发起书（走私犯与检察官模式）三种模式（由服务端 ProsecuteStatePayload 决定）：
 * <ul>
 *   <li>发起模式（bribeTotal=0 且非 pendingPhase）：点选一名非友方玩家作为自己的检察官，
 *       确认后双方 3 秒倒计时进入检查室，由对方定夺（受贿 / 检查）</li>
 *   <li>决策模式（pendingPhase 且 bribeTotal≥0）：我是检察官——显示对方贿赂袋价值，
 *       两按钮定夺：「受贿」（清空其贿赂袋获得对应分数，其货物袋同步清空兑换成分）/
 *       「进行检查」（无走私品赔 1/4 货款且其货物袋同步兑换；有走私品没收其全部货物）</li>
 *   <li>等待模式（pendingPhase 且 bribeTotal=-1）：我是被检查者——等待检察官定夺</li>
 * </ul>
 * 无总分提示区间、无冷却时间，本界面不做本地判断。
 */
public class ProsecuteScreen extends Screen {

	private record BookRow(java.util.UUID id, String name, boolean friend) {}

	private static final int HEADER_HEIGHT = 60;
	private static final int PANEL_W = 250;
	private static final int ROW_H = 24;
	private static final int SCROLL_SPEED = 24;

	private final NetworkRegistry.ProsecuteStatePayload state;
	private final List<BookRow> rows = new ArrayList<>();
	private BookRow selected = null;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private int listTop, listHeight;
	private Button confirmBtn;
	private Button bribeBtn;
	private Button inspectBtn;

	public ProsecuteScreen(NetworkRegistry.ProsecuteStatePayload state) {
		super(ClientL10n.gui("prosecute.title"));
		this.state = state;
		for (var e : state.players()) rows.add(new BookRow(e.id(), e.name(), e.friend()));
		// 检查进行中（决策/等待模式）：清单仅含对方 → 预选
		if (state.pendingPhase() && rows.size() == 1) {
			selected = rows.get(0);
		}
	}

	/** 决策模式（我是检察官，已进室，显示贿赂总价 + 定夺按钮）？ */
	private boolean decisionPhase() { return state.pendingPhase() && state.bribeTotal() >= 0; }

	/** 等待模式（我是被检查者，等待检察官定夺） */
	private boolean waitingPhase() { return state.pendingPhase() && state.bribeTotal() < 0; }

	@Override
	protected void init() {
		listTop = HEADER_HEIGHT;
		listHeight = this.height - HEADER_HEIGHT - 60;
		int contentHeight = rows.size() * ROW_H;
		maxScroll = Math.max(0, contentHeight - listHeight);
		scrollOffset = Math.min(scrollOffset, maxScroll);

		int bx = (this.width - PANEL_W) / 2 + 4;
		int btnY = listTop + listHeight + 16;
		int bw = (PANEL_W - 12) / 2;

		if (decisionPhase()) {
			// 决策模式：受贿 / 进行检查 两按钮
			bribeBtn = Button.builder(Component.literal(
							ClientL10n.tr("survival-contract.gui.prosecute.accept_bribe", "§a受贿")),
					btn -> sendAction(0)).bounds(bx, btnY, bw, 20).build();
			addRenderableWidget(bribeBtn);
			inspectBtn = Button.builder(Component.literal(
							ClientL10n.tr("survival-contract.gui.prosecute.inspect", "§6进行检查")),
					btn -> sendAction(1)).bounds(bx + bw + 8, btnY, bw, 20).build();
			addRenderableWidget(inspectBtn);
		} else if (waitingPhase()) {
			addRenderableWidget(Button.builder(
					Component.literal(ClientL10n.tr("survival-contract.gui.prosecute.close", "§7关闭")),
					btn -> onClose()).bounds(bx + bw / 2, btnY, bw, 20).build());
		} else {
			// 发起模式：确认发起（需先选一名非友方检察官）
			confirmBtn = Button.builder(
					Component.literal(ClientL10n.tr("survival-contract.gui.prosecute.confirm", "§b确认发起检查")),
					btn -> confirm()).bounds(bx, btnY, bw, 20).build();
			confirmBtn.active = selected != null;
			addRenderableWidget(confirmBtn);
			addRenderableWidget(Button.builder(
					Component.literal(ClientL10n.tr("survival-contract.gui.prosecute.cancel", "取消")),
					btn -> onClose()).bounds(bx + bw + 8, btnY, bw, 20).build());
		}
	}

	private boolean canConfirm() {
		return !state.pendingPhase() && selected != null;
	}

	private void confirm() {
		if (!canConfirm()) return;
		ClientPlayNetworking.send(new NetworkRegistry.ProsecuteActionPayload(selected.id(), 0));
		this.minecraft.setScreenAndShow(null);
	}

	/** 决策模式提交：action 0=受贿 / 1=进行检查（target=被检查者） */
	private void sendAction(int action) {
		if (selected == null) return;
		ClientPlayNetworking.send(new NetworkRegistry.ProsecuteActionPayload(selected.id(), action));
		this.minecraft.setScreenAndShow(null);
	}

	private int rowX() { return (this.width - PANEL_W) / 2; }

	private int rowY(int index) { return listTop + index * ROW_H - scrollOffset; }

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean active) {
		int mx = (int) event.x(), my = (int) event.y();
		if (state.pendingPhase()) return super.mouseClicked(event, active); // 决策/等待阶段：目标已定，不可更换
		if (mx >= rowX() && mx <= rowX() + PANEL_W && my >= listTop && my <= listTop + listHeight) {
			for (int i = 0; i < rows.size(); i++) {
				int y = rowY(i);
				if (my >= y && my <= y + ROW_H - 2) {
					BookRow row = rows.get(i);
					if (row.friend()) return true; // 友方不可选
					selected = selected == row ? null : row; // 再点一次取消选择
					if (confirmBtn != null) confirmBtn.active = selected != null;
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
		if (event.key() == 257 && canConfirm()) { confirm(); return true; } // Enter 确认
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
		g.fillGradient(0, 0, this.width, this.height, 0x8C0C1218, 0xB40C1218);

		// 标题条
		g.fill(left - 8, 6, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xE00E161C);
		g.fill(left - 8, HEADER_HEIGHT - 8, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xFF4A8ABF);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(this.width / 2.0f, 14.0f);
		pose.scale(1.3f, 1.3f);
		g.centeredText(font, ClientL10n.gui("prosecute.header"), 0, 0, 0xFFFFFFFF);
		pose.popMatrix();

		// 说明两行（按模式切换）
		String desc1, desc2;
		if (decisionPhase()) {
			desc1 = String.format(ClientL10n.tr("survival-contract.gui.prosecute.desc_decision1",
						"§e「%s」的贿赂袋价值：§l%d §r§e分"), rows.isEmpty() ? "?" : rows.get(0).name(), state.bribeTotal());
			desc2 = ClientL10n.tr("survival-contract.gui.prosecute.desc_decision2",
					"§7受贿=收下贿赂袋放行（其货物袋同步兑换）；检查=有走私品没收全部，无则赔货款 1/4");
		} else if (waitingPhase()) {
			desc1 = ClientL10n.tr("survival-contract.gui.prosecute.desc_wait1",
					"§7检查进行中：等待检察官定夺…");
			desc2 = ClientL10n.tr("survival-contract.gui.prosecute.desc_wait2",
					"§7受贿=交出贿赂袋放行，货物袋同步兑换成分；最长 2 分钟");
		} else {
			desc1 = ClientL10n.tr("survival-contract.gui.prosecute.desc_go1",
					"§7选择一名非友方玩家作为自己的检察官");
			desc2 = ClientL10n.tr("survival-contract.gui.prosecute.desc_go2",
					"§7双方 3 秒倒计时后进入检查室，由对方定夺（受贿 / 检查）");
		}
		g.centeredText(font, Component.literal(desc1), this.width / 2, HEADER_HEIGHT - 34, 0xFFC0C0C0);
		g.centeredText(font, Component.literal(desc2), this.width / 2, HEADER_HEIGHT - 24, 0xFFC0C0C0);

		// 状态行（决策模式显示 2 分钟时限；发起模式提示可发起）
		String statusLine = decisionPhase()
				? ClientL10n.tr("survival-contract.gui.prosecute.decision_window",
							"§e请在 §l2 分钟§e 内定夺，超时自动视为受贿")
				: waitingPhase()
				? ClientL10n.tr("survival-contract.gui.prosecute.wait_line", "§a检察官定夺后自动结算回归")
				: ClientL10n.tr("survival-contract.gui.prosecute.ready_line", "§a可以发起检查（无冷却时间）");
		g.centeredText(font, Component.literal(statusLine), this.width / 2, HEADER_HEIGHT - 12, 0xFFFFFFFF);

		// 玩家列表（滚动裁剪）
		g.enableScissor(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight);
		g.fill(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight, 0xC00C141C);
		if (rows.isEmpty()) {
			g.centeredText(font, Component.literal(ClientL10n.tr(
							"survival-contract.gui.prosecute.no_players", "§7当前没有其他玩家")),
					this.width / 2, listTop + 12, 0xFFFFFFFF);
		}
		for (int i = 0; i < rows.size(); i++) {
			int y = rowY(i);
			if (y + ROW_H < listTop || y > listTop + listHeight) continue;
			BookRow row = rows.get(i);
			boolean sel = selected == row;
			boolean hoverable = !row.friend();
			boolean hovered = hoverable && !sel && mouseX >= left + 4 && mouseX <= left + PANEL_W - 4
					&& mouseY >= y && mouseY <= y + ROW_H - 2;
			int bg = sel ? 0xFF2A4A5A : hovered ? 0xFF303C44
					: row.friend() ? 0xFF181C20 : 0xFF1C242C;
			g.fill(left + 4, y, left + PANEL_W - 4, y + ROW_H - 2, bg);
			int border = sel ? 0xFF70B0D0 : hovered ? 0xFF7888A0 : 0xFF445060;
			g.fill(left + 4, y, left + PANEL_W - 4, y + 1, border);
			g.fill(left + 4, y + ROW_H - 3, left + PANEL_W - 4, y + ROW_H - 2, border);
			String mark = sel ? "§b▶ " : row.friend() ? "§8" : "§f";
			String suffix = row.friend()
					? ClientL10n.tr("survival-contract.gui.prosecute.friend", " §7(友方)")
					: "";
			g.text(font, Component.literal(mark + row.name() + suffix), left + 12, y + 8,
					row.friend() ? 0xFF707870 : 0xFFFFFFFF, false);
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
		String hint = state.pendingPhase()
				? ClientL10n.tr("survival-contract.gui.prosecute.hint_pending",
						"§7检查室期间双方无法丢弃物品")
				: selected == null
				? ClientL10n.tr("survival-contract.gui.prosecute.hint_select",
						"§7点选一名非友方玩家作为你的检察官后确认")
				: String.format(ClientL10n.tr("survival-contract.gui.prosecute.hint_confirm",
				"§7已选「%s」——确认后 3 秒倒计时进入检查室"), selected.name());
		g.centeredText(font, Component.literal(hint), this.width / 2, this.height - 22, 0xFFC0C0C0);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
