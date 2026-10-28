package org.meteosphere.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the device starts or the app is updated: the launcher shows the weather widgets' initial layout until they
 * are drawn, so they are drawn at once from the weather they have, before the stale ones load it anew. The weather
 * notification is drawn anew too, setting its alarm, which doesn't survive a restart.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
        if (!boot && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        NotificationUpdater.update(context);
        Context appContext = context.getApplicationContext();
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                WidgetUpdater.drawAllNow(appContext);
                if (boot) {
                    WidgetUpdater.refreshAll(appContext, false);
                }
            } finally {
                result.finish();
            }
        }, "WidgetsRedraw").start();
    }
}
