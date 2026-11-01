package com.spotifyhud.hud;

import com.spotifyhud.HudConfig;
import com.spotifyhud.gui.Theme;
import com.spotifyhud.spotify.LyricsData;
import com.spotifyhud.spotify.Playback;
import com.spotifyhud.spotify.SpotifyService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/** Rysuje widget "teraz gra" w rogu ekranu oraz napisy (tekst piosenki) przy dole. */
public final class HudRenderer {
	private static final int W = 170;

	private HudRenderer() {
	}

	public static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden || mc.player == null) return;

		HudConfig cfg = HudConfig.get();
		Playback pb = SpotifyService.INSTANCE.playback();
		if (pb == null) return;

		if (cfg.hudEnabled) drawWidget(ctx, mc, cfg, pb);
		if (cfg.lyricsEnabled) drawLyrics(ctx, mc, cfg, pb);
	}

	private static void drawWidget(DrawContext ctx, MinecraftClient mc, HudConfig cfg, Playback pb) {
		TextRenderer tr = mc.textRenderer;
		float s = cfg.scale;
		int h = cfg.showArt ? 44 : (cfg.showProgress ? 40 : 30);
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		int margin = 6;

		float x = cfg.corner.right() ? sw - margin - W * s : margin;
		float y;
		if (cfg.corner.bottom()) {
			y = sh - margin - h * s - (cfg.corner.right() ? 0 : 40); // lewy dół: omijamy czat
		} else {
			y = margin;
			if (cfg.corner.right() && !mc.player.getStatusEffects().isEmpty()) y += 28 * s; // ikony efektów
		}

		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(s, s, 1f);

		int alpha = Math.max(0x10, Math.min(0xFF, (int) (cfg.bgOpacity * 255)));
		ctx.fill(0, 0, W, h, (alpha << 24) | 0x101010);
		ctx.drawBorder(0, 0, W, h, 0x33FFFFFF);
		ctx.fill(0, 0, 2, h, Theme.GREEN);

		int textX = 8;
		if (cfg.showArt) {
			textX = 44;
			Identifier art = SpotifyService.INSTANCE.artIdFor(pb.artUrl());
			if (art != null) {
				int aw = SpotifyService.INSTANCE.artWidth();
				int ah = SpotifyService.INSTANCE.artHeight();
				m.push();
				m.translate(6, 6, 0);
				m.scale(32f / aw, 32f / ah, 1f);
				ctx.drawTexture(RenderLayer::getGuiTextured, art, 0, 0, 0f, 0f, aw, ah, aw, ah);
				m.pop();
			} else {
				ctx.fill(6, 6, 38, 38, 0xFF2A2A2A);
			}
		}

		int maxW = W - textX - 6;
		ctx.drawText(tr, fit(tr, pb.title(), maxW), textX, 7, pb.playing() ? 0xFFFFFFFF : 0xFFB0B0B0, true);
		ctx.drawText(tr, fit(tr, pb.artist(), maxW), textX, 18, 0xFFB3B3B3, false);

		if (cfg.showProgress && pb.durationMs() > 0) {
			int x0 = textX, x1 = W - 8, by = h - 10;
			double frac = Math.min(1.0, pb.currentProgress() / (double) pb.durationMs());
			int fx = x0 + (int) ((x1 - x0) * frac);
			ctx.fill(x0, by, x1, by + 3, 0xFF404040);
			ctx.fill(x0, by, fx, by + 3, pb.playing() ? Theme.GREEN : 0xFF8A8A8A);
			ctx.fill(fx - 1, by - 1, fx + 2, by + 4, 0xFFFFFFFF);
		}
		m.pop();
	}

	private static void drawLyrics(DrawContext ctx, MinecraftClient mc, HudConfig cfg, Playback pb) {
		LyricsData ld = SpotifyService.INSTANCE.lyrics();
		if (ld == null || ld.state != LyricsData.State.READY || !ld.trackId.equals(pb.id())) return;

		long t = pb.currentProgress() + cfg.lyricsOffsetMs;
		int idx = ld.indexAt(t);
		if (idx < 0) return;
		LyricsData.Line cur = ld.lines.get(idx);
		if (cur.text().isBlank()) return;

		TextRenderer tr = mc.textRenderer;
		int sw = ctx.getScaledWindowWidth();
		int sh = ctx.getScaledWindowHeight();
		int maxW = Math.min((int) (sw * 0.7), 360);

		List<OrderedText> curLines = tr.wrapLines(Text.literal(cur.text()), maxW);
		String nextText = "";
		if (idx + 1 < ld.lines.size()) nextText = ld.lines.get(idx + 1).text();

		double k = Math.max(0.0, Math.min(1.0, (t - cur.timeMs()) / 250.0));
		int a = 0x30 + (int) (k * (0xFF - 0x30));
		int curColor = (a << 24) | 0xFFFFFF;
		int nextColor = (Math.min(a, 0x99) << 24) | 0xB3B3B3;

		int lineH = 10;
		int blockH = curLines.size() * lineH + (nextText.isBlank() ? 0 : lineH + 3);
		int widest = 0;
		for (OrderedText l : curLines) widest = Math.max(widest, tr.getWidth(l));
		String nextFit = nextText.isBlank() ? "" : fit(tr, nextText, maxW);
		if (!nextFit.isEmpty()) widest = Math.max(widest, tr.getWidth(nextFit));

		int cx = sw / 2;
		int top = sh - 96 - blockH;
		ctx.fill(cx - widest / 2 - 6, top - 4, cx + widest / 2 + 6, top + blockH + 3, 0x80000000);

		int y = top;
		for (OrderedText l : curLines) {
			ctx.drawText(tr, l, cx - tr.getWidth(l) / 2, y, curColor, true);
			y += lineH;
		}
		if (!nextFit.isEmpty()) {
			ctx.drawText(tr, nextFit, cx - tr.getWidth(nextFit) / 2, y + 3, nextColor, false);
		}
	}

	private static String fit(TextRenderer tr, String s, int maxW) {
		if (tr.getWidth(s) <= maxW) return s;
		return tr.trimToWidth(s, maxW - tr.getWidth("...")) + "...";
	}
}
