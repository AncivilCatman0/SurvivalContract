package com.survivalcontract.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 暴露 MinecraftServer 的三个私有成员，用于动态创建/拆除竞技场维度：
 * <ul>
 *   <li>{@code levels} —— 维度注册表，手动 put/remove ServerLevel</li>
 *   <li>{@code executor} —— ServerLevel 构造参数</li>
 *   <li>{@code storageSource} —— 世界存档访问器（构造参数 + getDimensionPath 删档）</li>
 * </ul>
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

	@Accessor("levels")
	Map<ResourceKey<Level>, ServerLevel> sc$getLevels();

	@Accessor("executor")
	Executor sc$getExecutor();

	@Accessor("storageSource")
	LevelStorageSource.LevelStorageAccess sc$getStorageSource();
}
