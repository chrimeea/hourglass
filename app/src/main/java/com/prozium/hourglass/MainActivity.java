package com.prozium.hourglass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.view.WindowManager;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

/**
 * Created by cristian on 08.05.2017.
 */

public class MainActivity extends android.app.Activity  implements SensorEventListener {

    Game game;
    AdView adView;
    SensorManager mSensorManager;

    @Override
    public final void onAccuracyChanged(final Sensor sensor, final int accuracy) {
    }

    @Override
    public final void onSensorChanged(final SensorEvent event) {
        if (game != null) {
            game.setPinch(event.values[0], -event.values[1]);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game.onPause();
        if (game.timer != null) {
            game.timer.shutdown();
        }
        adView.pause();
        mSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().hide();
        setContentView(R.layout.main);
        game = findViewById(R.id.glSurface);
        game.setEGLContextClientVersion(2);
        game.setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        game.setRenderer(new com.prozium.hourglass.Renderer(getResources(), game));
        game.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        adView = findViewById(R.id.adview);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                adView.loadAd(new AdRequest.Builder().build());
            }
        }, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game.first = true;
        game.onResume();
        adView.resume();
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
    }
}
