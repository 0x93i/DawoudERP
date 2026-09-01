package com.daoud.ui;

import com.daoud.dao.WorkerDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Warehouse;
import com.daoud.model.Worker;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class WorkersContent {

    public static Node build(int userId, String username, String role) {

        List<Warehouse> warehouses;
        if (role.equals("admin")) {
            warehouses = new ArrayList<>(WarehouseDAO.getAllWarehouses());
            warehouses.add(0, new Warehouse(-1, "عمال حرة", -1, ""));
        } else {
            warehouses = new ArrayList<>();
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            if (w != null) warehouses.add(w);
        }

        ComboBox<Warehouse> warehouseCombo = new ComboBox<>(FXCollections.observableArrayList(warehouses));
        warehouseCombo.setPromptText("اختار المخزن");

        ListView<Worker> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(500);

        Button addBtn = new Button("إضافة عامل"); addBtn.getStyleClass().add("btn-primary");
        Button attendanceBtn = new Button("تسجيل حضور اليوم"); attendanceBtn.getStyleClass().add("btn-default");
        Button openBtn = new Button("فتح الحساب"); openBtn.getStyleClass().add("btn-default");
        Button deleteBtn = new Button("حذف"); deleteBtn.getStyleClass().add("btn-danger");
        openBtn.setDisable(true); deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            openBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null || !role.equals("admin"));
        });

        warehouseCombo.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w != null)
                listView.setItems(FXCollections.observableArrayList(
                        WorkerDAO.getWorkersByWarehouse(w.getId())));
        });

        addBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            TextField nameField = new TextField();
            nameField.setPromptText("اسم العامل");
            TextField phoneField = new TextField();
            phoneField.setPromptText("رقم التليفون");
            TextField wageField = new TextField();
            wageField.setPromptText("الأجر اليومي");
            Button saveBtn = new Button("حفظ");
            saveBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            VBox layout = new VBox(10,
                    new Label("الاسم:"), nameField,
                    new Label("التليفون:"), phoneField,
                    new Label("الأجر اليومي:"), wageField,
                    saveBtn, errLbl);
            layout.setPadding(new Insets(20));
            Stage dialog = DialogHelper.create("إضافة عامل", layout, 320, 280);
            saveBtn.setOnAction(ev -> {
                if (nameField.getText().trim().isEmpty()) {
                    errLbl.setText("الاسم مطلوب");
                    return;
                }
                double wage = 0;
                try {
                    if (!wageField.getText().trim().isEmpty()) wage = Double.parseDouble(wageField.getText().trim()); }
                catch (NumberFormatException ex) {
                    errLbl.setText("الأجر لازم رقم");
                    return;
                }
//                WorkerDAO.addWorker(nameField.getText().trim(), phoneField.getText().trim(),
//                        w.getId() == -1 ? 0 : w.getId(), wage, userId);
//                WorkerDAO.addWorker(nameField.getText().trim(), phoneField.getText().trim(),
//                        w.getId() == -1 ? null : w.getId(), wage, userId);
                WorkerDAO.addWorker(
                        nameField.getText().trim(),
                        phoneField.getText().trim(),
                        w.getId() == -1 ? null : w.getId(),
                        wage,
                        userId
                );
                listView.setItems(FXCollections.observableArrayList(WorkerDAO.getWorkersByWarehouse(w.getId())));
                dialog.close();
            });
            dialog.show();
        });

        attendanceBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            List<Worker> workers = WorkerDAO.getWorkersByWarehouse(w.getId());
            if (workers.isEmpty()) { new Alert(Alert.AlertType.INFORMATION, "مفيش عمال").show();
                return;
            }
            VBox checkboxBox = new VBox(8);
            List<CheckBox> checkBoxes = new ArrayList<>();
            for (Worker worker : workers) {
                CheckBox cb = new CheckBox(worker.getName() + " — " + worker.getDailyWage() + " جنيه");
                cb.setSelected(true);
                checkBoxes.add(cb);
                checkboxBox.getChildren().add(cb);
            }
            Button saveBtn = new Button("تسجيل الحاضرين");
            saveBtn.getStyleClass().add("btn-primary");
            Label msg = new Label("");
            saveBtn.setOnAction(ev -> {
                int count = 0;
                for (int i = 0; i < workers.size(); i++)
                    if (checkBoxes.get(i).isSelected()) {
                        WorkerDAO.recordAttendance(workers.get(i).getId(), workers.get(i).getDailyWage(), userId);
                        count++;
                    }
                msg.setText("تم تسجيل " + count + " عامل ✓");
            });
            ScrollPane scroll = new ScrollPane(new VBox(10, new Label("الحاضرين:"), checkboxBox, saveBtn, msg));
            scroll.setFitToWidth(true);
            VBox layout = new VBox(scroll); layout.setPadding(new Insets(10));
            DialogHelper.create("تسجيل حضور", layout, 350, 400).show();
        });

        openBtn.setOnAction(e -> {
            Worker selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                MainLayout.loadContent(WorkerDetailContent.build(userId, username, role, selected));
                MainLayout.setTitle("حساب عامل: " + selected.getName());
            }
        });

        deleteBtn.setOnAction(e -> {
            Worker selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        WorkerDAO.deleteWorker(selected.getId());
                        Warehouse w = warehouseCombo.getValue();
                        if (w != null) listView.setItems(FXCollections.observableArrayList(
                                WorkerDAO.getWorkersByWarehouse(w.getId())));
                    }
                });
            }
        });

        HBox buttons = new HBox(10, addBtn, attendanceBtn, openBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.getChildren().addAll(
                new Label("المخزن:"), warehouseCombo,
                new Separator(),
                buttons, listView);

        return new VBox(12, card);
    }
}