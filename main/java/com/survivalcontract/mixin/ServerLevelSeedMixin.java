package com.survivalcontract.mixin;

import com.survivalcontract.dimension.ArenaWorldManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 竞技场维度的随机种子拦截。
 *
 * <p>原版 {@code ServerLevel.getSeed()} 硬编码返回世界种子
 * （{@code server.getWorldData().worldGenOptions().seed()}）。竞技场维度组是"每局新建"的
 * 临时维度，必须使用每局随机种子 —— 本注入在竞技场维度上把 getSeed 重定向为
 * {@link ArenaWorldManager#currentSeed()}。</p>
 *
 * <p>拦截生效范围：ChunkMap 构造器内的 {@code RandomState.create(...)}（地形/生物群系/
 * 结构生成的全部噪声种子）、{@code ChunkGeneratorStructureState}（结构放置间距）等
 * —— 这些都在 ServerLevel 构造期间或之后调用 {@code getSeed()}。</p>
 *
 * <p>注意：{@code dimension()} 声明在父类 {@link Level} 上（非 ServerLevel 自身），
 * 不能用 @Shadow，用 {@code ((Level)(Object)this)} 强转获取。</p>
 */
@Mixin(ServerLevel.class)
public class ServerLevelSeedMixin {

	@Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
	private void survivalcontract$arenaRandomSeed(CallbackInfoReturnable<Long> cir) {
		Level self = (Level) (Object) this;
		if (ArenaWorldManager.isArenaLevel(self.dimension())) {
			cir.setReturnValue(ArenaWorldManager.currentSeed());
		}
	}
}
