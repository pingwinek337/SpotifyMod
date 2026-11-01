package com.spotifyhud.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Kwadrat z kolorem do wyboru z palety. */
public class ColorSwatch extends ButtonWidget {
	private final int rgb;
	private final BooleanSupplier selected;

	public ColorSwatch(int x, int y, int size, int rgb, String name, BooleanSupplier selected, Runnable pick) {
		super(x, y, size, size, Text.literal(name), b -> pick.run(), DEFAULT_NARRATION_SUPPLIER);
		this.rgb = rgb;
		this.selected = selected;
	}

	@Override
	public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean sel = selected.getAsBoolean();
		int border = sel ? 0xFFFFFFFF : isHovered() ? 0xFFBBBBBB : 0xFF555555;
		ctx.fill(x, y, x + w, y + h, border);
		int inset = sel ? 3 : 1;
		ctx.fill(x + inset, y + inset, x + w - inset, y + h - inset, 0xFF000000 | rgb);
		if (sel) ctx.drawBorder(x - 1, y - 1, w + 2, h + 2, Theme.GREEN);
	}
}
