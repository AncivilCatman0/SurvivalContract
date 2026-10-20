package com.survivalcontract.lock;

import net.minecraft.world.entity.player.Player;

/**
 * 背包格锁定状态（双端独立注入式）：
 * <ul>
 *   <li>服务端在 SurvivalContract SERVER_STARTED 注册 {@link #serverChecker}（按玩家挑战集 + 游戏阶段）</li>
 *   <li>客户端在 SurvivalContractClient.onInitializeClient 注册 {@link #clientChecker}（按 ClientState sync 数据）</li>
 *   <li>判定入口 {@link #isLocked(Player, Integer)} 按玩家所在侧分流——单机模式下集成服务器与客户端共用 JVM，
 *       若共用一个静态字段，服务端注册时会覆写客户端实现（服务端实现遇到 LocalPlayer 一律 false，
 *       导致客户端预测不拦截、屏障图标不渲染），故必须双字段分离</li>
 * </ul>
 * 参数：Player + 玩家物品栏容器槽位索引（0-8 快捷栏永不禁用 / 9-35 背包 3x9）。
 */
public final class LockedSlots {

    /** 服务端权威判定（ServerPlayer） */
    public static java.util.function.BiPredicate<Player, Integer> serverChecker = (player, slot) -> false;

    /** 客户端预测判定（LocalPlayer，与服务端同规则保证点击无回弹） */
    public static java.util.function.BiPredicate<Player, Integer> clientChecker = (player, slot) -> false;

    /** 判定入口：按玩家所在逻辑侧分流到对应 checker */
    public static boolean isLocked(Player player, Integer slot) {
        return player.level().isClientSide()
                ? clientChecker.test(player, slot)
                : serverChecker.test(player, slot);
    }

    private LockedSlots() {
    }
}
