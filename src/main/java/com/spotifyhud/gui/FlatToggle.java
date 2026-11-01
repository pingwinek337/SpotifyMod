package com.spotifyhud.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Przełącznik: etykieta po lewej, "suwak" włącz/wyłącz po prawej. */
public class FlatToggle extends ButtonWidget {
	private final String label;
	private final BooleanSupplier state;

	public FlatToggle(int x, int y, int w, int h, String label, BooleanSupplier state, Runnable toggle) {
		super(x, y, w, h, Text.literal(label), b -> toggle.run(), DEFAULT_NARRATION_SUPPLIER);
		this.label = label;
		this.state = state;
	}

	@Override
	public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int bg = isHovered() ? Theme.lighten(Theme.BUTTON, 0.12f) : Theme.BUTTON;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		ctx.fill(x, y, x + w, y + h, bg);
		if (isFocused()) ctx.drawBorder(x, y, w, h, 0xFFFFFFFF);

		ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x + 8, y + (h - 8) / 2, Theme.TEXT);

		boolean on = state.getAsBoolean();
		int tw = 22, th = 10;
		int sx = x + w - tw - 8, sy = y + (h - th) / 2;
		int track = on ? Theme.GREEN : 0xFF555555;
		ctx.fill(sx + 1, sy, sx + tw - 1, sy + th, track);
		ctx.fill(sx, sy + 1, sx + tw, sy + th - 1, track);
		int kx = on ? sx + tw - 9 : sx + 3;
		ctx.fill(kx, sy + 2, kx + 6, sy + th - 2, 0xFFFFFFFF);
	}
}
