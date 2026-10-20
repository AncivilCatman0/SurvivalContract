package com.survivalcontract.mixin;

import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.rabbit.Rabbit.Variant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker Mixin for Rabbit to expose the private setVariant method.
 * Rabbit.Variant is stored in SynchedEntityData (DATA_TYPE_ID),
 * NOT in a simple field — @Accessor("variant") fails with InvalidAccessorException.
 * Use @Invoker("setVariant") to call the private method directly.
 */
@Mixin(Rabbit.class)
public interface RabbitAccessor {
	@Invoker("setVariant")
	void survivalcontract$invokeSetVariant(Variant variant);
}
