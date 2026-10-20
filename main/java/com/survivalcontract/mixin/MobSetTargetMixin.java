package com.survivalcontract.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 傀儡不索敌基地回收猪灵（第五十一批）：玩家造出的铁傀儡/雪傀儡会把大本营的回收猪灵
 * （Monster 类）当作攻击目标——即使猪灵有抗性提升 5 免疫伤害，索敌寻路仍会一直追着撞。
 * 在 Mob.setTarget 入口过滤：目标为大本营回收猪灵时直接取消设置，
 * 使索敌目标（含原版索敌 goal 的反复重选）永远不会生效。
 * 仅文明时代进行中生效（piglins 表为空时放行一切目标）。
 */
@Mixin(Mob.class)
public abstract class MobSetTargetMixin {

    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private void sc$noTargetRecyclePiglin(LivingEntity target, CallbackInfo ci) {
        if (target == null) return;
        if (com.survivalcontract.civilization.CivilizationManager.getInstance()
                .isRecyclePiglin(target.getUUID())) {
            ci.cancel();
        }
    }
}
