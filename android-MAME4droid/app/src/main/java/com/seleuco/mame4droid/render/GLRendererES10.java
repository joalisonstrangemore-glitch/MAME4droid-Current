/*
 * This file is part of MAME4droid.
 *
 * Copyright (C) 2025 David Valdeita (Seleuco)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Linking MAME4droid statically or dynamically with other modules is
 * making a combined work based on MAME4droid. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * In addition, as a special exception, the copyright holders of MAME4droid
 * give you permission to combine MAME4droid with free software programs
 * or libraries that are released under the GNU LGPL and with code included
 * in the standard release of MAME under the MAME License (or modified
 * versions of such code, with unchanged license). You may copy and
 * distribute such a system following the terms of the GNU GPL for MAME4droid
 * and the licenses of the other code concerned, provided that you include
 * the source code of that other code when and as the GNU GPL requires
 * distribution of source code.
 *
 * Note that people who make modified versions of MAME4idroid are not
 * obligated to grant this special exception for their modified versions; it
 * is their choice whether to do so. The GNU General Public License
 * gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version
 * which carries forward this exception.
 *
 * MAME4droid is dual-licensed: Alternatively, you can license MAME4droid
 * under a MAME license, as set out in http://mamedev.org/
 */

package com.seleuco.mame4droid.render;

import android.graphics.Color;
import android.opengl.GLSurfaceView.Renderer;
import android.util.Log;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.widgets.WarnWidget;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;

public class GLRendererES10 implements Renderer, IGLRenderer {

	protected int emuTextureId = -1;

	private final int[] mCrop;

	protected ByteBuffer byteBuffer = null;
	protected boolean emuTextureInit = false;
	protected boolean isAltPath = false;

	protected boolean smooth = false;

	protected MAME4droid mm = null;

	protected boolean warn = false;

	/**
	 * Sets the MAME4droid instance for the renderer.
	 * @param mm The MAME4droid application instance.
	 */
	public void setMAME4droid(MAME4droid mm) {
		this.mm = mm;
		if (mm == null) return;
		isAltPath = mm.getPrefsHelper().isAltGLPath();
	}

	public GLRendererES10() {
		mCrop = new int[4];
	}

	/**
	 * Notifies the renderer that the emulated screen size has changed.
	 * This forces the texture to be re-initialized in the next frame.
	 */
	public void changedEmulatedSize() {
		if (Emulator.getScreenBuffer() == null) return;
		byteBuffer = Emulator.getScreenBuffer();
		emuTextureInit = false;
	}

	/**
	 * Calculates the next power-of-two size for a given dimension.
	 * This is required for older OpenGL ES 1.0 implementations.
	 * @param gl The GL10 instance.
	 * @param size The original dimension.
	 * @return The smallest power-of-two size greater than or equal to the original size.
	 */
	private int getP2Size(GL10 gl, int size) {
		int p2Size = 1;
		while (p2Size < size) {
			p2Size <<= 1;
		}
		return p2Size;
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Log.v("mm", "onSurfaceCreated ");

		// Basic OpenGL ES 1.0 configuration
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // Changed to black for a cleaner look
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		gl.glShadeModel(GL10.GL_FLAT);
		gl.glEnable(GL10.GL_TEXTURE_2D);

		gl.glDisable(GL10.GL_DITHER);
		gl.glDisable(GL10.GL_LIGHTING);
		gl.glDisable(GL10.GL_BLEND);
		gl.glDisable(GL10.GL_CULL_FACE);
		gl.glDisable(GL10.GL_DEPTH_TEST);
		// Note: GL_MULTISAMPLE is an extension and might not be available
		// gl.glDisable(GL10.GL_MULTISAMPLE);

		emuTextureInit = false;
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int w, int h) {
		Log.v("mm", "sizeChanged: ==> new Viewport: [" + w + "," + h + "]");

		gl.glViewport(0, 0, w, h);

		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		gl.glOrthof(0f, w, h, 0f, -1f, 1f);
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();

		gl.glFrontFace(GL10.GL_CCW);

		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		emuTextureInit = false;
	}

	protected boolean isSmooth() {
		return Emulator.isEmuFiltering();
	}

	/**
	 * Releases the OpenGL texture resource.
	 * @param gl The GL10 instance.
	 */
	private void releaseTexture(GL10 gl) {
		if (emuTextureId != -1) {
			gl.glDeleteTextures(1, new int[]{emuTextureId}, 0);
			emuTextureId = -1; // Resetting ID to prevent double frees
		}
	}


	public void dispose(GL10 gl) {
		releaseTexture(gl);
	}

	/**
	 * Creates or updates the emulator texture.
	 * It handles texture creation only when necessary (first time or filter change).
	 * @param gl The GL10 instance.
	 */
	protected void createEmuTexture(final GL10 gl) {
		if (gl == null) return;

		// Recreate texture if it doesn't exist or if filter setting has changed
		if (emuTextureId == -1 || smooth != isSmooth()) {
			releaseTexture(gl); // Ensure old texture is deleted before creating a new one

			int[] mTextureNameWorkspace = new int[1];
			gl.glGenTextures(1, mTextureNameWorkspace, 0);
			emuTextureId = mTextureNameWorkspace[0];

			gl.glBindTexture(GL10.GL_TEXTURE_2D, emuTextureId);

			smooth = isSmooth();
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, smooth ? GL10.GL_LINEAR : GL10.GL_NEAREST);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, smooth ? GL10.GL_LINEAR : GL10.GL_NEAREST);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
			gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

			emuTextureInit = false;
		}

		// Initialize texture if not yet done
		if (!emuTextureInit) {
			gl.glBindTexture(GL10.GL_TEXTURE_2D, emuTextureId);
			int p2Width = getP2Size(gl, Emulator.getEmulatedWidth());
			int p2Height = getP2Size(gl, Emulator.getEmulatedHeight());
			ByteBuffer dummyBuffer = ByteBuffer.allocateDirect(p2Width * p2Height * 4);
			dummyBuffer.order(ByteOrder.nativeOrder());
			gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, p2Width, p2Height, 0, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, dummyBuffer);
			emuTextureInit = true;
		}

		final int error = gl.glGetError();
		if (error != GL10.GL_NO_ERROR) {
			Log.e("GLRender", "createEmuTexture GLError: " + error);
		}
	}

	@Override
	synchronized public void onDrawFrame(GL10 gl) {
		gl.glClearColor(0, 0, 0, 1.0f);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		if (byteBuffer == null) {
			ByteBuffer buf = Emulator.getScreenBuffer();
			if (buf == null) return;
			byteBuffer = buf;
		}

		byteBuffer.rewind();
		byteBuffer.order(ByteOrder.nativeOrder());

		try {
			createEmuTexture(gl);
		} catch (OutOfMemoryError e) {
			if (!warn) {
				new WarnWidget.WarnWidgetHelper(mm, "Not enough memory to create texture!", 5, Color.RED, true);
				warn = true;
			}
			return;
		}

		gl.glBindTexture(GL10.GL_TEXTURE_2D, emuTextureId);

		int width = Emulator.getEmulatedWidth();
		int height = Emulator.getEmulatedHeight();

		// Update the texture with the latest emulated frame data
		gl.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, byteBuffer);

		// Define the cropping rectangle for the texture
		mCrop[0] = 0;
		mCrop[1] = height;
		mCrop[2] = width;
		mCrop[3] = -height;

		((GL11) gl).glTexParameteriv(GL10.GL_TEXTURE_2D, GL11Ext.GL_TEXTURE_CROP_RECT_OES, mCrop, 0);

		// Draw the cropped texture to the screen
		((GL11Ext) gl).glDrawTexiOES(0, 0, 0, Emulator.getWindow_width(), Emulator.getWindow_height());

		final int error = gl.glGetError();
		if (error != GL10.GL_NO_ERROR) {
			Log.e("GLRender", "onDrawFrame GLError: " + error);
		}
	}
}
