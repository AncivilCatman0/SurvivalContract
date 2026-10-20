package com.survivalcontract.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露凋灵的原版 Boss 血条。
 *
 * <p>凋灵被召唤后（= 达成"凋零山庄"目标）游戏立刻结束，凋灵实体仍然存活且血条已推送给客户端。
 * 拆除竞技场维度只会移除服务端实体，<b>不会</b>给客户端发移除包 → 血条永久留在客户端，
 * 于是大厅和后续每一局都在叠加。需要在拆维度前显式 {@code removeAllPlayers()}。</p>
 */
@Mixin(WitherBoss.class)
public interface WitherBossAccessor {

	@Accessor("bossEvent")
	ServerBossEvent sc$getBossEvent();
}
