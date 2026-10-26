package space.pengtao.ledger;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 家庭版同步客户端：上传消费、拉取家庭账单/汇总（走 Cloudflare Worker）。 */
public class FamilyClient {

    /** 上传一笔消费到 Worker（异步，失败静默）。 */
    public static void upload(Context ctx, Transaction t) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        String member = AppSettings.getMemberName(ctx);
        if (worker.isEmpty() || family.isEmpty() || member.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("family_code", family);
                body.put("member", member);
                body.put("amount", t.amount);
                body.put("category", t.category == null ? "" : t.category);
                body.put("merchant", t.merchant == null ? "" : t.merchant);
                body.put("source", t.source == null ? "" : t.source);
                body.put("note", t.note == null ? "" : t.note);
                body.put("comment", t.comment == null ? "" : t.comment);
                body.put("remark", t.remark == null ? "" : t.remark);
                body.put("status", t.status);
                body.put("time", t.time);
                post(worker + "/api/transaction", body.toString());
            } catch (Exception ignored) {}
        }).start();
    }

    /** 同步本地历史账目到家庭（同步，请在后台线程调用）；返回成功条数，未配置返回 -1。 */
    public static int syncHistory(Context ctx, LedgerDbHelper db) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        String member = AppSettings.getMemberName(ctx);
        if (worker.isEmpty() || family.isEmpty() || member.isEmpty()) return -1;
        List<Transaction> list = db.list(100000);
        int count = 0;
        for (Transaction t : list) {
            try {
                JSONObject body = new JSONObject();
                body.put("family_code", family);
                body.put("member", member);
                body.put("amount", t.amount);
                body.put("category", t.category == null ? "" : t.category);
                body.put("merchant", t.merchant == null ? "" : t.merchant);
                body.put("source", t.source == null ? "" : t.source);
                body.put("note", t.note == null ? "" : t.note);
                body.put("comment", t.comment == null ? "" : t.comment);
                body.put("remark", t.remark == null ? "" : t.remark);
                body.put("status", t.status);
                body.put("time", t.time);
                post(worker + "/api/transaction", body.toString());
                count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    /** 创建家庭：让后端生成唯一家庭码；失败返回 null。 */
    public static String createFamily(Context ctx) {
        String worker = AppSettings.getWorkerUrl(ctx);
        if (worker.isEmpty()) return null;
        try {
            String resp = postAndGet(worker + "/api/family", "{}");
            JSONObject o = new JSONObject(resp);
            if (o.optBoolean("ok")) return o.optString("family_code");
        } catch (Exception ignored) {}
        return null;
    }

    /** 拉取家庭消费汇总（同步，请在后台线程调用）；失败返回 null。 */
    public static JSONArray fetchSummary(Context ctx) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        if (worker.isEmpty() || family.isEmpty()) return null;
        try {
            String resp = get(worker + "/api/summary?family_code=" + family);
            JSONObject o = new JSONObject(resp);
            if (o.optBoolean("ok")) return o.optJSONArray("summary");
        } catch (Exception ignored) {}
        return null;
    }

    /** 拉取家庭账单明细（同步，请在后台线程调用）；失败返回 null。 */
    public static JSONArray fetchTransactions(Context ctx) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        if (worker.isEmpty() || family.isEmpty()) return null;
        try {
            String resp = get(worker + "/api/transactions?family_code=" + family);
            JSONObject o = new JSONObject(resp);
            if (o.optBoolean("ok")) return o.optJSONArray("transactions");
        } catch (Exception ignored) {}
        return null;
    }

    /** 提交评论（异步，失败静默）。 */
    public static void submitComment(Context ctx, long transactionId, String content) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        String member = AppSettings.getMemberName(ctx);
        if (worker.isEmpty() || family.isEmpty() || member.isEmpty()
                || content == null || content.isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("family_code", family);
                body.put("transaction_id", transactionId);
                body.put("member", member);
                body.put("content", content);
                body.put("time", System.currentTimeMillis());
                post(worker + "/api/comment", body.toString());
            } catch (Exception ignored) {}
        }).start();
    }

    /** 拉取家庭所有评论（同步，请在后台线程调用）；失败返回 null。 */
    public static JSONArray fetchComments(Context ctx) {
        String worker = AppSettings.getWorkerUrl(ctx);
        String family = AppSettings.getFamilyCode(ctx);
        if (worker.isEmpty() || family.isEmpty()) return null;
        try {
            String resp = get(worker + "/api/comments?family_code=" + family);
            JSONObject o = new JSONObject(resp);
            if (o.optBoolean("ok")) return o.optJSONArray("comments");
        } catch (Exception ignored) {}
        return null;
    }

    private static void post(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();
        conn.getResponseCode();
        conn.disconnect();
    }

    private static String postAndGet(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = conn.getResponseCode();
        if (code != 200) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        int code = conn.getResponseCode();
        if (code != 200) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        conn.disconnect();
        return sb.toString();
    }
}
