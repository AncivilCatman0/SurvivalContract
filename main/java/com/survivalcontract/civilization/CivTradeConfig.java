package com.survivalcontract.civilization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.survivalcontract.SurvivalContract;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文明时代交易配置：config/survival-contract/civilization-trade.json（第五十一批）
 * <ul>
 *   <li>基地商人（红蓝大本营各一）：羊毛动态价（初始积分 / 每次购买上涨 / 上限）+ 商品货物
 *       （类型 / 数量 / 兑换积分）</li>
 *   <li>猪灵回收价目表：每个资源回收时对应的积分</li>
 *   <li>每局开始（setup）时重新读取——修改配置文件后下一局生效</li>
 *   <li>文件缺失时自动生成默认值；解析失败时使用默认值且不覆盖用户文件</li>
 *   <li>字段说明直接写在 JSON 的「说明」键里，玩家可自行调整</li>
 * </ul>
 */
public class CivTradeConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 交易配置文件路径：config/survival-contract/civilization-trade.json */
	public static Path file() {
		return FabricLoader.getInstance().getConfigDir()
				.resolve("survival-contract").resolve("civilization-trade.json");
	}

	// ==================== JSON 模型 ====================

	/** 配置版本号（默认值更新后旧版本文件自动迁移——备份 .bak 并重新生成） */
	public int configVersion = CONFIG_VERSION;

	/** 顶层说明（随默认文件生成） */
	public String 说明 = "文明时代交易配置：基地商人（红蓝大本营各一）+ 猪灵回收价目。每局开始时读取——修改后下一局生效。"
			+ "configVersion 为配置版本号（请勿修改）——默认值更新后旧版本文件会自动备份为 .bak 并重新生成。"
			+ "物品 id 使用原版注册名，可省略 minecraft: 前缀（如 oak_log）。"
			+ "merchant：羊毛（商品栏第一位，我方颜色羊毛）用积分兑换，初始 woolInitialCost 分，每购买一个上涨 woolCostStep 分，"
			+ "最高 woolMaxCost 分（按队伍独立计价）；goods 为羊毛之后的商品列表（type 支持 随机附魔书(默认不售)/随机武器装备/随机药水/回城卷轴/"
			+ "具体物品 id，count = 每次兑换数量，cost = 兑换积分；留空 = 无额外商品）。"
			+ "recyclePrices：大本营猪灵回收价目（物品 id → 每个兑换的积分，支持小数如 0.5；未列入的物品不回收）。"
			+ "删除本文件后重启会重新生成默认配置。";

	/** 基地商人配置 */
	public MerchantCfg merchant = new MerchantCfg();

	/** 猪灵回收价目表（键 = 物品注册名，可省略 minecraft: 前缀；值 = 每个积分，支持小数） */
	public Map<String, Double> recyclePrices = defaultRecyclePrices();

	/** 基地商人配置 */
	public static class MerchantCfg {
		public String 说明 = "基地商人：红蓝大本营中心区块各一个（不能行动、永久抗性提升 5、不消失），"
				+ "右键打开积分兑换界面。商品栏第一位固定为本队颜色羊毛（兑换后可放置占领）：初始 woolInitialCost 积分，"
				+ "每购买一个上涨 woolCostStep 积分，上限 woolMaxCost 积分（红蓝两队各自计价）。"
				+ "goods 为羊毛之后的商品（每局开始掷定后固定，红蓝两队一致）。";
		/** 羊毛初始兑换价（积分） */
		public int woolInitialCost = 80;
		/** 每购买一个羊毛上涨的积分 */
		public int woolCostStep = 4;
		/** 羊毛兑换价上限（积分；封顶后不再上涨） */
		public int woolMaxCost = 320;
		/** 羊毛之后的商品列表（type：随机附魔书(仅配置支持，默认不售)/随机武器装备/随机药水/具体物品 id；count：每次兑换数量；cost：兑换积分——固定价不涨价） */
		public List<GoodCfg> goods = defaultGoods();
	}

	/** 单条商品配置 */
	public static class GoodCfg {
		public String 说明 = "type：商品类型——随机附魔书 / 随机武器装备 / 随机药水 / 回城卷轴 / 具体物品 id（可省略 minecraft: 前缀）；"
				+ "count：每次兑换得到的数量；cost：兑换所需积分（回城卷轴为右键消耗后立刻回到队伍出生点）";
		/** 商品类型 */
		public String type = "";
		/** 每次兑换数量 */
		public int count = 1;
		/** 兑换所需积分 */
		public int cost = 0;
	}

	// ==================== 默认值 ====================

	/** 配置版本：默认值变更时 +1；旧版本文件会备份为 .bak 后重新生成默认。
	 *  2 = 第六十九批：商人取消附魔书售卖，改售附魔之瓶（30 积分/个，不涨价）
	 *  3 = 第七十八批：新增三号位固定商品 回城卷轴（500 积分/个，右键消耗回到队伍出生点） */
	public static final int CONFIG_VERSION = 3;

	/** 基地商人默认商品（= 第七十八批结构：1 附魔之瓶（30 积分，固定价不涨价）+ 1 回城卷轴（500 积分）
	 *  + 2 随机武器装备 + 1 随机药水；附魔之瓶之后即三号位 回城卷轴） */
	public static List<GoodCfg> defaultGoods() {
		List<GoodCfg> list = new ArrayList<>();
		GoodCfg bottle = new GoodCfg();
		bottle.type = "experience_bottle";
		bottle.count = 1;
		bottle.cost = 30;
		list.add(bottle);
		GoodCfg scroll = new GoodCfg();
		scroll.type = "回城卷轴";
		scroll.count = 1;
		scroll.cost = 500;
		list.add(scroll);
		for (int i = 0; i < 2; i++) {
			GoodCfg g = new GoodCfg();
			g.type = "随机武器装备";
			g.count = 1;
			g.cost = 500;
			list.add(g);
		}
		GoodCfg p = new GoodCfg();
		p.type = "随机药水";
		p.count = 1;
		p.cost = 300;
		list.add(p);
		return list;
	}

	/** 猪灵回收默认价目（= 第五十批及之前硬编码价表） */
	public static Map<String, Double> defaultRecyclePrices() {
		Map<String, Double> m = new LinkedHashMap<>();
		// 原木 ×9（1 分/个）
		for (String log : new String[]{"oak_log", "spruce_log", "birch_log", "jungle_log",
				"acacia_log", "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log"}) {
			m.put(log, 1.0);
		}
		m.put("charcoal", 2.0);
		m.put("potato", 0.5);
		// 石类（1 分/个）
		for (String st : new String[]{"cobblestone", "stone", "cobbled_deepslate",
				"deepslate", "blackstone", "tuff"}) {
			m.put(st, 1.0);
		}
		m.put("coal", 3.0);
		m.put("raw_copper", 1.5);
		m.put("iron_ingot", 3.0);
		m.put("gold_ingot", 3.0);
		m.put("diamond", 10.0);
		m.put("quartz_block", 10.0);
		m.put("lapis_lazuli", 2.0);
		m.put("redstone_block", 10.0);
		m.put("emerald", 2.0);
		m.put("netherite_ingot", 20.0);
		return m;
	}

	// ==================== 解析辅助 ====================

	/** 物品 id → Item（可省略 minecraft: 前缀；无效/未知回退 fallback，fallback 为 null 表示丢弃该项） */
	public static Item item(String id, Item fallback) {
		if (id == null || id.isBlank()) return fallback;
		try {
			String s = id.trim().toLowerCase(Locale.ROOT);
			if (!s.contains(":")) s = "minecraft:" + s;
			Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(s));
			return (it == null || it == Items.AIR) ? fallback : it;
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 交易配置中的物品 id 无效: {}（回退 {}）", id, fallback);
			return fallback;
		}
	}

	// ==================== 读取 ====================

	private static CivTradeConfig loaded;

	/** 当前生效的配置（未加载时自动加载） */
	public static synchronized CivTradeConfig get() {
		if (loaded == null) reload();
		return loaded;
	}

	/** 重新读取配置文件（每局 setup 时调用——修改后下一局生效） */
	public static synchronized CivTradeConfig reload() {
		try {
			Path f = file();
			if (!Files.exists(f)) {
				Files.createDirectories(f.getParent());
				Files.writeString(f, GSON.toJson(DEFAULTS));
				SurvivalContract.LOGGER.info("[Civilization] 已生成交易配置文件: {}", f);
				loaded = DEFAULTS;
				return loaded;
			}
			CivTradeConfig parsed = GSON.fromJson(Files.readString(f), CivTradeConfig.class);
			if (parsed == null) parsed = new CivTradeConfig();
			// 旧版本配置：默认值更新 → 备份为 .bak 后重新生成默认文件（用户自定义参数可从 .bak 抄回）。
			// 不能用 Gson 字段初始化值判断——旧文件缺失 configVersion 键时字段保持初始化值，
			// 必须先解析 JSON 树检测该键是否真实存在。
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
					Files.readString(f)).getAsJsonObject();
			int fileVersion = root.has("configVersion") ? root.get("configVersion").getAsInt() : -1;
			if (fileVersion < CONFIG_VERSION) {
				try {
					Files.copy(f, f.resolveSibling(f.getFileName().toString() + ".bak"),
							java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception ignored) {}
				Files.writeString(f, GSON.toJson(DEFAULTS));
				loaded = DEFAULTS;
				SurvivalContract.LOGGER.warn("[Civilization] 交易配置版本 {} < {}，已备份旧文件为 .bak 并重新生成默认配置: {}",
						parsed.configVersion, CONFIG_VERSION, f);
				return loaded;
			}
			// 用户文件缺节/显式 null → 用默认结构补齐
			if (parsed.merchant == null) parsed.merchant = new MerchantCfg();
			if (parsed.merchant.goods == null) parsed.merchant.goods = new ArrayList<>();
			if (parsed.recyclePrices == null) parsed.recyclePrices = defaultRecyclePrices();
			loaded = parsed;
			SurvivalContract.LOGGER.info("[Civilization] 交易配置已加载: {}", f);
		} catch (Exception e) {
			SurvivalContract.LOGGER.warn("[Civilization] 交易配置读取失败，使用默认值（不覆盖用户文件）", e);
			loaded = DEFAULTS;
		}
		return loaded;
	}

	/** 全量默认配置（也是用户文件缺项时的回退基准） */
	private static final CivTradeConfig DEFAULTS = new CivTradeConfig();
}
