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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shader helper functions.
 */
public final class ShaderUtil {

	/**
	 * Loads and compiles an OpenGL ES shader.
	 * @param tag The tag for logging.
	 * @param mm The MAME4droid instance to access resources.
	 * @param type The shader type (GL_VERTEX_SHADER or GL_FRAGMENT_SHADER).
	 * @param filename The name of the shader file.
	 * @param defineValuesMap A map of #define values for the preprocessor.
	 * @param version The GLES version (100 for GLES 2.0, 300 for GLES 3.0).
	 * @return The handle of the compiled shader, or 0 if it fails.
	 */
	public static int loadGLShader(
		String tag, MAME4droid mm, int type, String filename, Map<String, Integer> defineValuesMap, int version) {

		String code;
		try {
			// Start reading with an empty set for tracking includes.
			code = readShaderFile(mm, filename, new HashSet<>());
		} catch (IOException e) {
			Log.e(tag, "Error reading shader: " + filename + " - " + e.getMessage());
			return 0;
		}

		// Pre-processes the shader code with #version and #define directives
		String defines = (version == 3) ? "#version 300 es\n" : "#version 100\n";
		for (Map.Entry<String, Integer> entry : defineValuesMap.entrySet()) {
			defines += "#define " + entry.getKey() + " " + entry.getValue() + "\n";
		}
		code = defines + code;

		// Compiles the shader code.
		int shader = GLES32.glCreateShader(type);
		if (shader == 0) {
			Log.e(tag, "Failed to create shader of type " + type);
			return 0;
		}
		GLES32.glShaderSource(shader, code);
		GLES32.glCompileShader(shader);

		// Gets the compilation status.
		final int[] compileStatus = new int[1];
		GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0);

		// If compilation failed, delete the shader and log the error.
		if (compileStatus[0] == 0) {
			Log.e(tag, "Error compiling shader " + filename + ": " + GLES32.glGetShaderInfoLog(shader));
			GLES32.glDeleteShader(shader);
			shader = 0;
		}

		return shader;
	}

	/**
	 * Checks for errors in the OpenGL ES error queue and throws an exception if found.
	 * @param tag The tag for logging.
	 * @param label A context label for the error.
	 */
	public static void checkGLError(String tag, String label) {
		int error;
		// Drain the queue of all errors.
		while ((error = GLES32.glGetError()) != GLES32.GL_NO_ERROR) {
			Log.e(tag, label + ": glError " + error);
			// Throwing a RuntimeException will crash the app. In a rendering loop, this might be
			// undesirable. For debugging, it's useful. In production, you might just log the error.
			throw new RuntimeException(label + ": glError " + error);
		}
	}

	/**
	 * Reads a shader file, recursively handling #include directives and preventing circular dependencies.
	 * @param mm The MAME4droid instance.
	 * @param filename The name of the file to read.
	 * @param visitedFiles A set of filenames currently in the include stack to detect circular includes.
	 * @return The content of the shader file as a string.
	 * @throws IOException if there is an error reading the file or a circular include is detected.
	 */
	private static String readShaderFile(MAME4droid mm, String filename, Set<String> visitedFiles)
		throws IOException {

		// Use File.separator for platform-independent path construction.
		String path = mm.getPrefsHelper().getInstallationDIR() + "shaders" + File.separator + filename;

		// Detect circular include dependency.
		if (!visitedFiles.add(filename)) {
			throw new IOException("Circular #include detected in file: " + filename);
		}

		try (InputStream inputStream = Files.newInputStream(Paths.get(path));
			 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.trim().split("\\s+", 2);
				if (tokens.length == 2 && tokens[0].equals("#include")) {
					String includeFilename = tokens[1].replace("\"", "");
					sb.append(readShaderFile(mm, includeFilename, visitedFiles));
				} else {
					sb.append(line).append("\n");
				}
			}
			return sb.toString();
		} finally {
			// After this file is done being processed, remove it from the visited set
			// so it can be included again by other files in a non-circular way.
			visitedFiles.remove(filename);
		}
	}
}
