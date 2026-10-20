package com.survivalcontract.client.gui;

import com.survivalcontract.client.ClientState;
import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文明时代：自定义地形选择二级菜单（第四十九批，替代原「双禁」按钮入口）。
 * 视觉与职业选择二级菜单一致（半透明压暗背景 + 居中面板 + 金色标题条）。
 * 可选：预设-主世界（默认）/ 预设-挖掘工的梦想 / 预设-雪之王国 / 预设-沙漠；
 * 世界规则通用（仅切换地形生成，游戏规则/边界/结构禁用等全部不变）。
 */
public class TerrainPresetScreen extends Screen {

    private record PresetRow(String key, String labelKey, String descKey) {}

    private static final PresetRow[] PRESETS = {
            new PresetRow("OVERWORLD", "terrain.preset_overworld", "terrain.preset_overworld_desc"),
            new PresetRow("MINER", "terrain.preset_miner", "terrain.preset_miner_desc"),
            new PresetRow("SNOW", "terrain.preset_snow", "terrain.preset_snow_desc"),
            new PresetRow("DESERT", "terrain.preset_desert", "terrain.preset_desert_desc")
    };

    private final ChallengeBookScreen parent;
    private final Map<Button, PresetRow> buttonPresets = new HashMap<>();

    // ===== 面板布局（init 时计算）=====
    private static final int PANEL_W = 260;
    private static final int HEADER_H = 30;
    private static final int HINT_H = 16;
    private static final int BTN_H = 22;
    private static final int BTN_GAP = 5;
    private static final int BACK_H = 20;

    private int panelX, panelY, panelH;

    public TerrainPresetScreen(ChallengeBookScreen parent) {
        super(com.survivalcontract.client.ClientL10n.gui("terrain.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        buttonPresets.clear();
        int listH = PRESETS.length * BTN_H + (PRESETS.length - 1) * BTN_GAP;
        panelH = HEADER_H + HINT_H + 6 + listH + 8 + BACK_H + 10;
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);

        int y = panelY + HEADER_H + HINT_H + 6;
        String current = ClientState.INSTANCE.terrainPreset == null
                ? "OVERWORLD" : ClientState.INSTANCE.terrainPreset.toUpperCase(Locale.ROOT);

        for (int i = 0; i < PRESETS.length; i++) {
            PresetRow row = PRESETS[i];
            int btnY = y + i * (BTN_H + BTN_GAP);
            boolean selected = row.key().equals(current);
            String label = (selected ? "§a✔ " : "§f")
                    + com.survivalcontract.client.ClientL10n.guiText(row.labelKey());
            Button btn = Button.builder(Component.literal(label), b -> {
                ClientPlayNetworking.send(new NetworkRegistry.SetTerrainPresetPayload(row.key()));
                ClientState.INSTANCE.terrainPreset = row.key();
                onClose();
            }).bounds(panelX + 12, btnY, PANEL_W - 24, BTN_H).build();
            buttonPresets.put(btn, row);
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
        // === 背景：半透明压暗（透出世界）===
        graphics.fill(0, 0, this.width, this.height, 0x8C0C0C12);

        // === 居中面板 ===
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xF01A1A20);
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFF5A5A5A);
        graphics.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF3A3A3A);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFF4A4A4A);
        graphics.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + panelH, 0xFF3A3A3A);

        // === 标题条 ===
        graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + HEADER_H, 0xFF202020);
        graphics.fill(panelX + 1, panelY + HEADER_H - 1, panelX + PANEL_W - 1, panelY + HEADER_H, 0xFF8A6D1B);

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(panelX + PANEL_W / 2.0f, panelY + 8.0f);
        pose.scale(1.4f, 1.4f);
        graphics.centeredText(this.font, com.survivalcontract.client.ClientL10n.gui("terrain.header"), 0, 0, 0xFFFFFFFF);
        pose.popMatrix();

        // 提示行
        graphics.centeredText(this.font, com.survivalcontract.client.ClientL10n.gui("terrain.hint"),
                panelX + PANEL_W / 2, panelY + HEADER_H + 5, 0xFFC0C0C0);

        // 悬停 tooltip：预设说明
        for (var e : buttonPresets.entrySet()) {
            if (e.getKey().isHovered()) {
                PresetRow row = e.getValue();
                graphics.setComponentTooltipForNextFrame(this.font,
                        java.util.List.of(Component.literal("§e"
                                        + com.survivalcontract.client.ClientL10n.guiText(row.labelKey())),
                                Component.literal("§7"
                                        + com.survivalcontract.client.ClientL10n.guiText(row.descKey()))),
                        mouseX, mouseY);
            }
        }

        // 返回
        graphics.centeredText(this.font, com.survivalcontract.client.ClientL10n.gui("esc_back"),
                panelX + PANEL_W / 2, panelY + panelH - 4, 0xFF808080);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
        parent.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
