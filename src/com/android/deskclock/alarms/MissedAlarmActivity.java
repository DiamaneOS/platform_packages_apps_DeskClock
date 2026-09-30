/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.deskclock.alarms;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.app.NotificationManagerCompat;

import com.android.deskclock.LogUtils;
import com.android.deskclock.provider.AlarmInstance;

/**
 * Handles a tap on a missed-alarm notification: opens the alarm in the app and dismisses the
 * missed alarm. From target SDK 31 a broadcast receiver or service that a notification starts
 * cannot start an activity, so the notification starts this activity directly. It shows nothing
 * and is not exported: only the notification's immutable PendingIntent reaches it.
 */
public class MissedAlarmActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final Uri uri = intent.getData();
        final AlarmInstance instance = uri == null ? null
                : AlarmInstance.getInstance(getContentResolver(), AlarmInstance.getId(uri));
        if (instance == null) {
            LogUtils.e("No missed alarm instance for %s", uri);
            final int id = intent.getIntExtra(AlarmNotifications.EXTRA_NOTIFICATION_ID, -1);
            if (id != -1) {
                NotificationManagerCompat.from(this).cancel(id);
            }
        } else {
            // Open DeskClock on the alarms tab, at this alarm.
            startActivity(AlarmNotifications.createViewAlarmIntent(this, instance));
            AlarmStateManager.deleteInstanceAndUpdateParent(this, instance);
        }
        finish();
    }
}
