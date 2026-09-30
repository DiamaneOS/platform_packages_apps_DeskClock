/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.deskclock.alarms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4ClassRunner.class)
public class AlarmServiceTest {

    @Test
    public void onlyModesThatNeedPhoneStateStopTheAlarm() {
        assertTrue(AlarmService.isCallMode(AudioManager.MODE_IN_CALL));
        assertTrue(AlarmService.isCallMode(AudioManager.MODE_CALL_REDIRECT));
    }

    @Test
    public void modesAnyAppCanSetDoNotStopTheAlarm() {
        // Any app with MODIFY_AUDIO_SETTINGS can set these.
        assertFalse(AlarmService.isCallMode(AudioManager.MODE_NORMAL));
        assertFalse(AlarmService.isCallMode(AudioManager.MODE_RINGTONE));
        assertFalse(AlarmService.isCallMode(AudioManager.MODE_IN_COMMUNICATION));
        assertFalse(AlarmService.isCallMode(AudioManager.MODE_CALL_SCREENING));
    }
}
