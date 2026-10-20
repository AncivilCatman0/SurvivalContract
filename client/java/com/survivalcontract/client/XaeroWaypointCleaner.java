package com.survivalcontract.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.survivalcontract.SurvivalContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Xaero 的小地图（Xaero's Minimap）与世界地图（Xaero's World Map）适配：
 * 每局游戏结束回到大厅后，删除 Xaero 数据目录下竞技场维度
 * （survival-contract:arena / arena_nether / arena_end）对应的文件夹——
 * 文件夹内含该维度的全部手动存点、死亡点与已探索地图瓦片，随文件夹一并清除；
 * 大厅/原版维度的数据不受影响，下一局从零重新记录。
 *
 * 真实目录结构（用户实例 26.2 + Xaero minimap 26.5.1 / world-map 1.46.1 实测）：
 *   <gameDir>/xaero/minimap/<世界名>/dim%survival-contract$arena(/mw$<serverHash>_<dim>.txt)
 *   <gameDir>/xaero/world-map/<世界名>/survival-contract$arena(/mw$<serverHash>/*.png)
 * 维度 id 的 ":" 在 Xaero 里编码为 "$"（minimap 目录还带 "dim%" 前缀），
 * 旧版 Xaero 使用 <gameDir>/XaeroWaypoints 与 <gameDir>/XaeroWorldMap 两根，一并兼容。
 *
 * 触发：ClientState 收到 sync 阶段切换时调用——
 *   · 进入 LOBBY（游戏结束，PLAYING→FINISHED→LOBBY）：多轮延迟清扫
 *   · 进入 PLAYING（新一局开始）：立即清扫兜底
 *   · 断开连接：3 秒后再兜底一次
 *
 * 历史根因（实机 latest.log + 磁盘证据确认）：游戏结束实际阶段序列是
 * PLAYING→FINISHED（5 秒结算）→LOBBY，旧触发条件只认 PLAYING→LOBBY，
 * 回大厅清扫从未被注册；且 removed=0 时无任何日志导致无法察觉。
 *
 * 纯文件操作（无 Xaero 类依赖，Xaero 未装时静默跳过）。
 */
public class XaeroWaypointCleaner {

	/** 结束回大厅后的多轮清扫延迟（tick）：切回大厅同一秒 Xaero 仍在 flush 竞技场数据，故必须延迟且多轮 */
	private static final int[] SWEEP_DELAYS = {100, 400, 1200, 2400, 4800};
	/** 待执行清扫：绝对到期 tick（client tick 单调递增）→ 日志标签 */
	private static final TreeMap<Long, String> pending = new TreeMap<>();
	private static long clientTick = 0;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			clientTick++;
			if (pending.isEmpty()) return;
			// 取出所有到期点逐个执行（同一 tick 可能排了多个兜底）
			List<Map.Entry<Long, String>> due = new ArrayList<>();
			while (!pending.isEmpty() && pending.firstKey() <= clientTick) {
				due.add(pending.pollFirstEntry());
			}
			for (Map.Entry<Long, String> e : due) {
				cleanArenaWaypoints(e.getValue());
			}
		});
		// 断开连接（退档/退服）：Xaero 已全量落盘 → 3 秒后兜底清扫一次
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
				(handler, client) -> scheduleSweep(60, "断连兜底清扫"));
	}

	/** 安排一次延迟清扫（tick 数） */
	public static synchronized void scheduleSweep(int delayTicks, String label) {
		pending.put(clientTick + Math.max(1, delayTicks), label);
	}

	/** 立即清理（新一局开始前用）：同时作废上一局遗留的延迟轮次，防止快速重开局时旧轮次删掉新局数据 */
	public static synchronized void cleanArenaWaypoints() {
		pending.clear();
		cleanArenaWaypoints("新一局开始前兜底清扫");
	}

	/** 游戏结束回大厅：多轮延迟清扫（5s/20s/60s/120s/240s，防 Xaero 切维度 flush 的回写覆盖删除） */
	public static synchronized void scheduleCleanAfterGameEnd() {
		pending.clear();
		for (int i = 0; i < SWEEP_DELAYS.length; i++) {
			pending.put(clientTick + SWEEP_DELAYS[i], "回大厅后第" + (i + 1) + "轮延迟清扫");
		}
		SurvivalContract.LOGGER.info("[Xaero] 已安排回大厅后多轮清扫（5s/20s/60s/120s/240s）");
	}

	private static void cleanArenaWaypoints(String reason) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		int matched = 0;
		int removed = 0;
		List<Path> roots = List.of(
				gameDir.resolve("xaero"),
				gameDir.resolve("XaeroWaypoints"),
				gameDir.resolve("XaeroWorldMap"));
		StringBuilder detail = new StringBuilder();
		for (Path root : roots) {
			if (!Files.isDirectory(root)) continue;
			try (Stream<Path> walk = Files.walk(root, 8)) {
				// 先收集再删除，避免边遍历边删导致流异常；
				// 深度 8 覆盖 backup 与多层世界容器层级；**包含匹配**（维度目录名带容器前缀）
				List<Path> matches = walk.filter(Files::isDirectory)
						.filter(XaeroWaypointCleaner::isArenaDimDir)
						.toList();
				matched += matches.size();
				for (Path dir : matches) {
					if (deleteRecursively(dir)) {
						removed++;
						detail.append("\n    已删: ").append(gameDir.relativize(dir));
					} else {
						detail.append("\n    删除失败(下轮重试): ").append(gameDir.relativize(dir));
					}
				}
			} catch (IOException e) {
				SurvivalContract.LOGGER.warn("[Xaero] 扫描 {} 失败: {}", root, e.toString());
			}
		}
		// 无论是否删到东西都打日志（removed=0 可见性：用于区分"没触发"与"触发了但无残留"）
		SurvivalContract.LOGGER.info("[Xaero] {}：gameDir={}，命中 {} 个竞技场维度目录，删除 {} 个{}",
				reason, gameDir, matched, removed, detail.length() == 0 ? "" : detail.toString());
	}

	/**
	 * 竞技场维度目录判定：把目录名中的 "$" "%" "." 统一归一化为 ":"（Xaero 对维度 id
	 * 冒号的各版本编码变体），再匹配 survival-contract:arena —— 覆盖任意容器前缀
	 * （如 dim%survival-contract$arena、Multiplayer_frp-art.com$survival-contract$arena_nether）。
	 * 大厅维度（survival-contract:lobby）不清理。
	 */
	private static boolean isArenaDimDir(Path dir) {
		String norm = dir.getFileName().toString()
				.replace('$', ':').replace('%', ':').replace('.', ':')
				.toLowerCase();
		if (!norm.contains("survival-contract:arena")) return false;
		return !norm.contains("survival-contract:lobby");
	}

	private static boolean deleteRecursively(Path dir) {
		try (Stream<Path> walk = Files.walk(dir)) {
			// 先删子项再删目录本身（倒序保证父目录最后删）
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException ignored) {}
			});
			return !Files.exists(dir);
		} catch (IOException e) {
			SurvivalContract.LOGGER.warn("[Xaero] 删除 {} 失败: {}", dir, e.toString());
			return false;
		}
	}
}
