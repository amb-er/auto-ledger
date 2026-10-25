package space.pengtao.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends Activity {

    private LedgerDbHelper db;
    private PieChartView pieChart;
    private LinearLayout legendContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        db = new LedgerDbHelper(this);
        pieChart = findViewById(R.id.pie_chart);
        legendContainer = findViewById(R.id.legend);
        findViewById(R.id.btn_export).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btn_ai_report).setOnClickListener(v -> generateMonthlyReport());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        long start = monthStart();
        double expense = db.monthExpense(start);
        double income = db.monthIncome(start);
        ((TextView) findViewById(R.id.stats_expense)).setText("¥" + money(expense));
        ((TextView) findViewById(R.id.stats_income)).setText("收入 ¥" + money(income));
        ((TextView) findViewById(R.id.stats_balance)).setText("结余 ¥" + money(income - expense));

        Map<String, Double> stats = db.categoryStats(start);
        List<PieChartView.Slice> slices = new ArrayList<>();
        legendContainer.removeAllViews();
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            String cat = e.getKey();
            double v = e.getValue();
            slices.add(new PieChartView.Slice(colorFor(cat), (float) v));

            View row = getLayoutInflater().inflate(R.layout.item_category_stat, legendContainer, false);
            ((TextView) row.findViewById(R.id.cat_emoji)).setText(emojiFor(cat));
            ((TextView) row.findViewById(R.id.cat_name)).setText(cat);
            ((TextView) row.findViewById(R.id.cat_amount)).setText("¥" + money(v));
            ((TextView) row.findViewById(R.id.cat_percent)).setText(
                    expense > 0 ? String.format(Locale.CHINA, "%.0f%%", v / expense * 100) : "0%");
            legendContainer.addView(row);
        }
        pieChart.setSlices(slices);
    }

    private void exportCsv() {
        List<Transaction> list = db.list(100000);
        if (list.isEmpty()) {
            Toast.makeText(this, "没有账目可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("时间,类型,分类,商户,金额,来源,备注\n");
        for (Transaction t : list) {
            sb.append(fmtTime(t.time)).append(',')
                    .append(t.amount > 0 ? "收入" : "支出").append(',')
                    .append(csv(t.category)).append(',')
                    .append(csv(t.merchant)).append(',')
                    .append(String.format(Locale.CHINA, "%.2f", t.amount)).append(',')
                    .append(sourceLabel(t.source)).append(',')
                    .append(csv(t.note)).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "记账本导出");
        send.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(send, "导出账本"));
    }

    private void generateMonthlyReport() {
        SharedPreferences sp = getSharedPreferences(AiClient.SP_NAME, MODE_PRIVATE);
        if (sp.getString("api_key", "").isEmpty() || sp.getString("endpoint", "").isEmpty()) {
            Toast.makeText(this, "请先在主界面「AI 辣评设置」配置端点 + Key", Toast.LENGTH_LONG).show();
            return;
        }

        long start = monthStart();
        double expense = db.monthExpense(start);
        double income = db.monthIncome(start);
        Map<String, Double> stats = db.categoryStats(start);
        List<Transaction> list = db.list(10000);

        StringBuilder data = new StringBuilder();
        data.append("本月总支出 ").append(money(expense)).append(" 元，总收入 ").append(money(income)).append(" 元。\n");
        data.append("分类支出：");
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            data.append(e.getKey()).append(" ").append(money(e.getValue())).append(" 元、");
        }
        data.append("\n最大几笔消费：");
        for (Transaction t : topExpenses(list, 5)) {
            data.append(t.merchant).append(" ").append(money(Math.abs(t.amount))).append(" 元、");
        }

        Toast.makeText(this, "正在生成毒舌报告…", Toast.LENGTH_SHORT).show();
        final String payload = data.toString();
        new Thread(() -> {
            String report = AiClient.generateReport(this, payload);
            runOnUiThread(() -> {
                if (report == null || report.isEmpty()) {
                    Toast.makeText(this, "生成失败，请检查 AI 配置和网络", Toast.LENGTH_LONG).show();
                } else {
                    showReport(report);
                }
            });
        }).start();
    }

    private List<Transaction> topExpenses(List<Transaction> list, int n) {
        List<Transaction> expenses = new ArrayList<>();
        for (Transaction t : list) if (t.amount < 0) expenses.add(t);
        expenses.sort((a, b) -> Double.compare(a.amount, b.amount));
        return expenses.size() > n ? expenses.subList(0, n) : expenses;
    }

    private void showReport(String report) {
        new AlertDialog.Builder(this)
                .setTitle("本月毒舌消费报告")
                .setMessage(report)
                .setPositiveButton("分享", (d, w) -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, "我的本月消费报告：\n" + report);
                    startActivity(Intent.createChooser(send, "分享报告"));
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /** CSV 字段转义：含逗号/引号时加双引号。 */
    private String csv(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String money(double v) {
        return String.format(Locale.CHINA, "%.2f", v);
    }

    private long monthStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private String emojiFor(String cat) {
        if (cat.contains("餐饮")) return "🍜";
        if (cat.contains("交通")) return "🚌";
        if (cat.contains("购物")) return "🛒";
        if (cat.contains("居住")) return "🏠";
        if (cat.contains("娱乐")) return "🎮";
        if (cat.contains("转账")) return "💸";
        if (cat.contains("收入")) return "💰";
        return "💳";
    }

    private int colorFor(String cat) {
        if (cat.contains("餐饮")) return 0xFFFFB74D;
        if (cat.contains("交通")) return 0xFF64B5F6;
        if (cat.contains("购物")) return 0xFFBA68C8;
        if (cat.contains("居住")) return 0xFFA1887F;
        if (cat.contains("娱乐")) return 0xFFF06292;
        if (cat.contains("收入")) return 0xFF4DB6AC;
        if (cat.contains("转账")) return 0xFF9575CD;
        return 0xFFB0BEC5;
    }

    private String sourceLabel(String src) {
        if ("alipay".equals(src)) return "支付宝";
        if ("wechat".equals(src)) return "微信";
        if ("manual".equals(src)) return "手动";
        return src == null ? "" : src;
    }

    private String fmtTime(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }
}
