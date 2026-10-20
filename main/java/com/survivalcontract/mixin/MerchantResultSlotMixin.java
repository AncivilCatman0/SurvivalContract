package com.survivalcontract.mixin;

import com.survivalcontract.profession.ProfessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 行商职业：与村民（含流浪商人）交易时，必定获得双倍奖励。
 * 挂在交易结果槽 onTake（原版发放奖励之后），额外复制一份产物进背包（放不下丢在地上）。
 */
@Mixin(MerchantResultSlot.class)
public class MerchantResultSlotMixin {

	@Inject(method = "onTake", at = @At("TAIL"))
	private void sc$foolDoubleTrade(Player player, ItemStack stack, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) return;
		if (stack.isEmpty()) return;
		if (ProfessionManager.getInstance().getPlayerProfession(sp.getUUID())
				!= ProfessionManager.Profession.FOOL) return;
		ItemStack dup = stack.copy();
		if (!sp.getInventory().add(dup)) {
			com.survivalcontract.McCompat.drop(sp, dup, false);
		}
		sp.sendSystemMessage(Component.translatable("survival-contract.msg.trader_double_reward"));
	}
}
