package com.spotifyhud.spotify;

/** Migawka tego, co aktualnie gra na Spotify. */
public record Playback(String id, String title, String artist, String album, long durationMs,
					   long progressMs, boolean playing, String artUrl, long fetchedAtMs) {

	/** Postęp z interpolacją między odpytaniami API. */
	public long currentProgress() {
		long p = progressMs;
		if (playing) p += System.currentTimeMillis() - fetchedAtMs;
		if (durationMs > 0) p = Math.min(p, durationMs);
		return Math.max(0, p);
	}

	public Playback withPlaying(boolean nowPlaying) {
		return new Playback(id, title, artist, album, durationMs, currentProgress(), nowPlaying, artUrl,
				System.currentTimeMillis());
	}

	public String firstArtist() {
		int i = artist.indexOf(", ");
		return i < 0 ? artist : artist.substring(0, i);
	}
}
