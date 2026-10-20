package com.survivalcontract;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * MC 版本兼容层：通过反射屏蔽 26.2 ↔ 26.3 原版 API 差异。
 *
 * <pre>
 * 变更点（26.2 → 26.3）：
 *   1. Player.drop(stack, boolean)  → Player 基类已无 drop()；ServerPlayer.drop(stack, boolean, Prediction)
 *      但 26.2 ServerPlayer.drop(stack, boolean, boolean) 有第三个 boolean
 *   2. setInvulnerable(boolean)     → setPermanentlyInvulnerable(boolean) + setInvulnerableTime(int)
 *   3. BundleContents.itemCopies() → BundleContents.itemCopies()
 *   4. Entity.invulnerableTime (public field) → getInvulnerableTime()/setInvulnerableTime(int)
 *   5. new BundleContents.Mutable(bc)  → bc.asMutable()
 * </pre>
 */
public final class McCompat {

    private McCompat() {
    }

    // ============================================================
    // 1) drop —— 统一封装 26.2 / 26.3
    //    26.2 Player:          drop(ItemStack, boolean)                              [2 参数]
    //    26.2 ServerPlayer:    drop(ItemStack, boolean, boolean)                     [3 参数]
    //    26.3 Player 基类:     已无 drop()！必须走 ServerPlayer.drop(ItemStack, boolean, Prediction)
    //
    // 语义：serverDrop(player, stack, dropImmediately) —— 只关心掉在哪（2 参数版本）
    //       serverDrop(player, stack, dropImmediately, thirdBoolean) —— 关心第三参数语义
    //          26.2: 第三 boolean 原样传递
    //          26.3: 第三参数忽略（Prediction.SERVER_ONLY 由内部自动传）
    // ============================================================

    /** 2 参数语义版本 —— 屏蔽 26.2 Player.drop(2) vs 26.3 ServerPlayer.drop(3, Prediction) */
    public static ItemEntity drop(Player player, ItemStack stack, boolean dropImmediately) {
        return internalDrop(player, stack, dropImmediately, null);
    }

    /** 带第三 boolean 参数的版本（ServerPlayer.drop 第三参数语义） */
    public static ItemEntity drop(Player player, ItemStack stack, boolean dropImmediately, boolean thirdParam) {
        return internalDrop(player, stack, dropImmediately, thirdParam);
    }

    private static ItemEntity internalDrop(Player player, ItemStack stack, boolean dropImmediately, Boolean thirdBoolean) {
        Class<?> cls = player.getClass();

        // 26.3 ServerPlayer.drop(ItemStack, boolean, Prediction)
        Class<?> predictionCls = tryClass("net.minecraft.util.Prediction");
        if (predictionCls != null) {
            try {
                Object serverOnly = predictionCls.getField("SERVER_ONLY").get(null);
                Method m = findMethod(cls, "drop", ItemStack.class, boolean.class, predictionCls);
                if (m != null) {
                    m.setAccessible(true);
                    return (ItemEntity) m.invoke(player, stack, dropImmediately, serverOnly);
                }
            } catch (Throwable ignored) {
            }
        }

        // 26.2 Player.drop(ItemStack, boolean)  —— 无第三参数
        if (thirdBoolean == null) {
            try {
                Method m = findMethod(cls, "drop", ItemStack.class, boolean.class);
                if (m != null) {
                    m.setAccessible(true);
                    return (ItemEntity) m.invoke(player, stack, dropImmediately);
                }
            } catch (Throwable ignored) {
            }
        }

        // 26.2 ServerPlayer.drop(ItemStack, boolean, boolean)
        try {
            Method m = findMethod(cls, "drop", ItemStack.class, boolean.class, boolean.class);
            if (m != null) {
                m.setAccessible(true);
                boolean third = thirdBoolean != null ? thirdBoolean : false;
                return (ItemEntity) m.invoke(player, stack, dropImmediately, third);
            }
        } catch (Throwable ignored) {
        }

        throw new UnsupportedOperationException(
                "McCompat: 无法在 " + cls.getName() + " 上找到合适的 drop() 方法");
    }

    // ============================================================
    // 2) setInvulnerable —— Entity
    //    26.2: entity.setInvulnerable(boolean)
    //    26.3: entity.setPermanentlyInvulnerable(boolean)
    // ============================================================
    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        // 先试 26.2
        try {
            Method m = findMethod(entity.getClass(), "setInvulnerable", boolean.class);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(entity, invulnerable);
                return;
            }
        } catch (Throwable ignored) {
        }
        // 再试 26.3
        try {
            Method m = findMethod(entity.getClass(), "setPermanentlyInvulnerable", boolean.class);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(entity, invulnerable);
                return;
            }
        } catch (Throwable ignored) {
        }
        throw new UnsupportedOperationException(
                "McCompat: 无法在 " + entity.getClass().getName() + " 上找到 setInvulnerable/setPermanentlyInvulnerable");
    }

    // ============================================================
    // 3) BundleContents.itemCopies() / itemCopyStream()
    //    26.2: itemCopyStream()
    //    26.3: itemCopies()
    // ============================================================
    @SuppressWarnings("unchecked")
    public static Stream<ItemStack> itemCopies(BundleContents bc) {
        try {
            Method m = findMethod(bc.getClass(), "itemCopies");
            if (m != null) {
                m.setAccessible(true);
                return (Stream<ItemStack>) m.invoke(bc);
            }
        } catch (Throwable ignored) {
        }
        try {
            Method m = findMethod(bc.getClass(), "itemCopyStream");
            if (m != null) {
                m.setAccessible(true);
                return (Stream<ItemStack>) m.invoke(bc);
            }
        } catch (Throwable ignored) {
        }
        throw new UnsupportedOperationException(
                "McCompat: BundleContents 上找不到 itemCopies/itemCopyStream");
    }

    // ============================================================
    // 4) Entity.invulnerableTime —— 读 + 写
    //    26.2: public field
    //    26.3: private field + getInvulnerableTime()/setInvulnerableTime(int)
    // ============================================================
    public static int getInvulnerableTime(Entity entity) {
        try {
            Method m = findMethod(entity.getClass(), "getInvulnerableTime");
            if (m != null) {
                m.setAccessible(true);
                return (int) m.invoke(entity);
            }
        } catch (Throwable ignored) {
        }
        try {
            var f = entity.getClass().getField("invulnerableTime");
            return (int) f.get(entity);
        } catch (Throwable ignored) {
        }
        throw new UnsupportedOperationException("McCompat: 无法读取 invulnerableTime");
    }

    public static void setInvulnerableTime(Entity entity, int value) {
        boolean set = false;
        try {
            Method m = findMethod(entity.getClass(), "setInvulnerableTime", int.class);
            if (m != null) {
                m.setAccessible(true);
                m.invoke(entity, value);
                set = true;
            }
        } catch (Throwable ignored) {
        }
        if (!set) {
            try {
                var f = entity.getClass().getField("invulnerableTime");
                f.set(entity, value);
                set = true;
            } catch (Throwable ignored) {
            }
        }
        if (!set) throw new UnsupportedOperationException("McCompat: 无法设置 invulnerableTime");
        // 26.3 在 invulnerableTime 之外新增了独立的"伤害冷却闸"（LivingEntity.damageCooldownTime + lastHurt）：
        //   hurtServer 中 `damageCooldownTime > 10 && !BYPASSES_COOLDOWN && amount <= lastHurt → return false`
        // （26.2 该闸直接复用 invulnerableTime，故清零无敌帧即等于清闸）。26.3 必须同步清零，
        // 否则"取消原伤 + 补伤/重复伤害"链路里 ≤上次伤害的那次伤害会被静默吞掉（无伤害帧特征）。
        if (value <= 0) clearHurtCooldown(entity);
    }

    // ============================================================
    // 4b) 26.3 伤害冷却闸 —— LivingEntity.damageCooldownTime(int) / lastHurt(float)
    //     与 26.2 的 invulnerableTime 语义等价；仅 26.3 存在（26.2 调用自动忽略，无副作用）
    // ============================================================
    public static void clearHurtCooldown(Entity entity) {
        setFieldIfPresent(entity, "damageCooldownTime", Integer.valueOf(0));
        setFieldIfPresent(entity, "lastHurt", Float.valueOf(0.0f));
    }

    /** 反射沿类层次写入字段（字段不存在则忽略，兼容 26.2 无该字段的情况） */
    private static void setFieldIfPresent(Object obj, String name, Object value) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return;
            }
            c = c.getSuperclass();
        }
    }

    // ============================================================
    // 5) newMutableBundle —— 创建可变 BundleContents
    //    26.2: new BundleContents.Mutable(BundleContents)
    //    26.3: BundleContents.asMutable()
    //    两个版本的 Mutable 都有 tryInsert() / toImmutable()
    // ============================================================
    public static BundleContents.Mutable newMutableBundle(BundleContents bc) {
        // 先试 26.3 asMutable()
        try {
            Method m = findMethod(bc.getClass(), "asMutable");
            if (m != null) {
                m.setAccessible(true);
                Object o = m.invoke(bc);
                if (o instanceof BundleContents.Mutable mut) return mut;
            }
        } catch (Throwable ignored) {
        }
        // 再试 26.2 构造器
        try {
            var ctor = BundleContents.Mutable.class.getConstructor(BundleContents.class);
            return ctor.newInstance(bc);
        } catch (Throwable ignored) {
        }
        throw new UnsupportedOperationException("McCompat: 无法创建 Mutable BundleContents");
    }

    // ============================================================
    // 反射辅助
    // ============================================================
    private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Class<?> tryClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
