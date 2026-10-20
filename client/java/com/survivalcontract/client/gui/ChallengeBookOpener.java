package com.survivalcontract.client.gui;

import com.survivalcontract.network.NetworkRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 客户端侧的挑战书 GUI 打开协调器。
 *
 * 流程：
 *   1. 右键挑战书 → 服务端通过 UseItem 事件调用 LobbyManager.giveChallengeBook，
 *      并可选地发 OpenChallengeBookPayload 到服务端；客户端直接 openChallengeBook()
 *   2. 客户端发 ChallengeSyncRequestPayload → 服务端回 ChallengeSyncPayload
 *   3. 客户端 S2C handler 收到 payload → 应用到 ChallengeBookScreen → 显示
 *
 * 为了简化，我们把 "先请求再打开" 做成两步：
 *   - 客户端先 open 一个空 GUI，同时发 sync request
 *   - S2C handler 再 apply 数据并刷新
 */
public class ChallengeBookOpener {

    private static ChallengeBookScreen activeScreen;

    public static void register() {
        // 注意：这里不再注册 ChallengeSyncPayload / ScoreSyncPayload 的 receiver ——
        // Fabric API 对同一 payload 类型只允许注册一个 global receiver（后注册的静默失败），
        // ClientState 先注册已占用；sync 后刷新 GUI 的逻辑统一在 ClientState 的 receiver 中处理。

        // 右键挑战书 → 直接打开 GUI（客户端侧拦截，服务端只负责发 sync）
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() && player instanceof net.minecraft.client.player.LocalPlayer lp) {
                if (isChallengeBook(lp.getItemInHand(hand))) {
                    openChallengeBook();
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
                // 右键指认书（神的谎言）→ 请求服务端下发存活玩家清单，S2C 到达后打开指认书界面
                if (isAccuseBook(lp.getItemInHand(hand))) {
                    ClientPlayNetworking.send(new NetworkRegistry.OpenAccuseBookPayload());
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
                // 右键检查发起书（走私犯与检察官）→ 请求服务端下发清单/决策状态，S2C 到达后打开检查书界面
                if (isProsecuteBook(lp.getItemInHand(hand))) {
                    ClientPlayNetworking.send(new NetworkRegistry.OpenProsecutePayload());
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
                // 右键收纳袋（走私犯与检察官）→ 请求服务端打开对应收纳袋界面
                net.minecraft.world.InteractionResult bagResult = handleBagUse(lp, hand);
                if (bagResult != net.minecraft.world.InteractionResult.PASS) {
                    return bagResult;
                }
                // 右键神谕纸符（神的谎言，第五十九批）→ 请求服务端使用纸符
                // （平民：武装一次神罚免疫；使者：服务端下发存活清单 → 链接选人界面）
                if (isRelicPaper(lp.getItemInHand(hand))) {
                    ClientPlayNetworking.send(new NetworkRegistry.UseRelicPayload());
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
                // 右键任务刷新券（神的谎言）→ 请求服务端消耗并立即刷新当前专属任务
                if (isTaskRefreshToken(lp.getItemInHand(hand))) {
                    ClientPlayNetworking.send(new NetworkRegistry.UseTaskRefreshPayload());
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // 右键方块/实体路径（第六十七批）：手持收纳袋时同样请求服务端打开对应袋界面并
        // SUCCESS 短路原版收纳袋行为——否则原版收纳袋 GUI 经方块/实体路径可达，
        // 物品会被塞进钥匙道具内嵌 BundleContents，下次开袋被防夹带机制倒回物品栏
        //（"价值袋货物自己跑进物品栏"的根因）
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() && player instanceof net.minecraft.client.player.LocalPlayer lp) {
                return handleBagUse(lp, hand);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && player instanceof net.minecraft.client.player.LocalPlayer lp) {
                return handleBagUse(lp, hand);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }

    /** 手持收纳袋（价值袋/贿赂袋）的右键处理：发 OpenBagPayload 并 SUCCESS 短路原版行为；非收纳袋 PASS */
    private static net.minecraft.world.InteractionResult handleBagUse(net.minecraft.client.player.LocalPlayer lp,
                                                                      net.minecraft.world.InteractionHand hand) {
        Boolean bagBribe = isBagItem(lp.getItemInHand(hand));
        if (bagBribe == null) return net.minecraft.world.InteractionResult.PASS;
        ClientPlayNetworking.send(new NetworkRegistry.OpenBagPayload(bagBribe));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    private static boolean isChallengeBook(net.minecraft.world.item.ItemStack stack) {
        if (stack == null) return false;
        if (!stack.is(net.minecraft.world.item.Items.BOOK) && !stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) return false;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("挑战书");
    }

    /** 指认书（神的谎言）：普通书 + 自定义名含"指认书" */
    private static boolean isAccuseBook(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || !stack.is(net.minecraft.world.item.Items.BOOK)) return false;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("指认书");
    }

    /** 检查发起书（走私犯与检察官）：普通书 + 自定义名含"检查发起书" */
    private static boolean isProsecuteBook(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || !stack.is(net.minecraft.world.item.Items.BOOK)) return false;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("检查发起书");
    }

    /**
     * 收纳袋（走私犯与检察官，第六十四批）：棕色收纳袋（价值袋）/ 红色收纳袋（贿赂袋）+ 自定义名判定。
     * 26.2 无彩色 Bundle 常量 → 注册表 id 直查；右键 SUCCESS 短路原版收纳袋行为。
     * 返回 true=贿赂袋，false=价值袋，null=非收纳袋。
     */
    private static Boolean isBagItem(net.minecraft.world.item.ItemStack stack) {
        if (stack == null) return null;
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!id.equals("minecraft:bundle") && !id.equals("minecraft:red_bundle")) return null;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name == null) return null;
        String text = name.getString();
        if (text.contains("贿赂收纳袋")) return true;
        if (text.contains("价值收纳袋")) return false;
        return null;
    }

    /** 神谕纸符（神的谎言，第五十九批）：纸 + 自定义名含"神谕纸符" */
    private static boolean isRelicPaper(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || !stack.is(net.minecraft.world.item.Items.PAPER)) return false;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("神谕纸符");
    }

    /** 任务刷新券（神的谎言）：时钟 + 自定义名含"任务刷新券" */
    private static boolean isTaskRefreshToken(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || !stack.is(net.minecraft.world.item.Items.CLOCK)) return false;
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name != null && name.getString().contains("任务刷新券");
    }

    /** 打开挑战书 GUI（客户端直接调用） */
    public static void openChallengeBook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        activeScreen = new ChallengeBookScreen();
        mc.setScreenAndShow(activeScreen);

        // 请求服务端同步完整状态（sync 到达后由 ClientState 的 receiver 刷新 GUI）
        ClientPlayNetworking.send(new NetworkRegistry.ChallengeSyncRequestPayload());
    }

    public static ChallengeBookScreen getActiveScreen() {
        return activeScreen;
    }
}
