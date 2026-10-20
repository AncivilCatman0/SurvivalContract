package com.survivalcontract.task;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Manages per-player exclusive tasks that refresh every 10 minutes.
 * Tasks are divided into S/A/B difficulty tiers with different scores.
 * - Tier S (3 points): Hard tasks, uncommon goals
 * - Tier A (2 points): Medium difficulty
 * - Tier B (1 point): Easy tasks, common goals
 *
 * Tasks should NOT conflict with enabled challenges (e.g., if player can't eat steak, don't give them a "eat steak" task).
 */
public class TaskManager {
	public static final TaskManager INSTANCE = new TaskManager();

	public enum TaskTier { S, A, B }

	public enum TaskType {
		KILL_MOB,        // Kill N of a specific entity type
		CRAFT_ITEM,      // Craft N of an item
		USE_ITEM,        // Use/consume N of an item (eat, place, etc.)
		MINE_BLOCK,      // Mine N of a specific block
		ADVANCEMENT      // Complete an advancement
	}

	public static class PlayerTask {
		public final String id;
		public final String displayName;
		public final TaskTier tier;
		public final TaskType type;
		public final String targetId;     // Entity/item/block ID
		public final int requiredAmount;
		public int currentAmount = 0;
		public boolean completed = false;
		public long expireTick = 0;

		public PlayerTask(String id, String displayName, TaskTier tier, TaskType type,
						  String targetId, int requiredAmount) {
			this.id = id;
			this.displayName = displayName;
			this.tier = tier;
			this.type = type;
			this.targetId = targetId;
			this.requiredAmount = requiredAmount;
		}

		public int getScore() {
			// 专属任务分值全部翻倍，最低 2 分（2026-10-08）
			return switch (tier) {
				case S -> 6;
				case A -> 4;
				case B -> 2;
			};
		}

		/**
		 * 任务的冲突键：与规则的冲突键（Challenge.conflictKeys）求交集即视为冲突。
		 * KILL_MOB（击杀类）与"不能被 XX 杀死"（被杀类）不冲突，返回空集。
		 */
		public java.util.Set<String> conflictKeys() {
			return switch (type) {
				case MINE_BLOCK -> java.util.Set.of("mine:" + targetId);
				case CRAFT_ITEM -> java.util.Set.of("craft:" + targetId, "item:" + targetId);
				case USE_ITEM -> java.util.Set.of("item:" + targetId);
				default -> java.util.Set.of();
			};
		}
	}

	private final Random random = new Random();

	// Per-player active task
	private final Map<UUID, PlayerTask> activeTasks = new HashMap<>();

	// Task history to prevent repeats
	private final Map<UUID, Set<String>> recentTaskIds = new HashMap<>();

	// Per-player task issue count（开局前 2 个任务强制 B 级：任务要求-初始两个只能是最低级难度）
	private final Map<UUID, Integer> issuedCounts = new HashMap<>();

	// Per-player inventory snapshot from last tick (for CRAFT_ITEM detection)
	private final Map<UUID, Map<String, Integer>> lastInventoryCounts = new HashMap<>();
	/** 外部容器（箱子/工作台等）打开期间冻结背包快照 — 关闭界面后的 diff 一次性捕获期间增量，
	 *  修复"在工作台合成/取得任务物品后放进背包格子不计数"的问题 */
	private final Map<UUID, Boolean> invSnapshotFrozen = new HashMap<>();

	/** USE_ITEM 用原版统计（Stats.ITEM_USED）基线差值检测：
	 *  精确覆盖吃/放置/投掷/饮用等全部使用类型，不受中途获得干扰，
	 *  且统计按物品 id 计数（NBT 变体同样计数）。恢复存档后首个 tick 重建基线。 */
	private final Map<UUID, Integer> useStatBaselines = new HashMap<>();

	// Per-player soul sand valley stay tick counter (for FIND_QUARTERS task)
	private final Map<UUID, Long> soulSandValleyTicks = new HashMap<>();

	// ============ 任务抢夺（rule_task_rush）状态 ============
	/** 共享任务已发放轮数（前 2 轮强制 B 级，与常规模式一致） */
	private int rushIssuedCount = 0;
	/** 上一次全员统一刷新的任务 tick（同一 tick 多人触发只刷新一次） */
	private long lastRushIssueTick = Long.MIN_VALUE;

	// Task time limit (10 minutes per task)
	private static final long TASK_LIFETIME_TICKS = 12000L; // 10 分钟（20 ticks/秒）

	private MinecraftServer server;

	private TaskManager() {}

	public static TaskManager getInstance() {
		return INSTANCE;
	}

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		activeTasks.clear();
		recentTaskIds.clear();
		issuedCounts.clear();
	}

	public void onServerStop(MinecraftServer server) {
		this.server = null;
	}

	public PlayerTask getActiveTask(UUID playerId) {
		return activeTasks.get(playerId);
	}

	/**
	 * Issue a new task to a player. Called when game starts or after task completion/expiry.
	 * Must NOT conflict with player's enabled challenges.
	 * Must NOT be an overly hard task like killing slimes or witches.
	 */
	public void issueNewTask(ServerPlayer player) {
		GameStateManager gameState = GameStateManager.getInstance();

		// Don't issue tasks in lobby
		if (gameState.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
			return;
		}

		// 走私犯与检察官 / 文明时代 / 附属玩法：无专属任务
		if (gameState.isStowaway() || gameState.isCivilization() || gameState.isAddon()) {
			return;
		}

		// 任务抢夺（全局规则 rule_task_rush）：全员共享同一任务——
		// 由本 tick 首个触发者（开局首个空任务玩家/任意完成者/任意过期者）带动全员统一刷新
		if (gameState.isRuleActive("rule_task_rush")) {
			issueRushTasksToAll(player.level().getServer());
			return;
		}

		// Get player's challenges to check for conflicts
		Set<String> enabledChallenges = ChallengeManager.getInstance().getPlayerChallenges(player.getUUID());

		// Build candidate task pools based on player's dimension
		// 下界专属任务（S 级）只在玩家已进入下界后才会进入候选池：
		// 任务每 10 分钟（或完成后）才刷新一次 → 进下界不会立刻刷新出新任务，符合"进下界后才开始发放"
		// 下界/末地判定改为竞技场维度（PLAYING 世界是每局新建的 arena 维度组）
		boolean inNether = player.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA_NETHER;
		boolean inEnd = player.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA_END;

		List<TaskCandidate> candidates = new ArrayList<>();

		if (inNether) {
			// 玩家还在下界时刷新任务 → 只刷下界相关任务
			candidates.addAll(generateNetherTasks(enabledChallenges));
		} else {
			// Tier B tasks (easy, 1 point)
			candidates.addAll(generateKillMobTasks(enabledChallenges, false));
			candidates.addAll(generateCraftItemTasks(enabledChallenges, false));
			candidates.addAll(generateUseItemTasks(enabledChallenges, false));
			candidates.addAll(generateMineBlockTasks(enabledChallenges, false));

			// Tier A tasks (medium, 2 points)
			candidates.addAll(generateKillMobTasks(enabledChallenges, true));
			candidates.addAll(generateCraftItemTasks(enabledChallenges, true));
			candidates.addAll(generateMineBlockTasks(enabledChallenges, true));
		}

		// 冲突过滤：任务的目标物品/方块与玩家已选规则的禁止项冲突 → 不进候选池
		// （例：选了"不能吃牛排"就不会刷出"吃 N 个牛排"类任务；选了"不能获得钻石"就不会刷钻石矿挖掘任务）
		com.survivalcontract.challenge.ChallengeManager cm = com.survivalcontract.challenge.ChallengeManager.getInstance();
		cm.ensureRegistered();
		candidates.removeIf(c -> {
			for (String chId : enabledChallenges) {
				com.survivalcontract.challenge.Challenge ch = cm.getChallenge(chId);
				if (ch == null) continue;
				for (String key : ch.getConflictKeys()) {
					if (c.task.conflictKeys().contains(key)) return true;
				}
			}
			return false;
		});

		if (candidates.isEmpty()) {
			// Fallback: give a basic task
			candidates.add(new TaskCandidate(new PlayerTask("kill_zombies_3",
					"击杀 3 只僵尸", TaskTier.B, TaskType.KILL_MOB, "minecraft:zombie", 3)));
		}

		// Filter out recent tasks to avoid repeats
		Set<String> recent = recentTaskIds.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
		candidates.removeIf(c -> recent.contains(c.task.id));
		if (candidates.isEmpty()) {
			// 去重过滤后可能为空（近期任务太多）→ 兜底基础任务，避免随机取空崩溃
			candidates.add(new TaskCandidate(new PlayerTask("kill_zombies_3",
					"击杀 3 只僵尸", TaskTier.B, TaskType.KILL_MOB, "minecraft:zombie", 3)));
		}

		// 开局前 2 个任务强制最低级（B 级 1 分），之后恢复正常 S/A/B 随机
		int issued = issuedCounts.merge(player.getUUID(), 1, Integer::sum);
		if (issued <= 2) {
			candidates.removeIf(c -> c.task.tier != TaskTier.B);
			if (candidates.isEmpty()) {
				candidates.add(new TaskCandidate(new PlayerTask("kill_zombies_3",
						"击杀 3 只僵尸", TaskTier.B, TaskType.KILL_MOB, "minecraft:zombie", 3)));
			}
		}

		// Pick random candidate
		TaskCandidate chosen = candidates.get(random.nextInt(candidates.size()));
		PlayerTask task = chosen.task;

		// 新任务换目标 → 丢弃旧的使用统计基线（下个 tick 按新目标重建）
		useStatBaselines.remove(player.getUUID());

		// Set expiry time
		task.expireTick = player.level().getGameTime() + TASK_LIFETIME_TICKS;

		// Issue task
		activeTasks.put(player.getUUID(), task);
		recent.add(task.id);

		// Keep recent set bounded
		if (recent.size() > 20) {
			// Remove oldest entries (simple approach)
			Set<String> newRecent = new HashSet<>(recent);
			while (newRecent.size() > 15) {
				String toRemove = newRecent.iterator().next();
				newRecent.remove(toRemove);
			}
			recentTaskIds.put(player.getUUID(), newRecent);
		}

		SurvivalContract.LOGGER.info("Issued task {} to player {} (tier {})",
				task.displayName, player.getName().getString(), task.tier);

		// 即时推送任务文本到客户端 HUD（避免等最长 5 秒的 sync 周期，期间显示"暂无任务"）
		com.survivalcontract.network.NetworkRegistry.sendTaskUpdate(player);
	}

	/**
	 * 道具立即刷新当前专属任务（神的谎言「任务刷新券」右键使用）。
	 * 不走 10 分钟过期/完成等待，直接重掷；旧任务 id 进入近期列表防刷回同一个。
	 * 走私犯/文明模式无专属任务；任务抢夺为全员共享任务，不支持单人刷新。
	 *
	 * @return true=已换成另一个任务；false=当前无任务/模式不允许
	 */
	public boolean refreshTaskNow(ServerPlayer player) {
		GameStateManager gs = GameStateManager.getInstance();
		if (gs.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return false;
		if (gs.isStowaway() || gs.isCivilization()) return false;
		if (gs.isRuleActive("rule_task_rush")) return false;
		PlayerTask old = activeTasks.get(player.getUUID());
		if (old == null) return false;
		// 旧目标相关的统计基线/停留计数一并丢弃（issueNewTask 会再清一次使用基线，这里兜底）
		useStatBaselines.remove(player.getUUID());
		soulSandValleyTicks.remove(player.getUUID());
		issueNewTask(player);
		PlayerTask now = activeTasks.get(player.getUUID());
		// 极端情况：候选被规则/近期列表过滤光后走兜底任务，旧任务恰好也是该兜底 → 再掷一次
		if (now != null && now.id.equals(old.id)) {
			recentTaskIds.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).remove(old.id);
			issueNewTask(player);
			now = activeTasks.get(player.getUUID());
		}
		return now != null && !now.id.equals(old.id);
	}

	/**
	 * 任务抢夺：给所有在线玩家发放同一份任务（各自独立的进度副本）。
	 * 同一 tick 内只统一刷新一次（多人同批完成/过期时，避免重复掷骰导致任务不一致）。
	 */
	private void issueRushTasksToAll(MinecraftServer server) {
		// 走私犯与检察官 / 文明时代：无专属任务（任务抢夺规则同样停用）
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.isStowaway() || gsm.isCivilization()) return;
		long now = server.getTickCount();
		if (now == lastRushIssueTick) return;
		lastRushIssueTick = now;

		PlayerTask template = pickRushTaskTemplate(server);
		if (template == null) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerTask copy = new PlayerTask(template.id, template.displayName, template.tier,
					template.type, template.targetId, template.requiredAmount);
			copy.expireTick = p.level().getGameTime() + TASK_LIFETIME_TICKS;
			activeTasks.put(p.getUUID(), copy);
			recentTaskIds.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(copy.id);
			// 新任务换目标 → 丢弃旧的使用统计基线/地形停留计数（下个 tick 按新目标重建）
			useStatBaselines.remove(p.getUUID());
			soulSandValleyTicks.remove(p.getUUID());
			com.survivalcontract.network.NetworkRegistry.sendTaskUpdate(p);
		}
		SurvivalContract.LOGGER.info("[任务抢夺] 全员统一任务: {}", template.displayName);
	}

	/** 任务抢夺：按全员规则并集挑选一份任务模板（对所有人都可行的任务） */
	private PlayerTask pickRushTaskTemplate(MinecraftServer server) {
		Set<String> unionChallenges = new HashSet<>();
		Set<String> unionRecent = new HashSet<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			unionChallenges.addAll(ChallengeManager.getInstance().getPlayerChallenges(p.getUUID()));
			unionRecent.addAll(recentTaskIds.getOrDefault(p.getUUID(), Set.of()));
		}

		List<TaskCandidate> candidates = new ArrayList<>();
		candidates.addAll(generateKillMobTasks(unionChallenges, false));
		candidates.addAll(generateCraftItemTasks(unionChallenges, false));
		candidates.addAll(generateUseItemTasks(unionChallenges, false));
		candidates.addAll(generateMineBlockTasks(unionChallenges, false));
		candidates.addAll(generateKillMobTasks(unionChallenges, true));
		candidates.addAll(generateCraftItemTasks(unionChallenges, true));
		candidates.addAll(generateMineBlockTasks(unionChallenges, true));

		// 冲突过滤：任务必须对全员都可行（任一玩家的禁止项命中即排除）
		com.survivalcontract.challenge.ChallengeManager cm = com.survivalcontract.challenge.ChallengeManager.getInstance();
		cm.ensureRegistered();
		candidates.removeIf(c -> {
			for (String chId : unionChallenges) {
				com.survivalcontract.challenge.Challenge ch = cm.getChallenge(chId);
				if (ch == null) continue;
				for (String key : ch.getConflictKeys()) {
					if (c.task.conflictKeys().contains(key)) return true;
				}
			}
			return false;
		});
		// 去重过滤：近期任务对全员都算近期
		candidates.removeIf(c -> unionRecent.contains(c.task.id));
		if (candidates.isEmpty()) {
			candidates.add(new TaskCandidate(new PlayerTask("kill_zombies_3",
					"击杀 3 只僵尸", TaskTier.B, TaskType.KILL_MOB, "minecraft:zombie", 3)));
		}

		// 与常规模式一致：前 2 轮任务强制最低级（B 级 1 分）
		rushIssuedCount++;
		if (rushIssuedCount <= 2) {
			candidates.removeIf(c -> c.task.tier != TaskTier.B);
			if (candidates.isEmpty()) {
				candidates.add(new TaskCandidate(new PlayerTask("kill_zombies_3",
						"击杀 3 只僵尸", TaskTier.B, TaskType.KILL_MOB, "minecraft:zombie", 3)));
			}
		}

		return candidates.get(random.nextInt(candidates.size())).task;
	}

	/**
	 * Check and update task progress when a mob is killed
	 */
	public void onMobKilled(ServerPlayer player, String entityId) {
		PlayerTask task = activeTasks.get(player.getUUID());
		if (task == null || task.completed) return;
		if (task.type != TaskType.KILL_MOB) return;
		if (!task.targetId.equals(entityId)) return;

		task.currentAmount++;
		checkCompletion(player, task);
	}

	/**
	 * Check and update task progress when an item is crafted/used
	 */
	public void onItemAction(ServerPlayer player, String itemId, TaskType actionType) {
		PlayerTask task = activeTasks.get(player.getUUID());
		if (task == null || task.completed) return;
		if (task.type != actionType) return;
		if (!task.targetId.equals(itemId)) return;

		task.currentAmount++;
		checkCompletion(player, task);
	}

	/**
	 * 挖掘方块进度（原版自然方块任务）
	 */
	public void onBlockMined(ServerPlayer player, String blockId) {
		PlayerTask task = activeTasks.get(player.getUUID());
		if (task == null || task.completed) return;
		if (task.type != TaskType.MINE_BLOCK) return;
		if (!task.targetId.equals(blockId)) return;

		task.currentAmount++;
		checkCompletion(player, task);
	}

	private void checkCompletion(ServerPlayer player, PlayerTask task) {
		if (task.currentAmount >= task.requiredAmount && !task.completed) {
			task.completed = true;

			// Award score based on tier
			int score = task.getScore();
			GameStateManager.getInstance().addScore(player.getUUID(), score);

			// 神的谎言：完成任务 → 平民获得指认权 / 使者获得降罪权
			GameStateManager.getInstance().grantGodsLiesTaskReward(player);

			// 神的谎言（第五十九批）：首次完成 A/S 级任务 → 发放神谕纸符（每人每局最多一次）
			GameStateManager.getInstance().grantGodsLiesRelic(player,
					task.tier == TaskTier.S || task.tier == TaskTier.A);

			// Task completion sound
			player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

			// Player notification
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
					"survival-contract.msg.task_complete", taskDisplayName(task), score));

			SurvivalContract.LOGGER.info("Player {} completed task {} ({} points)",
					player.getName().getString(), task.displayName, score);

			// Issue next task after a short delay (or immediately)
			// Give player a moment to react
		}
	}

	/**
	 * Tick-based expiry check + CRAFT_ITEM inventory diff + biome stay detection
	 */
	public void tick(MinecraftServer server) {
		long now = server.getTickCount();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID pid = player.getUUID();
			PlayerTask task = activeTasks.get(pid);
			// 兜底：PLAYING 阶段玩家没有任务（或已过期被清）→ 立即发新任务
			if (task == null || task.completed) {
				if (GameStateManager.getInstance().getCurrentPhase() == GameStateManager.GamePhase.PLAYING) {
					issueNewTask(player);
				}
				continue;
			}

			// --- CRAFT_ITEM: inventory diff ---
			// 自己背包界面：正常 diff；外部容器界面：冻结快照（不实时更新），
			// 关闭界面回到背包的下一 tick diff 一次性捕获容器期间的获得增量
			// （USE_ITEM 不再用 diff：改用原版使用统计基线，见下方 use-stat 段）
			if (task.type == TaskType.CRAFT_ITEM) {
				if (player.containerMenu == player.inventoryMenu) {
					Map<String, Integer> currentCounts = countInventoryItems(player);
					Map<String, Integer> prev = lastInventoryCounts.put(pid, currentCounts);
					invSnapshotFrozen.put(pid, false);
					if (prev != null) {
						// 支持物品标签型目标（如 minecraft:planks = 任意木板）：按标签求和匹配
						int p = countMatching(prev, task.targetId);
						int c = countMatching(currentCounts, task.targetId);
						if (c > p) {
							int delta = c - p;
							for (int i = 0; i < delta; i++) {
								onItemAction(player, task.targetId, TaskType.CRAFT_ITEM);
							}
						}
					}
				} else if (!Boolean.TRUE.equals(invSnapshotFrozen.get(pid))) {
					// 首次打开外部容器：冻结（此前最后一次背包快照保持不变）
					invSnapshotFrozen.put(pid, true);
				}
				// 冻结期间不动快照
			}

			// --- USE_ITEM: 原版使用统计基线差值 ---
			// 统计按物品 id 计数（NBT 变体同 id 同计），覆盖进食完成、方块放置、珍珠投掷、药水饮用等全部类型
			if (task.type == TaskType.USE_ITEM) {
				int cur = getUseStat(player, task.targetId);
				if (cur >= 0) {
					Integer base = useStatBaselines.get(pid);
					if (base == null) {
						// 首个 tick 建立基线（开局发任务、完成换任务、恢复存档都走这里）
						useStatBaselines.put(pid, cur);
					} else if (cur > base) {
						useStatBaselines.put(pid, cur);
						task.currentAmount += cur - base;
						checkCompletion(player, task);
					} else if (cur < base) {
						// 统计异常回退（不应发生）→ 重新基线，避免负差值吞进度
						useStatBaselines.put(pid, cur);
					}
				}
			}

			// --- Soul Sand Valley stay task (FIND_QUARTERS) ---
			if (task.id.startsWith("find_quarters")) {
				if (player.level().dimension() == com.survivalcontract.dimension.ArenaWorldManager.ARENA_NETHER
						&& player.level().getBiome(player.blockPosition())
								.is(net.minecraft.world.level.biome.Biomes.SOUL_SAND_VALLEY)) {
					long ticksHere = soulSandValleyTicks.merge(pid, 1L, Long::sum);
					if (ticksHere >= 600) { // 30 秒
						task.currentAmount++;
						soulSandValleyTicks.remove(pid);
						checkCompletion(player, task);
					}
				} else {
					soulSandValleyTicks.remove(pid);
				}
			}
		}

		for (Map.Entry<UUID, PlayerTask> entry : new ArrayList<>(activeTasks.entrySet())) {
			PlayerTask task = entry.getValue();

			if (task.completed) {
				// Issue new task immediately after completion
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					issueNewTask(player);
				} else {
					activeTasks.remove(entry.getKey());
				}
				continue;
			}

			if (now >= task.expireTick) {
				// Task expired, no points awarded
				SurvivalContract.LOGGER.info("Task expired for player: {}", task.displayName);
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					issueNewTask(player);
				} else {
					activeTasks.remove(entry.getKey());
				}
			}
		}
	}

	/** 原版使用统计：玩家对目标物品的累计使用次数（物品不存在/异常返回 -1） */
	private static int getUseStat(ServerPlayer player, String targetId) {
		try {
			var item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(targetId));
			if (item == net.minecraft.world.item.Items.AIR) return -1;
			return player.getStats().getValue(net.minecraft.stats.Stats.ITEM_USED, item);
		} catch (Exception e) {
			return -1;
		}
	}

	/** 统计玩家背包中每种物品的数量（忽略空槽） */
	private Map<String, Integer> countInventoryItems(ServerPlayer player) {
		Map<String, Integer> counts = new HashMap<>();
		net.minecraft.world.Container inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			counts.merge(id, stack.getCount(), Integer::sum);
		}
		return counts;
	}

	/** 统计快照中匹配任务目标的物品总数（支持具体物品 id 与物品标签，如 minecraft:planks = 任意木板） */
	private static int countMatching(Map<String, Integer> counts, String targetId) {
		int total = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			if (itemMatchesTarget(e.getKey(), targetId)) total += e.getValue();
		}
		return total;
	}

	/** 任务目标匹配：精确物品 id；注册表中不存在的目标 id 视为物品标签（如 minecraft:planks 泛指所有木板） */
	private static boolean itemMatchesTarget(String itemId, String targetId) {
		if (itemId.equals(targetId)) return true;
		try {
			net.minecraft.resources.Identifier target = net.minecraft.resources.Identifier.parse(targetId);
			if (BuiltInRegistries.ITEM.containsKey(target)) return false; // 具体物品 → 上面已精确匹配
			net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag =
					net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, target);
			net.minecraft.world.item.Item item = BuiltInRegistries.ITEM
					.getValue(net.minecraft.resources.Identifier.parse(itemId));
			if (item == net.minecraft.world.item.Items.AIR) return false;
			return item.builtInRegistryHolder().is(tag);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 任务显示名（按类型用原版实体/物品/方块翻译键 → 客户端按自身语言渲染）。
	 * 无法按注册表解析的目标（物品标签等）回退生成时的简中名。
	 */
	public static net.minecraft.network.chat.Component taskDisplayName(PlayerTask t) {
		if (t.id.startsWith("find_quarters")) {
			return net.minecraft.network.chat.Component.translatable("survival-contract.task.find_quarters");
		}
		net.minecraft.network.chat.Component target =
				targetDisplayComponent(t.type, t.targetId, t.displayName);
		return switch (t.type) {
			case KILL_MOB -> net.minecraft.network.chat.Component.translatable(
					"survival-contract.task.kill", target, t.requiredAmount);
			case CRAFT_ITEM -> net.minecraft.network.chat.Component.translatable(
					"survival-contract.task.craft", target, t.requiredAmount);
			case USE_ITEM -> net.minecraft.network.chat.Component.translatable(
					"survival-contract.task.use", target, t.requiredAmount);
			case MINE_BLOCK -> net.minecraft.network.chat.Component.translatable(
					"survival-contract.task.mine", target, t.requiredAmount);
			default -> target;
		};
	}

	/** 任务目标的可翻译显示组件：实体→entity.minecraft.x / 物品→item.minecraft.x / 方块→block.minecraft.x */
	private static net.minecraft.network.chat.Component targetDisplayComponent(
			TaskType type, String targetId, String fallback) {
		try {
			var id = net.minecraft.resources.Identifier.parse(targetId);
			if (type == TaskType.KILL_MOB) {
				if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
					return net.minecraft.network.chat.Component.translatable(
							BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescriptionId());
				}
			} else {
				if (BuiltInRegistries.ITEM.containsKey(id)) {
					return net.minecraft.network.chat.Component.translatable(
							BuiltInRegistries.ITEM.getValue(id).getDescriptionId());
				}
				if (BuiltInRegistries.BLOCK.containsKey(id)) {
					return net.minecraft.network.chat.Component.translatable(
							BuiltInRegistries.BLOCK.getValue(id).getDescriptionId());
				}
			}
		} catch (Exception ignored) {}
		return net.minecraft.network.chat.Component.literal(fallback);
	}

	/**
	 * Clear all active tasks (when game ends)
	 */
	public void resetAll() {
		activeTasks.clear();
		recentTaskIds.clear();
		issuedCounts.clear();
		lastInventoryCounts.clear();
		invSnapshotFrozen.clear();
		useStatBaselines.clear();
		soulSandValleyTicks.clear();
		rushIssuedCount = 0;
		lastRushIssueTick = Long.MIN_VALUE;
	}

	// ============ 会话持久化（退出重进保留局内状态）所需的存取器 ============
	public Map<UUID, PlayerTask> getAllActiveTasks() { return new HashMap<>(activeTasks); }
	public Map<UUID, Integer> getAllIssuedCounts() { return new HashMap<>(issuedCounts); }
	public Map<UUID, Set<String>> getAllRecentTaskIds() { return new HashMap<>(recentTaskIds); }

	/** 恢复一个已存在的任务（不做冲突/去重检查，保持退出前的原状） */
	public void restoreTask(UUID playerId, PlayerTask task) {
		activeTasks.put(playerId, task);
	}
	public void restoreIssuedCount(UUID playerId, int count) { issuedCounts.put(playerId, count); }
	public void restoreRecentTaskIds(UUID playerId, Set<String> ids) { recentTaskIds.put(playerId, new HashSet<>(ids)); }

	// ==================== TASK GENERATION HELPERS ====================

	private record TaskCandidate(PlayerTask task) {}

	private List<TaskCandidate> generateKillMobTasks(Set<String> challenges, boolean hardMode) {
		List<TaskCandidate> tasks = new ArrayList<>();

		// Easy mobs (Tier B) - slimes and witches excluded per requirement #49
		String[][] easyMobs = {
				{"minecraft:zombie", "击杀僵尸"},
				{"minecraft:skeleton", "击杀骷髅"},
				{"minecraft:creeper", "击杀苦力怕"},
				{"minecraft:spider", "击杀蜘蛛"},
				{"minecraft:cow", "击杀牛"},
				{"minecraft:pig", "击杀猪"},
				{"minecraft:sheep", "击杀羊"},
				{"minecraft:chicken", "击杀鸡"}
		};

		String[][] hardMobs = {
				{"minecraft:husk", "击杀尸壳"},
				{"minecraft:stray", "击杀流髑"},
				{"minecraft:zombie_villager", "击杀僵尸村民"},
				{"minecraft:vindicator", "击杀卫道士"},
				{"minecraft:evoker", "击杀唤魔者"}
		};

		String[][] pool = hardMode ? hardMobs : easyMobs;
		TaskTier tier = hardMode ? TaskTier.A : TaskTier.B;
		int baseAmount = hardMode ? 2 : 3;

		for (String[] mob : pool) {
			String id = "kill_" + mob[0].replace("minecraft:", "") + "_" + baseAmount;
			tasks.add(new TaskCandidate(new PlayerTask(id, mob[1] + " " + baseAmount + " 只",
					tier, TaskType.KILL_MOB, mob[0], baseAmount)));
		}

		return tasks;
	}

	private List<TaskCandidate> generateCraftItemTasks(Set<String> challenges, boolean hardMode) {
		List<TaskCandidate> tasks = new ArrayList<>();

		String[][] easyCrafts = {
				{"minecraft:wooden_sword", "木剑"},
				{"minecraft:wooden_pickaxe", "木镐"},
				{"minecraft:stick", "木棍"},
				{"minecraft:planks", "木板"},
				{"minecraft:furnace", "熔炉"},
				{"minecraft:chest", "箱子"},
				{"minecraft:crafting_table", "工作台"}
		};

		String[][] hardCrafts = {
				{"minecraft:iron_sword", "铁剑"},
				{"minecraft:iron_pickaxe", "铁镐"},
				{"minecraft:bow", "弓"},
				{"minecraft:shield", "盾牌"},
				{"minecraft:bucket", "水桶"},
				{"minecraft:compass", "指南针"}
		};

		String[][] pool = hardMode ? hardCrafts : easyCrafts;
		TaskTier tier = hardMode ? TaskTier.A : TaskTier.B;
		int amount = hardMode ? 1 : 2;

		// Check if challenge blocks these items
		for (String[] item : pool) {
			// Check "no_diamond" etc - don't add task for blocked items
			if (challenges.contains("no_crafting_table") && item[0].equals("minecraft:crafting_table")) continue;
			if (challenges.contains("no_chest") && item[0].equals("minecraft:chest")) continue;

			String id = "craft_" + item[0].replace("minecraft:", "") + "_" + amount;
			tasks.add(new TaskCandidate(new PlayerTask(id, "合成 " + item[1] + " x" + amount,
					tier, TaskType.CRAFT_ITEM, item[0], amount)));
		}

		return tasks;
	}

	private List<TaskCandidate> generateUseItemTasks(Set<String> challenges, boolean hardMode) {
		List<TaskCandidate> tasks = new ArrayList<>();

		String[][] easyUses = {
				{"minecraft:bread", "面包"},
				{"minecraft:baked_potato", "烤马铃薯"},
				{"minecraft:apple", "苹果"},
				{"minecraft:stone", "放置石头"},
				{"minecraft:torch", "放置火把"}
		};

		TaskTier tier = TaskTier.B;
		int amount = hardMode ? 2 : 3;

		for (String[] item : easyUses) {
			// Skip if challenge blocks this food
			if (challenges.contains("no_eat_bread") && item[0].equals("minecraft:bread")) continue;
			if (challenges.contains("no_eat_potato") && item[0].equals("minecraft:baked_potato")) continue;
			if (challenges.contains("no_eat_apple") && item[0].equals("minecraft:apple")) continue;

			String id = "use_" + item[0].replace("minecraft:", "") + "_" + amount;
			tasks.add(new TaskCandidate(new PlayerTask(id, "使用 " + item[1] + " x" + amount,
					tier, TaskType.USE_ITEM, item[0], amount)));
		}

		return tasks;
	}

	/**
	 * 挖掘自然方块任务（原版统计数据）
	 * B 级：圆石/泥土/原木/沙子；A 级：煤矿/铁矿；S 级：钻石矿/绿宝石矿
	 */
	private List<TaskCandidate> generateMineBlockTasks(Set<String> challenges, boolean hardMode) {
		List<TaskCandidate> out = new ArrayList<>();
		if (hardMode) {
			out.add(new TaskCandidate(new PlayerTask("mine_coal_16", "挖掘 16 个煤矿石",
					TaskTier.A, TaskType.MINE_BLOCK, "minecraft:coal_ore", 16)));
			out.add(new TaskCandidate(new PlayerTask("mine_iron_12", "挖掘 12 个铁矿石",
					TaskTier.A, TaskType.MINE_BLOCK, "minecraft:iron_ore", 12)));
			out.add(new TaskCandidate(new PlayerTask("mine_diamond_3", "挖掘 3 个钻石矿石",
					TaskTier.S, TaskType.MINE_BLOCK, "minecraft:diamond_ore", 3)));
			out.add(new TaskCandidate(new PlayerTask("mine_emerald_3", "挖掘 3 个绿宝石矿石",
					TaskTier.S, TaskType.MINE_BLOCK, "minecraft:emerald_ore", 3)));
		} else {
			out.add(new TaskCandidate(new PlayerTask("mine_cobble_32", "挖掘 32 个圆石",
					TaskTier.B, TaskType.MINE_BLOCK, "minecraft:stone", 32)));
			out.add(new TaskCandidate(new PlayerTask("mine_dirt_24", "挖掘 24 个泥土",
					TaskTier.B, TaskType.MINE_BLOCK, "minecraft:dirt", 24)));
			out.add(new TaskCandidate(new PlayerTask("mine_log_16", "挖掘 16 个原木",
					TaskTier.B, TaskType.MINE_BLOCK, "minecraft:oak_log", 16)));
			out.add(new TaskCandidate(new PlayerTask("mine_sand_16", "挖掘 16 个沙子",
					TaskTier.B, TaskType.MINE_BLOCK, "minecraft:sand", 16)));
		}
		return out;
	}

	private List<TaskCandidate> generateNetherTasks(Set<String> challenges) {
		List<TaskCandidate> tasks = new ArrayList<>();

		tasks.add(new TaskCandidate(new PlayerTask("kill_blaze_2", "击杀 2 只烈焰人",
				TaskTier.S, TaskType.KILL_MOB, "minecraft:blaze", 2)));
		tasks.add(new TaskCandidate(new PlayerTask("kill_wither_skeleton_3", "击杀 3 只凋零骷髅",
				TaskTier.S, TaskType.KILL_MOB, "minecraft:wither_skeleton", 3)));
		tasks.add(new TaskCandidate(new PlayerTask("find_quarters_1", "在灵魂沙峡谷停留 30 秒",
				TaskTier.S, TaskType.KILL_MOB, "minecraft:soul_sand_valley", 1)));
		tasks.add(new TaskCandidate(new PlayerTask("mine_quartz_12", "挖掘 12 个下界石英矿石",
				TaskTier.B, TaskType.MINE_BLOCK, "minecraft:nether_quartz_ore", 12)));
		tasks.add(new TaskCandidate(new PlayerTask("kill_zombified_piglin_4", "击杀 4 只僵尸猪灵",
				TaskTier.A, TaskType.KILL_MOB, "minecraft:zombified_piglin", 4)));

		return tasks;
	}
}
