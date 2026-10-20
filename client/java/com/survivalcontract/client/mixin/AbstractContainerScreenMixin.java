package com.survivalcontract.client.mixin;

import com.survivalcontract.lock.LockedSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 锁定背包格覆盖渲染：在每个锁定空槽位置画屏障图标（survival-contract:lock_barrier，取自原版屏障贴图）。
 * 使用与原版空槽图标（盔甲栏轮廓等）完全相同的 blitSprite 管线——容器物品渲染管线在此屏幕中不可见，
 * 贴图 blit 是唯一在槽位上验证过可见的绘制路径。坐标为容器局部 slot.x/slot.y（与原版槽内绘制一致）。
 * 锁定槽本身始终为空（点击拦截 + 服务端清扫兜底），图标只是视觉覆盖，无法被拿取。
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    private static final Identifier LOCK_BARRIER_SPRITE = Identifier.fromNamespaceAndPath("survival-contract", "lock_barrier");

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void sc$drawLockOverlay(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (slot.container != player.getInventory()) return;
        if (!LockedSlots.isLocked(player, slot.getContainerSlot())) return;
        if (!slot.getItem().isEmpty()) return; // 只覆盖空槽（正常时锁定槽恒为空）
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_BARRIER_SPRITE, slot.x, slot.y, 16, 16);
    }
}
