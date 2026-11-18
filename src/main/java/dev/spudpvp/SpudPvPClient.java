package dev.spudpvp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Minimal local-only SpudPvP client mod.
 * No browser, credential, cookie, Discord, or Minecraft-session access.
 * No network connections or telemetry.
 */
public final class SpudPvPClient implements ClientModInitializer {
    private KeyBinding sprintKey;
    private boolean sprintEnabled;

    @Override
    public void onInitializeClient() {
        sprintKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.spudpvp.sprint",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (sprintKey.wasPressed()) {
                sprintEnabled = !sprintEnabled;
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("Toggle Sprint: " + (sprintEnabled ? "ON" : "OFF")),
                        true
                    );
                }
            }

            if (client.player != null && sprintEnabled
                && !client.player.isSneaking()
                && client.player.input.hasForwardMovement()) {
                client.player.setSprinting(true);
            }
        });
    }
}
