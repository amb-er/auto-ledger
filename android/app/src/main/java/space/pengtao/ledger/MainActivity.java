package space.pengtao.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private static final int REQ_IMPORT_CSV = 100;

    private LedgerDbHelper db;
    private TextView monthExpenseText;
    private TextView incomeText;
    private TextView balanceText;
    private ListView listView;
    private View emptyView;
    private TransactionAdapter adapter;
    private String searchKeyword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        db = new LedgerDbHelper(this);
        requestNotificationPermission();

        monthExpenseText = findViewById(R.id.month_expense);
        incomeText = findViewById(R.id.month_income);
        balanceText = findViewById(R.id.month_balance);
        listView = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty_view);

        adapter = new TransactionAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.fab).setOnClickListener(v -> showAddDialog());
        findViewById(R.id.btn_permission).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        findViewById(R.id.btn_search).setOnClickListener(v -> showSearchDialog());
        findViewById(R.id.btn_family).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FamilyActivity.class)));
        findViewById(R.id.stats_card).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, StatisticsActivity.class)));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Object row = adapter.getItem(position);
            if (row instanceof Transaction) {
                Transaction t = (Transaction) row;
                if (t.status == 0) {
                    db.confirm(t.id);
                    refresh();
                    Toast.makeText(this, "已确认", Toast.LENGTH_SHORT).show();
                }
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Object row = adapter.getItem(position);
            if (row instanceof Transaction) showItemMenu((Transaction) row);
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Transaction> list = searchKeyword == null ? db.list(200) : db.search(searchKeyword);
        adapter.setData(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        double expense = db.monthExpense(monthStart());
        double income = db.monthIncome(monthStart());
        monthExpenseText.setText("¥" + money(expense));
        incomeText.setText("收入 ¥" + money(income));
        balanceText.setText("结余 ¥" + money(income - expense));
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

    private void showAddDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null);
        EditText amount = v.findViewById(R.id.input_amount);
        EditText note = v.findViewById(R.id.input_note);
        RadioGroup typeGroup = v.findViewById(R.id.type_group);
        RadioGroup categoryGroup = v.findViewById(R.id.category_group);

        typeGroup.setOnCheckedChangeListener((g, id) ->
                categoryGroup.setVisibility(id == R.id.type_income ? View.GONE : View.VISIBLE));

        new AlertDialog.Builder(this)
                .setTitle("记一笔")
                .setView(v)
                .setPositiveButton("保存", (d, w) -> {
                    String as = amount.getText().toString().trim();
                    if (as.isEmpty()) {
                        Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double val;
                    try {
                        val = Double.parseDouble(as);
                    } catch (Exception e) {
                        return;
                    }
                    boolean income = typeGroup.getCheckedRadioButtonId() == R.id.type_income;
                    String nt = note.getText().toString().trim();
                    Transaction t = new Transaction();
                    t.amount = income ? val : -val;
                    t.category = income ? "收入" : categoryOf(categoryGroup.getCheckedRadioButtonId());
                    t.merchant = nt.isEmpty() ? (income ? "收入" : "手动记账") : nt;
                    t.source = "manual";
                    t.note = nt;
                    t.remark = nt;
                    t.time = System.currentTimeMillis();
                    t.member = AppSettings.getMemberName(MainActivity.this);
                    long id = db.insert(t);
                    RoastHelper.roastAsync(MainActivity.this, db, id, t);
                    FamilyClient.upload(MainActivity.this, t);
                    refresh();
                    Toast.makeText(this, "已记", Toast.LENGTH_SHORT).show();                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String categoryOf(int id) {
        if (id == R.id.cat_food) return "餐饮";
        if (id == R.id.cat_traffic) return "交通";
        if (id == R.id.cat_shopping) return "购物";
        if (id == R.id.cat_home) return "居住";
        if (id == R.id.cat_fun) return "娱乐";
        return "其他";
    }

    private void showPermissionDialog() {
        boolean notif = isNotificationEnabled();
        boolean acc = isAccessibilityEnabled();
        String log = PayNotificationListener.readLog(this);
        int logCount = log.isEmpty() ? 0 : log.split("\\[通知\\]", -1).length - 1;
        String msg = "自动记账依赖两个权限：\n\n"
                + "1. 通知使用权（自动记支付宝/微信账单）\n      "
                + (notif ? "✅ 已开启" : "❌ 未开启") + "\n"
                + "2. 无障碍（补读微信金额）\n      "
                + (acc ? "✅ 已开启" : "❌ 未开启") + "\n\n"
                + "最近收到通知：" + logCount + " 条";
        new AlertDialog.Builder(this)
                .setTitle("自动记账状态")
                .setMessage(msg)
                .setPositiveButton("通知使用权", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNeutralButton("无障碍", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("更多", (d, w) -> showMoreMenu())
                .show();
    }

    private void showMoreMenu() {
        new AlertDialog.Builder(this)
                .setItems(new String[]{"AI 辣评设置", "查看通知日志", "查看无障碍日志", "查看 AI 日志", "导入微信账单"}, (d, which) -> {
                    if (which == 0) showAiSettingsDialog();
                    else if (which == 1) showNotifLog();
                    else if (which == 2) showAccessLog();
                    else if (which == 3) showAiLog();
                    else importCsv();
                })
                .show();
    }

    private void showAiLog() {
        String log = AiClient.readAiLog(this);
        if (log.isEmpty()) log = "（暂无记录）\n\n说明：AI 辣评还没被触发过。";
        final String content = log;
        new AlertDialog.Builder(this)
                .setTitle("AI 调用日志")
                .setMessage(content)
                .setPositiveButton("复制", (d, w) -> copyToClipboard(content))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showAccessLog() {
        String log = LedgerAccessibilityService.readAccessLog(this);
        if (log.isEmpty()) log = "（暂无记录）\n\n说明：还没识别到微信的账单/转账/收款详情页。";
        final String content = log;
        new AlertDialog.Builder(this)
                .setTitle("无障碍识别日志")
                .setMessage(content)
                .setPositiveButton("复制", (d, w) -> copyToClipboard(content))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showNotifLog() {
        String log = PayNotificationListener.readLog(this);
        if (log.isEmpty()) log = "（暂无记录）\n\n说明：通知监听服务还没收到任何微信/支付宝通知。";
        final String content = log;
        new AlertDialog.Builder(this)
                .setTitle("最近收到的通知")
                .setMessage(content)
                .setPositiveButton("复制", (d, w) -> copyToClipboard(content))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void importCsv() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT_CSV);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_CSV || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        new Thread(() -> {
            try {
                String content = readText(uri);
                List<Transaction> list = CsvImporter.parse(content);
                List<Transaction> toInsert = new ArrayList<>();
                for (Transaction t : list) {
                    if (!db.hasNote(t.note)) toInsert.add(t);
                }
                int count = db.insertBatch(toInsert);
                runOnUiThread(() -> {
                    refresh();
                    Toast.makeText(this, "导入 " + count + " 条（跳过重复 "
                            + (list.size() - count) + " 条）", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String readText(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        byte[] data = bos.toByteArray();
        int offset = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        String content = new String(data, offset, data.length - offset, "UTF-8");
        if (!content.contains("交易") && !content.contains("微信")) {
            content = new String(data, offset, data.length - offset, "GBK");
        }
        return content;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 200);
            }
        }
    }

    private void copyToClipboard(String text) {        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("通知日志", text));
        Toast.makeText(this, "已复制，去微信粘贴给我", Toast.LENGTH_SHORT).show();
    }

    private boolean isNotificationEnabled() {
        String pkg = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (flat == null) return false;
        for (String s : flat.split(":")) {
            if (s.startsWith(pkg + "/")) return true;
        }
        return false;
    }

    private boolean isAccessibilityEnabled() {
        String pkg = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_accessibility_services");
        if (flat == null) return false;
        for (String s : flat.split(":")) {
            if (s.startsWith(pkg + "/")) return true;
        }
        return false;
    }

    private void showAiSettingsDialog() {
        SharedPreferences sp = getSharedPreferences(AiClient.SP_NAME, MODE_PRIVATE);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_ai_config, null);
        EditText endpoint = v.findViewById(R.id.ai_endpoint);
        EditText key = v.findViewById(R.id.ai_key);
        EditText model = v.findViewById(R.id.ai_model);
        endpoint.setText(sp.getString("endpoint", ""));
        key.setText(sp.getString("api_key", ""));
        model.setText(sp.getString("model", "deepseek-chat"));

        new AlertDialog.Builder(this)
                .setTitle("AI 辣评设置")
                .setView(v)
                .setPositiveButton("保存", (d, w) -> {
                    sp.edit()
                            .putString("endpoint", endpoint.getText().toString().trim())
                            .putString("api_key", key.getText().toString().trim())
                            .putString("model", model.getText().toString().trim())
                            .apply();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSearchDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_search, null);
        EditText input = v.findViewById(R.id.search_input);
        if (searchKeyword != null) input.setText(searchKeyword);
        new AlertDialog.Builder(this)
                .setTitle("搜索账目")
                .setView(v)
                .setPositiveButton("搜索", (d, w) -> {
                    String kw = input.getText().toString().trim();
                    searchKeyword = kw.isEmpty() ? null : kw;
                    refresh();
                })
                .setNegativeButton("清空", (d, w) -> {
                    searchKeyword = null;
                    refresh();
                })
                .show();
    }

    private void showItemMenu(Transaction t) {
        String title = (t.merchant == null || t.merchant.isEmpty()) ? "账目操作" : t.merchant;
        if (t.status == 0) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(new String[]{"确认", "编辑", "删除"}, (d, which) -> {
                        if (which == 0) {
                            db.confirm(t.id);
                            refresh();
                            Toast.makeText(this, "已确认", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) showEditDialog(t);
                        else confirmDelete(t);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(new String[]{"编辑", "删除"}, (d, which) -> {
                        if (which == 0) showEditDialog(t);
                        else confirmDelete(t);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    private void showEditDialog(Transaction t) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null);
        EditText amount = v.findViewById(R.id.input_amount);
        EditText note = v.findViewById(R.id.input_note);
        RadioGroup typeGroup = v.findViewById(R.id.type_group);
        RadioGroup categoryGroup = v.findViewById(R.id.category_group);

        boolean income = t.amount > 0;
        typeGroup.check(income ? R.id.type_income : R.id.type_expense);
        amount.setText(String.format(Locale.CHINA, "%.2f", Math.abs(t.amount)));
        note.setText(t.note == null ? "" : t.note);
        categoryGroup.setVisibility(income ? View.GONE : View.VISIBLE);
        if (!income) checkCategory(categoryGroup, t.category);

        typeGroup.setOnCheckedChangeListener((g, id) ->
                categoryGroup.setVisibility(id == R.id.type_income ? View.GONE : View.VISIBLE));

        new AlertDialog.Builder(this)
                .setTitle("编辑账目")
                .setView(v)
                .setPositiveButton("保存", (d, w) -> {
                    String as = amount.getText().toString().trim();
                    if (as.isEmpty()) {
                        Toast.makeText(this, "请输入金额", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double val;
                    try {
                        val = Double.parseDouble(as);
                    } catch (Exception e) {
                        return;
                    }
                    boolean inc = typeGroup.getCheckedRadioButtonId() == R.id.type_income;
                    String nt = note.getText().toString().trim();
                    t.amount = inc ? val : -val;
                    t.category = inc ? "收入" : categoryOf(categoryGroup.getCheckedRadioButtonId());
                    t.merchant = nt.isEmpty() ? (inc ? "收入" : "手动记账") : nt;
                    t.note = nt;
                    t.remark = nt;
                    db.update(t);
                    refresh();
                    Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(Transaction t) {
        new AlertDialog.Builder(this)
                .setTitle("删除")
                .setMessage("确定删除这条账目吗？")
                .setPositiveButton("删除", (d, w) -> {
                    db.delete(t.id);
                    refresh();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void checkCategory(RadioGroup group, String cat) {
        if (cat == null) {
            group.check(R.id.cat_other);
            return;
        }
        if (cat.contains("餐饮")) group.check(R.id.cat_food);
        else if (cat.contains("交通")) group.check(R.id.cat_traffic);
        else if (cat.contains("购物")) group.check(R.id.cat_shopping);
        else if (cat.contains("居住")) group.check(R.id.cat_home);
        else if (cat.contains("娱乐")) group.check(R.id.cat_fun);
        else group.check(R.id.cat_other);
    }

    class TransactionAdapter extends BaseAdapter {
        private final List<Object> rows = new ArrayList<>();
        private final List<Transaction> data = new ArrayList<>();

        void setData(List<Transaction> d) {
            data.clear();
            data.addAll(d);
            rows.clear();
            String lastKey = null;
            for (Transaction t : data) {
                String key = dayKey(t.time);
                if (!key.equals(lastKey)) {
                    rows.add(headerLabel(t.time));
                    lastKey = key;
                }
                rows.add(t);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getViewTypeCount() { return 2; }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Object row = rows.get(position);
            if (row instanceof String) {
                if (convert == null) {
                    convert = LayoutInflater.from(MainActivity.this)
                            .inflate(R.layout.item_section_header, parent, false);
                }
                ((TextView) convert).setText((String) row);
                return convert;
            }

            if (convert == null) {
                convert = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_transaction, parent, false);
            }
            Transaction t = (Transaction) row;
            TextView emoji = convert.findViewById(R.id.tx_emoji);
            TextView merchant = convert.findViewById(R.id.tx_merchant);
            TextView meta = convert.findViewById(R.id.tx_meta);
            TextView comment = convert.findViewById(R.id.tx_comment);
            TextView amount = convert.findViewById(R.id.tx_amount);

            String cat = t.category == null ? "其他" : t.category;
            emoji.setText(emojiFor(cat));
            Drawable bg = emoji.getBackground();
            if (bg != null) {
                bg.mutate().setTint(colorFor(cat));
            }

            String m = (t.merchant == null || t.merchant.isEmpty()) ? "未分类" : t.merchant;
            merchant.setText(t.status == 0 ? "⏳ " + m : m);
            meta.setText(sourceLabel(t.source) + " · " + cat + " · " + fmtTime(t.time)
                    + (t.status == 0 ? " · 待确认" : ""));

            boolean income = t.amount > 0;
            String sign = income ? "+" : "-";
            amount.setText(sign + "¥" + String.format(Locale.CHINA, "%.2f", Math.abs(t.amount)));
            amount.setTextColor(t.status == 0 ? 0xFFE8A020 :
                    (income ? 0xFF18A058 : 0xFFE5484D));
            if (t.comment != null && !t.comment.isEmpty()) {
                comment.setText("🔥 " + t.comment);
                comment.setVisibility(View.VISIBLE);
            } else {
                comment.setVisibility(View.GONE);
            }
            return convert;
        }
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
        if (cat.contains("餐饮")) return 0xFFFFE6C7;
        if (cat.contains("交通")) return 0xFFD8E8FF;
        if (cat.contains("购物")) return 0xFFEBDDFF;
        if (cat.contains("居住")) return 0xFFEADFC9;
        if (cat.contains("娱乐")) return 0xFFFFDDE4;
        if (cat.contains("收入")) return 0xFFD2F5E3;
        if (cat.contains("转账")) return 0xFFEBDDFF;
        return 0xFFE8EBEF;
    }

    private String sourceLabel(String src) {
        if ("alipay".equals(src)) return "支付宝";
        if ("wechat".equals(src)) return "微信";
        if ("manual".equals(src)) return "手动";
        return src == null ? "" : src;
    }

    private String fmtTime(long ms) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private String dayKey(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
    }

    private String headerLabel(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);

        Calendar now = Calendar.getInstance();
        boolean today = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
        if (today) return "今天";

        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = yesterday.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && yesterday.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
        if (isYesterday) return "昨天";

        String[] week = {"日", "一", "二", "三", "四", "五", "六"};
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH)
                + "日 周" + week[c.get(Calendar.DAY_OF_WEEK) - 1];
    }
}
