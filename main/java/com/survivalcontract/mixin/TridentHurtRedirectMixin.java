package com.survivalcontract.mixin;

import com.survivalcontract.MainEventListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ⑥ 三叉戟弹开修复（第五十二批）：同 ArrowHurtRedirectMixin——
 * ThrownTrident.onHitEntity 覆写了父类且自带 hurtOrSimulate 调用（先 dealtDamage=true 再补伤），
 * 必须单独重定向，弓手/嗜痛人/受伤增加规则补伤后三叉戟同样不再弹开。
 */
@Mixin(ThrownTrident.class)
public class TridentHurtRedirectMixin {

	@Redirect(method = "onHitEntity",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean sc$redirectTridentHurt(Entity entity, DamageSource source, float amount) {
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
