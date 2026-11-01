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

import java.util.ArrayList;
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
		if (cfg.lyricsEnabled && !cfg.lyricsMode.world) drawLyrics(ctx, mc, cfg, pb);
	}

	/**
	 * Napisy na ekranie, jedna linijka naraz (plus gasnąca poprzednia): po lewej, po prawej
	 * lub na środku, zależnie od numeru linijki w piosence - więc kolejne linijki skaczą między
	 * stronami, ale nigdy się nie nakładają.
	 */
	private static void drawScattered(DrawContext ctx, TextRenderer tr, HudConfig cfg,
								   List<LyricsView.Entry> entries, float s, float lw, float lh) {
		boolean dark = LyricsView.dark(cfg.lyricsColor);
		int wrapW = (int) Math.min(200, lw * 0.34);
		float cy = lh * 0.42f;
		float margin = 10 / s;
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(s, s, 1f);
		for (LyricsView.Entry e : entries) {
			int side = LyricsView.side(e.index()); // 0 lewo, 1 środek, 2 prawo
			List<OrderedText> ls = LyricsView.wrap(tr, e.text(), wrapW, e.current());
			int blockW = 0;
			for (OrderedText l : ls) blockW = Math.max(blockW, tr.getWidth(l));
			int blockH = ls.size() * 11;

			float af = LyricsView.alpha(e.offset(), e.fade());
			int color = LyricsView.color(cfg, e.offset(), e.fade());
			int bg = LyricsView.background(cfg, af);
			float y = cy - blockH / 2f;
			for (OrderedText l : ls) {
				int w = tr.getWidth(l);
				float x = switch (side) {
					case 0 -> margin;
					case 2 -> lw - margin - w;
					default -> (lw - w) / 2f;
				};
				ctx.fill((int) x - 3, (int) y - 1, (int) x + w + 3, (int) y + 9, bg);
				ctx.drawText(tr, l, (int) x, (int) y, color, !dark);
				y += 11;
			}
		}
		m.pop();
	}

	/** Napisy na ekranie: po lewej, po prawej albo na dole. */
	private static void drawLyrics(DrawContext ctx, MinecraftClient mc, HudConfig cfg, Playback pb) {
		long t = pb.currentProgress() + cfg.lyricsOffsetMs;
		List<LyricsView.Entry> entries = LyricsView.build(cfg, pb, SpotifyService.INSTANCE.lyrics(), t);
		if (entries.isEmpty()) return;

		TextRenderer tr = mc.textRenderer;
		float s = cfg.lyricsScale;
		float lw = ctx.getScaledWindowWidth() / s;
		float lh = ctx.getScaledWindowHeight() / s;
		HudConfig.LyricsMode mode = cfg.lyricsMode;

		if (mode == HudConfig.LyricsMode.SCATTER_SCREEN) {
			drawScattered(ctx, tr, cfg, entries, s, lw, lh);
			return;
		}
		boolean bottom = mode == HudConfig.LyricsMode.BOTTOM;
		boolean left = mode == HudConfig.LyricsMode.LEFT;

		int wrapW = bottom ? (int) Math.min(300, lw * 0.7) : (int) Math.min(190, lw * 0.3);
		float margin = 8 / s;
		boolean dark = LyricsView.dark(cfg.lyricsColor);

		// Najpierw układ: zawinięte linie każdego wpisu i łączna wysokość.
		List<List<OrderedText>> wrapped = new ArrayList<>();
		int total = 0;
		for (LyricsView.Entry e : entries) {
			List<OrderedText> ls = LyricsView.wrap(tr, e.text(), wrapW, e.current());
			wrapped.add(ls);
			total += ls.size() * 10 + 5;
		}
		total -= 5;

		float y = bottom ? lh - 100 / s - total : (lh - total) / 2f;

		MatrixStack m = ctx.getMatrices();
		m.push();
		m.scale(s, s, 1f);
		for (int i = 0; i < entries.size(); i++) {
			LyricsView.Entry e = entries.get(i);
			float af = LyricsView.alpha(e.offset(), e.fade());
			int color = LyricsView.color(cfg, e.offset(), e.fade());
			int bg = LyricsView.background(cfg, af);
			for (OrderedText l : wrapped.get(i)) {
				int w = tr.getWidth(l);
				float x = bottom ? (lw - w) / 2f : left ? margin : lw - margin - w;
				ctx.fill((int) x - 3, (int) y - 1, (int) x + w + 3, (int) y + 9, bg);
				ctx.drawText(tr, l, (int) x, (int) y, color, !dark);
				y += 10;
			}
			y += 5;
		}
		m.pop();
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

	private static String fit(TextRenderer tr, String s, int maxW) {
		if (tr.getWidth(s) <= maxW) return s;
		return tr.trimToWidth(s, maxW - tr.getWidth("...")) + "...";
	}
}
