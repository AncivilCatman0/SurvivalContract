package com.survivalcontract.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.survivalcontract.SurvivalContract;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 大厅配置（config/survival-contract/）：
 * <ul>
 *   <li>{@code lobby-config.json} — 大厅出生点坐标（spawnX/spawnY/spawnZ），首次启动自动生成默认值</li>
 *   <li>{@code lobby/} 文件夹 — 放入存档 zip（内含 region/*.mca，可选 entities/poi）
 *       即可替换内置大厅地图；也支持直接放 region/entities/poi 文件夹</li>
 * </ul>
 */
public class LobbyConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 内置默认出生点（与旧版硬编码一致） */
	public static final BlockPos DEFAULT_SPAWN = new BlockPos(9, -63, 7);

	/** 当前大厅出生点（由 lobby-config.json 覆盖） */
	public static BlockPos spawn = DEFAULT_SPAWN;

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("survival-contract");
	}

	public static Path lobbyDir() { return configDir().resolve("lobby"); }

	public static Path configFile() { return configDir().resolve("lobby-config.json"); }

	/** 生成缺失的文件夹/配置文件，并加载出生点坐标（服务端 STARTING 阶段调用） */
	public static void load() {
		try {
			Files.createDirectories(lobbyDir());
			Path cfg = configFile();
			if (!Files.exists(cfg)) {
				JsonObject def = new JsonObject();
				def.addProperty("spawnX", DEFAULT_SPAWN.getX());
				def.addProperty("spawnY", DEFAULT_SPAWN.getY());
				def.addProperty("spawnZ", DEFAULT_SPAWN.getZ());
				Files.writeString(cfg, GSON.toJson(def));
				SurvivalContract.LOGGER.info("Generated lobby config: {}", cfg);
			}
			// 解析出生点
			JsonObject json = GSON.fromJson(Files.readString(cfg), JsonObject.class);
			if (json != null && json.has("spawnX") && json.has("spawnY") && json.has("spawnZ")) {
				spawn = new BlockPos(json.get("spawnX").getAsInt(),
						json.get("spawnY").getAsInt(), json.get("spawnZ").getAsInt());
				SurvivalContract.LOGGER.info("Lobby spawn from config: {}", spawn);
			}
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to load lobby config, using default spawn {}", DEFAULT_SPAWN, e);
			spawn = DEFAULT_SPAWN;
		}
		// 回写 LobbyManager（teleportToLobby 读的是这里）
		LobbyManager.LOBBY_SPAWN = spawn;
	}

	/** 配置的大厅地图 zip（按文件名排序取第一个 .zip；无则返回 null） */
	public static Path findLobbyZip() {
		try {
			if (!Files.isDirectory(lobbyDir())) return null;
			try (Stream<Path> list = Files.list(lobbyDir())) {
				return list.filter(p -> Files.isRegularFile(p)
						&& p.getFileName().toString().toLowerCase().endsWith(".zip"))
						.sorted().findFirst().orElse(null);
			}
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("Failed to scan lobby config folder", e);
			return null;
		}
	}

	/** 测试/工具用：直接覆盖出生点 */
	public static void setSpawn(BlockPos pos) { spawn = pos; }

	/** 测试用：把当前出生点写回配置文件 */
	public static void save() {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("spawnX", spawn.getX());
			json.addProperty("spawnY", spawn.getY());
			json.addProperty("spawnZ", spawn.getZ());
			Files.writeString(configFile(), GSON.toJson(json));
		} catch (IOException e) {
			SurvivalContract.LOGGER.warn("Failed to save lobby config", e);
		}
	}
}
