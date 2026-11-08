package dev.spudpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Draws the SpudPvP client HUD.
 *
 * This class only reads local Minecraft client state and renders text.
 * It does not access files, browser data, credentials, or external services.
 */
public final class HudRenderer {
    private HudRenderer() {
    }

    public static void render(DrawContext context, MinecraftClient client) {
        if (client == null || client.player == null || client.textRenderer == null) {
            return;
        }

        int x = 8;
        int y = 8;
        int lineHeight = 11;

        y = drawLine(context, client, "SpudPvP", x, y, 0x55FFFF);
        y = drawLine(context, client, "FPS: " + client.getCurrentFps(), x, y, 0xFFFFFF);
        y = drawLine(context, client, "CPS: " + SpudPvPClient.cps, x, y, 0xFFFFFF);

        var player = client.player;
        String coordinates = String.format(
                java.util.Locale.ROOT,
                "XYZ: %.1f / %.1f / %.1f",
                player.getX(),
                player.getY(),
                player.getZ()
        );
        y = drawLine(context, client, coordinates, x, y, 0xFFFFFF);
        y = drawLine(
                context,
                client,
                "Sprint: " + (SpudPvPClient.sprintEnabled ? "ON" : "OFF"),
                x,
                y,
                SpudPvPClient.sprintEnabled ? 0x55FF55 : 0xFF7777
        );

        y += 3;
        drawLine(context, client, "W: " + key(client.options.forwardKey)
                + "  A: " + key(client.options.leftKey)
                + "  S: " + key(client.options.backKey)
                + "  D: " + key(client.options.rightKey), x, y, 0xDDDDDD);
    }

    private static int drawLine(
            DrawContext context,
            MinecraftClient client,
            String text,
            int x,
            int y,
            int color
    ) {
        context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, color);
        return y + 11;
    }

    private static String key(net.minecraft.client.option.KeyBinding binding) {
        return binding.isPressed() ? "[X]" : "[ ]";
    }
}
