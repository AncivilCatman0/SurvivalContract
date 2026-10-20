package com.survivalcontract.network;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.challenge.Challenge;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.game.GameStateManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

import java.util.*;

/**
 * 所有网络 payload 的注册中心。
 * <p>
 * 协议：
 * <ul>
 *   <li>客户端 → 服务端：{@link ChallengeTogglePayload}、{@link ChallengeSyncRequestPayload}、
 *       {@link ProfessionSelectPayload}、{@link OpenChallengeBookPayload}、{@link StartGamePayload}</li>
 *   <li>服务端 → 客户端：{@link ChallengeSyncPayload}、{@link ScoreSyncPayload}</li>
 * </ul>
 */
public class NetworkRegistry {

    // ============ Client → Server ============

    /** 请求打开挑战书（通常由 UseItem 事件在服务端触发，但也接受客户端显式请求） */
    public record OpenChallengeBookPayload() implements CustomPacketPayload {
        public static final Type<OpenChallengeBookPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "open_book"));
        public static final StreamCodec<FriendlyByteBuf, OpenChallengeBookPayload> CODEC =
                StreamCodec.unit(new OpenChallengeBookPayload());
        @Override public Type<OpenChallengeBookPayload> type() { return TYPE; }
    }

    /** 切换单个挑战开关 */
    public record ChallengeTogglePayload(String challengeId, boolean enabled) implements CustomPacketPayload {
        public static final Type<ChallengeTogglePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "challenge_toggle"));
        public static final StreamCodec<FriendlyByteBuf, ChallengeTogglePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.challengeId, 64); buf.writeBoolean(p.enabled); },
                        buf -> new ChallengeTogglePayload(buf.readUtf(64), buf.readBoolean()));
        @Override public Type<ChallengeTogglePayload> type() { return TYPE; }
    }

    /** 可自定义挑战的数值调节 */
    public record ChallengeValuePayload(String challengeId, int newValue) implements CustomPacketPayload {
        public static final Type<ChallengeValuePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "challenge_value"));
        public static final StreamCodec<FriendlyByteBuf, ChallengeValuePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.challengeId, 64); buf.writeInt(p.newValue); },
                        buf -> new ChallengeValuePayload(buf.readUtf(64), buf.readInt()));
        @Override public Type<ChallengeValuePayload> type() { return TYPE; }
    }

    /** 选择职业 */
    public record ProfessionSelectPayload(String professionKey) implements CustomPacketPayload {
        public static final Type<ProfessionSelectPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "profession_select"));
        public static final StreamCodec<FriendlyByteBuf, ProfessionSelectPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.professionKey, 32),
                        buf -> new ProfessionSelectPayload(buf.readUtf(32)));
        @Override public Type<ProfessionSelectPayload> type() { return TYPE; }
    }

    /** 切换遭遇事件开关（自选事件，按难度计分） */
    public record EventTogglePayload(String eventName, boolean enabled) implements CustomPacketPayload {
        public static final Type<EventTogglePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "event_toggle"));
        public static final StreamCodec<FriendlyByteBuf, EventTogglePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.eventName, 48); buf.writeBoolean(p.enabled); },
                        buf -> new EventTogglePayload(buf.readUtf(48), buf.readBoolean()));
        @Override public Type<EventTogglePayload> type() { return TYPE; }
    }

    /** 请求挑战状态同步（客户端首次打开挑战书时发） */
    public record ChallengeSyncRequestPayload() implements CustomPacketPayload {
        public static final Type<ChallengeSyncRequestPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "challenge_sync_req"));
        public static final StreamCodec<FriendlyByteBuf, ChallengeSyncRequestPayload> CODEC =
                StreamCodec.unit(new ChallengeSyncRequestPayload());
        @Override public Type<ChallengeSyncRequestPayload> type() { return TYPE; }
    }

    /** 切换游戏模式（个人/团队） */
    public record SetGameModePayload(String modeKey) implements CustomPacketPayload {
        public static final Type<SetGameModePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_mode"));
        public static final StreamCodec<FriendlyByteBuf, SetGameModePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.modeKey, 16),
                        buf -> new SetGameModePayload(buf.readUtf(16)));
        @Override public Type<SetGameModePayload> type() { return TYPE; }
    }

    /** 切换最终目标 */
    public record SetFinalGoalPayload(String goalKey) implements CustomPacketPayload {
        public static final Type<SetFinalGoalPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_final_goal"));
        public static final StreamCodec<FriendlyByteBuf, SetFinalGoalPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.goalKey, 32),
                        buf -> new SetFinalGoalPayload(buf.readUtf(32)));
        @Override public Type<SetFinalGoalPayload> type() { return TYPE; }
    }

    /** 设置游戏时长（分钟）；-1 = 无限制 */
    public record GameDurationPayload(int minutes) implements CustomPacketPayload {
        public static final Type<GameDurationPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "game_duration"));
        public static final StreamCodec<FriendlyByteBuf, GameDurationPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeInt(p.minutes),
                        buf -> new GameDurationPayload(buf.readInt()));
        @Override public Type<GameDurationPayload> type() { return TYPE; }
    }

    /** C2S：调整遭遇事件刷新间隔（isMin=true 调下限 A，否则调上限 B；delta = ±1） */
    public record EventIntervalPayload(String eventId, boolean isMin, int delta) implements CustomPacketPayload {
        public static final Type<EventIntervalPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "event_interval"));
        public static final StreamCodec<FriendlyByteBuf, EventIntervalPayload> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeUtf(p.eventId, 48); buf.writeBoolean(p.isMin); buf.writeInt(p.delta); },
                buf -> new EventIntervalPayload(buf.readUtf(48), buf.readBoolean(), buf.readInt()));
        @Override public Type<EventIntervalPayload> type() { return TYPE; }
    }

    /** C2S：设置死亡扣分（0/1/2，挑战书按钮循环切换） */
    public record SetDeathPenaltyPayload(int value) implements CustomPacketPayload {
        public static final Type<SetDeathPenaltyPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_death_penalty"));
        public static final StreamCodec<FriendlyByteBuf, SetDeathPenaltyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeInt(p.value),
                        buf -> new SetDeathPenaltyPayload(buf.readInt()));
        @Override public Type<SetDeathPenaltyPayload> type() { return TYPE; }
    }

    /** C2S：大厅初始菜单投票 — action 0 = "我决定好了"，1 = "开始游戏"（多人各需 2 名不同玩家） */
    public record LobbyVotePayload(int action) implements CustomPacketPayload {
        public static final Type<LobbyVotePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "lobby_vote"));
        public static final StreamCodec<FriendlyByteBuf, LobbyVotePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeVarInt(p.action),
                        buf -> new LobbyVotePayload(buf.readVarInt()));
        @Override public Type<LobbyVotePayload> type() { return TYPE; }
    }

    /** S2C：大厅初始菜单状态（阶段 + 两种投票的累计人数/所需人数 + 玩法/使者数量 + 自定义地形预设 + 附属玩法选项） */
    public record LobbyStatePayload(int stage, int confirmCount, int confirmNeed,
                                    int startCount, int startNeed,
                                    String playStyle, int envoyCount, String terrainPreset,
                                    Map<String, Integer> styleOptions) implements CustomPacketPayload {
        public static final Type<LobbyStatePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "lobby_state"));
        public static final StreamCodec<FriendlyByteBuf, LobbyStatePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeVarInt(p.stage); buf.writeVarInt(p.confirmCount);
                                buf.writeVarInt(p.confirmNeed); buf.writeVarInt(p.startCount); buf.writeVarInt(p.startNeed);
                                buf.writeUtf(p.playStyle, 32); buf.writeVarInt(p.envoyCount);
                                buf.writeUtf(p.terrainPreset == null ? "OVERWORLD" : p.terrainPreset, 16);
                                Map<String, Integer> opts = p.styleOptions == null ? Map.of() : p.styleOptions;
                                buf.writeVarInt(opts.size());
                                for (Map.Entry<String, Integer> e : opts.entrySet()) {
                                    buf.writeUtf(e.getKey(), 64);
                                    buf.writeVarInt(e.getValue());
                                } },
                        buf -> { int stage = buf.readVarInt(); int confirmCount = buf.readVarInt();
                                int confirmNeed = buf.readVarInt(); int startCount = buf.readVarInt();
                                int startNeed = buf.readVarInt(); String style = buf.readUtf(32);
                                int envoy = buf.readVarInt(); String terrain = buf.readUtf(16);
                                int n = buf.readVarInt(); Map<String, Integer> opts = new java.util.HashMap<>(n);
                                for (int i = 0; i < n; i++) opts.put(buf.readUtf(64), buf.readVarInt());
                                return new LobbyStatePayload(stage, confirmCount, confirmNeed,
                                        startCount, startNeed, style, envoy, terrain, opts); });
        @Override public Type<LobbyStatePayload> type() { return TYPE; }
    }

    /** 向单个玩家发送大厅初始菜单状态（开书 sync 请求/回大厅重置时调用） */
	public static void sendLobbyState(ServerPlayer player) {
		var gsm = GameStateManager.getInstance();
		int need = gsm.lobbyVoteNeeded(player.level().getServer());
		ServerPlayNetworking.send(player, new LobbyStatePayload(gsm.getLobbyStage(),
				gsm.getLobbyConfirmCount(), need, gsm.getLobbyStartCount(), need,
				gsm.getPlayStyleKey(), gsm.getEnvoyCount(), gsm.getTerrainPreset().name(),
				gsm.getStyleOptions()));
	}

    /** 全员广播大厅初始菜单状态（阶段切换/投票变化时调用） */
	public static void broadcastLobbyState(MinecraftServer server) {
		var gsm = GameStateManager.getInstance();
		int need = gsm.lobbyVoteNeeded(server);
		var pkt = new LobbyStatePayload(gsm.getLobbyStage(),
				gsm.getLobbyConfirmCount(), need, gsm.getLobbyStartCount(), need,
				gsm.getPlayStyleKey(), gsm.getEnvoyCount(), gsm.getTerrainPreset().name(),
				gsm.getStyleOptions());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(p, pkt);
		}
	}

    // ============ 玩法四选一 + 神的谎言 ============

    /** C2S：选择玩法（初始菜单：常规/神的谎言/走私犯与检察官/文明时代 + 已注册的附属玩法 id） */
    public record SetPlayStylePayload(String styleKey) implements CustomPacketPayload {
        public static final Type<SetPlayStylePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_play_style"));
        public static final StreamCodec<FriendlyByteBuf, SetPlayStylePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.styleKey, 64),
                        buf -> new SetPlayStylePayload(buf.readUtf(64)));
        @Override public Type<SetPlayStylePayload> type() { return TYPE; }
    }

    /** C2S：附属玩法二级菜单选项（styleId + option key + value；大厅阶段） */
    public record StyleOptionPayload(String styleId, String key, int value) implements CustomPacketPayload {
        public static final Type<StyleOptionPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "style_option"));
        public static final StreamCodec<FriendlyByteBuf, StyleOptionPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.styleId, 64); buf.writeUtf(p.key, 64); buf.writeVarInt(p.value); },
                        buf -> new StyleOptionPayload(buf.readUtf(64), buf.readUtf(64), buf.readVarInt()));
        @Override public Type<StyleOptionPayload> type() { return TYPE; }
    }

    /** C2S：文明时代自定义地形预设（OVERWORLD/MINER/SNOW/DESERT；大厅阶段，挑战书「自定义地形」菜单） */
    public record SetTerrainPresetPayload(String presetKey) implements CustomPacketPayload {
        public static final Type<SetTerrainPresetPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_terrain_preset"));
        public static final StreamCodec<FriendlyByteBuf, SetTerrainPresetPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.presetKey, 16),
                        buf -> new SetTerrainPresetPayload(buf.readUtf(16)));
        @Override public Type<SetTerrainPresetPayload> type() { return TYPE; }
    }

    /** C2S：神的谎言使者数量（1~4，二级菜单按钮循环切换） */
    public record SetEnvoyCountPayload(int count) implements CustomPacketPayload {
        public static final Type<SetEnvoyCountPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "set_envoy_count"));
        public static final StreamCodec<FriendlyByteBuf, SetEnvoyCountPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeVarInt(p.count),
                        buf -> new SetEnvoyCountPayload(buf.readVarInt()));
        @Override public Type<SetEnvoyCountPayload> type() { return TYPE; }
    }

    /** C2S：请求打开指认书（服务端校验身份/淘汰状态后下发存活玩家清单） */
    public record OpenAccuseBookPayload() implements CustomPacketPayload {
        public static final Type<OpenAccuseBookPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "open_accuse_book"));
        public static final StreamCodec<FriendlyByteBuf, OpenAccuseBookPayload> CODEC =
                StreamCodec.unit(new OpenAccuseBookPayload());
        @Override public Type<OpenAccuseBookPayload> type() { return TYPE; }
    }

    /** S2C：指认书状态（身份、可用次数、当前未被淘汰的所有玩家） */
    public record AccuseBookStatePayload(boolean isEnvoy, int charges,
                                         List<BookPlayerEntry> players) implements CustomPacketPayload {
        public record BookPlayerEntry(java.util.UUID id, String name) {}
        public static final Type<AccuseBookStatePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "accuse_book_state"));
        public static final StreamCodec<FriendlyByteBuf, AccuseBookStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.isEnvoy);
                    buf.writeVarInt(p.charges);
                    buf.writeVarInt(p.players.size());
                    for (BookPlayerEntry e : p.players) {
                        buf.writeUUID(e.id());
                        buf.writeUtf(e.name(), 32);
                    }
                },
                buf -> {
                    boolean envoy = buf.readBoolean();
                    int charges = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<BookPlayerEntry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(new BookPlayerEntry(buf.readUUID(), buf.readUtf(32)));
                    return new AccuseBookStatePayload(envoy, charges, list);
                });
        @Override public Type<AccuseBookStatePayload> type() { return TYPE; }
    }

    /** C2S：平民指认（第六十九批：命中使者 → 对方 -200 分；未命中 → 自己 -200 分） */
    public record AccuseActionPayload(java.util.UUID target) implements CustomPacketPayload {
        public static final Type<AccuseActionPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "accuse_action"));
        public static final StreamCodec<FriendlyByteBuf, AccuseActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.target),
                buf -> new AccuseActionPayload(buf.readUUID()));
        @Override public Type<AccuseActionPayload> type() { return TYPE; }
    }

    /** C2S：使者降罪（指定一名玩家扣 55 分，系统提示仅显示被扣分者） */
    public record EnvoyDeductPayload(java.util.UUID target) implements CustomPacketPayload {
        public static final Type<EnvoyDeductPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "envoy_deduct"));
        public static final StreamCodec<FriendlyByteBuf, EnvoyDeductPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.target),
                buf -> new EnvoyDeductPayload(buf.readUUID()));
        @Override public Type<EnvoyDeductPayload> type() { return TYPE; }
    }

    // ============ 神谕纸符（第五十九批：神的谎言 A/S 任务奖励消耗道具） ============

    /** C2S：使用神谕纸符（平民=武装神罚免疫；使者=服务端下发链接选人清单） */
    public record UseRelicPayload() implements CustomPacketPayload {
        public static final Type<UseRelicPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "use_relic"));
        public static final StreamCodec<FriendlyByteBuf, UseRelicPayload> CODEC =
                StreamCodec.unit(new UseRelicPayload());
        @Override public Type<UseRelicPayload> type() { return TYPE; }
    }

    // ============ 任务刷新券（神的谎言：开局每人 1 个，右键立即刷新当前专属任务） ============

    /** C2S：使用任务刷新券（服务端复验背包持有 → 消耗 1 个并重掷任务；失败返还） */
    public record UseTaskRefreshPayload() implements CustomPacketPayload {
        public static final Type<UseTaskRefreshPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "use_task_refresh"));
        public static final StreamCodec<FriendlyByteBuf, UseTaskRefreshPayload> CODEC =
                StreamCodec.unit(new UseTaskRefreshPayload());
        @Override public Type<UseTaskRefreshPayload> type() { return TYPE; }
    }

    /** S2C：纸符链接选人清单（使者使用纸符后，服务端校验通过 → 下发当前存活玩家） */
    public record RelicLinkStatePayload(List<AccuseBookStatePayload.BookPlayerEntry> players)
            implements CustomPacketPayload {
        public static final Type<RelicLinkStatePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "relic_link_state"));
        public static final StreamCodec<FriendlyByteBuf, RelicLinkStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.players.size());
                    for (AccuseBookStatePayload.BookPlayerEntry e : p.players) {
                        buf.writeUUID(e.id());
                        buf.writeUtf(e.name(), 32);
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    List<AccuseBookStatePayload.BookPlayerEntry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++)
                        list.add(new AccuseBookStatePayload.BookPlayerEntry(buf.readUUID(), buf.readUtf(32)));
                    return new RelicLinkStatePayload(list);
                });
        @Override public Type<RelicLinkStatePayload> type() { return TYPE; }
    }

    /** C2S：使者确认链接一名玩家（服务端复验后消耗纸符并记录链接） */
    public record ConfirmRelicLinkPayload(java.util.UUID target) implements CustomPacketPayload {
        public static final Type<ConfirmRelicLinkPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "confirm_relic_link"));
        public static final StreamCodec<FriendlyByteBuf, ConfirmRelicLinkPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUUID(p.target),
                buf -> new ConfirmRelicLinkPayload(buf.readUUID()));
        @Override public Type<ConfirmRelicLinkPayload> type() { return TYPE; }
    }

    // ============ 走私犯与检察官（PlayStyle.STOWAWAY） ============

    /** C2S：请求打开检查发起书（服务端校验后按状态下发：发起模式玩家清单 / 决策模式贿赂总价 / 等待模式） */
    public record OpenProsecutePayload() implements CustomPacketPayload {
        public static final Type<OpenProsecutePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "open_prosecute"));
        public static final StreamCodec<FriendlyByteBuf, OpenProsecutePayload> CODEC =
                StreamCodec.unit(new OpenProsecutePayload());
        @Override public Type<OpenProsecutePayload> type() { return TYPE; }
    }

    /**
     * S2C：检查发起书状态。
     * pendingPhase=false → 发起模式（选一名非友方玩家作为自己的检察官）；
     * pendingPhase=true → 检查进行中：bribeTotal≥0 = 决策模式（我是检察官，显示对方贿赂袋价值）；
     * bribeTotal=-1 = 等待模式（我是被检查者，等待检察官定夺）。无总分提示区间。
     */
    public record ProsecuteStatePayload(boolean pendingPhase, int bribeTotal,
                                        List<ProsecuteEntry> players) implements CustomPacketPayload {
        public record ProsecuteEntry(java.util.UUID id, String name, boolean friend) {}
        public static final Type<ProsecuteStatePayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "prosecute_state"));
        public static final StreamCodec<FriendlyByteBuf, ProsecuteStatePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.pendingPhase);
                    buf.writeVarInt(p.bribeTotal);
                    buf.writeVarInt(p.players.size());
                    for (ProsecuteEntry e : p.players) {
                        buf.writeUUID(e.id());
                        buf.writeUtf(e.name(), 32);
                        buf.writeBoolean(e.friend());
                    }
                },
                buf -> {
                    boolean pending = buf.readBoolean();
                    int bribe = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<ProsecuteEntry> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(new ProsecuteEntry(buf.readUUID(), buf.readUtf(32), buf.readBoolean()));
                    return new ProsecuteStatePayload(pending, bribe, list);
                });
        @Override public Type<ProsecuteStatePayload> type() { return TYPE; }
    }

    /**
     * C2S：检查动作。发起阶段：target=选定的检察官（action 忽略）；
     * 决策阶段：target=被检查者，action 0=受贿 / 1=进行检查。
     */
    public record ProsecuteActionPayload(java.util.UUID target, int action) implements CustomPacketPayload {
        public static final Type<ProsecuteActionPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "prosecute_action"));
        public static final StreamCodec<FriendlyByteBuf, ProsecuteActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUUID(p.target);
                    buf.writeVarInt(p.action);
                },
                buf -> new ProsecuteActionPayload(buf.readUUID(), buf.readVarInt()));
        @Override public Type<ProsecuteActionPayload> type() { return TYPE; }
    }

    /** C2S：打开自己的收纳袋（bribe=true 贿赂收纳袋 / false 价值收纳袋） */
    public record OpenBagPayload(boolean bribe) implements CustomPacketPayload {
        public static final Type<OpenBagPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "open_bag"));
        public static final StreamCodec<FriendlyByteBuf, OpenBagPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.bribe),
                buf -> new OpenBagPayload(buf.readBoolean()));
        @Override public Type<OpenBagPayload> type() { return TYPE; }
    }

    /**
     * S2C：收纳袋界面已打开（bribe=true 贿赂袋）。服务端 openMenu 发界面包后紧随发送本包；
     * 客户端记录/清除贿赂袋菜单引用（客户端菜单底层容器是原版新建的裸 SimpleContainer，
     * 禁放走私品的客户端识别依据——第六十六批）。
     */
    public record BagOpenPayload(boolean bribe) implements CustomPacketPayload {
        public static final Type<BagOpenPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "bag_open"));
        public static final StreamCodec<FriendlyByteBuf, BagOpenPayload> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeBoolean(p.bribe),
                buf -> new BagOpenPayload(buf.readBoolean()));
        @Override public Type<BagOpenPayload> type() { return TYPE; }
    }

    /** S2C：价值物品清单 sync（own = 己方三件（分值）；enemy = 团队模式对方三件（仅一件可见，无分值）） */
    public record SmuggleSyncPayload(List<SmuggleEntry> own, List<SmuggleEntry> enemy,
                                     int batchRemainSeconds) implements CustomPacketPayload {
        /** itemId 为空字符串 = 问号（未知物品）；score 仅己方条目有效 */
        public record SmuggleEntry(String itemId, int score) {}
        public static final Type<SmuggleSyncPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "smuggle_sync"));
        public static final StreamCodec<FriendlyByteBuf, SmuggleSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    writeEntries(buf, p.own);
                    writeEntries(buf, p.enemy);
                    buf.writeVarInt(p.batchRemainSeconds);
                },
                buf -> new SmuggleSyncPayload(readEntries(buf), readEntries(buf), buf.readVarInt()));
        private static void writeEntries(FriendlyByteBuf buf, List<SmuggleEntry> list) {
            buf.writeVarInt(list.size());
            for (SmuggleEntry e : list) {
                buf.writeUtf(e.itemId(), 64);
                buf.writeVarInt(e.score());
            }
        }
        private static List<SmuggleEntry> readEntries(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            List<SmuggleEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new SmuggleEntry(buf.readUtf(64), buf.readVarInt()));
            return list;
        }
        @Override public Type<SmuggleSyncPayload> type() { return TYPE; }
    }

    /** 专属任务 sync（服务端拼好显示文本：名称+进度+剩余时间） */
    public record TaskSyncPayload(net.minecraft.network.chat.Component taskText) implements CustomPacketPayload {
        public static final Type<TaskSyncPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "task_sync"));
        public static final StreamCodec<FriendlyByteBuf, TaskSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
                        .encode(buf, p.taskText()),
                buf -> new TaskSyncPayload(net.minecraft.network.chat.ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC
                        .decode(buf)));
        @Override public Type<TaskSyncPayload> type() { return TYPE; }
    }

    /** 双禁：选一条规则强制进入对面队伍（仅团队模式，每队一条，不能重复） */
    public record DoubleBanPayload(String challengeId) implements CustomPacketPayload {
        public static final Type<DoubleBanPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "double_ban"));
        public static final StreamCodec<FriendlyByteBuf, DoubleBanPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.challengeId, 64),
                        buf -> new DoubleBanPayload(buf.readUtf(64)));
        @Override public Type<DoubleBanPayload> type() { return TYPE; }
    }

    /** 开始游戏 */
	public record StartGamePayload() implements CustomPacketPayload {
		public static final Type<StartGamePayload> TYPE = new Type<>(
				Identifier.fromNamespaceAndPath("survival-contract", "start_game"));
		public static final StreamCodec<FriendlyByteBuf, StartGamePayload> CODEC =
				StreamCodec.unit(new StartGamePayload());
		@Override public Type<StartGamePayload> type() { return TYPE; }
	}

	/** 请求打开团队箱（快捷键） */
	public record OpenTeamChestPayload() implements CustomPacketPayload {
		public static final Type<OpenTeamChestPayload> TYPE = new Type<>(
				Identifier.fromNamespaceAndPath("survival-contract", "open_team_chest"));
		public static final StreamCodec<FriendlyByteBuf, OpenTeamChestPayload> CODEC =
				StreamCodec.unit(new OpenTeamChestPayload());
		@Override public Type<OpenTeamChestPayload> type() { return TYPE; }
	}

    // ============ Server → Client ============

    /**
     * 服务端发给客户端的完整挑战状态快照。
     * 格式：
     *   int 挑战总数 N
     *   对每个挑战：string id, bool enabled, int customValue
     *   string professionKey
     *   int initialScore (由已启用挑战的 baseScore 累加得到)
     *   string currentGameMode (solo / teams)
     *   string finalGoalKey
     *   string phase (LOBBY/PLAYING/FINISHED)
     *   int remainingSeconds
     *   string myTeamBan（我方队伍已双禁的挑战 id，"" = 未选）
     *   string enemyBan（对面队伍已双禁的挑战 id，"" = 未选）
     */
    public record ChallengeSyncPayload(List<Entry> entries, String professionKey, int initialScore,
                                       String currentGameMode, String finalGoalKey,
                                       String phase, int remainingSeconds,
                                       int freezeSeconds,
                                       int gameDurationMinutes,
                                       List<String> enabledEvents,
                                       String myTeamBan, String enemyBan,
                                       int deathPenalty, List<String> eventIntervals) implements CustomPacketPayload {
        public static final Type<ChallengeSyncPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "challenge_sync"));
        public static final StreamCodec<FriendlyByteBuf, ChallengeSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.entries.size());
                    for (Entry e : p.entries) {
                        buf.writeUtf(e.challengeId, 64);
                        buf.writeBoolean(e.enabled);
                        buf.writeInt(e.customValue);
                        buf.writeUtf(e.displayName, 64);
                        buf.writeUtf(e.description, 256);
                        buf.writeInt(e.baseScore);
                        buf.writeInt(e.penaltyScore);
                        buf.writeBoolean(e.customizable);
                        buf.writeUtf(e.iconItemId, 64);
                        buf.writeUtf(e.section == null ? "contract" : e.section, 16);
                    }
                    buf.writeUtf(p.professionKey, 32);
                    buf.writeInt(p.initialScore);
                    buf.writeUtf(p.currentGameMode, 16);
                    buf.writeUtf(p.finalGoalKey, 32);
                    buf.writeUtf(p.phase, 16);
                    buf.writeInt(p.remainingSeconds);
                    buf.writeInt(p.freezeSeconds);
                    buf.writeInt(p.enabledEvents.size());
                    for (String ev : p.enabledEvents) {
                        buf.writeUtf(ev, 48);
                    }
                    buf.writeUtf(p.myTeamBan, 64);
                    buf.writeUtf(p.enemyBan, 64);
                    buf.writeInt(p.gameDurationMinutes);
                    buf.writeInt(p.deathPenalty);
                    buf.writeInt(p.eventIntervals.size());
                    for (String s : p.eventIntervals) {
                        buf.writeUtf(s, 64);
                    }
                },
                buf -> {
                    int n = buf.readInt();
                    List<Entry> entries = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        entries.add(new Entry(buf.readUtf(64), buf.readBoolean(), buf.readInt(),
                                buf.readUtf(64), buf.readUtf(256), buf.readInt(), buf.readInt(),
                                buf.readBoolean(), buf.readUtf(64), buf.readUtf(16)));
                    }
                    String prof = buf.readUtf(32);
                    int score = buf.readInt();
                    String mode = buf.readUtf(16);
                    String goal = buf.readUtf(32);
                    String phase = buf.readUtf(16);
                    int remain = buf.readInt();
                    int freeze = buf.readInt();
                    int evCount = buf.readInt();
                    List<String> events = new ArrayList<>(evCount);
                    for (int i = 0; i < evCount; i++) {
                        events.add(buf.readUtf(48));
                    }
                    String myBan = buf.readUtf(64);
                    String enemyBan = buf.readUtf(64);
                    int durationMinutes = buf.readInt();
                    int deathPenalty = buf.readInt();
                    int ivCount = buf.readInt();
                    List<String> intervals = new ArrayList<>(ivCount);
                    for (int i = 0; i < ivCount; i++) {
                        intervals.add(buf.readUtf(64));
                    }
                    return new ChallengeSyncPayload(entries, prof, score, mode, goal, phase, remain, freeze,
                            durationMinutes, events, myBan, enemyBan, deathPenalty, intervals);
                });

        @Override public Type<ChallengeSyncPayload> type() { return TYPE; }

        /**
         * 单个挑战条目。携带完整显示数据（中文名/描述/分数/图标）：
         * 纯客户端的本地 ChallengeManager 注册表从未被填充（registerAllChallenges 仅在服务端
         * onServerStart 执行），卡片必须以服务端 sync 的数据为准，否则其他玩家的书里没有规则卡片。
         */
        public record Entry(String challengeId, boolean enabled, int customValue,
                            String displayName, String description, int baseScore, int penaltyScore,
                            boolean customizable, String iconItemId, String section) {}
    }

    /** 计分板同步 */
    public record ScoreSyncPayload(List<ScoreEntry> scores) implements CustomPacketPayload {
        public static final Type<ScoreSyncPayload> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("survival-contract", "score_sync"));
        public static final StreamCodec<FriendlyByteBuf, ScoreSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeInt(p.scores.size());
                    for (ScoreEntry e : p.scores) {
                        buf.writeUtf(e.playerName, 64);
                        buf.writeInt(e.score);
                        buf.writeUtf(e.team.name(), 8);
                    }
                },
                buf -> {
                    int n = buf.readInt();
                    List<ScoreEntry> scores = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        scores.add(new ScoreEntry(buf.readUtf(64), buf.readInt(),
                                GameStateManager.Team.valueOf(buf.readUtf(8))));
                    }
                    return new ScoreSyncPayload(scores);
                });

        @Override public Type<ScoreSyncPayload> type() { return TYPE; }

        public record ScoreEntry(String playerName, int score, GameStateManager.Team team) {}
    }

    // ============ 共享业务逻辑（接收器与服务端自测 ServerSelfTest 共用同一条代码路径） ============

    /**
     * 处理一次挑战开关：
     * <ul>
     *   <li>双禁锁定：被对方队伍"双禁"进来的规则，受队成员不能自行关闭</li>
     *   <li>团队模式：挑战规则同队互通（点亮状态同步到全部同队成员）</li>
     *   <li>rule_* 规则开关：更新全局状态并应用 gamerule（PVP / 保留物品栏）</li>
     *   <li>所有受影响玩家立即回传 sync（初始分数同步刷新）</li>
     * </ul>
     */
    public static void applyChallengeToggle(ServerPlayer player, String challengeId, boolean enabled) {
        GameStateManager gsm = GameStateManager.getInstance();

        // 双禁锁定：该规则被对面队伍"双禁"进来后，受队成员不能自行关闭
        if (!enabled && gsm.isTeamMode()) {
            var myTeam = gsm.getPlayerTeam(player.getUUID());
            for (var ban : gsm.getTeamBans().entrySet()) {
                if (!ban.getValue().equals(challengeId)) continue;
                GameStateManager.Team lockedTeam = ban.getKey() == GameStateManager.Team.RED
                        ? GameStateManager.Team.BLUE : GameStateManager.Team.RED;
                if (myTeam == lockedTeam) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.double_ban_rule_locked"));
                    sendSync(player);
                    return;
                }
            }
        }

        java.util.List<ServerPlayer> affected = new ArrayList<>();
        affected.add(player);
        // 疾病上限（第五十四批）：单个玩家身上最多同时存在三种疾病——开启疾病规则前校验
        // （开启者规则集即受影响集合：团队模式同队共享一套，走私犯全员共享一套）
        if (enabled) {
            Challenge disCh = ChallengeManager.getInstance().getChallenge(challengeId);
            if (disCh != null && "disease".equals(disCh.getSection())
                    && !ChallengeManager.getInstance().getPlayerChallenges(player.getUUID()).contains(challengeId)) {
                int diseaseCnt = 0;
                for (String rid : ChallengeManager.getInstance().getPlayerChallenges(player.getUUID())) {
                    var dc = ChallengeManager.getInstance().getChallenge(rid);
                    if (dc != null && "disease".equals(dc.getSection())) diseaseCnt++;
                }
                if (diseaseCnt >= 3) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.disease_max"));
                    sendSync(player);
                    return;
                }
            }
        }
        // 走私犯与检察官：所有玩家规则共享（任何玩家点亮/关闭 → 全员同步生效）
        if (gsm.isStowaway()) {
            for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
                if (gsm.isLobbySpectator(p.getUUID())) continue;
                if (enabled) {
                    ChallengeManager.getInstance().enableChallenge(p.getUUID(), challengeId);
                } else {
                    ChallengeManager.getInstance().disableChallenge(p.getUUID(), challengeId);
                }
                if (!affected.contains(p)) affected.add(p);
            }
        }
        // 团队模式：挑战规则同队互通（所有人共享同一套点亮规则）
        if (gsm.isTeamMode()) {
            var myTeam = gsm.getPlayerTeam(player.getUUID());
            if (myTeam == GameStateManager.Team.RED || myTeam == GameStateManager.Team.BLUE) {
                for (java.util.UUID pid : gsm.getTeamMembers(myTeam)) {
                    if (enabled) {
                        ChallengeManager.getInstance().enableChallenge(pid, challengeId);
                    } else {
                        ChallengeManager.getInstance().disableChallenge(pid, challengeId);
                    }
                }
                for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
                    if (gsm.getPlayerTeam(p.getUUID()) == myTeam && !affected.contains(p)) {
                        affected.add(p);
                    }
                }
            }
        }

        // 环境恶化板块：所有玩家共享（等同 rule_ 全局开关）——不进个人挑战集（避免计入初始分）
        Challenge secCh = ChallengeManager.getInstance().getChallenge(challengeId);
        if (secCh != null && "environment".equals(secCh.getSection())) {
            gsm.setRuleActive(challengeId, enabled);
            // 开启即加分/关闭即扣分：环境规则全员共享，所有玩家实时加减对应分值
            // 文明时代：环境/事件选项无分数（跳过加减）
            int bonus = secCh.getBaseScore();
            if (bonus != 0 && !gsm.isCivilization()) {
                for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
                    if (enabled) GameStateManager.getInstance().addScore(p.getUUID(), bonus);
                    else GameStateManager.getInstance().deductScore(p.getUUID(), bonus);
                }
            }
            for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) sendSync(p);
            return;
        }

        if (enabled) {
            ChallengeManager.getInstance().enableChallenge(player.getUUID(), challengeId);
        } else {
            ChallengeManager.getInstance().disableChallenge(player.getUUID(), challengeId);
        }
        // 规则开关：更新全局状态并立即应用 gamerule（PVP / 保留物品栏）
        if (challengeId.startsWith("rule_")) {
            gsm.setRuleActive(challengeId, enabled);
            applyRuleGamerules(player.level().getServer(), challengeId, enabled);
            // 规则开关是全局设置（等同模式/最终目标）：所有玩家同步点亮/熄灭
            for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) sendSync(p);
            return;
        }
        // 受影响的所有人（团队模式 = 全队）立即回传 sync，初始分数同步刷新
        for (ServerPlayer p : affected) sendSync(p);
    }

    /**
     * 处理一次双禁：仅团队模式 + 大厅阶段可用。
     * 将 challengeId 强制进入对面队伍的全部成员（无分，违规照常扣分），每队一条、不能重复。
     * 支持遭遇事件：id 以 "event:" 前缀标识（如 "event:LIGHTNING"），
     * 事件不进全局 enabledEvents，由 RandomEventManager 定向只对被禁队伍成员触发。
     */
    public static void applyDoubleBan(ServerPlayer player, String challengeId) {
        GameStateManager gsm = GameStateManager.getInstance();
        // 仅团队模式 + 大厅阶段可用
        if (!gsm.isTeamMode() || gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) {
            sendSync(player);
            return;
        }
        // 事件双禁：校验事件名合法
        if (challengeId.startsWith("event:")) {
            try {
                com.survivalcontract.event.RandomEventManager.EventType.valueOf(challengeId.substring(6));
            } catch (IllegalArgumentException e) {
                SurvivalContract.LOGGER.warn("Double ban: unknown event '{}'", challengeId);
                sendSync(player);
                return;
            }
        }
        // 防重复：该词条已被任一队选中 → 拒绝；我方队伍已选过 → 拒绝（每队仅一条，禁止覆盖）
        boolean dup = gsm.getTeamBans().containsValue(challengeId);
        GameStateManager.Team chooserTeam = gsm.getPlayerTeam(player.getUUID());
        boolean alreadyChosen = chooserTeam != null
                && gsm.getTeamBans().containsKey(chooserTeam);
        if (!dup && !alreadyChosen) {
            GameStateManager.Team myTeam = chooserTeam;
            if (myTeam == GameStateManager.Team.RED || myTeam == GameStateManager.Team.BLUE) {
                GameStateManager.Team enemy = myTeam == GameStateManager.Team.RED
                        ? GameStateManager.Team.BLUE : GameStateManager.Team.RED;
                gsm.setTeamBan(myTeam, challengeId);
                if (challengeId.startsWith("event:")) {
                    // 遭遇事件：定向触发，无需点亮（不进全局 enabledEvents，无分数）
                    SurvivalContract.LOGGER.info("Double ban: team {} banned event '{}' into team {}",
                            myTeam, challengeId.substring(6), enemy);
                } else {
                    // 强制对面队全部成员启用该规则（无分，违规照常扣分）
                    for (java.util.UUID pid : gsm.getTeamMembers(enemy)) {
                        ChallengeManager.getInstance().enableChallenge(pid, challengeId);
                    }
                    SurvivalContract.LOGGER.info("Double ban: team {} banned '{}' into team {}",
                            myTeam, challengeId, enemy);
                }
            }
        }
        for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) sendSync(p);
    }

    /**
     * 处理一次遭遇事件开关（自选事件，开启即加分）。
     * 被双禁的事件（teamBans 中含 "event:"+name）不允许任何队伍手动开关 —— 它只定向触发给被禁队伍。
     */
    public static void applyEventToggle(ServerPlayer player, String eventName, boolean enabled) {
        GameStateManager gsm = GameStateManager.getInstance();
        // 只允许在 LOBBY 阶段改选事件
        if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("survival-contract.msg.no_change_event_in_game"));
            return;
        }
        // 双禁锁定：已被任一队双禁的事件不可手动更改
        if (gsm.getTeamBans().containsValue("event:" + eventName)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("survival-contract.msg.event_double_banned"));
            sendSync(player);
            return;
        }
        try {
            com.survivalcontract.event.RandomEventManager.EventType.valueOf(eventName);
            // 个人模式：每玩家独立选择、独立触发；团队模式：全局开关（队伍互通，原行为）
            gsm.applyPlayerEventToggle(player.getUUID(), eventName, enabled);
        } catch (IllegalArgumentException e) {
            SurvivalContract.LOGGER.warn("Unknown event: {}", eventName);
        }
        sendSync(player);
    }

    /**
     * 处理一次职业选择（自测可直接调用；调用方负责 sendSync）。
     * <ul>
     *   <li>走私犯与检察官：锁定无职业，拒绝选择（强制回到 NONE）</li>
     *   <li>文明模式：愚者职业被禁用（第五十二批），强制回到 NONE</li>
     * </ul>
     */
    public static void applyProfessionSelect(ServerPlayer player, String professionKey) {
        var pm = com.survivalcontract.profession.ProfessionManager.getInstance();
        // 走私犯与检察官：锁定无职业，拒绝选择（强制回到 NONE）
        if (GameStateManager.getInstance().isStowaway()) {
            pm.setPlayerProfession(player.getUUID(),
                    com.survivalcontract.profession.ProfessionManager.Profession.NONE);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "survival-contract.msg.stowaway_profession_locked"));
            return;
        }
        // 文明模式：愚者职业被禁用（第五十二批）
        if (GameStateManager.getInstance().isCivilization()
                && "JESTER".equalsIgnoreCase(professionKey)) {
            pm.setPlayerProfession(player.getUUID(),
                    com.survivalcontract.profession.ProfessionManager.Profession.NONE);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "survival-contract.msg.civ_jester_disabled"));
            return;
        }
        try {
            var prof = com.survivalcontract.profession.ProfessionManager.Profession.valueOf(
                    professionKey.toUpperCase());
            pm.setPlayerProfession(player.getUUID(), prof);
        } catch (IllegalArgumentException e) {
            SurvivalContract.LOGGER.warn("Unknown profession: {}", professionKey);
        }
    }

    // ============ Registration ============

    /** 将规则开关应用到所有维度的 gamerule（26.2 gamerule 是按维度的）。 */
    private static void applyRuleGamerules(MinecraftServer server, String ruleId, boolean enabled) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            var rules = level.getGameRules();
            switch (ruleId) {
                case "rule_pvp" -> rules.set(net.minecraft.world.level.gamerules.GameRules.PVP, enabled, server);
                case "rule_keep_inventory" -> rules.set(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY, enabled, server);
                default -> {}
            }
        }
    }

    /** 将挑战书里的全部全局规则开关应用到所有维度 gamerule（startGame 生成新世界后必须调用 —— 新维度规则是默认值）。 */
    public static void applyAllRuleGamerules(MinecraftServer server) {
        GameStateManager gsm = GameStateManager.getInstance();
        applyRuleGamerules(server, "rule_pvp", gsm.isRuleActive("rule_pvp"));
        applyRuleGamerules(server, "rule_keep_inventory", gsm.isRuleActive("rule_keep_inventory"));
    }

    // ============ 玩法切换（大厅任何人可改；对局进行中仅管理员可改） ============

    /** 当前阶段是否允许切换玩法（大厅 / 对局进行中） */
    public static boolean isPlayStyleSwitchPhase() {
        var phase = GameStateManager.getInstance().getCurrentPhase();
        return phase == GameStateManager.GamePhase.LOBBY
                || phase == GameStateManager.GamePhase.PLAYING;
    }

    /**
     * 该玩家此刻是否可以切换玩法：
     * 大厅阶段任何人可改；对局进行中仅管理员（GameMaster 权限）可改；其余阶段（结算中）不可改。
     */
    public static boolean canSwitchPlayStyle(ServerPlayer player) {
        var phase = GameStateManager.getInstance().getCurrentPhase();
        if (phase == GameStateManager.GamePhase.LOBBY) return true;
        if (phase != GameStateManager.GamePhase.PLAYING) return false;
        if (player == null) return true; // 控制台/命令源由指令的 .requires 校验
        try {
            return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 应用玩法切换（大厅与对局进行中共用；权限由调用方/precondition 校验）。
     * <p>对局进行中切到附属玩法 → 立即调用其 {@code onGameStart}（固定地图出生点/套餐初始化）；
     * 切离附属玩法 → 调用其 {@code onGameEnd} 清理运行时状态。
     *
     * @return true = 已切换；false = 被拒绝（附提示）
     */
    public static boolean applyPlayStyle(MinecraftServer server, ServerPlayer player, String styleKey) {
        if (server == null) return false;
        var gsm = GameStateManager.getInstance();
        String raw = styleKey == null ? "" : styleKey;
        boolean playing = gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING;

        // ---- 1) 先校验目标玩法在当前游戏模式下是否允许（避免"先清理旧玩法再发现拒绝"）----
        var addon = com.survivalcontract.api.PlayStyleRegistry.byId(raw);
        String key = raw.toUpperCase();
        if (addon == null) {
            if ("GODS_LIES".equals(key) && gsm.getGameMode() != GameStateManager.GameMode.SOLO) {
                notifyPlayer(player, Component.translatable("survival-contract.msg.godslies_solo_only"));
                return false;
            }
            if ("CIVILIZATION".equals(key) && gsm.getGameMode() != GameStateManager.GameMode.TEAMS) {
                notifyPlayer(player, Component.translatable("survival-contract.msg.civ_team_only"));
                return false;
            }
        } else if (addon.forceTeamMode() && gsm.getGameMode() != GameStateManager.GameMode.TEAMS) {
            notifyPlayer(player, Component.translatable("survival-contract.msg.addon_team_only",
                    Component.translatable(addon.displayNameKey(), addon.displayNameFallback())));
            return false;
        }

        // ---- 2) 离开旧的附属玩法（目标不是同一个附属玩法时）----
        var previous = gsm.getAddonProvider();
        if (previous != null && (addon == null || !previous.id().equalsIgnoreCase(addon.id()))) {
            previous.onGameEnd(server);
        }

        // ---- 3) 应用 ----
        if (addon != null) {
            gsm.selectAddonStyle(addon.id());
            gsm.resetGodsLies();
            // 无最终目标玩法：时长必须有限（时间结束分数高者获胜）
            if (gsm.getGameDurationMinutes() < 0) gsm.setGameDurationMinutes(120);
            gsm.clearStyleOptions();
            for (var opt : addon.options()) gsm.setStyleOption(opt.key(), opt.defaultValue());
            String addonName = Component.translatable(addon.displayNameKey(), addon.displayNameFallback()).getString();
            broadcastAll(server, Component.translatable("survival-contract.msg.playstyle_switched", addonName));
            if (playing) startAddonMidGame(server, gsm, addon);
        } else {
            switch (key) {
                case "GODS_LIES" -> {
                    gsm.setPlayStyle(GameStateManager.PlayStyle.GODS_LIES);
                    gsm.resetGodsLies();
                    gsm.setEnvoyCount(1);
                    broadcastAll(server, Component.translatable("survival-contract.msg.playstyle_godslies"));
                }
                case "STOWAWAY" -> {
                    gsm.setPlayStyle(GameStateManager.PlayStyle.STOWAWAY);
                    gsm.resetGodsLies();
                    // 走私犯模式时长必须有限（时间结束分数高者获胜）——无限制切回 120 分钟
                    if (gsm.getGameDurationMinutes() < 0) gsm.setGameDurationMinutes(120);
                    broadcastAll(server, Component.translatable("survival-contract.msg.playstyle_stowaway"));
                }
                case "CIVILIZATION" -> {
                    gsm.setPlayStyle(GameStateManager.PlayStyle.CIVILIZATION);
                    gsm.resetGodsLies();
                    if (gsm.getGameDurationMinutes() < 0) gsm.setGameDurationMinutes(120);
                    broadcastAll(server, Component.translatable("survival-contract.msg.playstyle_civilization"));
                }
                default -> {
                    gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
                    gsm.resetGodsLies();
                    broadcastAll(server, Component.translatable("survival-contract.msg.playstyle_normal"));
                }
            }
        }

        syncAfterStyleSwitch(server, gsm);
        return true;
    }

    /** 对局进行中切到附属玩法：补齐队伍 → 职业锁定为「无」→ 交给附属玩法初始化（传送/套餐） */
    private static void startAddonMidGame(MinecraftServer server, GameStateManager gsm,
                                          com.survivalcontract.api.ScPlayStyleProvider addon) {
        gsm.setCurrentFinalGoal(null); // 无最终目标
        if (gsm.isTeamMode()) {
            com.survivalcontract.team.TeamManager.getInstance().assignMissing(server);
        }
        java.util.List<ServerPlayer> participants = new java.util.ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue; // 观战者不参战
            com.survivalcontract.profession.ProfessionManager.getInstance()
                    .setPlayerProfession(p.getUUID(),
                            com.survivalcontract.profession.ProfessionManager.Profession.NONE);
            participants.add(p);
        }
        addon.onGameStart(server, participants);
        SurvivalContract.LOGGER.info("Addon play style '{}' started mid-game with {} participants.",
                addon.id(), participants.size());
    }

    /** 切换后同步：全员 sendSync（大厅阶段再补一份大厅状态）+ 计分板刷新 */
    private static void syncAfterStyleSwitch(MinecraftServer server, GameStateManager gsm) {
        boolean lobby = gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendSync(p);
            if (lobby) sendLobbyState(p);
        }
        broadcastScores(server);
    }

    private static void notifyPlayer(ServerPlayer player, Component msg) {
        if (player != null) player.sendSystemMessage(msg);
    }

    private static void broadcastAll(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    public static void register() {
        SurvivalContract.LOGGER.info("Registering networking payloads...");

        // C2S
        PayloadTypeRegistry.serverboundPlay().register(OpenChallengeBookPayload.TYPE, OpenChallengeBookPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChallengeTogglePayload.TYPE, ChallengeTogglePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChallengeValuePayload.TYPE, ChallengeValuePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ProfessionSelectPayload.TYPE, ProfessionSelectPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EventTogglePayload.TYPE, EventTogglePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChallengeSyncRequestPayload.TYPE, ChallengeSyncRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetGameModePayload.TYPE, SetGameModePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetFinalGoalPayload.TYPE, SetFinalGoalPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GameDurationPayload.TYPE, GameDurationPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EventIntervalPayload.TYPE, EventIntervalPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetDeathPenaltyPayload.TYPE, SetDeathPenaltyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DoubleBanPayload.TYPE, DoubleBanPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StartGamePayload.TYPE, StartGamePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenTeamChestPayload.TYPE, OpenTeamChestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LobbyVotePayload.TYPE, LobbyVotePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetPlayStylePayload.TYPE, SetPlayStylePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetTerrainPresetPayload.TYPE, SetTerrainPresetPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StyleOptionPayload.TYPE, StyleOptionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetEnvoyCountPayload.TYPE, SetEnvoyCountPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenAccuseBookPayload.TYPE, OpenAccuseBookPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AccuseActionPayload.TYPE, AccuseActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EnvoyDeductPayload.TYPE, EnvoyDeductPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UseRelicPayload.TYPE, UseRelicPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfirmRelicLinkPayload.TYPE, ConfirmRelicLinkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UseTaskRefreshPayload.TYPE, UseTaskRefreshPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenProsecutePayload.TYPE, OpenProsecutePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ProsecuteActionPayload.TYPE, ProsecuteActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenBagPayload.TYPE, OpenBagPayload.CODEC);

        // S2C
        PayloadTypeRegistry.clientboundPlay().register(ChallengeSyncPayload.TYPE, ChallengeSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TaskSyncPayload.TYPE, TaskSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ScoreSyncPayload.TYPE, ScoreSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LobbyStatePayload.TYPE, LobbyStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AccuseBookStatePayload.TYPE, AccuseBookStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RelicLinkStatePayload.TYPE, RelicLinkStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProsecuteStatePayload.TYPE, ProsecuteStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SmuggleSyncPayload.TYPE, SmuggleSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BagOpenPayload.TYPE, BagOpenPayload.CODEC);

        // C2S handlers
        ServerPlayNetworking.registerGlobalReceiver(ChallengeTogglePayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改（防止游戏开始后通过发包绕过 GUI 限制）
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            // 附属玩法：无自选规则（仅保留下方 3 个全局开关），其余规则开关一律忽略（防作弊）
            if (GameStateManager.getInstance().isAddon()
                    && !java.util.Set.of("rule_pvp", "rule_night_vision", "rule_keep_inventory")
                            .contains(payload.challengeId())) return;
            applyChallengeToggle(context.player(), payload.challengeId(), payload.enabled());
        });

        ServerPlayNetworking.registerGlobalReceiver(ChallengeValuePayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            ServerPlayer player = context.player();
            Challenge c = ChallengeManager.getInstance().getChallenge(payload.challengeId());
            if (c != null && "inv_slot_lock".equals(payload.challengeId())) {
                // 背包格子锁定：27/18/9 三档切换
                int v = payload.newValue();
                c.setCustomValue(v == 27 || v == 9 ? v : 18);
            } else if (c != null && c.isCustomizable()) {
                // 限制在 10%-90%（以 10% 为单位）
                int v = Math.max(10, Math.min(90, payload.newValue()));
                c.setCustomValue(v);
            }
            // 回传 sync，客户端刷新加权后的初始分数（团队模式下全队数值互通，全员刷新）
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) sendSync(p);
        });

        ServerPlayNetworking.registerGlobalReceiver(ProfessionSelectPayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            // 附属玩法：职业锁定为「无」→ 忽略选择（防作弊）
            if (GameStateManager.getInstance().isAddon()) return;
            applyProfessionSelect(context.player(), payload.professionKey());
            sendSync(context.player()); // 回传确认，客户端按钮即时更新
        });

        ServerPlayNetworking.registerGlobalReceiver(EventTogglePayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            applyEventToggle(context.player(), payload.eventName(), payload.enabled());
        });

        ServerPlayNetworking.registerGlobalReceiver(ChallengeSyncRequestPayload.TYPE, (payload, context) -> {
            sendSync(context.player());
            // 附带大厅初始菜单状态：修复"客户端 stage 残留 1 导致回大厅后只见规则菜单"
            // （上一局结束后服务端已归 0，但客户端 Singleton 只能靠这条通道刷新）
            sendLobbyState(context.player());
        });

        ServerPlayNetworking.registerGlobalReceiver(SetGameModePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                    || GameStateManager.getInstance().getCurrentPhase()
                    == GameStateManager.GamePhase.LOBBY) {
                try {
                    GameStateManager.GameMode mode = GameStateManager.GameMode.valueOf(payload.modeKey().toUpperCase());
                    GameStateManager gsm = GameStateManager.getInstance();
                    // 神的谎言仅单人模式：切团队模式时若已选中神的谎言 → 拒绝（提示先切回常规玩法）
                    if (mode == GameStateManager.GameMode.TEAMS && gsm.isGodsLies()) {
                        player.sendSystemMessage(Component.translatable("survival-contract.msg.godslies_solo_only_detail"));
                        return;
                    }
                    boolean modeChanged = gsm.getGameMode() != mode;
                    gsm.setGameMode(mode);
                    // 重选模式：全部点亮的规则熄灭（清空每玩家已选规则 + 违规记录），
                    // 避免重新分队后队内各自残留上局选的不同规则
                    if (modeChanged) ChallengeManager.getInstance().resetAllPlayers();
                    // 团队模式：立即随机平分红蓝队 + 原版计分板染色（大厅里就能看到队名颜色）
                    if (mode == GameStateManager.GameMode.TEAMS) {
                        com.survivalcontract.team.TeamManager.getInstance().randomizeTeams(context.server());
                    } else {
                        // 个人模式：解除分队与染色
                        com.survivalcontract.team.TeamManager.getInstance().clearTeams(context.server());
                    }
                    // 全员刷新 HUD 与计分板
                    for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                        sendSync(p);
                    }
                    broadcastScores(context.server());
                } catch (IllegalArgumentException ignored) {}
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SetFinalGoalPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                    || GameStateManager.getInstance().getCurrentPhase()
                    == GameStateManager.GamePhase.LOBBY) {
                try {
                    if ("RANDOM".equalsIgnoreCase(payload.goalKey())) {
                        // 最终目标「随机」：大厅仅标记，开局时才随机揭晓
                        GameStateManager.getInstance().setRandomFinalGoal(true);
                    } else {
                        GameStateManager.FinalGoal goal = GameStateManager.FinalGoal.valueOf(payload.goalKey().toUpperCase());
                        GameStateManager gsm2 = GameStateManager.getInstance();
                        gsm2.setCurrentFinalGoal(goal);
                        gsm2.setRandomFinalGoal(false);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(GameDurationPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // 仅大厅阶段可改时长
            if (GameStateManager.getInstance().getCurrentPhase() == GameStateManager.GamePhase.LOBBY) {
                int m = payload.minutes();
                // 合法值：30/60/90/120 或 -1（无限制）；无最终目标玩法必须有限时长（分数最高者获胜）
                if ((GameStateManager.getInstance().isStowaway() || GameStateManager.getInstance().isCivilization())
                        && m == -1) {
                    player.sendSystemMessage(Component.translatable("survival-contract.msg.no_unlimited_duration"));
                    return;
                }
                if (m == -1 || m == 30 || m == 60 || m == 90 || m == 120) {
                    GameStateManager.getInstance().setGameDurationMinutes(m);
                }
                // 时长是全局设置：所有玩家的时长按钮同步显示
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) sendSync(p);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(EventIntervalPayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改；间隔是全局设置：任何玩家修改后全员同步
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            try {
                GameStateManager.getInstance().adjustEventInterval(payload.eventId(), payload.isMin(), payload.delta());
            } catch (IllegalArgumentException ignored) {} // 非法事件 id
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) sendSync(p);
        });

        ServerPlayNetworking.registerGlobalReceiver(SetDeathPenaltyPayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改；死亡扣分是全局设置：任何玩家修改后全员同步
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            int v = payload.value();
            if (v >= 0 && v <= 2) GameStateManager.getInstance().setDeathPenalty(v);
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) sendSync(p);
        });

        ServerPlayNetworking.registerGlobalReceiver(DoubleBanPayload.TYPE, (payload, context) -> {
            // 仅大厅阶段可修改
            if (GameStateManager.getInstance().getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            applyDoubleBan(context.player(), payload.challengeId());
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenTeamChestPayload.TYPE, (payload, context) ->
                com.survivalcontract.game.TeamChestManager.getInstance().openFor(context.player()));

        ServerPlayNetworking.registerGlobalReceiver(StartGamePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            // 允许任何在 LOBBY 阶段的玩家开始（不要求 OP）
            if (GameStateManager.getInstance().getCurrentPhase()
                    == GameStateManager.GamePhase.LOBBY) {
                var dm = com.survivalcontract.dimension.LobbyManager.getInstance();
                try {
                    dm.startGame(player.level().getServer());
                } catch (Exception e) {
                    // 网络包处理器异常会踢掉玩家/中断连接：这里兜住并告知，服务端继续运行
                    com.survivalcontract.SurvivalContract.LOGGER.error("[Network] startGame failed", e);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.arena_create_failed"));
                }
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("survival-contract.msg.game_already_running"));
            }
        });

        // ============ 大厅初始菜单投票：0 = "我决定好了"，1 = "开始游戏"，2 = "返回上级菜单" ============
        ServerPlayNetworking.registerGlobalReceiver(LobbyVotePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            // 观战者不参与投票（我决定好了/开始游戏/返回上级菜单）
            if (gsm.isLobbySpectator(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "survival-contract.msg.spectator_no_vote"));
                return;
            }
            int need = gsm.lobbyVoteNeeded(context.server());
            if (payload.action() == 0) {
                // "我决定好了"：多人需 2 名不同玩家 → 进入规则菜单
                if (gsm.getLobbyStage() >= 1) return;
                int count = gsm.registerLobbyConfirm(player.getUUID());
                if (count >= need) {
                    gsm.setLobbyStage(1);
                    for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "survival-contract.msg.all_confirmed_start"));
                    }
                }
                broadcastLobbyState(context.server());
            } else if (payload.action() == 1) {
                // "开始游戏"：必须在规则菜单（stage 1）且多人凑满 2 名不同玩家
                if (gsm.getLobbyStage() < 1) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.wait_all_ready"));
                    return;
                }
                int count = gsm.registerLobbyStart(player.getUUID());
                if (count >= need) {
                    var dm = com.survivalcontract.dimension.LobbyManager.getInstance();
                    try {
                        dm.startGame(context.server());
                    } catch (Exception e) {
                        com.survivalcontract.SurvivalContract.LOGGER.error("[Network] startGame failed (vote)", e);
                        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "survival-contract.msg.arena_create_failed"));
                    }
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.start_confirm_count", count, need));
                    broadcastLobbyState(context.server());
                }
            } else if (payload.action() == 2) {
                // "返回上级菜单"：从规则菜单回初始菜单，多人需 2 名不同玩家确认
                if (gsm.getLobbyStage() < 1) return;
                int count = gsm.registerLobbyBack(player.getUUID());
                if (count >= need) {
                    gsm.setLobbyStage(0);
                    gsm.resetLobbyVotes();
                    for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                "survival-contract.msg.all_confirmed_back"));
                    }
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                            "survival-contract.msg.back_confirm_count", count, need));
                }
                broadcastLobbyState(context.server());
            }
        });

        // ============ 玩法切换（大厅任何人可改；对局进行中仅管理员可改） ============
        ServerPlayNetworking.registerGlobalReceiver(SetPlayStylePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!canSwitchPlayStyle(player)) return;
            applyPlayStyle(context.server(), player, payload.styleKey());
        });

        // ============ 文明时代：自定义地形预设（大厅；二级菜单「自定义地形」，第四十九批） ============
        ServerPlayNetworking.registerGlobalReceiver(SetTerrainPresetPayload.TYPE, (payload, context) -> {
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            GameStateManager.TerrainPreset preset;
            try {
                preset = GameStateManager.TerrainPreset.valueOf(
                        payload.presetKey() == null ? "" : payload.presetKey().toUpperCase());
            } catch (IllegalArgumentException e) {
                return;
            }
            gsm.setTerrainPreset(preset);
            Component name = switch (preset) {
                case OVERWORLD -> Component.translatable("survival-contract.gui.terrain.preset_overworld");
                case MINER -> Component.translatable("survival-contract.gui.terrain.preset_miner");
                case SNOW -> Component.translatable("survival-contract.gui.terrain.preset_snow");
                case DESERT -> Component.translatable("survival-contract.gui.terrain.preset_desert");
            };
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.translatable("survival-contract.msg.terrain_switched", name));
                sendLobbyState(p);
            }
        });

        // ============ 附属玩法：二级菜单选项（大厅；仅当前选中的附属玩法可改） ============
        ServerPlayNetworking.registerGlobalReceiver(StyleOptionPayload.TYPE, (payload, context) -> {
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY) return;
            if (!gsm.isAddon() || payload.styleId() == null
                    || !payload.styleId().equalsIgnoreCase(gsm.getAddonStyleId())) return;
            var provider = gsm.getAddonProvider();
            if (provider == null) return;
            // 只接受该玩法声明过的选项键，并把值夹到 [min, max]
            for (var opt : provider.options()) {
                if (opt.key().equals(payload.key())) {
                    gsm.setStyleOption(opt.key(), opt.clamp(payload.value()));
                    break;
                }
            }
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                sendLobbyState(p);
            }
        });

        // ============ 神的谎言：使者数量（1~4，仅大厅 + 神的谎言模式） ============
        ServerPlayNetworking.registerGlobalReceiver(SetEnvoyCountPayload.TYPE, (payload, context) -> {
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.LOBBY || !gsm.isGodsLies()) return;
            gsm.setEnvoyCount(payload.count());
            broadcastLobbyState(context.server());
        });

        // ============ 指认书：打开（校验身份/淘汰状态 → 下发存活玩家清单 + 身份/次数） ============
        ServerPlayNetworking.registerGlobalReceiver(OpenAccuseBookPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (!gsm.hasPlayerState(player.getUUID()) || gsm.isEliminated(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.eliminated_no_accuse_book"));
                return;
            }
            boolean envoy = gsm.isGodEnvoy(player.getUUID());
            int charges = envoy ? gsm.getDeductCharges(player.getUUID()) : gsm.getAccuseCharges(player.getUUID());
            List<AccuseBookStatePayload.BookPlayerEntry> alive = new ArrayList<>();
            for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                if (!gsm.hasPlayerState(p.getUUID()) || gsm.isEliminated(p.getUUID())) continue;
                alive.add(new AccuseBookStatePayload.BookPlayerEntry(p.getUUID(), p.getName().getString()));
            }
            ServerPlayNetworking.send(player, new AccuseBookStatePayload(envoy, charges, alive));
        });

        // ============ 平民指认（第六十九批：命中使者 → 对方 -200 分；未命中 → 自己 -200 分） ============
        ServerPlayNetworking.registerGlobalReceiver(AccuseActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (gsm.isEliminated(player.getUUID())) return;
            if (gsm.isGodEnvoy(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.only_civilian_accuse"));
                return;
            }
            if (gsm.getAccuseCharges(player.getUUID()) <= 0) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.godslies.no_charge"));
                return;
            }
            java.util.UUID target = payload.target();
            ServerPlayer targetPlayer = target.equals(player.getUUID()) ? null
                    : context.server().getPlayerList().getPlayer(target);
            if (targetPlayer == null || !gsm.hasPlayerState(target) || gsm.isEliminated(target)) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.accuse_target_invalid"));
                return;
            }
            GameStateManager.AccuseResult result = gsm.resolveAccusation(player.getUUID(), target);
            if (result == GameStateManager.AccuseResult.HIT) {
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.translatable(
                            "survival-contract.msg.godslies.accuse_success",
                            player.getName().getString(), targetPlayer.getName().getString()));
                }
            } else if (result == GameStateManager.AccuseResult.MISS) {
                // 指认失败：不显示指认了谁，只显示指认失败（指认者自己 -200 分）
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.translatable(
                            "survival-contract.msg.godslies.accuse_fail",
                            player.getName().getString()));
                }
            }
            // NULLIFIED（仅剩一名使者 + 纸符契约）与 BOTH_OUT（使者与链接者一同出局，已单独广播）：
            // 都不再叠加通用的「指认成功/失败」文案（第九十一批）
            NetworkRegistry.broadcastScores(context.server());
        });

        // ============ 使者降罪（指定一名玩家扣 55 分；广播只显示被扣分者） ============
        ServerPlayNetworking.registerGlobalReceiver(EnvoyDeductPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (gsm.isEliminated(player.getUUID())) return;
            if (!gsm.isGodEnvoy(player.getUUID())) return;
            if (gsm.getDeductCharges(player.getUUID()) <= 0) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.godslies.no_charge"));
                return;
            }
            java.util.UUID target = payload.target();
            // 允许使者扣自己（保留自我降罪的战术选择）；其余目标必须在线且存活
            ServerPlayer targetPlayer = target.equals(player.getUUID())
                    ? player : context.server().getPlayerList().getPlayer(target);
            if (targetPlayer == null || !gsm.hasPlayerState(target) || gsm.isEliminated(target)) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.deduct_target_invalid"));
                return;
            }
            gsm.envoyDeduct(player.getUUID(), target);
            NetworkRegistry.broadcastScores(context.server());
        });

        // ============ 神谕纸符：使用（第五十九批：平民=武装神罚免疫；使者=下发链接选人清单） ============
        ServerPlayNetworking.registerGlobalReceiver(UseRelicPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (!gsm.hasPlayerState(player.getUUID()) || gsm.isEliminated(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.eliminated_no_relic"));
                return;
            }
            if (!gsm.hasRelicObtained(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.relic_unusable"));
                return;
            }
            if (gsm.isRelicUsed(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.godslies.relic_already_used"));
                return;
            }
            if (gsm.isGodEnvoy(player.getUUID())) {
                // 使者：下发存活玩家清单 → 客户端打开链接选人界面（确认后才消耗纸符）
                List<AccuseBookStatePayload.BookPlayerEntry> alive = new ArrayList<>();
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (!gsm.hasPlayerState(p.getUUID()) || gsm.isEliminated(p.getUUID())) continue;
                    alive.add(new AccuseBookStatePayload.BookPlayerEntry(p.getUUID(), p.getName().getString()));
                }
                ServerPlayNetworking.send(player, new RelicLinkStatePayload(alive));
            } else {
                // 平民：立即消耗纸符，武装一次神罚免疫
                gsm.useRelicCivilian(player);
            }
        });

        // ============ 任务刷新券：使用（神的谎言：消耗 1 个并立即刷新当前专属任务） ============
        ServerPlayNetworking.registerGlobalReceiver(UseTaskRefreshPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (!gsm.hasPlayerState(player.getUUID()) || gsm.isEliminated(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.eliminated_no_refresh"));
                return;
            }
            // 先消耗再刷新：背包没有券直接拒绝（防伪造包）；刷新失败（无任务等）原样返还
            if (!gsm.consumeTaskRefreshToken(player)) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.no_refresh_token"));
                return;
            }
            var tm = com.survivalcontract.task.TaskManager.getInstance();
            var oldTask = tm.getActiveTask(player.getUUID());
            boolean ok = tm.refreshTaskNow(player);
            if (!ok) {
                var refund = GameStateManager.createTaskRefreshToken();
                if (!player.getInventory().add(refund)) com.survivalcontract.McCompat.drop(player, refund, false);
                player.sendSystemMessage(Component.translatable("survival-contract.msg.no_task_to_refresh"));
                return;
            }
            player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            var newTask = tm.getActiveTask(player.getUUID());
            player.sendSystemMessage(Component.translatable("survival-contract.msg.godslies.task_refreshed",
                    com.survivalcontract.task.TaskManager.taskDisplayName(newTask))
                    .append(oldTask == null ? Component.empty()
                            : Component.translatable("survival-contract.msg.old_task_prefix")
                                    .append(com.survivalcontract.task.TaskManager.taskDisplayName(oldTask))
                                    .append(Component.translatable("survival-contract.msg.old_task_suffix"))));
        });

        // ============ 神谕纸符：使者确认链接（服务端复验 → 消耗纸符并记录链接） ============
        ServerPlayNetworking.registerGlobalReceiver(ConfirmRelicLinkPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isGodsLies()) return;
            if (!gsm.hasPlayerState(player.getUUID()) || gsm.isEliminated(player.getUUID())) return;
            if (!gsm.isGodEnvoy(player.getUUID())) return;
            java.util.UUID target = payload.target();
            ServerPlayer targetPlayer = target.equals(player.getUUID()) ? null
                    : context.server().getPlayerList().getPlayer(target);
            if (targetPlayer == null || !gsm.hasPlayerState(target) || gsm.isEliminated(target)) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.link_target_invalid"));
                return;
            }
            gsm.confirmRelicLink(player, target);
        });

        // ============ 走私犯与检察官：打开检查发起书（发起模式=玩家清单；决策模式=贿赂总价；等待模式） ============
        ServerPlayNetworking.registerGlobalReceiver(OpenProsecutePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isStowaway()) return;
            if (!gsm.hasPlayerState(player.getUUID())) return;
            var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();
            boolean pendingPhase = sm.isAwaitingDecision(player.getUUID())
                    || sm.isProsecutorOfPending(player.getUUID());
            List<ProsecuteStatePayload.ProsecuteEntry> list = new ArrayList<>();
            int bribeTotal = 0;
            if (sm.isProsecutorOfPending(player.getUUID())) {
                // 决策模式（我是检察官）：清单仅含被检查者；已进室 → 显示对方贿赂袋价值
                var pp = sm.prosecutorPending(player.getUUID());
                ServerPlayer initiator = context.server().getPlayerList().getPlayer(pp.initiator());
                if (initiator != null) {
                    list.add(new ProsecuteStatePayload.ProsecuteEntry(
                            initiator.getUUID(), initiator.getName().getString(), false));
                }
                bribeTotal = sm.isRoomEntered(pp.initiator()) ? sm.bribeBagTotal(pp.initiator()) : -1;
            } else if (sm.isAwaitingDecision(player.getUUID())) {
                // 等待模式（我是被检查者）：等待检察官定夺
                bribeTotal = -1;
                var pp = sm.awaitingRecord(player.getUUID());
                ServerPlayer prosecutor = context.server().getPlayerList().getPlayer(pp.prosecutor());
                if (prosecutor != null) {
                    list.add(new ProsecuteStatePayload.ProsecuteEntry(
                            prosecutor.getUUID(), prosecutor.getName().getString(), false));
                }
            } else {
                // 发起模式：全体玩家清单（非友方可选）
                for (ServerPlayer p : context.server().getPlayerList().getPlayers()) {
                    if (!gsm.hasPlayerState(p.getUUID())) continue;
                    boolean friend = gsm.isTeamMode()
                            && gsm.getPlayerTeam(p.getUUID()) == gsm.getPlayerTeam(player.getUUID());
                    list.add(new ProsecuteStatePayload.ProsecuteEntry(
                            p.getUUID(), p.getName().getString(), friend));
                }
            }
            ServerPlayNetworking.send(player, new ProsecuteStatePayload(pendingPhase, bribeTotal, list));
        });

        // ============ 走私犯与检察官：发起检查 / 检察官定夺 ============
        // 无进行中检查 → 发起（选非友方检察官，3 秒倒计时后进室）；我是检察官且已进室 → 提交定夺并结算回归
        ServerPlayNetworking.registerGlobalReceiver(ProsecuteActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isStowaway()) return;
            if (!gsm.hasPlayerState(player.getUUID())) return;
            var sm = com.survivalcontract.smuggle.SmuggleManager.getInstance();

            // 决策阶段：我是检察官 → 定夺（受贿 / 检查）
            var ppAsProsecutor = sm.prosecutorPending(player.getUUID());
            if (ppAsProsecutor != null && sm.isRoomEntered(ppAsProsecutor.initiator())
                    && payload.target().equals(ppAsProsecutor.initiator())) {
                sm.decide(context.server(), player.getUUID(), ppAsProsecutor.initiator(), payload.action());
                return;
            }

            // 发起阶段：我=被检查者，选择一名非友方玩家作为自己的检察官
            ServerPlayer target = payload.target().equals(player.getUUID())
                    ? null : context.server().getPlayerList().getPlayer(payload.target());
            if (target == null || !gsm.hasPlayerState(target.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.prosecutor_target_invalid"));
                return;
            }
            if (gsm.isTeamMode() && gsm.getPlayerTeam(target.getUUID()) == gsm.getPlayerTeam(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("survival-contract.msg.prosecutor_friendly_only"));
                return;
            }
            sm.initiate(player, target);
        });

        // ============ 走私犯与检察官：打开收纳袋（价值袋 / 贿赂袋） ============
        ServerPlayNetworking.registerGlobalReceiver(OpenBagPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            var gsm = GameStateManager.getInstance();
            if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isStowaway()) return;
            if (!gsm.hasPlayerState(player.getUUID())) return;
            com.survivalcontract.smuggle.SmuggleManager.getInstance().openBag(player, payload.bribe());
        });

        SurvivalContract.LOGGER.info("Networking payloads registered.");
    }

    /** 服务端主动发给某个玩家完整挑战状态 */
    public static void sendSync(ServerPlayer player) {
        UUID id = player.getUUID();
        ChallengeManager cm = ChallengeManager.getInstance();
        GameStateManager gsm = GameStateManager.getInstance();
        com.survivalcontract.profession.ProfessionManager pm =
                com.survivalcontract.profession.ProfessionManager.getInstance();

        List<ChallengeSyncPayload.Entry> entries = new ArrayList<>();
        for (Challenge c : cm.getAllChallenges()) {
            boolean isEnabled;
            if (c.getId().startsWith("rule_") || "environment".equals(c.getSection())) {
                // 规则开关（PVP/夜视/保留物品栏）与环境恶化板块是全局设置，等同模式/最终目标：所有玩家同步显示
                isEnabled = gsm.isRuleActive(c.getId());
            } else {
                isEnabled = cm.getPlayerChallenges(id).contains(c.getId());
            }
            entries.add(new ChallengeSyncPayload.Entry(
                    c.getId(),
                    isEnabled,
                    c.isCustomizable() || "inv_slot_lock".equals(c.getId()) ? c.getCustomValue() : 0,
                    c.getDisplayName(), c.getDescription(),
                    c.getBaseScore(), c.getPenaltyScore(),
                    c.isCustomizable(), c.getIconItemId(), c.getSection()));
        }

        // 双禁状态（按接收者队伍换算）：我方已禁 / 对面已禁（"" = 未选）
        String myBan = "";
        String enemyBan = "";
        if (gsm.isTeamMode()) {
            var myTeam = gsm.getPlayerTeam(id);
            if (myTeam == GameStateManager.Team.RED || myTeam == GameStateManager.Team.BLUE) {
                GameStateManager.Team enemy = myTeam == GameStateManager.Team.RED
                        ? GameStateManager.Team.BLUE : GameStateManager.Team.RED;
                myBan = gsm.getTeamBans().getOrDefault(myTeam, "");
                enemyBan = gsm.getTeamBans().getOrDefault(enemy, "");
            }
        }

        // 遭遇事件刷新间隔（紧凑编码 "eventId:min:max"，客户端挑战书事件行显示与调整用）
        List<String> intervals = new ArrayList<>();
        for (var type : com.survivalcontract.event.RandomEventManager.EventType.values()) {
            int[] r = gsm.getEventInterval(type.name());
            intervals.add(type.name() + ":" + r[0] + ":" + r[1]);
        }

        ChallengeSyncPayload payload = new ChallengeSyncPayload(
                entries,
                pm.getPlayerProfession(id).name(),
                gsm.calculateInitialScoreExcludingBans(id) + gsm.getEventScoreBonus(id),
                gsm.getGameMode().name().toLowerCase(),
                gsm.isRandomFinalGoal() ? "RANDOM"
                        : (gsm.getCurrentFinalGoal() == null ? "" : gsm.getCurrentFinalGoal().name().toLowerCase()),
                gsm.getCurrentPhase().name(),
                remainingSecondsPayload(gsm),
                gsm.getFreezeSecondsRemaining(),
                gsm.getGameDurationMinutes(),
                new ArrayList<>(gsm.getEnabledEventsFor(id)),
                myBan, enemyBan,
                gsm.getDeathPenalty(), intervals);

        ServerPlayNetworking.send(player, payload);

        // 专属任务 sync（服务端拼好显示文本）
        ServerPlayNetworking.send(player, new TaskSyncPayload(buildTaskText(player)));
    }

    /** 任务变更即时推送（发放新任务时调用，避免等下一轮 sync 显示"暂无任务"） */
    public static void sendTaskUpdate(ServerPlayer player) {
        ServerPlayNetworking.send(player, new TaskSyncPayload(buildTaskText(player)));
    }

    /** 专属任务 sync（任务名走原版翻译键 → 客户端按自身语言渲染名称；进度/时间为字面量） */
    public static Component buildTaskText(ServerPlayer player) {
        var t = com.survivalcontract.task.TaskManager.getInstance().getActiveTask(player.getUUID());
        if (t == null) return Component.translatable("survival-contract.gui.hud.no_task");
        long remainSec = Math.max(0, (t.expireTick - player.level().getGameTime()) / 20);
        String tierColor = switch (t.tier) {
            case S -> "§c"; case A -> "§6"; default -> "§7";
        };
        String timeStr = String.format("%02d:%02d", remainSec / 60, remainSec % 60);
        return Component.literal(tierColor + "[" + t.tier + "] §f")
                .append(com.survivalcontract.task.TaskManager.taskDisplayName(t))
                .append(Component.literal(" §e" + t.currentAmount + "/" + t.requiredAmount + " §b" + timeStr));
    }

    /** 剩余秒数：无限制时长返回 -1（客户端 HUD 显示"无限制"） */
    private static int remainingSecondsPayload(GameStateManager gsm) {
        long ticks = gsm.getRemainingTimeTicks();
        if (ticks < 0) return -1;
        return (int) (ticks / 20);
    }

    /** 每 2 秒广播一次所有玩家分数（供 HUD 计分板显示）。
     *  大厅阶段广播各玩家"当前选中规则累计的初始分数"，随规则点亮实时刷新；游戏中广播实际分数。 */
    public static void broadcastScores(net.minecraft.server.MinecraftServer server) {
        GameStateManager gsm = GameStateManager.getInstance();
        boolean lobby = gsm.getCurrentPhase() == GameStateManager.GamePhase.LOBBY;
        List<ScoreSyncPayload.ScoreEntry> entries = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean spec = gsm.isLobbySpectator(p.getUUID());
            int score = lobby
                    ? (spec ? 0 // 大厅观战者计分板显示 0
                        : gsm.calculateInitialScoreExcludingBans(p.getUUID()) + gsm.getEventScoreBonus(p.getUUID()))
                    : gsm.getScore(p.getUUID());
            entries.add(new ScoreSyncPayload.ScoreEntry(
                    p.getName().getString(),
                    score,
                    spec ? GameStateManager.Team.NONE : gsm.getPlayerTeam(p.getUUID())));
        }
        ScoreSyncPayload payload = new ScoreSyncPayload(entries);
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
