/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.deskclock.alarms;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;

import com.android.deskclock.AlarmAlertWakeLock;
import com.android.deskclock.LogUtils;
import com.android.deskclock.R;
import com.android.deskclock.events.Events;
import com.android.deskclock.provider.AlarmInstance;

/**
 * This service is in charge of starting/stopping the alarm. It will bring up and manage the
 * {@link AlarmActivity} as well as {@link AlarmKlaxon}.
 *
 * Registers a broadcast receiver to listen for snooze/dismiss intents. The broadcast receiver
 * exits early if AlarmActivity is bound to prevent double-processing of the snooze/dismiss intents.
 */
public class AlarmService extends Service {
    /** A public action sent by AlarmService when the alarm has started. */
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";

    /** A public action sent by AlarmService when the alarm has stopped for any reason. */
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    /** Private action used to stop an alarm with this service. */
    public static final String STOP_ALARM_ACTION = "STOP_ALARM";

    /** Binder given to AlarmActivity. */
    private final IBinder mBinder = new Binder();

    /** Whether the service is currently bound to AlarmActivity */
    private boolean mIsBound = false;

    /** Marks the ringing alarm missed when a phone call starts. */
    private final CallWatcher mCallWatcher = new CallWatcher();

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mIsBound = false;
        return super.onUnbind(intent);
    }

    /**
     * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
     * or using a different instance.
     *
     * @param context application context
     * @param instance you are trying to stop
     */
    public static void stopAlarm(Context context, AlarmInstance instance) {
        final Intent intent = AlarmInstance.createIntent(context, AlarmService.class, instance.mId)
                .setAction(STOP_ALARM_ACTION);

        // We don't need a wake lock here, since we are trying to kill an alarm
        context.startService(intent);
    }

    private AlarmInstance mCurrentAlarm = null;

    private void startAlarm(AlarmInstance instance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId);
        if (mCurrentAlarm != null) {
            AlarmStateManager.setMissedState(this, mCurrentAlarm);
            stopCurrentAlarm();
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this);

        mCurrentAlarm = instance;
        AlarmNotifications.showAlarmNotification(this, mCurrentAlarm);
        mCallWatcher.start();
        AlarmKlaxon.start(this, mCurrentAlarm);
        sendBroadcast(new Intent(ALARM_ALERT_ACTION));
    }

    private void stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop");
            return;
        }

        final long instanceId = mCurrentAlarm.mId;
        LogUtils.v("AlarmService.stop with instance: %s", instanceId);

        AlarmKlaxon.stop(this);
        mCallWatcher.stop();
        sendBroadcast(new Intent(ALARM_DONE_ACTION));

        stopForeground(true /* removeNotification */);

        mCurrentAlarm = null;
        AlarmAlertWakeLock.releaseCpuLock();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.v("AlarmService.onStartCommand() with %s", intent);
        if (intent == null) {
            return Service.START_NOT_STICKY;
        }

        final long instanceId = AlarmInstance.getId(intent.getData());
        switch (intent.getAction()) {
            case AlarmStateManager.CHANGE_STATE_ACTION:
                AlarmStateManager.handleIntent(this, intent);

                // If state is changed to firing, actually fire the alarm!
                final int alarmState = intent.getIntExtra(AlarmStateManager.ALARM_STATE_EXTRA, -1);
                if (alarmState == AlarmInstance.FIRED_STATE) {
                    final ContentResolver cr = this.getContentResolver();
                    final AlarmInstance instance = AlarmInstance.getInstance(cr, instanceId);
                    if (instance == null) {
                        LogUtils.e("No instance found to start alarm: %d", instanceId);
                        if (mCurrentAlarm != null) {
                            // Only release lock if we are not firing alarm
                            AlarmAlertWakeLock.releaseCpuLock();
                        }
                        break;
                    }

                    if (mCurrentAlarm != null && mCurrentAlarm.mId == instanceId) {
                        LogUtils.e("Alarm already started for instance: %d", instanceId);
                        break;
                    }
                    startAlarm(instance);
                }
                break;
            case STOP_ALARM_ACTION:
                if (mCurrentAlarm != null && mCurrentAlarm.mId != instanceId) {
                    LogUtils.e("Can't stop alarm for instance: %d because current alarm is: %d",
                            instanceId, mCurrentAlarm.mId);
                    break;
                }
                stopCurrentAlarm();
                stopSelf();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called");
        super.onDestroy();
        if (mCurrentAlarm != null) {
            stopCurrentAlarm();
        }
    }

    /**
     * Marks the ringing alarm missed when a phone call is answered. GrapheneOS's Clock did this
     * through the call state, which from target SDK 31 needs the phone permission. The audio mode
     * needs none, but any app with MODIFY_AUDIO_SETTINGS can set most modes, so only the modes
     * that need MODIFY_PHONE_STATE count (see {@link #isCallMode}). A call that only rings
     * therefore no longer stops the alarm.
     */
    private final class CallWatcher implements AudioManager.OnModeChangedListener {
        private AudioManager mAudioManager;
        /** The last mode seen: a call that was already on when the alarm started does not count. */
        private int mLastMode;
        private boolean mWatching;

        void start() {
            if (mWatching) {
                return;
            }
            mAudioManager = getSystemService(AudioManager.class);
            mLastMode = mAudioManager.getMode();
            mAudioManager.addOnModeChangedListener(getMainExecutor(), this);
            mWatching = true;
        }

        void stop() {
            if (mWatching) {
                mAudioManager.removeOnModeChangedListener(this);
                mWatching = false;
            }
        }

        @Override
        public void onModeChanged(int mode) {
            final int previous = mLastMode;
            mLastMode = mode;
            if (mCurrentAlarm == null || !callAnswered(previous, mode)) {
                return;
            }
            LogUtils.i("A call started while the alarm rang: marking it missed");
            startService(AlarmStateManager.createStateChangeIntent(AlarmService.this,
                    "AlarmService", mCurrentAlarm, AlarmInstance.MISSED_STATE));
        }
    }

    /**
     * Whether {@code mode} means a connected phone call. Only these modes need
     * MODIFY_PHONE_STATE, so no ordinary app can set them. MODE_RINGTONE, MODE_CALL_SCREENING
     * and MODE_IN_COMMUNICATION need only MODIFY_AUDIO_SETTINGS, which any app is granted at
     * install, and must not stop an alarm.
     */
    static boolean isCallMode(int mode) {
        return mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_CALL_REDIRECT;
    }

    /**
     * Whether the change from {@code previous} to {@code mode} connects a call. A call that ends
     * and a new one that is answered while the alarm still rings counts again.
     */
    static boolean callAnswered(int previous, int mode) {
        return !isCallMode(previous) && isCallMode(mode);
    }
}
