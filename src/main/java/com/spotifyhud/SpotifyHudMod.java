package com.spotifyhud;

import com.spotifyhud.gui.SettingsScreen;
import com.spotifyhud.hud.HudRenderer;
import com.spotifyhud.spotify.SpotifyService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyHudMod implements ClientModInitializer {
	public static final String MOD_ID = "spotifyhud";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static KeyBinding openSettings, toggleLyrics, toggleHud, playPause, nextTrack, prevTrack;

	@Override
	public void onInitializeClient() {
		HudConfig.get();
		SpotifyService.INSTANCE.start();

		openSettings = key("settings", GLFW.GLFW_KEY_K);
		toggleLyrics = key("toggle_lyrics", GLFW.GLFW_KEY_UNKNOWN);
		toggleHud = key("toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
		playPause = key("play_pause", GLFW.GLFW_KEY_UNKNOWN);
		nextTrack = key("next", GLFW.GLFW_KEY_UNKNOWN);
		prevTrack = key("previous", GLFW.GLFW_KEY_UNKNOWN);

		HudRenderCallback.EVENT.register(HudRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private static KeyBinding key(String id, int code) {
		return KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.spotifyhud." + id, InputUtil.Type.KEYSYM, code, "category.spotifyhud"));
	}

	private void tick(MinecraftClient mc) {
		HudConfig cfg = HudConfig.get();
		while (openSettings.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new SettingsScreen(null));
		}
		while (toggleLyrics.wasPressed()) {
			cfg.lyricsEnabled = !cfg.lyricsEnabled;
			cfg.save();
			SpotifyService.INSTANCE.pollSoon(50);
			message(mc, "Napisy " + (cfg.lyricsEnabled ? "włączone" : "wyłączone"));
		}
		while (toggleHud.wasPressed()) {
			cfg.hudEnabled = !cfg.hudEnabled;
			cfg.save();
			message(mc, "Widget " + (cfg.hudEnabled ? "włączony" : "wyłączony"));
		}
		while (playPause.wasPressed()) SpotifyService.INSTANCE.togglePlay();
		while (nextTrack.wasPressed()) SpotifyService.INSTANCE.next();
		while (prevTrack.wasPressed()) SpotifyService.INSTANCE.previous();
	}

	private static void message(MinecraftClient mc, String text) {
		if (mc.player != null) mc.player.sendMessage(Text.literal("Spotify HUD: " + text), true);
	}
}
