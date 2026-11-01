package com.spotifyhud.spotify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Minimalny klient HTTP oparty o HttpURLConnection (działa na każdym runtime Minecrafta). */
final class Http {
	record Response(int code, byte[] raw, Map<String, List<String>> headers) {
		String text() {
			return new String(raw, StandardCharsets.UTF_8);
		}

		String header(String name) {
			for (Map.Entry<String, List<String>> e : headers.entrySet()) {
				if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
					return e.getValue().get(0);
				}
			}
			return null;
		}
	}

	private Http() {
	}

	static Response send(String method, String url, Map<String, String> headers, String body) throws IOException {
		HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
		try {
			c.setRequestMethod(method);
			c.setConnectTimeout(8000);
			c.setReadTimeout(10000);
			c.setRequestProperty("User-Agent", "SpotifyHud/1.0 (Minecraft Fabric mod)");
			for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());

			boolean writes = method.equals("POST") || method.equals("PUT");
			if (writes) {
				byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
				c.setDoOutput(true);
				c.setFixedLengthStreamingMode(b.length);
				try (OutputStream o = c.getOutputStream()) {
					o.write(b);
				}
			}

			int code = c.getResponseCode();
			InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
			byte[] data = new byte[0];
			if (in != null) {
				try (in) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					in.transferTo(out);
					data = out.toByteArray();
				}
			}
			return new Response(code, data, c.getHeaderFields());
		} finally {
			c.disconnect();
		}
	}
}
