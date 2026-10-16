package dev.spudpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class HudRenderer {
    private static double zoomOriginalFov = -1;

    private HudRenderer() {}

    public static void render(DrawContext ctx, MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        int x = 8;
        int y = 8;
        ctx.fill(x - 4, y - 4, x + 142, y + 66, 0x990B1018);
        ctx.drawText(client.textRenderer, "§bSpud§fPvP", x, y, 0xFFFFFF, true);
        ctx.drawText(client.textRenderer, "FPS: " + MinecraftClient.getCurrentFps(), x, y + 12, 0xFFFFFF, true);
        ctx.drawText(client.textRenderer, "CPS: " + SpudPvPClient.cps, x, y + 24, 0xFFFFFF, true);
        ctx.drawText(client.textRenderer, "XYZ: "
                + MathHelper.floor(player.getX()) + " "
                + MathHelper.floor(player.getY()) + " "
                + MathHelper.floor(player.getZ()), x, y + 36, 0xFFFFFF, true);
        ctx.drawText(client.textRenderer, "Sprint: "
                + (SpudPvPClient.sprintEnabled ? "ON" : "OFF"), x, y + 48,
                SpudPvPClient.sprintEnabled ? 0x55FF55 : 0xAAAAAA, true);

        renderKeystrokes(ctx, client, client.getWindow().getScaledWidth() - 62,
                client.getWindow().getScaledHeight() - 58);
        applyZoom(client);
    }

    private static void renderKeystrokes(DrawContext ctx, MinecraftClient client, int x, int y) {
        boolean w = client.options.forwardKey.isPressed();
        boolean a = client.options.leftKey.isPressed();
        boolean s = client.options.backKey.isPressed();
        boolean d = client.options.rightKey.isPressed();
        boolean jump = client.options.jumpKey.isPressed();

        key(ctx, x + 20, y, "W", w);
        key(ctx, x, y + 20, "A", a);
        key(ctx, x + 20, y + 20, "S", s);
        key(ctx, x + 40, y + 20, "D", d);
        key(ctx, x, y + 40, "JUMP", jump);
    }

    private static void key(DrawContext ctx, int x, int y, String label, boolean pressed) {
        int width = label.equals("JUMP") ? 60 : 18;
        int background = pressed ? 0xCC36A269 : 0xAA151A22;
        ctx.fill(x, y, x + width, y + 16, background);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, label,
                x + width / 2, y + 4, 0xFFFFFF);
    }

    private static void applyZoom(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        int normal = client.options.getFov().getValue();
        if (SpudPvPClient.zoomHeld) {
            if (zoomOriginalFov < 0) {
                zoomOriginalFov = normal;
            }
            client.options.getFov().setValue((int) Math.min(30, zoomOriginalFov));
        } else if (zoomOriginalFov >= 0) {
            client.options.getFov().setValue((int) zoomOriginalFov);
            zoomOriginalFov = -1;
        }
    }
}
