package space.pengtao.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 家庭账单页：从 Worker 拉取家庭成员消费汇总 + 明细 + 评论。 */
public class FamilyActivity extends Activity {

    private LinearLayout membersContainer;
    private LinearLayout detailContainer;
    private TextView totalText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family);
        membersContainer = findViewById(R.id.members_container);
        detailContainer = findViewById(R.id.detail_container);
        totalText = findViewById(R.id.total_text);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        load();
    }

    private void load() {
        String family = AppSettings.getFamilyCode(this);
        String member = AppSettings.getMemberName(this);
        if (family.isEmpty() || member.isEmpty()) {
            totalText.setText("请先在「设置 → 家庭」里填成员昵称和家庭码");
            return;
        }
        totalText.setText("加载中…");
        new Thread(() -> {
            JSONArray summary = FamilyClient.fetchSummary(this);
            JSONArray transactions = FamilyClient.fetchTransactions(this);
            runOnUiThread(() -> render(summary, transactions));
        }).start();
    }

    private void render(JSONArray summary, JSONArray transactions) {
        double total = 0;
        membersContainer.removeAllViews();
        if (summary != null) {
            for (int i = 0; i < summary.length(); i++) {
                try {
                    JSONObject o = summary.getJSONObject(i);
                    String m = o.optString("member", "未知");
                    double v = o.optDouble("total", 0);
                    total += v;
                    addRow(membersContainer, m, money(-v), "", 0);
                } catch (Exception ignored) {}
            }
        }
        totalText.setText("家庭总支出 ¥" + money(-total));

        detailContainer.removeAllViews();
        if (transactions != null) {
            for (int i = 0; i < transactions.length(); i++) {
                try {
                    JSONObject o = transactions.getJSONObject(i);
                    long tid = o.optLong("id", 0);
                    String member = o.optString("member", "");
                    String merchant = o.optString("merchant", "");
                    String remark = o.optString("remark", "");
                    double amount = o.optDouble("amount", 0);
                    long time = o.optLong("time", 0);
                    String title = member + (merchant.isEmpty() ? "" : " · " + merchant);
                    String value = (amount >= 0 ? "+" : "-") + "¥" + money(Math.abs(amount)) + "  " + fmtTime(time);
                    addRow(detailContainer, title, value, remark, tid);
                } catch (Exception ignored) {}
            }
        }
        if (transactions == null || transactions.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无家庭账单数据");
            empty.setTextColor(getResources().getColor(R.color.text_sec));
            empty.setPadding(0, dp(8), 0, dp(8));
            detailContainer.addView(empty);
        }
    }

    private void addRow(LinearLayout parent, String title, String value, String remark, long tid) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_setting, parent, false);
        ((TextView) row.findViewById(R.id.setting_title)).setText(title);
        ((TextView) row.findViewById(R.id.setting_value)).setText(value);
        TextView sub = row.findViewById(R.id.setting_sub);
        if (remark != null && !remark.isEmpty()) {
            sub.setVisibility(View.VISIBLE);
            sub.setText("备注：" + remark);
        }
        if (tid > 0) {
            row.setOnClickListener(v -> showCommentDialog(tid, title, value));
        }
        parent.addView(row);
    }

    private void showCommentDialog(final long tid, final String title, final String value) {
        new Thread(() -> {
            JSONArray comments = FamilyClient.fetchComments(this);
            runOnUiThread(() -> showCommentDialogUi(tid, title, value, comments));
        }).start();
    }

    private void showCommentDialogUi(final long tid, String title, String value, JSONArray comments) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView head = new TextView(this);
        head.setText(title + "\n" + value);
        head.setTextColor(getResources().getColor(R.color.text_main));
        head.setTextSize(15);
        box.addView(head);

        if (comments != null) {
            boolean has = false;
            for (int i = 0; i < comments.length(); i++) {
                try {
                    JSONObject c = comments.getJSONObject(i);
                    if (c.optLong("transaction_id", 0) != tid) continue;
                    has = true;
                    TextView ct = new TextView(this);
                    ct.setText(c.optString("member", "") + "：" + c.optString("content", ""));
                    ct.setTextColor(getResources().getColor(R.color.text_main));
                    ct.setTextSize(14);
                    ct.setPadding(0, dp(8), 0, 0);
                    box.addView(ct);
                } catch (Exception ignored) {}
            }
            if (!has) {
                TextView none = new TextView(this);
                none.setText("（还没有评论）");
                none.setTextColor(getResources().getColor(R.color.text_sec));
                none.setTextSize(13);
                none.setPadding(0, dp(8), 0, 0);
                box.addView(none);
            }
        }

        final EditText input = new EditText(this);
        input.setHint("说点什么…");
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("评论")
                .setView(box)
                .setPositiveButton("发送", (d, w) -> {
                    String content = input.getText().toString().trim();
                    if (!content.isEmpty()) {
                        FamilyClient.submitComment(this, tid, content);
                        Toast.makeText(this, "已发送", Toast.LENGTH_SHORT).show();
                        load();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String money(double v) {
        return String.format(Locale.CHINA, "%.2f", v);
    }

    private String fmtTime(long ms) {
        if (ms <= 0) return "";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
