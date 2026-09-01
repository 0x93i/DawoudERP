package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsersScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label title = new Label("إدارة المستخدمين");

        ListView<String> listView = new ListView<>();
        refreshList(listView);

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> MainScreen.show(stage, userId, username, role));

        Button addBtn = new Button("إضافة مستخدم");
        Button deleteBtn = new Button("حذف المحدد");
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                deleteBtn.setDisable(selected == null));

        addBtn.setOnAction(e -> showAddDialog(listView));

        deleteBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                int id = Integer.parseInt(selected.split("\\|")[0].trim());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف المستخدم؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        deleteUser(id);
                        refreshList(listView);
                    }
                });
            }
        });

        HBox buttons = new HBox(10, addBtn, deleteBtn, backBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12, title, listView, buttons);
        layout.setPadding(new Insets(20));

        stage.setScene(new Scene(layout, 800, 600));
    }

    private static void refreshList(ListView<String> listView) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT id, username, role FROM users ORDER BY role, username";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getInt("id") + " | " + rs.getString("username") +
                        " — " + (rs.getString("role").equals("admin") ? "أدمن" : "مسؤول مخزن"));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        listView.setItems(FXCollections.observableArrayList(users));
    }

    private static void showAddDialog(ListView<String> listView) {
        Stage dialog = new Stage();
        dialog.setTitle("إضافة مستخدم جديد");

        TextField usernameField = new TextField();
        usernameField.setPromptText("اسم المستخدم");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("كلمة المرور");

        ComboBox<String> roleCombo = new ComboBox<>(
                FXCollections.observableArrayList("مسؤول مخزن", "أدمن"));
        roleCombo.setValue("مسؤول مخزن");

        Button saveBtn = new Button("حفظ");
        Label errorLbl = new Label("");

        saveBtn.setOnAction(e -> {
            String uname = usernameField.getText().trim();
            String pass = passwordField.getText().trim();
            if (uname.isEmpty() || pass.isEmpty()) {
                errorLbl.setText("كل الحقول مطلوبة");
                return;
            }
            String role = roleCombo.getValue().equals("أدمن") ? "admin" : "warehouse_manager";
            String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseManager_online.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uname);
                stmt.setString(2, pass);
                stmt.setString(3, role);
                stmt.executeUpdate();
                refreshList(listView);
                dialog.close();
            } catch (SQLException ex) {
                errorLbl.setText("الاسم موجود بالفعل");
            }
        });

        VBox layout = new VBox(10,
                new Label("اسم المستخدم:"), usernameField,
                new Label("كلمة المرور:"), passwordField,
                new Label("الدور:"), roleCombo,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        dialog.setScene(new Scene(layout, 320, 280));
        dialog.show();
    }

    private static void deleteUser(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}