package dev.spudpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Draws the small SpudPvP status HUD.
 *
 * This class only renders local Minecraft UI. It does not read browser,
 * launcher, Discord, or other application data and makes no network requests.
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        int x = 6;
        int y = 6;
        int lineHeight = client.textRenderer.fontHeight + 3;

        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal("SpudPvP"),
                x,
                y,
                0xFFFFFF
        );

        y += lineHeight;
        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal("Sprint: " + (SpudPvPClient.sprintEnabled ? "ON" : "OFF")),
                x,
                y,
                SpudPvPClient.sprintEnabled ? 0x55FF55 : 0xFF5555
        );

        y += lineHeight;
        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal("CPS: " + SpudPvPClient.cps),
                x,
                y,
                0xFFFFFF
        );

        y += lineHeight;
        context.drawTextWithShadow(
                client.textRenderer,
                Text.literal("Zoom: " + (SpudPvPClient.zoomHeld ? "ON" : "OFF")),
                x,
                y,
                SpudPvPClient.zoomHeld ? 0x55FF55 : 0xAAAAAA
        );
    }
}
