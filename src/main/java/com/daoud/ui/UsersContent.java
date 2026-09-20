package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UsersContent {

    public static Node build(int userId, String username, String role) {
        ListView<String> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(350);
        listView.setPlaceholder(new Label("جاري التحميل..."));
        refreshList(listView);

        Button addBtn = new Button("إضافة مستخدم"); addBtn.getStyleClass().add("btn-primary");
        Button deleteBtn = new Button("حذف"); deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                deleteBtn.setDisable(selected == null));

        addBtn.setOnAction(e -> {
            TextField usernameField = new TextField(); usernameField.setPromptText("اسم المستخدم");
            PasswordField passwordField = new PasswordField(); passwordField.setPromptText("كلمة المرور");
            ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("مسؤول مخزن", "أدمن"));
            roleCombo.setValue("مسؤول مخزن");
            Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            VBox layout = new VBox(10,
                    new Label("اسم المستخدم:"), usernameField,
                    new Label("كلمة المرور:"), passwordField,
                    new Label("الدور:"), roleCombo,
                    saveBtn, errLbl);
            layout.setPadding(new Insets(20));
            Stage dialog = DialogHelper.create("إضافة مستخدم", layout, 320, 280);
            saveBtn.setOnAction(ev -> {
                if (usernameField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty()) {
                    errLbl.setText("كل الحقول مطلوبة"); return;
                }
                final String uname = usernameField.getText().trim();
                final String pass = passwordField.getText().trim();
                final String r = roleCombo.getValue().equals("أدمن") ? "admin" : "warehouse_manager";

                errLbl.setStyle("-fx-text-fill: #5f5e5a;");
                errLbl.setText("جاري الحفظ...");

                AsyncHelper.runVoid(
                        () -> {
                            String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
                            try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setString(1, uname);
                                stmt.setString(2, pass);
                                stmt.setString(3, r);
                                stmt.executeUpdate();
                            }
                        },
                        () -> { refreshList(listView); dialog.close(); },
                        error -> {
                            errLbl.setStyle("-fx-text-fill: #b91c1c;");
                            errLbl.setText("الاسم موجود بالفعل");
                        },
                        saveBtn
                );
            });
            dialog.show();
        });

        deleteBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                int id = Integer.parseInt(selected.split("\\|")[0].trim());
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف المستخدم؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        deleteBtn.setDisable(true);
                        AsyncHelper.runVoid(
                                () -> {
                                    String sql = "DELETE FROM users WHERE id = ?";
                                    try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                                        stmt.setInt(1, id); stmt.executeUpdate();
                                    }
                                },
                                () -> refreshList(listView),
                                error -> refreshList(listView)
                        );
                    }
                });
            }
        });

        HBox buttons = new HBox(10, addBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10); card.getStyleClass().add("card");
        card.getChildren().addAll(new Label("المستخدمين:"), buttons, listView);

        return new VBox(12, card);
    }

    private static void refreshList(ListView<String> listView) {
        AsyncHelper.run(
                () -> {
                    List<String> users = new ArrayList<>();
                    String sql = "SELECT id, username, role FROM users ORDER BY role, username";
                    try (Connection conn = DatabaseManager_online.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next())
                            users.add(rs.getInt("id") + " | " + rs.getString("username") + " — " + (rs.getString("role").equals("admin") ? "أدمن" : "مسؤول مخزن"));
                    }
                    return users;
                },
                users -> listView.setItems(FXCollections.observableArrayList(users)),
                error -> listView.setPlaceholder(new Label("حصل خطأ في التحميل"))
        );
    }
}
