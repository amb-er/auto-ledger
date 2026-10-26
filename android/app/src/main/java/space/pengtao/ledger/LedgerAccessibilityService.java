package space.pengtao.ledger;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无障碍服务：微信支付/转账/收款金额补读。
 * 详情页（账单/转账/收款/交易/红包详情）识别 → 直接确认（status=1）；
 * 聊天界面兜底识别「转账/收款/红包 + 金额」→ 待确认（status=0）。
 * 去重用「画面指纹」：窗口里所有金额排序后作为指纹，画面没变就跳过。
 */
public class LedgerAccessibilityService extends AccessibilityService {

    private static final String WECHAT = "com.tencent.mm";

    private static final Pattern AMOUNT = Pattern.compile(
            "[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)|([0-9]+(?:\\.[0-9]{1,2})?)\\s*元");

    private static String lastFingerprint = "";
    private static long lastProcessTime = 0;
    private static long lastLogTime = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        logAccess("[无障碍] ✅ 服务已连接/激活");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!AppSettings.isAutoEnabled(this)) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (!WECHAT.equals(pkg)) return;

        int type = event.getEventType();
        boolean stateChange = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        boolean contentChange = type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        if (!stateChange && !contentChange) return;

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        AccessibilityNodeInfo root = source;
        AccessibilityNodeInfo parent;
        while ((parent = root.getParent()) != null) {
            root = parent;
        }

        List<String> texts = new ArrayList<>();
        collectText(root, texts, 0);
        String all = join(texts, 60);

        long now = System.currentTimeMillis();
        if (stateChange || (contentChange && now - lastLogTime > 5000)) {
            lastLogTime = now;
            logAccess("[无障碍] 微信窗口文本(" + texts.size() + "):\n" + all);
        }

        boolean isDetail = all.contains("账单详情") || all.contains("转账详情")
                || all.contains("收款详情") || all.contains("交易详情")
                || all.contains("红包详情");

        boolean hasTransfer;
        if (isDetail) {
            hasTransfer = true; // 详情页直接处理
        } else {
            hasTransfer = all.contains("转账") || all.contains("收款") || all.contains("红包");
        }
        if (!hasTransfer) return;

        // 画面指纹去重
        String fp = fingerprintOf(all);
        if (fp.equals(lastFingerprint)) return;
        if (now - lastProcessTime < 30000) return;
        lastFingerprint = fp;
        lastProcessTime = now;

        double amount = extractAmount(all);
        if (amount <= 0) {
            logAccess("[无障碍] 有转账/详情但没提取到金额");
            return;
        }

        boolean isOut = contains(all, "发起", "转出", "付款", "支付", "消费", "扣款");
        boolean isIn = contains(all, "收款", "收到", "到账", "退款", "入账", "转入");
        boolean isIncome = isIn && !isOut;

        double signed = isIncome ? amount : -amount;

        LedgerDbHelper db = new LedgerDbHelper(this);
        String money = String.format(java.util.Locale.CHINA, "%.2f", amount);
        long pendingId = db.fillPending(signed, "wechat");
        if (pendingId <= 0) {
            Transaction t = new Transaction();
            t.amount = signed;
            t.category = guessCategory(all);
            t.merchant = guessMerchant(texts);
            t.source = "wechat";
            t.note = all;
            t.status = isDetail ? 1 : 0; // 详情页直接确认，聊天界面待确认
            t.time = now;
            t.member = AppSettings.getMemberName(this);
            long id = db.insert(t);
            RoastHelper.roastAsync(this, db, id, t);
            FamilyClient.upload(this, t);
            NotifyHelper.notifyTransaction(this, id,
                    "记账本识别到一笔" + (isIncome ? "收款" : "支出"),
                    (isIncome ? "+" : "-") + "¥" + money
                            + (isDetail ? "（已确认）" : "，点击确认"));
            logAccess("[无障碍] ✅ 已入库 id=" + id + " 金额=" + signed
                    + " 收入=" + isIncome + " 详情页=" + isDetail
                    + " 状态=" + (isDetail ? "已确认" : "待确认"));
        } else {
            Transaction rt = new Transaction();
            rt.amount = signed;
            rt.category = guessCategory(all);
            rt.merchant = guessMerchant(texts);
            rt.source = "wechat";
            rt.member = AppSettings.getMemberName(this);
            RoastHelper.roastAsync(this, db, pendingId, rt);
            FamilyClient.upload(this, rt);
            NotifyHelper.notifyTransaction(this, pendingId,
                    "记账本补全了微信金额",
                    (isIncome ? "+" : "-") + "¥" + money);
            logAccess("[无障碍] ✅ 已回填待补 id=" + pendingId + " 金额=" + signed + " 收入=" + isIncome);
        }
    }

    /** 窗口画面指纹：所有金额排序后的列表字符串。 */
    private String fingerprintOf(String all) {
        Matcher m = AMOUNT.matcher(all);
        List<Double> amounts = new ArrayList<>();
        while (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                amounts.add(Double.parseDouble(g));
            } catch (Exception ignored) {}
        }
        Collections.sort(amounts);
        return amounts.toString();
    }

    private void collectText(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || out.size() > 200) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) out.add(t.toString());
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            if (c != null) collectText(c, out, depth + 1);
        }
    }

    private String join(List<String> list, int maxCount) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(list.size(), maxCount);
        for (int i = 0; i < n; i++) {
            String s = list.get(i);
            if (s.length() > 24) s = s.substring(0, 24);
            sb.append("[").append(s).append("] ");
        }
        if (list.size() > maxCount) sb.append("...");
        return sb.toString();
    }

    private double extractAmount(String all) {
        Matcher m = AMOUNT.matcher(all);
        double best = 0;
        while (m.find()) {
            String g = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                double v = Double.parseDouble(g);
                if (v > best) best = v;
            } catch (Exception ignored) {}
        }
        return best;
    }

    private void logAccess(String entry) {
        try {
            SharedPreferences sp = getSharedPreferences("access_log", MODE_PRIVATE);
            String existing = sp.getString("log", "");
            String combined = entry + (existing.isEmpty() ? "" : "\n\n" + existing);
            if (combined.length() > 10000) combined = combined.substring(0, 10000);
            sp.edit().putString("log", combined).apply();
        } catch (Exception ignored) {}
    }

    public static String readAccessLog(Context ctx) {
        return ctx.getSharedPreferences("access_log", Context.MODE_PRIVATE).getString("log", "");
    }

    private boolean contains(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    private String guessCategory(String s) {
        if (contains(s, "外卖", "餐", "饭", "奶茶", "咖啡", "美团", "饿了么", "麦当劳", "肯德基")) return "餐饮";
        if (contains(s, "打车", "滴滴", "地铁", "公交", "加油", "停车", "火车", "机票", "高铁")) return "交通";
        if (contains(s, "超市", "购物", "淘宝", "京东", "拼多多", "天猫", "商场", "便利店")) return "购物";
        if (contains(s, "房租", "水电", "燃气", "话费", "宽带", "物业")) return "居住";
        if (contains(s, "电影", "游戏", "视频", "音乐", "会员", "KTV")) return "娱乐";
        if (contains(s, "转账", "红包")) return "转账";
        if (contains(s, "收款", "退款", "到账")) return "收入";
        return "其他";
    }

    private String guessMerchant(List<String> texts) {
        for (String t : texts) {
            if (t.length() >= 2 && t.length() <= 20 &&
                    !t.contains("¥") && !t.contains("￥") && !t.contains("元") &&
                    !t.contains("账单") && !t.contains("详情") &&
                    !t.contains("支付") && !t.contains("交易") && !t.contains("转账")) {
                return t;
            }
        }
        return "微信";
    }

    @Override
    public void onInterrupt() {
    }
}
