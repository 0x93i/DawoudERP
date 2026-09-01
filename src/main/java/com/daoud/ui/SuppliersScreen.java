package com.daoud.ui;

import com.daoud.dao.WarehouseDAO;
import com.daoud.dao.SupplierDAO;
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

public class SuppliersScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label title = new Label("الموردين");

        ListView<Supplier> listView = new ListView<>();
        refreshList(listView, userId, role);

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> MainScreen.show(stage, userId, username, role));

        Button addBtn = new Button("إضافة مورد جديد");
        Button deleteBtn = new Button("حذف المورد المحدد");
        Button openBtn = new Button("فتح حساب المورد");

        // الإضافة والحذف للأدمن بس
        //addBtn.setDisable(!role.equals("admin"));
        // الإضافة للكل
        addBtn.setDisable(false);
        deleteBtn.setDisable(true);
        openBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean hasSelection = selected != null;
            deleteBtn.setDisable(!hasSelection || !role.equals("admin"));
            openBtn.setDisable(!hasSelection);
        });

        addBtn.setOnAction(e -> showAddDialog(userId, role, listView));

        deleteBtn.setOnAction(e -> {
            Supplier selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "هتحذف المورد: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        SupplierDAO.deleteSupplier(selected.getId());
                        refreshList(listView, userId, role);
                    }
                });
            }
        });

        openBtn.setOnAction(e -> {
            Supplier selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                SupplierDetailScreen.show(stage, userId, username, role, selected);
            }
        });

        HBox buttons = new HBox(10, addBtn, openBtn, deleteBtn, backBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12, title, listView, buttons);
        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout, 800, 600);
        stage.setScene(scene);
    }

    private static void refreshList(ListView<Supplier> listView, int userId, String role) {
        List<Supplier> suppliers;
        if (role.equals("admin")) {
            suppliers = SupplierDAO.getAllSuppliers();
        } else {
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            if (w != null) {
                suppliers = WarehouseDAO.getSuppliersByWarehouse(w.getId());
            } else {
                suppliers = new ArrayList<>();
            }
        }
        listView.setItems(FXCollections.observableArrayList(suppliers));
    }

    private static void showAddDialog(int userId, String role, ListView<Supplier> listView) {
        Stage dialog = new Stage();
        dialog.setTitle("إضافة مورد جديد");

        TextField nameField = new TextField();
        nameField.setPromptText("اسم المورد");

        TextField sectorField = new TextField();
        sectorField.setPromptText("القطاع (اختياري)");

        TextField floorField = new TextField();
        floorField.setPromptText("الأرضية (اختياري)");

        Button saveBtn = new Button("حفظ");
        Label errorLbl = new Label("");

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                errorLbl.setText("اسم المورد مطلوب");
                return;
            }
            double floor = 0;
            try {
                if (!floorField.getText().trim().isEmpty())
                    floor = Double.parseDouble(floorField.getText().trim());
            } catch (NumberFormatException ex) {
                errorLbl.setText("الأرضية لازم تكون رقم");
                return;
            }
//            SupplierDAO.addSupplier(name, sectorField.getText().trim(), floor, userId);
            SupplierDAO.addSupplier(name, "", sectorField.getText().trim(), floor, userId);
            refreshList(listView, userId, role);
            dialog.close();
        });

        VBox layout = new VBox(10,
                new Label("اسم المورد:"), nameField,
                new Label("القطاع:"), sectorField,
                new Label("الأرضية:"), floorField,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER_LEFT);

        dialog.setScene(new Scene(layout, 350, 320));
        dialog.show();
    }
}