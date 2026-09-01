package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class WarehousesScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label title = new Label("المخازن");

        ListView<Warehouse> listView = new ListView<>();
        refreshList(listView, userId, role);

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> MainScreen.show(stage, userId, username, role));

        Button addBtn = new Button("إضافة مخزن");
        Button deleteBtn = new Button("حذف المخزن");
        Button manageSuppliersBtn = new Button("موردي المخزن");
        Button openBtn = new Button("فتح المخزن");

        deleteBtn.setDisable(true);
        manageSuppliersBtn.setDisable(true);
        openBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean has = selected != null;
            deleteBtn.setDisable(!has || !role.equals("admin"));
            manageSuppliersBtn.setDisable(!has || !role.equals("admin"));
            openBtn.setDisable(!has);
        });

        addBtn.setOnAction(e -> showAddDialog(listView, userId, role));

        deleteBtn.setOnAction(e -> {
            Warehouse selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "هتحذف المخزن: " + selected.getName() + "؟");
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
            if (selected != null)
                WarehouseDetailScreen.show(stage, userId, username, role, selected);
        });

        HBox buttons;
        if (role.equals("admin")) {
            buttons = new HBox(10, addBtn, openBtn, manageSuppliersBtn, deleteBtn, backBtn);
        } else {
            buttons = new HBox(10, openBtn, backBtn);
        }
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12, title, listView, buttons);
        layout.setPadding(new Insets(20));

        stage.setScene(new Scene(layout, 800, 600));
    }

    private static void refreshList(ListView<Warehouse> listView, int userId, String role) {
        List<Warehouse> warehouses;
        if (role.equals("admin")) {
            warehouses = WarehouseDAO.getAllWarehouses();
        } else {
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            warehouses = new ArrayList<>();
            if (w != null) warehouses.add(w);
        }
        listView.setItems(FXCollections.observableArrayList(warehouses));
    }

    private static void showAddDialog(ListView<Warehouse> listView, int userId, String role) {
        Stage dialog = new Stage();
        dialog.setTitle("إضافة مخزن جديد");

        TextField nameField = new TextField();
        nameField.setPromptText("اسم المخزن");

        List<String> managers = WarehouseDAO.getAllManagers();
        ComboBox<String> managerCombo = new ComboBox<>(
                FXCollections.observableArrayList(managers));
        managerCombo.setPromptText("اختار المسؤول");

        Button saveBtn = new Button("حفظ");
        Label errorLbl = new Label("");

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("اسم المخزن مطلوب"); return; }
            if (managerCombo.getValue() == null) { errorLbl.setText("اختار مسؤول"); return; }
            int managerId = Integer.parseInt(managerCombo.getValue().split("\\|")[0]);
            WarehouseDAO.addWarehouse(name, managerId);
            refreshList(listView, userId, role);
            dialog.close();
        });

        VBox layout = new VBox(10,
                new Label("اسم المخزن:"), nameField,
                new Label("المسؤول:"), managerCombo,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        dialog.setScene(new Scene(layout, 350, 250));
        dialog.show();
    }

    private static void showManageSuppliersDialog(Warehouse warehouse) {
        Stage dialog = new Stage();
        dialog.setTitle("موردي المخزن: " + warehouse.getName());

        List<Supplier> allSuppliers = SupplierDAO.getAllSuppliers();
        List<String> warehouseSuppliers = WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId());

        ListView<String> assignedList = new ListView<>(
                FXCollections.observableArrayList(warehouseSuppliers));

        ComboBox<Supplier> supplierCombo = new ComboBox<>(
                FXCollections.observableArrayList(allSuppliers));
        supplierCombo.setPromptText("اختار مورد تضيفه");

        Button addSupBtn = new Button("إضافة");
        Button removeSupBtn = new Button("إزالة المحدد");
        Label msgLbl = new Label("");

        addSupBtn.setOnAction(e -> {
            Supplier s = supplierCombo.getValue();
            if (s == null) { msgLbl.setText("اختار مورد"); return; }
            WarehouseDAO.assignSupplierToWarehouse(warehouse.getId(), s.getId());
            assignedList.setItems(FXCollections.observableArrayList(
                    WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId())));
            msgLbl.setText("تم الإضافة");
        });

        removeSupBtn.setOnAction(e -> {
            String selected = assignedList.getSelectionModel().getSelectedItem();
            if (selected == null) { msgLbl.setText("اختار مورد تشيله"); return; }
            int supId = Integer.parseInt(selected.split("\\|")[0]);
            WarehouseDAO.removeSupplierFromWarehouse(warehouse.getId(), supId);
            assignedList.setItems(FXCollections.observableArrayList(
                    WarehouseDAO.getSuppliersOfWarehouse(warehouse.getId())));
            msgLbl.setText("تم الإزالة");
        });

        VBox layout = new VBox(10,
                new Label("الموردين المرتبطين:"), assignedList,
                new Label("إضافة مورد:"), supplierCombo,
                new HBox(10, addSupBtn, removeSupBtn),
                msgLbl);
        layout.setPadding(new Insets(20));
        dialog.setScene(new Scene(layout, 380, 400));
        dialog.show();
    }
}