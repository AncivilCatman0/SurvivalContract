package com.survivalcontract.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露末影龙战的原版 Boss 血条。
 *
 * <p>与 {@link WitherBossAccessor} 同理：竞技场末地被拆除时，末影龙血条不会被客户端自动清除，
 * 需要显式 {@code removeAllPlayers()}。</p>
 */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {

	@Accessor("dragonEvent")
	ServerBossEvent sc$getDragonEvent();
}
