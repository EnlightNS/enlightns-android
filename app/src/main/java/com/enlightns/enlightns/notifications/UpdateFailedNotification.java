package com.enlightns.enlightns.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;

import com.enlightns.enlightns.R;
import com.enlightns.enlightns.RecordListing;
import com.enlightns.enlightns.updater.NetworkChangeReceiver;


public class UpdateFailedNotification {

    private static final String NOTIFICATION_TAG = "UpdateFailed";


    public static void notify(final Context context) {
        final Resources res = context.getResources();

        final String title = res.getString(R.string.notification_record_update_failed_title);
        final String text = res.getString(R.string.notification_record_update_failed_text);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setDefaults(Notification.DEFAULT_ALL)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getBroadcast(context, 1,
                        new Intent(NetworkChangeReceiver.MANUAL_UPDATE_ACTION),
                        PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true);

        notify(context, builder.build());
    }

    private static void notify(final Context context, final Notification notification) {
        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_TAG, 0, notification);
    }

    public static void cancel(final Context context) {
        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_TAG, 0);
    }
}