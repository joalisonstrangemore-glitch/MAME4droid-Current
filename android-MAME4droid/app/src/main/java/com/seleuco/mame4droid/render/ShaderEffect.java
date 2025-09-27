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
 * distribute a such a system following the terms of the GNU GPL for MAME4droid
 * and the licenses of the other code concerned, provided that you include
 * the source code of that other code when and as the GNU GPL requires
 * distribution of source code.
 *
 * Note that people who make modified versions of MAME4idroid are not
 * obligated to grant this special exception for their modified versions; it
 * is their choice whether to do so. The GNU General Public License
 * gives permission to release a modified version which carries forward this exception.
 *
 * MAME4droid is dual-licensed: Alternatively, you can license MAME4droid
 * under a MAME license, as set out in http://mamedev.org/
 */

package com.seleuco.mame4droid.render;

import android.opengl.GLES32;
import android.util.Log;
import com.seleuco.mame4droid.MAME4droid;
import java.nio.FloatBuffer;
import java.util.HashMap;

/**
 * Class to handle the creation, use, and release of an OpenGL ES shader program.
 */
public class ShaderEffect {

	private static final String TAG = "ShaderEffect";
	// Constants for vertex data
	private static final int COORDS_PER_VERTEX = 2;
	private static final int VERTEX_COUNT = 4;

	private final MAME4droid mm;
	private int programHandle = -1;

	// Handles for vertex attributes and textures
	private int quadPositionHandle = -1;
	private int texPositionHandle = -1;
	private int textureUniformHandle = -1;
	private int viewProjectionMatrixHandle = -1;

	// Handles for uniforms specific to effect shaders
	private int inputSizeHandle = -1;
	private int outputSizeHandle = -1;
	private int textureSizeHandle = -1;
	private int frameCountHandle = -1;

	private boolean isEffectShader = false;

	public ShaderEffect(MAME4droid mm) {
		this.mm = mm;
	}

	/**
	 * Creates and links the shaders to form an OpenGL ES shader program.
	 * @param name The name of the shader file.
	 * @param version The GLES version (100 for GLES 2.0, 300 for GLES 3.0).
	 * @param isEffect Differentiates between a basic texture shader and a full-screen effect shader.
	 * @return true if the program was created successfully, otherwise false.
	 */
	public boolean create(String name, int version, boolean isEffect) {
		isEffectShader = isEffect;

		// Loads and compiles the vertex and fragment shaders
		int vertexShader = ShaderUtil.loadGLShader(TAG, mm, GLES32.GL_VERTEX_SHADER, name,
			new HashMap<String, Integer>() {{ put("VERTEX", 1); }}, version);
		int fragmentShader = ShaderUtil.loadGLShader(TAG, mm, GLES32.GL_FRAGMENT_SHADER, name,
			new HashMap<String, Integer>() {{ put("FRAGMENT", 1); }}, version);

		if (vertexShader <= 0 || fragmentShader <= 0) {
			Log.e(TAG, "Failed to load or compile shaders for: " + name);
			return false;
		}

		// Creates the shader program and links the shaders
		programHandle = GLES32.glCreateProgram();
		if (programHandle == 0) {
			Log.e(TAG, "glCreateProgram failed.");
			return false;
		}

		GLES32.glAttachShader(programHandle, vertexShader);
		GLES32.glAttachShader(programHandle, fragmentShader);
		GLES32.glLinkProgram(programHandle);

		// Detaches and deletes the individual shaders as they are no longer needed after linking
		GLES32.glDetachShader(programHandle, vertexShader);
		GLES32.glDetachShader(programHandle, fragmentShader);
		GLES32.glDeleteShader(vertexShader);
		GLES32.glDeleteShader(fragmentShader);

		// Check link status
		final int[] linkStatus = new int[1];
		GLES32.glGetProgramiv(programHandle, GLES32.GL_LINK_STATUS, linkStatus, 0);
		if (linkStatus[0] == 0) {
			Log.e(TAG, "Error linking program: " + GLES32.glGetProgramInfoLog(programHandle));
			dispose(); // Clean up the failed program
			return false;
		}

		// Gets the handles of the attribute and uniform locations.
		// It's crucial to check these handles. If they are -1, the shader does not use them,
		// and attempting to use them would be an error.
		quadPositionHandle = GLES32.glGetAttribLocation(programHandle, "VertexCoord");
		texPositionHandle = GLES32.glGetAttribLocation(programHandle, "TexCoord");
		textureUniformHandle = GLES32.glGetUniformLocation(programHandle, "Texture");
		viewProjectionMatrixHandle = GLES32.glGetUniformLocation(programHandle, "MVPMatrix");

		if (quadPositionHandle == -1 || texPositionHandle == -1 || textureUniformHandle == -1 || viewProjectionMatrixHandle == -1) {
			Log.e(TAG, "Could not get a required attribute or uniform location in shader: " + name);
			dispose();
			return false;
		}

		// Effect shaders have additional optional uniforms
		if (isEffectShader) {
			inputSizeHandle = GLES32.glGetUniformLocation(programHandle, "InputSize");
			outputSizeHandle = GLES32.glGetUniformLocation(programHandle, "OutputSize");
			textureSizeHandle = GLES32.glGetUniformLocation(programHandle, "TextureSize");
			frameCountHandle = GLES32.glGetUniformLocation(programHandle, "FrameCount");
		}

		return true;
	}

	public boolean isProgramReady() {
		return programHandle != -1;
	}

	/**
	 * Activates this shader program for rendering.
	 */
	public void useProgram() {
		GLES32.glUseProgram(programHandle);
	}

	/**
	 * Sets the model-view-projection matrix.
	 * @param matrix The projection matrix.
	 */
	public void setMVPMatrix(float[] matrix) {
		if (viewProjectionMatrixHandle != -1) {
			GLES32.glUniformMatrix4fv(viewProjectionMatrixHandle, 1, false, matrix, 0);
		}
	}

	/**
	 * Sets the uniforms specific to effect shaders.
	 * @param emuWidth Width of the emulated screen.
	 * @param emuHeight Height of the emulated screen.
	 * @param viewWidth Width of the surface view.
	 * @param viewHeight Height of the surface view.
	 * @param frame Current frame number.
	 */
	public void setUniforms(int emuWidth, int emuHeight, int viewWidth, int viewHeight, int frame) {
		if (isEffectShader) {
			// Only update uniforms if their handles were found
			if (textureSizeHandle != -1) GLES32.glUniform2f(textureSizeHandle, (float) emuWidth, (float) emuHeight);
			if (inputSizeHandle != -1) GLES32.glUniform2f(inputSizeHandle, (float) emuWidth, (float) emuHeight);
			if (outputSizeHandle != -1) GLES32.glUniform2f(outputSizeHandle, (float) viewWidth, (float) viewHeight);
			if (frameCountHandle != -1) GLES32.glUniform1i(frameCountHandle, frame);
		}
	}

	public int getTextureUniformHandle() {
		return textureUniformHandle;
	}

	/**
	 * Draws a quad on the screen.
	 * @param vertices The vertex buffer.
	 * @param texcoords The texture coordinate buffer.
	 */
	public void draw(FloatBuffer vertices, FloatBuffer texcoords) {
		if (!isProgramReady()) return;  // evita dibujar sin program preparado
		GLES32.glUseProgram(programHandle); // garantiza que el program está activo

		if (vertices != null) vertices.position(0);
		if (texcoords != null) texcoords.position(0);

		// Enables and assigns the vertex buffers
		GLES32.glEnableVertexAttribArray(quadPositionHandle);
		GLES32.glVertexAttribPointer(quadPositionHandle, COORDS_PER_VERTEX, GLES32.GL_FLOAT, false, 0, vertices);

		GLES32.glEnableVertexAttribArray(texPositionHandle);
		GLES32.glVertexAttribPointer(texPositionHandle, COORDS_PER_VERTEX, GLES32.GL_FLOAT, false, 0, texcoords);

		// Draws the quad
		GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT);

		// Disables the attribute arrays
		GLES32.glDisableVertexAttribArray(quadPositionHandle);
		GLES32.glDisableVertexAttribArray(texPositionHandle);
	}

	/**
	 * Releases the shader program resources.
	 */
	public void dispose() {
		if (programHandle != -1) {
			GLES32.glDeleteProgram(programHandle);
			programHandle = -1;
		}
	}
}
