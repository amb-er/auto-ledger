package space.pengtao.ledger;

import android.content.Context;
import android.provider.Settings;

/** 权限状态检测工具：通知使用权 / 无障碍。 */
public class PermissionUtil {

    /** 是否已开启「通知使用权」（NotificationListenerService）。 */
    public static boolean isNotificationEnabled(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        String pkg = ctx.getPackageName();
        for (String s : flat.split(":")) if (s.startsWith(pkg + "/")) return true;
        return false;
    }

    /** 是否已开启「无障碍」（AccessibilityService）。 */
    public static boolean isAccessibilityEnabled(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_accessibility_services");
        if (flat == null) return false;
        String pkg = ctx.getPackageName();
        for (String s : flat.split(":")) if (s.startsWith(pkg + "/")) return true;
        return false;
    }
}
