package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.FactoryDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Factory;
import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.List;

public class ShipmentsContent {

    public static Node build(int userId, String username, String role) {

        // ── الجدول ──
        VBox tableBox = new VBox(0);
        loadShipments(tableBox, "all");

        // ── فلاتر ──
        Button allBtn = new Button("الكل"); allBtn.getStyleClass().add("btn-primary");
        Button pendingBtn = new Button("في الطريق"); pendingBtn.getStyleClass().add("btn-default");
        Button completedBtn = new Button("مكتمل"); completedBtn.getStyleClass().add("btn-default");
        Button partialBtn = new Button("مدفوع جزئي"); partialBtn.getStyleClass().add("btn-default");

        allBtn.setOnAction(e -> { loadShipments(tableBox, "all"); setActive(allBtn, pendingBtn, completedBtn, partialBtn); });
        pendingBtn.setOnAction(e -> { loadShipments(tableBox, "pending"); setActive(pendingBtn, allBtn, completedBtn, partialBtn); });
        completedBtn.setOnAction(e -> { loadShipments(tableBox, "completed"); setActive(completedBtn, allBtn, pendingBtn, partialBtn); });
        partialBtn.setOnAction(e -> { loadShipments(tableBox, "partial"); setActive(partialBtn, allBtn, pendingBtn, completedBtn); });

        HBox filters = new HBox(8, allBtn, pendingBtn, completedBtn, partialBtn);
        filters.setAlignment(Pos.CENTER_RIGHT);

        // ── زر إضافة ──
        Button addBtn = new Button("+ شحنة جديدة");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showAddDialog(userId, tableBox));

        HBox topBar = new HBox(10, filters, new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, addBtn);
        topBar.setAlignment(Pos.CENTER_RIGHT);

        // ── Header الجدول ──
        HBox header = tableHeader();

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        Label cardTitle = new Label("الشحنات");
        cardTitle.getStyleClass().add("card-title");
        card.getChildren().addAll(cardTitle, topBar, header, tableBox);

        return new VBox(12, card);
    }

    private static HBox tableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10; -fx-background-radius: 6;");
        String[] cols = {"رقم الشحنة", "المورد", "المصنع", "الوزن الصافي", "سعر الكيلو", "الإجمالي", "صافي الربح", ""};
        double[] widths = {100, 120, 120, 100, 90, 100, 100, 80};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadShipments(VBox container, String filter) {
        container.getChildren().clear();
        String sql;
        if (filter.equals("all")) {
            sql = """
                SELECT sh.id, sh.shipment_number, s.name as supplier, f.name as factory,
                       sh.net_weight, sh.price_per_kg, sh.total_amount,
                       sh.cost_loading + sh.cost_workers + sh.cost_fuel + sh.cost_transport + sh.cost_other as total_costs,
                       sh.status, sh.shipment_date
                FROM shipments sh
                LEFT JOIN suppliers s ON sh.supplier_id = s.id
                LEFT JOIN factories f ON sh.factory_id = f.id
                ORDER BY sh.shipment_date DESC
            """;
        } else {
            sql = """
                SELECT sh.id, sh.shipment_number, s.name as supplier, f.name as factory,
                       sh.net_weight, sh.price_per_kg, sh.total_amount,
                       sh.cost_loading + sh.cost_workers + sh.cost_fuel + sh.cost_transport + sh.cost_other as total_costs,
                       sh.status, sh.shipment_date
                FROM shipments sh
                LEFT JOIN suppliers s ON sh.supplier_id = s.id
                LEFT JOIN factories f ON sh.factory_id = f.id
                WHERE sh.status = '""" + filter + "' ORDER BY sh.shipment_date DESC";
        }

        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                int id = rs.getInt("id");
                double netWeight = rs.getDouble("net_weight");
                double total = rs.getDouble("total_amount");
                double costs = rs.getDouble("total_costs");
                double profit = total - costs;
                String status = rs.getString("status");

                HBox row = new HBox();
                row.setStyle("-fx-padding: 9 10; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;");
                row.setOnMouseEntered(e -> row.setStyle("-fx-padding: 9 10; -fx-background-color: #f5f5f3; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;"));
                row.setOnMouseExited(e -> row.setStyle("-fx-padding: 9 10; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;"));

                String[] vals = {
                        rs.getString("shipment_number"),
                        rs.getString("supplier") != null ? rs.getString("supplier") : "—",
                        rs.getString("factory") != null ? rs.getString("factory") : "—",
                        String.format("%.1f طن", netWeight / 1000.0),
                        String.format("%.2f ج", rs.getDouble("price_per_kg")),
                        String.format("%.0f ج", total),
                        String.format("%.0f ج", profit)
                };
                double[] widths = {100, 120, 120, 100, 90, 100, 100};

                for (int i = 0; i < vals.length; i++) {
                    Label lbl = new Label(vals[i]);
                    lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (i == 6 ? (profit >= 0 ? "#3B6D11" : "#A32D2D") : "#1a1a18") + ";");
                    if (i == 6) lbl.setStyle(lbl.getStyle() + " -fx-font-weight: bold;");
                    lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
                    row.getChildren().add(lbl);
                }


                // زر تفاصيل
                Button detailBtn = new Button("تفاصيل");
                detailBtn.getStyleClass().add("btn-sm");
                detailBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-border-radius: 4; -fx-background-radius: 4;");
                final int shipId = id;
                detailBtn.setOnAction(e -> showDetailDialog(shipId, container, filter));
                row.getChildren().add(detailBtn);

                container.getChildren().add(row);
            }

            if (!hasRows) {
                Label empty = new Label("مفيش شحنات");
                empty.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
                container.getChildren().add(empty);
            }

        } catch (SQLException e) {
            container.getChildren().add(new Label("خطأ: " + e.getMessage()));
        }
    }

    private static void showAddDialog(int userId, VBox tableBox) {

        List<Supplier> suppliers = SupplierDAO.getAllSuppliers().stream()
                .filter(s -> {
                    String sql = "SELECT COUNT(*) FROM warehouse_suppliers WHERE supplier_id = ?";
                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, s.getId());
                        ResultSet rs = stmt.executeQuery();
                        return rs.next() && rs.getInt(1) == 0;
                    } catch (Exception ex) { return true; }
                })
                .collect(java.util.stream.Collectors.toList());
        List<Factory> factories = FactoryDAO.getAllFactories();

        // =========================
        // المورد والمصنع
        // =========================

        ComboBox<Supplier> supplierCombo =
                new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplierCombo.setPromptText("اختار المورد");

        ComboBox<Factory> factoryCombo =
                new ComboBox<>(FXCollections.observableArrayList(factories));
        factoryCombo.setPromptText("اختار المصنع");


        // =========================
        // بيانات الشراء من المورد
        // =========================

        TextField supplierGrossField = new TextField();
        supplierGrossField.setPromptText("الوزن الإجمالي عند المورد (كيلو)");

        TextField supplierDeductionField = new TextField();
        supplierDeductionField.setPromptText("نسبة خصم المورد %");

        TextField purchasePriceField = new TextField();
        purchasePriceField.setPromptText("سعر الشراء / كيلو");


        Label supplierNetLbl =
                new Label("صافي وزن المورد: —");

        Label purchaseTotalLbl =
                new Label("إجمالي الشراء: —");


        // =========================
        // بيانات البيع للمصنع
        // =========================

        TextField factoryGrossField = new TextField();
        factoryGrossField.setPromptText("الوزن الإجمالي عند المصنع (كيلو)");

        TextField factoryDeductionField = new TextField();
        factoryDeductionField.setPromptText("نسبة خصم المصنع %");

        TextField salePriceField = new TextField();
        salePriceField.setPromptText("سعر البيع / كيلو");


        Label factoryNetLbl =
                new Label("صافي وزن المصنع: —");

        Label saleTotalLbl =
                new Label("إجمالي البيع: —");


        // =========================
        // الأرباح
        // =========================

        Label grossProfitLbl =
                new Label("مجمل الربح: —");

        Label netProfitLbl =
                new Label("صافي الربح: —");


        // =========================
        // مصاريف التشغيل
        // =========================

        TextField loadingField = new TextField("0");
        loadingField.setPromptText("تحميل");

        TextField workersField = new TextField("0");
        workersField.setPromptText("عمال");

        TextField fuelField = new TextField("0");
        fuelField.setPromptText("بنزين");

        TextField transportField = new TextField("0");
        transportField.setPromptText("نقل");

        TextField otherField = new TextField("0");
        otherField.setPromptText("أخرى");


        // =========================
        // الحسابات
        // =========================

        Runnable calc = () -> {

            try {

                // -------- المورد --------

                double supplierGross = parseD(supplierGrossField);
                double supplierPct = parseD(supplierDeductionField);
                double supplierDeduction = supplierGross * (supplierPct / 100.0);
                double purchasePrice = parseD(purchasePriceField);
                double supplierNet = supplierGross - supplierDeduction;

                if (supplierNet < 0) {
                    supplierNet = 0;
                }

                double purchaseTotal =
                        supplierNet * purchasePrice;


                // -------- المصنع --------

                double factoryGross = parseD(factoryGrossField);
                double factoryPct = parseD(factoryDeductionField);
                double factoryDeduction = factoryGross * (factoryPct / 100.0);
                double salePrice = parseD(salePriceField);
                double factoryNet = factoryGross - factoryDeduction;

                if (factoryNet < 0) {
                    factoryNet = 0;
                }

                double saleTotal =
                        factoryNet * salePrice;


                // -------- المصاريف --------

                double costs =
                        parseD(loadingField)
                                + parseD(workersField)
                                + parseD(fuelField)
                                + parseD(transportField)
                                + parseD(otherField);


                // -------- الأرباح --------

                double grossProfit =
                        saleTotal - purchaseTotal;

                double netProfit =
                        grossProfit - costs;


                // =========================
                // عرض النتائج
                // =========================

                supplierNetLbl.setText(
                        String.format(
                                "صافي وزن المورد: %.1f كيلو",
                                supplierNet
                        )
                );

                purchaseTotalLbl.setText(
                        String.format(
                                "إجمالي الشراء: %.0f جنيه",
                                purchaseTotal
                        )
                );


                factoryNetLbl.setText(
                        String.format(
                                "صافي وزن المصنع: %.1f كيلو",
                                factoryNet
                        )
                );

                saleTotalLbl.setText(
                        String.format(
                                "إجمالي البيع: %.0f جنيه",
                                saleTotal
                        )
                );


                grossProfitLbl.setText(
                        String.format(
                                "مجمل الربح: %.0f جنيه",
                                grossProfit
                        )
                );

                netProfitLbl.setText(
                        String.format(
                                "صافي الربح: %.0f جنيه",
                                netProfit
                        )
                );


                grossProfitLbl.setStyle(
                        "-fx-font-weight: bold; -fx-text-fill: "
                                + (grossProfit >= 0
                                ? "#3B6D11"
                                : "#A32D2D")
                                + ";"
                );

                netProfitLbl.setStyle(
                        "-fx-font-weight: bold; -fx-text-fill: "
                                + (netProfit >= 0
                                ? "#3B6D11"
                                : "#A32D2D")
                                + ";"
                );


            } catch (Exception ex) {

                supplierNetLbl.setText("صافي وزن المورد: —");
                purchaseTotalLbl.setText("إجمالي الشراء: —");

                factoryNetLbl.setText("صافي وزن المصنع: —");
                saleTotalLbl.setText("إجمالي البيع: —");

                grossProfitLbl.setText("مجمل الربح: —");
                netProfitLbl.setText("صافي الربح: —");
            }
        };


        // =========================
        // Listeners
        // =========================

        supplierGrossField.textProperty()
                .addListener((o, old, n) -> calc.run());

        supplierDeductionField.textProperty()
                .addListener((o, old, n) -> calc.run());

        purchasePriceField.textProperty()
                .addListener((o, old, n) -> calc.run());


        factoryGrossField.textProperty()
                .addListener((o, old, n) -> calc.run());

        factoryDeductionField.textProperty()
                .addListener((o, old, n) -> calc.run());

        salePriceField.textProperty()
                .addListener((o, old, n) -> calc.run());


        loadingField.textProperty()
                .addListener((o, old, n) -> calc.run());

        workersField.textProperty()
                .addListener((o, old, n) -> calc.run());

        fuelField.textProperty()
                .addListener((o, old, n) -> calc.run());

        transportField.textProperty()
                .addListener((o, old, n) -> calc.run());

        otherField.textProperty()
                .addListener((o, old, n) -> calc.run());


        // =========================
        // زر الحفظ
        // =========================

        Button saveBtn =
                new Button("حفظ الشحنة");

        saveBtn.getStyleClass().add("btn-primary");

        Label errLbl =
                new Label("");


        // =========================
        // Layout
        // =========================

        VBox layout = new VBox(
                10,

                new Label("المورد:"),
                supplierCombo,

                new Label("المصنع:"),
                factoryCombo,

                new Separator(),

                new Label("━━━ بيانات الشراء من المورد ━━━"),

                new Label("الوزن الإجمالي عند المورد (كيلو):"),
                supplierGrossField,

                new Label("خصم المورد (كيلو):"),
                supplierDeductionField,

                new Label("سعر الشراء / كيلو:"),
                purchasePriceField,

                supplierNetLbl,
                purchaseTotalLbl,

                new Separator(),

                new Label("━━━ بيانات البيع للمصنع ━━━"),

                new Label("الوزن الإجمالي عند المصنع (كيلو):"),
                factoryGrossField,

                new Label("خصم المصنع (كيلو):"),
                factoryDeductionField,

                new Label("سعر البيع / كيلو:"),
                salePriceField,

                factoryNetLbl,
                saleTotalLbl,

                new Separator(),

                new Label("━━━ مصاريف التشغيل ━━━"),

                new HBox(
                        8,
                        new VBox(4,
                                new Label("تحميل:"),
                                loadingField
                        ),
                        new VBox(4,
                                new Label("عمال:"),
                                workersField
                        ),
                        new VBox(4,
                                new Label("بنزين:"),
                                fuelField
                        )
                ),

                new HBox(
                        8,
                        new VBox(4,
                                new Label("نقل:"),
                                transportField
                        ),
                        new VBox(4,
                                new Label("أخرى:"),
                                otherField
                        )
                ),

                new Separator(),

                grossProfitLbl,
                netProfitLbl,

                saveBtn,
                errLbl
        );

        layout.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox scrollContainer = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

//        Stage dialog =
//                DialogHelper.create(
//                        "شحنة جديدة",
//                        layout,
//                        500,
//                        750
//                );
        Stage dialog =
                DialogHelper.create(
                        "شحنة جديدة",
                        scrollContainer,
                        500,
                        650
                );

        // =========================
        // حفظ الشحنة
        // =========================

        saveBtn.setOnAction(e -> {

            if (supplierCombo.getValue() == null) {
                errLbl.setText("اختار المورد");
                return;
            }

            if (factoryCombo.getValue() == null) {
                errLbl.setText("اختار المصنع");
                return;
            }


            try {

                // -------- بيانات المورد --------

                double supplierGross =
                        Double.parseDouble(
                                supplierGrossField.getText().trim()
                        );

                double supplierPct = supplierDeductionField.getText().trim().isEmpty()
                        ? 0 : Double.parseDouble(supplierDeductionField.getText().trim());
                double supplierDeduction = supplierGross * (supplierPct / 100.0);
                double purchasePrice = Double.parseDouble(purchasePriceField.getText().trim());
                double supplierNet = supplierGross - supplierDeduction;


                if (supplierNet < 0) {
                    errLbl.setText(
                            "خصم المورد لا يمكن أن يكون أكبر من الوزن الإجمالي"
                    );
                    return;
                }


                double purchaseTotal =
                        supplierNet * purchasePrice;


                // -------- بيانات المصنع --------
                double factoryGross = Double.parseDouble(factoryGrossField.getText().trim());
                double factoryPct = factoryDeductionField.getText().trim().isEmpty()
                        ? 0 : Double.parseDouble(factoryDeductionField.getText().trim());

                double factoryDeduction = factoryGross * (factoryPct / 100.0);
                double factoryNet = factoryGross - factoryDeduction;
                double salePrice = Double.parseDouble(salePriceField.getText().trim());


                if (factoryNet < 0) {
                    errLbl.setText(
                            "خصم المصنع لا يمكن أن يكون أكبر من الوزن الإجمالي"
                    );
                    return;
                }


                double saleTotal =
                        factoryNet * salePrice;


                // -------- المصاريف --------

                double costLoading =
                        parseD(loadingField);

                double costWorkers =
                        parseD(workersField);

                double costFuel =
                        parseD(fuelField);

                double costTransport =
                        parseD(transportField);

                double costOther =
                        parseD(otherField);


                double totalCosts =
                        costLoading
                                + costWorkers
                                + costFuel
                                + costTransport
                                + costOther;


                // -------- الأرباح --------

                double grossProfit =
                        saleTotal - purchaseTotal;

                double netProfit =
                        grossProfit - totalCosts;


                // -------- رقم الشحنة --------

                String num =
                        generateShipmentNumber();


                // =========================
                // INSERT PostgreSQL
                // =========================

                String sql = """
                INSERT INTO shipments (
                    shipment_number,
                    supplier_id,
                    factory_id,
                    shipment_date,

                    gross_weight,
                    deduction_kg,
                    net_weight,
                    price_per_kg,
                    total_amount,

                    purchase_price_per_kg,
                    purchase_total,

                    factory_gross_weight,
                    factory_deduction_kg,
                    factory_net_weight,

                    sale_price_per_kg,
                    sale_total,

                    cost_loading,
                    cost_workers,
                    cost_fuel,
                    cost_transport,
                    cost_other,

                    gross_profit,
                    net_profit,

                    status,
                    recorded_by
                )

                VALUES (
                    ?, ?, ?, CURRENT_DATE,

                    ?, ?, ?, ?, ?,

                    ?, ?,

                    ?, ?, ?,

                    ?, ?,

                    ?, ?, ?, ?, ?,

                    ?, ?,

                    'pending',
                    ?
                )
                """;


                try (
                        Connection conn =
                                DatabaseManager_online.getConnection();

                        PreparedStatement stmt =
                                conn.prepareStatement(sql)
                ) {

                    int i = 1;


                    stmt.setString(i++, num);

                    stmt.setInt(
                            i++,
                            supplierCombo.getValue().getId()
                    );

                    stmt.setInt(
                            i++,
                            factoryCombo.getValue().getId()
                    );


                    // البيانات القديمة
                    // نحتفظ بها متوافقة مع بيانات المورد

                    stmt.setDouble(i++, supplierGross);
                    stmt.setDouble(i++, supplierDeduction);
                    stmt.setDouble(i++, supplierNet);
                    stmt.setDouble(i++, purchasePrice);
                    stmt.setDouble(i++, purchaseTotal);


                    // بيانات الشراء الجديدة

                    stmt.setDouble(
                            i++,
                            purchasePrice
                    );

                    stmt.setDouble(
                            i++,
                            purchaseTotal
                    );


                    // بيانات المصنع

                    stmt.setDouble(
                            i++,
                            factoryGross
                    );

                    stmt.setDouble(
                            i++,
                            factoryDeduction
                    );

                    stmt.setDouble(
                            i++,
                            factoryNet
                    );


                    // بيانات البيع

                    stmt.setDouble(
                            i++,
                            salePrice
                    );

                    stmt.setDouble(
                            i++,
                            saleTotal
                    );


                    // المصاريف

                    stmt.setDouble(i++, costLoading);
                    stmt.setDouble(i++, costWorkers);
                    stmt.setDouble(i++, costFuel);
                    stmt.setDouble(i++, costTransport);
                    stmt.setDouble(i++, costOther);


                    // الأرباح

                    stmt.setDouble(
                            i++,
                            grossProfit
                    );

                    stmt.setDouble(
                            i++,
                            netProfit
                    );


                    // المستخدم

                    stmt.setInt(
                            i++,
                            userId
                    );


                    stmt.executeUpdate();

                }
                // تسجيل في حساب المورد
                String supplierTransSql = "INSERT INTO supplier_transactions " +
                        "(supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
                try (Connection connS = DatabaseManager_online.getConnection();
                     PreparedStatement stmtS = connS.prepareStatement(supplierTransSql)) {
                    stmtS.setInt(1, supplierCombo.getValue().getId());
                    stmtS.setDouble(2, supplierGross);
                    stmtS.setDouble(3, supplierDeduction);
                    stmtS.setDouble(4, purchasePrice);
                    stmtS.setInt(5, userId);
                    stmtS.executeUpdate();
                }

                // تسجيل في factory_shipments
                String factoryShipSql = "INSERT INTO factory_shipments " +
                        "(factory_id, shipment_date, gross_weight, deduction_pct, deduction_kg, net_weight, price_per_kg, total_amount, source, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, 'supplier', ?)";
                try (Connection conn2 = DatabaseManager_online.getConnection();
                     PreparedStatement stmt2 = conn2.prepareStatement(factoryShipSql)) {
                    double fPct = factoryGross > 0 ? (factoryDeduction / factoryGross * 100) : 0;
                    stmt2.setInt(1, factoryCombo.getValue().getId());
                    stmt2.setDouble(2, factoryGross);
                    stmt2.setDouble(3, fPct);
                    stmt2.setDouble(4, factoryDeduction);
                    stmt2.setDouble(5, factoryNet);
                    stmt2.setDouble(6, salePrice);
                    stmt2.setDouble(7, saleTotal);
                    stmt2.setInt(8, userId);
                    stmt2.executeUpdate();
                }

                // تحديث الجدول

                loadShipments(
                        tableBox,
                        "all"
                );


                dialog.close();


            } catch (NumberFormatException ex) {

                errLbl.setText(
                        "تأكد إن كل الأوزان والأسعار أرقام صحيحة"
                );

            } catch (SQLException ex) {

                errLbl.setText(
                        "خطأ في قاعدة البيانات: "
                                + ex.getMessage()
                );
            }
        });


        dialog.show();
    }


    private static void showDetailDialog(int shipId, VBox tableBox, String filter) {
        try (Connection conn = DatabaseManager_online.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
            SELECT sh.*, s.name as supplier, f.name as factory
            FROM shipments sh
            LEFT JOIN suppliers s ON sh.supplier_id = s.id
            LEFT JOIN factories f ON sh.factory_id = f.id
            WHERE sh.id = ?
        """);
            stmt.setInt(1, shipId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return;

            double total = rs.getDouble("total_amount");
            double costs = rs.getDouble("cost_loading") + rs.getDouble("cost_workers") +
                    rs.getDouble("cost_fuel") + rs.getDouble("cost_transport") + rs.getDouble("cost_other");
            double profit = total - costs;

            VBox infoBox = new VBox(8);
            infoBox.setStyle("-fx-padding: 10;");

            addInfoRow(infoBox, "رقم الشحنة", rs.getString("shipment_number"));
            addInfoRow(infoBox, "التاريخ", rs.getString("shipment_date"));
            addInfoRow(infoBox, "المورد", rs.getString("supplier") != null ? rs.getString("supplier") : "—");
            addInfoRow(infoBox, "المصنع", rs.getString("factory") != null ? rs.getString("factory") : "—");

            infoBox.getChildren().add(new Separator());

            addInfoRow(infoBox, "الوزن الإجمالي عند المورد", String.format("%.1f كيلو", rs.getDouble("gross_weight")));
            addInfoRow(infoBox, "خصم المورد", String.format("%.1f كيلو", rs.getDouble("deduction_kg")));
            addInfoRow(infoBox, "الوزن الصافي عند المورد", String.format("%.1f كيلو", rs.getDouble("net_weight")));
            addInfoRow(infoBox, "سعر الشراء / كيلو", String.format("%.2f جنيه", rs.getDouble("price_per_kg")));
            addInfoRow(infoBox, "إجمالي الشراء", String.format("%.0f جنيه", total));

            infoBox.getChildren().add(new Separator());

            addInfoRow(infoBox, "الوزن الإجمالي عند المصنع", String.format("%.1f كيلو", rs.getDouble("factory_gross_weight")));
            addInfoRow(infoBox, "خصم المصنع", String.format("%.1f كيلو", rs.getDouble("factory_deduction_kg")));
            addInfoRow(infoBox, "الوزن الصافي عند المصنع", String.format("%.1f كيلو", rs.getDouble("factory_net_weight")));
            addInfoRow(infoBox, "سعر البيع / كيلو", String.format("%.2f جنيه", rs.getDouble("sale_price_per_kg")));
            addInfoRow(infoBox, "إجمالي البيع", String.format("%.0f جنيه", rs.getDouble("sale_total")));

            infoBox.getChildren().add(new Separator());

            addInfoRow(infoBox, "مصاريف التشغيل", String.format("%.0f جنيه", costs));

            Label profitLbl = new Label(String.format("%.0f جنيه", profit));
            profitLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (profit >= 0 ? "#3B6D11" : "#A32D2D") + ";");
            Label profitLabel = new Label("صافي الربح:");
            profitLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780; -fx-min-width: 140;");
            HBox profitRow = new HBox(10, profitLbl, profitLabel);
            profitRow.setStyle("-fx-padding: 4 0;");
            infoBox.getChildren().add(profitRow);

            ScrollPane scroll = new ScrollPane(infoBox);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(400);

            VBox layout = new VBox(12, scroll);
            layout.setPadding(new Insets(10));
            DialogHelper.create("تفاصيل الشحنة: " + rs.getString("shipment_number"), layout, 400, 460).show();

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void addInfoRow(VBox box, String label, String value) {
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 4 0;");
        Label labelLbl = new Label(label + ":");
        labelLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780; -fx-min-width: 140;");
        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
        row.getChildren().addAll(valueLbl, labelLbl);
        box.getChildren().add(row);
    }

    private static String generateShipmentNumber() {
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shipments")) {
            int count = rs.next() ? rs.getInt(1) + 1 : 1;
            return String.format("SH-%03d", count);
        } catch (SQLException e) { return "SH-001"; }
    }

    private static double parseD(TextField f) {
        try { return Double.parseDouble(f.getText().trim()); } catch (NumberFormatException e) { return 0; }
    }


    private static String getStatusStyle(String status) {
        return switch (status) {
            case "completed" -> "-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;";
            case "partial" -> "-fx-background-color: #FAEEDA; -fx-text-fill: #854F0B; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;";
            default -> "-fx-background-color: #E6F1FB; -fx-text-fill: #185FA5; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;";
        };
    }

    private static void setActive(Button active, Button... others) {
        active.getStyleClass().remove("btn-default");
        active.getStyleClass().add("btn-primary");
        for (Button b : others) {
            b.getStyleClass().remove("btn-primary");
            if (!b.getStyleClass().contains("btn-default")) b.getStyleClass().add("btn-default");
        }
    }

}