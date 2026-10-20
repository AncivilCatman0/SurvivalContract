package com.survivalcontract.game;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.dimension.ArenaWorldManager;
import com.survivalcontract.profession.ProfessionManager;
import com.survivalcontract.task.TaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 局内会话持久化：把 PLAYING 阶段的完整游戏状态写入存档根目录的 NBT 文件，
 * 服务器重启（单人存档退出到标题再重进）后恢复，避免"退出重进直接回大厅"。
 *
 * <p>独立服务器断开重连时服务器从未停止，内存状态本就保留；
 * 本类额外覆盖单人存档整体重启的场景。</p>
 *
 * <p>恢复时会用<b>保存时的同一个种子</b>重建竞技场维度（不删档），
 * 因此区块内的建造/破坏与玩家进度完整延续。</p>
 */
public final class SessionPersistence {

	private static final String FILE_NAME = "survival-contract-session.nbt";
	/** 跨局免罚特权独立文件（大厅/新局均保留，不随局内快照清除） */
	private static final String IMMUNITY_FILE_NAME = "survival-contract-immunities.nbt";

	private SessionPersistence() {}

	private static Path filePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}

	private static Path immunityPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(IMMUNITY_FILE_NAME);
	}

	/** 保存跨局免罚特权（授予/消耗时调用，保持文件与内存一致） */
	public static void saveImmunities(java.util.Map<UUID, Integer> immunities) {
		try {
			MinecraftServer server = GameStateManager.getInstance().getServer();
			if (server == null) return;
			CompoundTag root = new CompoundTag();
			immunities.forEach((id, v) -> root.putInt(id.toString(), v));
			writeAtomic(root, immunityPath(server));
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("[Session] Failed to save victory immunities", e);
		}
	}

	/**
	 * 原子落盘：先写同目录 {@code .tmp}，再整体替换目标文件。
	 *
	 * <p>直接写目标文件时，若进程在写入途中崩溃/断电，会把快照写成半个 NBT——
	 * 下次启动读取必然抛异常。原子写保证目标文件要么是旧版本、要么是新版本，不会出现半截。</p>
	 */
	private static void writeAtomic(CompoundTag root, Path target) throws IOException {
		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
		Files.createDirectories(target.getParent());
		try {
			NbtIo.writeCompressed(root, tmp);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {}
		}
	}

	/** 恢复跨局免罚特权（服务器启动时调用；文件不存在返回空 map） */
	public static java.util.Map<UUID, Integer> loadImmunities(MinecraftServer server) {
		java.util.Map<UUID, Integer> out = new java.util.HashMap<>();
		try {
			Path path = immunityPath(server);
			if (!Files.exists(path)) return out;
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			for (String key : root.keySet()) {
				UUID id = parseUuid(key);
				if (id != null) out.put(id, root.getIntOr(key, 0));
			}
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("[Session] Failed to load victory immunities", e);
		}
		return out;
	}

	/**
	 * 服务器停止前保存：仅在 PLAYING 阶段有意义的局内状态才保存。
	 * 非 PLAYING（大厅/已结束）会清除快照，避免下一局被旧数据污染。
	 */
	public static void save(MinecraftServer server) {
		try {
			GameStateManager gsm = GameStateManager.getInstance();
			if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
				clear(server);
				return;
			}

			CompoundTag root = new CompoundTag();
			root.putString("phase", gsm.getCurrentPhase().name());
			root.putString("gameMode", gsm.getGameMode().name());
			// 文明时代/神的谎言/走私犯/附属玩法没有最终目标（开局置 null）——
			// 旧实现直接 name() 会 NPE，导致这些玩法退出时永远写不出快照（第八十七批）
			root.putString("finalGoal", gsm.getCurrentFinalGoal() == null ? "" : gsm.getCurrentFinalGoal().name());
			// 玩法（重启后 GameStateManager 是新实例，默认 NORMAL；不保存会导致
			// 文明时代重进时按原版主世界建竞技场 → 四种地形预设全部变成"主世界"）
			root.putString("playStyle", gsm.getPlayStyle().name());
			root.putString("addonStyleId", gsm.getAddonStyleId() == null ? "" : gsm.getAddonStyleId());
			root.putInt("gameDurationMinutes", gsm.getGameDurationMinutes());
			// gameStartTime 是绝对的 tickCount，重启后归零 → 存相对偏移
			root.putLong("gameStartOffset", gsm.getGameStartTime() - server.getTickCount());
			BlockPos spawn = gsm.getPlayingSpawn();
			root.putIntArray("playingSpawn", new int[]{spawn.getX(), spawn.getY(), spawn.getZ()});
			root.putInt("playingBorderRadius", gsm.getPlayingBorderRadius());
			root.putLong("arenaSeed", ArenaWorldManager.currentSeed());
			root.putString("terrainPreset", gsm.getTerrainPreset().name()); // 自定义地形预设（第四十九批）

			root.putBoolean("pvpEnabled", gsm.isPvpEnabled());
			root.putBoolean("keepInventory", gsm.isKeepInventory());
			root.putBoolean("globalNightVision", gsm.isGlobalNightVision());
			root.putBoolean("enderDragonKilled", gsm.isEnderDragonKilled());
			root.putBoolean("witherKilled", gsm.isWitherKilled());
			root.putBoolean("endedByTimeout", gsm.isEndedByTimeout());
			root.putString("winningGoal", gsm.winningGoal() == null ? "" : gsm.winningGoal().name());

			root.put("activeRules", stringList(gsm.getActiveRules()));
			root.put("enabledEvents", stringList(gsm.getEnabledEvents()));

			// ---- 分数 ----
			CompoundTag scores = new CompoundTag();
			gsm.getAllScores().forEach((id, v) -> scores.putInt(id.toString(), v));
			root.put("scores", scores);

			// ---- 队伍 ----
			CompoundTag teams = new CompoundTag();
			gsm.getAllTeams().forEach((id, t) -> teams.putString(id.toString(), t.name()));
			root.put("playerTeams", teams);

			// ---- 固定重生点 ----
			CompoundTag respawns = new CompoundTag();
			gsm.getAllRespawnPoints().forEach((id, p) -> respawns.putIntArray(id.toString(),
					new int[]{p.getX(), p.getY(), p.getZ()}));
			root.put("respawnPoints", respawns);

			// ---- 双禁 ----
			CompoundTag bans = new CompoundTag();
			gsm.getTeamBans().forEach((t, v) -> bans.putString(t.name(), v));
			root.put("teamBans", bans);

			// ---- 每位玩家已选挑战 ----
			CompoundTag challenges = new CompoundTag();
			ChallengeManager.getInstance().getAllPlayerChallenges().forEach((id, ids) ->
					challenges.put(id.toString(), stringList(ids)));
			root.put("challenges", challenges);

			// ---- 个人模式：每玩家独立事件选择 ----
			CompoundTag playerEvents = new CompoundTag();
			gsm.getAllPlayerEnabledEvents().forEach((id, evs) ->
					playerEvents.put(id.toString(), stringList(evs)));
			root.put("playerEvents", playerEvents);

			// ---- 每位玩家职业 ----
			CompoundTag professions = new CompoundTag();
			ProfessionManager.getInstance().getAllProfessions().forEach((id, p) ->
					professions.putString(id.toString(), p.name()));
			root.put("professions", professions);

			// ---- 每人专属任务 ----
			TaskManager tm = TaskManager.getInstance();
			CompoundTag tasks = new CompoundTag();
			tm.getAllActiveTasks().forEach((id, task) -> {
				CompoundTag t = new CompoundTag();
				t.putString("id", task.id);
				t.putString("displayName", task.displayName);
				t.putString("tier", task.tier.name());
				t.putString("type", task.type.name());
				t.putString("targetId", task.targetId);
				t.putInt("requiredAmount", task.requiredAmount);
				t.putInt("currentAmount", task.currentAmount);
				t.putBoolean("completed", task.completed);
				t.putLong("expireTick", task.expireTick);
				tasks.put(id.toString(), t);
			});
			root.put("tasks", tasks);

			CompoundTag issued = new CompoundTag();
			tm.getAllIssuedCounts().forEach((id, c) -> issued.putInt(id.toString(), c));
			root.put("taskIssued", issued);

			CompoundTag recent = new CompoundTag();
			tm.getAllRecentTaskIds().forEach((id, ids) -> recent.put(id.toString(), stringList(ids)));
			root.put("taskRecent", recent);

			// ---- 团队箱 ----
			root.put("teamChests", TeamChestManager.getInstance().saveToNbt());

			// ---- 遭遇事件刷新间隔 + 死亡扣分（全局设置）----
			CompoundTag intervals = new CompoundTag();
			gsm.getAllEventIntervals().forEach((k, r) -> intervals.putIntArray(k, r));
			root.put("eventIntervals", intervals);
			root.putInt("deathPenalty", gsm.getDeathPenalty());

			writeAtomic(root, filePath(server));
			SurvivalContract.LOGGER.info("[Session] Saved in-progress session (phase={}, seed={})",
					gsm.getCurrentPhase(), ArenaWorldManager.currentSeed());
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("[Session] Failed to save session state", e);
		}
	}

	/**
	 * 服务器启动后恢复：若快照存在且是 PLAYING 阶段，则恢复全部状态并重建竞技场维度。
	 *
	 * @return 是否成功恢复了一个进行中的会话
	 */
	public static boolean restore(MinecraftServer server) {
		Path path = filePath(server);
		if (!Files.exists(path)) return false;
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			String phaseName = root.getStringOr("phase", "");
			if (!GameStateManager.GamePhase.PLAYING.name().equals(phaseName)) {
				clear(server);
				return false;
			}

			GameStateManager gsm = GameStateManager.getInstance();
			long seed = root.getLongOr("arenaSeed", 0L);
			String styleName = root.getStringOr("playStyle", "NORMAL");
			// 文明时代 / 附属玩法：运行时状态（疆域占领、大本营实体、固定地图进度）没有持久化，
			// 强行恢复只会得到一个没有疆域/边界的"假对局"（玩家被传到边界外、掉进虚空）。
			// 这类会话统一回大厅重开（第八十七批）
			if ("CIVILIZATION".equals(styleName) || "ADDON".equals(styleName)) {
				SurvivalContract.LOGGER.info("[Session] Play style {} cannot be restored across restarts, returning to LOBBY",
						styleName);
				clear(server);
				return false;
			}
			// 先清运行态（clearRuntimeState 会把地形预设重置为默认），随后再从快照恢复预设——
			// 若顺序颠倒，恢复出的预设会被 clearRuntimeState 重置回主世界（第五十八批）
			gsm.clearRuntimeState();

			// 玩法必须在重建竞技场维度前恢复：ArenaWorldManager.buildArenaLevels 用
			// isCivilization() 决定用自定义地形生成器还是原版主世界生成器（第八十七批）
			try {
				gsm.setPlayStyle(GameStateManager.PlayStyle.valueOf(styleName));
				String addonId = root.getStringOr("addonStyleId", "");
				if (!addonId.isEmpty()) gsm.setAddonStyleId(addonId);
			} catch (IllegalArgumentException ignored) {}

			// 自定义地形预设：必须在重建竞技场维度前恢复（建维度时按预设选生成器——第四十九批）
			try {
				gsm.setTerrainPreset(GameStateManager.TerrainPreset.valueOf(
						root.getStringOr("terrainPreset", "OVERWORLD")));
			} catch (IllegalArgumentException ignored) {}

			// 先把竞技场维度按原种子重建（不删档）——失败则整体回退到大厅
			if (!ArenaWorldManager.restoreArenaWorlds(server, seed)) {
				throw new IllegalStateException("Failed to rebuild arena worlds from saved seed " + seed);
			}
			gsm.setGameMode(GameStateManager.GameMode.valueOf(root.getStringOr("gameMode", "SOLO")));
			String goalName = root.getStringOr("finalGoal", "");
			gsm.setCurrentFinalGoal(goalName.isEmpty() ? null : GameStateManager.FinalGoal.valueOf(goalName));
			gsm.setGameDurationMinutes(root.getIntOr("gameDurationMinutes", 120));
			gsm.setGameStartTime(server.getTickCount() + root.getLongOr("gameStartOffset", 0L));
			gsm.setPlayingBorderRadius(root.getIntOr("playingBorderRadius", 2000));

			int[] spawnArr = root.getIntArray("playingSpawn").orElse(new int[]{0, 64, 0});
			if (spawnArr.length == 3) gsm.setPlayingSpawn(new BlockPos(spawnArr[0], spawnArr[1], spawnArr[2]));

			gsm.setPvpEnabled(root.getBooleanOr("pvpEnabled", false));
			gsm.setKeepInventory(root.getBooleanOr("keepInventory", false));
			gsm.setGlobalNightVision(root.getBooleanOr("globalNightVision", false));
			gsm.resetFlags();
			if (root.getBooleanOr("enderDragonKilled", false)) gsm.markEnderDragonKilled();
			if (root.getBooleanOr("witherKilled", false)) gsm.markWitherKilled();
			gsm.setEndedByTimeout(root.getBooleanOr("endedByTimeout", false));
			String winning = root.getStringOr("winningGoal", "");
			if (!winning.isEmpty()) {
				try { gsm.setWinningGoal(GameStateManager.FinalGoal.valueOf(winning)); } catch (IllegalArgumentException ignored) {}
			}

			gsm.getActiveRules().clear();
			readStringList(root.getListOrEmpty("activeRules")).forEach(r -> gsm.setRuleActive(r, true));
			gsm.getEnabledEvents().clear();
			readStringList(root.getListOrEmpty("enabledEvents")).forEach(e -> gsm.setEventEnabled(e, true));

			// ---- 分数 / 队伍 / 重生点 / 双禁 ----
			CompoundTag scores = root.getCompoundOrEmpty("scores");
			for (String key : scores.keySet()) {
				UUID id = parseUuid(key);
				if (id != null) gsm.setScore(id, scores.getIntOr(key, 0));
			}
			CompoundTag teams = root.getCompoundOrEmpty("playerTeams");
			for (String key : teams.keySet()) {
				UUID id = parseUuid(key);
				if (id == null) continue;
				try { gsm.setPlayerTeam(id, GameStateManager.Team.valueOf(teams.getStringOr(key, "NONE"))); }
				catch (IllegalArgumentException ignored) {}
			}
			CompoundTag respawns = root.getCompoundOrEmpty("respawnPoints");
			for (String key : respawns.keySet()) {
				UUID id = parseUuid(key);
				if (id == null) continue;
				int[] p = respawns.getIntArray(key).orElse(null);
				if (p != null && p.length == 3) gsm.setRespawnPoint(id, new BlockPos(p[0], p[1], p[2]));
			}
			CompoundTag bans = root.getCompoundOrEmpty("teamBans");
			for (String key : bans.keySet()) {
				try { gsm.setTeamBan(GameStateManager.Team.valueOf(key), bans.getStringOr(key, "")); }
				catch (IllegalArgumentException ignored) {}
			}

			// ---- 挑战 / 职业 / 任务 ----
			CompoundTag challenges = root.getCompoundOrEmpty("challenges");
			for (String key : challenges.keySet()) {
				UUID id = parseUuid(key);
				if (id != null) ChallengeManager.getInstance().setPlayerChallenges(id,
						readStringList(challenges.getListOrEmpty(key)));
			}
			CompoundTag playerEvents = root.getCompoundOrEmpty("playerEvents");
			for (String key : playerEvents.keySet()) {
				UUID id = parseUuid(key);
				if (id != null) gsm.setPlayerEnabledEvents(id,
						new java.util.HashSet<>(readStringList(playerEvents.getListOrEmpty(key))));
			}
			CompoundTag professions = root.getCompoundOrEmpty("professions");
			for (String key : professions.keySet()) {
				UUID id = parseUuid(key);
				if (id == null) continue;
				try { ProfessionManager.getInstance().setPlayerProfession(id,
						ProfessionManager.Profession.valueOf(professions.getStringOr(key, "CHEF"))); }
				catch (IllegalArgumentException ignored) {}
			}
			restoreTasks(root);

			// ---- 团队箱 ----
			TeamChestManager.getInstance().loadFromNbt(root.getCompoundOrEmpty("teamChests"));

			// ---- 遭遇事件刷新间隔 + 死亡扣分（全局设置）----
			CompoundTag intervals = root.getCompoundOrEmpty("eventIntervals");
			java.util.Map<String, int[]> intervalMap = new java.util.HashMap<>();
			for (String key : intervals.keySet()) {
				int[] r = intervals.getIntArray(key).orElse(null);
				if (r != null && r.length == 2) intervalMap.put(key, r);
			}
			gsm.setEventIntervals(intervalMap);
			gsm.setDeathPenalty(root.getIntOr("deathPenalty", 0));

			// 最后切阶段（重建维度已在前面完成）
			gsm.setCurrentPhase(GameStateManager.GamePhase.PLAYING);

			SurvivalContract.LOGGER.info("[Session] Restored in-progress session (seed={}, spawn={})",
					seed, gsm.getPlayingSpawn());
			return true;
		} catch (Exception e) {
			SurvivalContract.LOGGER.error("[Session] Failed to restore session, falling back to LOBBY "
					+ "(arena saves are kept untouched)", e);
			// 注意：这里绝不能调用 ArenaWorldManager.teardownArenaWorlds——
			// 恢复失败并不代表磁盘上的竞技场存档坏了，删掉会把玩家整局的建造/破坏全部清零（第八十九批）
			try {
				GameStateManager gsm = GameStateManager.getInstance();
				gsm.clearRuntimeState();
				gsm.setPlayStyle(GameStateManager.PlayStyle.NORMAL);
				gsm.setCurrentFinalGoal(null);
				gsm.setCurrentPhase(GameStateManager.GamePhase.LOBBY);
			} catch (Exception ignored) {}
			quarantine(server);
			return false;
		}
	}

	/** 把无法解析的快照改名隔离（保留现场供排查，同时避免每次启动都重复读取失败） */
	private static void quarantine(MinecraftServer server) {
		try {
			Path path = filePath(server);
			if (!Files.exists(path)) return;
			Path bad = path.resolveSibling(FILE_NAME + ".corrupt");
			Files.deleteIfExists(bad);
			Files.move(path, bad, StandardCopyOption.REPLACE_EXISTING);
			SurvivalContract.LOGGER.warn("[Session] Corrupt session snapshot moved to {}", bad);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Session] Failed to quarantine corrupt snapshot: {}", e.getMessage());
			clear(server);
		}
	}

	private static void restoreTasks(CompoundTag root) {
		TaskManager tm = TaskManager.getInstance();
		CompoundTag tasks = root.getCompoundOrEmpty("tasks");
		for (String key : tasks.keySet()) {
			UUID id = parseUuid(key);
			CompoundTag t = tasks.getCompoundOrEmpty(key);
			if (id == null || t.isEmpty()) continue;
			try {
				TaskManager.PlayerTask task = new TaskManager.PlayerTask(
						t.getStringOr("id", "kill_zombies_3"),
						t.getStringOr("displayName", "击杀 3 只僵尸"),
						TaskManager.TaskTier.valueOf(t.getStringOr("tier", "B")),
						TaskManager.TaskType.valueOf(t.getStringOr("type", "KILL_MOB")),
						t.getStringOr("targetId", "minecraft:zombie"),
						t.getIntOr("requiredAmount", 3));
				task.currentAmount = t.getIntOr("currentAmount", 0);
				task.completed = t.getBooleanOr("completed", false);
				task.expireTick = t.getLongOr("expireTick", 0L);
				tm.restoreTask(id, task);
			} catch (IllegalArgumentException ignored) {}
		}
		CompoundTag issued = root.getCompoundOrEmpty("taskIssued");
		for (String key : issued.keySet()) {
			UUID id = parseUuid(key);
			if (id != null) tm.restoreIssuedCount(id, issued.getIntOr(key, 0));
		}
		CompoundTag recent = root.getCompoundOrEmpty("taskRecent");
		for (String key : recent.keySet()) {
			UUID id = parseUuid(key);
			if (id != null) tm.restoreRecentTaskIds(id, readStringList(recent.getListOrEmpty(key)));
		}
	}

	/** 删除快照文件（游戏正常结束回大厅 / 快照无效时调用） */
	public static void clear(MinecraftServer server) {
		try {
			Files.deleteIfExists(filePath(server));
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Session] Failed to delete session file: {}", e.getMessage());
		}
	}

	private static ListTag stringList(java.util.Collection<String> values) {
		ListTag list = new ListTag();
		for (String v : values) list.add(StringTag.valueOf(v));
		return list;
	}

	private static Set<String> readStringList(ListTag list) {
		Set<String> out = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			String s = list.getStringOr(i, "");
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private static UUID parseUuid(String s) {
		try { return UUID.fromString(s); } catch (Exception e) { return null; }
	}
}