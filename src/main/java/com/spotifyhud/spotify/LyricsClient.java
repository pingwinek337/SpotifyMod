package com.spotifyhud.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spotifyhud.spotify.LyricsData.Line;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pobiera teksty z LRCLIB (https://lrclib.net) - darmowe API, bez klucza.
 * Spotify nie udostępnia tekstów przez publiczne API, dlatego używamy zewnętrznego źródła.
 */
final class LyricsClient {
	private static final String BASE = "https://lrclib.net/api";
	private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

	private LyricsClient() {
	}

	static List<Line> fetch(Playback p) throws IOException {
		String artist = p.firstArtist();

		String getUrl = BASE + "/get?track_name=" + enc(p.title()) + "&artist_name=" + enc(artist)
				+ "&album_name=" + enc(p.album()) + "&duration=" + (p.durationMs() / 1000);
		Http.Response r = Http.send("GET", getUrl, Map.of(), null);
		if (r.code() == 200) {
			List<Line> lines = parse(str(JsonParser.parseString(r.text()).getAsJsonObject(), "syncedLyrics"));
			if (!lines.isEmpty()) return lines;
		}

		String q = clean(p.title()) + " " + artist;
		Http.Response s = Http.send("GET", BASE + "/search?q=" + enc(q), Map.of(), null);
		if (s.code() != 200) return List.of();
		JsonArray arr = JsonParser.parseString(s.text()).getAsJsonArray();
		long targetSec = p.durationMs() / 1000;

		List<JsonObject> candidates = new ArrayList<>();
		for (JsonElement e : arr) {
			JsonObject o = e.getAsJsonObject();
			if (!str(o, "syncedLyrics").isBlank()) candidates.add(o);
		}
		candidates.sort(Comparator.comparingDouble(o -> Math.abs(num(o, "duration") - targetSec)));
		if (candidates.isEmpty()) return List.of();
		JsonObject best = candidates.get(0);
		if (Math.abs(num(best, "duration") - targetSec) > 8) return List.of();
		return parse(str(best, "syncedLyrics"));
	}

	static List<Line> parse(String lrc) {
		List<Line> out = new ArrayList<>();
		if (lrc == null) return out;
		for (String raw : lrc.split("\\R")) {
			List<Long> times = new ArrayList<>();
			Matcher m = TS.matcher(raw);
			int pos = 0;
			while (true) {
				m.region(pos, raw.length());
				if (!m.lookingAt()) break;
				times.add(toMs(m));
				pos = m.end();
			}
			if (times.isEmpty()) continue;
			String text = raw.substring(pos).trim();
			for (long t : times) out.add(new Line(t, text));
		}
		out.sort(Comparator.comparingLong(Line::timeMs));
		return out;
	}

	private static long toMs(Matcher m) {
		long ms = Long.parseLong(m.group(1)) * 60_000L + Long.parseLong(m.group(2)) * 1000L;
		String frac = m.group(3);
		if (frac != null) {
			long f = Long.parseLong(frac);
			ms += switch (frac.length()) {
				case 1 -> f * 100;
				case 2 -> f * 10;
				default -> f;
			};
		}
		return ms;
	}

	private static String clean(String title) {
		return title.replaceAll("\\s*[-–]\\s*(Remaster(ed)?|Live|Single Version|Radio Edit|Mono|Stereo).*$", "")
				.replaceAll("\\s*\\(.*?\\)", "")
				.replaceAll("\\s*\\[.*?]", "")
				.trim();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static double num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
	}
}
