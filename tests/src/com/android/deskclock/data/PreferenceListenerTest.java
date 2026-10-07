/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.deskclock.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import com.android.deskclock.R;
import com.android.deskclock.settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Settings changed while Clock runs must reach the cached values in the running app, also after
 * a garbage collection (SharedPreferences holds its change listeners only weakly).
 */
@RunWith(AndroidJUnit4ClassRunner.class)
public class PreferenceListenerTest {

    private Instrumentation mInstrumentation;
    private Context mContext;
    private SharedPreferences mPrefs;
    private String mSavedTimerRingtone;
    private String mSavedHomeTimeZone;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        // The same file the app and its settings screen use.
        final Context storage = mContext.createDeviceProtectedStorageContext();
        mPrefs = storage.getSharedPreferences(
                PreferenceManager.getDefaultSharedPreferencesName(storage), Context.MODE_PRIVATE);
        mSavedTimerRingtone = mPrefs.getString(SettingsActivity.KEY_TIMER_RINGTONE, null);
        mSavedHomeTimeZone = mPrefs.getString(SettingsActivity.KEY_HOME_TZ, null);
    }

    @After
    public void tearDown() {
        mPrefs.edit()
                .putString(SettingsActivity.KEY_TIMER_RINGTONE, mSavedTimerRingtone)
                .putString(SettingsActivity.KEY_HOME_TZ, mSavedHomeTimeZone)
                .commit();
    }

    @Test
    public void silentTimerSoundIsUsedAtOnce() {
        final DataModel dataModel = DataModel.getDataModel();
        final String[] title = new String[2];
        final boolean[] silent = new boolean[1];
        // DataModel is used on the main thread; assertions run here, on the test thread.
        mInstrumentation.runOnMainSync(() -> {
            // Start from the default sound and fill the caches, as opening the settings does.
            dataModel.setTimerRingtoneUri(dataModel.getDefaultTimerRingtoneUri());
            title[0] = dataModel.getTimerRingtoneTitle();
        });
        assertEquals(mContext.getString(R.string.default_timer_ringtone_title), title[0]);

        collectGarbage();

        mInstrumentation.runOnMainSync(() -> {
            // What the ringtone picker saves when Silent is chosen.
            dataModel.setTimerRingtoneUri(Uri.EMPTY);
            silent[0] = dataModel.isTimerRingtoneSilent();
            title[1] = dataModel.getTimerRingtoneTitle();
        });
        assertTrue(silent[0]);
        assertEquals(mContext.getString(R.string.silent_ringtone_title), title[1]);
    }

    @Test
    public void homeTimeZoneChangeIsUsedAtOnce() {
        final DataModel dataModel = DataModel.getDataModel();
        final String[] zone = new String[2];
        mInstrumentation.runOnMainSync(() -> {
            final String current = dataModel.getHomeCity().getTimeZone().getID();
            for (CharSequence id : dataModel.getTimeZones().getTimeZoneIds()) {
                if (!id.toString().equals(current)) {
                    zone[0] = id.toString();
                    break;
                }
            }
        });
        assertNotNull(zone[0]);

        collectGarbage();

        // The settings screen writes the home time zone straight into the preferences.
        mInstrumentation.runOnMainSync(() -> {
            mPrefs.edit().putString(SettingsActivity.KEY_HOME_TZ, zone[0]).apply();
            zone[1] = dataModel.getHomeCity().getTimeZone().getID();
        });
        assertEquals(zone[0], zone[1]);
    }

    private static void collectGarbage() {
        for (int i = 0; i < 3; i++) {
            Runtime.getRuntime().gc();
            System.runFinalization();
        }
    }
}
