package com.daoud;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.net.*;

public class UpdateChecker {

    private static final String CURRENT_VERSION = "2.9";
    private static final String GITHUB_API = "https://api.github.com/repos/0x93i/DawoudERP/releases/latest";

    public static void check() {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                String json = sb.toString();

                // استخرج الـ tag_name
                int tagStart = json.indexOf("\"tag_name\":\"") + 12;
                int tagEnd = json.indexOf("\"", tagStart);
                String latestVersion = json.substring(tagStart, tagEnd).replace("v", "");

                // استخرج الـ download URL
                int urlStart = json.indexOf("\"browser_download_url\":\"") + 24;
                int urlEnd = json.indexOf("\"", urlStart);
                String downloadUrl = urlStart > 24 ? json.substring(urlStart, urlEnd) : "";

                if (!latestVersion.equals(CURRENT_VERSION)) {
                    final String finalVersion = latestVersion;
                    final String finalUrl = downloadUrl;
                    Platform.runLater(() -> showUpdateDialog(finalVersion, finalUrl));
                }

            } catch (Exception e) {
                // مفيش انترنت أو GitHub — متعملش حاجة
                System.out.println("Update check skipped: " + e.getMessage());
            }
        }).start();
    }

    private static void showUpdateDialog(String newVersion, String downloadUrl) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("تحديث جديد متاح");
        alert.setHeaderText("🆕 إصدار جديد: v" + newVersion);
        alert.setContentText(
                "الإصدار الحالي: v" + CURRENT_VERSION + "\n" +
                        "الإصدار الجديد: v" + newVersion + "\n\n" +
                        "هل تريد فتح صفحة التحديث؟"
        );

        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !downloadUrl.isEmpty()) {
                try {
                    java.awt.Desktop.getDesktop().browse(new URI(downloadUrl));
                } catch (Exception e) {
                    System.err.println("Cannot open browser: " + e.getMessage());
                }
            }
        });
    }
}