package com.survivalcontract.mixin;

import com.survivalcontract.MainEventListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ⑥ 箭矢弹开修复（第五十二批）：
 * 弓手特权/嗜痛人/受到伤害增加等规则经 MainEventListener.redealHurt "取消原始伤害+补伤"实现，
 * ALLOW_DAMAGE 返回 false → onHitEntity 中的 hurtOrSimulate 返回 false →
 * 26.2 原版走 ProjectileDeflection.REVERSE 分支（箭矢弹开且可捡回）。
 * 本 mixin 重定向该调用：原调用被取消且目标刚被 redealHurt 补伤时，改判为命中成功 →
 * 箭矢 discard，不再弹开。纯保护性取消（无补伤标记）仍正常弹开。
 */
@Mixin(AbstractArrow.class)
public class ArrowHurtRedirectMixin {

	@Redirect(method = "onHitEntity",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean sc$redirectArrowHurt(Entity entity, DamageSource source, float amount) {
		boolean result = entity.hurtOrSimulate(source, amount);
		if (!result) {
			String redealt = MainEventListener.REDEALT_TARGET.get();
			if (redealt != null && redealt.equals(entity.getStringUUID())) {
				MainEventListener.REDEALT_TARGET.remove();
				return true;
			}
		}
		return result;
	}
}
