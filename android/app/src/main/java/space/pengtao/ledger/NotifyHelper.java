package space.pengtao.ledger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 发系统通知，告知用户识别到新账目，并附「确认」按钮（不跳 App 直接确认）。 */
public class NotifyHelper {

    private static final String CHANNEL_ID = "bookkeeping";
    private static final String CHANNEL_NAME = "记账提醒";

    public static void notifyTransaction(Context ctx, long id, String title, String content) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT));
            }

            int notifyId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

            // 点通知主体：跳回 App
            Intent open = new Intent(ctx, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            // 「确认」按钮：走广播，不跳 App
            Intent confirm = new Intent(ctx, ConfirmReceiver.class);
            confirm.setAction(ConfirmReceiver.ACTION);
            confirm.putExtra(ConfirmReceiver.EXTRA_ID, id);
            confirm.putExtra(ConfirmReceiver.EXTRA_NOTIFY_ID, notifyId);
            PendingIntent confirmPi = PendingIntent.getBroadcast(ctx, 0, confirm,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL_ID)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setAutoCancel(true)
                    .setContentIntent(openPi)
                    .addAction(0, "确认", confirmPi);

            nm.notify(notifyId, b.build());
        } catch (Exception ignored) {}
    }
}
