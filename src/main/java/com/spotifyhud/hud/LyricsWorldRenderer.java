package com.spotifyhud.hud;

import com.spotifyhud.HudConfig;
import com.spotifyhud.spotify.Playback;
import com.spotifyhud.spotify.SpotifyService;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Napisy w świecie 3D przed graczem. Tekst podąża płynnie za obrotem głowy i jest zawsze zwrócony
 * w Twoją stronę. Dwa układy:
 * <ul>
 *   <li>rozrzucony - linijki wiszą w różnych miejscach: po lewej, po prawej i na środku;</li>
 *   <li>kolumna - wszystkie linijki jedna pod drugą na niebie przed Tobą.</li>
 * </ul>
 */
public final class LyricsWorldRenderer {
	// Kolumna
	private static final double COL_DISTANCE = 32.0;
	private static final float COL_PITCH = -22f;
	private static final float COL_SCALE = 0.32f;
	private static final int COL_WRAP = 240;
	// Rozrzucone
	private static final double SC_DISTANCE = 14.0;
	private static final float SC_PITCH = -6f;
	private static final float SC_SCALE = 0.075f;
	private static final int SC_WRAP = 110;

	private static final int LIGHT = 0xF000F0;

	private static boolean init;
	private static float anchorYaw;
	private static long lastNanos;

	private LyricsWorldRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		HudConfig cfg = HudConfig.get();
		HudConfig.LyricsMode mode = cfg.lyricsMode;
		if (!cfg.lyricsEnabled || !mode.world) return;
		if (mc.player == null || mc.options.hudHidden) return;

		Playback pb = SpotifyService.INSTANCE.playback();
		if (pb == null) return;
		long t = pb.currentProgress() + cfg.lyricsOffsetMs;
		List<LyricsView.Entry> entries = LyricsView.build(cfg, pb, SpotifyService.INSTANCE.lyrics(), t);
		if (entries.isEmpty()) return;

		Camera cam = ctx.camera();
		Vec3d camPos = cam.getPos();

		long now = System.nanoTime();
		double dt = init ? (now - lastNanos) / 1e9 : 1.0;
		if (dt > 0.5) init = false; // długa przerwa (napisy były wyłączone) - ustaw się od razu przed graczem
		lastNanos = now;
		float yaw = cam.getYaw();
		if (!init) {
			anchorYaw = yaw;
			init = true;
		} else {
			float diff = MathHelper.wrapDegrees(yaw - anchorYaw);
			anchorYaw += diff * (float) (1 - Math.exp(-dt * 5));
		}

		MatrixStack ms = ctx.matrixStack();
		if (ms == null) ms = new MatrixStack();
		VertexConsumerProvider.Immediate buf = mc.getBufferBuilders().getEntityVertexConsumers();
		TextRenderer tr = mc.textRenderer;

		if (mode == HudConfig.LyricsMode.SCATTER_WORLD) {
			drawScattered(ms, buf, tr, cfg, cam, camPos, entries);
		} else {
			drawColumn(ms, buf, tr, cfg, cam, camPos, entries);
		}
		buf.draw();
	}

	private static void drawScattered(MatrixStack ms, VertexConsumerProvider.Immediate buf, TextRenderer tr,
									  HudConfig cfg, Camera cam, Vec3d camPos, List<LyricsView.Entry> entries) {
		boolean dark = LyricsView.dark(cfg.lyricsColor);
		float s = SC_SCALE * cfg.lyricsScale;
		for (LyricsView.Entry e : entries) {
			float[] slot = LyricsView.slot(e.index());
			// slot[0] = w prawo (+) / w lewo (-), slot[1] = w dół (+) / w górę (-)
			Vec3d pos = camPos.add(Vec3d.fromPolar(SC_PITCH + slot[1], anchorYaw + slot[0]).multiply(SC_DISTANCE));
			List<OrderedText> ls = LyricsView.wrap(tr, e.text(), SC_WRAP, e.current());
			int color = LyricsView.color(cfg, e.offset(), e.fade());
			int bg = LyricsView.background(cfg, LyricsView.alpha(e.offset(), e.fade()));

			ms.push();
			ms.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
			ms.multiply(cam.getRotation());
			ms.scale(s, -s, s);
			float y = -ls.size() * 11 / 2f;
			for (OrderedText l : ls) {
				tr.draw(l, -tr.getWidth(l) / 2f, y, color, !dark, ms.peek().getPositionMatrix(), buf,
						TextRenderer.TextLayerType.SEE_THROUGH, bg, LIGHT);
				y += 11;
			}
			ms.pop();
		}
	}

	private static void drawColumn(MatrixStack ms, VertexConsumerProvider.Immediate buf, TextRenderer tr,
								   HudConfig cfg, Camera cam, Vec3d camPos, List<LyricsView.Entry> entries) {
		boolean dark = LyricsView.dark(cfg.lyricsColor);
		Vec3d pos = camPos.add(Vec3d.fromPolar(COL_PITCH, anchorYaw).multiply(COL_DISTANCE));

		List<List<OrderedText>> wrapped = new ArrayList<>();
		int total = 0;
		for (LyricsView.Entry e : entries) {
			List<OrderedText> ls = LyricsView.wrap(tr, e.text(), COL_WRAP, e.current());
			wrapped.add(ls);
			total += ls.size() * 11 + 4;
		}
		total -= 4;

		float s = COL_SCALE * cfg.lyricsScale;
		ms.push();
		ms.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
		ms.multiply(cam.getRotation());
		ms.scale(s, -s, s);

		float y = -total / 2f;
		for (int i = 0; i < entries.size(); i++) {
			LyricsView.Entry e = entries.get(i);
			int color = LyricsView.color(cfg, e.offset(), e.fade());
			int bg = LyricsView.background(cfg, LyricsView.alpha(e.offset(), e.fade()));
			for (OrderedText l : wrapped.get(i)) {
				tr.draw(l, -tr.getWidth(l) / 2f, y, color, !dark, ms.peek().getPositionMatrix(), buf,
						TextRenderer.TextLayerType.SEE_THROUGH, bg, LIGHT);
				y += 11;
			}
			y += 4;
		}
		ms.pop();
	}
}
