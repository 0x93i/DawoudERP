package com.daoud.ui;

import com.daoud.dao.WarehouseDAO;
import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Warehouse;
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
        Button editBtn = new Button("تعديل"); editBtn.getStyleClass().add("btn-default");
        Button deleteBtn = new Button("حذف"); deleteBtn.getStyleClass().add("btn-danger");
        editBtn.setDisable(true);
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            editBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null);
        });

        addBtn.setOnAction(e -> {
            TextField usernameField = new TextField(); usernameField.setPromptText("اسم المستخدم");
            PasswordField passwordField = new PasswordField(); passwordField.setPromptText("كلمة المرور");
            ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("مسؤول مخزن", "أدمن"));
            roleCombo.setValue("مسؤول مخزن");

            // لو الدور "مسؤول مخزن"، نسيب عم داود يختار مخزن موجود يحطه
            // مسؤول عنه دلوقتي (هيستبدل أي مسؤول حالي للمخزن ده).
            Label warehouseLbl = new Label("يبقى مسؤول عن مخزن (اختياري):");
            ComboBox<Warehouse> warehouseCombo = new ComboBox<>();
            warehouseCombo.setPromptText("من غير مخزن دلوقتي");
            warehouseCombo.setMaxWidth(Double.MAX_VALUE);
            warehouseCombo.setCellFactory(cb -> new ListCell<>() {
                @Override protected void updateItem(Warehouse w, boolean empty) {
                    super.updateItem(w, empty);
                    setText(empty || w == null ? null :
                            w.getName() + (w.getManagerName() != null
                                           ? " — المسؤول الحالي: " + w.getManagerName()
                                           : " — من غير مسؤول حالياً"));
                }
            });
            warehouseCombo.setButtonCell(warehouseCombo.getCellFactory().call(null));
            warehouseCombo.setItems(FXCollections.observableArrayList(WarehouseDAO.getAllWarehouses()));

            roleCombo.valueProperty().addListener((obs, old, val) -> {
                boolean isManager = "مسؤول مخزن".equals(val);
                warehouseLbl.setVisible(isManager); warehouseLbl.setManaged(isManager);
                warehouseCombo.setVisible(isManager); warehouseCombo.setManaged(isManager);
            });

            Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            VBox layout = new VBox(10,
                    new Label("اسم المستخدم:"), usernameField,
                    new Label("كلمة المرور:"), passwordField,
                    new Label("الدور:"), roleCombo,
                    warehouseLbl, warehouseCombo,
                    saveBtn, errLbl);
            layout.setPadding(new Insets(20));
            Stage dialog = DialogHelper.create("إضافة مستخدم", layout, 340, 380);
            saveBtn.setOnAction(ev -> {
                if (usernameField.getText().trim().isEmpty() || passwordField.getText().trim().isEmpty()) {
                    errLbl.setText("كل الحقول مطلوبة"); return;
                }
                final String uname = usernameField.getText().trim();
                final String pass = passwordField.getText().trim();
                final String r = roleCombo.getValue().equals("أدمن") ? "admin" : "warehouse_manager";
                final Warehouse selectedWarehouse =
                        "مسؤول مخزن".equals(roleCombo.getValue()) ? warehouseCombo.getValue() : null;

                errLbl.setStyle("-fx-text-fill: #5f5e5a;");
                errLbl.setText("جاري الحفظ...");

                AsyncHelper.runVoid(
                        () -> {
                            String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?) RETURNING id";
                            int newUserId;
                            try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setString(1, uname);
                                stmt.setString(2, pass);
                                stmt.setString(3, r);
                                ResultSet rs = stmt.executeQuery();
                                rs.next();
                                newUserId = rs.getInt("id");
                            }
                            if (selectedWarehouse != null) {
                                WarehouseDAO.updateWarehouseManager(selectedWarehouse.getId(), newUserId);
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

        editBtn.setOnAction(e -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            String[] parts = selected.split("\\|", 2);
            int id = Integer.parseInt(parts[0].trim());
            String rest = parts[1].trim(); // "اسم — الدور بالعربي"
            String currentUsername = rest.split("—")[0].trim();
            String currentRoleArabic = rest.contains("أدمن") ? "أدمن" : "مسؤول مخزن";

            TextField usernameField = new TextField(currentUsername);
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("اتركها فاضية لو مش عايز تغيّرها");
            ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList("مسؤول مخزن", "أدمن"));
            roleCombo.setValue(currentRoleArabic);

            Label warehouseLbl = new Label("يبقى مسؤول عن مخزن (اختياري):");
            ComboBox<Warehouse> warehouseCombo = new ComboBox<>();
            warehouseCombo.setPromptText("من غير مخزن دلوقتي");
            warehouseCombo.setMaxWidth(Double.MAX_VALUE);
            warehouseCombo.setCellFactory(cb -> new ListCell<>() {
                @Override protected void updateItem(Warehouse w, boolean empty) {
                    super.updateItem(w, empty);
                    setText(empty || w == null ? null :
                            w.getName() + (w.getManagerName() != null
                                           ? " — المسؤول الحالي: " + w.getManagerName()
                                           : " — من غير مسؤول حالياً"));
                }
            });
            warehouseCombo.setButtonCell(warehouseCombo.getCellFactory().call(null));
            List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
            warehouseCombo.setItems(FXCollections.observableArrayList(warehouses));
            Warehouse currentlyManaged = WarehouseDAO.getWarehouseByManager(id);
            if (currentlyManaged != null) {
                for (Warehouse w : warehouses) {
                    if (w.getId() == currentlyManaged.getId()) { warehouseCombo.setValue(w); break; }
                }
            }

            boolean startsManager = "مسؤول مخزن".equals(currentRoleArabic);
            warehouseLbl.setVisible(startsManager); warehouseLbl.setManaged(startsManager);
            warehouseCombo.setVisible(startsManager); warehouseCombo.setManaged(startsManager);
            roleCombo.valueProperty().addListener((obs, old, val) -> {
                boolean isManager = "مسؤول مخزن".equals(val);
                warehouseLbl.setVisible(isManager); warehouseLbl.setManaged(isManager);
                warehouseCombo.setVisible(isManager); warehouseCombo.setManaged(isManager);
            });

            Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            VBox layout = new VBox(10,
                    new Label("اسم المستخدم:"), usernameField,
                    new Label("كلمة مرور جديدة:"), passwordField,
                    new Label("الدور:"), roleCombo,
                    warehouseLbl, warehouseCombo,
                    saveBtn, errLbl);
            layout.setPadding(new Insets(20));
            Stage dialog = DialogHelper.create("تعديل مستخدم", layout, 340, 400);

            saveBtn.setOnAction(ev -> {
                if (usernameField.getText().trim().isEmpty()) {
                    errLbl.setText("اسم المستخدم مطلوب"); return;
                }
                final String uname = usernameField.getText().trim();
                final String newPass = passwordField.getText().trim();
                final String r = roleCombo.getValue().equals("أدمن") ? "admin" : "warehouse_manager";
                final Warehouse selectedWarehouse =
                        "مسؤول مخزن".equals(roleCombo.getValue()) ? warehouseCombo.getValue() : null;

                errLbl.setStyle("-fx-text-fill: #5f5e5a;");
                errLbl.setText("جاري الحفظ...");

                AsyncHelper.runVoid(
                        () -> {
                            String sql = newPass.isEmpty()
                                    ? "UPDATE users SET username = ?, role = ? WHERE id = ?"
                                    : "UPDATE users SET username = ?, password_hash = ?, role = ? WHERE id = ?";
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                                if (newPass.isEmpty()) {
                                    stmt.setString(1, uname);
                                    stmt.setString(2, r);
                                    stmt.setInt(3, id);
                                } else {
                                    stmt.setString(1, uname);
                                    stmt.setString(2, newPass);
                                    stmt.setString(3, r);
                                    stmt.setInt(4, id);
                                }
                                stmt.executeUpdate();
                            }

                            // ── تحديث ربط المخزن بالمسؤول حسب الاختيار الجديد ──
                            Warehouse oldWarehouse = WarehouseDAO.getWarehouseByManager(id);
                            if (selectedWarehouse == null) {
                                // بقى أدمن، أو مسؤول مخزن من غير مخزن محدد
                                if (oldWarehouse != null) {
                                    WarehouseDAO.clearWarehouseManager(oldWarehouse.getId());
                                }
                            } else if (oldWarehouse == null || oldWarehouse.getId() != selectedWarehouse.getId()) {
                                if (oldWarehouse != null) {
                                    WarehouseDAO.clearWarehouseManager(oldWarehouse.getId());
                                }
                                WarehouseDAO.updateWarehouseManager(selectedWarehouse.getId(), id);
                            }
                        },
                        () -> { refreshList(listView); dialog.close(); },
                        error -> {
                            errLbl.setStyle("-fx-text-fill: #b91c1c;");
                            errLbl.setText("حصل خطأ، جرب اسم مستخدم تاني");
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

        HBox buttons = new HBox(10, addBtn, editBtn, deleteBtn);
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
