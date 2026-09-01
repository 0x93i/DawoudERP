package com.daoud.ui;

import com.daoud.dao.WorkerDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Warehouse;
import com.daoud.model.Worker;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class WorkersScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label title = new Label("العمال");

        List<Warehouse> warehouses;
        if (role.equals("admin")) {
            warehouses = new ArrayList<>(WarehouseDAO.getAllWarehouses());
            warehouses.add(0, new Warehouse(-1, "عمال حرة (غير مرتبطين)", -1, ""));
        } else {
            warehouses = new ArrayList<>();
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            if (w != null) warehouses.add(w);
        }

        ComboBox<Warehouse> warehouseCombo = new ComboBox<>(
                FXCollections.observableArrayList(warehouses));
        warehouseCombo.setPromptText("اختار المخزن");

        ListView<Worker> listView = new ListView<>();

        Button openBtn = new Button("فتح حساب العامل");
        Button addBtn = new Button("إضافة عامل");
        Button attendanceBtn = new Button("تسجيل حضور اليوم");
        Button deleteBtn = new Button("حذف العامل");
        openBtn.setDisable(true);
        deleteBtn.setDisable(true);

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
            if (w == null) { return; }
            showAddDialog(w.getId(), userId, listView);
        });

        attendanceBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            showAttendanceDialog(w.getId(), userId, listView);
        });

        deleteBtn.setOnAction(e -> {
            Worker selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "هتحذف العامل: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        WorkerDAO.deleteWorker(selected.getId());
                        Warehouse w = warehouseCombo.getValue();
                        if (w != null)
                            listView.setItems(FXCollections.observableArrayList(
                                    WorkerDAO.getWorkersByWarehouse(w.getId())));
                    }
                });
            }
        });

        openBtn.setOnAction(e -> {
            Worker selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null)
                WorkerDetailScreen.show(stage, userId, username, role, selected);
        });

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(ev -> MainScreen.show(stage, userId, username, role));

        HBox buttons = new HBox(10, addBtn, attendanceBtn, openBtn, deleteBtn, backBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12,
                title,
                new Label("المخزن:"), warehouseCombo,
                listView, buttons);
        layout.setPadding(new Insets(20));
        stage.setScene(new Scene(layout, 800, 600));
    }

    private static void showAddDialog(int warehouseId, int userId, ListView<Worker> listView) {
        Stage dialog = new Stage();
        dialog.setTitle("إضافة عامل جديد");

        TextField nameField = new TextField();
        nameField.setPromptText("اسم العامل");

        TextField wageField = new TextField();
        wageField.setPromptText("الأجر اليومي");

        Button saveBtn = new Button("حفظ");
        Label errorLbl = new Label("");

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("الاسم مطلوب"); return; }
            double wage = 0;
            try {
                if (!wageField.getText().trim().isEmpty())
                    wage = Double.parseDouble(wageField.getText().trim());
            } catch (NumberFormatException ex) {
                errorLbl.setText("الأجر لازم يكون رقم");
                return;
            }
            int wId = warehouseId == -1 ? 0 : warehouseId;
            WorkerDAO.addWorker(name,"", wId, wage, userId);
            listView.setItems(FXCollections.observableArrayList(
                    WorkerDAO.getWorkersByWarehouse(warehouseId)));
            dialog.close();
        });

        VBox layout = new VBox(10,
                new Label("الاسم:"), nameField,
                new Label("الأجر اليومي:"), wageField,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        dialog.setScene(new Scene(layout, 320, 240));
        dialog.show();
    }

    private static void showAttendanceDialog(int warehouseId, int userId, ListView<Worker> listView) {
        Stage dialog = new Stage();
        dialog.setTitle("تسجيل حضور اليوم");

        List<Worker> workers = WorkerDAO.getWorkersByWarehouse(warehouseId);
        if (workers.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "مفيش عمال في المخزن ده").show();
            return;
        }

        VBox checkboxBox = new VBox(8);
        List<CheckBox> checkBoxes = new ArrayList<>();

        for (Worker w : workers) {
            CheckBox cb = new CheckBox(w.getName() + " — أجر: " + w.getDailyWage() + " جنيه");
            cb.setSelected(true);
            checkBoxes.add(cb);
            checkboxBox.getChildren().add(cb);
        }

        Button saveBtn = new Button("تسجيل الحاضرين");
        Label msg = new Label("");

        saveBtn.setOnAction(e -> {
            int count = 0;
            for (int i = 0; i < workers.size(); i++) {
                if (checkBoxes.get(i).isSelected()) {
                    WorkerDAO.recordAttendance(workers.get(i).getId(),
                            workers.get(i).getDailyWage(), userId);
                    count++;
                }
            }
            msg.setText("تم تسجيل " + count + " عامل");
        });

        VBox layout = new VBox(10,
                new Label("اختار الحاضرين اليوم:"),
                checkboxBox,
                saveBtn, msg);
        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        dialog.setScene(new Scene(scroll, 350, 400));
        dialog.show();
    }
}