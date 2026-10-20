package com.survivalcontract.client.gui;

import com.survivalcontract.network.NetworkRegistry;
import com.survivalcontract.profession.ProfessionManager;
import com.survivalcontract.profession.ProfessionManager.Profession;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * 挑战书 GUI。
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │  生存契约 · 挑战书                     初始分数: 42  │ ← 标题栏
 * ├──────────────────────────────────────────────────────┤
 * │  [挑战卡片...]（紧凑卡片，滚轮滚动，裁剪渲染）        │
 * │  —— 遭遇事件（游戏期间随机触发）——                   │
 * │  [事件行...]（图标+名称+描述，不可点击）              │
 * ├──────────────────────────────────────────────────────┤
 * │  [职业▼] [模式▼] [目标▼]            [▶ 开始游戏]    │ ← 控制栏
 * └──────────────────────────────────────────────────────┘
 * </pre>
 * 注意：所有 text() 颜色必须带 0xFF alpha（ARGB），否则整段文字不可见！
 */
public class ChallengeBookScreen extends Screen {

    // ===== 卡片数据 =====
    private record ChallengeCard(String id, String displayName, String description,
                                 int baseScore, int penaltyScore,
                                 boolean customizable, int customValue, String iconItemId,
                                 String section) {}

    private final List<ChallengeCard> cards = new ArrayList<>();

    /** 客户端缓存的服务端同步状态。 */
    private final Set<String> enabled = new HashSet<>();
    private final Map<String, Integer> customValues = new HashMap<>();
    /** 客户端缓存的已启用遭遇事件（同步自服务端）。 */
    private final Set<String> enabledEvents = new HashSet<>();

    // 底部控制栏状态
    private String gameModeKey = "solo";
    private String finalGoalKey = "ENDER_DRAGON";
    private String professionKey = "CHEF";

    private int initialScore = 0;

    // 滚动偏移
    private int scrollOffset = 0;

    // ===== 布局常量 =====
    private static final int HEADER_HEIGHT = 44;
    private static final int FOOTER_HEIGHT = 76;   // 两行按钮：职业/模式/目标 + 3 个规则开关
    private static final int CARD_MAX_WIDTH = 150; // 卡片横向长度缩短（原 175），三列排版封顶
    private static final int CARD_HEIGHT = 40;     // 紧凑卡片高度
    private static final int CARD_GAP = 8;
    private static final int CARD_ICON_SIZE = 20;
    private static final int SCROLL_SPEED = 40;

    // 遭遇事件区（信息展示，不可点击）
    private static final int EVENT_HEADER_H = 20;
    private static final int EVENT_ROW_H = 26;
    private static final int EVENT_ICON_SIZE = 16;

    // 卡片网格区域（init 时计算）
    private int gridLeft, gridTop, gridWidth, gridHeight, columns;
    private int cardW, cardsStartX; // 卡片实际宽度（自适应封顶）与三列网格起始 X（整体居中）
    // 底部控制栏按钮集合（非大厅阶段统一置黑禁用）
    private final java.util.List<Button> footerButtons = new java.util.ArrayList<>();
    private int contentHeight = 0;
    private int maxScroll = 0;

    // ===== 板块化布局（10-06）：与神合约/久病成患/环境恶化/遭遇事件，可折叠 =====
    private static final int SECTION_HEADER_H = 22;
    private final java.util.Set<String> collapsedSections = new HashSet<>();

    /** 布局行：header=板块标题行（可点击折叠）；否则为卡片行（sectionId≠events）或事件行（=events） */
    private record BookRow(boolean header, String sectionId, String title, int x, int y, int index) {}

    /** 生成整本书的布局（含折叠状态），渲染与点击命中共用同一套坐标 */
    /** 神的谎言（stage 1 规则菜单）：所有规则（含奇遇事件）隐藏且不可自选 */
    private boolean godsLiesStage1 = false;

    /** 附属玩法：无自选规则 → 规则/事件整块隐藏（渲染占位见 renderCardContent） */
    private boolean addonHideRules = false;

    private List<BookRow> buildBookLayout() {
        List<BookRow> rows = new ArrayList<>();
        if (godsLiesStage1 || addonHideRules) return rows; // 神的谎言/附属玩法：规则/事件全部隐藏（渲染占位见 renderCardContent）
        boolean civilization = "CIVILIZATION".equalsIgnoreCase(
                com.survivalcontract.client.ClientState.INSTANCE.playStyleKey);
        int y = gridTop;
        // 文明时代：仅环境恶化和遭遇事件可选择（常规/疾病规则隐藏）
        String[][] secs = civilization
                ? new String[][]{{"environment", com.survivalcontract.client.ClientL10n.tr(
                        "survival-contract.gui.section.environment", "§a✦ 环境恶化 §7(全员共享)")}}
                : new String[][]{{"contract", com.survivalcontract.client.ClientL10n.tr(
                        "survival-contract.gui.section.contract", "§b✦ 与神合约 §7(常规规则)")},
                {"disease", com.survivalcontract.client.ClientL10n.tr(
                        "survival-contract.gui.section.disease", "§d✦ 久病成患 §7(疾病规则)")},
                {"environment", com.survivalcontract.client.ClientL10n.tr(
                        "survival-contract.gui.section.environment", "§a✦ 环境恶化 §7(全员共享)")}};
        for (String[] sec : secs) {
            rows.add(new BookRow(true, sec[0], sec[1], gridLeft, y, -1));
            y += SECTION_HEADER_H;
            if (!collapsedSections.contains(sec[0])) {
                List<Integer> idxs = new ArrayList<>();
                for (int i = 0; i < cards.size(); i++) {
                    if (sec[0].equals(cards.get(i).section())) idxs.add(i);
                }
                int n = 0;
                for (int idx : idxs) {
                    int col = n % columns;
                    int row = n / columns;
                    rows.add(new BookRow(false, sec[0], null,
                            cardsStartX + col * (cardW + CARD_GAP), y + row * (CARD_HEIGHT + CARD_GAP), idx));
                    n++;
                }
                y += Math.ceilDiv(idxs.size(), columns) * (CARD_HEIGHT + CARD_GAP);
            }
        }
        // 遭遇事件板块
        rows.add(new BookRow(true, "events", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.section.events", "§6✦ 遭遇事件 §7(点击行勾选，开启即加分，游戏期间触发)"), gridLeft, y, -1));
        y += SECTION_HEADER_H;
        if (!collapsedSections.contains("events")) {
            com.survivalcontract.event.RandomEventManager.EventType[] evs =
                    com.survivalcontract.event.RandomEventManager.EventType.values();
            for (int i = 0; i < evs.length; i++) {
                rows.add(new BookRow(false, "events", null, gridLeft, y, i));
                y += EVENT_ROW_H + 2;
            }
        }
        return rows;
    }

    /** 计算完整展开时的内容总高（maxScroll 用，折叠状态不影响滚动上限的最大值） */
    private int computeContentHeight() {
        int y = gridTop;
        for (int i = 0; i < 3; i++) {
            y += SECTION_HEADER_H;
            List<Integer> idxs = new ArrayList<>();
            String sec = i == 0 ? "contract" : i == 1 ? "disease" : "environment";
            for (BookRow r : probeRows(sec)) idxs.add(r.index());
            y += Math.ceilDiv(idxs.size(), columns) * (CARD_HEIGHT + CARD_GAP);
        }
        y += SECTION_HEADER_H;
        y += com.survivalcontract.event.RandomEventManager.EventType.values().length * (EVENT_ROW_H + 2);
        return y - gridTop;
    }

    /** 临时生成某板块的卡片行（仅取 index 数量） */
    private List<BookRow> probeRows(String sec) {
        List<BookRow> out = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            if (sec.equals(cards.get(i).section())) out.add(new BookRow(false, sec, null, 0, 0, i));
        }
        return out;
    }

    // ===== 构造 =====
    public ChallengeBookScreen() {
        super(com.survivalcontract.client.ClientL10n.gui("title"));
    }

    /**
     * 由 S2C 包更新客户端状态。
     * 只合并 enabled/自定义值/职业/模式/目标/分数 —— 卡片的展示数据保留本地版本。
     * @deprecated 状态现已统一持久化到 {@link com.survivalcontract.client.ClientState}，
     *             sync 后直接 {@link #rebuildWidgets()} 即可（init 会从 ClientState 恢复）。
     */
    @Deprecated(forRemoval = false)
    public void applySync(NetworkRegistry.ChallengeSyncPayload payload) {
        this.enabled.clear();
        this.customValues.clear();
        for (NetworkRegistry.ChallengeSyncPayload.Entry e : payload.entries()) {
            if (e.enabled()) enabled.add(e.challengeId());
            customValues.put(e.challengeId(), e.customValue());
        }

        this.enabledEvents.clear();
        this.enabledEvents.addAll(payload.enabledEvents());

        for (NetworkRegistry.ChallengeSyncPayload.Entry e : payload.entries()) {
            if (e.challengeId().startsWith("rule_")) continue; // 规则开关改用底部按钮，不进网格
            boolean known = cards.stream().anyMatch(c -> c.id().equals(e.challengeId()));
            if (!known) {
                cards.add(new ChallengeCard(e.challengeId(),
                        com.survivalcontract.client.ClientL10n.challengeName(e.challengeId(), e.displayName()),
                        com.survivalcontract.client.ClientL10n.challengeDesc(e.challengeId(), e.description()),
                        e.baseScore(), e.penaltyScore(), e.customizable(), e.customValue(), e.iconItemId(),
                        e.section() == null ? "contract" : e.section()));
            }
        }

        this.professionKey = payload.professionKey();
        this.initialScore = payload.initialScore();
        this.gameModeKey = payload.currentGameMode();
        this.finalGoalKey = payload.finalGoalKey().toUpperCase();
    }

    /** 公开给外部（Opener）调用的刷新方法，重建 widgets 并重新计算布局。 */
    public void rebuildWidgets() {
        clearWidgets();
        init();
    }

    /** 直接加载本地 ChallengeManager 数据（单机/集成服务器场景）。 */
    public void loadFromLocal() {
        var cm = com.survivalcontract.challenge.ChallengeManager.getInstance();
        Collection<com.survivalcontract.challenge.Challenge> all;
        try {
            all = cm.getAllChallenges();
        } catch (Exception e) {
            return; // 专用客户端上没有本地数据
        }
        this.cards.clear();
        for (var c : all) {
            if (c.getId().startsWith("rule_")) continue; // 规则开关改用底部按钮，不进网格
            cards.add(new ChallengeCard(c.getId(), c.getDisplayName(), c.getDescription(),
                    c.getBaseScore(), c.getPenaltyScore(),
                    c.isCustomizable(), c.getCustomValue(), c.getIconItemId(), c.getSection()));
        }
    }

    /**
     * 从 ClientState（全局持久缓存）恢复选择状态。
     * ClientState 由 sync 包持续更新（即使 GUI 未打开），因此重开 GUI 时
     * 点亮的挑战/规则/事件/职业/模式/目标不会丢失。
     */
    private void restoreFromClientState() {
        com.survivalcontract.client.ClientState cs = com.survivalcontract.client.ClientState.INSTANCE;
        enabled.clear();
        customValues.clear();
        for (var e : cs.challengeEntries) {
            if (e.enabled()) enabled.add(e.challengeId());
            customValues.put(e.challengeId(), e.customValue());
        }
        // 卡片数据一律以服务端 sync 清单为准（Entry 自带完整显示数据）。
        // 纯客户端的本地 ChallengeManager 注册表从未被填充（registerAllChallenges 仅在
        // 服务端 onServerStart 执行）—— 只靠 loadFromLocal 会导致其他玩家的书里没有规则卡片。
        if (!cs.challengeEntries.isEmpty()) {
            this.cards.clear();
            for (var e : cs.challengeEntries) {
                if (e.challengeId().startsWith("rule_")) continue; // 规则开关改用底部按钮，不进网格
                cards.add(new ChallengeCard(e.challengeId(),
                        com.survivalcontract.client.ClientL10n.challengeName(e.challengeId(), e.displayName()),
                        com.survivalcontract.client.ClientL10n.challengeDesc(e.challengeId(), e.description()),
                        e.baseScore(), e.penaltyScore(), e.customizable(), e.customValue(), e.iconItemId(),
                        e.section() == null ? "contract" : e.section()));
            }
        }
        enabledEvents.clear();
        enabledEvents.addAll(cs.enabledEvents);
        gameModeKey = cs.gameModeKey;
        finalGoalKey = cs.finalGoalKey;
        professionKey = cs.professionKey;
        initialScore = cs.initialScore;
    }

    // ===== 职业状态（供二级菜单回写）=====
    public String getProfessionKey() { return professionKey; }
    public void setProfessionKey(String key) {
        this.professionKey = key;
        com.survivalcontract.client.ClientState.INSTANCE.professionKey = key; // 全局持久（重开 GUI 不丢）
    }

    @Override
    protected void init() {
        super.init();

        if (cards.isEmpty()) {
            loadFromLocal();
        }

        // 从 ClientState 恢复持久状态（修复：重开 GUI 后点亮的规则全部熄灭）
        restoreFromClientState();

        var cs = com.survivalcontract.client.ClientState.INSTANCE;
        // === 大厅初始菜单（stage 0）：模式选择 + 玩家列表 + "我决定好了" ===
        if ("LOBBY".equalsIgnoreCase(cs.currentPhase) && cs.lobbyStage == 0) {
            godsLiesStage1 = false;
            addonHideRules = false;
            buildLobbyInitialMenu();
            return;
        }
        // === 神的谎言：规则全部隐藏且不可自选 ===
        // stage 1 规则菜单隐藏规则；PLAYING 阶段同样隐藏——开局后玩家也无法查看系统分配给自己的规则
        godsLiesStage1 = "GODS_LIES".equalsIgnoreCase(cs.playStyleKey)
                && (("LOBBY".equalsIgnoreCase(cs.currentPhase) && cs.lobbyStage >= 1)
                    || "PLAYING".equalsIgnoreCase(cs.currentPhase));
        boolean godsLies = "GODS_LIES".equalsIgnoreCase(cs.playStyleKey);
        boolean stowaway = "STOWAWAY".equalsIgnoreCase(cs.playStyleKey);
        boolean civilization = "CIVILIZATION".equalsIgnoreCase(cs.playStyleKey);
        // 附属玩法（注册表命中 playStyleKey）：整块抑制 / 锁定 / 无最终目标
        var addonProvider = com.survivalcontract.api.PlayStyleRegistry.byId(cs.playStyleKey);
        boolean addonStyle = addonProvider != null;
        com.survivalcontract.api.StyleUi addonUi = addonStyle ? addonProvider.ui()
                : com.survivalcontract.api.StyleUi.NONE;
        addonHideRules = addonStyle;
        boolean noGoalStyle = godsLies || stowaway || civilization || (addonStyle && addonUi.noFinalGoal());
        boolean lockProfession = stowaway || (addonStyle && addonUi.lockProfession());
        boolean disableTeamChest = stowaway || (addonStyle && addonUi.disableTeamChest());

        // === 计算卡片网格布局 ===
        int padding = 16;
        gridLeft = padding;
        gridTop = HEADER_HEIGHT;
        gridWidth = this.width - padding * 2;
        gridHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - padding;
        // 固定三列排版，卡片宽度自适应窗口并封顶（比原 175 缩短），整体居中
        columns = 3;
        cardW = Math.max(96, Math.min(CARD_MAX_WIDTH, (gridWidth - 2 * CARD_GAP) / columns));
        int blockW = columns * cardW + (columns - 1) * CARD_GAP;
        cardsStartX = gridLeft + Math.max(0, (gridWidth - blockW) / 2);

        // 板块化布局：与神合约/久病成患/环境恶化/遭遇事件 四个可折叠板块
        contentHeight = computeContentHeight();
        maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // === 底部控制栏（动态布局，避免按钮重叠）===
        footerButtons.clear(); // rebuild 时清理旧引用
        int footerY = this.height - FOOTER_HEIGHT + 12;

        int startBtnW = 130;
        int startBtnX = this.width - startBtnW - padding;
        int leftZoneRight = startBtnX - 8;
        int leftZoneLeft = padding;
        // 4 列布局（第一行：职业/模式/目标/时长；第二行：3 个规则开关），避免与开始按钮重叠
        int leftBtnW = (leftZoneRight - leftZoneLeft - 3 * 8) / 4;

        // 职业选择按钮 → 打开二级菜单
        // 走私犯与检察官 / 附属玩法（ui.lockProfession）：锁定无职业 → 按钮置灰不可点
        int btnX = leftZoneLeft;
        if (lockProfession) {
            Button lockedProf = Button.builder(Component.literal(com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.stowaway.profession_locked", "§8无职业（已锁定）")),
                    btn -> {}).bounds(btnX, footerY, leftBtnW, 20).build();
            lockedProf.active = false;
            addFooterButton(lockedProf);
        } else {
        addFooterButton(Button.builder(
                Component.literal(professionDisplayName()),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new ProfessionSelectScreen(this));
                    }
                }).bounds(btnX, footerY, leftBtnW, 20).build());
        }

        // 团队箱开关（原"游戏模式"按钮位）：与 PVP 同款启闭样式，全局设置（任意玩家切换全员同步）
        // 神的谎言：此位置替换为「使者数量」（1~4 循环）
        // 走私犯与检察官 / 附属玩法（ui.disableTeamChest）：团队箱强制关闭 → 按钮置灰不可点
        btnX += leftBtnW + 8;
        if (godsLies) {
            addFooterButton(Button.builder(
                    Component.literal(envoyCountLabel()),
                    btn -> {
                        int next = com.survivalcontract.client.ClientState.INSTANCE.envoyCount % 4 + 1;
                        com.survivalcontract.client.ClientState.INSTANCE.envoyCount = next;
                        ClientPlayNetworking.send(new NetworkRegistry.SetEnvoyCountPayload(next));
                        btn.setMessage(Component.literal(envoyCountLabel()));
                    }).bounds(btnX, footerY, leftBtnW, 20).build());
        } else if (disableTeamChest) {
            Button lockedChest = Button.builder(Component.literal(com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.stowaway.chest_locked", "§8团队箱（已关闭）")),
                    btn -> {}).bounds(btnX, footerY, leftBtnW, 20).build();
            lockedChest.active = false;
            addFooterButton(lockedChest);
        } else {
        addFooterButton(Button.builder(
                Component.literal(ruleButtonLabel("团队箱", "rule_team_chest")),
                btn -> {
                    boolean newState = !enabled.contains("rule_team_chest");
                    if (newState) enabled.add("rule_team_chest"); else enabled.remove("rule_team_chest");
                    ClientPlayNetworking.send(new NetworkRegistry.ChallengeTogglePayload("rule_team_chest", newState));
                    btn.setMessage(Component.literal(ruleButtonLabel("团队箱", "rule_team_chest")));
                }).bounds(btnX, footerY, leftBtnW, 20).build());
        }

        // 最终目标（无最终目标玩法：按钮置为不可点的占位）
        // 附属玩法且注册了 2 个以上选项：该位替换为第 2 个选项（如地狱厨房的「地图类型」）
        btnX += leftBtnW + 8;
        if (addonStyle && addonProvider.options().size() >= 2) {
            buildStyleOptionButton(addonProvider.options().get(1), addonProvider, btnX, footerY, leftBtnW);
        } else if (noGoalStyle) {
            Button noGoal = Button.builder(Component.literal(com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.godslies.no_goal_btn", "§8无最终目标")),
                    btn -> {}).bounds(btnX, footerY, leftBtnW, 20).build();
            noGoal.active = false;
            addFooterButton(noGoal);
        } else {
        addFooterButton(Button.builder(
                Component.literal(finalGoalDisplayName()),
                btn -> {
                    try {
                        String[] goals = {"ENDER_DRAGON", "ENDER_END", "WITHER", "RANDOM"};
                        int idx = Arrays.asList(goals).indexOf(finalGoalKey);
                        if (idx < 0) idx = 0;
                        finalGoalKey = goals[(idx + 1) % goals.length];
                        ClientPlayNetworking.send(new NetworkRegistry.SetFinalGoalPayload(finalGoalKey));
                        btn.setMessage(Component.literal(finalGoalDisplayName()));
                    } catch (Exception e) {
                        com.survivalcontract.SurvivalContract.LOGGER.error("goal button failed", e);
                    }
                }).bounds(btnX, footerY, leftBtnW, 20).build());
        }

        // === 第二行：三个游戏规则开关（PVP / 夜视 / 保留物品栏），位于职业/模式/目标正下方 ===
        // 神的谎言：这三个全局开关同样保留（gamerule 类全局状态，开局照常应用），仅双禁隐藏
        int ruleBtnY = footerY + 26;
        String[][] ruleDefs = {
                {"rule_pvp", "PVP"},
                {"rule_night_vision", "夜视"},
                {"rule_keep_inventory", "保留物品"}
        };
        btnX = leftZoneLeft;
        for (String[] def : ruleDefs) {
            String ruleId = def[0];
            // 文明时代：PVP 强制开启（锁定不可关）
            if (civilization && "rule_pvp".equals(ruleId)) {
                Button lockedPvp = Button.builder(Component.literal(com.survivalcontract.client.ClientL10n.tr(
                                "survival-contract.gui.civilization.pvp_locked", "§aPVP: §l已锁定开启")),
                        btn -> {}).bounds(btnX, ruleBtnY, leftBtnW, 20).build();
                lockedPvp.active = false;
                addFooterButton(lockedPvp);
                btnX += leftBtnW + 8;
                continue;
            }
            addFooterButton(Button.builder(
                    Component.literal(ruleButtonLabel(def[1], ruleId)),
                    btn -> {
                        boolean newState = !enabled.contains(ruleId);
                        if (newState) enabled.add(ruleId); else enabled.remove(ruleId);
                        ClientPlayNetworking.send(new NetworkRegistry.ChallengeTogglePayload(ruleId, newState));
                        btn.setMessage(Component.literal(ruleButtonLabel(def[1], ruleId)));
                    }).bounds(btnX, ruleBtnY, leftBtnW, 20).build());
            btnX += leftBtnW + 8;
        }

        // 第二行第 4 位按钮（神的谎言下不显示）：
        // 附属玩法 = 注册表第 1 个整数选项（循环切换，替代原双禁入口，如「需要完成的套餐组数」）；
        // 文明时代 = 自定义地形（第四十九批，替代原双禁入口，打开地形预设二级菜单）；
        // 其他玩法 = 双禁（仅团队模式可点）
        // 注意：服务端 sync 的 gameModeKey 是小写（"solo"/"teams"），必须用小写比较
        if (!godsLies) {
        if (addonStyle && !addonProvider.options().isEmpty()) {
            buildStyleOptionButton(addonProvider.options().get(0), addonProvider, btnX, ruleBtnY, leftBtnW);
        } else if (civilization) {
            addFooterButton(Button.builder(
                    Component.literal(com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.terrain_preset", "§3自定义地形")),
                    btn -> this.minecraft.setScreenAndShow(new TerrainPresetScreen(this))
            ).bounds(btnX, ruleBtnY, leftBtnW, 20).build());
        } else {
        boolean isTeamMode = "teams".equalsIgnoreCase(com.survivalcontract.client.ClientState.INSTANCE.gameModeKey);
        addFooterButton(Button.builder(
                Component.literal(isTeamMode
                        ? com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.double_ban", "§d双禁")
                        : com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.double_ban_locked", "§8双禁(仅团队)")),
                btn -> {
                    if (!isTeamMode) return;
                    this.minecraft.setScreenAndShow(new DoubleBanScreen(this));
                }).bounds(btnX, ruleBtnY, leftBtnW, 20).build());
        }
        }

        // 时长选择按钮（第一行第 4 位）：30 → 60 → 90 → 120 → 无限制 循环
        // 无最终目标玩法必须有限时长（时间结束分数高者获胜）→ 循环中跳过无限制
        int durationBtnX = leftZoneLeft + 3 * (leftBtnW + 8);
        addFooterButton(Button.builder(
                Component.literal(durationButtonLabel()),
                btn -> {
                    int[] cycle = noGoalStyle ? new int[]{30, 60, 90, 120} : new int[]{30, 60, 90, 120, -1};
                    int cur = com.survivalcontract.client.ClientState.INSTANCE.gameDurationMinutes;
                    int idx = 0;
                    for (int i = 0; i < cycle.length; i++) {
                        if (cycle[i] == cur) { idx = i; break; }
                    }
                    // 当前为无限制而循环不含 -1（无最终目标玩法）→ 从 30 开始
                    int curIdx = cur == -1 ? -1 : idx;
                    int next = cycle[(Math.max(0, curIdx) + 1) % cycle.length];
                    com.survivalcontract.client.ClientState.INSTANCE.gameDurationMinutes = next;
                    ClientPlayNetworking.send(new NetworkRegistry.GameDurationPayload(next));
                    btn.setMessage(Component.literal(durationButtonLabel()));
                }).bounds(durationBtnX, footerY, leftBtnW, 20).build());

        // 死亡扣分（左）+ 返回上级菜单（右）：死亡扣分 0 → 1 → 2 循环（全局设置，任意玩家修改全员同步）；
        // 返回上级菜单回大厅初始菜单，多人需 2 名不同玩家确认（服务端计数）
        int splitBtnW = (startBtnW - 6) / 2;
        addFooterButton(Button.builder(
                Component.literal(deathPenaltyLabel()),
                btn -> {
                    int next = (com.survivalcontract.client.ClientState.INSTANCE.deathPenalty + 1) % 3;
                    // 乐观更新 ClientState（服务端 sync 回来前按钮立即显示新值）
                    com.survivalcontract.client.ClientState.INSTANCE.deathPenalty = next;
                    ClientPlayNetworking.send(new NetworkRegistry.SetDeathPenaltyPayload(next));
                    btn.setMessage(Component.literal(deathPenaltyLabel()));
                }).bounds(startBtnX, footerY, splitBtnW, 22).build());
        addFooterButton(Button.builder(
                com.survivalcontract.client.ClientL10n.gui("back"),
                btn -> {
                    try {
                        ClientPlayNetworking.send(new NetworkRegistry.LobbyVotePayload(2));
                    } catch (Exception e) {
                        com.survivalcontract.SurvivalContract.LOGGER.error("back button failed", e);
                    }
                }).bounds(startBtnX + splitBtnW + 6, footerY, splitBtnW, 22).build());

        // 开始游戏（投票制：多人需 2 名不同玩家确认，服务端计数达标后才开始，不本地关闭 GUI）
        addFooterButton(Button.builder(
                Component.literal(startButtonLabel()),
                btn -> {
                    try {
                        ClientPlayNetworking.send(new NetworkRegistry.LobbyVotePayload(1));
                    } catch (Exception e) {
                        com.survivalcontract.SurvivalContract.LOGGER.error("start button failed", e);
                    }
                }).bounds(startBtnX, footerY + 24, startBtnW, 22).build());

        // === 非 LOBBY 阶段：底部所有按钮（职业/模式/目标/时长/双禁/3 规则开关/开始游戏）统一置黑禁用 ===
        if (!"LOBBY".equalsIgnoreCase(com.survivalcontract.client.ClientState.INSTANCE.currentPhase)) {
            for (Button b : footerButtons) {
                b.active = false;
                String plain = b.getMessage().getString().replaceAll("§[0-9a-fk-or]", "");
                b.setMessage(Component.literal("§8" + plain));
            }
        }
    }

    /** 底部控制栏按钮注册（统一收集，便于非大厅阶段批量置黑禁用） */
    private void addFooterButton(Button button) {
        addRenderableWidget(button);
        footerButtons.add(button);
    }

    // ============ 大厅初始菜单（stage 0） ============
    private record LobbyPlayerEntry(java.util.UUID id, String name, ItemStack head) {}
    private final java.util.List<LobbyPlayerEntry> lobbyListPlayers = new java.util.ArrayList<>();
    private int lobbyPanelX, lobbyPanelY, lobbyPanelW, lobbyPanelH;
    private int lobbyListScroll = 0;
    private static final int LOBBY_ROW_H = 26;
    // 任务抢夺/猎人游戏按钮引用（悬浮说明定位用）
    private Button rushButton;
    private Button hunterButton;

    /** 构建初始菜单 widgets：模式选择（互斥点亮）+ "我决定好了"（多人需 2 名玩家确认） */
    private void buildLobbyInitialMenu() {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;
        int padding = 16;
        int btnW = 150;
        int btnY = HEADER_HEIGHT + 12;
        int totalW = btnW * 2 + 16;
        int startX = (this.width - totalW) / 2;

        // 个人模式按钮（点亮 = 当前模式）
        boolean solo = !"teams".equalsIgnoreCase(cs.gameModeKey);
        addRenderableWidget(Button.builder(
                Component.literal(solo ? com.survivalcontract.client.ClientL10n.guiText("mode_solo")
                        : com.survivalcontract.client.ClientL10n.guiText("mode_solo_off")),
                btn -> {
                    cs.gameModeKey = "solo";
                    ClientPlayNetworking.send(new NetworkRegistry.SetGameModePayload("solo"));
                    rebuildWidgets();
                }).bounds(startX, btnY, btnW, 24).build());

        // 团队模式按钮（点击后服务端自动分红蓝队并染色）
        addRenderableWidget(Button.builder(
                Component.literal(solo ? com.survivalcontract.client.ClientL10n.guiText("mode_teams_off")
                        : com.survivalcontract.client.ClientL10n.guiText("mode_teams")),
                btn -> {
                    cs.gameModeKey = "teams";
                    ClientPlayNetworking.send(new NetworkRegistry.SetGameModePayload("teams"));
                    rebuildWidgets();
                }).bounds(startX + btnW + 16, btnY, btnW, 24).build());

        // 玩家列表面板矩形（渲染在 extractRenderState；五列，可滚动）
        // 布局自上而下（三行按钮统一高度 24，各自水平居中）：
        // 模式按钮(btnY..+24) → 第二行开关(任务抢夺/猎人游戏, btnY+28..+52) → 第三行玩法四选一(btnY+56..+80) →
        // 提示文字(HEADER_HEIGHT+96) → 玩家面板 → 进度提示(面板底+2) → "我决定好了"按钮(面板底+13)。
        // 文字/按钮跟随面板底边定位，面板高度按剩余空间自适应（至少一行 26px），任何窗口高度都不会重叠
        lobbyPanelX = padding + 20;
        lobbyPanelY = btnY + 100;
        lobbyPanelW = this.width - (padding + 20) * 2;
        int maxPanelBottom = this.height - 64; // 底部预留：进度提示(9)+间距(2)+按钮(26)+热键栏边距
        lobbyPanelH = Math.max(26, maxPanelBottom - lobbyPanelY);
        int panelBottom = lobbyPanelY + lobbyPanelH;

        // === 第二行开关：任务抢夺（全模式可用）+ 猎人游戏（仅团队模式可开） ===
        int row2Y = btnY + 28;
        boolean rushOn = enabled.contains("rule_task_rush");
        rushButton = Button.builder(
                Component.literal(rushOn ? com.survivalcontract.client.ClientL10n.guiText("task_rush")
                        : com.survivalcontract.client.ClientL10n.guiText("task_rush_off")),
                btn -> {
                    boolean newState = !enabled.contains("rule_task_rush");
                    if (newState) enabled.add("rule_task_rush"); else enabled.remove("rule_task_rush");
                    ClientPlayNetworking.send(new NetworkRegistry.ChallengeTogglePayload("rule_task_rush", newState));
                    rebuildWidgets();
                }).bounds(startX, row2Y, btnW, 24).build();
        addRenderableWidget(rushButton);

        boolean hunterUsable = !"solo".equalsIgnoreCase(cs.gameModeKey);
        boolean hunterOn = hunterUsable && enabled.contains("rule_hunter_game");
        hunterButton = Button.builder(
                Component.literal(hunterUsable
                        ? (hunterOn ? com.survivalcontract.client.ClientL10n.guiText("hunter_game")
                        : com.survivalcontract.client.ClientL10n.guiText("hunter_game_off"))
                        : com.survivalcontract.client.ClientL10n.guiText("hunter_game_locked")),
                btn -> {
                    if (!"solo".equalsIgnoreCase(cs.gameModeKey)) {
                        boolean newState = !enabled.contains("rule_hunter_game");
                        if (newState) enabled.add("rule_hunter_game"); else enabled.remove("rule_hunter_game");
                        ClientPlayNetworking.send(new NetworkRegistry.ChallengeTogglePayload("rule_hunter_game", newState));
                    }
                    rebuildWidgets();
                }).bounds(startX + btnW + 16, row2Y, btnW, 24).build();
        addRenderableWidget(hunterButton);

        // === 第三行：玩法选择（内置 4 项 + 注册表里已加载的附属玩法） ===
        int styleRowY = btnY + 56;
        java.util.List<String[]> styleList = new java.util.ArrayList<>();
        styleList.add(new String[]{"NORMAL", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.playstyle_normal", "常规模式")});
        styleList.add(new String[]{"GODS_LIES", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.playstyle_godslies", "神的谎言")});
        styleList.add(new String[]{"STOWAWAY", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.playstyle_stowaway", "走私犯与检察官")});
        styleList.add(new String[]{"CIVILIZATION", com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.playstyle_civilization", "文明时代")});
        // 附属玩法：显示名取自其语言文件（缺失则回退 displayNameFallback）
        for (var provider : com.survivalcontract.api.PlayStyleRegistry.all()) {
            String name = Component.translatable(provider.displayNameKey(), provider.displayNameFallback()).getString();
            if (name.equals(provider.displayNameKey())) name = provider.displayNameFallback();
            styleList.add(new String[]{provider.id(), name});
        }
        String[][] styleDefs = styleList.toArray(new String[0][]);
        int styleCount = styleDefs.length;
        int styleGap = 8;
        int availW = Math.max(200, this.width - 32);
        int styleW = Math.max(64, Math.min(110, (availW - (styleCount - 1) * styleGap) / styleCount));
        int styleH = 24;
        int styleStartX = (this.width - (styleCount * styleW + (styleCount - 1) * styleGap)) / 2;
        for (int i = 0; i < styleDefs.length; i++) {
            final String fkey = styleDefs[i][0];
            final String fname = styleDefs[i][1];
            boolean sel = fkey.equalsIgnoreCase(cs.playStyleKey);
            addRenderableWidget(Button.builder(
                    Component.literal((sel ? "§a✔ " : "§8") + fname),
                    btn -> {
                        ClientPlayNetworking.send(new NetworkRegistry.SetPlayStylePayload(fkey));
                        rebuildWidgets();
                    }).bounds(styleStartX + i * (styleW + styleGap), styleRowY, styleW, styleH).build());
        }

        collectLobbyPlayers();
        lobbyListScroll = 0;

        // "我决定好了！"（多人需 2 名不同玩家确认）— 紧贴面板底边之下
        addRenderableWidget(Button.builder(
                Component.literal(confirmLabel()),
                btn -> ClientPlayNetworking.send(new NetworkRegistry.LobbyVotePayload(0)))
                .bounds((this.width - 220) / 2, panelBottom + 13, 220, 26).build());
    }

    /** 收集当前世界内的玩家（连接 tab 列表为主，世界内玩家实体为兜底），头像 = 带 Profile 的玩家头 */
    private void collectLobbyPlayers() {
        lobbyListPlayers.clear();
        var mc = net.minecraft.client.Minecraft.getInstance();
        java.util.Set<java.util.UUID> seen = new HashSet<>();
        if (mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) {
                try {
                    var profile = info.getProfile();
                    if (profile == null || profile.id() == null || !seen.add(profile.id())) continue;
                    // 观战者（旁观者模式）不出现在书中名单
                    var entity = mc.level != null ? mc.level.getPlayerByUUID(profile.id()) : null;
                    if (entity != null && entity.isSpectator()) continue;
                    ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                    try {
                        head.set(net.minecraft.core.component.DataComponents.PROFILE,
                                net.minecraft.world.item.component.ResolvableProfile.createResolved(profile));
                    } catch (Exception ignored) { /* 解析失败回退普通头颅 */ }
                    String name = profile.name() == null ? "玩家" : profile.name();
                    lobbyListPlayers.add(new LobbyPlayerEntry(profile.id(), name, head));
                } catch (Exception ignored) { /* 单个条目失败不影响其余玩家 */ }
            }
        }
        // 兜底：tab 列表未就绪时用世界内玩家实体（正好就是"当前世界内的玩家"）
        if (lobbyListPlayers.isEmpty() && mc.level != null) {
            for (var p : mc.level.players()) {
                if (!seen.add(p.getUUID())) continue;
                if (p.isSpectator()) continue; // 观战者不出现在书中名单
                lobbyListPlayers.add(new LobbyPlayerEntry(p.getUUID(),
                        p.getName().getString(), new ItemStack(Items.PLAYER_HEAD)));
            }
        }
    }

    private String confirmLabel() {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;
        return com.survivalcontract.client.ClientL10n.guiText("confirm_ready",
                Math.min(cs.lobbyConfirmCount, cs.lobbyConfirmNeed) + "/" + cs.lobbyConfirmNeed);
    }

    /** 开始游戏按钮文本：带投票进度（n/need），达标后去掉进度后缀 */
    private String startButtonLabel() {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;
        int need = Math.max(1, cs.lobbyStartNeed);
        if (cs.lobbyStartCount >= need) return com.survivalcontract.client.ClientL10n.guiText("start_game_done");
        return com.survivalcontract.client.ClientL10n.guiText("start_game",
                Math.min(cs.lobbyStartCount, need) + "/" + need);
    }

    /** 初始菜单渲染：半透明玩家面板（五列头像+名称，团队模式自动染色，可滚动） */
    private void renderLobbyInitialMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;

        // 玩家列表为空时每帧重试收集（init 时 tab 列表/世界实体可能尚未就绪）
        if (lobbyListPlayers.isEmpty()) {
            collectLobbyPlayers();
        }

        // 标题栏
        g.fill(0, 0, this.width, HEADER_HEIGHT, 0xFF202020);
        g.text(this.font, com.survivalcontract.client.ClientL10n.gui("title"), 16, 10, 0xFFFFFFFF, false);
        g.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, 0xFF555555);

        String hint = com.survivalcontract.client.ClientL10n.guiText("mode_hint");
        g.text(this.font, Component.literal(hint),
                (this.width - this.font.width(hint)) / 2, HEADER_HEIGHT + 96, 0xFFFFFFFF, false);

        // 半透明玩家面板
        g.fill(lobbyPanelX, lobbyPanelY, lobbyPanelX + lobbyPanelW, lobbyPanelY + lobbyPanelH, 0x80202028);
        g.fill(lobbyPanelX, lobbyPanelY, lobbyPanelX + lobbyPanelW, lobbyPanelY + 1, 0xFF6A5A2A);
        g.fill(lobbyPanelX, lobbyPanelY + lobbyPanelH - 1, lobbyPanelX + lobbyPanelW, lobbyPanelY + lobbyPanelH, 0xFF6A5A2A);

        // 五列玩家列表（滚动裁剪）
        int cols = 5;
        int colW = lobbyPanelW / cols;
        int rows = Math.ceilDiv(lobbyListPlayers.size(), cols);
        int contentH = rows * LOBBY_ROW_H;
        int maxScroll = Math.max(0, contentH - lobbyPanelH + 8);
        lobbyListScroll = Math.min(lobbyListScroll, maxScroll);

        g.enableScissor(lobbyPanelX, lobbyPanelY, lobbyPanelX + lobbyPanelW, lobbyPanelY + lobbyPanelH);
        for (int i = 0; i < lobbyListPlayers.size(); i++) {
            var e = lobbyListPlayers.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = lobbyPanelX + 10 + col * colW;
            int y = lobbyPanelY + 6 + row * LOBBY_ROW_H - lobbyListScroll;

            // 头像（真实玩家皮肤头）
            if (!e.head().isEmpty()) g.item(e.head(), x, y, 0);
            // 名称染色：团队模式下由服务端原版计分板染色（红/蓝），个人模式白色
            int color = 0xFFFFFFFF;
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    var team = mc.level.getScoreboard().getPlayersTeam(e.name());
                    if (team != null) {
                        String tn = team.getName();
                        if ("survival_red".equals(tn)) color = 0xFFFF5555;
                        else if ("survival_blue".equals(tn)) color = 0xFF5555FF;
                    }
                }
            } catch (Exception ignored) {}
            g.text(this.font, Component.literal(e.name()), x + 22, y + 4, color, false);
        }
        g.disableScissor();

        // 确认进度提示（跟随面板底边：面板底 +2，"我决定好了"按钮在 +13，互不重叠）
        String progress = com.survivalcontract.client.ClientL10n.guiText("confirm_progress",
                Math.min(cs.lobbyConfirmCount, cs.lobbyConfirmNeed), cs.lobbyConfirmNeed);
        int panelBottom = lobbyPanelY + lobbyPanelH;
        g.text(this.font, Component.literal(progress),
                (this.width - this.font.width(progress)) / 2, panelBottom + 2, 0xFFA0A0A0, false);
    }

    /** 规则开关按钮文案：名称 + 开/关状态 */
    private String ruleButtonLabel(String name, String ruleId) {
        String resolved = com.survivalcontract.client.ClientL10n.tr(
                "survival-contract.gui.rule." + ruleId.substring("rule_".length()), name);
        return resolved + ": " + (enabled.contains(ruleId)
                ? com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.on", "§a开")
                : com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.off", "§c关"));
    }

    /** 神的谎言：使者数量按钮文案 */
    private String envoyCountLabel() {
        return String.format(com.survivalcontract.client.ClientL10n.tr(
                        "survival-contract.gui.envoy_count", "§d使者数量：%s"),
                com.survivalcontract.client.ClientState.INSTANCE.envoyCount);
    }

    // ============ 初始菜单按钮悬浮说明（任务抢夺 / 猎人游戏） ============

    /** 在 widgets 渲染之后设置 tooltip（保证显示在最上层），模式同职业菜单悬停说明。
     *  简中回退文案必须在此传入（简中环境内容键不走语言文件，直接用源码简中）。 */
    private void renderInitialMenuTooltips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        renderRuleTooltip(g, mouseX, mouseY, rushButton, "rule_task_rush", "任务抢夺",
                "开启后全员共享同一专属任务：任意玩家完成后，所有人立刻刷新为同一个新任务");
        renderRuleTooltip(g, mouseX, mouseY, hunterButton, "rule_hunter_game", "猎人游戏",
                "（仅团队模式）主动击杀其他玩家夺取其2分；误杀队友则全队每人扣除2分（仅PVP开启后有效）");
    }

    /** 悬停按钮时显示规则说明：规则名 + 描述（描述按宽度折行，语言文件优先、简中回退） */
    private void renderRuleTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                   Button btn, String ruleId, String fallbackName, String fallbackDesc) {
        if (btn == null || !btn.isMouseOver(mouseX, mouseY)) return;
        String name = com.survivalcontract.client.ClientL10n.challengeName(ruleId, fallbackName);
        String desc = com.survivalcontract.client.ClientL10n.challengeDesc(ruleId, fallbackDesc);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§e" + name));
        if (!desc.isEmpty()) {
            for (Component l : wrapText(this.font, desc, TOOLTIP_WRAP_WIDTH)) {
                lines.add(Component.literal("§7" + l.getString()));
            }
        }
        g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    /** 时长选择按钮文案 */
    private String durationButtonLabel() {
        int m = com.survivalcontract.client.ClientState.INSTANCE.gameDurationMinutes;
        return m < 0
                ? com.survivalcontract.client.ClientL10n.guiText("duration_unlimited")
                : com.survivalcontract.client.ClientL10n.guiText("duration", m);
    }

    private String professionDisplayName() {
        try {
            String key = com.survivalcontract.client.ClientState.INSTANCE.professionKey;
            String name = com.survivalcontract.client.ClientL10n.professionName(key, Profession.valueOf(key).chineseName);
            return com.survivalcontract.client.ClientL10n.guiText("profession_label", name);
        } catch (Exception e) { return com.survivalcontract.client.ClientL10n.guiText("profession_label", professionKey); }
    }

    /**
     * 附属玩法整数选项按钮（循环切换）：点击后乐观更新本地值并上报服务端。
     * 第 1 个选项占用第二行「双禁」位，第 2 个选项占用第一行「最终目标」位。
     */
    private void buildStyleOptionButton(com.survivalcontract.api.StyleOption opt,
                                       com.survivalcontract.api.ScPlayStyleProvider provider,
                                       int x, int y, int w) {
        addFooterButton(Button.builder(
                Component.literal(styleOptionLabel(opt)),
                btn -> {
                    int cur = com.survivalcontract.client.ClientState.INSTANCE
                            .styleOption(opt.key(), opt.defaultValue());
                    int next = opt.next(cur);
                    com.survivalcontract.client.ClientState.INSTANCE.styleOptions.put(opt.key(), next);
                    ClientPlayNetworking.send(new NetworkRegistry.StyleOptionPayload(
                            provider.id(), opt.key(), next));
                    btn.setMessage(Component.literal(styleOptionLabel(opt)));
                }).bounds(x, y, w, 20).build());
    }

    /** 附属玩法整数选项按钮文案（语言文件缺失时回退到 provider 提供的兜底文案） */
    private String styleOptionLabel(com.survivalcontract.api.StyleOption opt) {
        int v = com.survivalcontract.client.ClientState.INSTANCE.styleOption(opt.key(), opt.defaultValue());
        String valueLabel = opt.valueLabelKey(v); // 取值名（如「街角」）；无则用数字
        if (valueLabel != null) {
            Component name = Component.translatable(valueLabel);
            String nameText = name.getString();
            String s = Component.translatable(opt.labelKey(), name).getString();
            if (s.equals(opt.labelKey())) s = opt.labelFallback().replace("%s", nameText);
            return s;
        }
        String s = Component.translatable(opt.labelKey(), v).getString();
        if (s.equals(opt.labelKey())) {
            s = opt.labelFallback().replace("%s", String.valueOf(v));
        }
        return s;
    }

    private String finalGoalDisplayName() {
        String key = finalGoalKey.toUpperCase();
        String name;
        if ("RANDOM".equals(key)) {
            name = com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.goal.RANDOM", "§d随机");
        } else {
            try {
                name = com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.goal." + key,
                        com.survivalcontract.game.GameStateManager.FinalGoal.valueOf(key).displayName);
            } catch (Exception e) { return com.survivalcontract.client.ClientL10n.guiText("goal_label", finalGoalKey); }
        }
        return com.survivalcontract.client.ClientL10n.guiText("goal_label", name);
    }

    // ===== 渲染 =====
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;

        // === 大厅初始菜单（stage 0）单独渲染 ===
        if ("LOBBY".equalsIgnoreCase(cs.currentPhase) && cs.lobbyStage == 0 && lobbyListPlayers != null) {
            renderLobbyInitialMenu(graphics, mouseX, mouseY);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            renderInitialMenuTooltips(graphics, mouseX, mouseY);
            return;
        }

        // 背景
        graphics.fill(0, 0, this.width, this.height, 0xF0100010);

        // 标题栏
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, 0xFF202020);
        graphics.text(this.font, com.survivalcontract.client.ClientL10n.gui("title"), 16, 10, 0xFFFFFFFF, false);
        graphics.text(this.font, com.survivalcontract.client.ClientL10n.gui("grid_hint"), 16, HEADER_HEIGHT - 16, 0xFF808080, false);
        String scoreText = com.survivalcontract.client.ClientL10n.guiText("initial_score", calculateInitialScore());
        int scoreW = this.font.width(scoreText);
        graphics.text(this.font, Component.literal(scoreText), this.width - scoreW - 16, 10, 0xFFFFFFFF, false);

        // 分隔线
        graphics.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, 0xFF555555);
        graphics.fill(0, this.height - FOOTER_HEIGHT - 1, this.width, this.height - FOOTER_HEIGHT, 0xFF555555);

        // 网格区域（裁剪渲染：滚动内容不会覆盖顶部标题栏/底部控制栏）
        graphics.enableScissor(0, gridTop, this.width, gridTop + gridHeight);
        renderCardContent(graphics, mouseX, mouseY);
        graphics.disableScissor();

        // 底部背景
        graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, 0xFF202020);

        // 滚动条指示（在裁剪外绘制）
        renderScrollBar(graphics);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCardContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // --- 神的谎言（stage 1）：规则区占位说明 ---
        if (godsLiesStage1) {
            String[] lines = {
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.godslies.hidden_title", "§d§l✦ 神的谎言 ✦"),
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.godslies.hidden_desc", "§7所有规则与奇遇事件已隐藏且不可自选"),
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.godslies.hidden_desc2",
                            "§7系统已为每位玩家随机分配总分恰好 §f120 §7分的规则——§c开局后也不可查看"),
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.godslies.hidden_desc3",
                            "§5玩家中隐藏着「神的使者」——他们免疫所有规则，完成任务可指定玩家扣 55 分")
            };
            int y = gridTop + Math.max(16, (gridHeight - lines.length * 14) / 2);
            for (String line : lines) {
                graphics.centeredText(this.font, Component.literal(line), this.width / 2, y, 0xFFFFFFFF);
                y += 14;
            }
            return;
        }
        // --- 附属玩法：规则区占位说明 ---
        if (addonHideRules) {
            var provider = com.survivalcontract.api.PlayStyleRegistry.byId(
                    com.survivalcontract.client.ClientState.INSTANCE.playStyleKey);
            String title = provider != null
                    ? Component.translatable(provider.displayNameKey(), provider.displayNameFallback()).getString()
                    : com.survivalcontract.client.ClientL10n.guiText("addon.default_name");
            String[] lines = {
                    "§6§l✦ " + title + " ✦",
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.addon.hidden_desc", "§7本玩法无自选规则、职业锁定为「无」、团队箱关闭"),
                    com.survivalcontract.client.ClientL10n.tr(
                            "survival-contract.gui.addon.hidden_desc2",
                            "§7下方仍可调整 PVP / 夜视 / 保留物品栏三个全局开关")
            };
            int y = gridTop + Math.max(16, (gridHeight - lines.length * 14) / 2);
            for (String line : lines) {
                graphics.centeredText(this.font, Component.literal(line), this.width / 2, y, 0xFFFFFFFF);
                y += 14;
            }
            return;
        }
        // --- 板块化布局渲染（与 mouseClicked 命中共用 buildBookLayout 坐标）---
        for (BookRow r : buildBookLayout()) {
            int y = r.y() - scrollOffset;
            int h = r.header() ? SECTION_HEADER_H
                    : ("events".equals(r.sectionId()) ? EVENT_ROW_H : CARD_HEIGHT);
            if (y + h < gridTop || y > gridTop + gridHeight) continue;

            if (r.header()) {
                drawSectionHeader(graphics, r, y);
            } else if ("events".equals(r.sectionId())) {
                drawEventRow(graphics, com.survivalcontract.event.RandomEventManager.EventType.values()[r.index()],
                        r.x(), y, gridWidth);
            } else {
                drawCard(graphics, cards.get(r.index()), r.x(), y, mouseX, mouseY);
            }
        }
    }

    /** 板块标题行：名称 + 折叠/展开指示，点击整行折叠 */
    private void drawSectionHeader(GuiGraphicsExtractor g, BookRow r, int y) {
        boolean collapsed = collapsedSections.contains(r.sectionId());
        g.fill(r.x(), y, r.x() + gridWidth, y + SECTION_HEADER_H - 2, 0xFF3A3226);
        g.fill(r.x(), y, r.x() + gridWidth, y + 1, 0xFF8A6D1B);
        g.text(this.font, Component.literal((collapsed ? "§e▶ " : "§e▼ ") + r.title()),
                r.x() + 6, y + 5, 0xFFFFFFFF, false);
        String tip = collapsed
                ? com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.expand_hint", "§8点击展开")
                : com.survivalcontract.client.ClientL10n.tr("survival-contract.gui.collapse_hint", "§8点击收起");
        g.text(this.font, Component.literal(tip), r.x() + gridWidth - this.font.width(tip) - 8,
                y + 5, 0xFF808080, false);
    }

    private void drawCard(GuiGraphicsExtractor g, ChallengeCard card, int x, int y, int mouseX, int mouseY) {
        boolean active = enabled.contains(card.id());
        boolean hovered = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + CARD_HEIGHT;

        int bgColor = active ? 0xFF1F4A1F : 0xFF333333;
        if (hovered) bgColor = active ? 0xFF2A5E2A : 0xFF444444;
        g.fill(x, y, x + cardW, y + CARD_HEIGHT, bgColor);

        int borderColor = active ? 0xFF66BB6A : 0xFF666666;
        if (hovered) borderColor = active ? 0xFF81C784 : 0xFFAAAAAA;
        g.fill(x, y, x + cardW, y + 1, borderColor);
        g.fill(x, y + CARD_HEIGHT - 1, x + cardW, y + CARD_HEIGHT, borderColor);
        g.fill(x, y, x + 1, y + CARD_HEIGHT, borderColor);
        g.fill(x + cardW - 1, y, x + cardW, y + CARD_HEIGHT, borderColor);

        // 图标
        int iconX = x + 7;
        int iconY = y + (CARD_HEIGHT - CARD_ICON_SIZE) / 2;
        drawIconSafe(g, resolveIcon(card.iconItemId()), iconX, iconY, CARD_ICON_SIZE);

        // 名称（单行，超长省略）——红字规则（baseScore<0，选择即扣分）常显红色
        int textX = iconX + CARD_ICON_SIZE + 6;
        int maxNameWidth = cardW - CARD_ICON_SIZE - 40;
        String displayName = shorten(card.displayName(), maxNameWidth, this.font);
        boolean redRule = card.baseScore() < 0;
        String namePrefix = redRule ? "§c" : (active ? "§a" : "");
        g.text(this.font, Component.literal(namePrefix + displayName),
                textX, y + 4, 0xFFFFFFFF, false);

        // 中间行：分值（可自定义数值的卡片按当前数值加权显示；红字规则显示"选择即扣X分"）
        if (!card.id().startsWith("rule_")) {
            if (redRule) {
                g.text(this.font, com.survivalcontract.client.ClientL10n.gui("red_rule_cost", -card.baseScore()),
                        textX, y + 15, 0xFFFFFFFF, false);
            } else if ("inv_slot_lock".equals(card.id())) {
                int v = customValues.getOrDefault(card.id(), card.customValue());
                int pts = v == 27 ? 4 : v == 18 ? 8 : 16;
                g.text(this.font, Component.literal(com.survivalcontract.client.ClientL10n.guiText("score_only", pts)
                                + " / " + com.survivalcontract.client.ClientL10n.guiText("slots_short", v)),
                        textX, y + 15, 0xFFFFFFFF, false);
            } else {
                int shown = weightedScore(card, customValues.getOrDefault(card.id(), card.customValue()));
                g.text(this.font, card.penaltyScore() > 0
                                ? com.survivalcontract.client.ClientL10n.gui("score_with_penalty", shown, card.penaltyScore())
                                : com.survivalcontract.client.ClientL10n.gui("score_only", shown),
                        textX, y + 15, 0xFFFFFFFF, false);
            }
        }

        // 底行：背包格子锁定切换按钮 / 自定义数值（削弱百分比 ± 按钮，步长 10%）
        if ("inv_slot_lock".equals(card.id()) && active) {
            int v = customValues.getOrDefault(card.id(), card.customValue());
            g.text(this.font, com.survivalcontract.client.ClientL10n.gui("lock_slots", v),
                    textX, y + 27, 0xFFFFFFFF, false);
            int bx = textX + 64;
            g.fill(bx, y + 24, bx + 34, y + 36, 0xFF555555);
            g.text(this.font, com.survivalcontract.client.ClientL10n.gui("switch"), bx + 5, y + 27, 0xFFFFFFFF, false);
        } else if (card.customizable()) {
            int v = customValues.getOrDefault(card.id(), card.customValue());
            g.text(this.font, com.survivalcontract.client.ClientL10n.gui("custom_value", v + "%"),
                    textX, y + 27, 0xFFFFFFFF, false);
            int btnY = y + 24;
            int minusX = textX + 56;
            int plusX = textX + 72;
            g.fill(minusX, btnY, minusX + 12, btnY + 12, 0xFF555555);
            g.fill(minusX + 2, btnY + 5, minusX + 10, btnY + 7, 0xFFFFFFFF); // "-" 横
            g.fill(plusX, btnY, plusX + 12, btnY + 12, 0xFF555555);
            g.fill(plusX + 2, btnY + 5, plusX + 10, btnY + 7, 0xFFFFFFFF);   // "+" 横
            g.fill(plusX + 5, btnY + 2, plusX + 7, btnY + 10, 0xFFFFFFFF);   // "+" 竖
        } else {
            g.text(this.font, com.survivalcontract.client.ClientL10n.gui("not_customizable"), textX, y + 27, 0xFF808080, false);
        }

        // 右侧开关
        int toggleX = x + cardW - 16;
        int toggleY = y + 5;
        g.fill(toggleX, toggleY, toggleX + 10, toggleY + 10, active ? 0xFF4CAF50 : 0xFF616161);
        if (active) {
            g.text(this.font, Component.literal("✓"), toggleX + 2, toggleY + 1, 0xFFFFFFFF, false);
        }

        // 悬停 tooltip：完整名称 + 描述（描述统一折行，保证各界面 tooltip 宽度一致）
        if (hovered && !card.description().isEmpty()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§e" + card.displayName()));
            for (Component l : wrapText(font, card.description(), TOOLTIP_WRAP_WIDTH)) {
                lines.add(Component.literal("§7" + l.getString()));
            }
            g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
        }
    }

    /** tooltip 折行宽度上限（挑战书/职业/双禁/规则预览统一使用） */
    public static final int TOOLTIP_WRAP_WIDTH = 170;

    /** 按宽度折行：中文逐字、ASCII 单词不拆断；调用方负责给每行补颜色前缀 */
    public static List<Component> wrapText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        List<Component> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            boolean asciiRun = isAsciiWordChar(c) && !line.isEmpty() && isAsciiWordChar(line.charAt(line.length() - 1));
            if (!line.isEmpty() && !asciiRun && font.width(line.toString() + c) > maxWidth) {
                out.add(Component.literal(line.toString()));
                line.setLength(0);
            }
            line.append(c);
            i++;
        }
        if (!line.isEmpty()) out.add(Component.literal(line.toString()));
        return out;
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private void drawEventRow(GuiGraphicsExtractor g, com.survivalcontract.event.RandomEventManager.EventType ev,
                              int x, int y, int rowWidth) {
        int rowW = Math.min(rowWidth, 400);
        boolean on = enabledEvents.contains(ev.name());
        g.fill(x, y, x + rowW, y + EVENT_ROW_H, on ? 0xFF3A3320 : 0xFF2A2A33);
        g.fill(x, y, x + rowW, y + 1, on ? 0xFF8A7A3A : 0xFF444444);

        drawIconSafe(g, resolveIcon(eventIconItem(ev)), x + 5, y + (EVENT_ROW_H - EVENT_ICON_SIZE) / 2, EVENT_ICON_SIZE);

        String tierColor = switch (ev.tier) {
            case S -> "§c";
            case A -> "§6";
            case B -> "§7";
        };
        int textX = x + EVENT_ICON_SIZE + 12;
        String evName = com.survivalcontract.client.ClientL10n.eventName(ev.name(), ev.displayName);
        g.text(this.font, Component.literal(tierColor + "[" + ev.tier + "] " + evName
                        + " " + com.survivalcontract.client.ClientL10n.guiText("event_score", ev.score)),
                textX, y + 3, 0xFFFFFFFF, false);
        g.text(this.font, Component.literal("§8" + com.survivalcontract.client.ClientL10n.eventDesc(ev.name(), ev.description)),
                textX, y + 14, 0xFFFFFFFF, false);

        // 右侧勾选框
        int cbX = x + rowW - 18;
        int cbY = y + (EVENT_ROW_H - 10) / 2;
        g.fill(cbX, cbY, cbX + 10, cbY + 10, on ? 0xFFFBC02D : 0xFF616161);
        if (on) {
            g.text(this.font, Component.literal("✓"), cbX + 2, cbY + 1, 0xFF000000, false);
        }

        // 刷新间隔控件（A-B 分钟，全局设置；LOBBY 阶段可点）：[A-][A+] 7-10分 [B-][B+]
        // 坐标必须与 mouseClicked 的事件行命中公式保持一致
        int[] interval = com.survivalcontract.client.ClientState.INSTANCE.getEventInterval(ev.name());
        int ctrlY = y + (EVENT_ROW_H - 12) / 2;
        int bPlusX = cbX - 14, bMinusX = cbX - 28;
        int aPlusX = cbX - 82, aMinusX = cbX - 96;
        g.fill(bPlusX, ctrlY, bPlusX + 12, ctrlY + 12, 0xFF3A3A4A);
        g.fill(bMinusX, ctrlY, bMinusX + 12, ctrlY + 12, 0xFF3A3A4A);
        g.fill(aPlusX, ctrlY, aPlusX + 12, ctrlY + 12, 0xFF3A3A4A);
        g.fill(aMinusX, ctrlY, aMinusX + 12, ctrlY + 12, 0xFF3A3A4A);
        g.text(this.font, Component.literal("§f+"), bPlusX + 4, ctrlY + 2, 0xFFFFFFFF, false);
        g.text(this.font, Component.literal("§f-"), bMinusX + 4, ctrlY + 2, 0xFFFFFFFF, false);
        g.text(this.font, Component.literal("§f+"), aPlusX + 4, ctrlY + 2, 0xFFFFFFFF, false);
        g.text(this.font, Component.literal("§f-"), aMinusX + 4, ctrlY + 2, 0xFFFFFFFF, false);
        g.text(this.font, Component.literal("§e" + com.survivalcontract.client.ClientL10n.guiText("minutes_short", interval[0], interval[1])),
                bMinusX - 42, ctrlY + 2, 0xFFFFFFFF, false);
    }

    public static String eventIconItem(com.survivalcontract.event.RandomEventManager.EventType ev) {
        return switch (ev) {
            case LIGHTNING -> "minecraft:lightning_rod";
            case PILLAGER_SQUAD -> "minecraft:crossbow";
            case CAMEL_RIDER -> "minecraft:camel_husk_spawn_egg";
            case ZOMBIE_HORSE_CAVALRY -> "minecraft:zombie_horse_spawn_egg";
            case SKELETON_HORSE_TRAP -> "minecraft:skeleton_horse_spawn_egg";
            case SILENT_CREEPER -> "minecraft:gunpowder";
            case SKELETON_ELITE_SQUAD -> "minecraft:bow";
            case KILLER_RABBIT -> "minecraft:rabbit_foot";
            case FOUR_BROTHERS -> "minecraft:wolf_spawn_egg";
            case HUNGER_BOMB -> "minecraft:rotten_flesh";
            case CRIMSON_INVASION -> "minecraft:zoglin_spawn_egg";
        };
    }

    private void renderScrollBar(GuiGraphicsExtractor g) {
        if (maxScroll <= 0) return;
        int barWidth = 6;
        int barX = this.width - 12;
        int barTotalHeight = gridHeight;
        int thumbHeight = Math.max(20, barTotalHeight * barTotalHeight / (maxScroll + barTotalHeight));
        int thumbY = gridTop + (scrollOffset * (barTotalHeight - thumbHeight) / maxScroll);

        g.fill(barX, gridTop, barX + barWidth, gridTop + barTotalHeight, 0x80555555);
        g.fill(barX, thumbY, barX + barWidth, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private ItemStack resolveIcon(String id) {
        if (id == null || id.isEmpty()) return null; // 渲染侧用灰色"?"占位，不显示屏障
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

    /** 渲染物品图标；解析失败时画灰色"?"占位（绝不用屏障方块） */
    private void drawIconSafe(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
        if (icon != null) {
            g.item(icon, x, y, 0);
        } else {
            g.centeredText(this.font, Component.literal("§7?"), x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    private String shorten(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        if (font.width(text) <= maxWidth) return text;
        while (font.width(text + "…") > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private int calculateInitialScore() {
        return initialScore;
    }

    // ===== 鼠标处理 =====
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean active) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        // 仅大厅阶段可交互：游戏开始/结束后任务书内一切交互格子均不可点击（仅可浏览与滚动）
        if (!"LOBBY".equalsIgnoreCase(com.survivalcontract.client.ClientState.INSTANCE.currentPhase)) {
            return super.mouseClicked(event, active);
        }

        // 只有点击落在可视网格区内才做命中检测（板块标题 + 挑战卡片 + 遭遇事件行）
        if (mouseY >= gridTop && mouseY <= gridTop + gridHeight) {
            for (BookRow r : buildBookLayout()) {
                int y = r.y() - scrollOffset;

                // 板块标题行：点击折叠/展开
                if (r.header()) {
                    if (mouseX >= r.x() && mouseX <= r.x() + gridWidth
                            && mouseY >= y && mouseY <= y + SECTION_HEADER_H - 2) {
                        if (collapsedSections.contains(r.sectionId())) collapsedSections.remove(r.sectionId());
                        else collapsedSections.add(r.sectionId());
                        rebuildWidgets(); // 重算 maxScroll
                        return true;
                    }
                    continue;
                }

                if ("events".equals(r.sectionId())) {
                    // --- 遭遇事件行点击（与 drawEventRow 渲染使用同一套坐标公式）---
                    int rowW = Math.min(gridWidth, 400);
                    if (mouseX >= r.x() && mouseX <= r.x() + rowW && mouseY >= y && mouseY <= y + EVENT_ROW_H) {
                        var ev = com.survivalcontract.event.RandomEventManager.EventType.values()[r.index()];
                        int cbXm = r.x() + rowW - 18;
                        int ctrlYm = y + (EVENT_ROW_H - 12) / 2;
                        int bPlusXm = cbXm - 14, bMinusXm = cbXm - 28;
                        int aPlusXm = cbXm - 82, aMinusXm = cbXm - 96;
                        if (mouseY >= ctrlYm && mouseY <= ctrlYm + 12) {
                            if (mouseX >= bPlusXm && mouseX <= bPlusXm + 12) { adjustEventInterval(ev.name(), false, 1); return true; }
                            if (mouseX >= bMinusXm && mouseX <= bMinusXm + 12) { adjustEventInterval(ev.name(), false, -1); return true; }
                            if (mouseX >= aPlusXm && mouseX <= aPlusXm + 12) { adjustEventInterval(ev.name(), true, 1); return true; }
                            if (mouseX >= aMinusXm && mouseX <= aMinusXm + 12) { adjustEventInterval(ev.name(), true, -1); return true; }
                        }
                        toggleEvent(ev);
                        return true;
                    }
                    continue;
                }

                // --- 挑战卡片点击 ---
                if (mouseX >= r.x() && mouseX <= r.x() + cardW
                        && mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                    ChallengeCard card = cards.get(r.index());

                    // 可自定义数值卡片：先检查 [-]/[+] 按钮命中（步长 10%）
                    if (card.customizable()) {
                        int textX = r.x() + 7 + CARD_ICON_SIZE + 6;
                        int minusX = textX + 56, plusX = textX + 72;
                        int btnY = y + 24;
                        if (mouseX >= minusX && mouseX <= minusX + 12
                                && mouseY >= btnY && mouseY <= btnY + 12) {
                            adjustCustomValue(card, -1);
                            return true;
                        }
                        if (mouseX >= plusX && mouseX <= plusX + 12
                                && mouseY >= btnY && mouseY <= btnY + 12) {
                            adjustCustomValue(card, 1);
                            return true;
                        }
                    }

                    // 背包格子锁定：27/18/9 三档切换按钮（仅已启用时显示/可点，坐标与 drawCard 渲染一致）
                    if ("inv_slot_lock".equals(card.id()) && enabled.contains(card.id())) {
                        int textX = r.x() + 7 + CARD_ICON_SIZE + 6;
                        int bx = textX + 64;
                        if (mouseX >= bx && mouseX <= bx + 34 && mouseY >= y + 24 && mouseY <= y + 36) {
                            int v = customValues.getOrDefault(card.id(), card.customValue());
                            int next = v == 27 ? 18 : v == 18 ? 9 : 27;
                            customValues.put(card.id(), next);
                            ClientPlayNetworking.send(new NetworkRegistry.ChallengeValuePayload(card.id(), next));
                            // 三档分值：27格=4分 / 18格=8分 / 9格=16分，按差值调整本地面板预估分
                            initialScore += lockTierScore(next) - lockTierScore(v);
                            return true;
                        }
                    }

                    toggleCard(card);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, active);
    }

    /** 背包格子锁定三档分值：27格=4分 / 18格=8分 / 9格=16分 */
    private static int lockTierScore(int v) {
        return v == 27 ? 4 : v == 18 ? 8 : 16;
    }

    /** 切换遭遇事件勾选状态并发送给服务端（服务端回包后以 sync 为准）。 */
    private void toggleEvent(com.survivalcontract.event.RandomEventManager.EventType ev) {
        boolean newState = !enabledEvents.contains(ev.name());
        if (newState) enabledEvents.add(ev.name()); else enabledEvents.remove(ev.name());
        ClientPlayNetworking.send(new NetworkRegistry.EventTogglePayload(ev.name(), newState));
        initialScore += newState ? ev.score : -ev.score;
    }

    /** 死亡扣分按钮文本（全局设置，读 ClientState） */
    private String deathPenaltyLabel() {
        return com.survivalcontract.client.ClientL10n.guiText("death_penalty",
                com.survivalcontract.client.ClientState.INSTANCE.deathPenalty);
    }

    /** 调整遭遇事件刷新间隔（乐观更新 ClientState + 发包；服务端全员 sync 后以 sync 为准） */
    private void adjustEventInterval(String eventId, boolean adjustMin, int delta) {
        var cs = com.survivalcontract.client.ClientState.INSTANCE;
        int[] cur = cs.getEventInterval(eventId);
        int minM = cur[0] + (adjustMin ? delta : 0);
        int maxM = cur[1] + (adjustMin ? 0 : delta);
        // A∈[0,15]、B∈[5,20]，保证 A < B（与服务端 adjustEventInterval 同一套钳制）
        minM = Math.max(0, Math.min(15, minM));
        maxM = Math.max(5, Math.min(20, maxM));
        if (minM >= maxM) minM = maxM - 1;
        cs.eventIntervals.put(eventId, new int[]{minM, maxM});
        ClientPlayNetworking.send(new NetworkRegistry.EventIntervalPayload(eventId, adjustMin, delta));
    }

    private void toggleCard(ChallengeCard card) {
        boolean newState = !enabled.contains(card.id());
        if (newState) enabled.add(card.id()); else enabled.remove(card.id());
        ClientPlayNetworking.send(new NetworkRegistry.ChallengeTogglePayload(card.id(), newState));

        if (!card.id().startsWith("rule_")) {
            initialScore += newState
                    ? weightedScore(card, customValues.getOrDefault(card.id(), card.customValue()))
                    : -weightedScore(card, customValues.getOrDefault(card.id(), card.customValue()));
        }
    }

    /** 可自定义数值挑战的加权分数：分数 = baseScore × 数值 / 50（50% 为基准，削弱越多分越高） */
    private int weightedScore(ChallengeCard card, int value) {
        if (!card.customizable() || value <= 0) return card.baseScore();
        return Math.round(card.baseScore() * value / 50.0f);
    }

    /** 调整自定义数值（±10%，范围 10%-90%），同步服务端并本地立即刷新分数 */
    private void adjustCustomValue(ChallengeCard card, int direction) {
        int cur = customValues.getOrDefault(card.id(), card.customValue());
        int next = Math.max(10, Math.min(90, cur + direction * 10));
        if (next == cur) return;
        customValues.put(card.id(), next);
        ClientPlayNetworking.send(new NetworkRegistry.ChallengeValuePayload(card.id(), next));
        // 只有已启用的卡片才影响初始分数（与服务端 calculateInitialScore 一致）
        if (enabled.contains(card.id())) {
            initialScore += weightedScore(card, next) - weightedScore(card, cur);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // 初始菜单（stage 0）：鼠标在玩家列表面板内时滚动列表
        if ("LOBBY".equalsIgnoreCase(com.survivalcontract.client.ClientState.INSTANCE.currentPhase)
                && com.survivalcontract.client.ClientState.INSTANCE.lobbyStage == 0
                && mouseX >= lobbyPanelX && mouseX <= lobbyPanelX + lobbyPanelW
                && mouseY >= lobbyPanelY && mouseY <= lobbyPanelY + lobbyPanelH) {
            int maxListScroll = Math.max(0,
                    Math.ceilDiv(lobbyListPlayers.size(), 2) * LOBBY_ROW_H - lobbyPanelH + 8);
            lobbyListScroll -= (int) (vertical * SCROLL_SPEED);
            lobbyListScroll = Math.max(0, Math.min(lobbyListScroll, maxListScroll));
            return true;
        }
        if (mouseY >= gridTop && mouseY <= gridTop + gridHeight) {
            scrollOffset -= (int) (vertical * SCROLL_SPEED);
            if (scrollOffset < 0) scrollOffset = 0;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 264) { // Down
            scrollOffset += SCROLL_SPEED;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            return true;
        }
        if (event.key() == 265) { // Up
            scrollOffset -= SCROLL_SPEED;
            if (scrollOffset < 0) scrollOffset = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
