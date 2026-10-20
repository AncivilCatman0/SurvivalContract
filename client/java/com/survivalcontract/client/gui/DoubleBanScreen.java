package com.survivalcontract.client.gui;

import com.survivalcontract.client.ClientState;
import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 双禁选择页：与挑战书规则页样式一致（同样的规则卡片），但不显示分数。
 * 内容 = 全部挑战规则 + 全部遭遇事件（分区显示）。
 * 里面是"给对面选的词条"——点击一条即强制对面全队接受（无分，违规照常扣分）。
 * 每队仅能选一条；我方已选后其余全部置灰，对面已选的同一条不可再选。
 * 状态来自 ClientState（服务端 sync 按接收者队伍换算的 myTeamBan / enemyBan）。
 */
public class DoubleBanScreen extends Screen {

    /** 列表行：header=true 为分区标题（不可点击）；遭遇事件 id 使用 "event:" 前缀 */
    private record BanRow(String id, String displayName, String description, String iconItemId, boolean header) {}

    private static final int HEADER_HEIGHT = 48;
    private static final int PANEL_W = 300;
    private static final int ROW_H = 30;
    private static final int ROW_GAP = 4;
    private static final int ICON_SIZE = 20;
    private static final int SCROLL_SPEED = 32;

    private final ChallengeBookScreen parent;
    private final List<BanRow> rows = new ArrayList<>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int listTop, listHeight;

    public DoubleBanScreen(ChallengeBookScreen parent) {
        super(com.survivalcontract.client.ClientL10n.gui("double_ban.title"));
        this.parent = parent;
        ClientState cs = ClientState.INSTANCE;

        // ===== 分区一：挑战规则（rule_* 玩法开关不参与双禁）=====
        rows.add(new BanRow("", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.section.rules_short", "挑战规则"), "", null, true));
        if (cs.challengeEntries.isEmpty()) {
            // sync 未到达时的本地兜底
            try {
                for (var c : com.survivalcontract.challenge.ChallengeManager.getInstance().getAllChallenges()) {
                    if (c.getId().startsWith("rule_")) continue;
                    rows.add(new BanRow(c.getId(), c.getDisplayName(), c.getDescription(), c.getIconItemId(), false));
                }
            } catch (Exception ignored) {}
        } else {
            for (var e : cs.challengeEntries) {
                if (e.challengeId().startsWith("rule_")) continue;
                // Entry 自带完整显示数据（纯客户端本地注册表为空，不能依赖 findLocal）
                rows.add(new BanRow(e.challengeId(),
                        com.survivalcontract.client.ClientL10n.challengeName(e.challengeId(), e.displayName()),
                        com.survivalcontract.client.ClientL10n.challengeDesc(e.challengeId(), e.description()),
                        e.iconItemId(), false));
            }
        }

        // ===== 分区二：遭遇事件（全事件可禁给对面：定向触发、无分数）=====
        rows.add(new BanRow("", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.section.events_short", "遭遇事件"), "", null, true));
        for (var ev : com.survivalcontract.event.RandomEventManager.EventType.values()) {
            rows.add(new BanRow("event:" + ev.name(),
                    com.survivalcontract.client.ClientL10n.eventName(ev.name(), ev.displayName),
                    com.survivalcontract.client.ClientL10n.guiText("double_ban.event_grade", ev.tier)
                            + com.survivalcontract.client.ClientL10n.eventDesc(ev.name(), ev.description),
                    ev.icon, false));
        }
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        listTop = HEADER_HEIGHT;
        listHeight = this.height - HEADER_HEIGHT - 56;

        int contentHeight = rows.size() * (ROW_H + ROW_GAP);
        maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
        parent.rebuildWidgets();
    }

    private int rowX() { return (this.width - PANEL_W) / 2; }

    private int rowY(int index) { return listTop + index * (ROW_H + ROW_GAP) - scrollOffset; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean active) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        String myBan = ClientState.INSTANCE.myTeamBan;

        if (mouseX >= rowX() && mouseX <= rowX() + PANEL_W
                && mouseY >= listTop && mouseY <= listTop + listHeight) {
            for (int i = 0; i < rows.size(); i++) {
                int y = rowY(i);
                if (y + ROW_H < listTop || y > listTop + listHeight) continue;
                if (mouseX >= rowX() + 4 && mouseX <= rowX() + PANEL_W - 4
                        && mouseY >= y && mouseY <= y + ROW_H) {
                    BanRow row = rows.get(i);
                    if (row.header()) continue;
                    String cid = row.id();
                    // 仅能选一个：我方已选或对面已选的同一条不可再点
                    if (!myBan.isEmpty() || cid.equals(ClientState.INSTANCE.enemyBan)) return true;
                    ClientPlayNetworking.send(new NetworkRegistry.DoubleBanPayload(cid));
                    onClose();
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
        if (event.key() == 264 && maxScroll > 0) { // Down
            scrollOffset = Math.min(maxScroll, scrollOffset + SCROLL_SPEED);
            return true;
        }
        if (event.key() == 265 && maxScroll > 0) { // Up
            scrollOffset = Math.max(0, scrollOffset - SCROLL_SPEED);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var font = this.font;
        int left = rowX();
        ClientState cs = ClientState.INSTANCE;

        // 半透明压暗背景（透出世界）
        graphics.fillGradient(0, 0, this.width, this.height, 0x8C0C0C12, 0xB40C0C12);

        // 标题条 + 金色分隔线
        graphics.fill(left - 8, 6, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xE0151518);
        graphics.fill(left - 8, HEADER_HEIGHT - 8, left + PANEL_W + 8, HEADER_HEIGHT - 6, 0xFFC8A028);

        // 标题（1.3 倍字号，居中）
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(this.width / 2.0f, 15.0f);
        pose.scale(1.3f, 1.3f);
        graphics.centeredText(font, com.survivalcontract.client.ClientL10n.gui("double_ban.header"), 0, 0, 0xFFFFFFFF);
        pose.popMatrix();

        // 提示行
        graphics.centeredText(font, com.survivalcontract.client.ClientL10n.gui("double_ban.hint"),
                this.width / 2, HEADER_HEIGHT - 18, 0xFFC0C0C0);

        // ===== 词条行（裁剪滚动，样式与挑战书规则卡片一致但没有分数） =====
        graphics.enableScissor(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight);

        // 面板底色
        graphics.fill(left - 8, listTop, left + PANEL_W + 8, listTop + listHeight, 0xC0101014);

        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y + ROW_H < listTop || y > listTop + listHeight) continue;
            drawRow(graphics, rows.get(i), left, y, mouseX, mouseY, cs);
        }
        graphics.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int barX = left + PANEL_W + 2;
            int thumbH = Math.max(20, listHeight * listHeight / (maxScroll + listHeight));
            int thumbY = listTop + scrollOffset * (listHeight - thumbH) / maxScroll;
            graphics.fill(barX, listTop, barX + 5, listTop + listHeight, 0x80555555);
            graphics.fill(barX, thumbY, barX + 5, thumbY + thumbH, 0xFFAAAAAA);
        }

        // ===== 底部状态条 =====
        int bottomY = listTop + listHeight + 6;
        graphics.fill(left - 8, bottomY, left + PANEL_W + 8, this.height - 10, 0xE0151518);
        String myText = cs.myTeamBan.isEmpty()
                ? com.survivalcontract.client.ClientL10n.guiText("double_ban.not_selected")
                : "§d" + displayNameOf(cs.myTeamBan);
        String enemyText = cs.enemyBan.isEmpty()
                ? com.survivalcontract.client.ClientL10n.guiText("double_ban.not_selected")
                : "§c" + displayNameOf(cs.enemyBan);
        graphics.text(font, com.survivalcontract.client.ClientL10n.gui("double_ban.my_banned", myText),
                left + 2, bottomY + 5, 0xFFFFFFFF, false);
        graphics.text(font, com.survivalcontract.client.ClientL10n.gui("double_ban.enemy_banned", enemyText),
                left + 2, bottomY + 17, 0xFFFFFFFF, false);
        // 返回
        graphics.centeredText(font, com.survivalcontract.client.ClientL10n.gui("esc_back"), this.width / 2, this.height - 20, 0xFF808080);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphicsExtractor g, BanRow row, int left, int y,
                         int mouseX, int mouseY, ClientState cs) {
        // 分区标题行
        if (row.header()) {
            g.fill(left + 4, y, left + PANEL_W - 4, y + ROW_H, 0x66C8A028);
            g.text(this.font, Component.literal("§6§l" + row.displayName()), left + 10, y + 9, 0xFFFFFFFF, false);
            return;
        }

        boolean isEvent = row.id().startsWith("event:");
        boolean isMine = row.id().equals(cs.myTeamBan);
        boolean isEnemy = row.id().equals(cs.enemyBan);
        boolean locked = !cs.myTeamBan.isEmpty() || isEnemy; // 我方已选 → 其余全部锁定
        boolean hovered = !locked && !isMine
                && mouseX >= left + 4 && mouseX <= left + PANEL_W - 4
                && mouseY >= y && mouseY <= y + ROW_H;

        int bg = isMine ? 0xFF1F4A2A : isEnemy ? 0xFF3A2020 : (hovered ? 0xFF404048 : 0xFF2A2A30);
        g.fill(left + 4, y, left + PANEL_W - 4, y + ROW_H, bg);

        int border = isMine ? 0xFF66BB6A : isEnemy ? 0xFFBB5555 : (hovered ? 0xFFAAAAAA : 0xFF555560);
        g.fill(left + 4, y, left + PANEL_W - 4, y + 1, border);
        g.fill(left + 4, y + ROW_H - 1, left + PANEL_W - 4, y + ROW_H, border);

        // 图标：规则=物品图标；遭遇事件=emoji 文字图标
        if (isEvent) {
            String icon = row.iconItemId() == null || row.iconItemId().isEmpty() ? "★" : row.iconItemId();
            g.text(this.font, Component.literal("§e" + icon), left + 11, y + 8, 0xFFFFFFFF, false);
        } else {
            var icon = resolveIcon(row.iconItemId());
            if (icon != null) {
                g.item(icon, left + 11, y + (ROW_H - ICON_SIZE) / 2, 0);
            } else {
                // 解析失败 → 灰色"?"占位，绝不用屏障方块
                g.centeredText(this.font, Component.literal("§7?"),
                        left + 11 + ICON_SIZE / 2, y + (ROW_H - 8) / 2, 0xFFFFFFFF);
            }
        }

        // 名称（无分数行）；显示名缺失时回退到 id，绝不留空
        int textX = left + 11 + ICON_SIZE + 8;
        String nameColor = isMine ? "§a" : isEnemy ? "§7" : locked ? "§8" : "§f";
        String rawName = row.displayName() == null || row.displayName().isEmpty()
                ? row.id() : row.displayName();
        String displayName = shorten((isEvent
                        ? com.survivalcontract.client.ClientL10n.guiText("double_ban.event_prefix") : "") + rawName,
                PANEL_W - ICON_SIZE - 110, this.font);
        g.text(this.font, Component.literal(nameColor + displayName), textX, y + 9, 0xFFFFFFFF, false);

        // 右侧状态标记
        String tag;
        if (isMine) tag = com.survivalcontract.client.ClientL10n.guiText("double_ban.tag_mine");
        else if (isEnemy) tag = com.survivalcontract.client.ClientL10n.guiText("double_ban.tag_enemy");
        else if (locked) tag = com.survivalcontract.client.ClientL10n.guiText("double_ban.tag_locked");
        else tag = com.survivalcontract.client.ClientL10n.guiText("double_ban.tag_select");
        g.text(this.font, Component.literal(tag), left + PANEL_W - 86, y + 9, 0xFFFFFFFF, false);

        // 悬停 tooltip：完整描述（统一折行宽度）
        if (hovered && !row.description().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§e" + row.displayName()));
            for (Component l : ChallengeBookScreen.wrapText(this.font, row.description(),
                    ChallengeBookScreen.TOOLTIP_WRAP_WIDTH)) {
                lines.add(Component.literal("§7" + l.getString()));
            }
            g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
        }
    }

    private String displayNameOf(String id) {
        for (BanRow r : rows) if (r.id().equals(id)) return r.displayName();
        return id;
    }

    /** 解析物品图标；失败/缺失返回 null（渲染侧画灰色"?"占位，绝不用屏障方块） */
    private ItemStack resolveIcon(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            Identifier ident = Identifier.fromNamespaceAndPath(
                    id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft",
                    id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(ident);
            return item != null && item != Items.AIR ? new ItemStack(item) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String shorten(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        if (font.width(text) <= maxWidth) return text;
        while (font.width(text + "…") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
