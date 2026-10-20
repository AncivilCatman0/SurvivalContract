package com.survivalcontract.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 附属玩法（addon play style）对外契约。
 * <p>
 * 附属模组在 {@code fabric.mod.json} 里声明自定义入口点：
 * <pre>
 * "entrypoints": {
 *   "survival-contract:playstyle": [ "com.example.MyStyleProvider" ]
 * }
 * </pre>
 * 宿主 SC 启动时用 {@code FabricLoader.getEntrypointContainers("survival-contract:playstyle", ...)}
 * 收集并注册，随后挑战书里会多出一个该玩法的按钮；选中即进入「附属玩法」分支。
 * <p>
 * 职责边界：SC 提供注册 / 选项同步 / UI 渲染 / 抑制开关 / 三个生命周期钩子 / 地图注入工具 /
 * 世界规则工具；玩法全部逻辑由附属模组实现。
 */
public interface ScPlayStyleProvider {

	/** 玩法唯一 id（同时作为 {@code getPlayStyleKey()} 的返回值，建议小写、含命名空间风格，如 "hell-kitchen"） */
	String id();

	/** 按钮名称的本地化键（由附属模组的语言文件提供） */
	String displayNameKey();

	/** 语言文件缺失时的兜底按钮文案 */
	default String displayNameFallback() { return id(); }

	/** true = 仅团队模式可选（选中时若为个人模式，服务端拒绝） */
	default boolean forceTeamMode() { return false; }

	/** 挑战书 UI 抑制开关 */
	default StyleUi ui() { return StyleUi.NONE; }

	/** 二级菜单里的整数选项（宿主把第 4 位「双禁」替换成第一个选项的循环按钮） */
	default List<StyleOption> options() { return List.of(); }

	/** 开局钩子（仅参战者会收到；大厅观战者不含在内）。此时阶段已是 PLAYING、竞技场未创建。 */
	default void onGameStart(MinecraftServer server, List<ServerPlayer> players) {}

	/** 每服务端 tick 钩子（宿主在 END_SERVER_TICK 转发） */
	default void onServerTick(MinecraftServer server) {}

	/** 对局结束钩子（进入 FINISHED 的第一次 tick） */
	default void onGameEnd(MinecraftServer server) {}

	/** 玩家加入/重连钩子 */
	default void onPlayerJoin(ServerPlayer player) {}

	/**
	 * 对局进行中新加入玩家的观战落点钩子（宿主在把该玩家送去「竞技场出生点上空观战」之前调用）。
	 * <p>附属玩法的竞技场不存在（固定地图由附属模组自持），因此需要自行决定观战者在哪看。
	 * <p>返回 {@code true} = 附属玩法已接管：自行设置旁观者模式并把玩家传送到本玩法地图的观战点，
	 * 宿主不再把该玩家送往竞技场；返回 {@code false} = 走宿主默认（竞技场出生点上空观战）。
	 *
	 * @param server 服务端
	 * @param player 中途加入、且无参战状态、将被置为观战者的玩家
	 */
	default boolean onSpectatorJoin(MinecraftServer server, ServerPlayer player) { return false; }
}
