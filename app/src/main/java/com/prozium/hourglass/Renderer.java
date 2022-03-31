package com.prozium.hourglass;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by cristian on 08.05.2017.
 */

public class Renderer implements GLSurfaceView.Renderer {

    final Game game;
    int mMVPMatrixHandle, backgroundTexture, circleTexture, loadingTexture;
    float width, height;
    FloatBuffer vertexBuffer;
    Bitmap backgroundBitmap;
    final Resources resources;
    final float[] mMVPMatrix = new float[16], mProjectionMatrix = new float[16], mViewMatrix = new float[16];
    final static int FLOAT_SIZE = 4;
    final static String vertexShaderCode = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 position, color;" +
            "varying vec2 texcoords;" +
            "varying vec4 c;" +
            "void main() {" +
            "gl_Position = uMVPMatrix * vec4(position.xy, 0.0, 1.0);" +
            "texcoords = position.zw;" +
            "c = color;" +
            "}";
    final static String fragmentShaderCode= "precision mediump float;" +
            "uniform sampler2D screentexture;" +
            "varying vec2 texcoords;" +
            "varying vec4 c;" +
            "void main() {" +
            "gl_FragColor = texture2D(screentexture, texcoords) * c;" +
            "}";

    Renderer(final Resources resources, final Game game) {
        this.game = game;
        this.resources = resources;
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        final int program = GLES20.glCreateProgram();
        int shader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(shader, vertexShaderCode);
        GLES20.glCompileShader(shader);
        GLES20.glAttachShader(program, shader);
        shader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(shader, fragmentShaderCode);
        GLES20.glCompileShader(shader);
        GLES20.glAttachShader(program, shader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);
        final int[] buffers = new int[3];
        GLES20.glGenTextures(3, buffers, 0);
        loadingTexture = loadTextureFromBitmap(BitmapFactory.decodeResource(resources, R.drawable.loading), buffers[1]);
        circleTexture = loadTextureFromBitmap(BitmapFactory.decodeResource(resources, R.drawable.circle), buffers[2]);
        BitmapFactory.Options opt = new BitmapFactory.Options();
        //opt.inScaled = false;
        backgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.background, opt);
        backgroundTexture = loadTextureFromBitmap(backgroundBitmap, buffers[0]);
        GLES20.glGenBuffers(1, buffers, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        int phandle = GLES20.glGetAttribLocation(program, "position");
        GLES20.glEnableVertexAttribArray(phandle);
        GLES20.glVertexAttribPointer(phandle, game.BYTES_PER_VERTEX, GLES20.GL_FLOAT, false, 0, 0);
        phandle = GLES20.glGetAttribLocation(program, "color");
        GLES20.glEnableVertexAttribArray(phandle);
        GLES20.glVertexAttribPointer(phandle, game.BYTES_PER_COLOR, GLES20.GL_FLOAT, false, 0, (game.TOTAL + 1) * game.VERTEXES_PER_QUAD * game.BYTES_PER_VERTEX * FLOAT_SIZE);
        mMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1f, 0f);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, game.VERTEXES_PER_QUAD * (game.TOTAL + 1) * (game.BYTES_PER_VERTEX + game.BYTES_PER_COLOR) * FLOAT_SIZE, null, GLES20.GL_DYNAMIC_DRAW);
    }

    @Override
    public void onDrawFrame(final GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexture);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, game.VERTEXES_PER_QUAD);
        if (game.first) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, loadingTexture);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, game.VERTEXES_PER_QUAD);
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, circleTexture);
        vertexBuffer.position(game.VERTEXES_PER_QUAD * game.BYTES_PER_VERTEX);
        vertexBuffer.mark();
        vertexBuffer.put(game.quads);
        vertexBuffer.reset();
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexBuffer.position() * FLOAT_SIZE, game.VERTEXES_PER_QUAD * game.TOTAL * game.BYTES_PER_VERTEX * FLOAT_SIZE, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, game.VERTEXES_PER_QUAD, game.VERTEXES_PER_QUAD * game.TOTAL);
    }

    @Override
    public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
        if ((game.timer == null || game.timer.isShutdown()) && backgroundBitmap != null) {
            if (width < height) {
                this.width = (float) width / height;
                this.height = 1f;
                Matrix.frustumM(mProjectionMatrix, 0, -this.width, this.width, -this.height, this.height, 3, 100);
            } else {
                this.height = (float) height / width;
                this.width = 1f;
                Matrix.frustumM(mProjectionMatrix, 0, -this.width, this.width, -this.height, this.height, 3, 100);
            }
            Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
            GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            vertexBuffer = ByteBuffer.allocateDirect(game.VERTEXES_PER_QUAD * (game.TOTAL + 1) * (game.BYTES_PER_VERTEX + game.BYTES_PER_COLOR) * FLOAT_SIZE).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertexBuffer.position(0);
            vertexBuffer.put(new float[] {-this.width, this.height, 0f, 1f});
            vertexBuffer.put(new float[] {-this.width, -this.height, 0f, 0f});
            vertexBuffer.put(new float[] {this.width, -this.height, 1f, 0f});
            vertexBuffer.put(new float[] {-this.width, this.height, 0f, 1f});
            vertexBuffer.put(new float[] {this.width, -this.height, 1f, 0f});
            vertexBuffer.put(new float[] {this.width, this.height, 1f, 1f});
            vertexBuffer.position(0);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, game.VERTEXES_PER_QUAD * game.BYTES_PER_VERTEX * FLOAT_SIZE, vertexBuffer);
            GLES20.glViewport(0, 0, width, height);
            game.start(this.width, this.height, backgroundBitmap);
            backgroundBitmap = null;
            vertexBuffer.position(game.VERTEXES_PER_QUAD * (game.TOTAL + 1) * game.BYTES_PER_VERTEX);
            vertexBuffer.mark();
            vertexBuffer.put(new float[] {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f});
            vertexBuffer.put(game.color);
            vertexBuffer.reset();
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, vertexBuffer.position() * FLOAT_SIZE, game.VERTEXES_PER_QUAD * game.BYTES_PER_COLOR * (game.TOTAL + 1) * FLOAT_SIZE, vertexBuffer);
        }
    }

    int loadTextureFromBitmap(final Bitmap bitmap, final int textureId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return textureId;
    }
}