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
 * Wspólny model napisów dla wszystkich trybów wyświetlania:
 * poprzednia linijka (przyciemniona), aktualna (wyróżniona) i kilka nadchodzących.
 */
public final class LyricsView {
	/** offset: -1 = poprzednia, 0 = aktualnie śpiewana, 1..n = nadchodzące. */
	public record Entry(String text, int offset, float fade, int index) {
		public boolean current() {
			return offset == 0;
		}
	}

	/**
	 * Rozrzucone pozycje linijek: {przesunięcie w poziomie w stopniach (+ = w prawo),
	 * przesunięcie w pionie w stopniach (+ = w dół)}. Pozycja zależy od numeru linijki w piosence,
	 * więc linijka stoi w jednym miejscu, dopóki nie przestanie być widoczna.
	 */
	private static final float[][] SLOTS = {
			{0, 2}, {36, 9}, {-36, 4}, {18, -7}, {-20, -10}, {40, -4}, {-40, -6}, {2, 12}
	};

	public static float[] slot(int index) {
		return SLOTS[Math.floorMod(index, SLOTS.length)];
	}

	private static final Map<String, List<OrderedText>> WRAP_CACHE = new HashMap<>();

	private LyricsView() {
	}

	public static List<Entry> build(HudConfig cfg, Playback pb, LyricsData ld, long t) {
		if (pb == null || ld == null || ld.state != LyricsData.State.READY || !ld.trackId.equals(pb.id())) {
			return List.of();
		}
		int idx = ld.indexAt(t);
		List<Entry> out = new ArrayList<>();

		if (idx >= 0) {
			LyricsData.Line cur = ld.lines.get(idx);
			if (!cur.text().isBlank()) {
				for (int i = idx - 1; i >= 0; i--) {
					String tx = ld.lines.get(i).text();
					if (!tx.isBlank()) {
						out.add(new Entry(tx, -1, 1f, i));
						break;
					}
				}
				float f = Math.max(0f, Math.min(1f, (t - cur.timeMs()) / 250f));
				out.add(new Entry(cur.text(), 0, f, idx));
			}
		}

		int added = 0;
		for (int i = idx + 1; i < ld.lines.size() && added < cfg.lyricsLinesAhead; i++) {
			String tx = ld.lines.get(i).text();
			if (tx.isBlank()) continue;
			out.add(new Entry(tx, ++added, 1f, i));
		}
		return out;
	}

	/** Przezroczystość wpisu (0..1): poprzednia słabo widoczna, aktualna pełna, nadchodzące coraz bledsze. */
	public static float alpha(int offset, float fade) {
		if (offset < 0) return 0.38f;
		if (offset == 0) return 0.25f + 0.75f * fade;
		return Math.max(0.3f, 0.78f - 0.12f * offset);
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
		int a = (int) (0x80 * Math.max(0.35f, entryAlpha));
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
