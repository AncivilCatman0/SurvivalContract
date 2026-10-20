package com.survivalcontract.mixin;

import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 NetherPortalBlock 的私有 getExitPortal —— 竞技场维度重定向时复用原版
 * 的"查找/创建下界传送门"完整逻辑（原方法只依赖传入的目标维度，不含硬编码维度）。
 */
@Mixin(NetherPortalBlock.class)
public interface NetherPortalBlockInvoker {

	@Invoker("getExitPortal")
	TeleportTransition survivalcontract$getExitPortal(
			net.minecraft.server.level.ServerLevel targetLevel,
			net.minecraft.world.entity.Entity entity,
			net.minecraft.core.BlockPos portalPos,
			net.minecraft.core.BlockPos exitPos,
			boolean toNether,
			WorldBorder border);
}
