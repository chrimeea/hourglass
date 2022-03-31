package com.prozium.hourglass;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float2;
import android.renderscript.RenderScript;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by cristian on 08.05.2017.
 */
//TODO: allow square dots
//TODO: configurable settings
public class Game extends GLSurfaceView {

    final GestureDetector fling;
    ScheduledExecutorService timer;
    float[] quads, color;
    boolean first = true;
    final Float2 pinch = new Float2();
    int TOTAL = 2000;
    float SCALE = 0.02f;
    float SPEED = 0.000002f;
    final static int VERTEXES_PER_QUAD = 6;
    final static int BYTES_PER_VERTEX = 4;
    final static int BYTES_PER_COLOR = 4;
    final static long FPS = 33;

    public Game(Context context, AttributeSet attrs) {
        super(context, attrs);
        fling = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(final MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(final MotionEvent event1, final MotionEvent event2, final float velocityX, final float velocityY) {
                onPause();
                if (timer != null) {
                    timer.shutdown();
                    try {
                        timer.awaitTermination(1, TimeUnit.MINUTES);
                    } catch (InterruptedException e) {
                    }
                }
                if (TOTAL == 2000) {
                    TOTAL = 4000;
                    SCALE = 0.01f;
                    SPEED = 0.0000004f;
                    //TOTAL = 20000;
                    //SCALE = 0.002f;
                    //SPEED = 0.00000002f;
                } else {
                    TOTAL = 2000;
                    SCALE = 0.02f;
                    SPEED = 0.000002f;
                }
                first = true;
                onResume();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        fling.onTouchEvent(event);
        return true;
    }

    void setPinch(final float x, final float y) {
        pinch.x = x * SPEED;
        pinch.y = y * SPEED;
    }

    void start(final float width, final float height, final Bitmap background) {
        if (getVisibility() == GLSurfaceView.VISIBLE) {
            final RenderScript rs = RenderScript.create(getContext());
            final ScriptC_game script = new ScriptC_game(rs);
            script.bind_quads(Allocation.createSized(rs, Element.F32_4(rs), VERTEXES_PER_QUAD * TOTAL));
            script.bind_color(Allocation.createSized(rs, Element.F32_4(rs), VERTEXES_PER_QUAD * TOTAL));
            script.bind_pos(Allocation.createSized(rs, Element.F32_2(rs), TOTAL));
            script.bind_next_pos(Allocation.createSized(rs, Element.F32_2(rs), TOTAL));
            script.bind_forces(Allocation.createSized(rs, Element.F32_2(rs), TOTAL));
            script.bind_next_forces(Allocation.createSized(rs, Element.F32_2(rs), TOTAL));
            final Float2 scaleToPixels = new Float2(SCALE * background.getWidth() / (2f * width), SCALE * background.getHeight() / (2f * height));
            final int bucketsSize = Math.max((int) (2f * height / SCALE + 1f) * (int) (2f * width / SCALE + 1f), (int) (background.getHeight() / scaleToPixels.y + 1f) * (int) ((background.getWidth() + 1f) / scaleToPixels.x + 1f));
            script.bind_buckets_start(Allocation.createSized(rs, Element.I16(rs), bucketsSize));
            script.bind_buckets_iteration(Allocation.createSized(rs, Element.I16(rs), bucketsSize));
            script.bind_hit(Allocation.createSized(rs, Element.F32_2(rs), (background.getHeight() + 1) * (background.getWidth() + 1)));
            script.bind_buckets_list(Allocation.createSized(rs, Element.I16(rs), TOTAL));
            script.bind_background(Allocation.createSized(rs, Element.BOOLEAN(rs), (background.getHeight() + 1) * (background.getWidth() + 1)));
            script.set_scale(SCALE);
            script.set_scale_to_pixels(scaleToPixels);
            script.set_width(width);
            script.set_height(height);
            script.set_width_pixel(background.getWidth() + 1);
            script.set_height_pixel(background.getHeight());
            quads = new float[VERTEXES_PER_QUAD * BYTES_PER_VERTEX * TOTAL];
            color = new float[VERTEXES_PER_QUAD * BYTES_PER_COLOR * TOTAL];
            script.forEach_root_init_background(Allocation.createFromBitmap(rs, background, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT));
            script.invoke_func_init_safe();
            script.invoke_func_init_pos(TOTAL);
            script.get_color().copyTo(color);
            script.forEach_root_make_quads(script.get_pos());
            script.get_quads().copyTo(quads);
            timer = new ScheduledThreadPoolExecutor(1);
            timer.execute(new Runnable() {

                @Override
                public void run() {
                    int i;
                    long t1, t2, t3;
                    script.forEach_root_init_hit(script.get_background());
                    first = false;
                    t1 = t3 = System.currentTimeMillis();
                    while (!timer.isShutdown()) {
                        script.set_pinch(pinch);
                        for (i = 0; i < 2; i++) {
                            script.invoke_func_make_buckets();
                            script.forEach_root_resolve_to_next_pos(script.get_pos());
                            script.forEach_root_resolve_to_pos(script.get_pos());
                        }
                        t2 = System.currentTimeMillis();
                        if (2L * t2 - t1 - t3 > FPS) {
                            script.forEach_root_make_quads(script.get_pos());
                            script.get_quads().copyTo(quads);
                            requestRender();
                            t1 = t2;
                        }
                        t3 = t2;
                    }
                    script.destroy();
                    rs.destroy();
                }
            });
        }
    }
}
