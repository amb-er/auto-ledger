package space.pengtao.ledger;

import android.content.Context;
import android.content.SharedPreferences;

/** 应用设置（开关、主题等）的统一存取。 */
public class AppSettings {

    private static final String SP = "app_settings";

    // 自动记账总开关
    public static boolean isAutoEnabled(Context ctx) {
        return sp(ctx).getBoolean("auto_enabled", true);
    }

    public static void setAutoEnabled(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean("auto_enabled", v).apply();
    }

    // AI 辣评开关
    public static boolean isRoastEnabled(Context ctx) {
        return sp(ctx).getBoolean("roast_enabled", true);
    }

    public static void setRoastEnabled(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean("roast_enabled", v).apply();
    }

    // 深色模式：light / dark / system
    public static String getThemeMode(Context ctx) {
        return sp(ctx).getString("theme_mode", "system");
    }

    public static void setThemeMode(Context ctx, String v) {
        sp(ctx).edit().putString("theme_mode", v).apply();
    }

    // 家庭版：成员昵称 / 家庭码 / Worker 地址
    public static String getMemberName(Context ctx) {
        return sp(ctx).getString("member_name", "");
    }

    public static void setMemberName(Context ctx, String v) {
        sp(ctx).edit().putString("member_name", v).apply();
    }

    public static String getFamilyCode(Context ctx) {
        return sp(ctx).getString("family_code", "");
    }

    public static void setFamilyCode(Context ctx, String v) {
        sp(ctx).edit().putString("family_code", v).apply();
    }

    public static String getWorkerUrl(Context ctx) {
        return sp(ctx).getString("worker_url", "https://ledger.pengtao.space");
    }

    public static void setWorkerUrl(Context ctx, String v) {
        sp(ctx).edit().putString("worker_url", v).apply();
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }
}
