package com.daoud;

import com.daoud.db.DatabaseManager_online;
import com.daoud.ui.LoginScreen;
import javafx.application.Application;
import javafx.collections.ListChangeListener;
import javafx.geometry.NodeOrientation;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.swing.JOptionPane;

public class Main extends Application {

    @Override
    public void start(Stage stage) {
        try {
            Window.getWindows().addListener((ListChangeListener<Window>) change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Window window : change.getAddedSubList()) {
                            if (window.getScene() != null && window.getScene().getRoot() != null) {
                                window.getScene().getRoot()
                                        .setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                            }
                            window.sceneProperty().addListener((obs, oldScene, newScene) -> {
                                if (newScene != null && newScene.getRoot() != null) {
                                    newScene.getRoot()
                                            .setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                                }
                            });
                        }
                    }
                }
            });

            // تهيئة الداتابيز
            DatabaseManager_online.initializeDatabase();
            DatabaseManager_online.createDefaultAdmin();

            // عرض شاشة الدخول
            LoginScreen.show(stage);

            // تأمين تطبيق الاتجاه العربي
            if (stage.getScene() != null && stage.getScene().getRoot() != null) {
                stage.getScene().getRoot().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            }

        } catch (Throwable t) {
            t.printStackTrace();
            // إظهار نافذة خطأ صريحة من الويندوز بدلاً من الإغلاق الصامت
            JOptionPane.showMessageDialog(null,
                    "حدث خطأ أثناء تشغيل التطبيق:\n" + t.toString() + "\n" + (t.getCause() != null ? t.getCause() : ""),
                    "خطأ في التشغيل",
                    JOptionPane.ERROR_MESSAGE);
        }
        // Check for updates in background
        UpdateChecker.check();
    }

    public static void main(String[] args) {
        launch(args);
        // بعد ما البرنامج يفتح
    }
}