/*
 * This file is part of MAME4droid.
 *
 * Copyright (C) 2024 David Valdeita (Seleuco)
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

package com.seleuco.mame4droid.input;

import java.util.ArrayList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.view.MotionEvent;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.helpers.DialogHelper;
import com.seleuco.mame4droid.helpers.PrefsHelper;

public class ControlCustomizer {

	private static boolean enabled = false;

	// Constant for the movement snap-to-grid threshold
	private static final int SNAP_TO_GRID = 5;

	// Coordinates of the initial touch position
	private int initialTouchX = 0;
	private int initialTouchY = 0;

	// Initial offset of the control when it starts to be dragged
	private int initialXOffset = 0;
	private int initialYOffset = 0;

	private InputValue valueMoved = null;

	protected MAME4droid mm = null;

	// New Rect for the save button
	private Rect saveButtonRect;
	// Button dimensions
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 80;
	// Padding for the text within the button
	private static final int TEXT_PADDING = 10;

	/**
	 * Activates or deactivates the control customization mode.
	 * @param enabled The state of the mode.
	 */
	public static void setEnabled(boolean enabled) {
		ControlCustomizer.enabled = enabled;
	}

	/**
	 * Returns whether the customization mode is active.
	 * @return true if active, false otherwise.
	 */
	public static boolean isEnabled() {
		return enabled;
	}

	/**
	 * Assigns the main MAME4droid instance.
	 * @param value The MAME4droid instance.
	 */
	public void setMAME4droid(MAME4droid value) {
		mm = value;
	}

	/**
	 * Discards the position changes of the control layout.
	 * Resets temporary offsets to zero.
	 */
	public void discardDefinedControlLayout() {
		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		for (InputValue iv : values) {
			iv.setOffsetTMP(0, 0);
			if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
				mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
			}
		}
		mm.getInputView().updateImages();
	}

	/**
	 * Saves the current position of the controls to preferences.
	 */
	public void saveDefinedControlLayout() {
		StringBuilder definedStr = new StringBuilder();
		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		boolean first = true;
		for (InputValue iv : values) {
			// Commit temporary offset changes to permanent offsets
			iv.commitChanges();
			if (iv.getXoff() != 0 || iv.getYoff() != 0) {
				if (!first) {
					definedStr.append(",");
				}
				definedStr.append(iv.getType()).append(",").append(iv.getValue()).append(",").append(iv.getXoff()).append(",").append(iv.getYoff());
				first = false;
			}
		}
		if (mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_LANDSCAPE) {
			mm.getPrefsHelper().setDefinedControlLayoutLand(definedStr.toString());
		} else {
			mm.getPrefsHelper().setDefinedControlLayoutPortrait(definedStr.toString());
		}
	}

	/**
	 * Reads and applies the saved control position configuration.
	 */
	public void readDefinedControlLayout() {
		// Do not apply if in non-full portrait mode
		if (mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_PORTRAIT && !Emulator.isPortraitFull()) {
			return;
		}

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
		String definedStr = (mm.getMainHelper().getscrOrientation() == Configuration.ORIENTATION_LANDSCAPE)
			? mm.getPrefsHelper().getDefinedControlLayoutLand()
			: mm.getPrefsHelper().getDefinedControlLayoutPortrait();

		if (definedStr != null && !definedStr.isEmpty()) {
			String[] tokens = definedStr.split(",");
			if (tokens.length % 4 != 0) {
				// Handle error in string format
				return;
			}

			for (int i = 0; i < tokens.length; i += 4) {
				try {
					int type = Integer.parseInt(tokens[i]);
					int value = Integer.parseInt(tokens[i + 1]);
					int xoff = Integer.parseInt(tokens[i + 2]);
					int yoff = Integer.parseInt(tokens[i + 3]);

					for (InputValue iv : values) {
						if (iv.getType() == type && iv.getValue() == value) {
							iv.setOffset(xoff, yoff);
							if (type == TouchController.TYPE_ANALOG_RECT) {
								mm.getInputHandler().getTouchStick().setStickArea(iv.getRect());
							}
							break;
						}
					}
				} catch (NumberFormatException e) {
					// Log the error for debugging if the format is incorrect
					e.printStackTrace();
				}
			}
		}
		mm.getInputView().updateImages();
	}

	/**
	 * Updates the position of related rectangles for the moved control.
	 */
	protected void updateRelatedRects() {
		if (valueMoved == null) return;

		ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();

		// Control type that has an associated image
		if (valueMoved.getType() == TouchController.TYPE_BUTTON_RECT) {
			for (InputValue iv : values) {
				if (iv.getType() == TouchController.TYPE_BUTTON_IMG && iv.getValue() == valueMoved.getValue()) {
					iv.setOffsetTMP(valueMoved.getXoff_tmp(), valueMoved.getYoff_tmp());
					break;
				}
			}
		} else if (valueMoved.getType() == TouchController.TYPE_STICK_IMG || valueMoved.getType() == TouchController.TYPE_ANALOG_RECT) {
			// Control types that have an associated stick
			for (InputValue iv : values) {
				if (iv.getType() == TouchController.TYPE_STICK_RECT || iv.getType() == TouchController.TYPE_STICK_IMG || iv.getType() == TouchController.TYPE_ANALOG_RECT) {
					iv.setOffsetTMP(valueMoved.getXoff_tmp(), valueMoved.getYoff_tmp());
				}
				if (iv.getType() == TouchController.TYPE_ANALOG_RECT) {
					mm.getInputHandler().getTouchStick().setStickArea(valueMoved.getRect());
				}
			}
		}
	}

	/**
	 * Handles screen motion events to drag controls.
	 * @param event The motion event.
	 */
	public void handleMotion(MotionEvent event) {
		int action = event.getActionMasked();
		int x = (int) event.getX();
		int y = (int) event.getY();

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				// Check if the save button was touched
				if (saveButtonRect != null && saveButtonRect.contains(x, y)) {
					// Logic to save the layout
					mm.showDialog(DialogHelper.DIALOG_FINISH_CUSTOM_LAYOUT);

					mm.getInputView().invalidate();
					return;
				}

				// If not, look for a control to move
				ArrayList<InputValue> values = mm.getInputHandler().getTouchController().getAllInputData();
				for (InputValue iv : values) {
					// Check the type of control that can be moved
					if ((iv.getType() == TouchController.TYPE_BUTTON_RECT || iv.getType() == TouchController.TYPE_STICK_IMG || iv.getType() == TouchController.TYPE_ANALOG_RECT)
						&& iv.getRect().contains(x, y)) {
						valueMoved = iv;
						initialTouchX = x;
						initialTouchY = y;
						initialXOffset = iv.getXoff_tmp();
						initialYOffset = iv.getYoff_tmp();
						break;
					}
				}
				break;

			case MotionEvent.ACTION_MOVE:
				if (valueMoved != null) {
					int deltaX = x - initialTouchX;
					int deltaY = y - initialTouchY;

					// Snap-to-grid adjustment
					int newXOffset = initialXOffset + (deltaX / SNAP_TO_GRID) * SNAP_TO_GRID;
					int newYOffset = initialYOffset + (deltaY / SNAP_TO_GRID) * SNAP_TO_GRID;

					valueMoved.setOffsetTMP(newXOffset, newYOffset);
					updateRelatedRects();

					// Only invalidate if coordinates have changed
					if (deltaX != 0 || deltaY != 0) {
						mm.getInputView().updateImages();
						mm.getInputView().invalidate();
					}
				}
				break;

			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				valueMoved = null;
				mm.getInputView().invalidate();
				break;
		}
	}

	/**
	 * Draws the control rectangles for customization mode and the save button.
	 * @param canvas The canvas to draw on.
	 */
	public void draw(Canvas canvas) {
		if (canvas == null) return;

		// Draw the save button
		Paint pButton = new Paint();
		// Bright yellow color
		pButton.setColor(Color.YELLOW);
		pButton.setStyle(Style.FILL);
		pButton.setAlpha(200);

		int centerX = canvas.getWidth() / 2;
		//int topY = 20; // Fixed position at the top
		int centerY = canvas.getHeight() / 2;

		//saveButtonRect = new Rect(centerX - BUTTON_WIDTH / 2, topY, centerX + BUTTON_WIDTH / 2, topY + BUTTON_HEIGHT);
		saveButtonRect = new Rect(centerX - BUTTON_WIDTH / 2, centerY - BUTTON_HEIGHT / 2, centerX + BUTTON_WIDTH / 2, centerY + BUTTON_HEIGHT / 2);
		canvas.drawRect(saveButtonRect, pButton);

		// Draw the button text
		Paint pText = new Paint();
		pText.setColor(Color.BLACK); // Black text for contrast with yellow
		pText.setTextSize(30);
		pText.setTypeface(Typeface.DEFAULT_BOLD);
		pText.setTextAlign(Paint.Align.CENTER);

		// Calculate and adjust text size to fit with padding
		String text = "SAVE LAYOUT";
		float textWidth = pText.measureText(text);

		// Check if the text fits with the desired padding
		if (textWidth + (2 * TEXT_PADDING) > saveButtonRect.width()) {
			// Text is too large, adjust the size
			float scale = (float) (saveButtonRect.width() - (2 * TEXT_PADDING)) / textWidth;
			pText.setTextSize(pText.getTextSize() * scale);
		}

		// Draw the text centered in the button
		Rect textBounds = new Rect();
		pText.getTextBounds(text, 0, text.length(), textBounds);
		float textY = saveButtonRect.centerY() - ((pText.descent() + pText.ascent()) / 2);

		canvas.drawText(text, saveButtonRect.centerX(), textY, pText);

		// Draw the existing control rectangles
		ArrayList<InputValue> ids = mm.getInputHandler().getTouchController().getAllInputData();
		Paint p2 = new Paint();
		p2.setARGB(30, 255, 255, 255);
		p2.setStyle(Style.FILL);

		for (InputValue v : ids) {
			Rect r = v.getRect();
			if (r != null) {
				boolean draw = false;
				if (v.getType() == TouchController.TYPE_BUTTON_RECT) {
					draw = true;
				} else if (mm.getPrefsHelper().getControllerType() == PrefsHelper.PREF_DIGITAL_DPAD && v.getType() == TouchController.TYPE_STICK_RECT) {
					draw = true;
				} else if (mm.getPrefsHelper().getControllerType() != PrefsHelper.PREF_DIGITAL_DPAD && v.getType() == TouchController.TYPE_ANALOG_RECT) {
					draw = true;
				}

				if (draw) {
					canvas.drawRect(r, p2);
				}
			}
		}
	}
}
