package com.survivalcontract.mixin;

import com.survivalcontract.profession.ProfessionManager;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 工匠职业：合成工具或武器时 50% 概率获得耐久2，另有 10% 概率获得经验修补；
 * 合成工具另有 10% 概率获得效率4，合成武器另有 10% 概率获得锋利3（各附魔均可叠加）。
 *
 * 第五十二批修复「附魔不容易触发」：不再挂 ResultSlot.onTake（26.2 中 shift-click 合并进
 * 既有物品栈时 onTake 拿到的是被 setCount(0) 的空壳实例，附魔丢失），改为挂
 * CraftingMenu.slotChangedCraftingGrid TAIL——直接对装配结果本体（resultContainer.getItem(0)）
 * 掷骰附魔。工作台 3x3 与背包 2x2（InventoryMenu 同样调用本方法）全覆盖，
 * 结果预览即显示附魔，无论之后落入空槽位还是与背包同类物品合并，附魔都随物品走。
 */
@Mixin(CraftingMenu.class)
public class CraftingResultMixin {

	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void sc$artisanCraftBonus(AbstractContainerMenu menu, ServerLevel level, Player player,
			CraftingContainer craftSlots, ResultContainer resultContainer, RecipeHolder<CraftingRecipe> recipe,
			CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) return;
		ItemStack stack = resultContainer.getItem(0);
		if (stack.isEmpty() || !stack.isDamageableItem()) return;
		if (com.survivalcontract.game.GameStateManager.getInstance().getCurrentPhase()
				!= com.survivalcontract.game.GameStateManager.GamePhase.PLAYING) return;
		if (ProfessionManager.getInstance().getPlayerProfession(sp.getUUID())
				!= ProfessionManager.Profession.ARTISAN) return;
		// 仅工具/武器：有耐久且不是盔甲（26.2 无 ArmorItem：用 EQUIPPABLE 组件槽位判断）
		if (isArmorStack(stack)) return;

		var registries = sp.level().registryAccess();
		if (sp.getRandom().nextFloat() < 0.5f) {
			addEnchant(stack, registries, Enchantments.UNBREAKING, 2);
		}
		if (sp.getRandom().nextFloat() < 0.1f) {
			addEnchant(stack, registries, Enchantments.MENDING, 1);
		}
		// 10% 工具→效率4 / 武器→锋利3（与上面的耐久/经验修补各自独立掷骰，可叠加）
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (isCraftedTool(id) && sp.getRandom().nextFloat() < 0.1f) {
			addEnchant(stack, registries, Enchantments.EFFICIENCY, 4);
		}
		if (isCraftedWeapon(id) && sp.getRandom().nextFloat() < 0.1f) {
			addEnchant(stack, registries, Enchantments.SHARPNESS, 3);
		}
	}

	/** 合成工具判定：镐/斧/锹/锄/剪刀（效率4 掷骰；斧兼具工具与武器，两掷骰均参与） */
	private static boolean isCraftedTool(String id) {
		return id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel")
				|| id.endsWith("_hoe") || id.equals("minecraft:shears");
	}

	/** 合成武器判定：剑/斧/锤（锋利3 掷骰） */
	private static boolean isCraftedWeapon(String id) {
		return id.endsWith("_sword") || id.endsWith("_axe") || id.equals("minecraft:mace");
	}

	/** 盔甲判定（26.2：EQUIPPABLE 组件槽位） */
	private static boolean isArmorStack(ItemStack s) {
		var eq = s.get(DataComponents.EQUIPPABLE);
		if (eq == null) return false;
		return eq.slot() == net.minecraft.world.entity.EquipmentSlot.HEAD
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.CHEST
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.LEGS
				|| eq.slot() == net.minecraft.world.entity.EquipmentSlot.FEET;
	}

	/** 精确附加附魔（已有时取较高等级） */
	private static void addEnchant(ItemStack stack, HolderLookup.Provider registries,
			ResourceKey<Enchantment> key, int level) {
		Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mut.set(holder, Math.max(level, mut.getLevel(holder)));
		stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
	}
}
