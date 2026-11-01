package com.spotifyhud.spotify;

import java.util.List;

/** Zsynchronizowany tekst piosenki (format LRC). */
public final class LyricsData {
	public enum State { LOADING, READY, NONE }

	public record Line(long timeMs, String text) {
	}

	public final String trackId;
	public final State state;
	public final List<Line> lines;

	private LyricsData(String trackId, State state, List<Line> lines) {
		this.trackId = trackId;
		this.state = state;
		this.lines = lines;
	}

	public static LyricsData loading(String id) {
		return new LyricsData(id, State.LOADING, List.of());
	}

	public static LyricsData none(String id) {
		return new LyricsData(id, State.NONE, List.of());
	}

	public static LyricsData ready(String id, List<Line> lines) {
		return new LyricsData(id, State.READY, lines);
	}

	/** Indeks ostatniej linii, której czas już minął (lub -1). */
	public int indexAt(long t) {
		int lo = 0, hi = lines.size() - 1, ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= t) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}
}
