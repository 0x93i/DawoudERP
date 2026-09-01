package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class SuppliersContent {

    public static Node build(int userId, String username, String role) {
        ListView<Supplier> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(400);
        refreshList(listView, userId, role);

        Button addBtn = new Button("إضافة مورد");
        addBtn.getStyleClass().add("btn-primary");
        Button deleteBtn = new Button("حذف");
        deleteBtn.getStyleClass().add("btn-danger");
        Button openBtn = new Button("فتح الحساب");
        openBtn.getStyleClass().add("btn-default");

        addBtn.setDisable(!role.equals("admin"));
        deleteBtn.setDisable(true);
        openBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            deleteBtn.setDisable(selected == null || !role.equals("admin"));
            openBtn.setDisable(selected == null);
        });

        addBtn.setOnAction(e -> showAddDialog(userId, role, listView));

        deleteBtn.setOnAction(e -> {
            Supplier selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        SupplierDAO.deleteSupplier(selected.getId());
                        refreshList(listView, userId, role);
                    }
                });
            }
        });

        openBtn.setOnAction(e -> {
            Supplier selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                MainLayout.loadContent(SupplierDetailContent.build(userId, username, role, selected));
                MainLayout.setTitle("حساب: " + selected.getName());
            }
        });

        HBox buttons = new HBox(10, addBtn, openBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label cardTitle = new Label("قائمة الموردين");
        cardTitle.getStyleClass().add("card-title");
        card.getChildren().addAll(cardTitle, buttons, listView);

        return new VBox(12, card);
    }

    private static void refreshList(ListView<Supplier> listView, int userId, String role) {
        List<Supplier> suppliers;
        if (role.equals("admin")) {
            suppliers = SupplierDAO.getAllSuppliers();
        } else {
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            suppliers = w != null ? WarehouseDAO.getSuppliersByWarehouse(w.getId()) : new ArrayList<>();
        }
        listView.setItems(FXCollections.observableArrayList(suppliers));
    }

    private static void showAddDialog(int userId, String role, ListView<Supplier> listView) {
        TextField nameField = new TextField(); nameField.setPromptText("اسم المورد");
        TextField phoneField = new TextField(); phoneField.setPromptText("رقم التليفون");
        TextField sectorField = new TextField(); sectorField.setPromptText("القطاع");
        TextField floorField = new TextField(); floorField.setPromptText("الأرضية");
        Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
        Label errorLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("الاسم:"), nameField,
                new Label("التليفون:"), phoneField,
                new Label("القطاع:"), sectorField,
                new Label("الأرضية:"), floorField,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));

        Stage dialog = DialogHelper.create("إضافة مورد جديد", layout, 350, 340);

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("الاسم مطلوب"); return; }
            double floor = 0;
            try {
                if (!floorField.getText().trim().isEmpty())
                    floor = Double.parseDouble(floorField.getText().trim());
            } catch (NumberFormatException ex) { errorLbl.setText("الأرضية لازم رقم"); return; }
            SupplierDAO.addSupplier(name, phoneField.getText().trim(), sectorField.getText().trim(), floor, userId);
            refreshList(listView, userId, role);
            dialog.close();
        });

        dialog.show();
    }
}