package com.android.alarmclock;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.deskclock.LogUtils;
import com.android.deskclock.R;

public class DigitalAppWidgetConfigurationActivity extends AppCompatActivity {
    private static final LogUtils.Logger LOGGER = new LogUtils.Logger("DigitalWidgetConfig");

    private int mAppWidgetId = INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        // Set up only a widget of this app's digital clock, whatever the intent carries.
        mAppWidgetId = readWidgetId(getIntent());
        if (!isDigitalClockWidget(mAppWidgetId)) {
            LOGGER.w("Not a digital clock widget: " + mAppWidgetId);
            finish();
            return;
        }

        setContentView(R.layout.digital_widget_configuration);

        View transparent = findViewById(R.id.preview_transparent);
        transparent.setOnClickListener(v -> onWidgetContainerClicked(false));
        View solid = findViewById(R.id.preview_solid);
        solid.setOnClickListener(v -> onWidgetContainerClicked(true));
    }

    /** The widget id of {@code intent}, or none if it has none or its extras cannot be read. */
    private static int readWidgetId(@Nullable Intent intent) {
        if (intent == null) {
            return INVALID_APPWIDGET_ID;
        }
        try {
            return intent.getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID);
        } catch (RuntimeException e) {
            // Extras that do not unparcel.
            return INVALID_APPWIDGET_ID;
        }
    }

    /** Whether {@code widgetId} is a widget of this app's digital clock provider. */
    private boolean isDigitalClockWidget(int widgetId) {
        if (widgetId == INVALID_APPWIDGET_ID) {
            return false;
        }
        final AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId);
        return info != null && new ComponentName(this, DigitalAppWidgetProvider.class)
                .equals(info.provider);
    }

    private void onWidgetContainerClicked(boolean isSolid) {
        WidgetUtils.saveWidgetMode(this, mAppWidgetId, isSolid);
        AppWidgetManager wm = AppWidgetManager.getInstance(this);
        DigitalAppWidgetProvider.updateAppWidget(this, wm, mAppWidgetId);

        Intent result = new Intent();
        result.putExtra(EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
