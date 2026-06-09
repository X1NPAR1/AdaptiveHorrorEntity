package com.adaptivehorror.client;

import com.adaptivehorror.network.HorrorNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The one-time, fullscreen disclaimer. It cannot be dismissed with Escape; the player must either
 * accept (which informs the server and unlocks the experience for this world) or leave the world.
 * Text is fully localised via translation keys, never hardcoded.
 */
public final class DisclaimerScreen extends Screen {

    private static final int PANEL = 0xE6000000;

    public DisclaimerScreen() {
        super(Component.translatable("adaptivehorror.disclaimer.title"));
    }

    @Override
    protected void init() {
        final int cx = this.width / 2;
        final int by = this.height - 52;

        addRenderableWidget(Button.builder(
                        Component.translatable("adaptivehorror.disclaimer.accept"), b -> onAccept())
                .bounds(cx - 160, by, 150, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("adaptivehorror.disclaimer.quit"), b -> onQuit())
                .bounds(cx + 10, by, 150, 20).build());
    }

    private void onAccept() {
        HorrorNet.sendDisclaimerAccepted();
        this.minecraft.setScreen(null);
    }

    private void onQuit() {
        final Minecraft mc = this.minecraft;
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.setScreen(new TitleScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(40, 30, this.width - 40, this.height - 30, PANEL);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 48, 0xFFAA0000);

        final Component body = Component.translatable("adaptivehorror.disclaimer.body");
        int y = 76;
        for (FormattedCharSequence line : this.font.split(body, this.width - 120)) {
            graphics.drawString(this.font, line, 60, y, 0xFFDDDDDD);
            y += 12;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
