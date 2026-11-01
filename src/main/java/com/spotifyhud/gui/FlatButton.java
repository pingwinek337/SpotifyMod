package com.spotifyhud.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Płaski przycisk z dynamiczną etykietą i kolorem. */
public class FlatButton extends ButtonWidget {
	private final Supplier<Text> label;
	private final IntSupplier color;

	public FlatButton(int x, int y, int w, int h, Supplier<Text> label, Runnable onClick, IntSupplier color) {
		super(x, y, w, h, Text.empty(), b -> onClick.run(), DEFAULT_NARRATION_SUPPLIER);
		this.label = label;
		this.color = color;
	}

	@Override
	public Text getMessage() {
		return label == null ? Text.empty() : label.get();
	}

	@Override
	public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int base = color.getAsInt();
		int bg = isHovered() ? Theme.lighten(base, 0.15f) : base;
		ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
		if (isFocused()) ctx.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label.get(),
				getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, Theme.TEXT);
	}
}
