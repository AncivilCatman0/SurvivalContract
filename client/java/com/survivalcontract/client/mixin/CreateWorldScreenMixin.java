package com.survivalcontract.client.mixin;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.dimension.LobbyMode;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 用「sc大厅」世界类型创建世界时，在集成服务器启动<b>之前</b>向存档写入标记文件
 * {@code survival-contract/lobby-world.marker}；服务端 SERVER_STARTING 读到此标记才启用大厅模式。
 * <p>
 * 拦截点选在 createLevelFromExistingSettings 调用之前：此时存档目录已创建、
 * 服务器尚未启动，不存在"服务端先读标记、客户端后写"的竞态。
 * <p>
 * require=0：NeoForge + 信雅互联环境下该创建流程可能被改造，注入失败不致命。
 */
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

	private static final ResourceKey<WorldPreset> SC_LOBBY_PRESET = ResourceKey.create(
			Registries.WORLD_PRESET,
			Identifier.fromNamespaceAndPath(SurvivalContract.MOD_ID, "sc_lobby"));

	/**
	 * 标题界面「创建新的世界」走 openFresh → openCreateWorldScreen，原版恒传 {@link WorldPresets#NORMAL}
	 * 作为初始世界类型。仅在该值等于原版默认预设时替换为 sc大厅，使创建界面默认选中 sc大厅。
	 * <ul>
	 *   <li>演示/测试世界传 FLAT_ALL_DIMENSIONS，不替换；</li>
	 *   <li>「重建世界」走 createFromExisting，不经此方法，不替换。</li>
	 * </ul>
	 */
	@ModifyArg(
			method = "openCreateWorldScreen",
			at = @At(value = "INVOKE", target =
					"Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;<init>("
							+ "Lnet/minecraft/client/Minecraft;"
							+ "Ljava/lang/Runnable;"
							+ "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationContext;"
							+ "Ljava/util/Optional;"
							+ "Ljava/util/OptionalLong;"
							+ "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldCallback;)V"),
			index = 3,
			require = 0
	)
	private static Optional<ResourceKey<WorldPreset>> survivalcontract$defaultPreset(
			Optional<ResourceKey<WorldPreset>> original) {
		if (original.isPresent() && WorldPresets.NORMAL.equals(original.get())) {
			SurvivalContract.LOGGER.info("[LobbyMode] 创建世界初始世界类型：默认 → sc大厅");
			return Optional.of(SC_LOBBY_PRESET);
		}
		return original;
	}

	@Redirect(
			method = "createNewWorld",
			at = @At(value = "INVOKE", target =
					"Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;"
							+ "createLevelFromExistingSettings("
							+ "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
							+ "Lnet/minecraft/server/ReloadableServerResources;"
							+ "Lnet/minecraft/core/LayeredRegistryAccess;"
							+ "Lnet/minecraft/world/level/storage/LevelDataAndDimensions$WorldDataAndGenSettings;"
							+ "Ljava/util/Optional;)V"),
			require = 0
	)
	private void survivalcontract$writeLobbyMarker(
			WorldOpenFlows flows,
			LevelStorageSource.LevelStorageAccess access,
			ReloadableServerResources resources,
			LayeredRegistryAccess<RegistryLayer> layers,
			LevelDataAndDimensions.WorldDataAndGenSettings data,
			Optional<GameRules> rules) {
		try {
			WorldCreationUiState uiState =
					((CreateWorldScreenAccessor) (Object) this).survivalcontract$getUiState();
			Holder<WorldPreset> holder = uiState.getWorldType().preset();
			ResourceKey<WorldPreset> selected = holder == null ? null : holder.unwrapKey().orElse(null);
			if (SC_LOBBY_PRESET.equals(selected)) {
				Path markerDir = access.getLevelPath(LevelResource.ROOT).resolve(LobbyMode.MARKER_DIR);
				Files.createDirectories(markerDir);
				Files.writeString(markerDir.resolve(LobbyMode.MARKER_FILE),
						"survival-contract sc-lobby world marker\n",
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				SurvivalContract.LOGGER.info("[LobbyMode] 已写入 sc大厅 存档标记：{}/{}",
						LobbyMode.MARKER_DIR, LobbyMode.MARKER_FILE);
			}
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[LobbyMode] 写入大厅存档标记失败（该存档将按普通世界加载）", e);
		}
		flows.createLevelFromExistingSettings(access, resources, layers, data, rules);
	}
}
