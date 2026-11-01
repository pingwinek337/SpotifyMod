package com.spotifyhud.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spotifyhud.HudConfig;
import com.spotifyhud.SpotifyHudMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Odpytuje Spotify w tle i trzyma aktualny stan odtwarzania, okładkę oraz napisy. */
public final class SpotifyService {
	public static final SpotifyService INSTANCE = new SpotifyService();
	private static final String API = "https://api.spotify.com/v1";

	private final ScheduledExecutorService poller =
			Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "spotifyhud-poll"));
	private final ExecutorService worker = Executors.newCachedThreadPool(r -> daemon(r, "spotifyhud-worker"));

	private volatile Playback playback;
	private volatile LyricsData lyrics;
	private volatile String status = "";
	private volatile long backoffUntil;

	private volatile String artRequestedUrl = "";
	private volatile String artLoadedUrl = "";
	private volatile Identifier artId;
	private volatile int artW, artH;
	private int artCounter;
	private boolean started;

	private SpotifyService() {
	}

	private static Thread daemon(Runnable r, String name) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}

	public synchronized void start() {
		if (started) return;
		started = true;
		poller.scheduleWithFixedDelay(this::pollSafe, 1000, 2000, TimeUnit.MILLISECONDS);
	}

	public void pollSoon(long delayMs) {
		poller.schedule(this::pollSafe, delayMs, TimeUnit.MILLISECONDS);
	}

	// ---- stan dla HUD / GUI ----

	public Playback playback() {
		return playback;
	}

	public LyricsData lyrics() {
		return lyrics;
	}

	public String status() {
		return status;
	}

	public void setStatus(String s) {
		status = s == null ? "" : s;
	}

	public Identifier artIdFor(String url) {
		return url != null && url.equals(artLoadedUrl) ? artId : null;
	}

	public int artWidth() {
		return Math.max(1, artW);
	}

	public int artHeight() {
		return Math.max(1, artH);
	}

	public void reset() {
		playback = null;
		lyrics = null;
		status = "";
		artRequestedUrl = "";
	}

	public void toast(String title, String msg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> SystemToast.add(mc.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
				Text.literal(title), Text.literal(msg)));
	}

	// ---- sterowanie (wymaga Spotify Premium) ----

	public void togglePlay() {
		Playback p = playback;
		boolean playing = p != null && p.playing();
		control(playing ? "PUT" : "PUT", playing ? "/me/player/pause" : "/me/player/play");
		if (p != null) playback = p.withPlaying(!playing);
	}

	public void next() {
		control("POST", "/me/player/next");
	}

	public void previous() {
		control("POST", "/me/player/previous");
	}

	private void control(String method, String path) {
		if (!HudConfig.get().connected()) return;
		worker.submit(() -> {
			try {
				SpotifyAuth.ensureToken();
				Http.Response r = Http.send(method, API + path, bearer(), null);
				if (r.code() == 403) {
					toast("Spotify HUD", "Sterowanie wymaga Spotify Premium");
				} else if (r.code() == 404) {
					toast("Spotify HUD", "Brak aktywnego urządzenia Spotify");
				}
				pollSoon(500);
			} catch (IOException e) {
				SpotifyHudMod.LOG.warn("Błąd sterowania", e);
			}
		});
	}

	// ---- odpytywanie ----

	private void pollSafe() {
		try {
			poll();
		} catch (Throwable t) {
			SpotifyHudMod.LOG.warn("Błąd odpytywania Spotify", t);
		}
	}

	private void poll() {
		HudConfig cfg = HudConfig.get();
		if (!cfg.connected()) {
			playback = null;
			return;
		}
		if (System.currentTimeMillis() < backoffUntil) return;

		try {
			SpotifyAuth.ensureToken();
			Http.Response r = Http.send("GET", API + "/me/player/currently-playing?additional_types=episode",
					bearer(), null);
			switch (r.code()) {
				case 200 -> {
					status = "";
					handle(r);
				}
				case 204 -> {
					status = "";
					playback = null;
				}
				case 401 -> cfg.expiresAtMs = 0;
				case 429 -> {
					String ra = r.header("Retry-After");
					long sec = 5;
					try {
						if (ra != null) sec = Long.parseLong(ra.trim());
					} catch (NumberFormatException ignored) {
					}
					backoffUntil = System.currentTimeMillis() + sec * 1000L;
				}
				default -> status = "Spotify API: błąd " + r.code();
			}
		} catch (IOException e) {
			if (cfg.connected()) status = "Brak połączenia ze Spotify";
			backoffUntil = System.currentTimeMillis() + 5000;
		}

		Playback p = playback;
		if (p != null) {
			if (cfg.showArt) ensureArt(p);
			if (cfg.lyricsEnabled) ensureLyrics(p);
		}
	}

	private void handle(Http.Response r) {
		JsonObject root = JsonParser.parseString(r.text()).getAsJsonObject();
		JsonElement itemEl = root.get("item");
		if (itemEl == null || itemEl.isJsonNull()) { // np. reklama
			playback = null;
			return;
		}
		JsonObject item = itemEl.getAsJsonObject();

		String artist;
		JsonArray artists = item.has("artists") && item.get("artists").isJsonArray()
				? item.getAsJsonArray("artists") : null;
		if (artists != null) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement a : artists) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(str(a.getAsJsonObject(), "name"));
			}
			artist = sb.toString();
		} else if (item.has("show") && item.get("show").isJsonObject()) {
			artist = str(item.getAsJsonObject("show"), "name");
		} else {
			artist = "";
		}

		JsonArray images = null;
		String album = "";
		if (item.has("album") && item.get("album").isJsonObject()) {
			JsonObject al = item.getAsJsonObject("album");
			album = str(al, "name");
			if (al.has("images")) images = al.getAsJsonArray("images");
		} else if (item.has("images")) {
			images = item.getAsJsonArray("images");
		}

		long progress = root.has("progress_ms") && !root.get("progress_ms").isJsonNull()
				? root.get("progress_ms").getAsLong() : 0;
		boolean playing = root.has("is_playing") && root.get("is_playing").getAsBoolean();

		String id = str(item, "id");
		if (id.isEmpty()) id = str(item, "name") + "|" + artist;

		Playback p = new Playback(id, str(item, "name"), artist, album,
				item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0,
				progress, playing, pickImage(images), System.currentTimeMillis());
		playback = p;
	}

	/** Wybiera najmniejszą okładkę o szerokości >= 64 px. */
	private static String pickImage(JsonArray images) {
		if (images == null || images.isEmpty()) return "";
		String best = "";
		int bestW = Integer.MAX_VALUE;
		String largest = "";
		int largestW = -1;
		for (JsonElement e : images) {
			JsonObject o = e.getAsJsonObject();
			int w = o.has("width") && !o.get("width").isJsonNull() ? o.get("width").getAsInt() : 0;
			String url = str(o, "url");
			if (w >= 64 && w < bestW) {
				bestW = w;
				best = url;
			}
			if (w > largestW) {
				largestW = w;
				largest = url;
			}
		}
		return best.isEmpty() ? largest : best;
	}

	private Map<String, String> bearer() {
		return Map.of("Authorization", "Bearer " + HudConfig.get().accessToken);
	}

	// ---- okładka ----

	private void ensureArt(Playback p) {
		String url = p.artUrl();
		if (url.isEmpty() || url.equals(artRequestedUrl)) return;
		artRequestedUrl = url;
		worker.submit(() -> {
			try {
				Http.Response r = Http.send("GET", url, Map.of(), null);
				if (r.code() != 200) throw new IOException("HTTP " + r.code());
				NativeImage img = decode(r.raw());
				MinecraftClient mc = MinecraftClient.getInstance();
				mc.execute(() -> {
					if (!url.equals(artRequestedUrl)) {
						img.close();
						return;
					}
					Identifier old = artId;
					Identifier nid = Identifier.of("spotifyhud", "album_art_" + (++artCounter));
					int w = img.getWidth(), h = img.getHeight();
					mc.getTextureManager().registerTexture(nid, new NativeImageBackedTexture(img));
					artW = w;
					artH = h;
					artId = nid;
					artLoadedUrl = url;
					if (old != null) mc.getTextureManager().destroyTexture(old);
				});
			} catch (Exception e) {
				SpotifyHudMod.LOG.warn("Nie udało się pobrać okładki", e);
			}
		});
	}

	private static NativeImage decode(byte[] data) throws IOException {
		try {
			return NativeImage.read(new ByteArrayInputStream(data));
		} catch (Throwable ignored) {
			// zapasowa ścieżka: JPEG -> PNG przez ImageIO
		}
		BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
		if (bi == null) throw new IOException("Nieznany format obrazu");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(bi, "png", out);
		return NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
	}

	// ---- napisy ----

	private void ensureLyrics(Playback p) {
		LyricsData cur = lyrics;
		if (cur != null && cur.trackId.equals(p.id())) return;
		lyrics = LyricsData.loading(p.id());
		worker.submit(() -> {
			LyricsData result;
			try {
				List<LyricsData.Line> lines = LyricsClient.fetch(p);
				result = lines.isEmpty() ? LyricsData.none(p.id()) : LyricsData.ready(p.id(), lines);
			} catch (Exception e) {
				SpotifyHudMod.LOG.warn("Nie udało się pobrać tekstu", e);
				result = LyricsData.none(p.id());
			}
			LyricsData now = lyrics;
			if (now != null && now.trackId.equals(p.id())) lyrics = result;
		});
	}

	public String lyricsStatus() {
		LyricsData l = lyrics;
		Playback p = playback;
		if (l == null || p == null || !l.trackId.equals(p.id())) return "";
		return switch (l.state) {
			case LOADING -> "Napisy: ładowanie...";
			case READY -> "Napisy: dostępne";
			case NONE -> "Napisy: brak dla tego utworu";
		};
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
