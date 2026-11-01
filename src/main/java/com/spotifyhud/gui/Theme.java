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

	/** Paleta kolorów napisów (RGB) i ich nazwy. */
	public static final int[] LYRIC_COLORS = {
			0xFFFFFF, 0x000000, 0xE5242B, 0xFF9A2E, 0xFFE14D,
			0x1DB954, 0x4DD0FF, 0x3B6BFF, 0xA455FF, 0xFF6FB5
	};
	public static final String[] LYRIC_NAMES = {
			"Biały", "Czarny", "Czerwony", "Pomarańczowy", "Żółty",
			"Zielony", "Błękitny", "Niebieski", "Fioletowy", "Różowy"
	};

	private Theme() {
	}

	public static String colorName(int rgb) {
		for (int i = 0; i < LYRIC_COLORS.length; i++) {
			if (LYRIC_COLORS[i] == (rgb & 0xFFFFFF)) return LYRIC_NAMES[i];
		}
		return "Własny";
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
