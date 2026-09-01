package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LoginScreen {

    public static void show(Stage stage) {
        Label titleLabel = new Label("Dawoud ERP — تسجيل الدخول");

        TextField usernameField = new TextField();
        usernameField.setPromptText("اسم المستخدم");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("كلمة المرور");

        Button loginButton = new Button("دخول");
        Label errorLabel = new Label("");

        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("من فضلك ادخل اسم المستخدم وكلمة المرور");
                return;
            }

            String[] result = authenticate(username, password);
            if (result != null) {
                int userId = Integer.parseInt(result[0]);
                String role = result[1];
                MainLayout.show(stage, userId, username, role);
            } else {
                errorLabel.setText("اسم المستخدم أو كلمة المرور غلط");
            }
        });

        VBox layout = new VBox(12,
                titleLabel,
                new Label("اسم المستخدم:"), usernameField,
                new Label("كلمة المرور:"), passwordField,
                loginButton,
                errorLabel
        );
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        layout.setMaxWidth(300);

        VBox wrapper = new VBox(layout);
        wrapper.setAlignment(Pos.CENTER);

        Scene scene = new Scene(wrapper, 800, 600);
        stage.setScene(scene);
        scene.getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        stage.setTitle("برنامج عم داود");
        stage.show();
    }

    private static String[] authenticate(String username, String password) {
        String sql = "SELECT id, role FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("role")
                };
            }
        } catch (SQLException e) {
            System.err.println("Auth error: " + e.getMessage());
        }
        return null;
    }
}