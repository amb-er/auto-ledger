package space.pengtao.ledger;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;

/** 根据设置应用浅色/深色主题。 */
public class ThemeUtil {

    public static void applyTheme(Activity activity) {
        String mode = AppSettings.getThemeMode(activity);
        boolean dark;
        if ("dark".equals(mode)) {
            dark = true;
        } else if ("light".equals(mode)) {
            dark = false;
        } else {
            UiModeManager um = (UiModeManager) activity.getSystemService(Context.UI_MODE_SERVICE);
            dark = um != null && um.getNightMode() == UiModeManager.MODE_NIGHT_YES;
        }
        activity.setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme);
    }
}
