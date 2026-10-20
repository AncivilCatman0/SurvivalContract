package com.survivalcontract.game;

import com.mojang.serialization.DataResult;
import com.survivalcontract.SurvivalContract;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 团队箱：无实体容器，快捷键打开，1 行 × 9 格。
 * 个人模式（SOLO）：每个人的团队箱彼此独立；
 * 团队模式（TEAMS）：同色队伍（红/蓝）的团队箱物品互通。
 */
public class TeamChestManager {

	private static final TeamChestManager INSTANCE = new TeamChestManager();

	public static final int SIZE = 9; // 1*9

	// 个人模式：每玩家一个独立容器
	private final Map<UUID, SimpleContainer> soloChests = new HashMap<>();
	// 团队模式：按队伍名（"RED"/"BLUE"）共享
	private final Map<String, SimpleContainer> teamChests = new HashMap<>();

	private TeamChestManager() {}

	public static TeamChestManager getInstance() {
		return INSTANCE;
	}

	/** 取得玩家对应的团队箱容器（个人独立 / 同队互通）。 */
	public SimpleContainer getContainerFor(ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		if (gsm.getGameMode() == GameStateManager.GameMode.TEAMS) {
			GameStateManager.Team team = gsm.getPlayerTeam(player.getUUID());
			// NONE（未分配）回退红队箱，避免丢失
			String key = (team == GameStateManager.Team.BLUE) ? "BLUE" : "RED";
			return teamChests.computeIfAbsent(key, k -> new SimpleContainer(SIZE));
		}
		return soloChests.computeIfAbsent(player.getUUID(), k -> new SimpleContainer(SIZE));
	}

	/** 按快捷键打开团队箱（仅 PLAYING 阶段）。 */
	public void openFor(ServerPlayer player) {
		GameStateManager gsm = GameStateManager.getInstance();
		// 走私犯与检察官：团队箱强制关闭（货物装进价值收纳袋，检查通行时兑换）
		if (gsm.isStowaway()) {
			player.sendSystemMessage(Component.translatable("survival-contract.msg.team_chest_stowaway"));
			return;
		}
		// 附属玩法：团队箱强制关闭
		if (gsm.isAddon()) {
			player.sendSystemMessage(Component.translatable("survival-contract.msg.team_chest_addon"));
			return;
		}
		if (!gsm.isRuleActive("rule_team_chest")) {
			player.sendSystemMessage(Component.translatable("survival-contract.msg.team_chest_off"));
			return;
		}
		if (gsm.getCurrentPhase() != GameStateManager.GamePhase.PLAYING) {
			player.sendSystemMessage(Component.translatable("survival-contract.msg.team_chest_playing_only"));
			return;
		}
		SimpleContainer container = getContainerFor(player);
		Component title;
		if (GameStateManager.getInstance().getGameMode() == GameStateManager.GameMode.TEAMS) {
			GameStateManager.Team team = GameStateManager.getInstance().getPlayerTeam(player.getUUID());
			boolean blue = team == GameStateManager.Team.BLUE;
			title = Component.translatable(blue
					? "survival-contract.gui.team_chest.blue" : "survival-contract.gui.team_chest.red");
		} else {
			title = Component.translatable("survival-contract.gui.team_chest.title");
		}
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, container, 1),
				title));
	}

	/** 新一局开始时清空全部团队箱。 */
	public void clearAll() {
		soloChests.clear();
		teamChests.clear();
	}

	// ============ NBT 持久化（ItemStack.CODEC + NbtOps） ============

	/** 序列化全部团队箱内容（单人存档退出重进时恢复）。 */
	public CompoundTag saveToNbt() {
		CompoundTag root = new CompoundTag();
		if (!soloChests.isEmpty()) {
			CompoundTag solo = new CompoundTag();
			soloChests.forEach((uuid, c) -> solo.put(uuid.toString(), containerToList(c)));
			root.put("solo", solo);
		}
		if (!teamChests.isEmpty()) {
			CompoundTag teams = new CompoundTag();
			teamChests.forEach((name, c) -> teams.put(name, containerToList(c)));
			root.put("teams", teams);
		}
		return root;
	}

	/** 从 NBT 恢复团队箱内容。 */
	public void loadFromNbt(CompoundTag root) {
		clearAll();
		CompoundTag solo = root.getCompoundOrEmpty("solo");
		for (String key : solo.keySet()) {
			try {
				soloChests.put(UUID.fromString(key), listToContainer(solo.getListOrEmpty(key)));
			} catch (IllegalArgumentException ignored) {}
		}
		CompoundTag teams = root.getCompoundOrEmpty("teams");
		for (String key : teams.keySet()) {
			teamChests.put(key, listToContainer(teams.getListOrEmpty(key)));
		}
	}

	private ListTag containerToList(SimpleContainer c) {
		// 统一 CompoundTag 结构 {slot:int, item:Tag}，空槽跳过 — 避免 ListTag 混合类型写出崩溃
		ListTag list = new ListTag();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (stack.isEmpty()) continue;
			DataResult<Tag> result = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack);
			Tag tag = result.result().orElse(null);
			if (tag == null) continue;
			CompoundTag entry = new CompoundTag();
			entry.putInt("slot", i);
			entry.put("item", tag);
			list.add(entry);
		}
		return list;
	}

	private SimpleContainer listToContainer(ListTag list) {
		SimpleContainer c = new SimpleContainer(SIZE);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			if (entry.isEmpty()) continue;
			Tag itemTag = entry.get("item");
			if (itemTag == null) continue;
			int slot = entry.getIntOr("slot", -1);
			if (slot < 0 || slot >= SIZE) continue;
			ItemStack stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, itemTag).result().orElse(ItemStack.EMPTY);
			if (!stack.isEmpty()) c.setItem(slot, stack);
		}
		return c;
	}
}
