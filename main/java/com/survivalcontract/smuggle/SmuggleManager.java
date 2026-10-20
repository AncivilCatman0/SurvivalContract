package com.survivalcontract.smuggle;

import com.survivalcontract.SurvivalContract;
import com.survivalcontract.game.GameStateManager;
import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 走私犯与检察官（PlayStyle.STOWAWAY）核心逻辑：
 * <ol>
 *   <li><b>收纳袋</b>：开局每人获得两个不同的收纳袋——价值收纳袋（棕色原版收纳袋，存货品）与
 *       贿赂收纳袋（红色原版收纳袋，存贿赂物品）。袋内容物保存在服务端
 *       （本体只是钥匙道具，死亡重发；内嵌的原版 BundleContents 会在开袋/进检查时倒回物品栏防夹带）</li>
 *   <li><b>收集</b>：屏幕显示本组三个公开商品（每件独立随机 8/4/2 档，同批最多 1 个 8 分档），
 *       之外每队还有一个<b>仅我方可见的走私品</b>（固定 128 分，每轮换批刷新）；
 *       价值物品每 10 分钟更换一批</li>
 *   <li><b>检查通行兑换</b>：检查后若是通行（受贿 / 检查无走私品），被检查者货物收纳袋
 *       直接清空并按价值表兑换成分（给自己加分）</li>
 *   <li><b>检查</b>：每人一本「检查发起书」（死亡不掉落，重生返还），右键选一名非友方玩家
 *       作为自己的检察官 → 双方屏幕 3 秒倒计时 → 传送进密闭不可破坏的检查室
 *       （内部 5×7×3：石英墙壁 + 白桦木板底板/天花板 + 顶部蛙明灯 + 两侧杜鹃花树苗盆栽；
 *       长方向两端各一块地毯一红一蓝相隔 3 方块——被检查者站红地毯、检察官站蓝地毯，
 *       进室后双方<b>不能移动</b>（tick 钳制拉回））；
 *       进室时系统提示检察官对方的<b>贿赂袋价值</b>（走私品不能入袋不计价；无总分提示区间），检察官 2 分钟内定夺：
 *       <ul>
 *         <li><b>受贿</b>：清空被检查者贿赂收纳袋，检察官获得对应分数；
 *             被检查者货物袋同步清空兑换成分</li>
 *         <li><b>检查</b>：情况一，对方没有走私品 → 检察官赔偿对方货品 1/4 的分数并放行，
 *             被检查者货物袋同步清空兑换成分；情况二，对方有走私品 → 没收对方所有货物
 *             （背包价值物品 + 价值袋货物消失），检察官获得对应分数，贿赂袋不受影响</li>
 *       </ul>
 *       检察官超时未定夺 → 系统自动视为受贿。检查结束（含超时）双方传送回原位；
 *       被检查者物品栏中的散装价值物品在结算时一并兑换成分（杂物与过轮走私品保留不动，第六十七批）。
 *       检查发起书有 2 分钟冷却，且不能连续两次选择同一位玩家作为自己的检察官。同一时间只能进行一场检查</li>
 *   <li><b>胜负</b>：无最终目标，时间结束分数高者获胜</li>
 * </ol>
 * 组键（group key）：团队模式 "RED"/"BLUE"（队共享），个人模式玩家 UUID 字符串（独立）。
 */
public class SmuggleManager {

	private static final SmuggleManager INSTANCE = new SmuggleManager();

	public static SmuggleManager getInstance() { return INSTANCE; }

	private SmuggleManager() {}

	// ==================== 公开商品表（仅原材料/食物饮品，分值 8/4/2——工具与武器只能作为走私品出现） ====================
	private static final List<Item> POOL_8 = List.of(
			Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT, Items.GOLDEN_APPLE);
	private static final List<Item> POOL_4 = List.of(
			Items.IRON_INGOT, Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.GOLDEN_CARROT);
	private static final List<Item> POOL_2 = List.of(
			Items.COAL, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COPPER_INGOT,
			Items.BREAD, Items.COOKED_BEEF, Items.COOKED_PORKCHOP);

	// ==================== 走私品表（容易生存获得的日常原材料；固定 128 分；工具与武器已移除） ====================
	public static final int SMUGGLE_VALUE = 128;
	private static final List<Item> SMUGGLE_POOL = List.of(
			Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK,
			Items.SAND, Items.GRAVEL, Items.WHEAT, Items.STRING, Items.LEATHER,
			Items.FLINT, Items.BONE, Items.FEATHER, Items.SUGAR_CANE,
			Items.CLAY_BALL, Items.ROTTEN_FLESH);

	/** 贿赂袋静态价值表（仅非走私品；走私品禁入贿赂袋）：沿用公开商品池 8/4/2 档。
	 *  贿赂袋不受批次更换约束（用户未改其规则），所以沿用静态表计价。 */
	private static final Map<Item, Integer> BRIBE_VALUE_TABLE = new HashMap<>();

	static {
		POOL_8.forEach(i -> BRIBE_VALUE_TABLE.put(i, 8));
		POOL_4.forEach(i -> BRIBE_VALUE_TABLE.put(i, 4));
		POOL_2.forEach(i -> BRIBE_VALUE_TABLE.put(i, 2));
	}

	/** 公开商品静态价值（贿赂袋计价与 NetworkRegistry 下发分值参考用；批次感知计价走 itemValueForGroup） */
	public static int publicValueOf(Item item) { return BRIBE_VALUE_TABLE.getOrDefault(item, 0); }

	/** 是否走私品（固定 128 分的仅我方可见物品） */
	public static boolean isSmuggleItem(Item item) { return SMUGGLE_POOL.contains(item); }

	// ==================== 每组收集清单（批次制） ====================
	public static final long BATCH_INTERVAL_TICKS = 12000L; // 10 分钟换一批
	/** 组键 → 当前批次三件公开商品 */
	private final Map<String, Item[]> currentBatch = new HashMap<>();
	/** 组键 → 当前批次三件公开商品的分值（与 currentBatch 同索引，每件独立随机 8/4/2） */
	private final Map<String, int[]> batchScores = new HashMap<>();
	/** 组键 → 本批已通过结算"交易"过走私品的玩家集合（受贿通行换出 / 查获没收均算一次）。
	 *  换批时对应 Set 清空。用户规则 ⑥：每批次每位玩家最多一次走私品交易。 */
	private final Map<String, Set<UUID>> smuggleRedeemedThisBatch = new HashMap<>();
	/** 组键 → 当前批次走私品（仅我方可见，固定 128 分，每轮刷新） */
	private final Map<String, Item> smuggleItems = new HashMap<>();
	private long batchStartTick = 0L;

	private MinecraftServer serverRef = null;

	// ==================== 收纳袋（每玩家） ====================
	/** 玩家 → 价值收纳袋容器（27 格；存放货品，检查通行时清空兑换 / 查获时没收） */
	private final Map<UUID, SimpleContainer> valueBags = new HashMap<>();
	/** 玩家 → 贿赂收纳袋容器（27 格；存放贿赂物品，没收时不受影响） */
	private final Map<UUID, SimpleContainer> bribeBags = new HashMap<>();

	/**
	 * 贿赂收纳袋容器标记类：服务端 mixin 靠它识别打开的是贿赂袋（客户端菜单底层容器是
	 * 原版工厂新建的裸 SimpleContainer，服务端标记类对客户端不可见，靠 clientBribeBagMenu 识别）。
	 */
	public static class BribeBagContainer extends SimpleContainer {
		public BribeBagContainer() { super(27); }
	}

	/**
	 * 价值收纳袋容器标记类（与 BribeBagContainer 对称，第六十七批）：服务端 mixin 靠它识别
	 * 打开的是价值袋（客户端菜单底层容器是原版工厂新建的裸 SimpleContainer，
	 * 服务端标记类对客户端不可见，靠 clientValueBagMenu 识别）。
	 */
	public static class ValueBagContainer extends SimpleContainer {
		public ValueBagContainer() { super(27); }
	}

	/** 客户端当前打开的贿赂袋菜单（客户端收到 BagOpenPayload 后记录；引用相等比对，换开其他界面自然失效） */
	public static volatile AbstractContainerMenu clientBribeBagMenu = null;

	/** 客户端当前打开的价值袋菜单（与 clientBribeBagMenu 对称，第六十七批） */
	public static volatile AbstractContainerMenu clientValueBagMenu = null;


	// ==================== 检查 ====================
	public static final long PROSECUTE_COUNTDOWN_TICKS = 60L; // 进室前 3 秒倒计时
	public static final long PROSECUTE_WINDOW_TICKS = 2400L;  // 检查时间 2 分钟（进室后检察官定夺时限）
	/** 检查发起冷却：2 分钟（同一玩家两次发起检查的最小间隔） */
	public static final long PROSECUTE_COOLDOWN_TICKS = 2400L;
	/** 检查决策动作：受贿 */
	public static final int ACTION_ACCEPT_BRIBE = 0;
	/** 检查决策动作：进行检查 */
	public static final int ACTION_INSPECT = 1;
	/**
	 * 检查阶段：发起者（被检查者，持书人） → 待决检查记录。
	 * 发起后 3 秒倒计时传送进检查室；进室后双方锁地毯站位（不能移动），
	 * 检察官 2 分钟内定夺（受贿 / 检查），超时自动视为受贿。
	 */
	private final Map<UUID, PendingProsecution> pending = new HashMap<>();
	/** 已进室的检查（进室后检察官的发起书才显示决策界面） */
	private final Set<UUID> roomEntered = new HashSet<>();
	/** 玩家 → 上次发起检查的 tick（2 分钟冷却判定） */
	private final Map<UUID, Long> lastInitiateTick = new HashMap<>();
	/** 发起者 → 上一次选定的检察官（禁止连续两次选同一位玩家作为自己的检察官） */
	private final Map<UUID, UUID> lastProsecutorChosen = new HashMap<>();

	/** 进行中的检查（public 供自测直接构造/传参）。initiator=持书被检查者；prosecutor=其选定的检察官 */
	public record PendingProsecution(UUID initiator, UUID prosecutor,
									 BlockPos fromInitiator, BlockPos fromProsecutor,
									 long teleportTick, long deadlineTick) {}

	// 检查室（密闭不可破坏）
	private BlockPos cabinMin = null, cabinMax = null;
	private BlockPos cabinSpotA = null, cabinSpotB = null;
	/** 受保护方块快照（检查室全部方块含地毯/盆栽/蛙明灯）：每 2 秒自愈（玩家破坏已拦截，爆炸等环境伤害由此兜底） */
	private final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> protectedBlocks = new HashMap<>();

	// ==================== 倒计时大字（双方屏幕 3/2/1） ====================
	private record ScheduledTitle(UUID player, Component title, long sendAtTick) {}
	private final List<ScheduledTitle> scheduledTitles = new ArrayList<>();

	// ==================== 组键 ====================
	public String groupKeyOf(ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.isTeamMode()) {
			GameStateManager.Team team = gsm.getPlayerTeam(player.getUUID());
			return team == GameStateManager.Team.BLUE ? "BLUE" : "RED";
		}
		return player.getUUID().toString();
	}

	// ==================== 开局初始化（LobbyManager 调用） ====================
	public void setup(MinecraftServer server) {
		reset();
		serverRef = server;
		GameStateManager gsm = GameStateManager.getInstance();
		ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (level == null) level = server.overworld();
		batchStartTick = server.getTickCount();

		// 收集组键：团队模式每队一组，个人模式每人一组
		java.util.List<String> groupKeys = new java.util.ArrayList<>();
		if (gsm.isTeamMode()) {
			for (GameStateManager.Team team : new GameStateManager.Team[]{
					GameStateManager.Team.RED, GameStateManager.Team.BLUE}) {
				if (!gsm.getTeamMembers(team).isEmpty()) {
					groupKeys.add(team == GameStateManager.Team.BLUE ? "BLUE" : "RED");
				}
			}
		} else {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (gsm.isLobbySpectator(p.getUUID())) continue;
				groupKeys.add(p.getUUID().toString());
			}
		}

		// 首批价值物品清单 + 走私品
		for (String key : groupKeys) refreshBatch(key);

		buildCabin(level);
		broadcastSync(server);
		SurvivalContract.LOGGER.info("[Smuggle] Setup: {} groups, cabin at {}", groupKeys.size(),
				cabinMin == null ? "n/a" : cabinMin.toShortString());
	}

	/**
	 * 每人开局套装（LobbyManager 每玩家处理段调用）：初始化双收纳袋容器 +
	 * 发放两个收纳袋钥匙道具（价值收纳袋/贿赂收纳袋）。右键收纳袋打开对应袋界面。
	 */
	public void initPlayerKit(ServerPlayer player) {
		valueBags.put(player.getUUID(), new ValueBagContainer());
		bribeBags.put(player.getUUID(), new BribeBagContainer());
		giveBagItem(player, Items.BUNDLE, "§6价值收纳袋");
		giveBagItem(player, redBundleItem(), "§c贿赂收纳袋");
	}

	/** 重生/死亡后收纳袋钥匙缺失则补发（袋内容物在服务端，不随死亡丢失） */
	public void grantKitIfMissing(ServerPlayer player) {
		if (!GameStateManager.getInstance().isStowaway()) return;
		valueBags.computeIfAbsent(player.getUUID(), k -> new ValueBagContainer());
		bribeBags.computeIfAbsent(player.getUUID(), k -> new BribeBagContainer());
		if (!hasNamedItem(player, "价值收纳袋")) giveBagItem(player, Items.BUNDLE, "§6价值收纳袋");
		if (!hasNamedItem(player, "贿赂收纳袋")) giveBagItem(player, redBundleItem(), "§c贿赂收纳袋");
	}

	/** 26.2 无 Items.RED_BUNDLE 常量（DYED_BUNDLE 是染色集合）→ 注册表直查红色收纳袋（贿赂袋形态，红包寓意） */
	private static Item redBundleItem() {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "red_bundle"));
	}

	/** 是否原版收纳袋道具（棕色 BUNDLE / 红色 BUNDLE，其余颜色不属本玩法） */
	private static boolean isBundleItem(Item item) {
		return item == Items.BUNDLE || item == redBundleItem();
	}

	/** 是否收纳袋钥匙道具（价值袋/贿赂袋本体；空堆返回 false。服务端 Use 回调兜底拦截用，第六十七批） */
	public static boolean isBagKeyItem(ItemStack stack) {
		return !stack.isEmpty() && isBundleItem(stack.getItem());
	}

	private boolean hasNamedItem(ServerPlayer player, String namePart) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			var s = inv.getItem(i);
			if (s.isEmpty()) continue;
			var cn = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
			if (cn != null && cn.getString().contains(namePart)) return true;
		}
		return false;
	}

	private void giveBagItem(ServerPlayer player, Item item, String name) {
		var stack = new ItemStack(item);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
		if (!player.getInventory().add(stack)) com.survivalcontract.McCompat.drop(player, stack, false);
	}

	/**
	 * 换一批：三件公开商品各自独立随机 8/4/2 档——允许出现多个 4 或多个 2（如 8/4/4、2/2/2），
	 * 但同批最多 1 个 8 分档（第二个 8 分档降级为 4/2 档随机）；同档位不重复取同一种物品。
	 * 走私品：从走私品池随机抽取（固定 128 分，仅我方可见）。
	 * 换批后，上一批的公开商品/走私品在价值袋里全变成 0 分（itemValueForGroup 只认当前批次）。
	 */
	private void refreshBatch(String key) {
		Random rnd = new Random();
		List<List<Item>> pools = List.of(POOL_8, POOL_4, POOL_2);
		int[] scoreBuckets = new int[]{8, 4, 2};
		Set<Item> used = new HashSet<>();
		Item[] batch = new Item[3];
		int[] scores = new int[3];
		boolean hasEight = false;
		for (int i = 0; i < 3; i++) {
			int bucketIdx = rnd.nextInt(pools.size());
			if (bucketIdx == 0 && hasEight) {
				// 每批最多 1 个 8 分物品：第二个 8 分档降级为 4/2 档随机
				bucketIdx = rnd.nextBoolean() ? 1 : 2;
			}
			List<Item> pool = pools.get(bucketIdx);
			int score = scoreBuckets[bucketIdx];
			Item pick;
			do {
				pick = pool.get(rnd.nextInt(pool.size()));
			} while (used.contains(pick));
			used.add(pick);
			if (bucketIdx == 0) hasEight = true;
			batch[i] = pick;
			scores[i] = score;
		}
		currentBatch.put(key, batch);
		batchScores.put(key, scores);
		// 换批：清空本批走私品已交易记录（用户规则 ⑥ 每批重置）
		smuggleRedeemedThisBatch.put(key, new HashSet<>());
		// 走私品：避开对方组当前走私品（两队违禁品强制不同）
		String otherKey = key.equals("RED") ? "BLUE" : key.equals("BLUE") ? "RED" : null;
		Item taken = otherKey == null ? null : smuggleItems.get(otherKey);
		Item smuggle = SMUGGLE_POOL.get(rnd.nextInt(SMUGGLE_POOL.size()));
		if (taken != null) {
			for (int attempt = 0; attempt < 20 && smuggle == taken; attempt++) {
				smuggle = SMUGGLE_POOL.get(rnd.nextInt(SMUGGLE_POOL.size()));
			}
			if (smuggle == taken) {
				for (Item it : SMUGGLE_POOL) {
					if (it != taken) { smuggle = it; break; }
				}
			}
		}
		smuggleItems.put(key, smuggle);
	}

	// ==================== 每 tick（MainEventListener 主 tick 链调用） ====================
	public void tick(MinecraftServer server) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (!gsm.isStowaway() || gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) return;
		serverRef = server;
		long now = server.getTickCount();

		// 批次更换
		if (now - batchStartTick >= BATCH_INTERVAL_TICKS) {
			batchStartTick = now;
			for (String key : currentBatch.keySet()) refreshBatch(key);
			broadcastSync(server);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				p.sendSystemMessage(Component.translatable("survival-contract.msg.smuggle_batch_changed"));
			}
		}

		// 倒计时大字（3/2/1）
		if (!scheduledTitles.isEmpty()) {
			Iterator<ScheduledTitle> tit = scheduledTitles.iterator();
			while (tit.hasNext()) {
				ScheduledTitle st = tit.next();
				if (now < st.sendAtTick()) continue;
				tit.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(st.player());
				if (p == null) continue;
				p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 18, 5));
				p.connection.send(new ClientboundSetTitleTextPacket(st.title()));
			}
		}

		// 检查流程：3 秒倒计时结束进室；进室后 2 分钟未定夺自动视为受贿
		Iterator<Map.Entry<UUID, PendingProsecution>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			PendingProsecution pp = e.getValue();
			if (!roomEntered.contains(e.getKey()) && now >= pp.teleportTick()) {
				enterInspectionRoom(server, pp);
				continue;
			}
			if (roomEntered.contains(e.getKey()) && now >= pp.deadlineTick()) {
				it.remove();
				roomEntered.remove(e.getKey());
				timeoutAutoAcceptBribe(server, pp);
			}
		}

		// 检查室内双方不能移动：每 tick 钳制回各自地毯站位（保留视角，偏离即拉回）
		for (var e : pending.entrySet()) {
			if (!roomEntered.contains(e.getKey())) continue;
			PendingProsecution pp = e.getValue();
			clampToSpot(server.getPlayerList().getPlayer(e.getKey()), cabinSpotA);
			clampToSpot(server.getPlayerList().getPlayer(pp.prosecutor()), cabinSpotB);
		}

		// 受保护方块自愈（每 2 秒）
		if (now % 40 == 0) protectSelfHeal(server);
	}

	// ==================== 收纳袋界面 ====================
	/** 打开自己的收纳袋（bribe=true 贿赂收纳袋 / false 价值收纳袋）。C2S OpenBagPayload 触发 */
	public void openBag(ServerPlayer player, boolean bribe) {
		spillVanillaBundleContents(player);
		SimpleContainer bag = bribe
				? bribeBags.computeIfAbsent(player.getUUID(), k -> new BribeBagContainer())
				: valueBags.computeIfAbsent(player.getUUID(), k -> new ValueBagContainer());
		Component title = bribe
				? Component.translatable("survival-contract.msg.smuggle_bag_bribe_title")
				: Component.translatable("survival-contract.msg.smuggle_bag_value_title");
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, bag, 3), title));
		// 紧随界面包补发袋旗标包（同连接 FIFO，客户端先建好菜单再收到本包）：
		// 客户端记录/清除贿赂袋菜单引用——客户端菜单底层容器是原版新建的裸 SimpleContainer，
		// 无法像服务端那样按标记类识别，禁放走私品的客户端拦截靠该引用（第六十六批）
		ServerPlayNetworking.send(player, new NetworkRegistry.BagOpenPayload(bribe));
	}

	/**
	 * 防原版收纳袋夹带（第六十七批 + 本次修复）：把物品栏中收纳袋道具内嵌的原版内容
	 * （BundleContents 组件，物品栏左键点选塞入）转入该钥匙对应的<b>服务端虚拟袋容器</b>——
	 * 价值袋→valueBags / 贿赂袋→bribeBags，并清空内嵌存储。
	 *
	 * <p>早期实现把内嵌内容倒回物品栏 → 左键塞进收纳袋的货品只算物品栏散装（不计入袋、不换分），
	 * 表现为"只有右键开袋放进的才有分数"。现在按袋归属倒入对应袋容器，左键与右键一致计分。</p>
	 *
	 * <p>入袋约束与界面拦截保持一致：收纳袋钥匙（价值/贿赂袋本体）不得入袋；贿赂袋禁放走私品。
	 * 被拒或袋满放不下的货品退回物品栏（放不下掉脚下），避免凭空消失。</p>
	 * openBag 与进室检查时触发。
	 */
	private void spillVanillaBundleContents(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !isBundleItem(s.getItem())) continue;
			var bc = s.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
			if (bc == null || bc.isEmpty()) continue;
			boolean bribe = isBribeBagItem(s);
			SimpleContainer target = bribe
					? bribeBags.computeIfAbsent(player.getUUID(), k -> new BribeBagContainer())
					: valueBags.computeIfAbsent(player.getUUID(), k -> new ValueBagContainer());
			com.survivalcontract.McCompat.itemCopies(bc).forEach(inner -> {
				if (inner.isEmpty()) return;
				ItemStack stack = inner;
				// 收纳袋钥匙不得入袋；贿赂袋禁放走私品
				boolean reject = isBundleItem(stack.getItem()) || (bribe && isSmuggleItem(stack.getItem()));
				if (!reject) {
					ItemStack leftover = target.addItem(stack);
					if (leftover.isEmpty()) return;
					stack = leftover; // 袋满 → 余量退回物品栏
				}
				if (!inv.add(stack)) com.survivalcontract.McCompat.drop(player, stack, false);
			});
			s.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,
					net.minecraft.world.item.component.BundleContents.EMPTY);
		}
	}

	/** 该收纳袋道具是否为贿赂袋钥匙（按自定义名判定，红色收纳袋兜底） */
	private static boolean isBribeBagItem(ItemStack s) {
		var cn = s.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		if (cn != null && cn.getString().contains("贿赂")) return true;
		return s.getItem() == redBundleItem();
	}

	/** 贿赂袋价值（受贿结算与检察官提示用）：走私品不入袋（禁放），历史遗留走私品也不计价 */
	public int bribeBagTotal(UUID playerId) {
		return bribeBagValue(bribeBags.get(playerId));
	}

	/** 贿赂袋计价：跳过走私品；价值用静态公开商品表（贿赂袋不受批次约束） */
	private int bribeBagValue(SimpleContainer bag) {
		if (bag == null) return 0;
		int total = 0;
		for (int i = 0; i < bag.getContainerSize(); i++) {
			ItemStack s = bag.getItem(i);
			if (!s.isEmpty() && !isSmuggleItem(s.getItem())) total += publicValueOf(s.getItem()) * s.getCount();
		}
		return total;
	}

	/**
	 * 贿赂袋禁放走私品（点击无反馈；双端同规则，mixin 在 doClick HEAD 调用）。
	 * 贿赂袋兼容两边的价值物品（全局价值表），但走私品不能入袋：
	 * <ul>
	 *   <li>袋区（前 27 格）放置/拖拽：手持走私品 → 取消（拿取/shift 取出始终放行）</li>
	 *   <li>袋区数字键/F 换位：目标快捷栏/副手格为走私品 → 取消（交换会把走私品换进袋）</li>
	 *   <li>背包区 shift 快速移动：该格为走私品 → 取消（shift 是存入动作）</li>
	 * </ul>
	 * 识别：服务端看槽位底层容器是否 BribeBagContainer；客户端看 clientBribeBagMenu 引用比对。
	 * 返回 true = 点击被取消。
	 */
	public static boolean blockBribeDeposit(AbstractContainerMenu menu, Player player,
											int slotIndex, int button, ContainerInput input) {
		if (!(menu instanceof ChestMenu cm) || cm.getRowCount() != 3) return false;
		net.minecraft.world.inventory.Slot firstSlot = null;
		try {
			firstSlot = cm.getSlot(0);
		} catch (Exception e) {
			return false;
		}
		if (firstSlot == null) return false;
		if (!(firstSlot.container instanceof BribeBagContainer || menu == clientBribeBagMenu)) return false;
		if (player == null) return false;

		if (input == ContainerInput.QUICK_CRAFT) {
			// 拖拽放置：手持走私品时整个拖拽动作一律取消（begin/add/end 各阶段都无反馈）
			ItemStack carried = menu.getCarried();
			return !carried.isEmpty() && isSmuggleItem(carried.getItem());
		}
		if (slotIndex < 0) return false;
		if (slotIndex < 27) {
			// 袋区
			if (input == ContainerInput.PICKUP) {
				ItemStack carried = menu.getCarried();
				return !carried.isEmpty() && isSmuggleItem(carried.getItem());
			}
			if (input == ContainerInput.SWAP) {
				// 数字键（0-8 快捷栏）/ F（40 副手）换位：目标格为走私品则拦截
				if (net.minecraft.world.entity.player.Inventory.isHotbarSlot(button)
						|| button == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) {
					ItemStack target = player.getInventory().getItem(button);
					return !target.isEmpty() && isSmuggleItem(target.getItem());
				}
				return false;
			}
			// QUICK_MOVE（袋区 shift=取出）/ THROW / PICKUP_ALL / CLONE 放行
			return false;
		}
		// 背包区（27+）：仅 shift 快速移动是存入动作
		if (input == ContainerInput.QUICK_MOVE) {
			try {
				ItemStack s = cm.getSlot(slotIndex).getItem();
				return !s.isEmpty() && isSmuggleItem(s.getItem());
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 收纳袋互存禁放（点击无反馈；双端同规则，mixin 在 doClick HEAD 调用，第六十七批）：
	 * 任何收纳袋道具（价值袋/贿赂袋钥匙本体）都不能放进任一收纳袋界面——
	 * 钥匙存进服务端虚拟袋会成孤儿道具（后续袋清空时连带消失，玩法道具凭空丢失）。
	 * <ul>
	 *   <li>袋区（前 27 格）放置/拖拽：手持收纳袋道具 → 取消</li>
	 *   <li>袋区数字键/F 换位：目标快捷栏/副手格为收纳袋道具 → 取消（交换会把钥匙换进袋）</li>
	 *   <li>背包区 shift 快速移动：该格为收纳袋道具 → 取消（shift 是存入动作）</li>
	 *   <li>拿取/袋区 shift 取出/Q 丢弃始终放行</li>
	 * </ul>
	 * 识别：服务端看槽位底层容器是否 BribeBagContainer/ValueBagContainer；
	 * 客户端看 clientBribeBagMenu/clientValueBagMenu 引用比对。返回 true = 点击被取消。
	 */
	public static boolean blockBagKeyDeposit(AbstractContainerMenu menu, Player player,
											 int slotIndex, int button, ContainerInput input) {
		if (!(menu instanceof ChestMenu cm) || cm.getRowCount() != 3) return false;
		net.minecraft.world.inventory.Slot firstSlot = null;
		try {
			firstSlot = cm.getSlot(0);
		} catch (Exception e) {
			return false;
		}
		if (firstSlot == null) return false;
		if (!(firstSlot.container instanceof BribeBagContainer
				|| firstSlot.container instanceof ValueBagContainer
				|| menu == clientBribeBagMenu || menu == clientValueBagMenu)) return false;
		if (player == null) return false;

		if (input == ContainerInput.QUICK_CRAFT) {
			// 拖拽放置：手持收纳袋道具时整个拖拽动作一律取消（begin/add/end 各阶段都无反馈）
			ItemStack carried = menu.getCarried();
			return !carried.isEmpty() && isBundleItem(carried.getItem());
		}
		if (slotIndex < 0) return false;
		if (slotIndex < 27) {
			// 袋区
			if (input == ContainerInput.PICKUP) {
				ItemStack carried = menu.getCarried();
				return !carried.isEmpty() && isBundleItem(carried.getItem());
			}
			if (input == ContainerInput.SWAP) {
				// 数字键（0-8 快捷栏）/ F（40 副手）换位：目标格为收纳袋道具则拦截
				if (net.minecraft.world.entity.player.Inventory.isHotbarSlot(button)
						|| button == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) {
					ItemStack target = player.getInventory().getItem(button);
					return !target.isEmpty() && isBundleItem(target.getItem());
				}
				return false;
			}
			// QUICK_MOVE（袋区 shift=取出）/ THROW / PICKUP_ALL / CLONE 放行
			return false;
		}
		// 背包区（27+）：仅 shift 快速移动是存入动作
		if (input == ContainerInput.QUICK_MOVE) {
			try {
				ItemStack s = cm.getSlot(slotIndex).getItem();
				return !s.isEmpty() && isBundleItem(s.getItem());
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 批次感知计价（价值袋专用）：
	 * 该组当前批次的 3 件公开商品 → 各自独立分值（8/4/2，与 batchScores 同步）；
	 * 当前批次的走私品 → 固定 128；其余物品（上批货物、过轮走私品、非价值物品）→ 0。
	 * 批次更换后，上一批的公开商品/走私品在价值袋里立即失效（0 分）。
	 */
	private int itemValueForGroup(String groupKey, Item item) {
		// 走私品
		if (isSmuggleItem(item)) {
			return item == smuggleItems.get(groupKey) ? SMUGGLE_VALUE : 0;
		}
		// 公开商品：只认当前批次，按独立分值计价
		Item[] batch = currentBatch.get(groupKey);
		int[] scores = batchScores.get(groupKey);
		if (batch == null || scores == null) return 0;
		for (int i = 0; i < 3; i++) {
			if (batch[i] == item) return scores[i];
		}
		return 0;
	}

	/** 该玩家价值袋内的货品总价（批次感知；物品栏价值物品不计入，用户规则 ①） */
	public int goodsTotal(ServerPlayer player) {
		return bagTotal(valueBags.get(player.getUUID()), groupKeyOf(player));
	}

	/** 组键 + 容器 → 批次感知总价 */
	private int bagTotal(SimpleContainer bag, String groupKey) {
		if (bag == null) return 0;
		int total = 0;
		for (int i = 0; i < bag.getContainerSize(); i++) {
			ItemStack s = bag.getItem(i);
			if (!s.isEmpty()) total += itemValueForGroup(groupKey, s.getItem()) * s.getCount();
		}
		return total;
	}

	/**
	 * 该玩家价值袋里是否有当前批次走私品（批次感知；物品栏走私品不计入，用户规则 ①）。
	 * 构成"有走私品"的唯一证据——物品栏里塞 100 个走私品也不算走私，必须进价值收纳袋才暴露。
	 */
	public boolean hasSmuggleGood(ServerPlayer player) {
		String gk = groupKeyOf(player);
		Item cur = smuggleItems.get(gk);
		if (cur == null) return false;
		SimpleContainer vb = valueBags.get(player.getUUID());
		if (vb != null) {
			for (int i = 0; i < vb.getContainerSize(); i++) {
				if (!vb.getItem(i).isEmpty() && vb.getItem(i).getItem() == cur) return true;
			}
		}
		return false;
	}

	// ==================== 检查 ====================

	// ==================== 测试辅助访问器（自测直接驱动底层逻辑，不经过 GUI/网络） ====================
	public Item[] batchFor(String key) { return currentBatch.get(key); }

	/** 自测用：直接设置某组当前批次（绕过随机生成，让检查判定测试可确定物品）。
	 *  同步设置批次分值（默认用公开商品静态表 8/4/2——若物品不在静态表中则为 1）。
	 *  若需要自定义分值，直接调 setBatchScoresForTest(key, scores)。 */
	public void setBatchForTest(String key, Item[] batch) {
		currentBatch.put(key, batch);
		int[] scores = new int[3];
		for (int i = 0; i < 3; i++) scores[i] = publicValueOf(batch[i]) > 0 ? publicValueOf(batch[i]) : 1;
		batchScores.put(key, scores);
		smuggleRedeemedThisBatch.put(key, new HashSet<>());
	}

	/** 自测用：自定义批次分值（与 setBatchForTest 搭配） */
	public void setBatchScoresForTest(String key, int[] scores) { batchScores.put(key, scores); }

	public Item smuggleItemFor(String key) { return smuggleItems.get(key); }

	/** 自测用：固定走私品（绕过随机） */
	public void setSmuggleItemForTest(String key, Item item) { smuggleItems.put(key, item); }

	/** 自测用：重掷某组批次（走私品与对方组强制不同，第六十七批） */
	public void refreshBatchForTest(String key) { refreshBatch(key); }

	/** 仅自测：读取某组当前走私品（第六十七批双队去重断言用） */
	public Item smuggleItemForTest(String key) { return smuggleItems.get(key); }

	/** 仅自测：直接结算价值袋兑换并返回得分（批次感知） */
	public int redeemValueBagForTest(ServerPlayer target) { return redeemValueBag(target); }

	/** 仅自测：清除发起书 2 分钟冷却计时（保留"上次检察官"记忆），供既有流程测试跳过冷却 */
	public void clearProsecuteCooldownForTest() { lastInitiateTick.clear(); }

	/** 仅自测：清除发起限制（冷却计时 + 上次检察官记忆） */
	public void clearProsecuteLimitsForTest() { lastInitiateTick.clear(); lastProsecutorChosen.clear(); }

	public SimpleContainer valueBagFor(UUID playerId) {
		return valueBags.computeIfAbsent(playerId, k -> new ValueBagContainer());
	}

	public SimpleContainer bribeBagFor(UUID playerId) {
		return bribeBags.computeIfAbsent(playerId, k -> new BribeBagContainer());
	}

	public boolean hasCabin() { return cabinSpotA != null && cabinSpotB != null; }

	/** 自测用：检查室关键坐标（外壳 min/max + 地毯站位 A/B） */
	public BlockPos cabinMinForTest() { return cabinMin; }

	public BlockPos cabinMaxForTest() { return cabinMax; }

	public BlockPos cabinSpotAForTest() { return cabinSpotA; }

	public BlockPos cabinSpotBForTest() { return cabinSpotB; }

	/** 清除某玩家的待决检查记录（自测手动执行判定后防止 tick 二次执行） */
	public void dropPending(UUID initiator) {
		pending.remove(initiator);
		roomEntered.remove(initiator);
	}

	/**
	 * 发起检查（持书人=被检查者）：校验互斥 → 双方屏幕 3 秒倒计时 → 传送进检查室 →
	 * 系统提示检察官对方贿赂袋价值 → 检察官 2 分钟内右键检查发起书定夺（受贿 / 检查）。
	 * 每次发起后该玩家的发起书进入 2 分钟冷却，且不能连续两次选择同一位玩家作为自己的检察官。
	 * 返回 false 表示校验失败（已发提示）。
	 */
	public boolean initiate(ServerPlayer initiator, ServerPlayer prosecutor) {
		GameStateManager gsm = GameStateManager.getInstance();
		MinecraftServer server = initiator.level().getServer();
		long now = server.getTickCount();
		// 冷却：同一玩家 2 分钟内不得再次发起检查
		Long lastTick = lastInitiateTick.get(initiator.getUUID());
		if (lastTick != null && now - lastTick < PROSECUTE_COOLDOWN_TICKS) {
			long remainSec = (PROSECUTE_COOLDOWN_TICKS - (now - lastTick) + 19) / 20;
			initiator.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_cooldown", remainSec));
			return false;
		}
		// 不能连续两次选择同一位玩家作为自己的检察官
		UUID lastProsecutor = lastProsecutorChosen.get(initiator.getUUID());
		if (lastProsecutor != null && lastProsecutor.equals(prosecutor.getUUID())) {
			initiator.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_same_prosecutor"));
			return false;
		}
		// 检查互斥：已有两人待检查（检查室使用中）→ 其余人不能发起，尤其不能对检查室内的两人发起
		if (!pending.isEmpty()) {
			initiator.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_room_busy"));
			return false;
		}
		if (cabinSpotA == null || cabinSpotB == null) {
			initiator.sendSystemMessage(Component.translatable("survival-contract.msg.smuggle_room_not_ready"));
			return false;
		}
		long teleportTick = now + PROSECUTE_COUNTDOWN_TICKS;
		pending.put(initiator.getUUID(), new PendingProsecution(
				initiator.getUUID(), prosecutor.getUUID(),
				initiator.blockPosition(), prosecutor.blockPosition(),
				teleportTick, teleportTick + PROSECUTE_WINDOW_TICKS));
		// 记录冷却起点与本次检察官（供下次发起校验）
		lastInitiateTick.put(initiator.getUUID(), now);
		lastProsecutorChosen.put(initiator.getUUID(), prosecutor.getUUID());
		// 双方屏幕 3 秒倒计时（3/2/1 大字），倒计时结束后进入检查室
		scheduledTitles.add(new ScheduledTitle(initiator.getUUID(), Component.literal("§b§l3"), now + 1));
		scheduledTitles.add(new ScheduledTitle(prosecutor.getUUID(), Component.literal("§b§l3"), now + 1));
		scheduledTitles.add(new ScheduledTitle(initiator.getUUID(), Component.literal("§b§l2"), now + 20));
		scheduledTitles.add(new ScheduledTitle(prosecutor.getUUID(), Component.literal("§b§l2"), now + 20));
		scheduledTitles.add(new ScheduledTitle(initiator.getUUID(), Component.literal("§b§l1"), now + 40));
		scheduledTitles.add(new ScheduledTitle(prosecutor.getUUID(), Component.literal("§b§l1"), now + 40));
		initiator.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_initiated", prosecutor.getName().getString()));
		prosecutor.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_selected_prosecutor", initiator.getName().getString()));
		return true;
	}

	/** 该玩家是否处于检查阶段（已发起、尚未结算） */
	public boolean isAwaitingDecision(UUID initiator) { return pending.containsKey(initiator); }

	/** 该玩家是否是某场进行中检查的检察官 */
	public boolean isProsecutorOfPending(UUID prosecutor) {
		for (var pp : pending.values()) {
			if (pp.prosecutor().equals(prosecutor)) return true;
		}
		return false;
	}

	/** 该玩家作为检察官对应的检查记录（未担任返回 null） */
	public PendingProsecution prosecutorPending(UUID prosecutor) {
		for (var pp : pending.values()) {
			if (pp.prosecutor().equals(prosecutor)) return pp;
		}
		return null;
	}

	/** 该玩家是否已进室（进室后其检察官发起书才显示决策界面） */
	public boolean isRoomEntered(UUID initiator) { return roomEntered.contains(initiator); }

	/** 检查阶段的完整记录（含双方原位，结算回传用；未发起返回 null） */
	public PendingProsecution awaitingRecord(UUID initiator) { return pending.get(initiator); }

	/**
	 * 进室：双方传送进检查室各就地毯站位（被检查者=红地毯 / 检察官=蓝地毯，进室后不能移动）
	 * + 系统提示检察官对方贿赂袋价值（public：tick 自动调用，自测也可直接驱动进室）。
	 */
	public void enterInspectionRoom(MinecraftServer server, PendingProsecution pp) {
		roomEntered.add(pp.initiator());
		ServerPlayer initiator = server.getPlayerList().getPlayer(pp.initiator());
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(pp.prosecutor());
		teleport(initiator, cabinSpotA);
		teleport(prosecutor, cabinSpotB);
		if (initiator != null) spillVanillaBundleContents(initiator);
		if (prosecutor != null) spillVanillaBundleContents(prosecutor);
		if (prosecutor != null && initiator != null) {
			int bribe = bribeBagTotal(pp.initiator());
			prosecutor.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_bribe_value_report",
					initiator.getName().getString(), bribe));
			initiator.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_entered_room"));
		}
	}

	/**
	 * 检察官超时未定夺 → 系统自动视为受贿（第六十三批）。
	 * tick 到期路径：pending/roomEntered 已先行移除后调用（自测也可传 awaitingRecord 直接驱动）。
	 * 双方不在线或阶段已变 → 仅传送回原位不结算。
	 */
	public void timeoutAutoAcceptBribe(MinecraftServer server, PendingProsecution pp) {
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(pp.prosecutor());
		ServerPlayer initiator = server.getPlayerList().getPlayer(pp.initiator());
		GameStateManager gsm = GameStateManager.getInstance();
		if (prosecutor == null || initiator == null
				|| gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isStowaway()) {
			teleportBack(server, pp.initiator(), pp.fromInitiator());
			teleportBack(server, pp.prosecutor(), pp.fromProsecutor());
			return;
		}
		Component timeoutMsg = Component.translatable("survival-contract.msg.smuggle_timeout");
		initiator.sendSystemMessage(timeoutMsg);
		prosecutor.sendSystemMessage(timeoutMsg);
		String verdict = settleAcceptBribe(server, pp);
		finishInspection(server, pp, verdict);
	}

	/**
	 * 受贿结算（用户规则 ②）：
	 *   贿赂收纳袋 → 清空，检察官获得对应分数；
	 *   价值收纳袋 → 清空，被检查者兑换成分（批次感知；走私品若本批未交易过则计 128）。
	 */
	private String settleAcceptBribe(MinecraftServer server, PendingProsecution pp) {
		GameStateManager gsm = GameStateManager.getInstance();
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(pp.prosecutor());
		ServerPlayer initiator = server.getPlayerList().getPlayer(pp.initiator());
		SimpleContainer bribeBag = bribeBags.get(pp.initiator());
		int bribe = bribeBagValue(bribeBag);
		if (bribeBag != null) bribeBag.clearContent();
		gsm.addScore(pp.prosecutor(), bribe);
		int bagGain = initiator == null ? 0 : redeemValueBag(initiator);
		if (prosecutor != null) prosecutor.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_bribe_taken",
				initiator == null
						? Component.translatable("survival-contract.msg.smuggle_other")
						: initiator.getName().getString(), bribe));
		if (initiator != null) initiator.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_your_bribe_taken", bribe, bagGain > 0
						? Component.translatable("survival-contract.msg.smuggle_bag_cleared", bagGain)
						: Component.translatable("survival-contract.msg.smuggle_bag_empty")));
		return "survival-contract.msg.smuggle_verdict_bribe";
	}

	/**
	 * 检查收尾：全员广播 + 双方传送回原位。
	 * 物品栏散装价值物品不受任何影响（用户规则 ①）。
	 */
	private void finishInspection(MinecraftServer server, PendingProsecution pp, String verdict) {
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(pp.prosecutor());
		ServerPlayer initiator = server.getPlayerList().getPlayer(pp.initiator());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("survival-contract.msg.smuggle_inspection_end",
					prosecutor == null ? "?" : prosecutor.getName().getString(),
					initiator == null ? "?" : initiator.getName().getString(),
					Component.translatable(verdict)));
		}
		teleportBack(server, pp.initiator(), pp.fromInitiator());
		teleportBack(server, pp.prosecutor(), pp.fromProsecutor());
	}

	/**
	 * 检察官定夺（用户规则 ①②③④⑥）：
	 *   action 0=受贿：贿赂袋清→检察官加分；价值袋清→被检查者加分。
	 *   action 1=检查：
	 *     - 无走私品 → 检察官赔偿货品 1/4（货品=价值袋内容），价值袋清→被检查者加分；
	 *     - 有走私品 → 价值袋清→检察官加分（没收），贿赂袋不受影响。
	 */
	public void decide(MinecraftServer server, UUID prosecutorId, UUID initiatorId, int action) {
		PendingProsecution pp = pending.get(initiatorId);
		if (pp == null || !pp.prosecutor().equals(prosecutorId)) return;
		GameStateManager gsm = GameStateManager.getInstance();
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(prosecutorId);
		ServerPlayer initiator = server.getPlayerList().getPlayer(initiatorId);
		pending.remove(initiatorId);
		roomEntered.remove(initiatorId);
		if (prosecutor == null || initiator == null
				|| gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING || !gsm.isStowaway()) {
			teleportBack(server, initiatorId, pp.fromInitiator());
			teleportBack(server, prosecutorId, pp.fromProsecutor());
			return;
		}
		String verdict = action == ACTION_ACCEPT_BRIBE
				? settleAcceptBribe(server, pp)
				: settleInspect(server, pp);
		finishInspection(server, pp, verdict);
	}

	/**
	 * 检查结算（用户规则 ③④）：
	 *   无走私品 → 检察官赔偿货品 1/4（货品=价值袋总价）+ 价值袋清→被检查者加分；
	 *   有走私品 → 没收价值袋给检察官加分，贿赂袋不受影响。
	 */
	private String settleInspect(MinecraftServer server, PendingProsecution pp) {
		GameStateManager gsm = GameStateManager.getInstance();
		ServerPlayer prosecutor = server.getPlayerList().getPlayer(pp.prosecutor());
		ServerPlayer initiator = server.getPlayerList().getPlayer(pp.initiator());
		if (!hasSmuggleGood(initiator)) {
			// 情况一：对面没有走私品
			int goods = goodsTotal(initiator);
			int comp = goods / 4;
			if (comp > 0) gsm.deductScore(pp.prosecutor(), comp);
			gsm.addScore(pp.initiator(), comp);
			int bagGain = redeemValueBag(initiator);
			prosecutor.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_inspect_none_pros", comp));
			initiator.sendSystemMessage(Component.translatable(
					"survival-contract.msg.smuggle_inspect_none_init", comp, goods, bagGain > 0
							? Component.translatable("survival-contract.msg.smuggle_bag_cleared", bagGain)
							: Component.translatable("survival-contract.msg.smuggle_bag_empty")));
			return "survival-contract.msg.smuggle_verdict_none";
		}
		// 情况二：对面有走私品 → 没收价值袋所有物品给检察官加分，贿赂袋不受影响
		int confiscated = confiscateValueBagOnly(initiator);
		gsm.addScore(pp.prosecutor(), confiscated);
		prosecutor.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_inspect_found_pros",
				initiator.getName().getString(), confiscated));
		initiator.sendSystemMessage(Component.translatable(
				"survival-contract.msg.smuggle_inspect_found_init", confiscated));
		return "survival-contract.msg.smuggle_verdict_found";
	}

	/**
	 * 没收价值袋所有物品给检察官（用户规则 ④）：
	 * 仅清价值收纳袋容器；贿赂袋/物品栏不受任何影响。
	 * 批次感知：当前批次公开商品按独立分值、走私品按 128（每批每位玩家一次上限），上批货物 0 分。
	 */
	private int confiscateValueBagOnly(ServerPlayer target) {
		SimpleContainer vb = valueBags.get(target.getUUID());
		if (vb == null) return 0;
		String gk = groupKeyOf(target);
		return processBagAndClear(vb, gk, target.getUUID());
	}

	/**
	 * 通行兑换（受贿 / 无走私品放行共用，用户规则 ②③）：
	 * 仅清价值收纳袋容器并给被检查者加分。物品栏价值物品不动。
	 * 批次感知：当前批次公开商品按独立分值、走私品按 128（每批每位玩家一次上限），上批货物 0 分。
	 */
	private int redeemValueBag(ServerPlayer target) {
		SimpleContainer vb = valueBags.get(target.getUUID());
		if (vb == null) return 0;
		String gk = groupKeyOf(target);
		int gain = processBagAndClear(vb, gk, target.getUUID());
		if (gain > 0) GameStateManager.getInstance().addScore(target.getUUID(), gain);
		return gain;
	}

	/**
	 * 通用：价值袋计价 + 清空 + 走私品批次上限登记。
	 * - 公开商品只认该组当前批次三件，各自独立分值；
	 * - 走私品：匹配当前批次则 128 分，同一玩家本批超过一次 → 0 分（用户规则 ⑥）；
	 * - 其他物品（上批货物、过轮走私品、非价值物品）→ 0 分但仍随袋清空（袋子语义）；
	 * - 返回本批次感知下的实际分值总和（供结算加/扣分用）。
	 */
	private int processBagAndClear(SimpleContainer bag, String groupKey, UUID playerId) {
		int total = 0;
		Item curSmuggle = smuggleItems.get(groupKey);
		Set<UUID> redeemed = smuggleRedeemedThisBatch.computeIfAbsent(groupKey, k -> new HashSet<>());
		boolean smuggleThisBatchAlreadyUsed = redeemed.contains(playerId);
		for (int i = 0; i < bag.getContainerSize(); i++) {
			ItemStack s = bag.getItem(i);
			if (s.isEmpty()) continue;
			Item it = s.getItem();
			if (isSmuggleItem(it)) {
				if (it == curSmuggle && !smuggleThisBatchAlreadyUsed) {
					// 袋内违禁品无论多少个（整组/多格），清空后最多只按 1 个计 128 分
					total += SMUGGLE_VALUE;
				}
				// 本批走私品无论是否计过分都要登记一次（一场结算后就锁定，下一场不管是换出还是没收都不再计）
				redeemed.add(playerId);
				smuggleThisBatchAlreadyUsed = true;
				continue;
			}
			int v = itemValueForGroup(groupKey, it);
			if (v > 0) total += v * s.getCount();
		}
		bag.clearContent();
		return total;
	}

	/** 该玩家是否在检查室内（检查阶段双方：被检查者或检察官） */
	public boolean isInInspection(UUID playerId) {
		for (var e : pending.entrySet()) {
			if (e.getKey().equals(playerId) || e.getValue().prosecutor().equals(playerId)) return true;
		}
		return false;
	}

	// ==================== 检查室 ====================
	/**
	 * 出生点旁高空搭建密闭检查室（第六十三批：内部 5×7×3——长5 × 宽7 × 高3）：
	 * 石英墙壁 + 白桦木板底板/天花板 + 顶部中央蛙明灯照明 + 两侧杜鹃花树苗盆栽；
	 * 长方向两端各一块地毯（一红一蓝，中间相隔 3 方块）——被检查者站红地毯、检察官站蓝地毯。
	 */
	private void buildCabin(ServerLevel level) {
		BlockPos spawn = GameStateManager.getInstance().getPlayingSpawn();
		int x = spawn.getX() + 40, z = spawn.getZ() + 40;
		int y = Math.min(250, spawn.getY() + 100);
		var quartz = Blocks.QUARTZ_BLOCK.defaultBlockState();
		var plank = Blocks.BIRCH_PLANKS.defaultBlockState();
		var froglight = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
		// 26.2 无 Blocks.RED_CARPET 常量（CARPET 是 ColorCollection）→ 注册表直查原版地毯
		var carpetRed = BuiltInRegistries.BLOCK.getValue(
				Identifier.fromNamespaceAndPath("minecraft", "red_carpet")).defaultBlockState();
		var carpetBlue = BuiltInRegistries.BLOCK.getValue(
				Identifier.fromNamespaceAndPath("minecraft", "blue_carpet")).defaultBlockState();
		var pottedAzalea = Blocks.POTTED_AZALEA.defaultBlockState();
		var air = Blocks.AIR.defaultBlockState();
		// 外壳：底板 dy=-1 / 天花板 dy=3 为白桦木板；四周墙（含四角）石英；内部 dx 0-4 × dz 0-6 × dy 0-2
		for (int dx = -1; dx <= 5; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				for (int dz = -1; dz <= 7; dz++) {
					var state = dy == -1 || dy == 3 ? plank
							: (dx == -1 || dx == 5 || dz == -1 || dz == 7) ? quartz : air;
					BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
					level.setBlock(pos, state, 3);
					protectedBlocks.put(pos.immutable(), state);
				}
			}
		}
		// 顶部中央蛙明灯（替换天花板中央一块，向室内照明）
		BlockPos lightPos = new BlockPos(x + 2, y + 3, z + 3);
		level.setBlock(lightPos, froglight, 3);
		protectedBlocks.put(lightPos.immutable(), froglight);
		// 长方向两端地毯：红 (0,0,3) / 蓝 (4,0,3)，中间相隔 3 方块
		BlockPos redCarpet = new BlockPos(x, y, z + 3);
		BlockPos blueCarpet = new BlockPos(x + 4, y, z + 3);
		level.setBlock(redCarpet, carpetRed, 3);
		level.setBlock(blueCarpet, carpetBlue, 3);
		protectedBlocks.put(redCarpet.immutable(), carpetRed);
		protectedBlocks.put(blueCarpet.immutable(), carpetBlue);
		// 两侧杜鹃花树苗盆栽（两条长墙内侧中央）
		BlockPos potA = new BlockPos(x + 2, y, z);
		BlockPos potB = new BlockPos(x + 2, y, z + 6);
		level.setBlock(potA, pottedAzalea, 3);
		level.setBlock(potB, pottedAzalea, 3);
		protectedBlocks.put(potA.immutable(), pottedAzalea);
		protectedBlocks.put(potB.immutable(), pottedAzalea);
		cabinMin = new BlockPos(x - 1, y - 1, z - 1);
		cabinMax = new BlockPos(x + 5, y + 3, z + 7);
		cabinSpotA = new BlockPos(x, y, z + 3);     // 被检查者：红地毯位（地毯格，实体落在毯面）
		cabinSpotB = new BlockPos(x + 4, y, z + 3); // 检察官：蓝地毯位
	}

	/** 受保护方块自愈（每 2 秒）：玩家破坏已被事件拦截，爆炸/活塞等环境破坏由此恢复 */
	private void protectSelfHeal(MinecraftServer server) {
		if (protectedBlocks.isEmpty()) return;
		ServerLevel level = com.survivalcontract.dimension.ArenaWorldManager.getArenaOverworld(server);
		if (level == null) return;
		protectedBlocks.forEach((pos, state) -> {
			if (!level.getBlockState(pos).equals(state)) level.setBlock(pos, state, 3);
		});
	}

	private boolean isProtectedPos(BlockPos pos) {
		if (cabinMin == null || cabinMax == null) return false;
		return pos.getX() >= cabinMin.getX() && pos.getX() <= cabinMax.getX()
				&& pos.getY() >= cabinMin.getY() && pos.getY() <= cabinMax.getY()
				&& pos.getZ() >= cabinMin.getZ() && pos.getZ() <= cabinMax.getZ();
	}

	/** 注册保护事件（检查室不可破坏）——mod 初始化时调用一次。
	 *  爆炸保护无 Fabric 事件可用（26.2），由 tick 自愈机制兜底（protectSelfHeal）。
	 *  检查室禁丢弃由 ServerPlayerDropMixin 实现（26.2 Fabric API 已移除 ItemTossEvent）。 */
	public void registerEvents() {
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(world, player, pos, state, blockEntity) -> {
			if (isProtectedPos(pos)) {
				if (player instanceof ServerPlayer sp) {
					sp.sendSystemMessage(Component.translatable("survival-contract.msg.smuggle_indestructible"));
				}
				return false;
			}
			return true;
		});
	}

	// ==================== 传送 ====================
	private void teleport(ServerPlayer player, BlockPos pos) {
		if (player != null && player.level() instanceof ServerLevel level) {
			player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
					java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		}
	}

	/** 检查室内锁站位：偏离地毯站位点（水平 >0.25 格或垂直 >0.25 格）即拉回（保留视角） */
	private void clampToSpot(ServerPlayer p, BlockPos spot) {
		if (p == null || spot == null) return;
		double dx = p.getX() - (spot.getX() + 0.5);
		double dy = p.getY() - spot.getY();
		double dz = p.getZ() - (spot.getZ() + 0.5);
		if (dx * dx + dz * dz > 0.0625 || Math.abs(dy) > 0.25) teleport(p, spot);
	}

	private void teleportBack(MinecraftServer server, UUID playerId, BlockPos from) {
		ServerPlayer p = server.getPlayerList().getPlayer(playerId);
		if (p == null || from == null) return;
		if (p.level() instanceof ServerLevel level) {
			p.teleportTo(level, from.getX() + 0.5, from.getY(), from.getZ() + 0.5,
					java.util.Set.of(), p.getYRot(), p.getXRot(), false);
		}
	}

	// ==================== 状态同步（S2C SmuggleSyncPayload） ====================
	public void broadcastSync(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSync(p);
	}

	public void sendSync(ServerPlayer p) {
		GameStateManager gsm = GameStateManager.getInstance();
		List<NetworkRegistry.SmuggleSyncPayload.SmuggleEntry> own = new ArrayList<>();
		List<NetworkRegistry.SmuggleSyncPayload.SmuggleEntry> enemy = new ArrayList<>();
		int remain = 0;
		if (gsm.isStowaway() && serverRef != null) {
			String myKey = groupKeyOf(p);
			Item[] batch = currentBatch.get(myKey);
			int[] scores = batchScores.get(myKey);
			if (batch != null && scores != null) {
				for (int i = 0; i < 3; i++) {
					own.add(new NetworkRegistry.SmuggleSyncPayload.SmuggleEntry(itemId(batch[i]), scores[i]));
				}
			}
			// 走私品：仅我方可见（对方清单不下发），固定 128 分
			Item smuggleItem = smuggleItems.get(myKey);
			if (smuggleItem != null) {
				own.add(new NetworkRegistry.SmuggleSyncPayload.SmuggleEntry(
						itemId(smuggleItem), SMUGGLE_VALUE));
			}
			// 团队模式：对方公开清单三件全部可见（名称+图标），但不显示分值（score 恒 0）；
			// 对方走私品不下发（仅我方可见）
			if (gsm.isTeamMode()) {
				String enemyKey = myKey.equals("RED") ? "BLUE" : "RED";
				Item[] eb = currentBatch.get(enemyKey);
				if (eb != null) {
					for (Item item : eb) {
						enemy.add(new NetworkRegistry.SmuggleSyncPayload.SmuggleEntry(itemId(item), 0));
					}
				}
			}
			remain = (int) Math.max(0, (BATCH_INTERVAL_TICKS - (serverRef.getTickCount() - batchStartTick)) / 20);
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
				new NetworkRegistry.SmuggleSyncPayload(own, enemy, remain));
	}

	private static String itemId(Item item) {
		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id == null ? "" : id.toString();
	}

	// ==================== 重置（resetSessionStatics / performResetForLobby 调用） ====================
	public void reset() {
		currentBatch.clear();
		batchScores.clear();
		smuggleRedeemedThisBatch.clear();
		smuggleItems.clear();
		valueBags.clear();
		bribeBags.clear();
		pending.clear();
		roomEntered.clear();
		lastInitiateTick.clear();
		lastProsecutorChosen.clear();
		scheduledTitles.clear();
		protectedBlocks.clear();
		batchStartTick = 0L;
		cabinMin = cabinMax = cabinSpotA = cabinSpotB = null;
		serverRef = null;
	}
}
