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

    @Test
    public void onlyAConnectingCallCounts() {
        assertTrue(AlarmService.callAnswered(AudioManager.MODE_NORMAL, AudioManager.MODE_IN_CALL));
        assertTrue(AlarmService.callAnswered(
                AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL));
        // The same call again, or a call moving to another device, is no new call.
        assertFalse(AlarmService.callAnswered(
                AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_CALL));
        assertFalse(AlarmService.callAnswered(
                AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_REDIRECT));
        assertFalse(AlarmService.callAnswered(AudioManager.MODE_IN_CALL, AudioManager.MODE_NORMAL));
        assertFalse(AlarmService.callAnswered(
                AudioManager.MODE_NORMAL, AudioManager.MODE_IN_COMMUNICATION));
    }

    @Test
    public void aSecondCallCountsAfterTheFirstEnds() {
        // The alarm started during call A; A ended; call B is answered.
        int last = AudioManager.MODE_IN_CALL;
        assertFalse(AlarmService.callAnswered(last, AudioManager.MODE_NORMAL));
        last = AudioManager.MODE_NORMAL;
        assertTrue(AlarmService.callAnswered(last, AudioManager.MODE_IN_CALL));
    }
}
