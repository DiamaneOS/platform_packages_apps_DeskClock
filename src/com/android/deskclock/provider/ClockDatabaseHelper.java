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

package com.android.deskclock.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;

import com.android.deskclock.LogUtils;
import com.android.deskclock.data.Weekdays;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class for opening the database from multiple providers.  Also provides
 * some common functionality.
 */
class ClockDatabaseHelper extends SQLiteOpenHelper {
    /**
     * Original Clock Database.
     **/
    private static final int VERSION_5 = 5;

    /**
     * Added alarm_instances table
     * Added selected_cities table
     * Added DELETE_AFTER_USE column to alarms table
     */
    private static final int VERSION_6 = 6;

    /**
     * Added alarm settings to instance table.
     */
    private static final int VERSION_7 = 7;

    /**
     * Removed selected_cities table.
     *
     * <p>The database stays at this version, GrapheneOS's Clock's, so that Clock can still open it
     * if it ships again. The increasing volume column this app adds is created with the tables, or
     * added by {@link #onOpen} where it is missing. GrapheneOS's Clock names every column it reads
     * and its inserts leave that column to its default, so the column does not affect it.
     *
     * <p>Earlier builds of this app went on to version 12: 10 added the increasing volume column,
     * 11 a profile column and 12 removed that again. {@link #onDowngrade} takes those back to 8.
     */
    private static final int VERSION_8 = 8;

    /** The columns of the alarms table at version 8. */
    private static final String[] ALARMS_COLUMNS_V8 = {
            ClockContract.AlarmsColumns._ID,
            ClockContract.AlarmsColumns.HOUR,
            ClockContract.AlarmsColumns.MINUTES,
            ClockContract.AlarmsColumns.DAYS_OF_WEEK,
            ClockContract.AlarmsColumns.ENABLED,
            ClockContract.AlarmsColumns.VIBRATE,
            ClockContract.AlarmsColumns.LABEL,
            ClockContract.AlarmsColumns.RINGTONE,
            ClockContract.AlarmsColumns.DELETE_AFTER_USE,
    };

    /** The columns of the instances table at version 8. */
    private static final String[] INSTANCES_COLUMNS_V8 = {
            ClockContract.InstancesColumns._ID,
            ClockContract.InstancesColumns.YEAR,
            ClockContract.InstancesColumns.MONTH,
            ClockContract.InstancesColumns.DAY,
            ClockContract.InstancesColumns.HOUR,
            ClockContract.InstancesColumns.MINUTES,
            ClockContract.InstancesColumns.VIBRATE,
            ClockContract.InstancesColumns.LABEL,
            ClockContract.InstancesColumns.RINGTONE,
            ClockContract.InstancesColumns.ALARM_STATE,
            ClockContract.InstancesColumns.ALARM_ID,
    };

    // This creates a default alarm at 8:30 for every Mon,Tue,Wed,Thu,Fri
    private static final String DEFAULT_ALARM_1 = "(8, 30, 31, 0, 1, '', NULL, 0, 0);";

    // This creates a default alarm at 9:30 for every Sat,Sun
    private static final String DEFAULT_ALARM_2 = "(9, 00, 96, 0, 1, '', NULL, 0, 0);";

    // Database and table names
    static final String DATABASE_NAME = "alarms.db";
    static final String OLD_ALARMS_TABLE_NAME = "alarms";
    static final String ALARMS_TABLE_NAME = "alarm_templates";
    static final String INSTANCES_TABLE_NAME = "alarm_instances";
    private static final String SELECTED_CITIES_TABLE_NAME = "selected_cities";

    private static void createAlarmsTable(SQLiteDatabase db, String alarmsTableName) {
        db.execSQL("CREATE TABLE " + alarmsTableName + " (" +
                ClockContract.AlarmsColumns._ID + " INTEGER PRIMARY KEY," +
                ClockContract.AlarmsColumns.HOUR + " INTEGER NOT NULL, " +
                ClockContract.AlarmsColumns.MINUTES + " INTEGER NOT NULL, " +
                ClockContract.AlarmsColumns.DAYS_OF_WEEK + " INTEGER NOT NULL, " +
                ClockContract.AlarmsColumns.ENABLED + " INTEGER NOT NULL, " +
                ClockContract.AlarmsColumns.VIBRATE + " INTEGER NOT NULL, " +
                ClockContract.AlarmsColumns.LABEL + " TEXT NOT NULL, " +
                ClockContract.AlarmsColumns.RINGTONE + " TEXT, " +
                ClockContract.AlarmsColumns.DELETE_AFTER_USE + " INTEGER NOT NULL DEFAULT 0, " +
                ClockContract.AlarmsColumns.INCREASING_VOLUME + " INTEGER NOT NULL DEFAULT 0);");
        LogUtils.i("Alarms Table created");
    }

    private static void createInstanceTable(SQLiteDatabase db, String instanceTableName) {
        db.execSQL("CREATE TABLE " + instanceTableName + " (" +
                ClockContract.InstancesColumns._ID + " INTEGER PRIMARY KEY," +
                ClockContract.InstancesColumns.YEAR + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.MONTH + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.DAY + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.HOUR + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.MINUTES + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.VIBRATE + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.LABEL + " TEXT NOT NULL, " +
                ClockContract.InstancesColumns.RINGTONE + " TEXT, " +
                ClockContract.InstancesColumns.ALARM_STATE + " INTEGER NOT NULL, " +
                ClockContract.InstancesColumns.ALARM_ID + " INTEGER REFERENCES " +
                    ALARMS_TABLE_NAME + "(" + ClockContract.AlarmsColumns._ID + ") " +
                    "ON UPDATE CASCADE ON DELETE CASCADE, " +
                ClockContract.InstancesColumns.INCREASING_VOLUME + " INTEGER NOT NULL DEFAULT 0);");
        LogUtils.i("Instance table created");
    }

    public ClockDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, VERSION_8);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAlarmsTable(db, ALARMS_TABLE_NAME);
        createInstanceTable(db, INSTANCES_TABLE_NAME);

        // insert default alarms
        LogUtils.i("Inserting default alarms");
        String cs = ", "; //comma and space
        String insertMe = "INSERT INTO " + ALARMS_TABLE_NAME + " (" +
                ClockContract.AlarmsColumns.HOUR + cs +
                ClockContract.AlarmsColumns.MINUTES + cs +
                ClockContract.AlarmsColumns.DAYS_OF_WEEK + cs +
                ClockContract.AlarmsColumns.ENABLED + cs +
                ClockContract.AlarmsColumns.VIBRATE + cs +
                ClockContract.AlarmsColumns.LABEL + cs +
                ClockContract.AlarmsColumns.RINGTONE + cs +
                ClockContract.AlarmsColumns.DELETE_AFTER_USE + cs +
                ClockContract.AlarmsColumns.INCREASING_VOLUME + ") VALUES ";
        db.execSQL(insertMe + DEFAULT_ALARM_1);
        db.execSQL(insertMe + DEFAULT_ALARM_2);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
        LogUtils.v("Upgrading alarms database from version %d to %d", oldVersion, currentVersion);

        if (oldVersion <= VERSION_7) {
            // This was not used in VERSION_7 or prior, so we can just drop it.
            db.execSQL("DROP TABLE IF EXISTS " + SELECTED_CITIES_TABLE_NAME + ";");
        }

        if (oldVersion <= VERSION_6) {
            // This was not used in VERSION_6 or prior, so we can just drop it.
            db.execSQL("DROP TABLE IF EXISTS " + INSTANCES_TABLE_NAME + ";");

            // Create new alarms table and copy over the data
            createAlarmsTable(db, ALARMS_TABLE_NAME);
            createInstanceTable(db, INSTANCES_TABLE_NAME);

            LogUtils.i("Copying old alarms to new table");
            final String[] OLD_TABLE_COLUMNS = {
                    "_id",
                    "hour",
                    "minutes",
                    "daysofweek",
                    "enabled",
                    "vibrate",
                    "message",
                    "alert",
                    "incvol"
            };
            try (Cursor cursor = db.query(OLD_ALARMS_TABLE_NAME, OLD_TABLE_COLUMNS,
                    null, null, null, null, null)) {
                final Calendar currentTime = Calendar.getInstance();
                while (cursor != null && cursor.moveToNext()) {
                    final Alarm alarm = new Alarm();
                    alarm.id = cursor.getLong(0);
                    alarm.hour = cursor.getInt(1);
                    alarm.minutes = cursor.getInt(2);
                    alarm.daysOfWeek = Weekdays.fromBits(cursor.getInt(3));
                    alarm.enabled = cursor.getInt(4) == 1;
                    alarm.vibrate = cursor.getInt(5) == 1;
                    alarm.label = cursor.getString(6);

                    final String alertString = cursor.getString(7);
                    if ("silent".equals(alertString)) {
                        alarm.alert = Alarm.NO_RINGTONE_URI;
                    } else {
                        alarm.alert =
                                TextUtils.isEmpty(alertString) ? null : Uri.parse(alertString);
                    }
                    alarm.increasingVolume = cursor.getInt(8) == 1;

                    // Save new version of alarm and create alarm instance for it
                    db.insert(ALARMS_TABLE_NAME, null, Alarm.createContentValues(alarm));
                    if (alarm.enabled) {
                        AlarmInstance newInstance = alarm.createInstanceAfter(currentTime);
                        db.insert(INSTANCES_TABLE_NAME, null,
                                AlarmInstance.createContentValues(newInstance));
                    }
                }
            }

            LogUtils.i("Dropping old alarm table");
            db.execSQL("DROP TABLE IF EXISTS " + OLD_ALARMS_TABLE_NAME + ";");
        }
    }

    /**
     * Takes a database from an earlier build of this app, at version 10 to 12, back to version 8.
     * Its tables have every column of version 8, and their other columns have defaults, so the
     * alarms are kept and only the version changes. A database that does not fit is recreated
     * with the default alarms, since one that cannot be opened schedules no alarms at all.
     */
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtils.i("Downgrading alarms database from version %d to %d", oldVersion, newVersion);
        if (fitsVersion8(db, ALARMS_TABLE_NAME, ALARMS_COLUMNS_V8)
                && fitsVersion8(db, INSTANCES_TABLE_NAME, INSTANCES_COLUMNS_V8)) {
            return;
        }

        LogUtils.w("Alarms database version %d does not fit version %d: recreating it",
                oldVersion, newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + INSTANCES_TABLE_NAME + ";");
        db.execSQL("DROP TABLE IF EXISTS " + ALARMS_TABLE_NAME + ";");
        onCreate(db);
    }

    /**
     * Adds the increasing volume column where the tables lack it, as when GrapheneOS's Clock
     * created them.
     */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (db.isReadOnly()) {
            return;
        }
        addIncreasingVolumeIfMissing(db, ALARMS_TABLE_NAME);
        addIncreasingVolumeIfMissing(db, INSTANCES_TABLE_NAME);
    }

    private static void addIncreasingVolumeIfMissing(SQLiteDatabase db, String table) {
        final Map<String, Boolean> columns = readColumns(db, table);
        if (!columns.isEmpty()
                && !columns.containsKey(ClockContract.AlarmsColumns.INCREASING_VOLUME)) {
            LogUtils.i("Adding the increasing volume column to %s", table);
            db.execSQL("ALTER TABLE " + table
                    + " ADD COLUMN " + ClockContract.AlarmsColumns.INCREASING_VOLUME
                    + " INTEGER NOT NULL DEFAULT 0;");
        }
    }

    /**
     * Whether {@code table} has all of {@code columnsV8}, and every other column has a default or
     * accepts null, so that both this app and GrapheneOS's Clock can insert rows. The increasing
     * volume column may be missing: {@link #onOpen} adds it.
     */
    private static boolean fitsVersion8(SQLiteDatabase db, String table, String[] columnsV8) {
        final Map<String, Boolean> columns = readColumns(db, table);
        for (String column : columnsV8) {
            if (columns.remove(column) == null) {
                return false;
            }
        }
        columns.remove(ClockContract.AlarmsColumns.INCREASING_VOLUME);
        for (boolean optional : columns.values()) {
            if (!optional) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the columns of {@code table}, none if there is no such table, each mapped to whether
     * an insert may leave it out: it has a default, accepts null or is the row ID.
     */
    private static Map<String, Boolean> readColumns(SQLiteDatabase db, String table) {
        final Map<String, Boolean> columns = new HashMap<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            final int name = cursor.getColumnIndexOrThrow("name");
            final int notNull = cursor.getColumnIndexOrThrow("notnull");
            final int defaultValue = cursor.getColumnIndexOrThrow("dflt_value");
            final int primaryKey = cursor.getColumnIndexOrThrow("pk");
            while (cursor.moveToNext()) {
                columns.put(cursor.getString(name).toLowerCase(Locale.ROOT),
                        cursor.getInt(notNull) == 0 || !cursor.isNull(defaultValue)
                                || cursor.getInt(primaryKey) != 0);
            }
        }
        return columns;
    }

    long fixAlarmInsert(ContentValues values) {
        // Why are we doing this? Is this not a programming bug if we try to
        // insert an already used id?
        final SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long rowId;
        try {
            // Check if we are trying to re-use an existing id.
            final Object value = values.get(ClockContract.AlarmsColumns._ID);
            if (value != null) {
                long id = (Long) value;
                if (id > -1) {
                    final String[] columns = {ClockContract.AlarmsColumns._ID};
                    final String selection = ClockContract.AlarmsColumns._ID + " = ?";
                    final String[] selectionArgs = {String.valueOf(id)};
                    try (Cursor cursor = db.query(ALARMS_TABLE_NAME, columns, selection,
                            selectionArgs, null, null, null)) {
                        if (cursor.moveToFirst()) {
                            // Record exists. Remove the id so sqlite can generate a new one.
                            values.putNull(ClockContract.AlarmsColumns._ID);
                        }
                    }
                }
            }

            rowId = db.insert(ALARMS_TABLE_NAME, ClockContract.AlarmsColumns.RINGTONE, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (rowId < 0) {
            throw new SQLException("Failed to insert row");
        }
        LogUtils.v("Added alarm rowId = " + rowId);

        return rowId;
    }
}
