package com.survivalcontract.lock;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 拾取守卫（「禁用背包格子」规则）：拾取物品时只在**未锁定槽**插入。
 * <ul>
 *   <li>可用槽（快捷栏 + 未锁定的背包格）占满 → 返回 false 且**不改动传入栈** → 实体物品留在地上，拾取失败；</li>
 *   <li>未锁定槽里有同类且未达堆叠上限的栈 → 先正常补堆（余量继续留在实体里）；</li>
 *   <li>未开启「禁用背包格子」（或非 PLAYING 阶段）→ 交回原版 {@link Inventory#add(ItemStack)}。</li>
 * </ul>
 * 由 {@code ItemEntityPickupMixin} 重定向 {@code ItemEntity.playerTouch} 里那一次 add 调用接入，
 * 因此其它 {@code add} 调用方（补给发放、铁厨刀补发等）行为保持不变。
 */
public final class PickupGuard {

    private PickupGuard() {
    }

    /** 拾取入口：返回是否有物品被收下（false = 可用槽已满，物品留在地上） */
    public static boolean pickup(Inventory inv, ItemStack stack) {
        if (inv == null || stack == null || stack.isEmpty()) return false;
        Player player = inv.player;
        if (player == null) return false;
        // 只处理真正的背包槽（0~35：快捷栏 + 主背包）；不要用 getContainerSize()——
        // 它还会把护甲/副手/躯体等装备槽（36~40）算进来，导致物品被塞进装备槽。
        int size = Inventory.INVENTORY_SIZE;
        boolean[] locked = null;
        for (int i = 0; i < size; i++) {
            if (!LockedSlots.isLocked(player, i)) continue;
            if (locked == null) locked = new boolean[size];
            locked[i] = true;
        }
        if (locked == null) return inv.add(stack); // 未开启「禁用背包格子」→ 原版行为
        return addUnlocked(inv, stack, locked);
    }

    /** 只在未锁定槽插入：先补堆同类未满栈，再占未锁定空槽（就地修改传入栈，语义同原版 add） */
    private static boolean addUnlocked(Inventory inv, ItemStack stack, boolean[] locked) {
        int size = Inventory.INVENTORY_SIZE;
        int start = stack.getCount();
        if (stack.isStackable()) {
            for (int i = 0; i < size && !stack.isEmpty(); i++) {
                if (locked[i]) continue;
                ItemStack cur = inv.getItem(i);
                if (cur.isEmpty() || !ItemStack.isSameItemSameComponents(cur, stack)) continue;
                int room = cur.getMaxStackSize() - cur.getCount();
                if (room <= 0) continue;
                int move = Math.min(room, stack.getCount());
                cur.grow(move);
                cur.setPopTime(5);
                stack.shrink(move);
            }
        }
        if (!stack.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (locked[i] || !inv.getItem(i).isEmpty()) continue;
                inv.setItem(i, stack.copyAndClear());
                inv.getItem(i).setPopTime(5);
                break;
            }
        }
        if (stack.getCount() != start) inv.setChanged();
        return stack.getCount() < start;
    }
}
