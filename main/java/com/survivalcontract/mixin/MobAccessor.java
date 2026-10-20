package com.survivalcontract.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/**
 * 暴露 Mob 的私有战利品表覆盖字段（NBT "DeathLootTable"；死亡掉落结算后自动清空）。
 *
 * <p>26.2 原版 magma_cube 战利品表的岩浆奶油条目带 {@code size≥2} 条件——本模组刷出的
 * 小型岩浆怪（size=1）按原版表不掉任何东西，需要覆盖为自定义战利品表
 * survival-contract:civilization/zone_mob。Mob 没有公开 setter，走 accessor（与
 * WitherBossAccessor/RabbitAccessor 同模式）。</p>
 */
@Mixin(Mob.class)
public interface MobAccessor {

	@Accessor("lootTable")
	void sc$setLootTable(Optional<ResourceKey<LootTable>> table);
}
