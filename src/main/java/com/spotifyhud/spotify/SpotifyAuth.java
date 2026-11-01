package com.spotifyhud.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spotifyhud.HudConfig;
import com.spotifyhud.SpotifyHudMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Logowanie do Spotify: Authorization Code + PKCE, z lokalnym serwerkiem na callback. */
public final class SpotifyAuth {
	public static final int PORT = 8888;
	public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
	private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
	private static final String SCOPES =
			"user-read-playback-state user-read-currently-playing user-modify-playback-state";
	private static final SecureRandom RNG = new SecureRandom();
	private static volatile ServerSocket server;

	private SpotifyAuth() {
	}

	public static synchronized void startLogin() {
		HudConfig cfg = HudConfig.get();
		SpotifyService sp = SpotifyService.INSTANCE;
		String clientId = cfg.clientId.trim();
		if (clientId.isEmpty()) {
			sp.setStatus("Wpisz najpierw Client ID z developer.spotify.com");
			return;
		}
		closeServer();
		final ServerSocket ss;
		try {
			ss = new ServerSocket(PORT, 4, InetAddress.getByName("127.0.0.1"));
		} catch (IOException e) {
			sp.setStatus("Port " + PORT + " jest zajęty - zamknij inny program");
			return;
		}
		server = ss;

		String verifier = random(64);
		String state = random(16);
		String challenge = b64(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
		String url = AUTH_URL + "?client_id=" + enc(clientId) + "&response_type=code&redirect_uri=" + enc(REDIRECT)
				+ "&scope=" + enc(SCOPES) + "&code_challenge_method=S256&code_challenge=" + challenge
				+ "&state=" + state;

		sp.setStatus("Czekam na zalogowanie w przeglądarce...");
		Thread t = new Thread(() -> serve(ss, clientId, verifier, state), "spotifyhud-auth");
		t.setDaemon(true);
		t.start();

		try {
			Util.getOperatingSystem().open(URI.create(url));
		} catch (Exception e) {
			SpotifyHudMod.LOG.warn("Nie udało się otworzyć przeglądarki", e);
			sp.setStatus("Nie mogę otworzyć przeglądarki");
		}
	}

	private static void serve(ServerSocket ss, String clientId, String verifier, String state) {
		SpotifyService sp = SpotifyService.INSTANCE;
		String code = null;
		String error = null;
		long deadline = System.currentTimeMillis() + 180_000;
		try {
			ss.setSoTimeout(1000);
			while (code == null && error == null && System.currentTimeMillis() < deadline && !ss.isClosed()) {
				Socket s;
				try {
					s = ss.accept();
				} catch (SocketTimeoutException e) {
					continue;
				}
				try (s) {
					s.setSoTimeout(5000);
					BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
					String line = r.readLine();
					if (line == null) continue;
					String[] parts = line.split(" ");
					if (parts.length < 2) continue;
					String target = parts[1];
					int q = target.indexOf('?');
					String path = q < 0 ? target : target.substring(0, q);
					if (!path.equals("/callback")) {
						respond(s, 404, "Not found");
						continue;
					}
					Map<String, String> params = parseQuery(q < 0 ? "" : target.substring(q + 1));
					if (params.get("error") != null) {
						error = params.get("error");
						respond(s, 400, page(false, "Spotify odrzuciło logowanie: " + error));
					} else if (params.get("code") != null && state.equals(params.get("state"))) {
						code = params.get("code");
						respond(s, 200, page(true, "Konto połączone! Możesz wrócić do Minecrafta."));
					} else {
						respond(s, 400, page(false, "Nieprawidłowa odpowiedź logowania."));
					}
				} catch (IOException ignored) {
				}
			}
		} catch (IOException e) {
			SpotifyHudMod.LOG.warn("Błąd serwera logowania", e);
		} finally {
			try {
				ss.close();
			} catch (IOException ignored) {
			}
		}

		if (error != null) {
			sp.setStatus("Logowanie anulowane: " + error);
		} else if (code == null) {
			sp.setStatus("Logowanie wygasło - spróbuj ponownie");
		} else {
			exchange(clientId, code, verifier);
		}
	}

	private static void exchange(String clientId, String code, String verifier) {
		HudConfig cfg = HudConfig.get();
		SpotifyService sp = SpotifyService.INSTANCE;
		try {
			String body = form("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
					"client_id", clientId, "code_verifier", verifier);
			Http.Response r = Http.send("POST", TOKEN_URL,
					Map.of("Content-Type", "application/x-www-form-urlencoded"), body);
			if (r.code() != 200) {
				SpotifyHudMod.LOG.warn("Token HTTP {}: {}", r.code(), r.text());
				sp.setStatus("Błąd logowania (HTTP " + r.code() + ") - sprawdź Client ID i Redirect URI");
				return;
			}
			applyTokens(cfg, JsonParser.parseString(r.text()).getAsJsonObject());

			try {
				Http.Response me = Http.send("GET", "https://api.spotify.com/v1/me",
						Map.of("Authorization", "Bearer " + cfg.accessToken), null);
				if (me.code() == 200) {
					JsonObject o = JsonParser.parseString(me.text()).getAsJsonObject();
					cfg.displayName = o.has("display_name") && !o.get("display_name").isJsonNull()
							? o.get("display_name").getAsString() : o.get("id").getAsString();
				}
			} catch (Exception ignored) {
			}
			cfg.save();
			sp.setStatus("");
			sp.toast("Spotify HUD", "Połączono z kontem " + cfg.displayName);
			sp.pollSoon(100);
		} catch (Exception e) {
			SpotifyHudMod.LOG.warn("Wymiana kodu nie powiodła się", e);
			sp.setStatus("Błąd logowania: " + e.getMessage());
		}
	}

	/** Odświeża access token, gdy zaraz wygaśnie. */
	public static synchronized void ensureToken() throws IOException {
		HudConfig cfg = HudConfig.get();
		if (!cfg.connected()) throw new IOException("Brak połączenia z kontem");
		if (System.currentTimeMillis() < cfg.expiresAtMs - 60_000) return;

		String body = form("grant_type", "refresh_token", "refresh_token", cfg.refreshToken,
				"client_id", cfg.clientId.trim());
		Http.Response r = Http.send("POST", TOKEN_URL, Map.of("Content-Type", "application/x-www-form-urlencoded"), body);
		if (r.code() == 400 || r.code() == 401) {
			logout();
			SpotifyService.INSTANCE.setStatus("Sesja wygasła - połącz konto ponownie");
			throw new IOException("Odświeżenie tokenu odrzucone");
		}
		if (r.code() != 200) throw new IOException("Token HTTP " + r.code());
		applyTokens(cfg, JsonParser.parseString(r.text()).getAsJsonObject());
		cfg.save();
	}

	private static void applyTokens(HudConfig cfg, JsonObject o) {
		cfg.accessToken = o.get("access_token").getAsString();
		if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
			cfg.refreshToken = o.get("refresh_token").getAsString();
		}
		cfg.expiresAtMs = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L;
	}

	public static synchronized void logout() {
		closeServer();
		HudConfig cfg = HudConfig.get();
		cfg.accessToken = "";
		cfg.refreshToken = "";
		cfg.expiresAtMs = 0;
		cfg.displayName = "";
		cfg.save();
		SpotifyService.INSTANCE.reset();
	}

	private static void closeServer() {
		ServerSocket s = server;
		server = null;
		if (s != null) {
			try {
				s.close();
			} catch (IOException ignored) {
			}
		}
	}

	// ---- helpers ----

	private static void respond(Socket s, int code, String html) throws IOException {
		byte[] b = html.getBytes(StandardCharsets.UTF_8);
		String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\n"
				+ "Content-Type: text/html; charset=utf-8\r\n"
				+ "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
		OutputStream o = s.getOutputStream();
		o.write(head.getBytes(StandardCharsets.UTF_8));
		o.write(b);
		o.flush();
	}

	private static String page(boolean ok, String msg) {
		String color = ok ? "#1DB954" : "#e5484d";
		return "<!doctype html><html><head><meta charset=utf-8><title>Spotify HUD</title></head>"
				+ "<body style=\"margin:0;height:100vh;display:grid;place-items:center;background:#121212;"
				+ "font-family:system-ui,sans-serif;color:#fff\"><div style=\"text-align:center\">"
				+ "<div style=\"font-size:48px;color:" + color + "\">" + (ok ? "&#10003;" : "&#10007;") + "</div>"
				+ "<h2>Spotify HUD</h2><p style=\"color:#b3b3b3\">" + escape(msg) + "</p></div></body></html>";
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static Map<String, String> parseQuery(String q) {
		Map<String, String> m = new HashMap<>();
		for (String pair : q.split("&")) {
			if (pair.isEmpty()) continue;
			int i = pair.indexOf('=');
			String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
			String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
			m.put(k, v);
		}
		return m;
	}

	private static String form(String... kv) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			if (sb.length() > 0) sb.append('&');
			sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
		}
		return sb.toString();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String random(int n) {
		byte[] b = new byte[n];
		RNG.nextBytes(b);
		String s = b64(b);
		return s.substring(0, Math.min(n, s.length()));
	}

	private static String b64(byte[] b) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}

	private static byte[] sha256(byte[] in) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(in);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
