package com.survivalcontract.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * ① 文明模式自然刷怪双闸（第五十二批 1/4 → 第六十五批再减半）：
 * 每个区块的自然刷怪入口 spawnForChunk 有 7/8 概率被整体跳过（期望产出 = 原来的 1/8），
 * 且世界敌对生物总量达上限（原版怪物刷怪容量的一半）后拒绝自然刷怪。
 * 门槛逻辑在 CivilizationManager.naturalSpawnAllowed（静态，自测可直接断言）。
 */
@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

	@Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
	private static void sc$reduceNaturalSpawns(ServerLevel level, LevelChunk chunk,
			NaturalSpawner.SpawnState state, List<net.minecraft.world.entity.MobCategory> categories, CallbackInfo ci) {
		if (!com.survivalcontract.civilization.CivilizationManager.naturalSpawnAllowed(level)) {
			ci.cancel();
		}
	}
}
