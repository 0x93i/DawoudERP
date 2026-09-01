package com.daoud.ui;

import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.List;

public class WarehouseDetailScreen {

    public static void show(Stage stage, int userId, String username, String role, Warehouse warehouse) {

        Label title = new Label("مخزن: " + warehouse.getName());

        // ── إجمالي البضاعة ──
        Label stockTitle = new Label("── إجمالي البضاعة الحالية ──");
        Label stockLabel = new Label();
        refreshStock(stockLabel, warehouse.getId());

        // ── تسجيل دخول بضاعة ──
        Label entryTitle = new Label("── تسجيل دخول بضاعة ──");

        List<Supplier> suppliers = WarehouseDAO.getSuppliersByWarehouse(warehouse.getId());
        ComboBox<Supplier> supplierCombo = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplierCombo.setPromptText("اختار المورد");

        TextField totalWeightField = new TextField();
        totalWeightField.setPromptText("الكمية الكلية (كيلو)");

        Button saveEntryBtn = new Button("تسجيل الدخول");
        Label entryMsg = new Label("");

        saveEntryBtn.setOnAction(e -> {
            if (supplierCombo.getValue() == null) { entryMsg.setText("اختار المورد"); return; }
            try {
                double total = Double.parseDouble(totalWeightField.getText().trim());
                String sql = """
                    INSERT INTO warehouse_stock_entries 
                    (warehouse_id, supplier_id, entry_date, total_weight, recorded_by)
                    VALUES (?, ?, CURRENT_DATE, ?, ?)
                """;
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, warehouse.getId());
                    stmt.setInt(2, supplierCombo.getValue().getId());
                    stmt.setDouble(3, total);
                    stmt.setInt(4, userId);
                    stmt.executeUpdate();
                }
                totalWeightField.clear();
                entryMsg.setText("تم التسجيل");
                refreshStock(stockLabel, warehouse.getId());
            } catch (NumberFormatException ex) {
                entryMsg.setText("ادخل رقم صحيح");
            } catch (SQLException ex) {
                entryMsg.setText("خطأ: " + ex.getMessage());
            }
        });

        // ── تسجيل خروج بضاعة ──
        Label exitTitle = new Label("── تسجيل خروج بضاعة ──");

        TextField exitGreenField = new TextField();
        exitGreenField.setPromptText("أخضر (كيلو)");

        TextField exitColoredField = new TextField();
        exitColoredField.setPromptText("ألوان (كيلو)");

        TextField exitWhiteField = new TextField();
        exitWhiteField.setPromptText("أبيض (كيلو)");

        TextField exitWasteField = new TextField();
        exitWasteField.setPromptText("زبالة (كيلو)");

        TextField exitDestination = new TextField();
        exitDestination.setPromptText("الوجهة (مثلاً: مصنع النيل)");

        Label exitTotalLabel = new Label("الإجمالي: —");

        // حساب الإجمالي تلقائي
        Runnable calcExitTotal = () -> {
            try {
                double g = exitGreenField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitGreenField.getText().trim());
                double c = exitColoredField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitColoredField.getText().trim());
                double w = exitWhiteField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitWhiteField.getText().trim());
                double ws = exitWasteField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitWasteField.getText().trim());
                exitTotalLabel.setText(String.format("الإجمالي: %.1f كيلو", g + c + w + ws));
            } catch (NumberFormatException ex) {
                exitTotalLabel.setText("الإجمالي: —");
            }
        };

        exitGreenField.textProperty().addListener((obs, o, n) -> calcExitTotal.run());
        exitColoredField.textProperty().addListener((obs, o, n) -> calcExitTotal.run());
        exitWhiteField.textProperty().addListener((obs, o, n) -> calcExitTotal.run());
        exitWasteField.textProperty().addListener((obs, o, n) -> calcExitTotal.run());

        Button saveExitBtn = new Button("تسجيل الخروج");
        Label exitMsg = new Label("");

        saveExitBtn.setOnAction(e -> {
            try {
                double green = exitGreenField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitGreenField.getText().trim());
                double colored = exitColoredField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitColoredField.getText().trim());
                double white = exitWhiteField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitWhiteField.getText().trim());
                double waste = exitWasteField.getText().trim().isEmpty() ? 0 : Double.parseDouble(exitWasteField.getText().trim());

                if (green + colored + white + waste == 0) { exitMsg.setText("ادخل كمية"); return; }

                String sql = """
                    INSERT INTO warehouse_stock_exits
                    (warehouse_id, exit_date, weight_green, weight_colored, weight_white, weight_waste, destination, recorded_by)
                    VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?)
                """;
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, warehouse.getId());
                    stmt.setDouble(2, green);
                    stmt.setDouble(3, colored);
                    stmt.setDouble(4, white);
                    stmt.setDouble(5, waste);
                    stmt.setString(6, exitDestination.getText().trim());
                    stmt.setInt(7, userId);
                    stmt.executeUpdate();
                }
                exitGreenField.clear();
                exitColoredField.clear();
                exitWhiteField.clear();
                exitWasteField.clear();
                exitDestination.clear();
                exitTotalLabel.setText("الإجمالي: —");
                exitMsg.setText("تم التسجيل");
                refreshStock(stockLabel, warehouse.getId());
            } catch (NumberFormatException ex) {
                exitMsg.setText("تأكد من الأرقام");
            } catch (SQLException ex) {
                exitMsg.setText("خطأ: " + ex.getMessage());
            }
        });

        // ── قائمة الموردين ──
        Label suppliersTitle = new Label("── الموردين ──");
        ListView<Supplier> suppliersList = new ListView<>(FXCollections.observableArrayList(suppliers));

        Button openSupplierBtn = new Button("فتح حساب المورد");
        openSupplierBtn.setDisable(true);

        Button addSupplierBtn = new Button("إضافة مورد جديد للمخزن");
        addSupplierBtn.setOnAction(e -> {
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

            saveBtn.setOnAction(ev -> {
                String name = nameField.getText().trim();
                if (name.isEmpty()) { errorLbl.setText("اسم المورد مطلوب"); return; }
                double floor = 0;
                try {
                    if (!floorField.getText().trim().isEmpty())
                        floor = Double.parseDouble(floorField.getText().trim());
                } catch (NumberFormatException ex) {
                    errorLbl.setText("الأرضية لازم تكون رقم"); return;
                }
                com.daoud.dao.SupplierDAO.addSupplier(nameField.getText().trim(), "", "", floor, userId);                List<Supplier> all = com.daoud.dao.SupplierDAO.getAllSuppliers();
                for (Supplier s : all) {
                    if (s.getName().equals(name)) {
                        WarehouseDAO.assignSupplierToWarehouse(warehouse.getId(), s.getId());
                        break;
                    }
                }
                List<Supplier> updated = WarehouseDAO.getSuppliersByWarehouse(warehouse.getId());
                suppliersList.setItems(FXCollections.observableArrayList(updated));
                supplierCombo.setItems(FXCollections.observableArrayList(updated));
                dialog.close();
            });

            VBox layout2 = new VBox(10,
                    new Label("اسم المورد:"), nameField,
                    new Label("القطاع:"), sectorField,
                    new Label("الأرضية:"), floorField,
                    saveBtn, errorLbl);
            layout2.setPadding(new Insets(20));
            dialog.setScene(new Scene(layout2, 350, 300));
            dialog.show();
        });

        suppliersList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                openSupplierBtn.setDisable(selected == null));

        openSupplierBtn.setOnAction(e -> {
            Supplier selected = suppliersList.getSelectionModel().getSelectedItem();
            if (selected != null)
                SupplierDetailScreen.show(stage, userId, username, role, selected);
        });

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> WarehousesScreen.show(stage, userId, username, role));

        VBox layout = new VBox(10,
                title,
                new Separator(),
                stockTitle, stockLabel,
                new Separator(),
                entryTitle,
                new Label("المورد:"), supplierCombo,
                new Label("الكمية الكلية:"), totalWeightField,
                saveEntryBtn, entryMsg,
                new Separator(),
                exitTitle,
                new Label("أخضر:"), exitGreenField,
                new Label("ألوان:"), exitColoredField,
                new Label("أبيض:"), exitWhiteField,
                new Label("زبالة:"), exitWasteField,
                new Label("الوجهة:"), exitDestination,
                exitTotalLabel,
                saveExitBtn, exitMsg,
                new Separator(),
                suppliersTitle, suppliersList,
                openSupplierBtn, addSupplierBtn,
                backBtn
        );
        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll, 800, 600));
    }

    private static void refreshStock(Label lbl, int warehouseId) {
        try (Connection conn = DatabaseManager_online.getConnection()) {
            // إجمالي الداخل
            String entrySql = "SELECT COALESCE(SUM(total_weight), 0) as total FROM warehouse_stock_entries WHERE warehouse_id = ?";
            PreparedStatement es = conn.prepareStatement(entrySql);
            es.setInt(1, warehouseId);
            ResultSet ers = es.executeQuery();
            double totalIn = ers.next() ? ers.getDouble("total") : 0;

            // إجمالي الخارج بالأنواع
            String exitSql = """
                SELECT 
                    COALESCE(SUM(weight_green), 0) as green,
                    COALESCE(SUM(weight_colored), 0) as colored,
                    COALESCE(SUM(weight_white), 0) as white,
                    COALESCE(SUM(weight_waste), 0) as waste
                FROM warehouse_stock_exits WHERE warehouse_id = ?
            """;
            PreparedStatement xs = conn.prepareStatement(exitSql);
            xs.setInt(1, warehouseId);
            ResultSet xrs = xs.executeQuery();
            double green = 0, colored = 0, white = 0, waste = 0;
            if (xrs.next()) {
                green = xrs.getDouble("green");
                colored = xrs.getDouble("colored");
                white = xrs.getDouble("white");
                waste = xrs.getDouble("waste");
            }
            double totalOut = green + colored + white + waste;
            double remaining = totalIn - totalOut;

            lbl.setText(String.format(
                    "داخل: %.1f | خارج: %.1f | متبقي: %.1f كيلو%nأخضر: %.1f | ألوان: %.1f | أبيض: %.1f | زبالة: %.1f",
                    totalIn, totalOut, remaining, green, colored, white, waste));

        } catch (SQLException e) {
            lbl.setText("خطأ في تحميل البيانات");
        }
    }
}