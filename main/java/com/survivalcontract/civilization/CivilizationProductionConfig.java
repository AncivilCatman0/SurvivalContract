package com.survivalcontract.civilization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.survivalcontract.SurvivalContract;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 文明时代资源产出配置：config/survival-contract/civilization-production.json
 * <ul>
 *   <li>覆盖全部资源区的产出间隔 / 数量 / 概率副产品 / 概率刷怪 / 收获前置方块 / 数量上限，
 *       以及野生动物区、大本营战斗物资、大本营物资桶的全部参数</li>
 *   <li>每局开始（setup）时重新读取——修改配置文件后下一局生效</li>
 *   <li>文件缺失时自动生成默认值；解析失败时使用默认值且不覆盖用户文件</li>
 *   <li>字段说明直接写在 JSON 的「说明」键里，玩家可自行调整</li>
 * </ul>
 */
public class CivilizationProductionConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 产出配置文件路径：config/survival-contract/civilization-production.json */
	public static Path file() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("survival-contract").resolve("civilization-production.json");
	}

	// ==================== JSON 模型 ====================

	/** 配置版本号（默认值更新后旧版本文件自动迁移——备份 .bak 并重新生成） */
	public int configVersion = CONFIG_VERSION;

	/** 顶层说明（随默认文件生成） */
	public String 说明 = "文明时代资源产出配置。每局开始时读取——修改后下一局生效。"
			+ "configVersion 为配置版本号（请勿修改）——默认值更新后旧版本文件会自动备份为 .bak 并重新生成。"
			+ "时间单位为秒；数量为每次产出的物品个数；概率为百分比（支持小数）。"
			+ "物品 id 使用原版注册名，可省略 minecraft: 前缀（如 oak_log）。"
			+ "产出只进入区块内的箱子/木桶（大本营战斗物资只进物资桶）；区块内没有容器时不产出。"
			+ "count 设为 0 可关闭该区产出。铜/铁/金/钻石/石英/青金石/合金区需要按要求放置熔炉/高炉/煤炭块/钻石块才会产出（见各区说明）。"
			+ "各资源区还支持：outputChancePercent（主产物本次产出的概率%，0 视为 100）、"
			+ "spawnIntervalSeconds（刷怪间隔秒，0 = 跟随产出间隔 intervalSeconds）、"
			+ "spawnMaxHealth（刷出生物最大生命值，0 = 原版默认，如小型岩浆怪 1 点血）、"
			+ "spawnCap（该区块内本系统生成的生物上限，0 = 默认 12）；"
			+ "bonuses 每项支持 count（本次命中时产出的数量，0 视为 1）与 totalCap（单个区块累计产出上限，0 = 无上限，如钻石区黑曜石 30）。"
			+ "大本营战斗物资：每 intervalSeconds 秒产出 baseCount 组；每占领 claimsPerStep 个区块间隔 -reducePerStepSeconds 秒，最低 minSeconds 秒。"
			+ "zones 每区还支持 requireBlocks：收获前置方块 id 列表（如 copper_chest——放置后才产出；缺省用各区默认前置）。"
			+ "zoneLimits.limits 限制各资源区在全图的数量上限（如 ALLOY:5 / DIAMOND:20；缺省或 0 = 无限制）。"
			+ "删除本文件后重启会重新生成默认配置。";

	/** 资源区产出（键 = 区块类型名：WOOD/STONE/COPPER/IRON/GOLD/DIAMOND/QUARTZ/LAPIS/ALLOY/SAND） */
	public Map<String, ZoneCfg> zones = new LinkedHashMap<>();

	/** 野生动物区 */
	public WildlifeCfg wildlife = new WildlifeCfg();

	/** 大本营战斗物资（风弹 + 金苹果） */
	public HqSuppliesCfg hqSupplies = new HqSuppliesCfg();

	/** 大本营物资桶（末影珍珠 + 风弹 + 稀有物资） */
	public SupplyBarrelCfg supplyBarrel = new SupplyBarrelCfg();

	/** 资源区数量上限（键 = 区块类型名，值 = 全图最多数量；缺省或 0 = 无限制） */
	public ZoneLimitsCfg zoneLimits = new ZoneLimitsCfg();

	/** 单个资源区的产出配置 */
	public static class ZoneCfg {
		public String 说明 = "";
		/** 产出间隔（秒） */
		public int intervalSeconds;
		/** 主产出物品 id（原版注册名，可省略 minecraft: 前缀） */
		public String output = "";
		/** 每次产出的主产物数量（0 = 关闭该区产出） */
		public int count;
		/** 主产物本次产出的概率（百分比；0 视为 100 = 必定产出） */
		public double outputChancePercent = 100;
		/** 概率副产品 */
		public List<BonusCfg> bonuses = new ArrayList<>();
		/** 概率刷怪的生物 id（留空 = 不刷怪） */
		public String spawnMob = "";
		/** 每次刷怪结算时刷怪概率（百分比） */
		public int spawnChancePercent;
		/** 每次刷怪数量 */
		public int spawnCount;
		/** 刷怪间隔（秒；0 = 跟随产出间隔 intervalSeconds） */
		public int spawnIntervalSeconds = 0;
		/** 刷出生物最大生命值（0 = 原版默认：岩浆怪等 cube 类固定小型，其余为原版注册属性） */
		public double spawnMaxHealth = 0;
		/** 该区块内本系统生成的生物上限（0 = 默认 12） */
		public int spawnCap = 12;
		/** 收获前置方块 id 列表（原版方块注册名，可省略 minecraft: 前缀；空 = 无前置）。
		 *  缺省（null）时使用各区默认：石头区=任意木头、铜区=熔炉+煤炭块、铁区=熔炉+铜块+铜箱子、
		 *  金区=高炉+铁块+铜箱子、钻石区=高炉+金块+铜箱子、石英区=熔炉、
		 *  青金石区=熔炉（须在 Y≤0 层）+末影箱、合金区=高炉+钻石块+铜箱子、沙区=末影箱。
		 *  铜箱子按氧化/涂蜡家族匹配（任意变体有效）；木头按 *_wood 家族匹配。 */
		public List<String> requireBlocks;
	}

	/** 概率副产品：chancePercent% 概率额外产出 count 个物品（items 候选池随机取一） */
	public static class BonusCfg {
		public String 说明 = "";
		public double chancePercent;
		/** 本次命中时产出的数量（0 视为 1） */
		public int count = 1;
		/** 单个区块累计产出上限（0 = 无上限；达到上限后该副产品不再产出） */
		public int totalCap = 0;
		public List<String> items = new ArrayList<>();
	}

	/** 野生动物区配置 */
	public static class WildlifeCfg {
		public String 说明 = "野生动物区：每 intervalSeconds 秒刷 animalPerInterval 只动物（区内上限 animalCap 只）；"
				+ "villagerChancePercent 概率刷 1 名村民（区内无村民时）；有村民时每 intervalSeconds 秒产出 emeraldCount 个绿宝石。";
		public int intervalSeconds = 60;
		public int animalPerInterval = 6;
		public int animalCap = 12;
		public int villagerChancePercent = 15;
		public int emeraldCount = 2;
	}

	/** 大本营战斗物资配置 */
	public static class HqSuppliesCfg {
		public String 说明 = "大本营战斗物资：每 intervalSeconds 秒按 chancePercent% 概率产出 baseCount 组"
				+ "（每组 = items 池里每种物品各 1 个，默认风弹 1 + 金苹果 1）；"
				+ "每占领 claimsPerStep 个区块，产出间隔 -reducePerStepSeconds 秒，最低 minSeconds 秒；"
				+ "仅注入本队大本营的物资桶，桶满则溢出丢弃。";
		/** 基础产出间隔（秒） */
		public int intervalSeconds = 300;
		/** 每次结算产出的组数 */
		public int baseCount = 1;
		/** 每占领多少个区块缩短一次间隔 */
		public int claimsPerStep = 5;
		/** 每步缩短的秒数 */
		public int reducePerStepSeconds = 15;
		/** 间隔下限（秒） */
		public int minSeconds = 60;
		/** 每次结算的产出概率（百分比；0 视为 100） */
		public double chancePercent = 100;
		/** 每组产出的物品 id 池：每组每种物品各产出 1 个（默认 = 旧版固定行为） */
		public List<String> items = new ArrayList<>(List.of("wind_charge", "golden_apple"));
	}

	/** 大本营物资桶配置 */
	public static class SupplyBarrelCfg {
		public String 说明 = "大本营物资桶（猪灵脚下）：每 intervalSeconds 秒产出 pearlCount 个末影珍珠 + windChargeCount 个风弹；"
				+ "rareChancePercent 概率额外产出稀有物资（rareItems 池随机取一，默认鞘翅/重锤，全队提示）；"
				+ "每多占领 1 个区块刷新间隔 -reducePerClaimSeconds 秒，最低 minSeconds 秒。";
		public int intervalSeconds = 600;
		public int minSeconds = 120;
		public int reducePerClaimSeconds = 10;
		public int pearlCount = 1;
		public int windChargeCount = 1;
		public int rareChancePercent = 5;
		/** 稀有物资候选池（物品 id；默认鞘翅/重锤） */
		public List<String> rareItems = new ArrayList<>(List.of("elytra", "mace"));
	}

	/** 资源区数量上限配置 */
	public static class ZoneLimitsCfg {
		public String 说明 = "资源区数量上限：limits 键 = 资源区类型（WOOD/STONE/COPPER/IRON/GOLD/DIAMOND/QUARTZ/LAPIS/ALLOY/SAND），"
				+ "值 = 全图（15×15 区块）内该类型资源区的最多数量；缺省或 0 = 无限制。"
				+ "默认：合金区 ≤5、钻石区 ≤20。";
		/** 键 = 资源区类型名；值 = 上限（0 = 无限制） */
		public Map<String, Integer> limits = new LinkedHashMap<>();

		public ZoneLimitsCfg() {
			limits.put("ALLOY", 5);
			limits.put("DIAMOND", 20);
		}
	}

	// ==================== 默认值（与旧版硬编码行为一致） ====================

	/** 配置版本：默认值变更（概率/间隔调整等）时 +1；旧版本文件会备份为 .bak 后重新生成默认。
	 *  5 = 第七十八批：合金碎片区新增 10% 概率额外产出 1 个下界岩 */
	public static final int CONFIG_VERSION = 5;

	/** 参与产出的资源区（大本营/野生动物区不在其中——野生动物区有独立配置节） */
	static final CivilizationManager.ZoneType[] PRODUCIBLE = {
			CivilizationManager.ZoneType.WOOD, CivilizationManager.ZoneType.STONE,
			CivilizationManager.ZoneType.COPPER, CivilizationManager.ZoneType.IRON,
			CivilizationManager.ZoneType.GOLD, CivilizationManager.ZoneType.DIAMOND,
			CivilizationManager.ZoneType.QUARTZ, CivilizationManager.ZoneType.LAPIS,
			CivilizationManager.ZoneType.ALLOY, CivilizationManager.ZoneType.SAND};

	/** 全量默认配置（也是用户文件缺项时的回退基准） */
	private static final CivilizationProductionConfig DEFAULTS = buildDefaults();

	private static CivilizationProductionConfig buildDefaults() {
		CivilizationProductionConfig c = new CivilizationProductionConfig();

		c.zones.put("WOOD", require(zone("木头区：每 intervalSeconds 秒产出 count 个橡木原木；"
				+ "30% 概率额外产出 1 个苹果；10% 概率额外产出 1 个随机蔬果（西瓜片/南瓜/胡萝卜/小麦/马铃薯）。无前置。",
				60, "oak_log", 6,
				bonus("苹果", 30, "apple"),
				bonus("随机蔬果", 10, "melon_slice", "pumpkin", "carrot", "wheat", "potato"))));
		c.zones.put("STONE", require(zone("石头区：每 intervalSeconds 秒产出 count 个圆石；20% 概率额外产出 1 个煤炭；"
				+ "15% 概率额外产出 1 个沙砾。前置：任意木头（各树种的六面皮木块，非原木）。",
				60, "cobblestone", 10,
				bonus("煤炭", 20, "coal"),
				bonus("沙砾", 15, "gravel")), "oak_wood"));
		c.zones.put("COPPER", require(zone("铜矿区：每 intervalSeconds 秒产出 count 个粗铜。前置：熔炉 + 煤炭块。",
				60, "raw_copper", 12), "furnace", "coal_block"));
		c.zones.put("IRON", require(zone("铁矿区：每 intervalSeconds 秒产出 count 个粗铁。前置：熔炉 + 铜块 + 铜箱子"
				+ "（氧化/涂蜡变体同样有效，氧化不会中断收获）。",
				60, "raw_iron", 8), "furnace", "copper_block", "waxed_copper_chest"));
		c.zones.put("GOLD", require(zone("金矿区：每 intervalSeconds 秒产出 count 个粗金。前置：高炉 + 铁块 + 铜箱子"
				+ "（氧化/涂蜡变体同样有效，氧化不会中断收获）。",
				60, "raw_gold", 10), "blast_furnace", "iron_block", "waxed_copper_chest"));
		c.zones.put("DIAMOND", require(zone("钻石区：每 intervalSeconds 秒产出 count 个钻石；10% 概率额外产出 1 个黑曜石"
				+ "（每个钻石区累计最多 30 个，产满后不再产出）。前置：高炉 + 金块 + 铜箱子"
				+ "（氧化/涂蜡变体同样有效，氧化不会中断收获）。",
				60, "diamond", 5,
				bonusCap("黑曜石", 10, 30, "obsidian")), "blast_furnace", "gold_block", "waxed_copper_chest"));
		c.zones.put("QUARTZ", require(zone("石英区：每 intervalSeconds 秒产出 count 个石英；8% 概率额外产出 1 个地狱疣；"
				+ "30% 概率刷 2 只岩浆怪。前置：熔炉。",
				60, "quartz", 12,
				new BonusCfg[]{bonus("地狱疣", 8, "nether_wart")},
				"magma_cube", 30, 2), "furnace"));
		c.zones.put("LAPIS", require(zone("青金石区：每 intervalSeconds 秒产出 count 个青金石；30% 概率额外产出 1 个红石粉；"
				+ "15% 概率额外产出 1 个经验瓶。前置：熔炉 + 末影箱，且熔炉必须放置在 Y≤0 层。",
				60, "lapis_lazuli", 10,
				bonus("红石粉", 30, "redstone"),
				bonus("经验瓶", 15, "experience_bottle")), "furnace", "ender_chest"));
		c.zones.put("ALLOY", require(zone("合金碎片区：每 intervalSeconds 秒产出 count 个下界合金碎片；8% 概率额外产出 1 个烈焰棒；"
				+ "4% 概率额外产出 1 个下界合金升级锻造模板（每区块累计最多 3 个）；"
				+ "10% 概率额外产出 1 个下界岩；"
				+ "25% 概率刷 1 只凋灵骷髅。前置：高炉 + 钻石块 + 铜箱子"
				+ "（氧化/涂蜡变体同样有效，氧化不会中断收获）。",
				60, "netherite_scrap", 10,
				new BonusCfg[]{bonus("烈焰棒", 8, "blaze_rod"),
						bonusCap("锻造模板", 4, 3, "netherite_upgrade_smithing_template"),
						bonus("下界岩", 10, "netherrack")},
				"wither_skeleton", 25, 1),
				"blast_furnace", "diamond_block", "waxed_copper_chest"));
		c.zones.put("SAND", require(zone("沙区：需要先放置末影箱才能开始收获——每 intervalSeconds 秒产出 count 个沙子；"
				+ "5% 概率额外产出 1 个火药；10% 概率额外产出 1 个甘蔗；15% 概率额外产出 1 个玻璃。"
				+ "产出照常进入区内放置的箱子/木桶。",
				20, "sand", 8,
				bonus("火药", 5, "gunpowder"),
				bonus("甘蔗", 10, "sugar_cane"),
				bonus("玻璃", 15, "glass")), "ender_chest"));
		c.resolve(c);
		return c;
	}

	/** 设置收获前置方块（默认无前置；方块 id 可省略 minecraft: 前缀） */
	private static ZoneCfg require(ZoneCfg z, String... blocks) {
		z.requireBlocks = new ArrayList<>(List.of(blocks));
		return z;
	}

	private static ZoneCfg zone(String desc, int interval, String output, int count, BonusCfg... bonuses) {
		return zone(desc, interval, output, count, bonuses, "", 0, 0);
	}

	private static ZoneCfg zone(String desc, int interval, String output, int count,
	                            BonusCfg[] bonuses, String spawnMob, int spawnChance, int spawnCount) {
		ZoneCfg z = new ZoneCfg();
		z.说明 = desc;
		z.intervalSeconds = interval;
		z.output = output;
		z.count = count;
		for (BonusCfg b : bonuses) z.bonuses.add(b);
		z.spawnMob = spawnMob;
		z.spawnChancePercent = spawnChance;
		z.spawnCount = spawnCount;
		return z;
	}

	private static BonusCfg bonus(String desc, double chance, String... items) {
		BonusCfg b = new BonusCfg();
		b.说明 = desc + "（概率百分比 " + chance + "%，每次产出结算时按此概率额外产出 1 个）";
		b.chancePercent = chance;
		for (String s : items) b.items.add(s);
		return b;
	}

	/** 带单区累计上限的副产品（totalCap = 单个区块累计最多产出的个数，0 = 无上限） */
	private static BonusCfg bonusCap(String desc, double chance, int cap, String... items) {
		BonusCfg b = bonus(desc, chance, items);
		b.说明 = desc + "（概率百分比 " + chance + "%，每次产出结算时按此概率额外产出 1 个；"
				+ "单区块累计上限 " + cap + " 个，产满后不再产出）";
		b.totalCap = cap;
		return b;
	}

	// ==================== 解析结果（transient，不写入文件） ====================

	/** 已解析的概率副产品（totalCap = 单区块累计上限，0 = 无上限） */
	public record RBonus(double chancePercent, int count, int totalCap, Item[] items) {}

	private final transient Map<CivilizationManager.ZoneType, Long> resInterval = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Integer> resCount = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Item> resOutput = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Double> resOutputChance = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, List<RBonus>> resBonuses = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, String> resSpawnMob = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Integer> resSpawnChance = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Integer> resSpawnCount = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Long> resSpawnInterval = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Double> resSpawnMaxHealth = new EnumMap<>(CivilizationManager.ZoneType.class);
	private final transient Map<CivilizationManager.ZoneType, Integer> resSpawnCap = new EnumMap<>(CivilizationManager.ZoneType.class);
	/** 已解析的收获前置方块（空数组 = 无前置） */
	private final transient Map<CivilizationManager.ZoneType, Block[]> resRequireBlocks = new EnumMap<>(CivilizationManager.ZoneType.class);
	/** 已解析的资源区数量上限（缺省 = 无限制） */
	private final transient Map<CivilizationManager.ZoneType, Integer> resZoneLimits = new EnumMap<>(CivilizationManager.ZoneType.class);
	/** 大本营战斗物资：已解析的产出概率与物品池 */
	private final transient List<Item> resHqItems = new ArrayList<>();
	private transient double resHqChance = 100;
	/** 大本营物资桶：已解析的稀有物资池 */
	private final transient List<Item> resRareItems = new ArrayList<>();

	/** 把字符串物品 id 解析为 Item（无效回退默认值），并保证所有资源区条目齐全（fallback = 缺项回退基准） */
	private void resolve(CivilizationProductionConfig fallback) {
		for (CivilizationManager.ZoneType t : PRODUCIBLE) {
			ZoneCfg d = fallback.zones.get(t.name());
			ZoneCfg c = zones != null ? zones.get(t.name()) : null;
			if (c == null) c = d;
			resInterval.put(t, Math.max(1, c.intervalSeconds > 0 ? c.intervalSeconds : d.intervalSeconds) * 20L);
			resCount.put(t, Math.max(0, c.count >= 0 ? c.count : d.count));
			resOutput.put(t, item(c.output, item(d.output, Items.OAK_LOG)));
			resOutputChance.put(t, c.outputChancePercent > 0 ? Math.min(100.0, c.outputChancePercent) : 100.0);
			List<RBonus> list = new ArrayList<>();
			if (c.bonuses != null) {
				for (BonusCfg b : c.bonuses) {
					if (b == null || b.items == null || b.items.isEmpty()) continue;
					List<Item> pool = new ArrayList<>();
					for (String s : b.items) {
						Item it = item(s, null);
						if (it != null) pool.add(it);
					}
					if (!pool.isEmpty()) {
						list.add(new RBonus(Math.max(0, b.chancePercent), Math.max(1, b.count),
								Math.max(0, b.totalCap), pool.toArray(new Item[0])));
					}
				}
			}
			resBonuses.put(t, list);
			resSpawnMob.put(t, c.spawnMob == null ? "" : c.spawnMob.trim().toLowerCase(Locale.ROOT));
			resSpawnChance.put(t, Math.max(0, c.spawnChancePercent));
			resSpawnCount.put(t, Math.max(1, c.spawnCount));
			// 刷怪间隔：0 = 跟随产出间隔；上限：0 = 默认 12
			long spawnInt = c.spawnIntervalSeconds > 0 ? c.spawnIntervalSeconds * 20L : resInterval.get(t);
			resSpawnInterval.put(t, spawnInt);
			resSpawnMaxHealth.put(t, Math.max(0, c.spawnMaxHealth));
			resSpawnCap.put(t, c.spawnCap > 0 ? c.spawnCap : 12);
			// 收获前置方块：用户配置 > 各区默认；解析失败的方块 id 丢弃并告警
			List<String> reqRaw = c.requireBlocks != null ? c.requireBlocks
					: (d.requireBlocks != null ? d.requireBlocks : List.of());
			List<Block> reqList = new ArrayList<>();
			for (String s : reqRaw) {
				Block b = blockOf(s);
				if (b != null) reqList.add(b);
			}
			resRequireBlocks.put(t, reqList.toArray(new Block[0]));
			// 数量上限：用户显式配置 > 默认；0/负数 = 无限制；键完全缺省时回退默认表中同名键
			Integer lim = zoneLimits != null && zoneLimits.limits != null
					? zoneLimits.limits.get(t.name()) : null;
			if (lim != null && lim <= 0) lim = null;
			if (lim == null) {
				Map<String, Integer> dLim = fallback.zoneLimits != null ? fallback.zoneLimits.limits : null;
				Integer dl = dLim != null ? dLim.get(t.name()) : null;
				if (dl != null && dl > 0) lim = dl;
			}
			resZoneLimits.put(t, lim);
		}
		// 大本营战斗物资：概率 + 物品池（缺省/全部无效 → 旧版固定行为 风弹+金苹果）
		resHqChance = hqSupplies != null && hqSupplies.chancePercent > 0
				? Math.min(100.0, hqSupplies.chancePercent) : 100.0;
		resHqItems.clear();
		if (hqSupplies != null && hqSupplies.items != null) {
			for (String s : hqSupplies.items) {
				Item it = item(s, null);
				if (it != null) resHqItems.add(it);
			}
		}
		if (resHqItems.isEmpty()) {
			resHqItems.add(Items.WIND_CHARGE);
			resHqItems.add(Items.GOLDEN_APPLE);
		}
		// 大本营物资桶：稀有物资池（缺省/全部无效 → 旧版固定行为 鞘翅/重锤）
		resRareItems.clear();
		if (supplyBarrel != null && supplyBarrel.rareItems != null) {
			for (String s : supplyBarrel.rareItems) {
				Item it = item(s, null);
				if (it != null) resRareItems.add(it);
			}
		}
		if (resRareItems.isEmpty()) {
			resRareItems.add(Items.ELYTRA);
			resRareItems.add(Items.MACE);
		}
	}

	/** 物品 id → Item（可省略 minecraft: 前缀；无效/未知回退 fallback，fallback 为 null 表示丢弃该项） */
	private static Item item(String id, Item fallback) {
		if (id == null || id.isBlank()) return fallback;
		try {
			String s = id.trim().toLowerCase(Locale.ROOT);
			if (!s.contains(":")) s = "minecraft:" + s;
			Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(s));
			return (it == null || it == Items.AIR) ? fallback : it;
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 产出配置中的物品 id 无效: {}（回退 {}）", id, fallback);
			return fallback;
		}
	}

	/** 方块 id → Block（可省略 minecraft: 前缀；无效/未知返回 null 并告警） */
	private static Block blockOf(String id) {
		if (id == null || id.isBlank()) return null;
		try {
			String s = id.trim().toLowerCase(Locale.ROOT);
			if (!s.contains(":")) s = "minecraft:" + s;
			Block b = BuiltInRegistries.BLOCK.getValue(Identifier.parse(s));
			if (b == null || b == Blocks.AIR) {
				SurvivalContract.LOGGER.warn("[Civilization] 产出配置中的前置方块 id 无效: {}（忽略）", id);
				return null;
			}
			return b;
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 产出配置中的前置方块 id 无效: {}（忽略）", id);
			return null;
		}
	}

	// ==================== 读取 ====================

	private static CivilizationProductionConfig loaded;

	/** 当前生效的配置（未加载时自动加载） */
	public static synchronized CivilizationProductionConfig get() {
		if (loaded == null) reload();
		return loaded;
	}

	/** 重新读取配置文件（每局 setup 时调用——修改后下一局生效） */
	public static synchronized CivilizationProductionConfig reload() {
		try {
			Path f = file();
			if (!Files.exists(f)) {
				Files.createDirectories(f.getParent());
				Files.writeString(f, GSON.toJson(DEFAULTS));
				SurvivalContract.LOGGER.info("[Civilization] 已生成产出配置文件: {}", f);
				loaded = DEFAULTS;
				return loaded;
			}
			CivilizationProductionConfig parsed = GSON.fromJson(Files.readString(f), CivilizationProductionConfig.class);
			if (parsed == null) parsed = new CivilizationProductionConfig();
			// 旧版本配置：默认值已更新（如第四十九批概率调整）→ 备份为 .bak 后重新生成默认文件。
			// 用户若自定义过参数，可从 .bak 里把对应字段抄回新文件。
			// 注意：不能用 Gson 字段的初始化值判断——旧文件缺失 configVersion 键时字段保持初始化值，
			// 必须先解析 JSON 树检测该键是否真实存在。
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
					Files.readString(f)).getAsJsonObject();
			int fileVersion = root.has("configVersion") ? root.get("configVersion").getAsInt() : -1;
			if (fileVersion < CONFIG_VERSION) {
				try {
					java.nio.file.Files.copy(f, f.resolveSibling(
							f.getFileName().toString() + ".bak"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {}
				Files.writeString(f, GSON.toJson(DEFAULTS));
				loaded = DEFAULTS;
				SurvivalContract.LOGGER.warn("[Civilization] 配置版本 {} < {}，已备份旧文件为 .bak 并重新生成默认配置: {}",
						parsed.configVersion, CONFIG_VERSION, f);
				return loaded;
			}
			// 用户文件缺节/显式 null → 用默认结构补齐
			if (parsed.zones == null) parsed.zones = new LinkedHashMap<>();
			if (parsed.wildlife == null) parsed.wildlife = new WildlifeCfg();
			if (parsed.hqSupplies == null) parsed.hqSupplies = new HqSuppliesCfg();
			if (parsed.supplyBarrel == null) parsed.supplyBarrel = new SupplyBarrelCfg();
			if (parsed.zoneLimits == null) parsed.zoneLimits = new ZoneLimitsCfg();
			parsed.resolve(DEFAULTS);
			loaded = parsed;
			SurvivalContract.LOGGER.info("[Civilization] 产出配置已加载: {}", f);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 产出配置读取失败，使用默认值（不覆盖用户文件）", e);
			loaded = DEFAULTS;
		}
		return loaded;
	}

	// ==================== 查询接口（ CivilizationManager 使用） ====================

	/** 资源区产出间隔（tick） */
	public long zoneIntervalTicks(CivilizationManager.ZoneType t) {
		Long v = resInterval.get(t);
		return v == null ? 1200L : v;
	}

	/** 资源区每次产出数量 */
	public int zoneCount(CivilizationManager.ZoneType t) {
		Integer v = resCount.get(t);
		return v == null ? 0 : v;
	}

	/** 资源区主产出物品 */
	public Item zoneOutput(CivilizationManager.ZoneType t) {
		Item v = resOutput.get(t);
		return v == null ? Items.AIR : v;
	}

	/** 资源区概率副产品 */
	public List<RBonus> zoneBonuses(CivilizationManager.ZoneType t) {
		List<RBonus> v = resBonuses.get(t);
		return v == null ? List.of() : v;
	}

	/** 资源区概率刷怪生物 id（"" = 不刷） */
	public String zoneSpawnMob(CivilizationManager.ZoneType t) {
		String v = resSpawnMob.get(t);
		return v == null ? "" : v;
	}

	/** 资源区刷怪概率（百分比） */
	public int zoneSpawnChancePercent(CivilizationManager.ZoneType t) {
		Integer v = resSpawnChance.get(t);
		return v == null ? 0 : v;
	}

	/** 资源区每次刷怪数量 */
	public int zoneSpawnCount(CivilizationManager.ZoneType t) {
		Integer v = resSpawnCount.get(t);
		return v == null ? 1 : v;
	}

	/** 资源区主产物产出概率（百分比，0.0-100.0） */
	public double zoneOutputChancePercent(CivilizationManager.ZoneType t) {
		Double v = resOutputChance.get(t);
		return v == null ? 100.0 : v;
	}

	/** 资源区刷怪间隔（tick；spawnIntervalSeconds=0 时已回退为产出间隔） */
	public long zoneSpawnIntervalTicks(CivilizationManager.ZoneType t) {
		Long v = resSpawnInterval.get(t);
		return v == null ? 1200L : v;
	}

	/** 资源区刷出生物最大生命值（0 = 原版默认） */
	public double zoneSpawnMaxHealth(CivilizationManager.ZoneType t) {
		Double v = resSpawnMaxHealth.get(t);
		return v == null ? 0 : v;
	}

	/** 资源区收获前置方块（空数组 = 无前置；铜箱子按氧化/涂蜡家族匹配由 CivilizationManager 处理） */
	public Block[] zoneRequireBlocks(CivilizationManager.ZoneType t) {
		Block[] v = resRequireBlocks.get(t);
		return v == null ? new Block[0] : v;
	}

	/** 资源区全图数量上限（null = 无限制） */
	public Integer zoneLimit(CivilizationManager.ZoneType t) {
		return resZoneLimits.get(t);
	}

	/** 已解析的资源区上限表（setup 分配用；未配置的类型不在表中 = 无限制） */
	public Map<CivilizationManager.ZoneType, Integer> zoneLimitMap() {
		return resZoneLimits;
	}

	/** 资源区块内本系统生成的生物上限（0 时已回退为默认 12） */
	public int zoneSpawnCap(CivilizationManager.ZoneType t) {
		Integer v = resSpawnCap.get(t);
		return v == null ? 12 : v;
	}

	/** 大本营战斗物资：每组产出的物品池（每组每种物品各 1 个） */
	public List<Item> hqItems() {
		return resHqItems;
	}

	/** 大本营战斗物资：每次结算的产出概率（百分比） */
	public double hqChancePercent() {
		return resHqChance;
	}

	/** 大本营物资桶：稀有物资候选池 */
	public List<Item> supplyBarrelRareItems() {
		return resRareItems;
	}

	/** 野生动物区产出/刷怪间隔（tick） */
	public long wildlifeIntervalTicks() {
		return Math.max(1, wildlife.intervalSeconds) * 20L;
	}
}
