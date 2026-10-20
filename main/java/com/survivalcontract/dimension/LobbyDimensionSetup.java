package com.survivalcontract.dimension;

import com.survivalcontract.SurvivalContract;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.*;
import java.nio.file.*;

/**
 * Handles setting up the custom lobby dimension from bundled region files.
 *
 * Flow:
 * 1. On SERVER_STARTING (before dimensions load), check if the lobby dimension's
 *    region/entities/poi folders exist in the world save at
 *    <world>/dimensions/survival-contract/lobby/
 * 2. If not, copy the bundled .mca files from the mod jar's
 *    data/survival-contract/lobby/ resource path into those folders.
 * 3. On SERVER_STARTED, grab the ServerLevel for our custom dimension.
 */
public class LobbyDimensionSetup {

	/** Resource key for our lobby dimension, keyed into Registries.DIMENSION. */
	public static final ResourceKey<Level> LOBBY_LEVEL_KEY = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.fromNamespaceAndPath("survival-contract", "lobby")
		);

	/** Jar-internal path prefix where bundled .mca files live. */
	private static final String LOBBY_RESOURCE_PREFIX = "data/survival-contract/lobby/";

	/** Relative path (from world save dir) where dimension region files live. */
	private static final String LOBBY_DIM_REL_PATH = "dimensions" + File.separator
			+ "survival-contract" + File.separator + "lobby";

	/** Known mca file names per subdirectory (pre-cataloged). */
	private static final String[][] MCA_FILES = {
			// region
			{"r.0.0.mca", "r.0.-1.mca", "r.0.1.mca", "r.-1.0.mca", "r.-1.-1.mca", "r.-1.1.mca"},
			// entities
			{"r.0.0.mca", "r.0.-1.mca", "r.-1.0.mca", "r.-1.-1.mca"},
			// poi
			{"r.0.0.mca", "r.0.-1.mca", "r.0.1.mca", "r.-1.0.mca", "r.-1.-1.mca", "r.-1.1.mca"}
	};

	/** Sub-folders we need to populate. */
	private static final String[] MCA_SUBDIRS = {"region", "entities", "poi"};

	public static void register() {
		// Load config (spawn coords) + copy files BEFORE the server initializes dimensions
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			// 大厅模式判定（专用服务器看 config；单人看存档标记文件），必须先于一切大厅逻辑
			LobbyMode.init(server);
			if (!LobbyMode.isActive()) {
				SurvivalContract.LOGGER.info("Lobby mode disabled: skip lobby dimension injection.");
				return;
			}
			LobbyConfig.load();
			injectRegionFiles(server);
		});

		// Grab the ServerLevel AFTER dimensions are ready
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!LobbyMode.isActive()) {
				// 大厅模式关闭：不绑定任何大厅维度（lobbyLevel 保持 null，所有传送自动短路）
				return;
			}
			try {
				ServerLevel lobby = server.getLevel(LOBBY_LEVEL_KEY);
				if (lobby != null) {
					LobbyManager.getInstance().setLobbyLevel(lobby);
					SurvivalContract.LOGGER.info("Custom lobby dimension loaded successfully! Spawn at {} {}",
							LobbyManager.LOBBY_SPAWN, lobby.dimension());
				} else {
					// Fallback: use overworld
					SurvivalContract.LOGGER.warn("Could not load custom lobby dimension, using overworld as fallback.");
					LobbyManager.getInstance().setLobbyLevel(server.overworld());
				}
			} catch (Exception e) {
				SurvivalContract.LOGGER.error("Failed to load lobby dimension", e);
				LobbyManager.getInstance().setLobbyLevel(server.overworld());
			}
		});
	}

	/**
	 * 注入大厅地图（public 便于自测复跑）：
	 * <ol>
	 *   <li>优先：config/survival-contract/lobby/ 下的存档 zip（内含 region/entities/poi 的 .mca，
	 *       支持嵌套文件夹）</li>
	 *   <li>其次：同目录下直接放的 region/entities/poi 文件夹</li>
	 *   <li>兜底：mod JAR 内置大厅地图</li>
	 * </ol>
	 * 始终覆盖写入：修复此前错误生成垃圾区块的世界。
	 * 具体逻辑已抽到 {@link FixedMapInjector}（供附属玩法复用）。
	 */
	public static void injectRegionFiles(MinecraftServer server) {
		FixedMapInjector.inject(server, "survival-contract", "lobby", LOBBY_RESOURCE_PREFIX, MCA_FILES,
				LobbyConfig.findLobbyZip(), LobbyConfig.lobbyDir(), "大厅地图");
	}
}
