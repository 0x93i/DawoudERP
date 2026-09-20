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
import java.util.ArrayList;
import java.util.List;

public class WarehouseDetailContent {

    private record StockData(double totalIn, double green, double colored, double white, double waste) {}

    public static Node build(int userId, String username, String role, Warehouse warehouse) {

        // ── إجمالي البضاعة ──
        VBox stockCard = new VBox(10);
        stockCard.getStyleClass().add("card");
        stockCard.setMaxWidth(Double.MAX_VALUE);
        Label stockTitle = new Label("إجمالي البضاعة في المخزن"); stockTitle.getStyleClass().add("card-title");
        Label stockLoading = new Label("جاري التحميل...");
        stockLoading.setStyle("-fx-text-fill: #888780; -fx-font-size: 12px;");
        stockCard.getChildren().addAll(stockTitle, stockLoading);
        refreshStockCard(stockCard, warehouse.getId());

        // ── قائمة الموردين ──
        ListView<Supplier> suppliersList = new ListView<>();
        suppliersList.setPrefHeight(300);
        suppliersList.setMinHeight(200);
        suppliersList.setFixedCellSize(35);
        suppliersList.setPlaceholder(new Label("جاري تحميل الموردين..."));
        suppliersList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                    setStyle("-fx-font-size: 13px; -fx-text-fill: #1a1a18; -fx-padding: 6 12;");
                }
            }
        });

        ComboBox<Supplier> supplierCombo = new ComboBox<>();
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

        loadSuppliers(supplierCombo, suppliersList, warehouse.getId());

        Button addNewSupplierBtn = new Button("+ مورد جديد");
        addNewSupplierBtn.getStyleClass().add("btn-default");
        addNewSupplierBtn.setStyle("-fx-font-size: 11px;");
        addNewSupplierBtn.setOnAction(e -> showAddSupplierDialog(userId, role, warehouse, supplierCombo, suppliersList));

        HBox supplierRow = new HBox(8, supplierCombo, addNewSupplierBtn);
        supplierRow.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(supplierCombo, Priority.ALWAYS);

        // ── حقول دخول البضاعة ──
        TextField totalWeightField = new TextField();
        totalWeightField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionField = new TextField(); deductionField.setPromptText("نسبة الخصم %");
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
                double pct = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double dedKg = gross * (pct / 100.0);
                double net = gross - dedKg;
                pctLabel.setText(String.format("كمية الخصم: %.2f كيلو", dedKg));
                netLabel.setText(String.format("الوزن الصافي: %.1f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", net * price));
            } catch (NumberFormatException ex) {
                pctLabel.setText("كمية الخصم: —");
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
                double pct = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double ded = gross * (pct / 100.0);
                int supplierId = supplierCombo.getValue().getId();

                entryMsg.setText("جاري الحفظ...");

                AsyncHelper.runVoid(
                        () -> {
                            String sql = "INSERT INTO warehouse_stock_entries " +
                                    "(warehouse_id, supplier_id, entry_date, total_weight, recorded_by) " +
                                    "VALUES (?, ?, CURRENT_DATE, ?, ?)";
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setInt(1, warehouse.getId());
                                stmt.setInt(2, supplierId);
                                stmt.setDouble(3, gross - ded);
                                stmt.setInt(4, userId);
                                stmt.executeUpdate();
                            }

                            String supplierSql = "INSERT INTO supplier_transactions " +
                                    "(supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) " +
                                    "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(supplierSql)) {
                                stmt.setInt(1, supplierId);
                                stmt.setDouble(2, gross); stmt.setDouble(3, ded);
                                stmt.setDouble(4, price); stmt.setInt(5, userId);
                                stmt.executeUpdate();
                            }
                        },
                        () -> {
                            totalWeightField.clear(); deductionField.clear(); priceField.clear();
                            pctLabel.setText("نسبة الخصم: —");
                            netLabel.setText("الوزن الصافي: —");
                            totalLabel.setText("الإجمالي: —");
                            entryMsg.setText("تم ✓");
                            refreshStockCard(stockCard, warehouse.getId());
                        },
                        error -> entryMsg.setText("خطأ: " + (error != null ? error.getMessage() : "")),
                        saveEntryBtn
                );
            } catch (NumberFormatException ex) { entryMsg.setText("ادخل أرقام صحيحة"); }
        });

        // ── خروج بضاعة ──
        TextField exitGreenField = new TextField(); exitGreenField.setPromptText("0");
        TextField exitColoredField = new TextField(); exitColoredField.setPromptText("0");
        TextField exitWhiteField = new TextField(); exitWhiteField.setPromptText("0");
        TextField exitWasteField = new TextField(); exitWasteField.setPromptText("0");
        TextField exitPriceField = new TextField(); exitPriceField.setPromptText("سعر الشراء / كيلو");

        // بتتحمل في الخلفية وتتضاف لنفس القايمة دي لما تجهز (القايمة نفسها بتتلمس من داخل الأكشن بعدين)
        List<com.daoud.model.Factory> factories = new ArrayList<>();
        ComboBox<String> destinationCombo = new ComboBox<>();
        destinationCombo.getItems().add("عم داود");
        destinationCombo.setPromptText("اختار الوجهة");
        destinationCombo.setMaxWidth(Double.MAX_VALUE);

        AsyncHelper.run(
                com.daoud.dao.FactoryDAO::getAllFactories,
                loaded -> {
                    factories.addAll(loaded);
                    for (com.daoud.model.Factory f : loaded) destinationCombo.getItems().add("مصنع: " + f.getName());
                }
        );

        Label exitTotalLabel = new Label("الإجمالي: —");
        Label exitValueLabel = new Label("قيمة الشراء: —");
        exitTotalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3B6D11;");
        exitValueLabel.setStyle("-fx-text-fill: #888780; -fx-font-size: 12px;");

        Runnable calcExit = () -> {
            try {
                double g = parse(exitGreenField), c = parse(exitColoredField);
                double w = parse(exitWhiteField), ws = parse(exitWasteField);
                double total = g + c + w + ws;
                double price = parse(exitPriceField);
                exitTotalLabel.setText(String.format("الإجمالي: %.1f كيلو", total));
                exitValueLabel.setText(price > 0 ?
                        String.format("قيمة الشراء: %.2f جنيه", total * price) : "قيمة الشراء: —");
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
                double totalKg = green + colored + white + waste;
                if (totalKg == 0) { exitMsg.setText("ادخل كمية"); return; }
                if (destinationCombo.getValue() == null) { exitMsg.setText("اختار الوجهة"); return; }

                String destination = destinationCombo.getValue();

                // لو مصنع — افتح dialog لبيانات الشحنة
                if (destination.startsWith("مصنع: ")) {
                    String factoryName = destination.replace("مصنع: ", "");
                    com.daoud.model.Factory targetFactory = null;
                    for (com.daoud.model.Factory f : factories) {
                        if (f.getName().equals(factoryName)) { targetFactory = f; break; }
                    }
                    if (targetFactory == null) { exitMsg.setText("مصنع غير موجود"); return; }

                    final com.daoud.model.Factory finalFactory = targetFactory;
                    final double purchaseTotal = totalKg * exitPrice;

                    // ── Dialog بيانات الشحنة ──
                    TextField factoryGrossField = new TextField(String.valueOf(totalKg));
                    factoryGrossField.setPromptText("الوزن الإجمالي عند المصنع");
                    TextField factoryDeductionField = new TextField("0");
                    factoryDeductionField.setPromptText("نسبة الخصم %");
                    TextField sellPriceField = new TextField();
                    sellPriceField.setPromptText("سعر البيع / كيلو");

                    Label factoryNetLabel = new Label("صافي وزن المصنع: —");
                    Label sellTotalLabel = new Label("إجمالي البيع: —");
                    Label grossProfitLabel = new Label("مجمل الربح: —");
                    Label netProfitLabel = new Label("صافي الربح: —");

                    factoryNetLabel.setStyle("-fx-text-fill: #888780;");
                    sellTotalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3B6D11;");

                    TextField costLoading = new TextField("0"); costLoading.setPromptText("تحميل");
                    TextField costWorkers = new TextField("0"); costWorkers.setPromptText("عمال");
                    TextField costFuel = new TextField("0"); costFuel.setPromptText("بنزين");
                    TextField costTransport = new TextField("0"); costTransport.setPromptText("نقل");
                    TextField costOther = new TextField("0"); costOther.setPromptText("أخرى");

                    Runnable calcShipment = () -> {
                        try {
                            double fGross = Double.parseDouble(factoryGrossField.getText().trim());
                            double fPct = parseField(factoryDeductionField);
                            double sellPrice = sellPriceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(sellPriceField.getText().trim());
                            double fDedKg = fGross * (fPct / 100.0);
                            double fNet = fGross - fDedKg;
                            double sellTotal = fNet * sellPrice;
                            double grossProfit = sellTotal - purchaseTotal;

                            factoryNetLabel.setText(String.format("صافي وزن المصنع: %.2f كيلو (خصم: %.2f كيلو)", fNet, fDedKg));
                            sellTotalLabel.setText(String.format("إجمالي البيع: %.2f جنيه", sellTotal));
                            grossProfitLabel.setText(String.format("مجمل الربح: %.2f جنيه", grossProfit));
                            grossProfitLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (grossProfit >= 0 ? "#3B6D11" : "#A32D2D") + ";");
                        } catch (NumberFormatException ex) {
                            factoryNetLabel.setText("صافي وزن المصنع: —");
                        }
                    };

                    factoryGrossField.textProperty().addListener((o,old,n) -> calcShipment.run());
                    factoryDeductionField.textProperty().addListener((o,old,n) -> calcShipment.run());
                    sellPriceField.textProperty().addListener((o,old,n) -> calcShipment.run());
                    costLoading.textProperty().addListener((o,old,n) -> calcShipment.run());
                    costWorkers.textProperty().addListener((o,old,n) -> calcShipment.run());
                    costFuel.textProperty().addListener((o,old,n) -> calcShipment.run());
                    costTransport.textProperty().addListener((o,old,n) -> calcShipment.run());
                    costOther.textProperty().addListener((o,old,n) -> calcShipment.run());

                    Button saveShipBtn = new Button("حفظ الشحنة"); saveShipBtn.getStyleClass().add("btn-primary");
                    Label shipErr = new Label("");

                    saveShipBtn.setOnAction(ev -> {
                        try {
                            double fGross = Double.parseDouble(factoryGrossField.getText().trim());
                            double fPct = parseField(factoryDeductionField);
                            double fDed = fGross * (fPct / 100.0);
                            double sellPrice = Double.parseDouble(sellPriceField.getText().trim());
                            double fNet = fGross - fDed;
                            double sellTotal = fNet * sellPrice;

                            shipErr.setText("جاري الحفظ...");

                            AsyncHelper.runVoid(
                                    () -> {
                                        // تسجيل في warehouse_stock_exits
                                        String exitSql = "INSERT INTO warehouse_stock_exits " +
                                                "(warehouse_id, exit_date, weight_green, weight_colored, weight_white, weight_waste, destination, exit_price, exit_value, recorded_by) " +
                                                "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?)";
                                        try (Connection conn = DatabaseManager_online.getConnection();
                                             PreparedStatement stmt = conn.prepareStatement(exitSql)) {
                                            stmt.setInt(1, warehouse.getId());
                                            stmt.setDouble(2, green); stmt.setDouble(3, colored);
                                            stmt.setDouble(4, white); stmt.setDouble(5, waste);
                                            stmt.setString(6, destination);
                                            stmt.setDouble(7, exitPrice);
                                            stmt.setDouble(8, totalKg * exitPrice);
                                            stmt.setInt(9, userId);
                                            stmt.executeUpdate();
                                        }

                                        // تسجيل في factory_shipments
                                        String shipSql = "INSERT INTO factory_shipments " +
                                                "(factory_id, shipment_date, gross_weight, deduction_kg, net_weight, price_per_kg, total_amount, recorded_by) " +
                                                "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?)";
                                        try (Connection conn = DatabaseManager_online.getConnection();
                                             PreparedStatement stmt = conn.prepareStatement(shipSql)) {
                                            stmt.setInt(1, finalFactory.getId());
                                            stmt.setDouble(2, fGross); stmt.setDouble(3, fDed);
                                            stmt.setDouble(4, fNet); stmt.setDouble(5, sellPrice);
                                            stmt.setDouble(6, sellTotal);
                                            stmt.setInt(7, userId);
                                            stmt.executeUpdate();
                                        }
                                    },
                                    () -> {
                                        exitGreenField.clear(); exitColoredField.clear();
                                        exitWhiteField.clear(); exitWasteField.clear(); exitPriceField.clear();
                                        exitTotalLabel.setText("الإجمالي: —");
                                        exitValueLabel.setText("قيمة الشراء: —");
                                        exitMsg.setText("تم تسجيل الشحنة ✓");
                                        refreshStockCard(stockCard, warehouse.getId());
                                        ((Stage) saveShipBtn.getScene().getWindow()).close();
                                    },
                                    error -> shipErr.setText("خطأ: " + (error != null ? error.getMessage() : "")),
                                    saveShipBtn
                            );

                        } catch (NumberFormatException ex) { shipErr.setText("تأكد من الأرقام"); }
                    });

                    VBox shipLayout = new VBox(10,
                            new Label("--- بيانات البيع للمصنع ---"),
                            new Label(String.format("قيمة الشراء: %.2f جنيه", purchaseTotal)),
                            new Separator(),
                            new Label("الوزن الإجمالي عند المصنع:"), factoryGrossField,
                            new Label("خصم المصنع (كيلو):"), factoryDeductionField,
                            new Label("سعر البيع / كيلو:"), sellPriceField,
                            factoryNetLabel, sellTotalLabel,
                            new Separator(),
                            grossProfitLabel,
                            saveShipBtn, shipErr);
                    shipLayout.setPadding(new Insets(20));
                    ScrollPane scroll = new ScrollPane(shipLayout);
                    scroll.setFitToWidth(true);
                    VBox dialogBox = new VBox(scroll);
                    DialogHelper.create("بيانات الشحنة: " + finalFactory.getName(), dialogBox, 420, 380).show();
                    return;
                }

                // لو عم داود — سجل مباشرة
                exitMsg.setText("جاري الحفظ...");
                AsyncHelper.runVoid(
                        () -> {
                            String sql = "INSERT INTO warehouse_stock_exits " +
                                    "(warehouse_id, exit_date, weight_green, weight_colored, weight_white, weight_waste, destination, exit_price, exit_value, recorded_by) " +
                                    "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                        },
                        () -> {
                            exitGreenField.clear(); exitColoredField.clear();
                            exitWhiteField.clear(); exitWasteField.clear(); exitPriceField.clear();
                            exitTotalLabel.setText("الإجمالي: —");
                            exitValueLabel.setText("قيمة الشراء: —");
                            exitMsg.setText("تم ✓");
                            refreshStockCard(stockCard, warehouse.getId());
                        },
                        error -> exitMsg.setText("خطأ: " + (error != null ? error.getMessage() : "")),
                        saveExitBtn
                );

            } catch (NumberFormatException ex) { exitMsg.setText("تأكد من الأرقام"); }
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
                        new VBox(4, new Label("كمية الخصم (مئوية):"), deductionField),
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
                new VBox(4, new Label("سعر الشراء / كيلو:"), exitPriceField),
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

    private static void loadSuppliers(ComboBox<Supplier> combo, ListView<Supplier> list, int warehouseId) {
        AsyncHelper.run(
                () -> WarehouseDAO.getSuppliersByWarehouse(warehouseId),
                suppliers -> {
                    combo.setItems(FXCollections.observableArrayList(suppliers));
                    list.setItems(FXCollections.observableArrayList(suppliers));
                },
                error -> list.setPlaceholder(new Label("حصل خطأ في تحميل الموردين"))
        );
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

            final String name = nameField.getText().trim();
            final String phone = phoneField.getText().trim();
            final String sector = sectorField.getText().trim();
            final double finalFloor = floor;

            errLbl.setStyle("-fx-text-fill: #5f5e5a;");
            errLbl.setText("جاري الحفظ...");

            AsyncHelper.run(
                    () -> {
                        SupplierDAO.addSupplier(name, phone, sector, finalFloor, userId);

                        List<Supplier> all = SupplierDAO.getAllSuppliers();
                        for (Supplier s : all) {
                            if (s.getName().equals(name)) {
                                WarehouseDAO.assignSupplierToWarehouse(warehouse.getId(), s.getId());
                                break;
                            }
                        }

                        return WarehouseDAO.getSuppliersByWarehouse(warehouse.getId());
                    },
                    updated -> {
                        combo.getItems().setAll(updated);
                        if (list != null) list.getItems().setAll(updated);
                        dialog.close();
                    },
                    error -> {
                        errLbl.setStyle("-fx-text-fill: #b91c1c;");
                        errLbl.setText("حصل خطأ أثناء الحفظ");
                    },
                    saveBtn
            );
        });

        dialog.show();
    }

    private static void refreshStockCard(VBox card, int warehouseId) {
        AsyncHelper.run(
                () -> loadStockData(warehouseId),
                data -> renderStockCard(card, data),
                error -> {
                    card.getChildren().clear();
                    Label title = new Label("إجمالي البضاعة في المخزن"); title.getStyleClass().add("card-title");
                    card.getChildren().addAll(title, new Label("خطأ في تحميل البيانات"));
                }
        );
    }

    private static StockData loadStockData(int warehouseId) throws SQLException {
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

            return new StockData(totalIn, green, colored, white, waste);
        }
    }

    private static void renderStockCard(VBox card, StockData data) {
        card.getChildren().clear();
        Label title = new Label("إجمالي البضاعة في المخزن"); title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        double totalOut = data.green() + data.colored() + data.white() + data.waste();
        double remaining = data.totalIn() - totalOut;

        HBox statsRow = new HBox(12);
        statsRow.getChildren().addAll(
                statBox("إجمالي الداخل", String.format("%.1f", data.totalIn() / 1000), "طن"),
                statBox("إجمالي الخارج", String.format("%.1f", totalOut / 1000), "طن"),
                statBox("المتبقي", String.format("%.1f", remaining / 1000), "طن")
        );
        statsRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        HBox typesRow = new HBox(12);
        typesRow.getChildren().addAll(
                typeBox("أخضر", data.green(), "#2d7d2d"),
                typeBox("ألوان", data.colored(), "#185FA5"),
                typeBox("أبيض", data.white(), "#5f5e5a"),
                typeBox("زبالة", data.waste(), "#854F0B")
        );
        typesRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        card.getChildren().addAll(statsRow, typesRow);
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

    private static double parseField(TextField f) {
        try { return f.getText().trim().isEmpty() ? 0 : Double.parseDouble(f.getText().trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
