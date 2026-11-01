package com.spotifyhud.gui;

import com.spotifyhud.HudConfig;
import com.spotifyhud.hud.LyricsView;
import com.spotifyhud.spotify.Playback;
import com.spotifyhud.spotify.SpotifyAuth;
import com.spotifyhud.spotify.SpotifyService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/** Ekran ustawień Spotify HUD z zakładkami: Konto, Widget, Napisy. */
public class SettingsScreen extends Screen {
	private static final int PW = 360, PH = 268;
	private static final String HINT = "Client ID z developer.spotify.com";

	private enum Tab {
		ACCOUNT("Konto"), WIDGET("Widget"), LYRICS("Napisy");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static Tab tab = Tab.ACCOUNT;

	private final Screen parent;
	private final HudConfig cfg = HudConfig.get();
	private final SpotifyService sp = SpotifyService.INSTANCE;
	private TextFieldWidget clientField;
	private int px, py, y0;

	public SettingsScreen(Screen parent) {
		super(Text.literal("Spotify HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = Math.max(2, (height - PH) / 2);
		y0 = py + 78;
		int x = px + 16, w = PW - 32, half = (w - 6) / 2, x2 = x + half + 6;

		// Zakładki
		int tabW = (w - 8) / 3;
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			final Tab t = tabs[i];
			addDrawableChild(new FlatButton(x + i * (tabW + 4), py + 48, tabW, 20, () -> Text.literal(t.label),
					() -> {
						tab = t;
						clearChildren();
						init();
					}, () -> tab == t ? Theme.BUTTON_GREEN : Theme.BUTTON));
		}

		switch (tab) {
			case ACCOUNT -> initAccount(x, w);
			case WIDGET -> initWidget(x, x2, half);
			case LYRICS -> initLyrics(x, x2, half, w);
		}

		addDrawableChild(new FlatButton(x, py + PH - 28, w, 20, () -> Text.literal("Gotowe"), this::close,
				() -> Theme.BUTTON));
	}

	private void initAccount(int x, int w) {
		addDrawableChild(new FlatButton(x, y0, w, 22,
				() -> Text.literal(cfg.connected() ? "Rozłącz konto Spotify" : "Połącz ze Spotify"),
				this::onConnect, () -> cfg.connected() ? Theme.BUTTON_RED : Theme.BUTTON_GREEN));

		clientField = new TextFieldWidget(textRenderer, x, y0 + 30, w, 20, Text.literal("Client ID"));
		clientField.setMaxLength(64);
		clientField.setText(cfg.clientId);
		clientField.setSuggestion(cfg.clientId.isEmpty() ? HINT : null);
		clientField.setChangedListener(s -> {
			cfg.clientId = s.trim();
			clientField.setSuggestion(s.isEmpty() ? HINT : null);
		});
		addDrawableChild(clientField);
	}

	private void initWidget(int x, int x2, int half) {
		addDrawableChild(new FlatToggle(x, y0, half, 22, "Widget", () -> cfg.hudEnabled,
				() -> cfg.hudEnabled = !cfg.hudEnabled));
		addDrawableChild(new FlatToggle(x2, y0, half, 22, "Okładka", () -> cfg.showArt, () -> {
			cfg.showArt = !cfg.showArt;
			sp.pollSoon(50);
		}));
		addDrawableChild(new FlatToggle(x, y0 + 28, half, 22, "Pasek postępu", () -> cfg.showProgress,
				() -> cfg.showProgress = !cfg.showProgress));
		addDrawableChild(new FlatButton(x2, y0 + 28, half, 22,
				() -> Text.literal("Pozycja: " + cfg.corner.label),
				() -> cfg.corner = cfg.corner.next(), () -> Theme.BUTTON));
		addDrawableChild(new FlatSlider(x, y0 + 56, half, 22, "Skala", 0.5, 2.0, cfg.scale,
				v -> String.format("%.2fx", v), v -> cfg.scale = (float) (Math.round(v * 20) / 20.0)));
		addDrawableChild(new FlatSlider(x2, y0 + 56, half, 22, "Tło", 0.0, 1.0, cfg.bgOpacity,
				v -> Math.round(v * 100) + "%", v -> cfg.bgOpacity = (float) (Math.round(v * 20) / 20.0)));
	}

	private void initLyrics(int x, int x2, int half, int w) {
		addDrawableChild(new FlatToggle(x, y0, half, 22, "Napisy", () -> cfg.lyricsEnabled, () -> {
			cfg.lyricsEnabled = !cfg.lyricsEnabled;
			sp.pollSoon(50);
		}));
		addDrawableChild(new FlatButton(x2, y0, half, 22,
				() -> Text.literal(cfg.lyricsMode.label),
				() -> cfg.lyricsMode = cfg.lyricsMode.next(), () -> Theme.BUTTON));

		int size = 22, gap = 6;
		for (int i = 0; i < Theme.LYRIC_COLORS.length; i++) {
			final int rgb = Theme.LYRIC_COLORS[i];
			addDrawableChild(new ColorSwatch(x + i * (size + gap), y0 + 40, size, rgb, Theme.LYRIC_NAMES[i],
					() -> (cfg.lyricsColor & 0xFFFFFF) == rgb, () -> cfg.lyricsColor = rgb));
		}

		addDrawableChild(new FlatSlider(x, y0 + 70, half, 22, "Linie dalej", 0, 6, cfg.lyricsLinesAhead,
				v -> String.valueOf(Math.round(v)), v -> cfg.lyricsLinesAhead = (int) Math.round(v)));
		addDrawableChild(new FlatSlider(x2, y0 + 70, half, 22, "Rozmiar", 0.6, 2.0, cfg.lyricsScale,
				v -> String.format("%.1fx", v), v -> cfg.lyricsScale = (float) (Math.round(v * 10) / 10.0)));
		addDrawableChild(new FlatSlider(x, y0 + 96, w, 22, "Synchronizacja", -3000, 3000, cfg.lyricsOffsetMs,
				v -> {
					long ms = Math.round(v / 50) * 50;
					return (ms >= 0 ? "+" : "") + ms + " ms";
				}, v -> cfg.lyricsOffsetMs = (int) (Math.round(v / 50) * 50)));
	}

	private void onConnect() {
		if (cfg.connected()) {
			SpotifyAuth.logout();
		} else {
			cfg.clientId = clientField.getText().trim();
			cfg.save();
			SpotifyAuth.startLogin();
		}
	}

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, 0x55000000);
		ctx.fill(px, py, px + PW, py + PH, Theme.PANEL);
		ctx.drawBorder(px, py, PW, PH, Theme.BORDER);
		ctx.fill(px, py, px + PW, py + 3, Theme.GREEN);
		ctx.fill(px + 16, py + 72, px + PW - 16, py + 73, 0xFF2A2A2A); // linia pod zakładkami
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		drawHeader(ctx);
		int x = px + 16, w = PW - 32;

		switch (tab) {
			case ACCOUNT -> {
				String[] help = {
						"1. Wejdź na developer.spotify.com/dashboard i kliknij Create app.",
						"2. Redirect URI wpisz dokładnie: http://127.0.0.1:8888/callback",
						"3. Zaznacz Web API, zapisz i skopiuj Client ID do pola wyżej.",
						"4. Kliknij Połącz ze Spotify i zaakceptuj w przeglądarce."
				};
				int y = y0 + 60;
				for (String line : help) {
					ctx.drawText(textRenderer, line, x, y, Theme.MUTED, false);
					y += 12;
				}
			}
			case LYRICS -> {
				ctx.drawText(textRenderer, "Kolor napisów:", x, y0 + 28, Theme.MUTED, false);
				String name = Theme.colorName(cfg.lyricsColor);
				ctx.drawText(textRenderer, name, x + w - textRenderer.getWidth(name), y0 + 28, Theme.TEXT, false);
				drawPreview(ctx, x, y0 + 124, w, 38);
			}
			default -> {
			}
		}
	}

	private void drawHeader(DrawContext ctx) {
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(px + 16, py + 12, 0);
		m.scale(1.4f, 1.4f, 1f);
		ctx.drawText(textRenderer, "Spotify", 0, 0, Theme.GREEN, true);
		ctx.drawText(textRenderer, " HUD", textRenderer.getWidth("Spotify"), 0, Theme.TEXT, true);
		m.pop();

		int x = px + 16, maxW = PW - 32;
		boolean on = cfg.connected();
		String who = on ? "Połączono" + (cfg.displayName.isEmpty() ? "" : ": " + cfg.displayName) : "Niepołączono";
		int whoW = textRenderer.getWidth(who);
		int rx = px + PW - 16 - whoW;
		ctx.fill(rx - 10, py + 15, rx - 4, py + 21, on ? Theme.GREEN : 0xFF666666);
		ctx.drawText(textRenderer, who, rx, py + 14, on ? Theme.GREEN : Theme.MUTED, false);

		String line2 = sp.status();
		int color2 = 0xFFE6C34D;
		if (line2.isEmpty()) {
			Playback p = sp.playback();
			if (p != null) {
				line2 = "Teraz gra: " + p.title() + " - " + p.artist();
				color2 = Theme.MUTED;
				if (on && cfg.lyricsEnabled) {
					String ls = sp.lyricsStatus();
					if (!ls.isEmpty()) line2 += "  |  " + ls;
				}
			}
		}
		if (!line2.isEmpty()) {
			if (textRenderer.getWidth(line2) > maxW) {
				line2 = textRenderer.trimToWidth(line2, maxW - textRenderer.getWidth("...")) + "...";
			}
			ctx.drawText(textRenderer, line2, x, py + 32, color2, false);
		}
	}

	/** Podgląd: jak będą wyglądać napisy w wybranym kolorze (na tle nieba). */
	private void drawPreview(DrawContext ctx, int x, int y, int w, int h) {
		ctx.fill(x, y, x + w, y + h, 0xFF6F9AD0);
		ctx.drawBorder(x, y, w, h, 0xFF2A2A2A);
		boolean dark = LyricsView.dark(cfg.lyricsColor);

		String[] texts = {"Poprzednia linijka", "Aktualna linijka tekstu", "Nadchodząca linijka"};
		int[] offsets = {-1, 0, 1};
		int[] xs = {x + 12, x + w / 2, x + w - 12};
		int[] ys = {y + 6, y + 14, y + 25};
		for (int i = 0; i < texts.length; i++) {
			if (i == 2 && cfg.lyricsLinesAhead == 0) continue;
			Text t = Text.literal(texts[i]);
			if (offsets[i] == 0) t = ((net.minecraft.text.MutableText) t).formatted(net.minecraft.util.Formatting.BOLD);
			OrderedText o = t.asOrderedText();
			int tw = textRenderer.getWidth(o);
			int tx = i == 0 ? xs[i] : i == 1 ? xs[i] - tw / 2 : xs[i] - tw;
			float af = LyricsView.alpha(offsets[i], 1f);
			ctx.fill(tx - 3, ys[i] - 1, tx + tw + 3, ys[i] + 9, LyricsView.background(cfg, af));
			ctx.drawText(textRenderer, o, tx, ys[i], LyricsView.color(cfg, offsets[i], 1f), !dark);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public void removed() {
		cfg.save();
	}
}
