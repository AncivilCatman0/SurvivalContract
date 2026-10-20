package com.survivalcontract.mixin;

import com.survivalcontract.dimension.ArenaWorldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 末地传送门重定向（两个方向）：
 * <ul>
 *   <li><b>进末地</b>：玩家在<b>竞技场主世界</b>踩进末地传送门（要塞）→ 目的地改为
 *       <b>竞技场末地</b>（原版硬编码 {@code Level.END}）。逻辑照抄原版进入分支
 *       （黑曜石平台 + 朝向 + Relative 组合）。</li>
 *   <li><b>出末地</b>：玩家在<b>竞技场末地</b>踩进龙死后生成的返回传送门 →
 *       原版走 {@code findRespawnPositionAndUseSpawnBlock}（用玩家自己的重生配置，
 *       指向竞技场出生点，天然正确）；非玩家实体送回竞技场主世界出生点。
 *       不重定向的话原版会因 {@code dimension() != Level.END} 判定成"进末地"，
 *       把玩家错误送进原版末地。</li>
 * </ul>
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void survivalcontract$arenaEndRedirect(ServerLevel fromLevel, net.minecraft.world.entity.Entity entity,
			BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
		// ===== 出末地方向：竞技场末地的返回传送门 → 回重生点/竞技场主世界 =====
		if (fromLevel.dimension() == ArenaWorldManager.ARENA_END) {
			if (entity instanceof ServerPlayer sp) {
				// 原版出末地分支：用玩家自己的重生配置（游戏开始时已固定到竞技场出生点）
				cir.setReturnValue(sp.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
			} else {
				ServerLevel target = fromLevel.getServer().getLevel(ArenaWorldManager.ARENA);
				if (target == null) return;
				var rd = target.getRespawnData();
				Vec3 spawnPos = Vec3.atBottomCenterOf(entity.adjustSpawnLocation(target, rd.pos()));
				// Relative.ROTATION 是预构建的 Set<Relative> 常量（非枚举值），直接传参
				Set<Relative> relatives = Relative.union(Relative.DELTA, Relative.ROTATION);
				cir.setReturnValue(new TeleportTransition(target, spawnPos, Vec3.ZERO,
						rd.yaw(), rd.pitch(), relatives, TeleportTransition.PLAY_PORTAL_SOUND));
			}
			return;
		}

		// ===== 进末地方向：只重定向"竞技场主世界 → 竞技场末地" =====
		if (fromLevel.dimension() != ArenaWorldManager.ARENA) return;

		ServerLevel target = fromLevel.getServer().getLevel(ArenaWorldManager.ARENA_END);
		if (target == null) return; // 竞技场末地未创建（不该发生）→ 原版逻辑兜底

		// === 以下照抄原版"进入末地"分支字节码 ===
		Vec3 spawnPos = Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT);
		EndPlatformFeature.createEndPlatform(target, BlockPos.containing(spawnPos).below(), true);
		float yaw = Direction.WEST.toYRot();
		float pitch = 0.0f;
		Set<Relative> relatives = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT));
		Vec3 teleportPos = entity instanceof ServerPlayer ? spawnPos.subtract(0.0D, 1.0D, 0.0D) : spawnPos;

		cir.setReturnValue(new TeleportTransition(target, teleportPos, Vec3.ZERO, yaw, pitch, relatives,
				TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)));
	}
}
