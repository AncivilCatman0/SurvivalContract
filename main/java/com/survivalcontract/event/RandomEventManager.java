package com.survivalcontract.event;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/**
 * 随机遭遇事件管理器。
 * 每 7-10 分钟随机触发一种，5 秒预警后在目标玩家附近生成事件实体。
 * 实体 10 分钟后自动清除。
 */
public class RandomEventManager {
	public static final RandomEventManager INSTANCE = new RandomEventManager();

	public enum EventType {
		LIGHTNING("雷击", EventTier.B, "⚡", "目标玩家附近落下数道闪电，注意躲避", 5),
		PILLAGER_SQUAD("掠夺者小队", EventTier.S, "🏹", "一队掠夺者手持弩箭向目标发起围攻", 15),
		CAMEL_RIDER("骆驼骑士", EventTier.S, "🐪", "骆驼尸壳搭载铁长矛尸壳与焦骸双骑手，远距离冲刺突刺", 15),
		ZOMBIE_HORSE_CAVALRY("僵尸马骑兵", EventTier.S, "🧟", "一名手持铁长矛的僵尸骑着僵尸马疾驰冲来", 15),
		SKELETON_HORSE_TRAP("陷阱骷髅马", EventTier.S, "🏇", "一只可疑的骷髅马，靠近它将引来雷击并唤醒四名骷髅马骑士", 15),
		SILENT_CREEPER("无声苦力怕", EventTier.S, "💣", "悄无声息接近并爆炸的苦力怕", 15),
		SKELETON_ELITE_SQUAD("骷髅精英队", EventTier.S, "💀", "装备附魔弓与盔甲的骷髅精英小队", 15),
		KILLER_RABBIT("杀手兔", EventTier.A, "🐰", "看似可爱实则凶残的杀手兔（The Killer Rabbit）", 10),
		FOUR_BROTHERS("四郎兄弟", EventTier.A, "🐺", "四只迅捷的狼群协同围猎", 10),
		HUNGER_BOMB("饥饿炸弹", EventTier.S, "🍖", "在目标周围点燃一枚TNT，被爆炸波及且未死亡的玩家将获得30秒饥饿效果", 15),
		CRIMSON_INVASION("猩红入侵", EventTier.S, "🧟", "一只僵尸疣猪兽从下界裂隙涌入，狂暴无比", 15);

		public final String displayName;
		public final EventTier tier;
		public final String icon;
		public final String description;
		/** 选择该事件获得的分数（按难度分级） */
		public final int score;
		EventType(String name, EventTier tier, String icon, String description, int score) {
			this.displayName = name; this.tier = tier; this.icon = icon; this.description = description;
			this.score = score;
		}
	}

	public enum EventTier { S, A, B }

	private final Map<EventType, Long> nextTriggerTime = new HashMap<>();
	private final Random random = new Random();
	private final Map<UUID, Long> entityDespawnTime = new HashMap<>();
	/** 陷阱骷髅马（未唤醒）：horseId → 0，玩家靠近 10 格后触发雷击+骑士变身 */
	private final Set<UUID> trapHorses = new HashSet<>();
	/** 饥饿炸弹事件生成的 TNT：爆炸波及玩家的识别标记 */
	private final Set<UUID> hungerBombTnts = new HashSet<>();
	private final Map<UUID, Map<EventType, Long>> pendingNotifications = new HashMap<>();
	// 个人模式：每玩家独立计时器（ playerId → 事件 → 下次触发 tick），互不影响
	private final Map<UUID, Map<EventType, Long>> playerNextTriggerTime = new HashMap<>();
	private static final long WARNING_TICKS = 20 * 5; // 5 秒预警

	// 预警结束时间（5 秒后触发真正的事件）
	private final Map<UUID, Long> warningEndTicks = new HashMap<>();
	// 预警 Boss Bar（全局唯一一条：新预警直接替换旧条并清掉旧观众，防止多人多事件时顶部堆积；
	// 倒计时进度取最新一次预警）。原版风格：白色 + PROGRESS，与末影龙/凋零一致的外观。
	private net.minecraft.server.level.ServerBossEvent warningBossBar = null;
	private UUID warningBarTarget = null;
	private long warningBarEndTick = 0L;

	/** 事件实体最长存在时间：10 分钟（真实时间） */
	private static final long ENTITY_LIFETIME_TICKS = 20L * 60 * 10;

	private MinecraftServer server;

	private RandomEventManager() {}
	public static RandomEventManager getInstance() { return INSTANCE; }

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		resetTimers();
	}

	public void onServerStop(MinecraftServer server) {
		this.server = null;
		nextTriggerTime.clear();
		entityDespawnTime.clear();
		pendingNotifications.clear();
		playerNextTriggerTime.clear();
		trapHorses.clear();
		hungerBombTnts.clear();
		clearAllBossBars();
	}

	/**
	 * 清理预警 Boss Bar（全局唯一一条）：显式向客户端发送移除包（removeAllPlayers + setVisible(false)），
	 * 并清空预警状态。游戏结束拆维度前必须调用，否则血条会残留到大厅和后续新局。
	 */
	public void clearAllBossBars() {
		if (warningBossBar != null) {
			try {
				warningBossBar.removeAllPlayers();
				warningBossBar.setVisible(false);
			} catch (Exception ignored) {}
		}
		warningBarTarget = null;
		warningEndTicks.clear();
	}

	public void resetTimers() {
		long now = server != null ? server.getTickCount() : 0;
		for (EventType type : EventType.values()) {
			nextTriggerTime.put(type, now + randomTriggerDelay(type));
		}
		playerNextTriggerTime.clear();
		trapHorses.clear();
		hungerBombTnts.clear();
	}

	private long randomTriggerDelay(EventType type) {
		// 刷新间隔为全局可调参数（分钟）：大厅内任意玩家可在挑战书中调整，全员同步。
		// 默认 7-10 分钟；A/B 定义域与保证 A<B 的钳制在 GameStateManager.adjustEventInterval 中
		int[] range = GameStateManager.getInstance().getEventInterval(type.name());
		int minS = range[0] * 60;
		int maxS = Math.max(range[0], range[1]) * 60;
		int seconds = minS + random.nextInt(maxS - minS + 1);
		return seconds * 20L;
	}

	public void tick(MinecraftServer server) {
		this.server = server;
		long now = server.getTickCount();

		// --- Boss Bar 预警倒计时更新（1.0 → 0.0，全局仅一条，展示最新预警）---
		if (warningBarTarget != null && warningBossBar != null) {
			long remaining = warningBarEndTick - now;
			if (remaining <= 0) {
				warningBossBar.removeAllPlayers();
				warningBossBar.setVisible(false);
				warningBarTarget = null;
			} else {
				warningBossBar.setProgress((float) remaining / WARNING_TICKS);
			}
		}
		// 清理已到期的每玩家预警计时（事件实际生成由 pendingNotifications 驱动，互不影响）
		warningEndTicks.values().removeIf(end -> end <= now);

		processNotifications(server, now);
		GameStateManager gsm = GameStateManager.getInstance();
		boolean playing = gsm.getCurrentPhase() == GameStateManager.GamePhase.PLAYING;
		if (playing) {
			// 每玩家独立计时（个人/团队模式统一）：每个玩家只触发自己点亮的事件，目标锁定本人。
			// 双禁事件：被禁队伍成员视为已启用（各自独立计时触发）。
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Map<EventType, Long> timers = playerNextTriggerTime.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
				for (EventType type : EventType.values()) {
					boolean enabled = gsm.isPlayerEventEnabled(p.getUUID(), type.name());
					if (!enabled) {
						GameStateManager.Team banTeam = gsm.getEventBanTeam(type.name());
						if (banTeam != null && gsm.getPlayerTeam(p.getUUID()) == banTeam) enabled = true;
					}
					if (!enabled) continue;
					long due = timers.computeIfAbsent(type, k -> now + randomTriggerDelay(type));
					if (now < due) continue;
					triggerEvent(server, type, now, List.of(p));
					timers.put(type, now + randomTriggerDelay(type));
				}
			}
		}
		// 陷阱骷髅马：任意玩家靠近 10 格 → 雷击唤醒并生成 4 名骷髅马骑士（原版陷阱机制）
			if (!trapHorses.isEmpty() && playing) {
				for (UUID horseId : new ArrayList<>(trapHorses)) {
					Entity horse = null;
					ServerLevel horseLevel = null;
					for (ServerLevel lvl : server.getAllLevels()) {
						Entity e = lvl.getEntity(horseId);
						if (e != null) { horse = e; horseLevel = lvl; break; }
					}
					if (!(horse instanceof SkeletonHorse skelHorse) || horseLevel == null) {
						trapHorses.remove(horseId); // 实体已消失（死亡/过期清除）→ 移除记录
						continue;
					}
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						if (p.level() == horseLevel && p.distanceToSqr(horse) < 10 * 10) {
							triggerSkeletonTrap(horseLevel, skelHorse, p);
							break;
						}
					}
				}
			}
			cleanupExpiredEntities(now);
		}

		/** 陷阱骷髅马被唤醒：雷击原马位置并生成 4 名骷髅马骑士（附魔铁头盔保护III + 附魔弓力量II） */
		private void triggerSkeletonTrap(ServerLevel level, SkeletonHorse horse, ServerPlayer target) {
			trapHorses.remove(horse.getUUID());
			double hx = horse.getX(), hy = horse.getY(), hz = horse.getZ();
			long despawnAt = server.getTickCount() + ENTITY_LIFETIME_TICKS;
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setPos(hx, hy, hz);
				level.addFreshEntity(bolt);
			}
			for (int i = 0; i < 4; i++) {
				SkeletonHorse newHorse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.TRIGGERED);
				Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.TRIGGERED);
				if (newHorse != null && skeleton != null) {
					double ox = hx + (random.nextDouble() * 4 - 2);
					double oz = hz + (random.nextDouble() * 4 - 2);
					newHorse.setPos(ox, hy, oz);
					newHorse.setTamed(true);
					skeleton.setPos(ox, hy + 1, oz);
					// 骷髅骑手：附魔铁头盔（保护III）+ 附魔弓（力量II）
					skeleton.setItemSlot(EquipmentSlot.HEAD,
							enchanted(level, Items.IRON_HELMET, Enchantments.PROTECTION, 3));
					skeleton.setItemSlot(EquipmentSlot.MAINHAND,
							enchanted(level, Items.BOW, Enchantments.POWER, 2));
					skeleton.setTarget(target);
					level.addFreshEntity(newHorse);
					level.addFreshEntity(skeleton);
					skeleton.startRiding(newHorse);
					entityDespawnTime.put(newHorse.getUUID(), despawnAt);
					entityDespawnTime.put(skeleton.getUUID(), despawnAt);
				}
			}
			target.sendSystemMessage(Component.translatable("survival-contract.msg.event_skeleton_horse_awake"));
			target.playSound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, 5.0f, 1.0f);
			SurvivalContract.LOGGER.info("Skeleton horse trap triggered by {}", target.getName().getString());
		}

		/** 半径 radius 格内寻找避雷针（雷击优先目标） */
		private BlockPos findNearbyLightningRod(ServerLevel level, BlockPos center, int radius) {
			for (BlockPos p : BlockPos.betweenClosed(
					center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
				if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) {
					return p.immutable();
				}
			}
			return null;
		}

		/** 从玩家头顶向上扫描 32 格，找第一个顶面完整的方块（完整方块 = 雷击优先目标） */
		private BlockPos findCoverBlockAbove(ServerLevel level, BlockPos playerPos) {
			BlockPos start = playerPos.above(2);
			for (int dy = 0; dy < 32; dy++) {
				BlockPos p = start.above(dy);
				var state = level.getBlockState(p);
				if (!state.isAir() && state.isFaceSturdy(level, p, net.minecraft.core.Direction.UP)) {
					return p.immutable();
				}
			}
			return null;
		}

		/** 该 UUID 是否为遭遇事件生成的实体（白月/金月/赤月等环境效果不作用于事件生物） */
	public boolean isEventEntity(UUID id) {
		return entityDespawnTime.containsKey(id);
	}

	/** 判断指定实体 UUID 是否为饥饿炸弹事件生成的 TNT（ALLOW_DAMAGE 爆炸波及判定用） */
		public boolean isHungerBombTnt(UUID entityId) {
			return hungerBombTnts.contains(entityId);
		}

	/**
	 * 游戏开始时调用：重置计时器。
	 * 自测模式（SC_SELFTEST=1）下，把第一个启用事件的触发提前到 3 秒后，
	 * 便于实机验证预警 Boss Bar。
	 */
	public void onGameStarted(MinecraftServer server) {
		resetTimers();
		if (System.getenv("SC_SELFTEST") != null) {
			long now = server.getTickCount();
			GameStateManager gsm = GameStateManager.getInstance();
			// 团队模式：加速全局第一个启用事件；个人模式：加速每个玩家自己的第一个启用事件
			if (gsm.isTeamMode()) {
				boolean first = true;
				for (EventType type : EventType.values()) {
					if (gsm.isEventEnabled(type.name())) {
						nextTriggerTime.put(type, first ? now + 60 : nextTriggerTime.get(type));
						first = false;
					}
				}
			} else {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					boolean firstP = true;
					Map<EventType, Long> timers = playerNextTriggerTime.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
					for (EventType type : EventType.values()) {
						if (gsm.isPlayerEventEnabled(p.getUUID(), type.name())) {
							timers.put(type, firstP ? now + 60 : now + randomTriggerDelay(type));
							firstP = false;
						}
					}
				}
			}
			SurvivalContract.LOGGER.info("[SELFTEST] event trigger accelerated (first enabled event in 3s)");
		}
	}

	/** 事件名（语言文件键；en_us.json 提供英文，客户端按自身语言解析） */
	private static Component eventName(EventType type) {
		return Component.translatable("survival-contract.event." + type.name() + ".name");
	}

	private void triggerEvent(MinecraftServer server, EventType type, long now, List<ServerPlayer> candidates) {
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		if (candidates.isEmpty()) return;
		ServerPlayer target = candidates.get(random.nextInt(candidates.size()));

		// 给目标玩家和附近队友发预警系统消息
		Component warnMsg = Component.translatable("survival-contract.msg.event_warning",
				type.icon, eventName(type));
		target.sendSystemMessage(warnMsg);
		for (ServerPlayer p : players) {
			if (p != target && p.distanceTo(target) < 30) {
				p.sendSystemMessage(Component.translatable("survival-contract.msg.event_warning_near",
						eventName(type), target.getName().getString()));
			}
		}

		// 预警展示：大标题 + 原版风格白色 Boss Bar 倒计时 + 音效
		Component title = Component.translatable("survival-contract.msg.event_title", eventName(type));
		Component subtitle = Component.translatable("survival-contract.msg.event_subtitle_5s");
		showTitle(target, title, subtitle);
		for (ServerPlayer p : players) {
			if (p != target && p.distanceTo(target) < 30) {
				showTitle(p, title, subtitle);
			}
		}
		warningEndTicks.put(target.getUUID(), now + WARNING_TICKS);

		// Boss Bar：全局唯一一条（新预警替换旧条并清掉旧观众，防顶部堆积）；白色 + PROGRESS 样式，倒计时 1.0 → 0.0
		if (warningBossBar == null) {
			warningBossBar = new net.minecraft.server.level.ServerBossEvent(
					java.util.UUID.randomUUID(), title, net.minecraft.world.BossEvent.BossBarColor.WHITE,
					net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
			warningBossBar.setDarkenScreen(false);
			warningBossBar.setPlayBossMusic(false);
		}
		warningBossBar.removeAllPlayers();
		warningBossBar.setName(title);
		warningBossBar.setProgress(1.0f);
		warningBossBar.setVisible(true);
		warningBossBar.addPlayer(target);
		for (ServerPlayer p : players) {
			if (p != target && p.distanceTo(target) < 30) warningBossBar.addPlayer(p);
		}
		warningBarTarget = target.getUUID();
		warningBarEndTick = now + WARNING_TICKS;

		// 预警音效
		target.playSound(net.minecraft.sounds.SoundEvents.BELL_BLOCK, 5.0f, 0.5f);

		pendingNotifications.computeIfAbsent(target.getUUID(), k -> new HashMap<>())
				.put(type, now + WARNING_TICKS);
	}

	private static void showTitle(ServerPlayer p, Component title, Component subtitle) {
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 60, 10));
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
	}

	private void processNotifications(MinecraftServer server, long now) {
		for (Map.Entry<UUID, Map<EventType, Long>> playerEntry : new ArrayList<>(pendingNotifications.entrySet())) {
			for (Map.Entry<EventType, Long> eventEntry : new ArrayList<>(playerEntry.getValue().entrySet())) {
				if (now >= eventEntry.getValue()) {
						ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
						if (player != null) {
							spawnEventEntity(player, eventEntry.getKey());
							warningEndTicks.remove(playerEntry.getKey());
						}
						playerEntry.getValue().remove(eventEntry.getKey());
					}
			}
			if (playerEntry.getValue().isEmpty()) pendingNotifications.remove(playerEntry.getKey());
		}
	}

	/**
	 * 构造带单个附魔的物品（26.2 数据组件 API）。
	 * 用于事件实体装备：create() 不走 finalizeSpawn，天生装备不会自动应用，必须显式给。
	 */
	private static ItemStack enchanted(ServerLevel level, Item item, ResourceKey<Enchantment> key, int lv) {
		ItemStack stack = new ItemStack(item);
		Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mut.set(holder, lv);
		stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
		return stack;
	}

	/** 在玩家附近找安全的实体生成点：脚下实心 + 上方空气 + 半径 r 内随机 */
	private BlockPos findSafeSpawnNear(ServerPlayer player, int r) {
		ServerLevel level = (ServerLevel) player.level();
		double px = player.getX(), pz = player.getZ();
		for (int attempt = 0; attempt < 8; attempt++) {
			int ox = random.nextInt(r * 2) - r;
			int oz = random.nextInt(r * 2) - r;
			int fx = (int) px + ox;
			int fz = (int) pz + oz;
			for (int y = 120; y > 2; y--) {
				BlockPos bp = new BlockPos(fx, y, fz);
				var state = level.getBlockState(bp);
				var below = level.getBlockState(bp.below());
				if (!state.isSolid() && below.isSolid()) {
					return bp.above(); // 玩家站在实心块上的空气里
				}
			}
		}
		return player.blockPosition();
	}

	/** 洞穴出生点：玩家附近随机偏移，从玩家脚下向下扫描空气腔（脚/头为空气、脚下实心）。
	 *  事件生物有概率生成在洞穴里；找不到返回 null（调用方回退地表）。 */
	private BlockPos findCaveSpawnNear(ServerPlayer player, int r) {
		ServerLevel level = (ServerLevel) player.level();
		double px = player.getX(), pz = player.getZ();
		int py = (int) player.getY();
		for (int attempt = 0; attempt < 10; attempt++) {
			int ox = random.nextInt(r * 2) - r;
			int oz = random.nextInt(r * 2) - r;
			int fx = (int) px + ox;
			int fz = (int) pz + oz;
			for (int y = py - 1; y > Math.max(py - 24, level.getMinY() + 1); y--) {
				BlockPos bp = new BlockPos(fx, y, fz);
				var here = level.getBlockState(bp);
				var above = level.getBlockState(bp.above());
				var below = level.getBlockState(bp.below());
				if (!here.isSolid() && !above.isSolid() && below.isSolid()) {
					return bp; // 洞穴空气腔
				}
			}
		}
		return null;
	}

	/** 事件生物窒息保护：生成点必须提供 3 格净空（脚/头/头顶均非固体、非液体、不窒息，脚下实心），
	 *  否则高体形生物（骆驼/马）的头部会卡进洞穴顶窒息。不满足时从原点向上最多 8 格寻找，
	 *  仍失败回退玩家地表安全点。 */
	private BlockPos ensureSpawnHeadroom(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (isClearForEventMob(level, pos)) return pos;
		for (int y = pos.getY() + 1; y <= pos.getY() + 8; y++) {
			BlockPos bp = new BlockPos(pos.getX(), y, pos.getZ());
			if (isClearForEventMob(level, bp)) return bp;
		}
		BlockPos surface = findSafeSpawnNear(player, 8);
		return surface != null ? surface : pos;
	}

	/** 3 格净空判定：y..y+2 均非固体、非液体、不窒息，y-1 为实心地面 */
	private boolean isClearForEventMob(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos.below()).isSolid()) return false;
		for (int dy = 0; dy < 3; dy++) {
			BlockPos bp = pos.above(dy);
			var s = level.getBlockState(bp);
			if (s.isSolid() || s.liquid() || s.isSuffocating(level, bp)) return false;
		}
		return true;
	}

	private void spawnEventEntity(ServerPlayer player, EventType type) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos safe = findSafeSpawnNear(player, 8);
		// 50% 概率尝试洞穴出生点（周围 12 格、玩家脚下 24 格内的空气腔），失败自动回退地表
		if (random.nextBoolean()) {
			BlockPos cave = findCaveSpawnNear(player, 12);
			if (cave != null) safe = cave;
		}
		// 窒息保护：所选点必须对高体形生物安全（脚/头/头顶 3 格非固体、非液体、不窒息），
		// 不满足（如 2 格洞穴气腔会卡住骆驼/马的头导致窒息）→ 从该点向上找净空，仍失败回退地表点
		safe = ensureSpawnHeadroom(level, safe, player);
		// 用 server tick 时钟（与 cleanupExpiredEntities 的 now 一致，避免 gameTime 偏移）
		long despawnAt = server.getTickCount() + ENTITY_LIFETIME_TICKS;
		double x = safe.getX() + 0.5, y = safe.getY(), z = safe.getZ() + 0.5;

		SurvivalContract.LOGGER.info("Spawning event {} for {} at ({},{},{})",
				type.displayName, player.getName().getString(), x, y, z);

		switch (type) {
			case LIGHTNING -> {
				// 雷击目标选择：8 格半径内有避雷针 → 劈避雷针；否则玩家头顶有完整方块 → 劈方块顶部；
				// 都没有 → 劈玩家脚下（原逻辑）
				double lx = player.getX(), ly = player.getY(), lz = player.getZ();
				double strikeX = lx, strikeY = ly, strikeZ = lz;
				BlockPos rodPos = findNearbyLightningRod(level, player.blockPosition(), 8);
				if (rodPos != null) {
					strikeX = rodPos.getX() + 0.5;
					strikeY = rodPos.getY();
					strikeZ = rodPos.getZ() + 0.5;
					player.sendSystemMessage(Component.translatable("survival-contract.msg.event_lightning_rod"));
				} else {
					BlockPos coverTop = findCoverBlockAbove(level, player.blockPosition());
					if (coverTop != null) {
						strikeX = coverTop.getX() + 0.5;
						strikeY = coverTop.getY() + 1;
						strikeZ = coverTop.getZ() + 0.5;
						player.sendSystemMessage(Component.translatable("survival-contract.msg.event_lightning_cover"));
					}
				}
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
						strikeX, strikeY, strikeZ, 10, 0.1, 0.1, 0.1, 0.01);
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setPos(strikeX, strikeY, strikeZ);
					level.addFreshEntity(bolt);
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_lightning"));
				player.playSound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, 5.0f, 0.8f);
			}
			case PILLAGER_SQUAD -> {
				for (int i = 0; i < 3 + random.nextInt(2); i++) {
					var pillager = EntityTypes.PILLAGER.create(level, EntitySpawnReason.TRIGGERED);
					if (pillager != null) {
						double ox = x + random.nextDouble() * 8 - 4;
						double oz = z + random.nextDouble() * 8 - 4;
						pillager.setPos(ox, y, oz);
						// 掠夺者必须手持弩（create 不走 finalizeSpawn，天生装备不会自动给）
						pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
						pillager.setTarget(player); // 仇恨优先锁定事件触发者
						level.addFreshEntity(pillager);
						entityDespawnTime.put(pillager.getUUID(), despawnAt);
					}
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_pillager_squad"));
				player.playSound(net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 2.0f, 1.2f);
			}
			case CAMEL_RIDER -> {
				// 骆驼骑士：骆驼尸壳（1.21.10+）固定搭载两名骑手——铁长矛尸壳 + 焦骸（骆驼双座位）
				// 移速保持原版（用户反馈对齐尸壳后过快）
				CamelHusk camel = EntityTypes.CAMEL_HUSK.create(level, EntitySpawnReason.TRIGGERED);
				Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.TRIGGERED);
				Parched parched = EntityTypes.PARCHED.create(level, EntitySpawnReason.TRIGGERED);
				if (camel != null && husk != null && parched != null) {
					camel.setPos(x, y, z);
					husk.setPos(x, y + 1, z);
					parched.setPos(x, y + 1, z);
					husk.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
							new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SPEAR));
					husk.setTarget(player); // 仇恨优先锁定事件触发者
					parched.setTarget(player);
					level.addFreshEntity(camel);
					level.addFreshEntity(husk);
					level.addFreshEntity(parched);
					husk.startRiding(camel);
					parched.startRiding(camel); // 骆驼双座：第二骑手焦骸
					entityDespawnTime.put(camel.getUUID(), despawnAt);
					entityDespawnTime.put(husk.getUUID(), despawnAt);
					entityDespawnTime.put(parched.getUUID(), despawnAt);
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_camel_rider"));
				player.playSound(net.minecraft.sounds.SoundEvents.CAMEL_HUSK_AMBIENT, 2.0f, 1.0f);
			}
			case ZOMBIE_HORSE_CAVALRY -> {
				// 僵尸马骑兵：僵尸马（已驯服）搭载手持铁长矛的僵尸
				ZombieHorse horse = EntityTypes.ZOMBIE_HORSE.create(level, EntitySpawnReason.TRIGGERED);
				Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
				if (horse != null && zombie != null) {
					horse.setPos(x, y, z);
					horse.setTamed(true);
					zombie.setPos(x, y + 1, z);
					zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
							new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SPEAR));
					zombie.setTarget(player); // 仇恨优先锁定事件触发者
					level.addFreshEntity(horse);
					level.addFreshEntity(zombie);
					zombie.startRiding(horse);
					entityDespawnTime.put(horse.getUUID(), despawnAt);
					entityDespawnTime.put(zombie.getUUID(), despawnAt);
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_zombie_horse"));
				player.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_HORSE_AMBIENT, 2.0f, 1.0f);
			}
			case SKELETON_HORSE_TRAP -> {
				// 参考原版陷阱骷髅马：先生成一只安静的骷髅马（无骑手），
				// 玩家靠近 10 格后雷击唤醒并变身 4 名骷髅马骑士（见 tick 的 trapHorses 检测）
				SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.TRIGGERED);
				if (horse != null) {
					horse.setPos(x, y, z);
					horse.setTamed(true);
					level.addFreshEntity(horse);
					entityDespawnTime.put(horse.getUUID(), despawnAt);
					trapHorses.add(horse.getUUID());
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_skeleton_horse_hint"));
				player.playSound(net.minecraft.sounds.SoundEvents.SKELETON_HORSE_AMBIENT, 2.0f, 1.0f);
			}
			case SILENT_CREEPER -> {
				Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.TRIGGERED);
				if (creeper != null) {
					creeper.setPos(x, y, z);
					creeper.setSilent(true); // silent：正常苦力怕有声音，我们禁掉
					creeper.setTarget(player); // 仇恨优先锁定事件触发者
					level.addFreshEntity(creeper);
					entityDespawnTime.put(creeper.getUUID(), despawnAt);
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_silent_creeper"));
			}
			case SKELETON_ELITE_SQUAD -> {
				// 精英小队：1 流髑(Stray) + 1 焦骸(Parched) + 1 沼骸(Bogged)，全员持弓
				List<net.minecraft.world.entity.EntityType<? extends Monster>> squad =
						List.of(EntityTypes.STRAY, EntityTypes.PARCHED, EntityTypes.BOGGED);
				for (var et : squad) {
					Monster e = et.create(level, EntitySpawnReason.TRIGGERED);
					if (e != null) {
						e.setPos(x + random.nextDouble() * 8 - 4, y, z + random.nextDouble() * 8 - 4);
						e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
						e.setTarget(player);
						level.addFreshEntity(e);
						entityDespawnTime.put(e.getUUID(), despawnAt);
					}
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_skeleton_elite"));
				player.playSound(net.minecraft.sounds.SoundEvents.WITHER_SKELETON_AMBIENT, 2.0f, 1.0f);
			}
			case KILLER_RABBIT -> {
				// 两只杀手兔协同进攻
				for (int i = 0; i < 2; i++) {
					Rabbit rabbit = EntityTypes.RABBIT.create(level, EntitySpawnReason.TRIGGERED);
					if (rabbit != null) {
						rabbit.setPos(x + (i == 0 ? 0 : 1.2), y, z + (i == 0 ? 0 : 0.6));
						// 用 Mixin Invoker 调用 private setVariant(EVIL)
						if (rabbit instanceof com.survivalcontract.mixin.RabbitAccessor accessor) {
							accessor.survivalcontract$invokeSetVariant(Rabbit.Variant.EVIL);
						}
						rabbit.setCustomName(Component.literal("Killer Rabbit"));
						rabbit.setCustomNameVisible(true);
						rabbit.setTarget(player); // 仇恨优先锁定事件触发者（杀手兔可攻击玩家）
						level.addFreshEntity(rabbit);
						entityDespawnTime.put(rabbit.getUUID(), despawnAt);
					}
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_killer_rabbit"));
				player.playSound(net.minecraft.sounds.SoundEvents.RABBIT_HURT, 2.0f, 0.5f);
			}
			case FOUR_BROTHERS -> {
				for (int i = 0; i < 4; i++) {
					Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.TRIGGERED);
						if (wolf != null) {
							wolf.setPos(x + random.nextDouble() * 6 - 3, y, z + random.nextDouble() * 6 - 3);
							wolf.setAggressive(true);
							level.addFreshEntity(wolf);
							wolf.setTarget(player);
							// 四郎兄弟加强：无限时间的迅捷 II（-1 = INFINITE_DURATION）
							wolf.addEffect(new MobEffectInstance(MobEffects.SPEED, -1, 1, false, false, false));
							// addFreshEntity 后 attributes 已初始化
						try {
							var damageAttr = wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
							if (damageAttr != null) damageAttr.setBaseValue(6.0);
						} catch (Exception ex) {
							SurvivalContract.LOGGER.warn("Failed to enhance wolf attribute: {}", ex.getMessage());
						}
						entityDespawnTime.put(wolf.getUUID(), despawnAt);
					}
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_four_brothers"));
				player.playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT, 2.0f, 1.2f);
			}
			case HUNGER_BOMB -> {
				// 在目标玩家周围 3x3 方块范围内随机一格生成点燃的 TNT；
				// 不再直接给予饥饿效果 — 被爆炸波及且未死亡的玩家才会获得 30 秒饥饿
				//（ALLOW_DAMAGE 检测，见 MainEventListener）
				PrimedTnt tnt = EntityTypes.TNT.create(level, EntitySpawnReason.TRIGGERED);
				if (tnt != null) {
					double tx = player.blockPosition().getX() + (random.nextInt(3) - 1) + 0.5;
					double tz = player.blockPosition().getZ() + (random.nextInt(3) - 1) + 0.5;
					tnt.setPos(tx, player.getY(), tz);
					tnt.setFuse(40);
					level.addFreshEntity(tnt);
					entityDespawnTime.put(tnt.getUUID(), despawnAt);
					hungerBombTnts.add(tnt.getUUID());
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_hunger_bomb"));
				player.playSound(net.minecraft.sounds.SoundEvents.TNT_PRIMED, 2.0f, 1.0f);
			}
			case CRIMSON_INVASION -> {
				// 猩红入侵：一只僵尸疣猪兽（Zoglin）
				net.minecraft.world.entity.monster.Zoglin zoglin =
						EntityTypes.ZOGLIN.create(level, EntitySpawnReason.TRIGGERED);
				if (zoglin != null) {
					zoglin.setPos(x, y, z);
					zoglin.setTarget(player); // 仇恨优先锁定事件触发者
					level.addFreshEntity(zoglin);
					entityDespawnTime.put(zoglin.getUUID(), despawnAt);
				}
				player.sendSystemMessage(Component.translatable("survival-contract.msg.event_crimson_invasion"));
				player.playSound(net.minecraft.sounds.SoundEvents.ZOGLIN_AMBIENT, 2.0f, 1.0f);
			}
		}
	}

	private void cleanupExpiredEntities(long now) {
		for (Map.Entry<UUID, Long> entry : new ArrayList<>(entityDespawnTime.entrySet())) {
			if (now >= entry.getValue() && server != null) {
				for (ServerLevel level : server.getAllLevels()) {
					Entity e = level.getEntity(entry.getKey());
					if (e != null) e.discard();
				}
				entityDespawnTime.remove(entry.getKey());
				hungerBombTnts.remove(entry.getKey());
				trapHorses.remove(entry.getKey());
			}
		}
	}
}
