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
import java.util.ArrayList;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ShipmentsContent {

    // ========= ضع هذه الكلاس الداخلية أعلى الكلاس (بعد السطر: public class ShipmentsContent {) =========
    private static class SupplierBlock {
        ComboBox<Supplier> combo;
        TextField grossField, pctField, priceField;
        Label netLbl, totalLbl;
        VBox box;

        double getGross()  { return parseD(grossField); }
        double getPct()    { return parseD(pctField); }
        double getPrice()  { return parseD(priceField); }
        double getDedKg()  { return getGross() * (getPct() / 100.0); }
        double getNet()    { double n = getGross() - getDedKg(); return n < 0 ? 0 : n; }
        double getTotal()  { return getNet() * getPrice(); }
    }

    // ========= استبدل showAddDialog القديمة بالكامل بهذه =========
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

        ComboBox<Factory> factoryCombo = new ComboBox<>(FXCollections.observableArrayList(factories));
        factoryCombo.setPromptText("اختار المصنع");
        factoryCombo.setMaxWidth(Double.MAX_VALUE);

        // ───────── الموردين (ديناميكي) ─────────
        List<SupplierBlock> blocks = new ArrayList<>();
        VBox suppliersContainer = new VBox(10);

        Label purchaseGrandTotalLbl = new Label("إجمالي الشراء الكلي: —");
        purchaseGrandTotalLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #3B6D11; -fx-font-size: 13px;");

        // ───────── بيانات البيع للمصنع ─────────
        TextField factoryGrossField = new TextField();
        factoryGrossField.setPromptText("الوزن الإجمالي عند المصنع (كيلو)");

        TextField factoryDeductionField = new TextField();
        factoryDeductionField.setPromptText("نسبة خصم المصنع %");

        TextField salePriceField = new TextField();
        salePriceField.setPromptText("سعر البيع / كيلو");

        Label factoryNetLbl = new Label("صافي وزن المصنع: —");
        Label saleTotalLbl  = new Label("إجمالي البيع: —");

        // ───────── مصاريف التشغيل ─────────
        TextField loadingField   = new TextField("0");
        TextField workersField   = new TextField("0");
        TextField fuelField      = new TextField("0");
        TextField transportField = new TextField("0");
        TextField otherField     = new TextField("0");

        Label grossProfitLbl = new Label("مجمل الربح: —");
        Label netProfitLbl   = new Label("صافي الربح: —");

        // ───────── الحسابات ─────────
        Runnable calc = () -> {
            try {
                double purchaseGrandTotal = 0;
                for (SupplierBlock b : blocks) {
                    b.netLbl.setText(String.format("الصافي: %.1f كيلو", b.getNet()));
                    b.totalLbl.setText(String.format("الإجمالي: %.0f جنيه", b.getTotal()));
                    purchaseGrandTotal += b.getTotal();
                }
                purchaseGrandTotalLbl.setText(
                        String.format("إجمالي الشراء الكلي: %.0f جنيه", purchaseGrandTotal));

                double fGross = parseD(factoryGrossField);
                double fPct   = parseD(factoryDeductionField);
                double fDed   = fGross * (fPct / 100.0);
                double fNet   = fGross - fDed;
                if (fNet < 0) fNet = 0;
                double salePrice = parseD(salePriceField);
                double saleTotal = fNet * salePrice;

                double costs = parseD(loadingField) + parseD(workersField)
                        + parseD(fuelField) + parseD(transportField) + parseD(otherField);

                double grossProfit = saleTotal - purchaseGrandTotal;
                double netProfit   = saleTotal - (purchaseGrandTotal + costs);

                factoryNetLbl.setText(String.format("صافي وزن المصنع: %.1f كيلو", fNet));
                saleTotalLbl.setText(String.format("إجمالي البيع: %.0f جنيه", saleTotal));
                grossProfitLbl.setText(String.format("مجمل الربح: %.0f جنيه", grossProfit));
                netProfitLbl.setText(String.format("صافي الربح: %.0f جنيه", netProfit));

                grossProfitLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                        + (grossProfit >= 0 ? "#3B6D11" : "#A32D2D") + ";");
                netProfitLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: "
                        + (netProfit >= 0 ? "#3B6D11" : "#A32D2D") + ";");

            } catch (Exception ex) {
                purchaseGrandTotalLbl.setText("إجمالي الشراء الكلي: —");
                factoryNetLbl.setText("صافي وزن المصنع: —");
                saleTotalLbl.setText("إجمالي البيع: —");
                grossProfitLbl.setText("مجمل الربح: —");
                netProfitLbl.setText("صافي الربح: —");
            }
        };

        // ───────── دالة إنشاء بلوك مورد ─────────
        Runnable[] renumber = new Runnable[1];

        java.util.function.Supplier<SupplierBlock> makeBlock = () -> {
            SupplierBlock b = new SupplierBlock();

            b.combo = new ComboBox<>(FXCollections.observableArrayList(suppliers));
            b.combo.setPromptText("اختار المورد");
            b.combo.setMaxWidth(Double.MAX_VALUE);

            b.grossField = new TextField(); b.grossField.setPromptText("الوزن الإجمالي (كيلو)");
            b.pctField   = new TextField(); b.pctField.setPromptText("نسبة الخصم %");
            b.priceField = new TextField(); b.priceField.setPromptText("سعر الشراء / كيلو");

            b.grossField.setMaxWidth(Double.MAX_VALUE);
            b.pctField.setMaxWidth(Double.MAX_VALUE);
            b.priceField.setMaxWidth(Double.MAX_VALUE);

            b.netLbl   = new Label("الصافي: —");
            b.totalLbl = new Label("الإجمالي: —");
            b.netLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            b.totalLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3B6D11;");

            b.grossField.textProperty().addListener((o, ov, nv) -> calc.run());
            b.pctField.textProperty().addListener((o, ov, nv) -> calc.run());
            b.priceField.textProperty().addListener((o, ov, nv) -> calc.run());

            Label title = new Label("مورد");
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1a1a18;");

            Button removeBtn = new Button("حذف");
            removeBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; "
                    + "-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-cursor: hand;");
            removeBtn.setOnAction(ev -> {
                if (blocks.size() <= 1) return;
                blocks.remove(b);
                suppliersContainer.getChildren().remove(b.box);
                renumber[0].run();
                calc.run();
            });

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            HBox header = new HBox(8, title, sp, removeBtn);
            header.setAlignment(Pos.CENTER_RIGHT);

            b.box = new VBox(6,
                    header,
                    b.combo,
                    new HBox(8,
                            new VBox(3, new Label("الوزن الإجمالي:"), b.grossField),
                            new VBox(3, new Label("نسبة الخصم %:"),  b.pctField),
                            new VBox(3, new Label("سعر الشراء:"),    b.priceField)),
                    new HBox(14, b.netLbl, b.totalLbl));

            HBox.setHgrow(b.grossField, Priority.ALWAYS);
            HBox.setHgrow(b.pctField, Priority.ALWAYS);
            HBox.setHgrow(b.priceField, Priority.ALWAYS);

            b.box.setStyle("-fx-background-color: #fafaf8; -fx-padding: 10; "
                    + "-fx-background-radius: 6; -fx-border-color: #e8e8e6; -fx-border-radius: 6;");
            return b;
        };

        renumber[0] = () -> {
            for (int i = 0; i < blocks.size(); i++) {
                HBox hdr = (HBox) blocks.get(i).box.getChildren().get(0);
                ((Label) hdr.getChildren().get(0)).setText("مورد " + (i + 1));
            }
        };

        // أول مورد
        SupplierBlock first = makeBlock.get();
        blocks.add(first);
        suppliersContainer.getChildren().add(first.box);
        renumber[0].run();

        Button addSupplierBtn = new Button("+ إضافة مورد");
        addSupplierBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; "
                + "-fx-font-size: 12px; -fx-padding: 5 14; -fx-background-radius: 4; -fx-cursor: hand;");
        addSupplierBtn.setOnAction(ev -> {
            SupplierBlock nb = makeBlock.get();
            blocks.add(nb);
            suppliersContainer.getChildren().add(nb.box);
            renumber[0].run();
            calc.run();
        });

        factoryGrossField.textProperty().addListener((o, ov, nv) -> calc.run());
        factoryDeductionField.textProperty().addListener((o, ov, nv) -> calc.run());
        salePriceField.textProperty().addListener((o, ov, nv) -> calc.run());
        loadingField.textProperty().addListener((o, ov, nv) -> calc.run());
        workersField.textProperty().addListener((o, ov, nv) -> calc.run());
        fuelField.textProperty().addListener((o, ov, nv) -> calc.run());
        transportField.textProperty().addListener((o, ov, nv) -> calc.run());
        otherField.textProperty().addListener((o, ov, nv) -> calc.run());

        Button saveBtn = new Button("حفظ الشحنة");
        saveBtn.getStyleClass().add("btn-primary");
        Label errLbl = new Label("");

        // ───────── Layout ─────────
        VBox layout = new VBox(10,
                new Label("المصنع:"), factoryCombo,
                new Separator(),
                new Label("━━━ بيانات الشراء من الموردين ━━━"),
                suppliersContainer,
                addSupplierBtn,
                purchaseGrandTotalLbl,
                new Separator(),
                new Label("━━━ بيانات البيع للمصنع ━━━"),
                new Label("الوزن الإجمالي عند المصنع (كيلو):"), factoryGrossField,
                new Label("نسبة خصم المصنع %:"), factoryDeductionField,
                new Label("سعر البيع / كيلو:"), salePriceField,
                factoryNetLbl, saleTotalLbl,
                new Separator(),
                new Label("━━━ مصاريف التشغيل ━━━"),
                new HBox(8,
                        new VBox(4, new Label("تحميل:"), loadingField),
                        new VBox(4, new Label("عمال:"), workersField),
                        new VBox(4, new Label("بنزين:"), fuelField)),
                new HBox(8,
                        new VBox(4, new Label("نقل:"), transportField),
                        new VBox(4, new Label("أخرى:"), otherField)),
                new Separator(),
                grossProfitLbl, netProfitLbl,
                saveBtn, errLbl);

        layout.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox scrollContainer = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Stage dialog = DialogHelper.create("شحنة جديدة", scrollContainer, 520, 680);

        // ───────── الحفظ ─────────
        saveBtn.setOnAction(e -> {

            if (factoryCombo.getValue() == null) { errLbl.setText("اختار المصنع"); return; }

            for (int i = 0; i < blocks.size(); i++) {
                SupplierBlock b = blocks.get(i);
                if (b.combo.getValue() == null) {
                    errLbl.setText("اختار المورد رقم " + (i + 1)); return;
                }
                if (b.getGross() <= 0) {
                    errLbl.setText("ادخل وزن المورد رقم " + (i + 1)); return;
                }
                if (b.getPrice() <= 0) {
                    errLbl.setText("ادخل سعر شراء المورد رقم " + (i + 1)); return;
                }
                if (b.getPct() > 100) {
                    errLbl.setText("نسبة خصم المورد رقم " + (i + 1) + " أكبر من 100%"); return;
                }
            }

            try {
                double purchaseGrandTotal = 0, grossSum = 0, dedSum = 0, netSum = 0;
                for (SupplierBlock b : blocks) {
                    purchaseGrandTotal += b.getTotal();
                    grossSum += b.getGross();
                    dedSum   += b.getDedKg();
                    netSum   += b.getNet();
                }
                double avgPurchasePrice = netSum > 0 ? purchaseGrandTotal / netSum : 0;

                double factoryGross = Double.parseDouble(factoryGrossField.getText().trim());
                double factoryPct   = parseD(factoryDeductionField);
                double factoryDeduction = factoryGross * (factoryPct / 100.0);
                double salePrice = Double.parseDouble(salePriceField.getText().trim());
                double factoryNet = factoryGross - factoryDeduction;
                if (factoryNet < 0) { errLbl.setText("نسبة خصم المصنع أكبر من 100%"); return; }
                double saleTotal = factoryNet * salePrice;

                double costLoading = parseD(loadingField);
                double costWorkers = parseD(workersField);
                double costFuel = parseD(fuelField);
                double costTransport = parseD(transportField);
                double costOther = parseD(otherField);
                double totalCosts = costLoading + costWorkers + costFuel + costTransport + costOther;

                double grossProfit = saleTotal - purchaseGrandTotal;
                double netProfit   = saleTotal - (purchaseGrandTotal + totalCosts);

                String num = generateShipmentNumber();
                int firstSupplierId = blocks.get(0).combo.getValue().getId();

                String sql = """
                    INSERT INTO shipments (
                        shipment_number, supplier_id, factory_id, shipment_date,
                        gross_weight, deduction_kg, net_weight, price_per_kg, total_amount,
                        purchase_price_per_kg, purchase_total,
                        factory_gross_weight, factory_deduction_kg, factory_net_weight,
                        sale_price_per_kg, sale_total,
                        cost_loading, cost_workers, cost_fuel, cost_transport, cost_other,
                        gross_profit, net_profit, status, recorded_by
                    ) VALUES (
                        ?, ?, ?, CURRENT_DATE,
                        ?, ?, ?, ?, ?,
                        ?, ?,
                        ?, ?, ?,
                        ?, ?,
                        ?, ?, ?, ?, ?,
                        ?, ?, 'pending', ?
                    ) RETURNING id
                """;

                int shipmentId = -1;

                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    int i = 1;
                    stmt.setString(i++, num);
                    stmt.setInt(i++, firstSupplierId);
                    stmt.setInt(i++, factoryCombo.getValue().getId());
                    stmt.setDouble(i++, grossSum);
                    stmt.setDouble(i++, dedSum);
                    stmt.setDouble(i++, netSum);
                    stmt.setDouble(i++, avgPurchasePrice);
                    stmt.setDouble(i++, purchaseGrandTotal);
                    stmt.setDouble(i++, avgPurchasePrice);
                    stmt.setDouble(i++, purchaseGrandTotal);
                    stmt.setDouble(i++, factoryGross);
                    stmt.setDouble(i++, factoryDeduction);
                    stmt.setDouble(i++, factoryNet);
                    stmt.setDouble(i++, salePrice);
                    stmt.setDouble(i++, saleTotal);
                    stmt.setDouble(i++, costLoading);
                    stmt.setDouble(i++, costWorkers);
                    stmt.setDouble(i++, costFuel);
                    stmt.setDouble(i++, costTransport);
                    stmt.setDouble(i++, costOther);
                    stmt.setDouble(i++, grossProfit);
                    stmt.setDouble(i++, netProfit);
                    stmt.setInt(i++, userId);

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) shipmentId = rs.getInt(1);
                }

                // موردي الشحنة + حساب كل مورد
                String blockSql = "INSERT INTO shipment_suppliers " +
                        "(shipment_id, supplier_id, gross_weight, deduction_pct, deduction_kg, net_weight, price_per_kg, total_amount) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                String supTransSql = "INSERT INTO supplier_transactions " +
                        "(supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";

                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement bs = conn.prepareStatement(blockSql);
                     PreparedStatement ts = conn.prepareStatement(supTransSql)) {

                    for (SupplierBlock b : blocks) {
                        if (shipmentId != -1) {
                            bs.setInt(1, shipmentId);
                            bs.setInt(2, b.combo.getValue().getId());
                            bs.setDouble(3, b.getGross());
                            bs.setDouble(4, b.getPct());
                            bs.setDouble(5, b.getDedKg());
                            bs.setDouble(6, b.getNet());
                            bs.setDouble(7, b.getPrice());
                            bs.setDouble(8, b.getTotal());
                            bs.addBatch();
                        }
                        ts.setInt(1, b.combo.getValue().getId());
                        ts.setDouble(2, b.getGross());
                        ts.setDouble(3, b.getDedKg());
                        ts.setDouble(4, b.getPrice());
                        ts.setInt(5, userId);
                        ts.addBatch();
                    }
                    if (shipmentId != -1) bs.executeBatch();
                    ts.executeBatch();
                }

                // حساب المصنع
                StringBuilder namesSb = new StringBuilder();
                for (SupplierBlock b : blocks) {
                    if (namesSb.length() > 0) namesSb.append(" + ");
                    namesSb.append(b.combo.getValue().getName());
                }

                String factoryShipSql = "INSERT INTO factory_shipments " +
                        "(factory_id, shipment_date, supplier_name, gross_weight, deduction_pct, deduction_kg, " +
                        "net_weight, price_per_kg, total_amount, source, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?, ?, 'supplier', ?)";
                try (Connection conn2 = DatabaseManager_online.getConnection();
                     PreparedStatement stmt2 = conn2.prepareStatement(factoryShipSql)) {
                    stmt2.setInt(1, factoryCombo.getValue().getId());
                    stmt2.setString(2, namesSb.toString());
                    stmt2.setDouble(3, factoryGross);
                    stmt2.setDouble(4, factoryPct);
                    stmt2.setDouble(5, factoryDeduction);
                    stmt2.setDouble(6, factoryNet);
                    stmt2.setDouble(7, salePrice);
                    stmt2.setDouble(8, saleTotal);
                    stmt2.setInt(9, userId);
                    stmt2.executeUpdate();
                }

                loadShipments(tableBox, "all");
                dialog.close();

            } catch (NumberFormatException ex) {
                errLbl.setText("تأكد إن كل الأوزان والأسعار أرقام صحيحة");
            } catch (SQLException ex) {
                errLbl.setText("خطأ في قاعدة البيانات: " + ex.getMessage());
            }
        });

        dialog.show();
    }


    public static Node build(int userId, String username, String role) {

        // ── الجدول ──
        VBox tableBox = new VBox(0);
        loadShipments(tableBox, "all");



        // ── زر إضافة ──
        Button addBtn = new Button("+ شحنة جديدة");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showAddDialog(userId, tableBox));

        HBox topBar = new HBox(10, new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }}, addBtn);
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
//        String[] cols = {"رقم الشحنة", "المورد", "المصنع", "الوزن الصافي", "سعر الكيلو", "الإجمالي", "صافي الربح", ""};
//        double[] widths = {100, 120, 120, 100, 90, 100, 100, 80};
        String[] cols = {"التاريخ", "المورد", "المصنع", "الوزن الصافي", "سعر الكيلو", "الإجمالي", "صافي الربح", ""};
        double[] widths = {110, 130, 120, 100, 90, 100, 100, 80};

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

        String sql = """
            SELECT sh.id, sh.shipment_date,
                   COALESCE(ss_sup.name, s.name) AS supplier,
                   f.name AS factory,
                   COALESCE(ss.net_weight,   sh.net_weight)   AS row_net,
                   COALESCE(ss.price_per_kg, sh.price_per_kg) AS row_price,
                   COALESCE(ss.total_amount, sh.total_amount) AS row_total,
                   sh.sale_total, sh.purchase_total,
                   sh.cost_loading + sh.cost_workers + sh.cost_fuel
                     + sh.cost_transport + sh.cost_other AS total_costs
            FROM shipments sh
            LEFT JOIN shipment_suppliers ss ON ss.shipment_id = sh.id
            LEFT JOIN suppliers ss_sup ON ss.supplier_id = ss_sup.id
            LEFT JOIN suppliers s ON sh.supplier_id = s.id
            LEFT JOIN factories f ON sh.factory_id = f.id
            ORDER BY sh.shipment_date DESC, sh.id DESC, ss.id ASC
        """;

        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;

                int id = rs.getInt("id");
                double rowNet   = rs.getDouble("row_net");
                double rowPrice = rs.getDouble("row_price");
                double rowTotal = rs.getDouble("row_total");
                double saleTotal     = rs.getDouble("sale_total");
                double purchaseTotal = rs.getDouble("purchase_total");
                double costs         = rs.getDouble("total_costs");

                double shipmentProfit = saleTotal - (purchaseTotal + costs);
                double share = purchaseTotal > 0 ? (rowTotal / purchaseTotal) : 1.0;
                double profit = shipmentProfit * share;

                HBox row = new HBox();
                row.setStyle("-fx-padding: 9 10; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;");
                row.setOnMouseEntered(e -> row.setStyle("-fx-padding: 9 10; -fx-background-color: #f5f5f3; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;"));
                row.setOnMouseExited(e -> row.setStyle("-fx-padding: 9 10; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;"));

                String[] vals = {
                        rs.getString("shipment_date"),
                        rs.getString("supplier") != null ? rs.getString("supplier") : "—",
                        rs.getString("factory")  != null ? rs.getString("factory")  : "—",
                        String.format("%.1f طن", rowNet / 1000.0),
                        String.format("%.2f ج", rowPrice),
                        String.format("%.0f ج", rowTotal),
                        String.format("%.0f ج", profit)
                };
                double[] widths = {110, 130, 120, 100, 90, 100, 100};

                for (int i = 0; i < vals.length; i++) {
                    Label lbl = new Label(vals[i]);
                    lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: "
                            + (i == 6 ? (profit >= 0 ? "#3B6D11" : "#A32D2D") : "#1a1a18") + ";");
                    if (i == 6) lbl.setStyle(lbl.getStyle() + " -fx-font-weight: bold;");
                    lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
                    row.getChildren().add(lbl);
                }

                Button detailBtn = new Button("تفاصيل");
                detailBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
                detailBtn.setMinWidth(80); detailBtn.setPrefWidth(80);
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




    private static void showDetailDialog(int shipId, VBox tableBox, String filter) {
        try (Connection conn = DatabaseManager_online.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT sh.*, s.name AS supplier, f.name AS factory
                FROM shipments sh
                LEFT JOIN suppliers s ON sh.supplier_id = s.id
                LEFT JOIN factories f ON sh.factory_id = f.id
                WHERE sh.id = ?
            """);
            stmt.setInt(1, shipId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return;

            String shipNumber = rs.getString("shipment_number");
            String shipDate   = rs.getString("shipment_date");
            String factoryName = rs.getString("factory") != null ? rs.getString("factory") : "—";

            double saleTotal     = rs.getDouble("sale_total");
            double purchaseTotal = rs.getDouble("purchase_total");
            double costs = rs.getDouble("cost_loading") + rs.getDouble("cost_workers")
                    + rs.getDouble("cost_fuel") + rs.getDouble("cost_transport") + rs.getDouble("cost_other");
            double profit = saleTotal - (purchaseTotal + costs);

            double fGross = rs.getDouble("factory_gross_weight");
            double fDed   = rs.getDouble("factory_deduction_kg");
            double fNet   = rs.getDouble("factory_net_weight");
            double sPrice = rs.getDouble("sale_price_per_kg");

            // الموردين
            List<String[]> supplierRows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT sup.name, ss.gross_weight, ss.deduction_pct, ss.deduction_kg,
                           ss.net_weight, ss.price_per_kg, ss.total_amount
                    FROM shipment_suppliers ss
                    JOIN suppliers sup ON ss.supplier_id = sup.id
                    WHERE ss.shipment_id = ? ORDER BY ss.id
                """)) {
                ps.setInt(1, shipId);
                ResultSet srs = ps.executeQuery();
                while (srs.next()) {
                    supplierRows.add(new String[]{
                            srs.getString("name"),
                            String.format("%.1f كيلو", srs.getDouble("gross_weight")),
                            String.format("%.1f%%", srs.getDouble("deduction_pct")),
                            String.format("%.1f كيلو", srs.getDouble("deduction_kg")),
                            String.format("%.1f كيلو", srs.getDouble("net_weight")),
                            String.format("%.2f جنيه", srs.getDouble("price_per_kg")),
                            String.format("%.0f جنيه", srs.getDouble("total_amount"))
                    });
                }
            }

            // شحنة قديمة بمورد واحد
            if (supplierRows.isEmpty()) {
                double g = rs.getDouble("gross_weight");
                double d = rs.getDouble("deduction_kg");
                supplierRows.add(new String[]{
                        rs.getString("supplier") != null ? rs.getString("supplier") : "—",
                        String.format("%.1f كيلو", g),
                        String.format("%.1f%%", g > 0 ? d / g * 100 : 0),
                        String.format("%.1f كيلو", d),
                        String.format("%.1f كيلو", rs.getDouble("net_weight")),
                        String.format("%.2f جنيه", rs.getDouble("price_per_kg")),
                        String.format("%.0f جنيه", rs.getDouble("total_amount"))
                });
            }

            VBox infoBox = new VBox(8);
            infoBox.setStyle("-fx-padding: 10;");

            addInfoRow(infoBox, "رقم الشحنة", shipNumber);
            addInfoRow(infoBox, "التاريخ", shipDate);
            addInfoRow(infoBox, "المصنع", factoryName);
            addInfoRow(infoBox, "عدد الموردين", String.valueOf(supplierRows.size()));

            infoBox.getChildren().add(new Separator());

            Label supTitle = new Label("━━━ بيانات الشراء من الموردين ━━━");
            supTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            infoBox.getChildren().add(supTitle);

            int n = 1;
            for (String[] r : supplierRows) {
                VBox b = new VBox(4);
                b.setStyle("-fx-background-color: #fafaf8; -fx-padding: 10; -fx-background-radius: 6;");
                Label t = new Label("مورد " + n++ + ": " + r[0]);
                t.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #3B6D11;");
                b.getChildren().add(t);
                addInfoRow(b, "الوزن الإجمالي", r[1]);
                addInfoRow(b, "نسبة الخصم", r[2]);
                addInfoRow(b, "كمية الخصم", r[3]);
                addInfoRow(b, "الوزن الصافي", r[4]);
                addInfoRow(b, "سعر الشراء / كيلو", r[5]);
                addInfoRow(b, "الإجمالي", r[6]);
                infoBox.getChildren().add(b);
            }

            addInfoRow(infoBox, "إجمالي الشراء الكلي", String.format("%.0f جنيه", purchaseTotal));

            infoBox.getChildren().add(new Separator());

            Label facTitle = new Label("━━━ بيانات البيع للمصنع ━━━");
            facTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            infoBox.getChildren().add(facTitle);

            addInfoRow(infoBox, "الوزن الإجمالي عند المصنع", String.format("%.1f كيلو", fGross));
            addInfoRow(infoBox, "خصم المصنع", String.format("%.1f كيلو", fDed));
            addInfoRow(infoBox, "الوزن الصافي عند المصنع", String.format("%.1f كيلو", fNet));
            addInfoRow(infoBox, "سعر البيع / كيلو", String.format("%.2f جنيه", sPrice));
            addInfoRow(infoBox, "إجمالي البيع", String.format("%.0f جنيه", saleTotal));

            infoBox.getChildren().add(new Separator());

            addInfoRow(infoBox, "مصاريف التشغيل", String.format("%.0f جنيه", costs));

            Label profitLbl = new Label(String.format("%.0f جنيه", profit));
            profitLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: "
                    + (profit >= 0 ? "#3B6D11" : "#A32D2D") + ";");
            Label profitLabel = new Label("صافي الربح:");
            profitLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780; -fx-min-width: 140;");
            HBox profitRow = new HBox(10, profitLbl, profitLabel);
            profitRow.setStyle("-fx-padding: 4 0;");
            infoBox.getChildren().add(profitRow);

            ScrollPane scroll = new ScrollPane(infoBox);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(480);

            VBox layout = new VBox(12, scroll);
            layout.setPadding(new Insets(10));
            DialogHelper.create("تفاصيل الشحنة: " + shipNumber, layout, 430, 560).show();

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