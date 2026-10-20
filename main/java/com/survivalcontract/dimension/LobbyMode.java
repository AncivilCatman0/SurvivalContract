package com.survivalcontract.dimension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.survivalcontract.SurvivalContract;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 大厅模式总开关（每个服务器实例一份，SERVER_STARTING 时判定，STOPPED 时复位）。
 *
 * <h2>判定规则</h2>
 * <ul>
 *   <li><b>专用服务器</b>：读取独立配置文件
 *       {@code config/survival-contract/lobby-mode.json} 的 {@code enabled} 字段，
 *       首次启动自动生成，默认 {@code true}（开启）。</li>
 *   <li><b>单人（集成服务器）</b>：只看存档内标记文件
 *       {@code <存档>/survival-contract/lobby-world.marker}——
 *       仅当世界是用客户端「创建世界 → 世界类型 = sc大厅」创建时，
 *       由客户端在建档瞬间写入。其他世界类型（默认/超平坦/大型生物群系/
 *       其他模组的世界类型）一律不启用大厅：不传送、不发挑战书、不注入大厅维度，
 *       完全等同原版生存，避免与其他模组冲突。</li>
 *   <li><b>自测</b>：{@code SC_SELFTEST=1} 时强制开启（自动化测试的旧存档没有标记）。</li>
 * </ul>
 *
 * 关闭时所有大厅链路（JOIN 传送、AFTER_RESPAWN 改道、LOBBY 阶段 buff/虚空保护、
 * 大厅维度注入与绑定、开局 startGame）都必须通过本类短路。
 */
public final class LobbyMode {

	/** 标记文件相对存档根的目录名（客户端 mixin 写、服务端读，字符串必须保持一致） */
	public static final String MARKER_DIR = "survival-contract";
	/** 标记文件名：存在即代表该单人存档是「sc大厅」世界类型 */
	public static final String MARKER_FILE = "lobby-world.marker";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static boolean forceActiveForTests = false;
	private static boolean initialized = false;
	private static boolean active = false;

	private LobbyMode() {}

	/** 自测框架调用：无视配置/标记强制开启大厅模式 */
	public static void forceActiveForTests() {
		forceActiveForTests = true;
	}

	/** SERVER_STARTING 阶段调用：必须在任何维度注入/传送逻辑之前完成判定 */
	public static synchronized void init(MinecraftServer server) {
		initialized = true;
		active = false;
		try {
			if (forceActiveForTests) {
				active = true;
				SurvivalContract.LOGGER.info("[LobbyMode] 大厅模式：开启（自测强制）");
				return;
			}
			if (server.isDedicatedServer()) {
				active = readDedicatedServerConfig();
				SurvivalContract.LOGGER.info("[LobbyMode] 大厅模式（专用服务器）：{}", active ? "开启" : "关闭");
			} else {
				Path marker = server.getWorldPath(LevelResource.ROOT)
						.resolve(MARKER_DIR).resolve(MARKER_FILE);
				active = Files.isRegularFile(marker);
				SurvivalContract.LOGGER.info("[LobbyMode] 大厅模式（单人存档）：{}（标记文件 {}）",
						active ? "开启" : "关闭", marker);
			}
		} catch (Exception e) {
			active = false;
			SurvivalContract.LOGGER.warn("[LobbyMode] 判定失败，大厅模式默认关闭", e);
		}
	}

	/** 当前服务器是否启用大厅模式；未初始化（服务端生命周期外）保守返回 false */
	public static boolean isActive() {
		return initialized && active;
	}

	/** SERVER_STOPPED 调用：退出存档/关服后复位，避免静态状态污染下一次启动 */
	public static synchronized void reset() {
		initialized = false;
		active = false;
	}

	/** 读取专用服务器开关配置；缺失时生成默认文件（默认开启） */
	private static boolean readDedicatedServerConfig() {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("survival-contract");
			Files.createDirectories(dir);
			Path file = dir.resolve("lobby-mode.json");
			if (!Files.exists(file)) {
				JsonObject def = new JsonObject();
				def.addProperty("_comment",
						"大厅模式总开关（仅对专用服务器生效；单人游戏由创建世界时的「sc大厅」世界类型决定）。"
								+ "false=关闭：玩家不进大厅、不发挑战书、不注入大厅维度，等同原版生存。改后重启服务器生效。");
				def.addProperty("enabled", true);
				Files.writeString(file, GSON.toJson(def));
				SurvivalContract.LOGGER.info("[LobbyMode] 已生成大厅模式配置（默认开启）：{}", file);
				return true;
			}
			JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
			if (json != null && json.has("enabled")) {
				return json.get("enabled").getAsBoolean();
			}
			SurvivalContract.LOGGER.warn("[LobbyMode] {} 缺少 enabled 字段，按默认开启处理", file);
			return true;
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[LobbyMode] 读取大厅模式配置失败，按默认开启处理", e);
			return true;
		}
	}
}
