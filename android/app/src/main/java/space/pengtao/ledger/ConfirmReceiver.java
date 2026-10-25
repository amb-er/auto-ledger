package space.pengtao.ledger;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 通知上的「确认」按钮：直接在后台确认账目，不跳回 App。 */
public class ConfirmReceiver extends BroadcastReceiver {

    public static final String ACTION = "space.pengtao.ledger.CONFIRM";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_NOTIFY_ID = "notify_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(EXTRA_ID, -1);
        int notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, -1);
        if (id > 0) {
            new LedgerDbHelper(context).confirm(id);
        }
        if (notifyId > 0) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(notifyId);
        }
    }
}
