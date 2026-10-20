package com.survivalcontract.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 大厅维度雾效覆盖：把远方虚空渲染改为正常生存地图白天的观感。
 * <p>
 * 大厅是浮空岛屿结构，地图边缘之外直接暴露虚空（overworld 虚空平面是深蓝渐变，
 * 正常生存里被地形和距离雾遮挡，浮空地图则一览无余）。
 * 处理方式：在大厅维度把雾色调成白天亮天蓝，并把雾距离拉近 ——
 * 边缘外的虚空被明亮雾色覆盖，观感与正常生存白天的远景一致。
 */
@Mixin(FogRenderer.class)
public abstract class LobbyFogMixin {

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void survivalcontract$lobbyDaylightFog(Camera camera, int renderDistance, DeltaTracker deltaTracker,
			float partialTick, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		if (level == null || cir.getReturnValue() == null) return;
		Identifier dim = level.dimension().identifier();
		if (!"survival-contract".equals(dim.getNamespace()) || !"lobby".equals(dim.getPath())) return;

		FogData data = cir.getReturnValue();
		// 白天天空亮蓝色雾（与正午天际线颜色衔接）
		data.color.set(0.68f, 0.82f, 1.0f, 1.0f);
		// 拉近雾距离：地图边缘外完全被雾覆盖，不再露出深蓝虚空
		data.environmentalStart = 40.0f;
		data.environmentalEnd = 150.0f;
		data.renderDistanceStart = 40.0f;
		data.renderDistanceEnd = 150.0f;
		data.skyEnd = 180.0f;
		data.cloudEnd = 160.0f;
	}
}
