package com.survivalcontract.client.gui;

import com.survivalcontract.network.NetworkRegistry;
import com.survivalcontract.profession.ProfessionManager.Profession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 职业选择二级菜单。
 * 视觉：半透明压暗背景 + 居中面板（边框/标题条/金色强调），标题 1.4 倍字号，
 * 与挑战书配色一致（深灰 0xFF202020 / 分隔 0xFF555555 / 金色标题）。
 * 每个职业一行按钮，鼠标悬停显示具体效果（职业名 + 描述）。
 */
public class ProfessionSelectScreen extends Screen {

    private final ChallengeBookScreen parent;
    private final Map<Button, Profession> buttonProfessions = new HashMap<>();

    // ===== 面板布局（init 时计算）=====
    private static final int PANEL_W = 244;
    private static final int HEADER_H = 30;
    private static final int HINT_H = 16;
    private static final int BTN_H = 22;
    private static final int BTN_GAP = 5;
    private static final int BACK_H = 20;

    private int panelX, panelY, panelH;
    /** 职业按钮高度（行数多时自适应压缩，见 init） */
    private int btnH = BTN_H;

    public ProfessionSelectScreen(ChallengeBookScreen parent) {
        super(com.survivalcontract.client.ClientL10n.gui("select_profession_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        buttonProfessions.clear();

        Profession[] all = Profession.values();
        // 文明模式禁用愚者（第五十二批）：客户端同步隐藏按钮（服务端亦强制重置为无职业）
        if ("CIVILIZATION".equalsIgnoreCase(com.survivalcontract.client.ClientState.INSTANCE.playStyleKey)) {
            List<Profession> enabled = new ArrayList<>();
            for (Profession p : all) if (p != Profession.JESTER) enabled.add(p);
            all = enabled.toArray(new Profession[0]);
        }
        // 双列布局：职业数量增多（新增盗贼 → 11 项 6 行）时按钮高度自适应压缩，保证面板与返回按钮不出屏
        int cols = 2;
        int rows = Math.ceilDiv(all.length, cols);
        int btnW = (PANEL_W - 24 - 8) / cols;
        int availListH = Math.max(rows * 16,
                this.height - 8 - (HEADER_H + HINT_H + 6 + 8 + BACK_H + 10));
        btnH = Math.max(16, Math.min(BTN_H, (availListH - (rows - 1) * BTN_GAP) / rows));
        int listH = rows * btnH + (rows - 1) * BTN_GAP;
        panelH = HEADER_H + HINT_H + 6 + listH + 8 + BACK_H + 10;
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);

        int y = panelY + HEADER_H + HINT_H + 6;

        for (int i = 0; i < all.length; i++) {
            Profession p = all[i];
            int col = i % cols;
            int row = i / cols;
            int btnX = panelX + 12 + col * (btnW + 8);
            int btnY = y + row * (btnH + BTN_GAP);
            boolean selected = p.name().equals(parent.getProfessionKey());
            String label = (selected ? "§a✔ " : "§f")
                    + com.survivalcontract.client.ClientL10n.professionName(p.name(), p.chineseName);
            Button btn = Button.builder(Component.literal(label), b -> {
                try {
                    ClientPlayNetworking.send(new NetworkRegistry.ProfessionSelectPayload(p.name()));
                    parent.setProfessionKey(p.name());
                } catch (Exception e) {
                    com.survivalcontract.SurvivalContract.LOGGER.error("profession select failed", e);
                }
                onClose();
            }).bounds(btnX, btnY, btnW, BTN_H).build();
            buttonProfessions.put(btn, p);
            addRenderableWidget(btn);
        }

        // 返回按钮（面板底部）
        addRenderableWidget(Button.builder(
                com.survivalcontract.client.ClientL10n.gui("back_btn"),
                b -> onClose()
        ).bounds(panelX + 12, panelY + panelH - BACK_H - 8, PANEL_W - 24, BACK_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // === 背景：半透明压暗（不再是整屏实心黑块，透出世界）===
        graphics.fill(0, 0, this.width, this.height, 0x8C0C0C12);

        // === 居中面板 ===
        // 面板主体
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xF01A1A20);
        // 面板边框（外深内亮，立体感）
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF5A5A5A);           // 上
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF3A3A3A); // 下
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF4A4A4A);           // 左
        graphics.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF3A3A3A); // 右

        // === 标题条 ===
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + HEADER_H, 0xFF202020);
        // 标题条底部分隔线（金色强调）
        graphics.fill(panelX + 1, panelY + HEADER_H - 1, panelX + PANEL_W - 1, panelY + HEADER_H, 0xFF8A6D1B);

        // 标题（1.4 倍字号，居中于标题条）
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(panelX + PANEL_W / 2.0f, panelY + 8.0f);
        pose.scale(1.4f, 1.4f);
        graphics.centeredText(this.font, com.survivalcontract.client.ClientL10n.gui("select_profession_title"), 0, 0, 0xFFFFFFFF);
        pose.popMatrix();

        // 提示行
        String hint = com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.select_profession_hint", "§7鼠标悬停查看职业效果，点击选择");
        int hintW = this.font.width(hint);
        graphics.text(this.font, Component.literal(hint),
                panelX + (PANEL_W - hintW) / 2, panelY + HEADER_H, 0xFFA0A0A0, false);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 悬停 tooltip：职业效果说明（在 widgets 渲染后设置，保证在最上层）
        for (var entry : buttonProfessions.entrySet()) {
            Button btn = entry.getKey();
            if (btn.isMouseOver(mouseX, mouseY)) {
                Profession p = entry.getValue();
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal("§e" + com.survivalcontract.client.ClientL10n.professionName(p.name(), p.chineseName)));
                String desc = com.survivalcontract.client.ClientL10n.professionDesc(p.name(), p.description);
                // description 含 \n（会渲染成无法识别的方块字符）——先按行拆分再折行
                for (String rawLine : desc.split("\n")) {
                    for (Component line : wrapText(rawLine, 170)) {
                        lines.add(Component.literal("§7" + line.getString()));
                    }
                }
                graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
                break;
            }
        }
    }

    /** 按像素宽度把长文本折行。 */
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
    public void onClose() {
        if (this.minecraft != null) {
            // 回到挑战书界面（重建以刷新职业按钮文案）
            parent.rebuildWidgets();
            this.minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
