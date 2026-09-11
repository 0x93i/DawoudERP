package com.daoud.ui;

import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Warehouse;
import com.daoud.model.Supplier;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class WarehousesContent {

    public static Node build(Stage stage, int userId, String username, String role) {
        ListView<Warehouse> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(400);
        refreshList(listView, userId, role);

        Button addBtn = new Button("إضافة مخزن");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setDisable(!role.equals("admin"));

        Button openBtn = new Button("فتح المخزن");
        openBtn.getStyleClass().add("btn-default");
        openBtn.setDisable(true);

        Button manageSuppliersBtn = new Button("موردي المخزن");
        manageSuppliersBtn.getStyleClass().add("btn-default");
        manageSuppliersBtn.setDisable(true);

        Button deleteBtn = new Button("حذف");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean has = selected != null;
            openBtn.setDisable(!has);
            manageSuppliersBtn.setDisable(!has || !role.equals("admin"));
            deleteBtn.setDisable(!has || !role.equals("admin"));
        });

        addBtn.setOnAction(e -> showAddDialog(listView, userId, role));

        deleteBtn.setOnAction(e -> {
            Warehouse selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        WarehouseDAO.deleteWarehouse(selected.getId());
                        refreshList(listView, userId, role);
                    }
                });
            }
        });

        manageSuppliersBtn.setOnAction(e -> {
            Warehouse selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) showManageSuppliersDialog(selected);
        });

        openBtn.setOnAction(e -> {
            Warehouse selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                MainLayout.loadContent(WarehouseDetailContent.build(userId, username, role, selected));
                MainLayout.setTitle("مخزن: " + selected.getName());
            }
        });

        HBox buttons = new HBox(10, addBtn, openBtn, manageSuppliersBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label cardTitle = new Label("قائمة المخازن");
        cardTitle.getStyleClass().add("card-title");
        card.getChildren().addAll(cardTitle, buttons, listView);

        return new VBox(12, card);
    }

    private static void refreshList(ListView<Warehouse> listView, int userId, String role) {
        List<Warehouse> warehouses;
        if (role.equals("admin")) {
            warehouses = WarehouseDAO.getAllWarehouses();
        } else {
            warehouses = new ArrayList<>();
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            if (w != null) warehouses.add(w);
        }
        listView.setItems(FXCollections.observableArrayList(warehouses));
    }

    private static void showAddDialog(ListView<Warehouse> listView, int userId, String role) {
        TextField nameField = new TextField(); nameField.setPromptText("اسم المخزن");
        List<String> managers = WarehouseDAO.getAllManagers();
        ComboBox<String> managerCombo = new ComboBox<>(FXCollections.observableArrayList(managers));
        managerCombo.setPromptText("اختار المسؤول");
        Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
        Label errorLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("اسم المخزن:"), nameField,
                new Label("المسؤول:"), managerCombo,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));

        Stage dialog = DialogHelper.create("إضافة مخزن", layout, 350, 250);

        saveBtn.setOnAction(e -> {
            if (nameField.getText().trim().isEmpty()) { errorLbl.setText("الاسم مطلوب"); return; }
            if (managerCombo.getValue() == null) { errorLbl.setText("اختار مسؤول"); return; }
            int managerId = Integer.parseInt(managerCombo.getValue().split("\\|")[0]);
            WarehouseDAO.addWarehouse(nameField.getText().trim(), managerId);

// عمل خزنة للمخزن الجديد تلقائي
            String warehouseName = nameField.getText().trim();
            String vaultSql = "INSERT INTO vaults (name, owner_type, owner_id) " +
                    "SELECT 'خزنة ' || name, 'warehouse', id FROM warehouses WHERE name = ? ORDER BY id DESC LIMIT 1";
            try (java.sql.Connection conn = com.daoud.db.DatabaseManager_online.getConnection();
                 java.sql.PreparedStatement vstmt = conn.prepareStatement(vaultSql)) {
                vstmt.setString(1, warehouseName);
                vstmt.executeUpdate();
            } catch (java.sql.SQLException ex) {
                System.err.println("Vault creation error: " + ex.getMessage());
            }

            refreshList(listView, userId, role);
            dialog.close();
        });

        dialog.show();
    }

    private static void showManageSuppliersDialog(Warehouse warehouse) {
        List<Supplier> allSuppliers = com.daoud.dao.SupplierDAO.getAllSuppliers();
        List<String> warehouseSuppliers = WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId());

        ListView<String> assignedList = new ListView<>(FXCollections.observableArrayList(warehouseSuppliers));
        assignedList.setPrefHeight(200);

        ComboBox<Supplier> supplierCombo = new ComboBox<>(FXCollections.observableArrayList(allSuppliers));
        supplierCombo.setPromptText("اختار مورد");

        Button addSupBtn = new Button("إضافة"); addSupBtn.getStyleClass().add("btn-primary");
        Button removeSupBtn = new Button("إزالة"); removeSupBtn.getStyleClass().add("btn-danger");
        Label msgLbl = new Label("");

        addSupBtn.setOnAction(e -> {
            Supplier s = supplierCombo.getValue();
            if (s == null) { msgLbl.setText("اختار مورد"); return; }
            WarehouseDAO.assignSupplierToWarehouse(warehouse.getId(), s.getId());
            assignedList.setItems(FXCollections.observableArrayList(
                    WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId())));
            msgLbl.setText("تم");
        });

        removeSupBtn.setOnAction(e -> {
            String selected = assignedList.getSelectionModel().getSelectedItem();
            if (selected == null) { msgLbl.setText("اختار مورد"); return; }
            int supId = Integer.parseInt(selected.split("\\|")[0]);
            WarehouseDAO.removeSupplierFromWarehouse(warehouse.getId(), supId);
            assignedList.setItems(FXCollections.observableArrayList(
                    WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId())));
            msgLbl.setText("تم");
        });

        VBox layout = new VBox(10,
                new Label("الموردين المرتبطين:"), assignedList,
                new Label("إضافة مورد:"), supplierCombo,
                new HBox(10, addSupBtn, removeSupBtn), msgLbl);
        layout.setPadding(new Insets(20));

        DialogHelper.create("موردي المخزن: " + warehouse.getName(), layout, 380, 420).show();
    }
}