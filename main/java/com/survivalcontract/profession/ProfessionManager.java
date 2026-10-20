package com.survivalcontract.profession;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.challenge.Challenge;
import com.survivalcontract.challenge.ChallengeManager;
import com.survivalcontract.game.GameStateManager;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/**
 * Manages the profession system.
 */
public class ProfessionManager {
	public static final ProfessionManager INSTANCE = new ProfessionManager();

	public enum Profession {
		NONE("无职业", "None",
				"无特殊能力\n不携带任何初始物品\n初始 +10 分",
				"无免疫特权，初始 +10 分",
				new ProfessionItems()),
		CHEF("大厨", "Chef",
				"将非肉类食物的饥饿值与饱和度下降目标变为等量提升\n全队有效\n自身食用面包和烤马铃薯不会因违反目标而扣分",
				"自身食用面包和烤马铃薯不会因违反目标而扣分",
				new ProfessionItems(
						new ItemStack(Items.BAKED_POTATO, 16),
						new ItemStack(Items.BREAD, 16),
						new ItemStack(Items.SMOKER)
				)),
		WARRIOR("斗士", "Warrior",
				"使用熟肉类后获得1min的力量2效果\n自身不受食用肉类造成的扣分影响",
				"自身不受食用肉类造成的扣分影响",
				new ProfessionItems(
						new ItemStack(Items.COOKED_BEEF, 2),
						new ItemStack(Items.COOKED_PORKCHOP, 2),
						new ItemStack(Items.COOKED_MUTTON, 2),
						new ItemStack(Items.COOKED_CHICKEN, 2),
						new ItemStack(Items.IRON_SWORD)
				)),
		SCOUT("斥候", "Scout",
				"移动和跳跃和氧气值的削弱目标改为等量增加\n自身不会因违反跳跃目标而扣分\n使用药水也不会被扣分",
				"自身不会因违反跳跃目标而扣分，使用药水也不会被扣分",
				new ProfessionItems(
						createPotion(Items.POTION, Potions.STRONG_SWIFTNESS),   // 迅捷3
						createPotion(Items.POTION, Potions.STRONG_LEAPING),     // 跳跃2
						createPotion(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE) // 喷溅型抗火
				)),
		ARCHER("弓手", "Archer",
				"使用弓箭、弩、投掷三叉戟时造成伤害提升50%\n自身的远程攻击不受「攻击伤害降低」目标的影响（近战仍受影响）\n初始弓附带力量3与耐久5",
				"自身的远程攻击不受「攻击伤害降低」目标的影响（近战仍受影响）",
				new ProfessionItems(
						new ItemStack(Items.BOW), // 力量3+耐久5附魔在 giveStartingItems 中应用
						new ItemStack(Items.ARROW, 8)
				)),
		MINER("矿工", "Miner",
				"手持任意镐时获得无限时间的急迫2\n挖掘/获得钻石、绿宝石、下界合金锭不扣分\n初始金镐附有效率4与耐久3，且不可破坏",
				"挖掘或获得钻石、绿宝石、下界合金锭不扣分",
				new ProfessionItems(
						createDurabilityIronPickaxe(),
						new ItemStack(Items.GOLDEN_PICKAXE) // 效率4+耐久3+不可破坏在 giveStartingItems 中应用
				)),
		ARTISAN("工匠", "Artisan",
				"初始携带工作台、砂轮、铁砧\n自身免疫因违反使用/获得附魔台和铁砧规则而扣分\n合成工具或武器时50%概率获得耐久2，另有10%概率获得经验修补；合成工具另有10%概率获得效率4，合成武器另有10%概率获得锋利3（各附魔可叠加）",
				"自身免疫因违反使用/获得附魔台和铁砧规则而扣分",
				new ProfessionItems(
						new ItemStack(Items.CRAFTING_TABLE),
						new ItemStack(Items.GRINDSTONE),
						new ItemStack(Items.ANVIL)
				)),
		FOOL("行商", "Trader",
				"初始携带32金锭和8个金苹果\n自身获得金锭和任意金质物品方块时免疫扣分\n免疫违反村民交易扣分\n与村民交易时，必定获得双倍奖励\n每次猪灵交易结束时，15%概率额外随机获得以下四种礼物之一并直接放入物品栏：哭泣黑曜石、末影珍珠、灵魂疾行铁靴、抗火药水",
				"获得金锭和任意金质物品时免疫扣分，免疫违反村民交易扣分",
				new ProfessionItems(
						new ItemStack(Items.GOLD_INGOT, 32),
						new ItemStack(Items.GOLDEN_APPLE, 8)
				)),
		JESTER("愚者", "Fool",
				"初始携带1个绿宝石\n免疫任意获得物品会扣分的规则（获得禁止类物品不会被没收）\n丢出一颗钻石或绿宝石后，随机获得6~8个生存模式可非作弊获得的物品/方块",
				"免疫任意获得物品会扣分的规则（获得禁止类物品不会被没收）",
				new ProfessionItems(
						new ItemStack(Items.EMERALD)
				)),
		MUSICIAN("乐师", "Musician",
				"手持木棍或烈焰棒互动音符盒演奏：正面音色为半径16格内的友方（含自身）施加对应效果；负面音色（黑暗/不祥之兆/虚弱）对敌方生效\n持续演奏（间隔≤2秒）时每演奏1次，友方状态时长+15秒（可叠加，至多10分钟），自身时长仅刷新\n5秒内演奏3种不同音色：所有友方获得15秒随机正面状态（从所奏音色的正面效果中随机）\n敲击底鼓/颤音琴/小号音色：半径16格内除自身与队友外的所有生物受到2点真实伤害并被小幅击退（每3秒最多一次）\n解锁手鼓：获得成就「与火共舞」后，主手持音符盒、副手持任意方块，主手右键空气或音符盒即以副手方块对应的音色演奏（等同木棍效果，含音波攻击与合奏）\n免疫负面状态效果（凋零除外）\n初始携带1个音符盒",
				"免疫负面状态效果（凋零除外）",
				new ProfessionItems(
						new ItemStack(Items.NOTE_BLOCK)
				)),
		THIEF("盗贼", "Thief",
				"靠近生物并右键：夺取其手持武器/工具、副手与全身装备（卫道士的铁斧、骷髅的弓、溺尸的三叉戟等一并夺走）\n对可掉落头颅的生物（僵尸/尸壳/溺尸/骷髅/流浪者/沼骸/凋灵骷髅/苦力怕/猪灵），右键直接击杀并夺取其头颅\n以上效果每 2 分钟触发一次\n初始携带抢夺3与经验修补的铁斧\n免疫被敌对生物击杀造成的规则扣分",
				"免疫被敌对生物击杀造成的规则扣分",
				new ProfessionItems(
						new ItemStack(Items.IRON_AXE) // 抢夺3+经验修补在 giveStartingItems 中应用
				));

		public final String chineseName;
		public final String englishName;
		public final String description;
		/** HUD 精简描述（游戏开始后屏幕顶行）：仅显示自身免疫扣分相关说明 */
		public final String hudDesc;
		public final ItemStack[] startingItems;

		Profession(String cn, String en, String desc, String hud, ProfessionItems items) {
			this.chineseName = cn;
			this.englishName = en;
			this.description = desc;
			this.hudDesc = hud;
			this.startingItems = items.items;
		}
	}

	private record ProfessionItems(ItemStack... items) {}
	private final Map<UUID, Profession> playerProfessions = new HashMap<>();
	private MinecraftServer server;

	// ============ 进食结算状态（大厨/斗士特权 + 食物削弱挑战） ============
	/** 肉类物品（"非肉类食物"判断用，含生熟肉、鱼、兔肉煲等） */
	private static final Set<String> MEAT_ITEMS = Set.of(
			"minecraft:beef", "minecraft:cooked_beef", "minecraft:porkchop", "minecraft:cooked_porkchop",
			"minecraft:chicken", "minecraft:cooked_chicken", "minecraft:mutton", "minecraft:cooked_mutton",
			"minecraft:rabbit", "minecraft:cooked_rabbit", "minecraft:cod", "minecraft:cooked_cod",
			"minecraft:salmon", "minecraft:cooked_salmon", "minecraft:tropical_fish", "minecraft:pufferfish",
			"minecraft:rotten_flesh", "minecraft:rabbit_stew");
	/** 熟肉类（斗士：食用后获得 1 分钟力量 II） */
	private static final Set<String> COOKED_MEAT_ITEMS = Set.of(
			"minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
			"minecraft:cooked_mutton", "minecraft:cooked_rabbit", "minecraft:cooked_cod",
			"minecraft:cooked_salmon", "minecraft:rabbit_stew");

	/** 一次进食的待结算记录（UseItem 时记录，食物值上涨时结算） */
	private record PendingFood(String itemId, int nutrition, float saturationModifier,
			boolean meat, boolean cookedMeat) {}

	private final Map<UUID, PendingFood> pendingEats = new HashMap<>();
	private final Map<UUID, Integer> lastFoodLevels = new HashMap<>();

	/** 职业反转特权首次生效提示（每玩家每特权只提示一次，防止刷屏） */
	private final Map<UUID, Set<String>> perkNotified = new HashMap<>();

	/** 盗贼右键夺取冷却：玩家 UUID → 可再次触发的游戏时刻（2 分钟 = 2400 tick） */
	public static final long THIEF_COOLDOWN_TICKS = 2400;
	private final Map<UUID, Long> thiefCooldownUntil = new HashMap<>();

	/** 可掉落头颅的生物 → 对应头颅物品（末影龙/玩家不计入：前者是最终目标、后者非生物） */
	private static final Map<EntityType<?>, Item> HEAD_DROPS = Map.ofEntries(
			Map.entry(EntityTypes.ZOMBIE, Items.ZOMBIE_HEAD),
			Map.entry(EntityTypes.HUSK, Items.ZOMBIE_HEAD),
			Map.entry(EntityTypes.DROWNED, Items.ZOMBIE_HEAD),
			Map.entry(EntityTypes.ZOMBIE_VILLAGER, Items.ZOMBIE_HEAD),
			Map.entry(EntityTypes.SKELETON, Items.SKELETON_SKULL),
			Map.entry(EntityTypes.STRAY, Items.SKELETON_SKULL),
			Map.entry(EntityTypes.BOGGED, Items.SKELETON_SKULL),
			Map.entry(EntityTypes.PARCHED, Items.SKELETON_SKULL),
			Map.entry(EntityTypes.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL),
			Map.entry(EntityTypes.CREEPER, Items.CREEPER_HEAD),
			Map.entry(EntityTypes.PIGLIN, Items.PIGLIN_HEAD));

	private ProfessionManager() {}
	public static ProfessionManager getInstance() { return INSTANCE; }

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		playerProfessions.clear();
		pendingEats.clear();
		lastFoodLevels.clear();
		thiefCooldownUntil.clear();
	}

	public Profession getPlayerProfession(UUID playerId) {
		return playerProfessions.getOrDefault(playerId, Profession.NONE);
	}
	public void setPlayerProfession(UUID playerId, Profession profession) {
		playerProfessions.put(playerId, profession);
	}
	public boolean hasChosenProfession(UUID playerId) { return playerProfessions.containsKey(playerId); }
	// ============ 会话持久化（退出重进保留局内状态）所需的存取器 ============
	public Map<UUID, Profession> getAllProfessions() { return new HashMap<>(playerProfessions); }
	public void clearAll() {
		playerProfessions.clear();
		pendingEats.clear();
		lastFoodLevels.clear();
		thiefCooldownUntil.clear();
	}

	public void giveStartingItems(ServerPlayer player) {
		Profession prof = getPlayerProfession(player.getUUID());
		player.getInventory().clearContent();
		var registries = player.level().registryAccess();
		for (ItemStack stack : prof.startingItems) {
			ItemStack copy = stack.copy();
			// 附魔物品在拿到 RegistryAccess 后应用（注册表在 enum 构造时不可用）
			if (copy.is(Items.BOW)) {
				// 弓手：力量3 + 耐久5弓
				enchant(copy, registries, Enchantments.POWER, 3);
				enchant(copy, registries, Enchantments.UNBREAKING, 5);
			} else if (copy.is(Items.GOLDEN_PICKAXE)) {
				// 矿工：效率4 + 耐久3 + 不可破坏金镐
				enchant(copy, registries, Enchantments.EFFICIENCY, 4);
				enchant(copy, registries, Enchantments.UNBREAKING, 3);
				copy.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
			} else if (copy.is(Items.IRON_AXE)) {
				// 盗贼：抢夺3 + 经验修补铁斧
				enchant(copy, registries, Enchantments.LOOTING, 3);
				enchant(copy, registries, Enchantments.MENDING, 1);
			}
			player.getInventory().add(copy);
		}
	}

	/** 给物品精确附加指定等级的附魔（26.2 数据组件 API） */
	private static void enchant(ItemStack stack, HolderLookup.Provider registries,
			ResourceKey<Enchantment> key, int level) {
		Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
		ItemEnchantments.Mutable mut = new ItemEnchantments.Mutable(
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mut.set(holder, level);
		stack.set(DataComponents.ENCHANTMENTS, mut.toImmutable());
	}

	/** 创建带指定药水效果的药水物品栈 */
	private static ItemStack createPotion(Item item, Holder<Potion> potion) {
		return PotionContents.createItemStack(item, potion);
	}

	public boolean isImmuneToChallenge(UUID playerId, String challengeId) {
		Profession prof = getPlayerProfession(playerId);
		return switch (prof) {
			case NONE -> false;
			case CHEF -> switch (challengeId) {
				case "no_eat_bread", "no_eat_potato" -> true;
				default -> false;
			};
			case WARRIOR -> switch (challengeId) {
				// 斗士：免疫低血糖、脆骨症
				case "no_eat_beef", "no_eat_pork", "no_eat_mutton", "no_eat_chicken",
						"hypoglycemia", "brittle_bones" -> true;
				default -> false;
			};
			case SCOUT -> switch (challengeId) {
				case "no_jump", "no_potions" -> true;
				default -> false;
			};
			case MINER -> switch (challengeId) {
				// 矿工：免疫尘肺病
				case "no_diamond", "no_netherite", "no_gold", "dust_lung" -> true;
				default -> false;
			};
			case ARTISAN -> switch (challengeId) {
				case "no_enchanting", "no_anvil" -> true;
				default -> false;
			};
			case FOOL -> switch (challengeId) {
				case "no_gold", "no_villager_trade" -> true;
				default -> false;
			};
			case JESTER -> switch (challengeId) {
				// 愚者：免疫任意"获得物品会扣分"的规则（获得禁止类物品不会被没收）
				case "no_diamond", "no_gold", "no_netherite", "no_lapis", "no_potions" -> true;
				default -> false;
			};
			case MUSICIAN -> false;
			case ARCHER -> false;
			case THIEF -> switch (challengeId) {
				// 盗贼：免疫「不能被 XX 敌对生物杀死」系列死亡扣分
				case "no_killed_by_zombie", "no_killed_by_skeleton", "no_killed_by_spider",
						"no_killed_by_creeper", "no_killed_by_witch", "no_killed_by_enderman",
						"no_killed_by_blaze", "no_killed_by_drowned", "no_killed_by_magma_cube" -> true;
				default -> false;
			};
		};
	}

	/** 职业反转特权首次生效提示：每玩家每 key 只发一次 */
	public void notifyPerkOnce(ServerPlayer player, String key, Component message) {
		Set<String> keys = perkNotified.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
		if (keys.add(key)) {
			player.sendSystemMessage(message);
		}
	}

	/** 新一局开始时清空提示标记（让反转效果在新一局重新提示一次） */
	public void clearPerkFlags() {
		perkNotified.clear();
	}

	public boolean isScout(UUID p) { return getPlayerProfession(p) == Profession.SCOUT; }
	public boolean isMiner(UUID p) { return getPlayerProfession(p) == Profession.MINER; }
	public boolean isWarrior(UUID p) { return getPlayerProfession(p) == Profession.WARRIOR; }
	public boolean isArcher(UUID p) { return getPlayerProfession(p) == Profession.ARCHER; }
	public boolean isChef(UUID p) { return getPlayerProfession(p) == Profession.CHEF; }
	public boolean isThief(UUID p) { return getPlayerProfession(p) == Profession.THIEF; }

	/**
	 * 盗贼右键生物特权（UseEntityCallback 内调用，仅在 PLAYING 阶段）：
	 * <ol>
	 *   <li>夺取目标全部装备：主手（武器/工具）、副手、四件护甲；进盗贼背包，背包满则掉落脚下</li>
	 *   <li>目标属于「可掉落头颅」的生物 → 直接击杀并夺取其头颅（不重复掉落原版头颅）</li>
	 * </ol>
	 * 触发后 2 分钟（2400 tick）冷却；若目标既无装备也无可夺取头颅则不接管、不消耗冷却。
	 *
	 * @return true 表示本次右键已由盗贼特权接管（调用方应阻断原版交互）
	 */
	public boolean tryThiefSteal(ServerPlayer thief, Entity target) {
		if (!(target instanceof LivingEntity living)) return false;
		if (target instanceof ServerPlayer) return false; // 不对玩家生效（避免无限掠夺 PvP）
		if (!(living.level() instanceof ServerLevel level)) return false;

		long now = level.getGameTime();
		Long until = thiefCooldownUntil.get(thief.getUUID());
		if (until != null && now < until) return false; // 冷却中：不接管，保留原版交互

		// 收集可夺取的装备（主手/副手/四件护甲）
		java.util.List<ItemStack> taken = new java.util.ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = living.getItemBySlot(slot);
			if (!s.isEmpty()) {
				taken.add(s.copy());
				living.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		Item headItem = HEAD_DROPS.get(living.getType());
		if (taken.isEmpty() && headItem == null) return false; // 无可夺取目标 → 不接管、不消耗冷却

		String targetName = living.getName().getString();
		if (!taken.isEmpty()) {
			StringBuilder names = new StringBuilder();
			for (ItemStack s : taken) {
				if (names.length() > 0) names.append(", ");
				names.append(s.getHoverName().getString()).append("×").append(s.getCount());
			}
			for (ItemStack s : taken) giveOrDrop(thief, s);
			thief.sendSystemMessage(Component.translatable("survival-contract.msg.thief_stole",
					targetName, names.toString()));
		}

		if (headItem != null) {
			// 直接击杀（以盗贼为击杀者，正常结算掉落/经验），再手动发放头颅；
			// 先清伤害冷却闸，保证刚被击中的生物也能被这一下打死
			com.survivalcontract.McCompat.clearHurtCooldown(living);
			living.hurtServer(level, level.damageSources().playerAttack(thief), Float.MAX_VALUE);
			if (living.isAlive()) living.kill(level); // 兜底：极少数伤害被规则拦截时直接移除
			ItemStack head = new ItemStack(headItem);
			giveOrDrop(thief, head);
			thief.sendSystemMessage(Component.translatable("survival-contract.msg.thief_execute",
					targetName, head.getHoverName().getString()));
		}

		thiefCooldownUntil.put(thief.getUUID(), now + THIEF_COOLDOWN_TICKS);
		thief.sendSystemMessage(Component.translatable("survival-contract.msg.thief_cooldown"));
		return true;
	}

	/** 放入背包，满则掉在玩家脚下（不消失） */
	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) return;
		if (player.getInventory().add(stack)) return;
		ItemEntity ie = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
		player.level().addFreshEntity(ie);
	}

	/** 自测：读取盗贼冷却结束时刻（无记录返回 0） */
	public long getThiefCooldownUntilForTest(UUID p) { return thiefCooldownUntil.getOrDefault(p, 0L); }
	/** 自测：清空盗贼冷却 */
	public void clearThiefCooldown() { thiefCooldownUntil.clear(); }

	public void tickMinerEffect(ServerPlayer player) {
		if (!isMiner(player.getUUID())) return;
		boolean holdingPick = isHoldingPickaxe(player);
		if (holdingPick) {
			if (!player.hasEffect(MobEffects.HASTE)) {
				// -1 = 原版无限时长（INFINITE_DURATION）
				player.addEffect(new MobEffectInstance(MobEffects.HASTE, -1, 1, false, false, false));
			}
		} else {
			if (player.hasEffect(MobEffects.HASTE)) {
				player.removeEffect(MobEffects.HASTE);
			}
		}
	}

	/**
	 * 职业特权每 tick 结算（PLAYING 阶段调用）：
	 * <ul>
	 *   <li>矿工：手持任意镐 → 无限急迫 II</li>
	 *   <li>斥候：移速/跳跃/氧气削弱类挑战反转为等量增益</li>
	 *   <li>大厨：非肉类食物的"饥饿值与饱和度降低"目标反转为等量提升（团队模式下全队有效）</li>
	 *   <li>斗士：食用熟肉类后获得 1 分钟力量 II</li>
	 *   <li>食物削弱挑战（reduced_food_saturation）本体结算：无大厨时按数值降低饥饿收益</li>
	 * </ul>
	 */
	public void tickPerks(ServerPlayer player, long tick) {
		UUID pid = player.getUUID();
		Set<String> ch = ChallengeManager.getInstance().getPlayerChallenges(pid);
		boolean scout = isScout(pid);

		// 矿工：手持任意镐 → 无限急迫 II
		tickMinerEffect(player);

		// 移动速度减慢（可自定义：值 10-90 → 缓慢 0-3 级）
		// 斥候特权：削弱目标改为等量增加 → 缓慢 N 级换成迅捷 N 级
		// 每 20tick 续 80tick + 非环境效果：客户端显示正常倒计时，挑战关闭后 4 秒内自然消退
		if (ch.contains("slower_speed")) {
			int amp = Math.max(0, Math.min(3, customValue("slower_speed") / 25));
			if (tick % 20 == 0) {
				if (scout) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, amp, false, false, true));
					notifyPerkOnce(player, "scout_speed", Component.translatable(
							"survival-contract.msg.scout_speed", amp + 1));
				} else {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, amp, false, false, true));
				}
			}
		}
		// 斥候：跳跃削弱 → 跳跃提升 II（显示正常倒计时，避免看起来像无限 buff）
		if (ch.contains("no_jump") && scout && tick % 20 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 80, 1, false, false, true));
			notifyPerkOnce(player, "scout_jump", Component.translatable("survival-contract.msg.scout_jump"));
		}
		// 氧气值降低（可自定义）：普通玩家水下耗氧加速；斥候反转 → 水下呼吸
		if (ch.contains("reduced_oxygen")) {
			if (scout) {
				if (tick % 20 == 0) {
					player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false, true));
					notifyPerkOnce(player, "scout_oxygen", Component.translatable("survival-contract.msg.scout_oxygen"));
				}
			} else if (player.isInWater() && player.getAirSupply() < player.getMaxAirSupply()
					&& !player.hasEffect(MobEffects.WATER_BREATHING) && !player.hasEffect(MobEffects.CONDUIT_POWER)) {
				if (tick % 20 == 0) {
					int extra = Math.max(10, (int) (player.getMaxAirSupply() * customValue("reduced_oxygen") / 200.0f));
					player.setAirSupply(Math.max(0, player.getAirSupply() - extra));
				}
			}
		}

		// 进食结算（大厨反转提升 / 食物削弱挑战 / 斗士力量 II）
		tickFoodSettlement(player);
	}

	/** 记录一次物品使用：若是食物则挂起待结算记录（UseItemCallback 在服务端调用） */
	public void onFoodUseStart(ServerPlayer player, ItemStack stack) {
		var food = stack.get(DataComponents.FOOD);
		if (food == null) return;
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		pendingEats.put(player.getUUID(), new PendingFood(
				id, food.nutrition(), food.saturation(),
				MEAT_ITEMS.contains(id), COOKED_MEAT_ITEMS.contains(id)));
	}

	/** 检测食物值上涨（进食完成）→ 按职业/挑战结算 */
	private void tickFoodSettlement(ServerPlayer player) {
		UUID pid = player.getUUID();
		int cur = player.getFoodData().getFoodLevel();
		Integer prev = lastFoodLevels.put(pid, cur);
		PendingFood pending = pendingEats.get(pid);
		if (prev == null || pending == null || cur <= prev) return;

		pendingEats.remove(pid);
		Set<String> ch = ChallengeManager.getInstance().getPlayerChallenges(pid);
		GameStateManager gsm = GameStateManager.getInstance();

		// 大厨特权是否生效：自己是大厨；或团队模式下同队有大厨（全队有效）
		boolean chefApplies = false;
		if (isChef(pid)) {
			chefApplies = true;
		} else if (gsm.isTeamMode()) {
			var team = gsm.getPlayerTeam(pid);
			if (team == GameStateManager.Team.RED || team == GameStateManager.Team.BLUE) {
				for (UUID member : gsm.getTeamMembers(team)) {
					if (isChef(member)) { chefApplies = true; break; }
				}
			}
		}

		var fd = player.getFoodData();
		if (chefApplies && !pending.meat() && ch.contains("reduced_food_saturation")) {
			// 大厨：非肉类食物的下降目标 → 等量提升（-X% 变 +X%，净收益 ×(1+2X%)）
			int pct = customValue("reduced_food_saturation");
			int bonus = Math.max(1, Math.round(pending.nutrition() * 2 * pct / 100.0f));
			fd.setFoodLevel(Math.min(20, cur + bonus));
			float satBonus = pending.saturationModifier() * pending.nutrition() * 2 * pct / 100.0f;
			fd.setSaturation(Math.min(fd.getFoodLevel(), fd.getSaturationLevel() + satBonus));
			player.sendSystemMessage(Component.translatable("survival-contract.msg.chef_food_up", bonus));
		} else if (!chefApplies && ch.contains("reduced_food_saturation")) {
			// 挑战本体：食物饥饿值与饱和度降低（原版已全额发放 → 结算时扣回 X%）
			int pct = customValue("reduced_food_saturation");
			int reduce = Math.max(1, Math.round(pending.nutrition() * pct / 100.0f));
			fd.setFoodLevel(Math.max(0, cur - reduce));
			float satReduce = pending.saturationModifier() * pending.nutrition() * pct / 100.0f;
			fd.setSaturation(Math.max(0f, fd.getSaturationLevel() - satReduce));
			player.sendSystemMessage(Component.translatable("survival-contract.msg.food_saturation_down", reduce));
		}

		// 斗士：食用熟肉类 → 1 分钟力量 II
		if (pending.cookedMeat() && isWarrior(pid)) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200, 1, false, true, true));
			player.sendSystemMessage(Component.translatable("survival-contract.msg.warrior_full_meal"));
		}
	}

	/** 读取可自定义挑战的数值（默认 50） */
	private static int customValue(String challengeId) {
		Challenge c = ChallengeManager.getInstance().getChallenge(challengeId);
		return c != null && c.isCustomizable() ? c.getCustomValue() : 50;
	}

	public boolean isHoldingPickaxe(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		return main.is(Items.IRON_PICKAXE) || main.is(Items.GOLDEN_PICKAXE)
				|| main.is(Items.DIAMOND_PICKAXE) || main.is(Items.NETHERITE_PICKAXE)
				|| main.is(Items.STONE_PICKAXE) || main.is(Items.WOODEN_PICKAXE);
	}

	private static ItemStack createDurabilityIronPickaxe() {
		// 铁镐耐久 250 → 仅剩 25% 耐久 = 已损耗 75% = 188
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(188);
		return pick;
	}
}
