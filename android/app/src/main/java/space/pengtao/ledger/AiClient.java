package space.pengtao.ledger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 调用 OpenAI 兼容接口生成毒舌辣评 / 月度报告（纯原生 HttpURLConnection + org.json）。 */
public class AiClient {

    public static final String SP_NAME = "ai_config";

    /** 生成单笔消费的毒舌辣评；未配置或失败返回 null。 */
    public static String roast(Context ctx, String amount, String category, String merchant) {
        String prompt = "你是毒舌又爱玩梗的记账管家。对下面这笔消费给出一句毒舌辣评："
                + "要犀利、带点损、会玩梗（网络热梗/谐音梗/抽象梗都行，但别老套、别生硬堆砌），"
                + "幽默但不冒犯，25字以内，纯中文，直接输出辣评本身，不要任何前缀和解释。\n"
                + "消费：金额" + amount + "元，分类" + category + "，商家" + merchant;
        return chat(ctx, prompt, 100);
    }

    /** 生成月度毒舌消费报告；未配置或失败返回 null。 */
    public static String generateReport(Context ctx, String data) {
        String prompt = "你是毒舌又爱玩梗的记账管家。根据下面的本月消费数据，写一份月度消费报告："
                + "要毒舌犀利、会玩梗、有幽默感，指出最败家的地方、该省的地方，"
                + "结构清晰（可分点或加小标题），150字以内，纯中文，直接输出报告内容。\n\n" + data;
        return chat(ctx, prompt, 500);
    }

    private static String chat(Context ctx, String prompt, int maxTokens) {
        SharedPreferences sp = ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String base = sp.getString("endpoint", "").trim();
        String key = sp.getString("api_key", "").trim();
        String model = sp.getString("model", "deepseek-chat").trim();
        if (base.isEmpty() || key.isEmpty()) {
            logAi(ctx, "❌ 未配置 AI：endpoint=" + (base.isEmpty() ? "空" : base)
                    + "，key=" + (key.isEmpty() ? "空" : "已填"));
            return null;
        }

        String url = buildUrl(base);
        logAi(ctx, "调用 AI：\n  url=" + url + "\n  model=" + model);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", msgs);
            body.put("max_tokens", maxTokens);
            body.put("temperature", 1.0);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                logAi(ctx, "❌ HTTP " + code + "：\n  " + readError(conn));
                return null;
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject resp = new JSONObject(sb.toString());
            String content = resp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim();
            logAi(ctx, "✅ AI 返回：" + content);
            return content;
        } catch (Exception e) {
            logAi(ctx, "❌ 异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
            return null;
        }
    }

    private static String readError(HttpURLConnection conn) {
        try {
            InputStream es = conn.getErrorStream();
            if (es == null) return "(无错误响应体)";
            BufferedReader br = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String s = sb.toString();
            return s.length() > 300 ? s.substring(0, 300) : s;
        } catch (Exception e) {
            return "";
        }
    }

    private static void logAi(Context ctx, String entry) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("ai_log", Context.MODE_PRIVATE);
            String existing = sp.getString("log", "");
            String combined = entry + (existing.isEmpty() ? "" : "\n\n" + existing);
            if (combined.length() > 8000) combined = combined.substring(0, 8000);
            sp.edit().putString("log", combined).apply();
        } catch (Exception ignored) {}
    }

    public static String readAiLog(Context ctx) {
        return ctx.getSharedPreferences("ai_log", Context.MODE_PRIVATE).getString("log", "");
    }

    private static String buildUrl(String base) {
        if (base.endsWith("/chat/completions")) return base;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (b.endsWith("/v1")) return b + "/chat/completions";
        return b + "/v1/chat/completions";
    }
}
