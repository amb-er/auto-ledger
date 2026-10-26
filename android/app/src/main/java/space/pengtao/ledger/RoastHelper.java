package space.pengtao.ledger;

import android.content.Context;

import java.util.Locale;

/** 记账后异步调用 AI 生成毒舌辣评并回写。 */
public class RoastHelper {

    /** 只对支出生成辣评；后台线程执行，不阻塞 UI。 */
    public static void roastAsync(Context ctx, LedgerDbHelper db, long id, Transaction t) {
        if (t == null || t.amount >= 0) return; // 收入不辣评
        if (!AppSettings.isRoastEnabled(ctx)) return; // 辣评开关关闭
        new Thread(() -> {
            String amount = String.format(Locale.CHINA, "%.2f", Math.abs(t.amount));
            String comment = AiClient.roast(ctx, amount,
                    t.category == null ? "其他" : t.category,
                    t.merchant == null ? "" : t.merchant);
            if (comment != null && !comment.isEmpty()) {
                db.updateComment(id, comment);
            }
        }).start();
    }
}
