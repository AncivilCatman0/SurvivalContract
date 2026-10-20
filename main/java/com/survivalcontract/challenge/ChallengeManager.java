package com.survivalcontract.challenge;

import com.survivalcontract.SurvivalContract;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Manages all challenges: definitions, per-player selections, and violation tracking.
 */
public class ChallengeManager {
	public static final ChallengeManager INSTANCE = new ChallengeManager();

	// All defined challenges
	private final Map<String, Challenge> allChallenges = new LinkedHashMap<>();

	// Per-player enabled challenges: playerId -> set of challenge ids
	private final Map<UUID, Set<String>> playerChallenges = new HashMap<>();

	// Per-player violation tracking (to prevent repeat same violation per tick)
	private final Map<UUID, Set<String>> recentlyViolated = new HashMap<>();
	/** 每玩家每条规则一局仅扣一次（违规扣分记录，key = playerId + ":" + ruleId） */
	private final Set<String> penalizedOnce = new HashSet<>();

	private MinecraftServer server;

	private ChallengeManager() {}

	public static ChallengeManager getInstance() {
		return INSTANCE;
	}

	public void onServerStart(MinecraftServer server) {
		this.server = server;
		this.playerChallenges.clear();
		this.recentlyViolated.clear();
		registerAllChallenges();
	}

	public void onServerStop(MinecraftServer server) {
		this.server = null;
	}

	/**
	 * Register all challenge definitions based on the requirements.
	 * Note: Each challenge has its OWN baseScore and penaltyScore - different challenges give different points!
	 */
	private void registerAllChallenges() {
		// ===== ITEM/BLOCK RESTRICTIONS =====
		// Note: removed "can't use workbench" per requirement #9
		register(new Challenge("no_enchanting", "不能使用附魔台", "禁止使用附魔台",
				5, 3, Challenge.Category.ITEM_RESTRICTION, "minecraft:enchanting_table"));
		register(new Challenge("no_anvil", "不能使用铁砧", "禁止使用铁砧",
				5, 3, Challenge.Category.ITEM_RESTRICTION, "minecraft:anvil"));
		register(new Challenge("no_chest", "不能使用箱子", "禁止打开普通箱子",
				3, 2, Challenge.Category.ITEM_RESTRICTION, "minecraft:chest"));
		register(new Challenge("no_ender_pearl", "不使用末影珍珠", "禁止投掷/使用末影珍珠",
				4, 0, Challenge.Category.ITEM_RESTRICTION, "minecraft:ender_pearl"));
		register(new Challenge("no_furnace", "不能使用熔炉", "禁止使用熔炉冶炼物品",
				3, 2, Challenge.Category.ITEM_RESTRICTION, "minecraft:furnace"));
		register(new Challenge("no_brewing_stand", "不能使用酿造台", "禁止使用酿造台酿造药水",
				4, 2, Challenge.Category.ITEM_RESTRICTION, "minecraft:brewing_stand"));
		register(new Challenge("no_beacon", "不能使用重生锚", "禁止使用重生锚",
				6, 4, Challenge.Category.ITEM_RESTRICTION, "minecraft:respawn_anchor"));

		// ===== 不可获得（阻断获取途径） =====
		register(new Challenge("no_diamond", "不能获得钻石", "禁止获得钻石和钻石相关物品",
				8, 5, Challenge.Category.ACQUISITION_BLOCK, "minecraft:diamond"));
		register(new Challenge("no_gold", "不能获得金锭", "禁止获得金锭",
				5, 4, Challenge.Category.ACQUISITION_BLOCK, "minecraft:gold_ingot"));
		register(new Challenge("no_netherite", "不能获得下界合金", "禁止获得下界合金锭",
				10, 6, Challenge.Category.ACQUISITION_BLOCK, "minecraft:netherite_ingot"));
		register(new Challenge("no_lapis", "不能获得青金石", "禁止获得青金石（含矿石形态）",
				3, 2, Challenge.Category.ACQUISITION_BLOCK, "minecraft:lapis_lazuli"));
		register(new Challenge("iron_ore_risk", "贫瘠铁矿", "挖掘铁矿石或深层铁矿石时，50%概率不掉落粗铁，10%概率掉落双倍粗铁",
				4, 3, Challenge.Category.ACQUISITION_BLOCK, "minecraft:raw_iron"));
		getChallenge("iron_ore_risk").setNoViolation(true); // 纯掉落概率规则：无违规扣分，卡片不显示违反文本

		// ===== 行为/工具限制 =====
		register(new Challenge("no_kill_iron_golem", "不能击杀铁傀儡", "击杀铁傀儡时违规扣分",
				4, 3, Challenge.Category.KILL_RESTRICTION, "minecraft:iron_golem_spawn_egg"));
		register(new Challenge("no_villager_trade", "不能与村民交易", "禁止与村民/流浪商人交易",
				4, 3, Challenge.Category.ITEM_RESTRICTION, "minecraft:emerald"));
		register(new Challenge("no_fishing_rod", "不能使用钓鱼竿", "禁止使用钓鱼竿",
				3, 2, Challenge.Category.ITEM_RESTRICTION, "minecraft:fishing_rod"));
		register(new Challenge("no_boat", "不能使用任意船", "禁止放置或乘坐任何船/木筏/运输船",
				3, 2, Challenge.Category.ITEM_RESTRICTION, "minecraft:oak_boat"));

		// ===== 红字规则（选择时直接扣分，取消后恢复被扣分数；baseScore 为负） =====
		register(new Challenge("ghast_replacement", "恶魂庇佑", "恶魂火球击中自己不受伤，并获得瞬间治疗II效果",
				-6, 0, Challenge.Category.RED_TEXT, "minecraft:ghast_tear"));

		register(new Challenge("piglin_deal", "一本万利", "猪灵与猪灵蛮兵不会对你造成伤害；可用金粒与猪灵交易，效果与用金锭交易一致",
				-10, 0, Challenge.Category.RED_TEXT, "minecraft:gold_nugget"));

		// ===== 死亡限制（判定 = 死于对应原因时才扣分；死亡照常发生、不保护不回血；每局该规则仅扣一次）=====
		register(new Challenge("no_fall_damage", "不能摔死", "摔死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:feather"));
		register(new Challenge("no_fire_damage", "不能被火烧死", "被火烧死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:blaze_rod"));
		register(new Challenge("no_lava_damage", "不能掉进岩浆", "被岩浆烧死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				6, 4, Challenge.Category.DEATH_RESTRICTION, "minecraft:bucket"));
		register(new Challenge("no_drowning", "不能淹死", "被淹死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				4, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:water_bucket"));
		register(new Challenge("no_cactus", "不能被仙人掌杀死", "被仙人掌扎死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				3, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:cactus"));
		register(new Challenge("no_fall_in_void", "不能掉进虚空", "掉进虚空即扣分并传送回出生点（每次都会传送）",
				7, 5, Challenge.Category.DEATH_RESTRICTION, "minecraft:end_portal_frame"));

		// ===== 不能被 XX 杀死 =====
		register(new Challenge("no_killed_by_zombie", "不能被僵尸杀死", "被僵尸杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:zombie_spawn_egg"));
		register(new Challenge("no_killed_by_skeleton", "不能被骷髅杀死", "被骷髅杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:skeleton_spawn_egg"));
		register(new Challenge("no_killed_by_spider", "不能被蜘蛛杀死", "被蜘蛛杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				4, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:spider_spawn_egg"));
		register(new Challenge("no_killed_by_creeper", "不能被苦力怕炸死", "被苦力怕炸死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				6, 4, Challenge.Category.DEATH_RESTRICTION, "minecraft:creeper_spawn_egg"));
		register(new Challenge("no_killed_by_witch", "不能被女巫杀死", "被女巫杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				4, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:witch_spawn_egg"));
		register(new Challenge("no_killed_by_enderman", "不能被末影人杀死", "被末影人杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:enderman_spawn_egg"));
		register(new Challenge("no_killed_by_blaze", "不能被烈焰人杀死", "被烈焰人杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				5, 3, Challenge.Category.DEATH_RESTRICTION, "minecraft:blaze_spawn_egg"));
		register(new Challenge("no_killed_by_drowned", "不能被溺尸杀死", "被溺尸杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				4, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:drowned_spawn_egg"));
		register(new Challenge("no_killed_by_magma_cube", "不能被岩浆怪杀死", "被岩浆怪杀死时扣分（死亡照常发生、不保护；每局仅扣一次）",
				4, 2, Challenge.Category.DEATH_RESTRICTION, "minecraft:magma_cube_spawn_egg"));

		// ===== 与神合约板块新规则（常规）=====
		register(new Challenge("no_natural_regen", "无法自然回血", "无法通过饱食度自然恢复生命值（药水与再生效果仍可回血）",
				5, 4, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:glistering_melon_slice"));
		register(new Challenge("inv_slot_lock", "背包格子锁定", "锁定背包格子（快捷栏除外）：27格 +4分 / 18格 +8分 / 9格 +16分，锁定格以屏障标记且无法放入物品（点击卡片上的按钮切换格数）",
				4, 3, Challenge.Category.RULE, "minecraft:barrier"));
		register(new Challenge("max_health_down", "血量上限下降", "血量上限按自定义百分比下降（选值越高分越高）",
				5, 4, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:totem_of_undying"));
		register(new Challenge("mining_slow", "挖掘速度下降", "挖掘速度按自定义百分比下降（选值越高分越高）",
				4, 3, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:wooden_pickaxe"));

		// ===== 久病成患板块（疾病规则，排版同与神合约；分值为常规板块两倍）=====
		register(new Challenge("insomnia", "失眠症", "游戏内每天22点获得迟缓1与凋零1并持续扣血，6点自然解除；睡眠时停止扣血，当天睡眠累计15秒后状态移出",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:purple_bed"));
		register(new Challenge("hypothermia", "失温症", "进入覆雪平原、冰刺地形及其变种5秒后开始受到细雪冻结伤害；仅穿着全套皮革护甲时免疫",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:snowball"));
		register(new Challenge("heatstroke", "热射病", "进入沙漠及其变种5秒后、或靠近岩浆2格内获得持续灼伤；仅在不穿任何护甲且身上携带水桶时免疫",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:magma_cream"));
		register(new Challenge("dust_lung", "尘肺病", "高度处于负层（Y<0）时连续1分钟获得无限时间中毒效果，离开负层后清除",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:cobweb"));
		register(new Challenge("altitude_edema", "高原水肿", "高度高于128层时连续1分钟获得无限时间中毒效果，高度低于128层后清除",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:potion"));
		register(new Challenge("decompression", "减压症", "完全进入水中后，若5秒之内就离开水面，则获得15秒的缓慢2、虚弱2与挖掘疲劳1效果",
				8, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:water_bucket"));
		register(new Challenge("zero_gravity", "失重", "玩家连续跳跃两次后，获得10秒漂浮效果",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:feather"));
		register(new Challenge("brittle_bones", "脆骨症", "摔落伤害的起始判定距离为1格高度；攻击时有30%概率反馈自身所造成伤害的一半",
				12, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:bone"));
		register(new Challenge("hypoglycemia", "低血糖", "仅可以吃南瓜派、曲奇饼、蛋糕、烤马铃薯、面包、苹果、甜浆果、发光浆果恢复饱食度，吃其他食物将被阻止；饱食度下降速度翻倍；仅剩3格饱食度时获得无限失明与缓慢3",
				10, 0, Challenge.Category.FOOD_PENALTY, "minecraft:cookie"));
		register(new Challenge("exhibitionist", "暴露狂", "自身四件装备最多穿两件，穿多了就自燃",
				8, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:leather_chestplate"));
		register(new Challenge("pain_lover", "嗜痛人", "自身连续受到同一种伤害类型时，每次伤害下降2点，最低变为1点",
				-8, 0, Challenge.Category.RED_TEXT, "minecraft:wither_rose"));

		// ===== 环境恶化板块（选中与取消所有玩家共享，开启时全员获得对应分数）=====
		register(new Challenge("eternal_night", "永夜", "时间锁定为午夜（所有玩家共享，任意玩家开关全员生效）",
				8, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:coal_block"));
		register(new Challenge("camouflage", "迷彩", "所有敌对生物与中立生物生成时获得30秒隐身（所有玩家共享，任意玩家开关全员生效）",
				12, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:fermented_spider_eye"));
		register(new Challenge("neutral_hostile", "恶意感知", "所有中立生物会主动攻击所有玩家（如僵尸猪灵、铁傀儡、蜜蜂等）（全员共享，任意玩家开关）",
				12, 0, Challenge.Category.PASSIVE_MOB_HOSTILE, "minecraft:zombified_piglin_spawn_egg"));
		register(new Challenge("white_moon", "白月", "夜间时，自然生成的敌对生物首次被杀死时立即生成一个相同的个体（NBT 完整保留）；遭遇事件生物不受影响（全员共享）",
				12, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:skeleton_skull"));
		register(new Challenge("golden_moon", "金月", "夜间时，自然生成的敌对生物拥有抗性提升2，白天解除（全员共享）",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:gold_block"));
		register(new Challenge("red_moon", "赤月", "夜间时，自然生成的敌对生物会分裂出一个相同个体，且均拥有力量2，白天解除（全员共享）",
				16, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:redstone_block"));
		register(new Challenge("midnight_bell", "午夜凶铃", "游戏时刻零点时，随机一名玩家受到一次无来源的音波尖啸伤害（每20分钟最多触发一次，连续两次不会选中同一玩家）（全员共享）",
				10, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:bell"));

		// 板块归属：疾病/环境
		for (String id : new String[]{"insomnia", "hypothermia", "heatstroke", "brittle_bones",
				"hypoglycemia", "exhibitionist", "pain_lover", "dust_lung", "altitude_edema", "decompression",
				"zero_gravity"}) {
			getChallenge(id).setSection("disease");
		}
		for (String id : new String[]{"eternal_night", "camouflage", "neutral_hostile",
				"white_moon", "golden_moon", "red_moon", "midnight_bell"}) {
			getChallenge(id).setSection("environment");
		}
		// 被动型疾病/环境规则没有"违规"概念（条件触发即生效），免违规标记（不显示违反文本、不扣分）
		// 低血糖按用户要求不扣分（吃其他食物仅阻止并提示）
		for (String id : new String[]{"insomnia", "hypothermia", "heatstroke", "brittle_bones",
				"exhibitionist", "eternal_night", "camouflage", "hypoglycemia",
				"dust_lung", "altitude_edema", "decompression", "neutral_hostile",
				"white_moon", "golden_moon", "red_moon", "zero_gravity", "midnight_bell"}) {
			getChallenge(id).setNoViolation(true);
		}
		// 血量上限下降 / 挖掘速度下降：可自定义百分比（默认 50%）
		getChallenge("max_health_down").setCustomizable(true);
		getChallenge("max_health_down").setCustomValue(50);
		getChallenge("mining_slow").setCustomizable(true);
		getChallenge("mining_slow").setCustomValue(50);
		// 背包格子锁定：默认 18 格
		getChallenge("inv_slot_lock").setCustomValue(18);

		// ===== 冲突键：专属任务按这些键过滤候选，避免"选了规则还接到做不到的任务" =====
		// （setConflictKeys() 在方法末尾调用：此时全部规则已注册完毕）

		// ===== ATTRIBUTE PENALTIES (customizable damage/defense) =====
		Challenge reducedAttack = new Challenge("reduced_attack", "攻击伤害降低", "玩家攻击伤害降低（可自定义百分比）",
				6, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:iron_sword");
		reducedAttack.setCustomizable(true);
		reducedAttack.setCustomValue(50); // 50% reduction default
		reducedAttack.setCustomValueLabel("伤害降低百分比");
		register(reducedAttack);

		// ===== 受到伤害增加：细分为各类伤害（原"受到伤害增加"单一规则拆分，全部保留百分比自定义）=====
		String[][] damageRules = {
				{"reduced_defense_fire", "火焰伤害增加", "受到的火焰类伤害增加（岩浆/火烧/脚下方块点燃），可自定义百分比", "minecraft:fire_charge"},
				{"reduced_defense_fall", "摔落伤害增加", "受到的摔落伤害增加，可自定义百分比", "minecraft:feather"},
				{"reduced_defense_wither", "凋零伤害增加", "受到的凋零效果伤害增加，可自定义百分比", "minecraft:wither_skeleton_skull"},
				{"reduced_defense_lightning", "雷击伤害增加", "受到的雷击伤害增加，可自定义百分比", "minecraft:lightning_rod"},
				{"reduced_defense_freeze", "细雪伤害增加", "受到的冰冻（细雪）伤害增加，可自定义百分比", "minecraft:powder_snow_bucket"}
		};
		for (String[] r : damageRules) {
			Challenge rc = new Challenge(r[0], r[1], r[2],
					3, 0, Challenge.Category.ATTRIBUTE_PENALTY, r[3]);
			rc.setCustomizable(true);
			rc.setCustomValue(30);
			rc.setCustomValueLabel("伤害增加百分比");
			register(rc);
		}

		Challenge reducedArmor = new Challenge("reduced_armor", "护甲值降低", "所有护甲值减半",
				5, 0, Challenge.Category.ATTRIBUTE_PENALTY, "minecraft:iron_chestplate");
		reducedArmor.setCustomizable(true);
		reducedArmor.setCustomValue(50);
		reducedArmor.setCustomValueLabel("护甲降低百分比");
		register(reducedArmor);

		// ===== MOVEMENT PENALTIES =====
		register(new Challenge("no_jump", "不能跳跃", "禁止跳跃",
				5, 3, Challenge.Category.MOVEMENT_PENALTY, "minecraft:rabbit_foot"));
		register(new Challenge("slower_speed", "移动速度减慢", "移动速度降低（可自定义）",
				5, 0, Challenge.Category.MOVEMENT_PENALTY, "minecraft:sugar"));
		// 移动速度减慢改为可自定义百分比（10%-90%，步长10%；应用点 tickPerks 按 数值/25 换算缓慢/迅捷等级，
		// 分数按 数值/50 加权 — 与其他可自定义规则一致）
		Challenge slowerSpeed = getChallenge("slower_speed");
		slowerSpeed.setCustomizable(true);
		slowerSpeed.setCustomValue(50);
		slowerSpeed.setCustomValueLabel("速度降低百分比");
		// Can't jump variant handled above

		Challenge reducedOxygen = new Challenge("reduced_oxygen", "氧气值降低", "水下氧气值降低（可自定义）",
				4, 0, Challenge.Category.MOVEMENT_PENALTY, "minecraft:turtle_helmet");
		reducedOxygen.setCustomizable(true);
		reducedOxygen.setCustomValue(50);
		reducedOxygen.setCustomValueLabel("氧气降低百分比");
		register(reducedOxygen);

		// ===== FOOD PENALTIES =====
		register(new Challenge("no_eat_beef", "不能吃牛肉", "禁止食用牛肉（生牛肉与熟牛排都算）",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:cooked_beef"));
		register(new Challenge("no_eat_pork", "不能吃猪肉", "禁止食用猪肉（生猪排与熟猪排都算）",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:cooked_porkchop"));
		register(new Challenge("no_eat_chicken", "不能吃鸡肉", "禁止食用鸡肉（生鸡肉与熟鸡肉都算）",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:cooked_chicken"));
		register(new Challenge("no_eat_mutton", "不能吃羊肉", "禁止食用羊肉（生羊肉与熟羊肉都算）",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:cooked_mutton"));
		register(new Challenge("no_eat_fish", "不能吃鱼", "禁止食用各种鱼",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:cooked_cod"));
		register(new Challenge("no_eat_apple", "不能吃苹果", "禁止食用苹果和金苹果",
				3, 2, Challenge.Category.FOOD_PENALTY, "minecraft:apple"));
		register(new Challenge("no_eat_golden_apple", "不能吃金苹果", "禁止食用金苹果和附魔金苹果",
				6, 3, Challenge.Category.FOOD_PENALTY, "minecraft:golden_apple"));
		register(new Challenge("no_eat_bread", "不能吃面包", "禁止食用面包",
				2, 1, Challenge.Category.FOOD_PENALTY, "minecraft:bread"));
		register(new Challenge("no_eat_potato", "不能吃烤马铃薯", "禁止食用烤马铃薯",
				2, 1, Challenge.Category.FOOD_PENALTY, "minecraft:baked_potato"));

		Challenge reducedFoodSaturation = new Challenge("reduced_food_saturation", "食物饥饿值与饱和度降低", "所有食物恢复的饥饿值和饱和度降低（可自定义）",
				5, 0, Challenge.Category.FOOD_PENALTY, "minecraft:cake");
		reducedFoodSaturation.setCustomizable(true);
		reducedFoodSaturation.setCustomValue(50);
		reducedFoodSaturation.setCustomValueLabel("饥饿降低百分比");
		register(reducedFoodSaturation);

		// ===== PASSIVE/FRIENDLY MOB CHANGES =====
		register(new Challenge("friendly_counter_attack", "友好生物受攻击会反击", "攻击友好生物后会受到反击伤害（等同空手）",
				10, 0, Challenge.Category.FRIENDLY_MOB_COUNTER, "minecraft:cow_spawn_egg"));

		// ===== POTION RESTRICTIONS =====
		register(new Challenge("no_potions", "不能使用药水", "禁止使用任何药水（斥候职业自动豁免）",
				6, 3, Challenge.Category.POTION_RESTRICTION, "minecraft:potion"));

		// ===== 规则开关（不计分）=====
		register(new Challenge("rule_pvp", "玩家伤害（PVP）", "开启后玩家之间可以互相造成伤害",
				0, 0, Challenge.Category.RULE, "minecraft:diamond_sword"));
		register(new Challenge("rule_night_vision", "全局夜视", "游戏期间所有玩家获得夜视效果",
				0, 0, Challenge.Category.RULE, "minecraft:golden_carrot"));
		register(new Challenge("rule_keep_inventory", "保留物品栏", "死亡时不掉落物品和经验（keepInventory）",
				0, 0, Challenge.Category.RULE, "minecraft:bundle"));
		register(new Challenge("rule_team_chest", "团队箱", "开启后玩家可以打开团队箱（G 键或按钮）存取物品",
				0, 0, Challenge.Category.RULE, "minecraft:chest"));
		register(new Challenge("rule_task_rush", "任务抢夺", "开启后全员共享同一专属任务：任意玩家完成后，所有人立刻刷新为同一个新任务",
				0, 0, Challenge.Category.RULE, "minecraft:compass"));
		register(new Challenge("rule_hunter_game", "猎人游戏", "（仅团队模式）主动击杀其他玩家夺取其2分；误杀队友则全队每人扣除2分（仅PVP开启后有效）",
				0, 0, Challenge.Category.RULE, "minecraft:iron_sword"));

		// 全部规则注册完毕 → 统一设置任务冲突键
		setConflictKeys();
	}

	/** 统一设置规则的任务冲突键（必须在所有规则注册之后调用） */
	private void setConflictKeys() {
		// 任务冲突键分三类前缀：item:（使用/吃/合成产物）、craft:（合成目标）、mine:（挖掘目标）。
		// KILL_MOB（击杀）与"不能被 XX 杀死"（被杀）语义不同，击杀类任务不产出任何冲突键。
		getChallenge("no_enchanting").setConflictKeys("block:minecraft:enchanting_table",
				"item:minecraft:enchanting_table");
		getChallenge("no_anvil").setConflictKeys("block:minecraft:anvil", "item:minecraft:anvil");
		getChallenge("no_chest").setConflictKeys("block:minecraft:chest",
				"craft:minecraft:chest", "item:minecraft:chest");
		getChallenge("no_ender_pearl").setConflictKeys("item:minecraft:ender_pearl");
		getChallenge("no_furnace").setConflictKeys("block:minecraft:furnace",
				"craft:minecraft:furnace", "item:minecraft:furnace");
		getChallenge("no_brewing_stand").setConflictKeys("block:minecraft:brewing_stand",
				"craft:minecraft:brewing_stand", "item:minecraft:brewing_stand");
		getChallenge("no_beacon").setConflictKeys("block:minecraft:respawn_anchor",
				"item:minecraft:respawn_anchor");
		getChallenge("no_diamond").setConflictKeys("item:minecraft:diamond",
				"mine:minecraft:diamond_ore", "mine:minecraft:deepslate_diamond_ore");
		getChallenge("no_gold").setConflictKeys("item:minecraft:gold_ingot", "item:minecraft:raw_gold",
				"mine:minecraft:gold_ore", "mine:minecraft:deepslate_gold_ore", "mine:minecraft:nether_gold_ore");
		getChallenge("no_netherite").setConflictKeys("item:minecraft:netherite_ingot", "item:minecraft:netherite_scrap",
				"mine:minecraft:ancient_debris");
		getChallenge("no_eat_beef").setConflictKeys("item:minecraft:cooked_beef", "item:minecraft:beef");
		getChallenge("no_eat_pork").setConflictKeys("item:minecraft:cooked_porkchop", "item:minecraft:porkchop");
		getChallenge("no_eat_chicken").setConflictKeys("item:minecraft:cooked_chicken", "item:minecraft:chicken");
		getChallenge("no_eat_mutton").setConflictKeys("item:minecraft:cooked_mutton", "item:minecraft:mutton");
		getChallenge("no_eat_fish").setConflictKeys("item:minecraft:cooked_cod", "item:minecraft:cod",
				"item:minecraft:cooked_salmon", "item:minecraft:salmon");
		getChallenge("no_eat_apple").setConflictKeys("item:minecraft:apple");
		getChallenge("no_eat_golden_apple").setConflictKeys("item:minecraft:golden_apple",
				"item:minecraft:enchanted_golden_apple");
		getChallenge("no_eat_bread").setConflictKeys("item:minecraft:bread");
		getChallenge("no_eat_potato").setConflictKeys("item:minecraft:baked_potato", "item:minecraft:potato");
		getChallenge("no_potions").setConflictKeys("item:minecraft:potion",
				"item:minecraft:splash_potion", "item:minecraft:lingering_potion");
		getChallenge("no_lapis").setConflictKeys("item:minecraft:lapis_lazuli",
				"mine:minecraft:lapis_ore", "mine:minecraft:deepslate_lapis_ore");
		getChallenge("no_fishing_rod").setConflictKeys("item:minecraft:fishing_rod");
	}

	private void register(Challenge challenge) {
		allChallenges.put(challenge.getId(), challenge);
	}

	/**
	 * 确保挑战定义已注册。定义是纯数据（无服务端依赖），
	 * 客户端 HUD/GUI 也会调用此方法来解析真实的名称与图标，避免硬编码映射表失配。
	 */
	public void ensureRegistered() {
		if (allChallenges.isEmpty()) {
			registerAllChallenges();
		}
	}

	public Collection<Challenge> getAllChallenges() {
		return allChallenges.values();
	}

	public Challenge getChallenge(String id) {
		return allChallenges.get(id);
	}

	public int getChallengeCount() {
		return allChallenges.size();
	}

	/**
	 * Get enabled challenges for a player (or team's challenges in team mode).
	 */
	public Set<String> getPlayerChallenges(UUID playerId) {
		return playerChallenges.getOrDefault(playerId, Collections.emptySet());
	}

	/**
	 * Enable challenge for a player
	 */
	public void enableChallenge(UUID playerId, String challengeId) {
		playerChallenges.computeIfAbsent(playerId, k -> new HashSet<>()).add(challengeId);
	}

	/**
	 * Disable challenge for a player
	 */
	public void disableChallenge(UUID playerId, String challengeId) {
		Set<String> set = playerChallenges.get(playerId);
		if (set != null) {
			set.remove(challengeId);
		}
	}

	public void clearPlayerChallenges(UUID playerId) {
		playerChallenges.remove(playerId);
	}

	public void resetAllPlayers() {
		playerChallenges.clear();
		recentlyViolated.clear();
		penalizedOnce.clear();
	}

	/** 新一局开始时清空违规扣分记录（每条规则的"仅扣一次"名额重置） */
	public void clearPenaltyFlags() {
		penalizedOnce.clear();
	}

	// ============ 会话持久化（退出重进保留局内状态）所需的存取器 ============
	public Map<UUID, Set<String>> getAllPlayerChallenges() {
		Map<UUID, Set<String>> copy = new HashMap<>();
		playerChallenges.forEach((k, v) -> copy.put(k, new HashSet<>(v)));
		return copy;
	}

	public void setPlayerChallenges(UUID playerId, Set<String> ids) {
		playerChallenges.put(playerId, new HashSet<>(ids));
	}

	/**
	 * Calculate the total initial score for a player based on their selected challenges.
	 * Each challenge has its OWN base score, so more challenges = higher initial score.
	 * 可自定义数值的挑战按数值加权：分数 = baseScore × 数值 / 50（50% 为基准，削弱越多分越高）。
	 */
	public int calculateInitialScore(UUID playerId) {
		int total = 0;
		for (String challengeId : getPlayerChallenges(playerId)) {
			Challenge c = getChallenge(challengeId);
			if (c == null) continue;
			if ("inv_slot_lock".equals(challengeId)) {
				// 背包格子锁定：27格 +4 分 / 18格 +8 分 / 9格 +16 分
				int v = c.getCustomValue();
				total += v == 27 ? 4 : v == 18 ? 8 : 16;
			} else if (c.isCustomizable() && c.getCustomValue() > 0) {
				total += Math.round(c.getBaseScore() * c.getCustomValue() / 50.0f);
			} else {
				total += c.getBaseScore();
			}
		}
		// 环境恶化板块：全员共享，激活中的规则给所有玩家计分（开启即加分）
		for (Challenge c : getAllChallenges()) {
			if ("environment".equals(c.getSection())
					&& com.survivalcontract.game.GameStateManager.getInstance().isRuleActive(c.getId())) {
				total += c.getBaseScore();
			}
		}
		return total;
	}

	// ============ 神的谎言：规则随机分配 ============

	/**
	 * 随机抽一组「生效得分」总和恰好等于 targetScore 的正向规则（子集和 DP + 随机回溯）。
	 * 池：baseScore > 0 的玩家规则（排除 rule_* 全局开关、背包格锁定与环境恶化板块共享规则）。
	 * 可自定义数值的规则按生效加权分入池（与 calculateInitialScore 的贡献一致）。
	 * 理论上凑不满目标分时，返回不超过目标的最大可达组合。
	 */
	public List<String> randomRuleSetForScore(int targetScore, java.util.Random rnd) {
		record PoolEntry(String id, int score) {}
		List<PoolEntry> pool = new ArrayList<>();
		for (Challenge c : getAllChallenges()) {
			if (c.getBaseScore() <= 0) continue;
			String cid = c.getId();
			if (cid.startsWith("rule_") || "inv_slot_lock".equals(cid) || "environment".equals(c.getSection())) continue;
			int sc = c.isCustomizable() && c.getCustomValue() > 0
					? Math.round(c.getBaseScore() * c.getCustomValue() / 50.0f) : c.getBaseScore();
			if (sc > 0 && sc <= targetScore) pool.add(new PoolEntry(cid, sc));
		}
		if (pool.isEmpty() || targetScore <= 0) return new ArrayList<>();
		pool.sort((a, b) -> Integer.compare(a.score(), b.score()));
		int n = pool.size();
		boolean[][] dp = new boolean[n + 1][targetScore + 1];
		dp[0][0] = true;
		for (int i = 1; i <= n; i++) {
			int sc = pool.get(i - 1).score();
			for (int s = 0; s <= targetScore; s++) {
				dp[i][s] = dp[i - 1][s] || (s >= sc && dp[i - 1][s - sc]);
			}
		}
		int best = targetScore;
		while (best > 0 && !dp[n][best]) best--;
		if (best == 0) return new ArrayList<>();
		// 随机回溯出一组方案（每个位置在"跳过可行"与"选取可行"之间随机决策；
		// 疾病规则最多选 3 条——单个玩家身上最多同时存在三种疾病，第五十四批）
		List<String> out = new ArrayList<>();
		int s = best, diseaseCount = 0;
		for (int i = n; i >= 1 && s > 0; i--) {
			int sc = pool.get(i - 1).score();
			boolean skipOk = dp[i - 1][s];
			boolean takeOk = s >= sc && dp[i - 1][s - sc];
			if (takeOk && (!skipOk || rnd.nextBoolean())) {
				var cTake = getChallenge(pool.get(i - 1).id());
				boolean isDisease = cTake != null && "disease".equals(cTake.getSection());
				if (isDisease && diseaseCount >= 3 && skipOk) continue; // 疾病已满 3 → 强制跳过（DP 保证剩余仍可达）
				out.add(pool.get(i - 1).id());
				s -= sc;
				if (isDisease) diseaseCount++;
			}
		}
		return out;
	}

	/**
	 * Record a challenge violation for a player
	 * @return the penalty applied (0 if already counted this tick)
	 */
	public int recordViolation(UUID playerId, String challengeId) {
		Set<String> playerSet = recentlyViolated.computeIfAbsent(playerId, k -> new HashSet<>());
		if (!playerSet.add(challengeId)) {
			return 0; // Already violated this tick
		}

		Challenge c = getChallenge(challengeId);
		if (c == null || !getPlayerChallenges(playerId).contains(challengeId)) {
			return 0;
		}

		// 每玩家每条规则一局仅扣一次（第二次违规起只拦截不再扣分）
		if (!penalizedOnce.add(playerId + ":" + challengeId)) {
			return 0;
		}

		return c.getPenaltyScore();
	}

	/**
	 * Reset per-tick violation tracking for a player
	 */
	public void tickViolationReset(UUID playerId) {
		recentlyViolated.remove(playerId);
	}
}
