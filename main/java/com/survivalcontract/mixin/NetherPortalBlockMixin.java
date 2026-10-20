package com.survivalcontract.mixin;

import com.survivalcontract.dimension.ArenaWorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 下界传送门重定向：玩家在竞技场维度组内穿过下界传送门时，目标维度改为
 * 竞技场下界/竞技场主世界（而非原版 NETHER/OVERWORLD）。
 *
 * <p>其余逻辑（1:8 坐标换算、边界 clamp、查找/创建传送门、音效）全部复用原版：
 * 坐标计算照抄 {@code getPortalDestination} 原版字节码，传送门查找直接调私有
 * {@code getExitPortal}（该私有方法只依赖传入的目标维度，无硬编码维度）。</p>
 */
@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void survivalcontract$arenaNetherRedirect(ServerLevel fromLevel, Entity entity, BlockPos pos,
			CallbackInfoReturnable<TeleportTransition> cir) {
		ResourceKey<Level> targetKey = ArenaWorldManager.getNetherDestination(fromLevel.dimension());
		if (targetKey == null) return; // 非竞技场维度 → 原版逻辑

		ServerLevel target = fromLevel.getServer().getLevel(targetKey);
		if (target == null) return; // 竞技场下界未创建（不该发生）→ 原版逻辑兜底

		boolean toNether = targetKey == ArenaWorldManager.ARENA_NETHER;
		WorldBorder border = target.getWorldBorder();
		double scale = DimensionType.getTeleportationScale(
				fromLevel.dimensionType(), target.dimensionType());
		BlockPos exitPos = border.clampToBounds(
				entity.getX() * scale, entity.getY(), entity.getZ() * scale);

		cir.setReturnValue(((NetherPortalBlockInvoker) this).survivalcontract$getExitPortal(
				target, entity, pos, exitPos, toNether, border));
	}
}
