package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.List;

public class WarehouseDetailContent {

    public static Node build(int userId, String username, String role, Warehouse warehouse) {

        // ── إجمالي البضاعة ──
        VBox stockCard = new VBox(10);
        stockCard.getStyleClass().add("card");
        stockCard.setMaxWidth(Double.MAX_VALUE);
        refreshStockCard(stockCard, warehouse.getId());

        // ── قائمة الموردين ──
        List<Supplier> suppliers = WarehouseDAO.getSuppliersByWarehouse(warehouse.getId());
        javafx.collections.ObservableList<Supplier> supplierObsList = FXCollections.observableArrayList(suppliers);
        ListView<Supplier> suppliersList = new ListView<>(supplierObsList);
        suppliersList.setPrefHeight(180);
        suppliersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        ComboBox<Supplier> supplierCombo = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplierCombo.setPromptText("اختار المورد");
        supplierCombo.setMaxWidth(Double.MAX_VALUE);
        supplierCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        supplierCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        Button addNewSupplierBtn = new Button("+ مورد جديد");
        addNewSupplierBtn.getStyleClass().add("btn-default");
        addNewSupplierBtn.setStyle("-fx-font-size: 11px;");
        addNewSupplierBtn.setOnAction(e -> showAddSupplierDialog(userId, role, warehouse, supplierCombo, suppliersList));

        HBox supplierRow = new HBox(8, supplierCombo, addNewSupplierBtn);
        supplierRow.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(supplierCombo, Priority.ALWAYS);

        // ── حقول دخول البضاعة ──
        TextField totalWeightField = new TextField(); totalWeightField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionField = new TextField(); deductionField.setPromptText("كمية الخصم (كيلو)");
        TextField priceField = new TextField(); priceField.setPromptText("سعر الكيلو");

        Label pctLabel = new Label("نسبة الخصم: —");
        Label netLabel = new Label("الوزن الصافي: —");
        Label totalLabel = new Label("الإجمالي: —");

        pctLabel.setStyle("-fx-text-fill: #888780; -fx-font-size: 12px;");
        netLabel.setStyle("-fx-text-fill: #3B6D11; -fx-font-weight: bold; -fx-font-size: 12px;");
        totalLabel.setStyle("-fx-text-fill: #3B6D11; -fx-font-weight: bold; -fx-font-size: 12px;");

        Runnable calcEntry = () -> {
            try {
                double gross = Double.parseDouble(totalWeightField.getText().trim());
                double ded = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double net = gross - ded;
                double pct = gross > 0 ? (ded / gross) * 100.0 : 0;
                pctLabel.setText(String.format("نسبة الخصم: %.2f%%", pct));
                netLabel.setText(String.format("الوزن الصافي: %.1f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", net * price));
            } catch (NumberFormatException ex) {
                pctLabel.setText("نسبة الخصم: —");
                netLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
            }
        };

        totalWeightField.textProperty().addListener((o,old,n) -> calcEntry.run());
        deductionField.textProperty().addListener((o,old,n) -> calcEntry.run());
        priceField.textProperty().addListener((o,old,n) -> calcEntry.run());

        Button saveEntryBtn = new Button("حفظ الدخول"); saveEntryBtn.getStyleClass().add("btn-primary");
        Label entryMsg = new Label("");

        saveEntryBtn.setOnAction(e -> {
            if (supplierCombo.getValue() == null) { entryMsg.setText("اختار المورد"); return; }
            try {
                double gross = Double.parseDouble(totalWeightField.getText().trim());
                double ded = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());

                String sql = "INSERT INTO warehouse_stock_entries " +
                        "(warehouse_id, supplier_id, entry_date, total_weight, recorded_by) " +
                        "VALUES (?, ?, CURRENT_DATE, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, warehouse.getId());
                    stmt.setInt(2, supplierCombo.getValue().getId());
                    stmt.setDouble(3, gross - ded);
                    stmt.setInt(4, userId);
                    stmt.executeUpdate();
                }

                String supplierSql = "INSERT INTO supplier_transactions " +
                        "(supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(supplierSql)) {
                    stmt.setInt(1, supplierCombo.getValue().getId());
                    stmt.setDouble(2, gross);
                    stmt.setDouble(3, ded);
                    stmt.setDouble(4, price);
                    stmt.setInt(5, userId);
                    stmt.executeUpdate();
                }

                totalWeightField.clear(); deductionField.clear(); priceField.clear();
                pctLabel.setText("نسبة الخصم: —");
                netLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
                entryMsg.setText("تم ✓");
                refreshStockCard(stockCard, warehouse.getId());
            } catch (NumberFormatException ex) { entryMsg.setText("ادخل أرقام صحيحة");
            } catch (SQLException ex) { entryMsg.setText("خطأ: " + ex.getMessage()); }
        });

        // ── خروج بضاعة ──
        TextField exitGreenField = new TextField(); exitGreenField.setPromptText("0");
        TextField exitColoredField = new TextField(); exitColoredField.setPromptText("0");
        TextField exitWhiteField = new TextField(); exitWhiteField.setPromptText("0");
        TextField exitWasteField = new TextField(); exitWasteField.setPromptText("0");
        TextField exitPriceField = new TextField(); exitPriceField.setPromptText("سعر الكيلو");

        List<com.daoud.model.Factory> factories = com.daoud.dao.FactoryDAO.getAllFactories();
        ComboBox<String> destinationCombo = new ComboBox<>();
        destinationCombo.getItems().add("عم داود");
        for (com.daoud.model.Factory f : factories) {
            destinationCombo.getItems().add("مصنع: " + f.getName());
        }
        destinationCombo.setPromptText("اختار الوجهة");
        destinationCombo.setMaxWidth(Double.MAX_VALUE);

        Label exitTotalLabel = new Label("الإجمالي: —");
        Label exitValueLabel = new Label("القيمة: —");
        exitTotalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3B6D11;");
        exitValueLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3B6D11;");

        Runnable calcExit = () -> {
            try {
                double g = parse(exitGreenField), c = parse(exitColoredField);
                double w = parse(exitWhiteField), ws = parse(exitWasteField);
                double total = g + c + w + ws;
                double price = parse(exitPriceField);
                exitTotalLabel.setText(String.format("الإجمالي: %.1f كيلو", total));
                exitValueLabel.setText(price > 0 ?
                        String.format("القيمة: %.2f جنيه", total * price) : "القيمة: —");
            } catch (NumberFormatException ex) { exitTotalLabel.setText("الإجمالي: —"); }
        };

        exitGreenField.textProperty().addListener((o,old,n) -> calcExit.run());
        exitColoredField.textProperty().addListener((o,old,n) -> calcExit.run());
        exitWhiteField.textProperty().addListener((o,old,n) -> calcExit.run());
        exitWasteField.textProperty().addListener((o,old,n) -> calcExit.run());
        exitPriceField.textProperty().addListener((o,old,n) -> calcExit.run());

        Button saveExitBtn = new Button("تسجيل الخروج"); saveExitBtn.getStyleClass().add("btn-primary");
        Label exitMsg = new Label("");

        saveExitBtn.setOnAction(e -> {
            try {
                double green = parse(exitGreenField), colored = parse(exitColoredField);
                double white = parse(exitWhiteField), waste = parse(exitWasteField);
                double exitPrice = parse(exitPriceField);
                if (green + colored + white + waste == 0) { exitMsg.setText("ادخل كمية"); return; }
                if (destinationCombo.getValue() == null) { exitMsg.setText("اختار الوجهة"); return; }

                String destination = destinationCombo.getValue();
                String sql = "INSERT INTO warehouse_stock_exits " +
                        "(warehouse_id, exit_date, weight_green, weight_colored, weight_white, weight_waste, destination, exit_price, exit_value, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?)";
                double totalKg = green + colored + white + waste;
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, warehouse.getId());
                    stmt.setDouble(2, green); stmt.setDouble(3, colored);
                    stmt.setDouble(4, white); stmt.setDouble(5, waste);
                    stmt.setString(6, destination);
                    stmt.setDouble(7, exitPrice);
                    stmt.setDouble(8, totalKg * exitPrice);
                    stmt.setInt(9, userId);
                    stmt.executeUpdate();
                }

                // لو مصنع — سجل شحنة تلقائي
                if (destination.startsWith("مصنع: ")) {
                    String factoryName = destination.replace("مصنع: ", "");
                    for (com.daoud.model.Factory f : factories) {
                        if (f.getName().equals(factoryName)) {
                            String shipSql = "INSERT INTO factory_shipments " +
                                    "(factory_id, shipment_date, gross_weight, deduction_pct, deduction_kg, net_weight, price_per_kg, total_amount, recorded_by) " +
                                    "VALUES (?, CURRENT_DATE, ?, 0, 0, ?, ?, ?, ?)";
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(shipSql)) {
                                stmt.setInt(1, f.getId());
                                stmt.setDouble(2, totalKg);
                                stmt.setDouble(3, totalKg);
                                stmt.setDouble(4, exitPrice);
                                stmt.setDouble(5, totalKg * exitPrice);
                                stmt.setInt(6, userId);
                                stmt.executeUpdate();
                            }
                            break;
                        }
                    }
                }

                exitGreenField.clear(); exitColoredField.clear();
                exitWhiteField.clear(); exitWasteField.clear(); exitPriceField.clear();
                exitTotalLabel.setText("الإجمالي: —");
                exitValueLabel.setText("القيمة: —");
                exitMsg.setText("تم ✓");
                refreshStockCard(stockCard, warehouse.getId());
            } catch (NumberFormatException ex) { exitMsg.setText("تأكد من الأرقام");
            } catch (SQLException ex) { exitMsg.setText("خطأ: " + ex.getMessage()); }
        });

        // ── موردي المخزن ──
        Button openSupplierBtn = new Button("فتح حساب المورد");
        openSupplierBtn.getStyleClass().add("btn-default");
        openSupplierBtn.setDisable(true);
        suppliersList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                openSupplierBtn.setDisable(selected == null));
        openSupplierBtn.setOnAction(e -> {
            Supplier selected = suppliersList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                MainLayout.loadContent(SupplierDetailContent.build(userId, username, role, selected));
                MainLayout.setTitle("حساب: " + selected.getName());
            }
        });

        Button addSupBtn = new Button("+ إضافة مورد للمخزن");
        addSupBtn.getStyleClass().add("btn-default");
        addSupBtn.setVisible(role.equals("admin"));
        addSupBtn.setManaged(role.equals("admin"));
        addSupBtn.setOnAction(e -> showAddSupplierDialog(userId, role, warehouse, supplierCombo, suppliersList));

        Button backBtn = new Button("← رجوع للمخازن");
        backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(WarehousesContent.build(null, userId, username, role));
            MainLayout.setTitle("المخازن");
        });

        // ── Cards ──
        VBox entryCard = new VBox(10); entryCard.getStyleClass().add("card");
        Label entryTitle = new Label("تسجيل دخول بضاعة"); entryTitle.getStyleClass().add("card-title");
        entryCard.getChildren().addAll(
                entryTitle,
                new Label("المورد:"), supplierRow,
                new HBox(12,
                        new VBox(4, new Label("الوزن الإجمالي:"), totalWeightField),
                        new VBox(4, new Label("كمية الخصم (كيلو):"), deductionField),
                        new VBox(4, new Label("سعر الكيلو:"), priceField)),
                new HBox(16, pctLabel, netLabel, totalLabel),
                saveEntryBtn, entryMsg);

        HBox.setHgrow(totalWeightField, Priority.ALWAYS);
        HBox.setHgrow(deductionField, Priority.ALWAYS);
        HBox.setHgrow(priceField, Priority.ALWAYS);
        totalWeightField.setMaxWidth(Double.MAX_VALUE);
        deductionField.setMaxWidth(Double.MAX_VALUE);
        priceField.setMaxWidth(Double.MAX_VALUE);

        VBox exitCard = new VBox(10); exitCard.getStyleClass().add("card");
        Label exitTitle = new Label("تسجيل خروج بضاعة"); exitTitle.getStyleClass().add("card-title");
        exitCard.getChildren().addAll(
                exitTitle,
                new HBox(12,
                        new VBox(4, new Label("أخضر (كيلو):"), exitGreenField),
                        new VBox(4, new Label("ألوان (كيلو):"), exitColoredField),
                        new VBox(4, new Label("أبيض (كيلو):"), exitWhiteField),
                        new VBox(4, new Label("زبالة (كيلو):"), exitWasteField)),
                new VBox(4, new Label("سعر الكيلو:"), exitPriceField),
                new VBox(4, new Label("الوجهة:"), destinationCombo),
                new HBox(16, exitTotalLabel, exitValueLabel),
                saveExitBtn, exitMsg);

        exitGreenField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(exitGreenField, Priority.ALWAYS);
        exitColoredField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(exitColoredField, Priority.ALWAYS);
        exitWhiteField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(exitWhiteField, Priority.ALWAYS);
        exitWasteField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(exitWasteField, Priority.ALWAYS);
        exitPriceField.setMaxWidth(Double.MAX_VALUE);

        VBox suppliersCard = new VBox(10); suppliersCard.getStyleClass().add("card");
        Label supTitle = new Label("موردي المخزن"); supTitle.getStyleClass().add("card-title");
        suppliersCard.getChildren().addAll(
                supTitle, suppliersList,
                new HBox(8, openSupplierBtn, addSupBtn));

        HBox mainRow = new HBox(12, entryCard, exitCard);
        HBox.setHgrow(entryCard, Priority.ALWAYS);
        HBox.setHgrow(exitCard, Priority.ALWAYS);
        entryCard.setMaxWidth(Double.MAX_VALUE);
        exitCard.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(12, backBtn, stockCard, mainRow, suppliersCard);
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);

        return content;
    }

    private static void showAddSupplierDialog(int userId, String role, Warehouse warehouse,
                                              ComboBox<Supplier> combo, ListView<Supplier> list) {
        TextField nameField = new TextField(); nameField.setPromptText("اسم المورد");
        TextField phoneField = new TextField(); phoneField.setPromptText("رقم التليفون");
        TextField sectorField = new TextField(); sectorField.setPromptText("القطاع");

        TextField floorField = new TextField(); floorField.setPromptText("الأرضية");
        Label floorLabel = new Label("الأرضية:");
        floorField.setVisible(role.equals("admin")); floorField.setManaged(role.equals("admin"));
        floorLabel.setVisible(role.equals("admin")); floorLabel.setManaged(role.equals("admin"));

        Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
        Label errLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("الاسم:"), nameField,
                new Label("التليفون:"), phoneField,
                new Label("القطاع:"), sectorField,
                floorLabel, floorField,
                saveBtn, errLbl);
        layout.setPadding(new Insets(20));
        Stage dialog = DialogHelper.create("إضافة مورد جديد", layout, 350, 340);

        saveBtn.setOnAction(ev -> {
            if (nameField.getText().trim().isEmpty()) { errLbl.setText("الاسم مطلوب"); return; }
            double floor = 0;
            try {
                if (!floorField.getText().trim().isEmpty())
                    floor = Double.parseDouble(floorField.getText().trim());
            } catch (NumberFormatException ex) { errLbl.setText("الأرضية لازم رقم"); return; }

            SupplierDAO.addSupplier(nameField.getText().trim(), phoneField.getText().trim(),
                    sectorField.getText().trim(), floor, userId);

            List<Supplier> all = SupplierDAO.getAllSuppliers();
            for (Supplier s : all) {
                if (s.getName().equals(nameField.getText().trim())) {
                    WarehouseDAO.assignSupplierToWarehouse(warehouse.getId(), s.getId());
                    break;
                }
            }

            List<Supplier> updated = WarehouseDAO.getSuppliersByWarehouse(warehouse.getId());
            combo.getItems().setAll(updated);
            if (list != null) list.getItems().setAll(updated);
            dialog.close();
        });

        dialog.show();
    }

    private static void refreshStockCard(VBox card, int warehouseId) {
        card.getChildren().clear();
        Label title = new Label("إجمالي البضاعة في المخزن"); title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        try (Connection conn = DatabaseManager_online.getConnection()) {
            PreparedStatement es = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_weight), 0) as total FROM warehouse_stock_entries WHERE warehouse_id = ?");
            es.setInt(1, warehouseId); ResultSet ers = es.executeQuery();
            double totalIn = ers.next() ? ers.getDouble("total") : 0;

            PreparedStatement xs = conn.prepareStatement(
                    "SELECT COALESCE(SUM(weight_green),0) as g, COALESCE(SUM(weight_colored),0) as c, " +
                            "COALESCE(SUM(weight_white),0) as w, COALESCE(SUM(weight_waste),0) as ws " +
                            "FROM warehouse_stock_exits WHERE warehouse_id = ?");
            xs.setInt(1, warehouseId); ResultSet xrs = xs.executeQuery();
            double green=0, colored=0, white=0, waste=0;
            if (xrs.next()) { green=xrs.getDouble("g"); colored=xrs.getDouble("c"); white=xrs.getDouble("w"); waste=xrs.getDouble("ws"); }
            double totalOut = green + colored + white + waste;
            double remaining = totalIn - totalOut;

            HBox statsRow = new HBox(12);
            statsRow.getChildren().addAll(
                    statBox("إجمالي الداخل", String.format("%.1f", totalIn / 1000), "طن"),
                    statBox("إجمالي الخارج", String.format("%.1f", totalOut / 1000), "طن"),
                    statBox("المتبقي", String.format("%.1f", remaining / 1000), "طن")
            );
            statsRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

            HBox typesRow = new HBox(12);
            typesRow.getChildren().addAll(
                    typeBox("أخضر", green, "#2d7d2d"),
                    typeBox("ألوان", colored, "#185FA5"),
                    typeBox("أبيض", white, "#5f5e5a"),
                    typeBox("زبالة", waste, "#854F0B")
            );
            typesRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

            card.getChildren().addAll(statsRow, typesRow);

        } catch (SQLException e) {
            card.getChildren().add(new Label("خطأ في تحميل البيانات"));
        }
    }

    private static VBox statBox(String title, String value, String unit) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
        Label unitLbl = new Label(unit);
        unitLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
        VBox box = new VBox(2, titleLbl, valueLbl, unitLbl);
        box.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 12; -fx-background-radius: 8;");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static VBox typeBox(String name, double value, String color) {
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");
        Label valueLbl = new Label(String.format("%.1f كيلو", value));
        valueLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        VBox box = new VBox(2, nameLbl, valueLbl);
        box.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 10; -fx-background-radius: 8;");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static double parse(TextField f) {
        try { return f.getText().trim().isEmpty() ? 0 : Double.parseDouble(f.getText().trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}