package com.survivalcontract.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露服务端玩家挖掘状态（第五十批）：
 * 文明时代占领羊毛被玩家挖掘过程中 → 羊毛位置保持发光高亮。
 * {@code isDestroyingBlock}/{@code destroyPos} 在客户端按住左键挖掘期间由原版维护，
 * 挖掘中断/换目标/挖掉后自动复位——正好作为"破坏过程中"的判定来源。
 */
@Mixin(ServerPlayerGameMode.class)
public interface ServerPlayerGameModeAccessor {

	@Accessor("isDestroyingBlock")
	boolean sc$isDestroyingBlock();

	@Accessor("destroyPos")
	BlockPos sc$getDestroyPos();

	/** 自测用：模拟假人正在挖掘指定方块 */
	@Accessor("isDestroyingBlock")
	void sc$setDestroying(boolean v);

	/** 自测用：模拟假人挖掘目标位置 */
	@Accessor("destroyPos")
	void sc$setDestroyPos(BlockPos pos);
}
