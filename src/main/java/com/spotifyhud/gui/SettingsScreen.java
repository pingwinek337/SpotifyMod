package com.spotifyhud.gui;

import com.spotifyhud.HudConfig;
import com.spotifyhud.spotify.Playback;
import com.spotifyhud.spotify.SpotifyAuth;
import com.spotifyhud.spotify.SpotifyService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Ekran ustawień Spotify HUD. */
public class SettingsScreen extends Screen {
	private static final int PW = 340, PH = 236;
	private static final String HINT = "Client ID z developer.spotify.com";

	private final Screen parent;
	private final HudConfig cfg = HudConfig.get();
	private final SpotifyService sp = SpotifyService.INSTANCE;
	private TextFieldWidget clientField;
	private int px, py;

	public SettingsScreen(Screen parent) {
		super(Text.literal("Spotify HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		px = (width - PW) / 2;
		py = Math.max(2, (height - PH) / 2);
		int x = px + 16, w = PW - 32, half = (w - 6) / 2, x2 = x + half + 6;

		addDrawableChild(new FlatButton(x, py + 50, w, 20,
				() -> Text.literal(cfg.connected() ? "Rozłącz konto Spotify" : "Połącz ze Spotify"),
				this::onConnect, () -> cfg.connected() ? Theme.BUTTON_RED : Theme.BUTTON_GREEN));

		clientField = new TextFieldWidget(textRenderer, x, py + 74, w, 20, Text.literal("Client ID"));
		clientField.setMaxLength(64);
		clientField.setText(cfg.clientId);
		clientField.setSuggestion(cfg.clientId.isEmpty() ? HINT : null);
		clientField.setChangedListener(s -> {
			cfg.clientId = s.trim();
			clientField.setSuggestion(s.isEmpty() ? HINT : null);
		});
		addDrawableChild(clientField);

		addDrawableChild(new FlatToggle(x, py + 102, half, 20, "Widget", () -> cfg.hudEnabled,
				() -> cfg.hudEnabled = !cfg.hudEnabled));
		addDrawableChild(new FlatToggle(x2, py + 102, half, 20, "Napisy", () -> cfg.lyricsEnabled, () -> {
			cfg.lyricsEnabled = !cfg.lyricsEnabled;
			sp.pollSoon(50);
		}));
		addDrawableChild(new FlatToggle(x, py + 126, half, 20, "Okładka", () -> cfg.showArt, () -> {
			cfg.showArt = !cfg.showArt;
			sp.pollSoon(50);
		}));
		addDrawableChild(new FlatToggle(x2, py + 126, half, 20, "Pasek postępu", () -> cfg.showProgress,
				() -> cfg.showProgress = !cfg.showProgress));

		addDrawableChild(new FlatButton(x, py + 150, half, 20,
				() -> Text.literal("Pozycja: " + cfg.corner.label),
				() -> cfg.corner = cfg.corner.next(), () -> Theme.BUTTON));
		addDrawableChild(new FlatSlider(x2, py + 150, half, 20, "Skala", 0.5, 2.0, cfg.scale,
				v -> String.format("%.2fx", v), v -> cfg.scale = (float) (Math.round(v * 20) / 20.0)));

		addDrawableChild(new FlatSlider(x, py + 174, half, 20, "Tło", 0.0, 1.0, cfg.bgOpacity,
				v -> Math.round(v * 100) + "%", v -> cfg.bgOpacity = (float) (Math.round(v * 20) / 20.0)));
		addDrawableChild(new FlatSlider(x2, py + 174, half, 20, "Sync napisów", -3000, 3000, cfg.lyricsOffsetMs,
				v -> (Math.round(v / 50) * 50 >= 0 ? "+" : "") + (Math.round(v / 50) * 50) + " ms",
				v -> cfg.lyricsOffsetMs = (int) (Math.round(v / 50) * 50)));

		addDrawableChild(new FlatButton(x, py + 204, w, 20, () -> Text.literal("Gotowe"), this::close,
				() -> Theme.BUTTON));
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
		ctx.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0x66000000);
		ctx.fill(px, py, px + PW, py + PH, Theme.PANEL);
		ctx.drawBorder(px, py, PW, PH, Theme.BORDER);
		ctx.fill(px, py, px + PW, py + 3, Theme.GREEN);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);

		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(px + 16, py + 12, 0);
		m.scale(1.4f, 1.4f, 1f);
		ctx.drawText(textRenderer, "Spotify", 0, 0, Theme.GREEN, true);
		ctx.drawText(textRenderer, " HUD", textRenderer.getWidth("Spotify"), 0, Theme.TEXT, true);
		m.pop();

		int x = px + 16, maxW = PW - 32;
		boolean on = cfg.connected();
		ctx.fill(x, py + 30, x + 6, py + 36, on ? Theme.GREEN : 0xFF666666);
		String who = on ? "Połączono" + (cfg.displayName.isEmpty() ? "" : " jako " + cfg.displayName) : "Niepołączono";
		ctx.drawText(textRenderer, who, x + 10, py + 29, on ? Theme.GREEN : Theme.MUTED, false);

		if (on && cfg.lyricsEnabled) {
			String ls = sp.lyricsStatus();
			if (!ls.isEmpty()) {
				ctx.drawText(textRenderer, ls, px + PW - 16 - textRenderer.getWidth(ls), py + 29, Theme.MUTED, false);
			}
		}

		String line2 = sp.status();
		int color2 = 0xFFE6C34D;
		if (line2.isEmpty()) {
			Playback p = sp.playback();
			if (p != null) {
				line2 = "Teraz gra: " + p.title() + " - " + p.artist();
				color2 = Theme.MUTED;
			}
		}
		if (!line2.isEmpty()) {
			if (textRenderer.getWidth(line2) > maxW) {
				line2 = textRenderer.trimToWidth(line2, maxW - textRenderer.getWidth("...")) + "...";
			}
			ctx.drawText(textRenderer, line2, x, py + 39, color2, false);
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
