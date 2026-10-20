package com.survivalcontract.mixin;

import com.survivalcontract.lock.PickupGuard;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 「禁用背包格子」规则：只重定向 {@code ItemEntity.playerTouch} 里那一次
 * {@code Inventory.add(ItemStack)} 调用，交给 {@link PickupGuard} 只往未锁定槽插入。
 *
 * <p>可用槽占满时 add 返回 false 且不改动原栈 → 物品留在地上（拾取失败）；
 * 同一格未达堆叠上限的同类物品仍会正常补堆（部分合并时余量继续留在实体里）。
 *
 * <p>只改这一处调用点：其它 {@code Inventory.add} 调用方（补给发放、铁厨刀补发、
 * 合成产物回收等）保持原版行为，不受影响。
 */
@Mixin(ItemEntity.class)
public class ItemEntityPickupMixin {

    @Redirect(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean sc$pickupIntoUnlockedSlots(Inventory inventory, ItemStack stack) {
        return PickupGuard.pickup(inventory, stack);
    }
}
