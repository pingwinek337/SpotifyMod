package com.spotifyhud.hud;

import com.spotifyhud.HudConfig;
import com.spotifyhud.spotify.LyricsData;
import com.spotifyhud.spotify.Playback;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wspólny model napisów dla wszystkich trybów wyświetlania: tylko to, co jest aktualnie śpiewane,
 * plus poprzednia linijka powoli gasnąca. Żadnego podglądu linijek, których jeszcze nie słychać.
 */
public final class LyricsView {
	/** offset: -1 = poprzednia (gaśnie), 0 = aktualnie śpiewana. */
	public record Entry(String text, int offset, float fade, int index) {
		public boolean current() {
			return offset == 0;
		}
	}

	/** Strona ekranu/sceny na podstawie numeru linijki: 0 = lewo, 1 = środek, 2 = prawo. Zawsze inna niż poprzednia. */
	public static int side(int index) {
		return Math.floorMod(index, 3);
	}

	/** Jak długo (ms) poprzednia linijka gaśnie, zanim zniknie całkiem. */
	private static final long PREV_FADE_MS = 1400;
	/** Jak długo (ms) aktualna linijka narasta po starcie. */
	private static final long CUR_FADE_MS = 450;

	private static final Map<String, List<OrderedText>> WRAP_CACHE = new HashMap<>();

	private LyricsView() {
	}

	public static List<Entry> build(HudConfig cfg, Playback pb, LyricsData ld, long t) {
		if (pb == null || ld == null || ld.state != LyricsData.State.READY || !ld.trackId.equals(pb.id())) {
			return List.of();
		}
		int idx = ld.indexAt(t);
		if (idx < 0) return List.of();

		LyricsData.Line cur = ld.lines.get(idx);
		if (cur.text().isBlank()) return List.of();
		List<Entry> out = new ArrayList<>();

		for (int i = idx - 1; i >= 0; i--) {
			String tx = ld.lines.get(i).text();
			if (!tx.isBlank()) {
				float since = t - cur.timeMs();
				float prevFade = Math.max(0f, 1f - since / PREV_FADE_MS);
				if (prevFade > 0.02f) out.add(new Entry(tx, -1, prevFade, i));
				break;
			}
		}

		float f = Math.max(0f, Math.min(1f, (t - cur.timeMs()) / (float) CUR_FADE_MS));
		out.add(new Entry(cur.text(), 0, f, idx));
		return out;
	}

	/** Przezroczystość wpisu (0..1): poprzednia gaśnie w czasie, aktualna narasta przy starcie. */
	public static float alpha(int offset, float fade) {
		if (offset < 0) return 0.15f + 0.45f * fade; // fade tu = prevFade (maleje do 0)
		return 0.3f + 0.7f * fade;
	}

	/** Kolor napisów (ARGB) wybrany przez gracza, z przezroczystością zależną od wpisu. */
	public static int color(HudConfig cfg, int offset, float fade) {
		int a = Math.max(0x10, Math.min(0xFF, (int) (alpha(offset, fade) * 255)));
		return (a << 24) | (cfg.lyricsColor & 0xFFFFFF);
	}

	/** Czy wybrany kolor jest ciemny (wtedy tło pod napisem jest jasne, żeby było widać tekst). */
	public static boolean dark(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return (0.299 * r + 0.587 * g + 0.114 * b) < 100;
	}

	/** Kolor tła pod napisem (ARGB) z uwzględnieniem przezroczystości wpisu. */
	public static int background(HudConfig cfg, float entryAlpha) {
		int base = dark(cfg.lyricsColor) ? 0xFFFFFF : 0x000000;
		int a = (int) (0x80 * Math.max(0.3f, entryAlpha));
		return (a << 24) | base;
	}

	/** Zawijanie tekstu z cache (liczone raz na linijkę, nie co klatkę). */
	public static List<OrderedText> wrap(TextRenderer tr, String text, int width, boolean bold) {
		String key = width + (bold ? "B" : "N") + text;
		List<OrderedText> r = WRAP_CACHE.get(key);
		if (r == null) {
			if (WRAP_CACHE.size() > 160) WRAP_CACHE.clear();
			MutableText t = Text.literal(text);
			if (bold) t.formatted(Formatting.BOLD);
			r = tr.wrapLines(t, width);
			WRAP_CACHE.put(key, r);
		}
		return r;
	}
}
