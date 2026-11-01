package com.spotifyhud.gui;

/** Kolory (ARGB) używane w GUI i HUD. */
public final class Theme {
	public static final int GREEN = 0xFF1DB954;
	public static final int PANEL = 0xF0121212;
	public static final int BORDER = 0xFF2A2A2A;
	public static final int BUTTON = 0xFF2B2B2B;
	public static final int BUTTON_GREEN = 0xFF1A8F42;
	public static final int BUTTON_RED = 0xFF8F2A2A;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int MUTED = 0xFF9A9A9A;

	private Theme() {
	}

	public static int lighten(int argb, float amount) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		r += (int) ((255 - r) * amount);
		g += (int) ((255 - g) * amount);
		b += (int) ((255 - b) * amount);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
