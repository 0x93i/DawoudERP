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

        ComboBox<Warehouse> warehouseCombo = new ComboBox<>();
        warehouseCombo.setPromptText("جاري تحميل المخازن...");
        warehouseCombo.setDisable(true);

        ListView<Worker> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(500);
        listView.setPlaceholder(new Label("اختار مخزن الأول"));

        // ── تحميل قائمة المخازن في الخلفية ──
        AsyncHelper.run(
                () -> {
                    if (role.equals("admin")) {
                        List<Warehouse> list = new ArrayList<>(WarehouseDAO.getAllWarehouses());
                        list.add(0, new Warehouse(-1, "عمال حرة", -1, ""));
                        return list;
                    }
                    List<Warehouse> list = new ArrayList<>();
                    Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
                    if (w != null) list.add(w);
                    return list;
                },
                warehouses -> {
                    warehouseCombo.setItems(FXCollections.observableArrayList(warehouses));
                    warehouseCombo.setPromptText("اختار المخزن");
                    warehouseCombo.setDisable(false);
                },
                error -> warehouseCombo.setPromptText("حصل خطأ في تحميل المخازن")
        );

        Button addBtn = new Button("إضافة عامل"); addBtn.getStyleClass().add("btn-primary");
        Button attendanceBtn = new Button("تسجيل حضور اليوم"); attendanceBtn.getStyleClass().add("btn-default");
        Button openBtn = new Button("فتح الحساب"); openBtn.getStyleClass().add("btn-default");
        Button deleteBtn = new Button("حذف"); deleteBtn.getStyleClass().add("btn-danger");
        openBtn.setDisable(true); deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            openBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null || !role.equals("admin"));
        });

        Runnable reloadWorkers = () -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            listView.setPlaceholder(new Label("جاري التحميل..."));
            AsyncHelper.run(
                    () -> WorkerDAO.getWorkersByWarehouse(w.getId()),
                    workers -> listView.setItems(FXCollections.observableArrayList(workers)),
                    error -> listView.setPlaceholder(new Label("حصل خطأ في التحميل"))
            );
        };

        warehouseCombo.setOnAction(e -> reloadWorkers.run());

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
                    if (!wageField.getText().trim().isEmpty()) wage = Double.parseDouble(wageField.getText().trim());
                } catch (NumberFormatException ex) {
                    errLbl.setText("الأجر لازم رقم");
                    return;
                }

                final String name = nameField.getText().trim();
                final String phone = phoneField.getText().trim();
                final double finalWage = wage;
                final Integer warehouseId = w.getId() == -1 ? null : w.getId();

                errLbl.setStyle("-fx-text-fill: #5f5e5a;");
                errLbl.setText("جاري الحفظ...");

                AsyncHelper.runVoid(
                        () -> WorkerDAO.addWorker(name, phone, warehouseId, finalWage, userId),
                        () -> { reloadWorkers.run(); dialog.close(); },
                        error -> {
                            errLbl.setStyle("-fx-text-fill: #b91c1c;");
                            errLbl.setText("حصل خطأ أثناء الحفظ");
                        },
                        saveBtn
                );
            });
            dialog.show();
        });

        attendanceBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;

            VBox checkboxBox = new VBox(8);
            checkboxBox.getChildren().add(new Label("جاري التحميل..."));
            Button saveBtn = new Button("تسجيل الحاضرين");
            saveBtn.getStyleClass().add("btn-primary");
            saveBtn.setDisable(true);
            Label msg = new Label("");
            ScrollPane scroll = new ScrollPane(new VBox(10, new Label("الحاضرين:"), checkboxBox, saveBtn, msg));
            scroll.setFitToWidth(true);
            VBox layout = new VBox(scroll); layout.setPadding(new Insets(10));
            Stage dialog = DialogHelper.create("تسجيل حضور", layout, 350, 400);

            AsyncHelper.run(
                    () -> WorkerDAO.getWorkersByWarehouse(w.getId()),
                    workers -> {
                        checkboxBox.getChildren().clear();
                        if (workers.isEmpty()) {
                            checkboxBox.getChildren().add(new Label("مفيش عمال"));
                            return;
                        }
                        List<CheckBox> checkBoxes = new ArrayList<>();
                        for (Worker worker : workers) {
                            CheckBox cb = new CheckBox(worker.getName() + " — " + worker.getDailyWage() + " جنيه");
                            cb.setSelected(true);
                            checkBoxes.add(cb);
                            checkboxBox.getChildren().add(cb);
                        }
                        saveBtn.setDisable(false);
                        saveBtn.setOnAction(ev -> {
                            List<Worker> selectedWorkers = new ArrayList<>();
                            for (int i = 0; i < workers.size(); i++)
                                if (checkBoxes.get(i).isSelected()) selectedWorkers.add(workers.get(i));

                            msg.setText("جاري التسجيل...");
                            AsyncHelper.runVoid(
                                    () -> {
                                        for (Worker worker : selectedWorkers)
                                            WorkerDAO.recordAttendance(worker.getId(), worker.getDailyWage(), userId);
                                    },
                                    () -> msg.setText("تم تسجيل " + selectedWorkers.size() + " عامل ✓"),
                                    error -> msg.setText("حصل خطأ أثناء التسجيل"),
                                    saveBtn
                            );
                        });
                    },
                    error -> checkboxBox.getChildren().setAll(new Label("حصل خطأ في التحميل"))
            );

            dialog.show();
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
                        deleteBtn.setDisable(true);
                        AsyncHelper.runVoid(
                                () -> WorkerDAO.deleteWorker(selected.getId()),
                                reloadWorkers,
                                error -> reloadWorkers.run()
                        );
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
