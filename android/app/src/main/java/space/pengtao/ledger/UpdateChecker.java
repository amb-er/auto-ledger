package space.pengtao.ledger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 自建更新检查：对比下载页 version.json，下载新 APK 并跳转系统安装。 */
public class UpdateChecker {

    private static final String VERSION_URL = "https://app.pengtao.space/version.json";

    /** silent=true 静默检查（有新版本才弹窗，无新版本不打扰）；false 手动检查（总有 Toast 反馈）。 */
    public static void check(Context ctx, boolean silent) {
        new Thread(() -> {
            try {
                String json = httpGet(VERSION_URL);
                JSONObject o = new JSONObject(json);
                long serverCode = o.optLong("versionCode");
                String versionName = o.optString("versionName");
                String url = o.optString("url");
                String note = o.optString("note");

                long currentCode = currentVersionCode(ctx);
                if (serverCode <= currentCode) {
                    if (!silent) toast(ctx, "已是最新版本 " + versionName);
                    return;
                }
                Handler h = new Handler(Looper.getMainLooper());
                h.post(() -> showUpdateDialog(ctx, versionName, note, url));
            } catch (Exception ignored) {
                if (!silent) toast(ctx, "检查更新失败，请稍后重试");
            }
        }).start();
    }

    private static void showUpdateDialog(Context ctx, String versionName, String note, String url) {
        String msg = note.isEmpty() ? "是否下载更新？" : note + "\n\n是否下载更新？";
        new AlertDialog.Builder(ctx)
                .setTitle("发现新版本 " + versionName)
                .setMessage(msg)
                .setPositiveButton("更新", (d, w) -> downloadAndInstall(ctx, url))
                .setNegativeButton("取消", null)
                .show();
    }

    private static void downloadAndInstall(Context ctx, String url) {
        toast(ctx, "开始下载新版本…");
        new Thread(() -> {
            try {
                File dir = new File(ctx.getCacheDir(), "apk");
                if (!dir.exists()) dir.mkdirs();
                File apk = new File(dir, "AutoLedger.apk");
                download(url, apk);
                install(ctx, apk);
            } catch (Exception e) {
                toast(ctx, "下载失败：" + e.getMessage());
            }
        }).start();
    }

    private static void install(Context ctx, File apk) {
        // Android 8+ 需要「安装未知应用」权限
        if (Build.VERSION.SDK_INT >= 26 && !ctx.getPackageManager().canRequestPackageInstalls()) {
            toast(ctx, "请先允许「安装未知应用」权限");
            Intent perm = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            perm.setData(Uri.parse("package:" + ctx.getPackageName()));
            perm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(perm);
            return;
        }
        Uri uri = ApkFileProvider.getUriForFile(ctx, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private static void download(String urlStr, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AutoLedger");
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        InputStream is = conn.getInputStream();
        FileOutputStream os = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
        os.close();
        is.close();
        conn.disconnect();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    private static long currentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= 28) return pi.getLongVersionCode();
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void toast(Context ctx, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }
}
