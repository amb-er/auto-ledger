package space.pengtao.ledger;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 监听支付宝 / 微信的支付、转账、收款通知，自动记账。
 * 每条通知的处理全过程（每一步）都写进通知日志，便于诊断。
 */
public class PayNotificationListener extends NotificationListenerService {

    private static final String ALIPAY = "com.eg.android.AlipayGphone";
    private static final String WECHAT = "com.tencent.mm";

    private static final Pattern AMOUNT_SYMBOL =
            Pattern.compile("[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern AMOUNT_UNIT =
            Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*元");

    private static final String[] IGNORE = {
            "优惠券", "优惠", "立减", "红包封面", "活动", "权益", "贷款", "借钱", "额度", "利率",
            "广告", "营销", "推广", "验证码", "校验码", "登录", "安全提醒", "风险提醒", "密码"
    };
    private static final String[] INCOME = {
            "收款", "收入", "到账", "入账", "退款", "转入", "已收", "收钱"
    };
    private static final String[] EXPENSE = {
            "支付", "付款", "支出", "消费", "扣款", "转出"
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!ALIPAY.equals(pkg) && !WECHAT.equals(pkg)) return;

        StringBuilder t = new StringBuilder("[通知] pkg=").append(pkg);

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) {
            t.append("\n  ❌ 无 extras");
            log(t.toString());
            return;
        }

        String title = str(n.extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(n.extras.getCharSequence(Notification.EXTRA_TEXT));
        String bigText = str(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        t.append("\n  title=[").append(title).append("]");
        t.append("\n  text=[").append(text).append("]");
        t.append("\n  bigText=[").append(bigText).append("]");
        t.append("\n  sub=[").append(sub).append("]");

        String full = (title + " " + text + " " + bigText + " " + sub).trim();
        t.append("\n  full=[").append(full).append("]");

        if (full.isEmpty()) {
            t.append("\n  ❌ 空通知");
            log(t.toString());
            return;
        }

        boolean hasRefund = full.contains("退款");
        boolean hitIgnore = containsAny(full, IGNORE);
        t.append("\n  含退款=").append(hasRefund).append(", 命中忽略词=").append(hitIgnore);
        if (hitIgnore && !hasRefund) {
            t.append("\n  ❌ 被忽略(营销/验证码)");
            log(t.toString());
            return;
        }

        boolean moneyRelated = containsAny(full,
                "支付", "付款", "收款", "转账", "到账", "红包", "消费", "交易", "退款",
                "入账", "收入", "余额", "账单", "零钱", "提现", "充值")
                || full.contains("¥") || full.contains("￥") || full.contains("元");
        t.append("\n  钱相关=").append(moneyRelated);
        if (!moneyRelated) {
            t.append("\n  ❌ 非钱相关");
            log(t.toString());
            return;
        }

        double amount = extractAmount(full);
        t.append("\n  提取金额=").append(amount);

        boolean incomeHit = containsAny(full, INCOME);
        boolean expenseHit = containsAny(full, EXPENSE);
        boolean isIncome = hasRefund || (incomeHit && !expenseHit);
        t.append("\n  收入词=").append(incomeHit)
                .append(", 支出词=").append(expenseHit)
                .append(", 判收入=").append(isIncome);

        double signed = amount > 0 ? (isIncome ? amount : -amount) : 0;
        t.append("\n  最终 signed=").append(signed);

        Transaction tx = new Transaction();
        tx.amount = signed;
        tx.category = guessCategory(full);
        tx.merchant = guessMerchant(title, full);
        tx.source = ALIPAY.equals(pkg) ? "alipay" : "wechat";
        tx.note = signed == 0 ? "[金额待补] " + full : full;
        tx.status = 0; // 自动识别，待确认
        tx.time = System.currentTimeMillis();

        LedgerDbHelper db = new LedgerDbHelper(this);
        long id = db.insert(tx);
        RoastHelper.roastAsync(this, db, id, tx);
        if (signed != 0) {
            NotifyHelper.notifyTransaction(this, id,
                    "记账本识别到一笔" + (isIncome ? "收款" : "支出"),
                    (isIncome ? "+" : "-") + "¥" + String.format(java.util.Locale.CHINA, "%.2f", Math.abs(signed))
                            + "，点击确认");
        }
        t.append("\n  ✅ 已入库 id=").append(id)
                .append(" 金额=").append(signed)
                .append(" 分类=").append(tx.category)
                .append(" 商户=").append(tx.merchant);
        log(t.toString());
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private void log(String entry) {
        try {
            SharedPreferences sp = getSharedPreferences("notif_log", MODE_PRIVATE);
            String existing = sp.getString("log", "");
            String combined = entry + (existing.isEmpty() ? "" : "\n\n" + existing);
            if (combined.length() > 8000) combined = combined.substring(0, 8000);
            sp.edit().putString("log", combined).apply();
        } catch (Exception ignored) {}
    }

    public static String readLog(Context ctx) {
        return ctx.getSharedPreferences("notif_log", Context.MODE_PRIVATE).getString("log", "");
    }

    private static double extractAmount(String s) {
        String masked = s
                .replaceAll("20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?", " ")
                .replaceAll("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", " ");
        Matcher m1 = AMOUNT_SYMBOL.matcher(masked);
        if (m1.find()) {
            try { return Double.parseDouble(m1.group(1)); } catch (Exception e) {}
        }
        Matcher m2 = AMOUNT_UNIT.matcher(masked);
        if (m2.find()) {
            try { return Double.parseDouble(m2.group(1)); } catch (Exception e) {}
        }
        return 0;
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private static String guessMerchant(String title, String full) {
        if (title != null && title.length() > 0 && title.length() <= 20) return title;
        return full.length() > 24 ? full.substring(0, 24) : full;
    }

    private static String guessCategory(String s) {
        if (containsAny(s, "外卖", "餐", "饭", "奶茶", "咖啡", "饿了么", "美团", "麦当劳", "肯德基", "瑞幸")) return "餐饮";
        if (containsAny(s, "打车", "滴滴", "地铁", "公交", "加油", "停车", "火车", "机票", "高铁")) return "交通";
        if (containsAny(s, "超市", "购物", "淘宝", "京东", "拼多多", "天猫", "商场", "便利店")) return "购物";
        if (containsAny(s, "房租", "水电", "燃气", "话费", "宽带", "物业")) return "居住";
        if (containsAny(s, "电影", "游戏", "视频", "音乐", "会员", "KTV", "酒吧")) return "娱乐";
        if (containsAny(s, "转账", "红包")) return "转账";
        if (containsAny(s, "收款", "退款", "工资", "收入", "到账")) return "收入";
        return "其他";
    }
}
