package dev.spudpvp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SpudPvPClient implements ClientModInitializer {
    public static final String MOD_ID = "spudpvp";
    public static boolean sprintEnabled = false;
    public static boolean zoomHeld = false;
    public static boolean hudEnabled = true;
    public static int cps = 0;

    private static long cpsWindowStart = System.currentTimeMillis();
    private static int clicksInWindow = 0;

    private static KeyBinding sprintKey;
    private static KeyBinding zoomKey;
    private static KeyBinding hudKey;
    private static KeyBinding resetKey;

    @Override
    public void onInitializeClient() {
        sprintKey = register("key.spudpvp.sprint", GLFW.GLFW_KEY_R);
        zoomKey = register("key.spudpvp.zoom", GLFW.GLFW_KEY_C);
        hudKey = register("key.spudpvp.hud", GLFW.GLFW_KEY_H);
        resetKey = register("key.spudpvp.reset", GLFW.GLFW_KEY_UNKNOWN);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sprintKey.wasPressed()) {
                sprintEnabled = !sprintEnabled;
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Toggle Sprint: " + (sprintEnabled ? "ON" : "OFF")), true);
                }
            }

            while (hudKey.wasPressed()) {
                hudEnabled = !hudEnabled;
            }

            while (resetKey.wasPressed()) {
                sprintEnabled = false;
                hudEnabled = true;
                zoomHeld = false;
            }

            zoomHeld = zoomKey.isPressed();

            if (client.player != null && sprintEnabled && !client.player.isSneaking()
                    && client.player.input.hasForwardMovement()) {
                client.player.setSprinting(true);
            }

            long now = System.currentTimeMillis();
            if (now - cpsWindowStart >= 1000) {
                cps = clicksInWindow;
                clicksInWindow = 0;
                cpsWindowStart = now;
            }

            if (client.player != null && client.options.attackKey.wasPressed()) {
                clicksInWindow++;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || !hudEnabled) {
                return;
            }
            HudRenderer.render(drawContext, client);
        });
    }

    private static KeyBinding register(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                translationKey,
                InputUtil.Type.KEYSYM,
                keyCode,
                KeyBinding.Category.MISC
        ));
    }
}
