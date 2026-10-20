package com.survivalcontract.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * 客户端文案本地化助手。
 * <p>键约定（资源：assets/survival-contract/lang/&lt;locale&gt;.json）：</p>
 * <ul>
 *   <li>survival-contract.challenge.&lt;id&gt;.name / .desc —— 挑战规则名与描述</li>
 *   <li>survival-contract.profession.&lt;KEY&gt;.name / .desc —— 职业名与描述</li>
 *   <li>survival-contract.event.&lt;NAME&gt;.name / .desc —— 遭遇事件名与描述</li>
 *   <li>survival-contract.gui.&lt;key&gt; —— 界面静态文案（zh_cn.json 必须提供，否则简中显示英文）</li>
 *   <li>survival-contract.msg.&lt;key&gt; —— 服务端广播（服务端发 Component.translatable，客户端按自身语言解析）</li>
 * </ul>
 * <p>解析顺序：当前游戏语言的语言文件 → 语言文件缺键时回退 fallback（服务端下发/源码简中）。
 * 简体中文环境：内容键不在 zh_cn.json 中（源码简中即为权威），自动回退；GUI/消息键在 zh_cn.json 提供。</p>
 */
public final class ClientL10n {

    private ClientL10n() {}

    /** 解析翻译键：语言文件无该键 → 返回 fallback。
     *  简中环境直接返回 fallback（源码简中即权威）：原版语言加载以 en_us 为基底合并，
     *  zh_cn.json 缺失的内容键若走 getOrDefault 会抢先解析成 en_us 英文值。 */
    public static String tr(String key, String fallback) {
        try {
            if ("zh_cn".equals(net.minecraft.client.Minecraft.getInstance()
                    .getLanguageManager().getSelected())) {
                return fallback;
            }
            return Language.getInstance().getOrDefault(key, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String challengeName(String id, String fallback) {
        return tr("survival-contract.challenge." + id + ".name", fallback);
    }

    public static String challengeDesc(String id, String fallback) {
        return tr("survival-contract.challenge." + id + ".desc", fallback);
    }

    public static String professionName(String enumKey, String fallback) {
        return tr("survival-contract.profession." + enumKey + ".name", fallback);
    }

    public static String professionDesc(String enumKey, String fallback) {
        return tr("survival-contract.profession." + enumKey + ".desc", fallback);
    }

    public static String eventName(String enumKey, String fallback) {
        return tr("survival-contract.event." + enumKey + ".name", fallback);
    }

    public static String eventDesc(String enumKey, String fallback) {
        return tr("survival-contract.event." + enumKey + ".desc", fallback);
    }

    /** GUI 键（Component 形式，可带格式化参数；zh_cn.json 必须提供对应值） */
    public static Component gui(String key, Object... args) {
        return Component.translatable("survival-contract.gui." + key, args);
    }

    /** GUI 键的纯文本形式（拼接进字符串标签时使用） */
    public static String guiText(String key, Object... args) {
        return gui(key, args).getString();
    }
}
