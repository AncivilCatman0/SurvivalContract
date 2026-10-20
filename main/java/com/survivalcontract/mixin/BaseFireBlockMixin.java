package com.survivalcontract.mixin;

import com.survivalcontract.dimension.ArenaWorldManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 的 {@code BaseFireBlock.inPortalDimension(Level)} 硬编码判定
 * {@code dimension() == Level.OVERWORLD || == Level.NETHER} — 竞技场维度
 * （survival-contract:arena / arena_nether）不在名单内，导致打火石点燃黑曜石
 * 门框架后火能放置但传送门方块永不生成（"开不了下界"）。
 *
 * <p>修复：竞技场主世界/下界维度视为"传送门维度"，放行原版门框检测与
 * 传送门方块填充逻辑（末地走 EndPortalBlock 不受影响）。</p>
 */
@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin {

	@Inject(method = "inPortalDimension", at = @At("HEAD"), cancellable = true)
	private static void survivalcontract$arenaIsPortalDimension(Level level, CallbackInfoReturnable<Boolean> cir) {
		ResourceKey<Level> key = level.dimension();
		if (key == ArenaWorldManager.ARENA || key == ArenaWorldManager.ARENA_NETHER) {
			cir.setReturnValue(true);
		}
	}
}
