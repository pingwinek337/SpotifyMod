package com.spotifyhud.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Płaski suwak z etykietą i wartością w środku. */
public class FlatSlider extends SliderWidget {
	private final String label;
	private final double min, max;
	private final DoubleFunction<String> format;
	private final DoubleConsumer onChange;

	public FlatSlider(int x, int y, int w, int h, String label, double min, double max, double current,
					  DoubleFunction<String> format, DoubleConsumer onChange) {
		super(x, y, w, h, Text.empty(), (current - min) / (max - min));
		this.label = label;
		this.min = min;
		this.max = max;
		this.format = format;
		this.onChange = onChange;
		updateMessage();
	}

	private double current() {
		return min + value * (max - min);
	}

	@Override
	protected void updateMessage() {
		if (format == null) return;
		setMessage(Text.literal(label + ": " + format.apply(current())));
	}

	@Override
	protected void applyValue() {
		onChange.accept(current());
	}

	@Override
	public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		int bg = isHovered() ? Theme.lighten(Theme.BUTTON, 0.08f) : Theme.BUTTON;
		ctx.fill(x, y, x + w, y + h, bg);
		int fillW = (int) ((w - 4) * value);
		ctx.fill(x, y, x + 2 + fillW, y + h, 0xFF17713A);
		ctx.fill(x + fillW, y, x + fillW + 4, y + h, Theme.GREEN);
		if (isFocused()) ctx.drawBorder(x, y, w, h, 0xFFFFFFFF);
		ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
				x + w / 2, y + (h - 8) / 2, Theme.TEXT);
	}
}
