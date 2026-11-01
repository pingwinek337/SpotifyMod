package com.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ustawienia moda zapisywane w config/spotifyhud.json. */
public final class HudConfig {
	public enum Corner {
		TOP_RIGHT("Prawy górny"), TOP_LEFT("Lewy górny"), BOTTOM_RIGHT("Prawy dolny"), BOTTOM_LEFT("Lewy dolny");

		public final String label;

		Corner(String label) {
			this.label = label;
		}

		public Corner next() {
			Corner[] v = values();
			return v[(ordinal() + 1) % v.length];
		}

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_RIGHT || this == BOTTOM_LEFT;
		}
	}

	/** Gdzie wyświetlać napisy. */
	public enum LyricsMode {
		SCATTER_WORLD("Rozrzucone w 3D", true), SCATTER_SCREEN("Rozrzucone na ekranie", false),
		WORLD("Kolumna w 3D", true), LEFT("Lewa strona", false), RIGHT("Prawa strona", false),
		BOTTOM("Dół ekranu", false);

		public final String label;
		/** true = rysowane w świecie 3D (LyricsWorldRenderer), false = na ekranie (HudRenderer). */
		public final boolean world;

		LyricsMode(String label, boolean world) {
			this.label = label;
			this.world = world;
		}

		public LyricsMode next() {
			LyricsMode[] v = values();
			return v[(ordinal() + 1) % v.length];
		}
	}

	public LyricsMode lyricsMode = LyricsMode.SCATTER_WORLD;
	/** Kolor napisów jako RGB (bez alfy). */
	public int lyricsColor = 0xFFFFFF;
	/** Ile nadchodzących linijek pokazywać (jeszcze nieśpiewanych). */
	public int lyricsLinesAhead = 3;
	public float lyricsScale = 1.0f;

	public String clientId = "";
	public String accessToken = "";
	public String refreshToken = "";
	public long expiresAtMs = 0;
	public String displayName = "";

	public boolean hudEnabled = true;
	public boolean lyricsEnabled = false;
	public boolean showArt = true;
	public boolean showProgress = true;
	public Corner corner = Corner.TOP_RIGHT;
	public float scale = 1.0f;
	public float bgOpacity = 0.65f;
	public int lyricsOffsetMs = 0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static HudConfig instance;

	public static synchronized HudConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("spotifyhud.json");
	}

	private static HudConfig load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				HudConfig c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), HudConfig.class);
				if (c != null) {
					if (c.corner == null) c.corner = Corner.TOP_RIGHT;
					if (c.lyricsMode == null) c.lyricsMode = LyricsMode.SCATTER_WORLD;
					if (c.lyricsScale <= 0) c.lyricsScale = 1.0f;
					c.lyricsLinesAhead = Math.max(0, Math.min(6, c.lyricsLinesAhead));
					c.lyricsColor &= 0xFFFFFF;
					return c;
				}
			}
		} catch (Exception e) {
			SpotifyHudMod.LOG.warn("Nie udało się wczytać configu", e);
		}
		return new HudConfig();
	}

	public synchronized void save() {
		try {
			Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SpotifyHudMod.LOG.warn("Nie udało się zapisać configu", e);
		}
	}

	public boolean connected() {
		return refreshToken != null && !refreshToken.isEmpty();
	}
}
