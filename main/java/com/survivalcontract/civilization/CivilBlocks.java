package com.survivalcontract.civilization;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 文明时代专属占领羊毛方块：
 * <ul>
 *   <li>硬度为原版羊毛的 5 倍（0.8 → 4.0）——破坏所需时长变为原本的五倍</li>
 *   <li>无战利品表（noLootTable）——破坏后不掉落</li>
 *   <li>其余属性（音效/爆炸抗性/外观）与原版红/蓝羊毛一致</li>
 * </ul>
 */
public class CivilBlocks {

	/** 26.2 无 Blocks.RED_WOOL 常量（WOOL 是 ColorCollection）→ 注册表直查原版羊毛 */
	private static Block vanillaWool(String color) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", color + "_wool"));
	}

	/** 26.2 方块/物品构造前必须 setId（drops/描述解析依赖 id），注册与构造同处完成 */
	private static Block create(String path, String color) {
		Identifier rid = Identifier.fromNamespaceAndPath("survival-contract", path);
		var blockKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, rid);
		var itemKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, rid);
		Block block = new Block(BlockBehaviour.Properties.ofFullCopy(vanillaWool(color))
				.strength(4.0f, 0.8f).noLootTable().setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block,
				new Item.Properties().setId(itemKey)));
		return block;
	}

	public static final Block CLAIM_WOOL_RED = create("claim_wool_red", "red");
	public static final Block CLAIM_WOOL_BLUE = create("claim_wool_blue", "blue");

	/** mod 初始化时调用：触发类加载——方块与物品在 static 字段初始化（create）时即完成注册 */
	public static void register() {
	}

	private CivilBlocks() {}
}
