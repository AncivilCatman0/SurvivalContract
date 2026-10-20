package com.survivalcontract.mixin;

import com.survivalcontract.lock.LockedSlots;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 菜单点击总入口 doClick（26.2 签名 IILContainerInput;Player;V）双端拦截：
 * ① 锁定背包格：玩家物品栏的锁定槽完全点击免疫——放入/拿取/shift/数字键/Q 丢弃全部取消（点击无反馈）；
 * ② 贿赂收纳袋禁放走私品（第六十六批）：放置/换位/shift 存入/拖拽一律取消，从袋里拿取不受影响；
 * ③ 收纳袋互存禁放（第六十七批）：任何收纳袋道具不能放进任一收纳袋界面（防孤儿道具）。
 * 双端生效（服务端权威 + 客户端预测同规则），客户端不会出现"放入后弹回"。
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {

    @Inject(method = "doClick(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true)
    private void sc$blockLockedSlots(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo ci) {
        // 文明商人兑换界面：服务端点击商品格 = 兑换（扣积分发货）并取消原版拾取/放置；
        // 客户端瞬时预测由容器同步自动纠正（第四十九批）
        if (com.survivalcontract.civilization.CivilizationManager.handleMerchantGuiClick(
                (AbstractContainerMenu) (Object) this, player, slotIndex)) {
            ci.cancel();
            return;
        }
        // 贿赂收纳袋：禁止把走私品放进贿赂袋（第六十六批）——放置/换位/shift 存入/拖拽
        // 一律取消（点击无反馈），从袋里拿取不受影响。服务端按袋标记容器识别，
        // 客户端按开袋旗标包记录的菜单引用识别（客户端菜单底层容器是原版新建的裸容器）
        if (com.survivalcontract.smuggle.SmuggleManager.blockBribeDeposit(
                (AbstractContainerMenu) (Object) this, player, slotIndex, button, input)) {
            ci.cancel();
            return;
        }
        // 收纳袋互存禁放（第六十七批）：任何收纳袋道具不能放进任一收纳袋界面
        if (com.survivalcontract.smuggle.SmuggleManager.blockBagKeyDeposit(
                (AbstractContainerMenu) (Object) this, player, slotIndex, button, input)) {
            ci.cancel();
            return;
        }
        if (slotIndex < 0 || player == null) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot slot;
        try {
            slot = self.getSlot(slotIndex);
        } catch (Exception e) {
            return; // 越界索引（拖拽阶段等使用特殊索引，交回原逻辑）
        }
        if (slot == null || slot.container != player.getInventory()) return;
        if (LockedSlots.isLocked(player, slot.getContainerSlot())) {
            ci.cancel();
        }
    }
}
