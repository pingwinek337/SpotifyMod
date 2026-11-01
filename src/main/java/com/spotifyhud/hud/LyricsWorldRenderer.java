package com.spotifyhud.hud;

import com.spotifyhud.HudConfig;
import com.spotifyhud.spotify.LyricsData;
import com.spotifyhud.spotify.Playback;
import com.spotifyhud.spotify.SpotifyService;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Napisy wiszące w świecie przed graczem: tekst unosi się kilka kratek przed Tobą
 * (płynnie podąża za obrotem głowy) i jest zawsze zwrócony w Twoją stronę.
 */
public final class LyricsWorldRenderer {
	private static final double DISTANCE = 3.5;
	private static final float SCALE = 0.035f;
	private static final int LIGHT = 0xF000F0;

	private static boolean init;
	private static float anchorYaw;
	private static long lastNanos;

	private LyricsWorldRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		HudConfig cfg = HudConfig.get();
		if (!cfg.lyricsEnabled || mc.player == null || mc.options.hudHidden) return;

		Playback pb = SpotifyService.INSTANCE.playback();
		LyricsData ld = SpotifyService.INSTANCE.lyrics();
		if (pb == null || ld == null || ld.state != LyricsData.State.READY || !ld.trackId.equals(pb.id())) return;

		long t = pb.currentProgress() + cfg.lyricsOffsetMs;
		int idx = ld.indexAt(t);
		if (idx < 0) return;
		LyricsData.Line cur = ld.lines.get(idx);
		if (cur.text().isBlank()) return;
		String next = idx + 1 < ld.lines.size() ? ld.lines.get(idx + 1).text() : "";

		Camera cam = ctx.camera();
		Vec3d camPos = cam.getPos();

		long now = System.nanoTime();
		double dt = init ? (now - lastNanos) / 1e9 : 1.0;
		lastNanos = now;
		float yaw = cam.getYaw();
		if (!init) {
			anchorYaw = yaw;
			init = true;
		} else {
			float diff = MathHelper.wrapDegrees(yaw - anchorYaw);
			anchorYaw += diff * (float) (1 - Math.exp(-dt * 5));
		}

		Vec3d pos = camPos.add(Vec3d.fromPolar(8f, anchorYaw).multiply(DISTANCE));

		TextRenderer tr = mc.textRenderer;
		double k = Math.max(0.0, Math.min(1.0, (t - cur.timeMs()) / 250.0));
		int a = 0x30 + (int) (k * (0xFF - 0x30));
		int curColor = (a << 24) | 0xFFFFFF;
		int nextColor = (Math.min(a, 0x99) << 24) | 0xC8C8C8;

		List<OrderedText> curLines = tr.wrapLines(Text.literal(cur.text()), 240);
		List<OrderedText> nextLines = next.isBlank() ? List.of() : tr.wrapLines(Text.literal(next), 240);

		MatrixStack ms = ctx.matrixStack();
		if (ms == null) ms = new MatrixStack();
		VertexConsumerProvider.Immediate buf = mc.getBufferBuilders().getEntityVertexConsumers();

		ms.push();
		ms.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
		ms.multiply(cam.getRotation());
		ms.scale(SCALE, -SCALE, SCALE);

		int total = (curLines.size() + (nextLines.isEmpty() ? 0 : nextLines.size())) * 11 + (nextLines.isEmpty() ? 0 : 4);
		float y = -total / 2f;
		for (OrderedText l : curLines) {
			tr.draw(l, -tr.getWidth(l) / 2f, y, curColor, true, ms.peek().getPositionMatrix(), buf,
					TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, LIGHT);
			y += 11;
		}
		y += 4;
		for (OrderedText l : nextLines) {
			tr.draw(l, -tr.getWidth(l) / 2f, y, nextColor, false, ms.peek().getPositionMatrix(), buf,
					TextRenderer.TextLayerType.SEE_THROUGH, 0x33000000, LIGHT);
			y += 11;
		}
		ms.pop();
		buf.draw();
	}
}
