package space.pengtao.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends Activity {

    private static final int REQ_IMPORT_CSV = 100;
    private static final int REQ_RESTORE = 101;

    private LedgerDbHelper db;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        db = new LedgerDbHelper(this);
        container = findViewById(R.id.settings_container);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        buildSettings();
    }

    private void buildSettings() {
        container.removeAllViews();

        addHeader("权限");
        addRow("通知使用权", isNotificationEnabled() ? "✅ 已开启" : "❌ 未开启",
                v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        addRow("无障碍", isAccessibilityEnabled() ? "✅ 已开启" : "❌ 未开启",
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        addHeader("自动记账");
        addSwitchRow("自动记账总开关", AppSettings.isAutoEnabled(this), (btn, checked) ->
                AppSettings.setAutoEnabled(this, checked));

        addHeader("AI");
        addSwitchRow("AI 毒舌辣评", AppSettings.isRoastEnabled(this), (btn, checked) ->
                AppSettings.setRoastEnabled(this, checked));
        addRow("AI 辣评设置", "端点 / Key / 模型", v -> showAiConfigDialog());

        addHeader("外观");
        addRow("深色模式", themeLabel(), v -> showThemeDialog());

        addHeader("家庭");
        addRow("成员昵称", memberLabel(), v -> showMemberNameDialog());
        addRow("家庭码", familyCodeLabel(), v -> showFamilyCodeDialog());
        addRow("服务器地址", AppSettings.getWorkerUrl(this), v -> showWorkerDialog());
        addRow("同步历史账目", "把本地已有账目上传到家庭", v -> syncHistory());

        addHeader("数据");
        addRow("导入微信账单", "CSV", v -> importCsv());
        addRow("导出账本", "CSV", v -> exportCsv());
        addRow("备份账目", "导出 JSON", v -> backupData());
        addRow("恢复账目", "导入 JSON", v -> restoreData());
        addRow("清除全部账目", "", v -> clearData());

        addHeader("日志");
        addRow("查看日志", "", v -> showLogs());
        addRow("导出日志到文件", "一键导出到 Download", v -> exportLogFile());
        addRow("清空日志", "通知 / 无障碍 / AI", v -> clearLogs());

        addHeader("关于");
        addRow("版本", "2.2.0", v -> showAbout());
    }

    // ---------- 家庭设置 ----------

    private String memberLabel() {
        String m = AppSettings.getMemberName(this);
        return m.isEmpty() ? "未设置" : m;
    }

    private String familyCodeLabel() {
        String c = AppSettings.getFamilyCode(this);
        return c.isEmpty() ? "未设置" : c;
    }

    private void showMemberNameDialog() {
        showTextInputDialog("成员昵称", AppSettings.getMemberName(this), "如：爸爸 / 妈妈 / 小明",
                v -> AppSettings.setMemberName(this, v));
    }

    private void showFamilyCodeDialog() {
        final EditText input = new EditText(this);
        input.setText(AppSettings.getFamilyCode(this));
        input.setHint("点「生成新码」由系统创建唯一码");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("家庭码")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    AppSettings.setFamilyCode(this, input.getText().toString().trim());
                    buildSettings();
                })
                .setNeutralButton("生成新码", (d, w) -> generateFamilyCode(d))
                .setNegativeButton("取消", null)
                .show();
    }

    private void generateFamilyCode(final android.content.DialogInterface dialog) {
        new Thread(() -> {
            String code = FamilyClient.createFamily(this);
            runOnUiThread(() -> {
                if (code != null && !code.isEmpty()) {
                    AppSettings.setFamilyCode(this, code);
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("家庭码", code));
                    dialog.dismiss();
                    buildSettings();
                    Toast.makeText(this, "已生成家庭码并复制到剪贴板，去粘贴发给家人", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "生成失败，检查服务器地址和网络", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void syncHistory() {
        new Thread(() -> {
            int count = FamilyClient.syncHistory(this, db);
            runOnUiThread(() -> {
                if (count >= 0) {
                    Toast.makeText(this, "已同步 " + count + " 条到家庭", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "请先填好成员昵称和家庭码", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void showWorkerDialog() {
        showTextInputDialog("服务器地址", AppSettings.getWorkerUrl(this), "如 https://ledger.pengtao.space",
                v -> AppSettings.setWorkerUrl(this, v));
    }

    private void showTextInputDialog(String title, String current, String hint,
                                     java.util.function.Consumer<String> onSave) {
        final EditText input = new EditText(this);
        input.setText(current);
        input.setHint(hint);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    onSave.accept(input.getText().toString().trim());
                    buildSettings();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 构建辅助 ----------

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getResources().getColor(R.color.text_sec));
        tv.setTextSize(13);
        tv.setPadding(dp(16), dp(16), dp(16), dp(6));
        container.addView(tv);
    }

    private void addRow(String title, String value, View.OnClickListener listener) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_setting, container, false);
        ((TextView) row.findViewById(R.id.setting_title)).setText(title);
        ((TextView) row.findViewById(R.id.setting_value)).setText(value);
        row.setOnClickListener(listener);
        container.addView(row);
    }

    private void addSwitchRow(String title, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_setting, container, false);
        ((TextView) row.findViewById(R.id.setting_title)).setText(title);
        row.findViewById(R.id.setting_value).setVisibility(View.GONE);
        Switch sw = row.findViewById(R.id.setting_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        container.addView(row);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- 权限检测 ----------

    private boolean isNotificationEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        String pkg = getPackageName();
        for (String s : flat.split(":")) if (s.startsWith(pkg + "/")) return true;
        return false;
    }

    private boolean isAccessibilityEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_accessibility_services");
        if (flat == null) return false;
        String pkg = getPackageName();
        for (String s : flat.split(":")) if (s.startsWith(pkg + "/")) return true;
        return false;
    }

    // ---------- AI 设置 ----------

    private void showAiConfigDialog() {
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

    // ---------- 深色模式 ----------

    private String themeLabel() {
        String m = AppSettings.getThemeMode(this);
        if ("dark".equals(m)) return "深色";
        if ("light".equals(m)) return "浅色";
        return "跟随系统";
    }

    private void showThemeDialog() {
        final String[] modes = {"浅色", "深色", "跟随系统"};
        final String[] keys = {"light", "dark", "system"};
        int cur = 2;
        String m = AppSettings.getThemeMode(this);
        if ("light".equals(m)) cur = 0;
        else if ("dark".equals(m)) cur = 1;
        new AlertDialog.Builder(this)
                .setTitle("深色模式")
                .setSingleChoiceItems(modes, cur, (d, which) -> {
                    AppSettings.setThemeMode(this, keys[which]);
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 数据 ----------

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

    private void restoreData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_RESTORE);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_IMPORT_CSV) {
            doImportCsv(uri);
        } else if (requestCode == REQ_RESTORE) {
            doRestore(uri);
        }
    }

    private void doImportCsv(Uri uri) {
        new Thread(() -> {
            try {
                String content = readText(uri);
                List<Transaction> list = CsvImporter.parse(content);
                List<Transaction> toInsert = new ArrayList<>();
                for (Transaction t : list) if (!db.hasNote(t.note)) toInsert.add(t);
                int count = db.insertBatch(toInsert);
                runOnUiThread(() -> Toast.makeText(this,
                        "导入 " + count + " 条（跳过重复 " + (list.size() - count) + " 条）", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void doRestore(Uri uri) {
        new Thread(() -> {
            try {
                String content = readText(uri);
                JSONArray arr = new JSONArray(content);
                List<Transaction> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Transaction t = new Transaction();
                    t.amount = o.getDouble("amount");
                    t.category = o.optString("category");
                    t.merchant = o.optString("merchant");
                    t.source = o.optString("source");
                    t.note = o.optString("note");
                    t.comment = o.optString("comment");
                    t.status = o.optInt("status", 1);
                    t.time = o.getLong("time");
                    list.add(t);
                }
                int count = db.insertBatch(list);
                runOnUiThread(() -> Toast.makeText(this, "恢复 " + count + " 条", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "恢复失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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

    private void backupData() {
        List<Transaction> list = db.list(100000);
        if (list.isEmpty()) {
            Toast.makeText(this, "没有账目可备份", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray arr = new JSONArray();
            for (Transaction t : list) {
                JSONObject o = new JSONObject();
                o.put("amount", t.amount);
                o.put("category", t.category == null ? "" : t.category);
                o.put("merchant", t.merchant == null ? "" : t.merchant);
                o.put("source", t.source == null ? "" : t.source);
                o.put("note", t.note == null ? "" : t.note);
                o.put("comment", t.comment == null ? "" : t.comment);
                o.put("status", t.status);
                o.put("time", t.time);
                arr.put(o);
            }
            saveToDownloads("记账本备份_" + timestamp() + ".json", arr.toString());
        } catch (Exception e) {
            Toast.makeText(this, "备份失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearData() {
        new AlertDialog.Builder(this)
                .setTitle("清除全部账目")
                .setMessage("确定删除所有账目吗？此操作不可恢复！")
                .setPositiveButton("删除", (d, w) -> {
                    db.clearAll();
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 日志 ----------

    private void showLogs() {
        String notif = PayNotificationListener.readLog(this);
        String access = LedgerAccessibilityService.readAccessLog(this);
        String ai = AiClient.readAiLog(this);
        StringBuilder sb = new StringBuilder();
        sb.append("【通知日志】\n").append(notif.isEmpty() ? "（空）" : notif).append("\n\n");
        sb.append("【无障碍日志】\n").append(access.isEmpty() ? "（空）" : access).append("\n\n");
        sb.append("【AI 日志】\n").append(ai.isEmpty() ? "（空）" : ai);
        final String content = sb.toString();
        new AlertDialog.Builder(this)
                .setTitle("日志")
                .setMessage(content)
                .setPositiveButton("复制", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("日志", content));
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void exportLogFile() {
        String notif = PayNotificationListener.readLog(this);
        String access = LedgerAccessibilityService.readAccessLog(this);
        String ai = AiClient.readAiLog(this);
        StringBuilder sb = new StringBuilder();
        sb.append("记账本日志 ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date())).append("\n\n");
        sb.append("【通知日志】\n").append(notif.isEmpty() ? "（空）" : notif).append("\n\n");
        sb.append("【无障碍日志】\n").append(access.isEmpty() ? "（空）" : access).append("\n\n");
        sb.append("【AI 日志】\n").append(ai.isEmpty() ? "（空）" : ai);
        saveToDownloads("记账本日志_" + timestamp() + ".txt", sb.toString());
    }

    private void clearLogs() {
        new AlertDialog.Builder(this)
                .setTitle("清空日志")
                .setMessage("确定清空通知日志、无障碍日志、AI 日志吗？")
                .setPositiveButton("清空", (d, w) -> {
                    getSharedPreferences("notif_log", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("access_log", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("ai_log", MODE_PRIVATE).edit().clear().apply();
                    Toast.makeText(this, "已清空所有日志", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 关于 ----------

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("记账本")
                .setMessage("版本 2.2.0\n\n安卓自动记账 App\n支付宝/微信自动记账 + AI 毒舌辣评\n\n开源：github.com/amb-er/auto-ledger")
                .setPositiveButton("关闭", null)
                .show();
    }

    // ---------- 工具 ----------

    private void saveToDownloads(String fileName, String content) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("无法创建文件");
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new Exception("无法写入文件");
            os.write(content.getBytes(StandardCharsets.UTF_8));
            os.close();
            Toast.makeText(this, "已导出到 Download/" + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
        if (!content.contains("交易") && !content.contains("微信") && !content.contains("{")) {
            content = new String(data, offset, data.length - offset, "GBK");
        }
        return content;
    }

    private String csv(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String fmtTime(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(ms));
    }

    private String sourceLabel(String src) {
        if ("alipay".equals(src)) return "支付宝";
        if ("wechat".equals(src)) return "微信";
        if ("manual".equals(src)) return "手动";
        return src == null ? "" : src;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
    }
}
