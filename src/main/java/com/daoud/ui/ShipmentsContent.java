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
        String[] cols = {"رقم الشحنة", "المورد", "المصنع", "الوزن الصافي", "سعر الكيلو", "الإجمالي", "صافي الربح", "الحالة", ""};
        double[] widths = {100, 120, 120, 100, 90, 100, 100, 90, 80};
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

                // الحالة badge
                Label statusLbl = new Label(getStatusText(status));
                statusLbl.setStyle(getStatusStyle(status));
                statusLbl.setMinWidth(90);
                row.getChildren().add(statusLbl);

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
        List<Supplier> suppliers = SupplierDAO.getAllSuppliers();
        List<Factory> factories = FactoryDAO.getAllFactories();

        ComboBox<Supplier> supplierCombo = new ComboBox<>(FXCollections.observableArrayList(suppliers));
        supplierCombo.setPromptText("اختار المورد");

        ComboBox<Factory> factoryCombo = new ComboBox<>(FXCollections.observableArrayList(factories));
        factoryCombo.setPromptText("اختار المصنع");

        TextField grossField = new TextField(); grossField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionField = new TextField(); deductionField.setPromptText("خصم الوزن (كيلو)");
        TextField priceField = new TextField(); priceField.setPromptText("سعر الكيلو");

        Label netWeightLbl = new Label("الوزن الصافي: —");
        Label totalLbl = new Label("الإجمالي: —");

        // مصاريف التشغيل
        TextField loadingField = new TextField("0"); loadingField.setPromptText("تحميل");
        TextField workersField = new TextField("0"); workersField.setPromptText("عمال");
        TextField fuelField = new TextField("0"); fuelField.setPromptText("بنزين");
        TextField transportField = new TextField("0"); transportField.setPromptText("نقل");
        TextField otherField = new TextField("0"); otherField.setPromptText("أخرى");
        Label profitLbl = new Label("صافي الربح: —");

        Runnable calc = () -> {
            try {
                double gross = Double.parseDouble(grossField.getText().trim());
                double ded = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double net = gross - ded;
                double total = net * price;
                double costs = parseD(loadingField) + parseD(workersField) + parseD(fuelField) + parseD(transportField) + parseD(otherField);
                netWeightLbl.setText(String.format("الوزن الصافي: %.1f كيلو", net));
                totalLbl.setText(String.format("الإجمالي: %.0f جنيه", total));
                profitLbl.setText(String.format("صافي الربح: %.0f جنيه", total - costs));
                profitLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: " + ((total - costs) >= 0 ? "#3B6D11" : "#A32D2D") + ";");
            } catch (NumberFormatException ex) {
                netWeightLbl.setText("الوزن الصافي: —");
            }
        };

        grossField.textProperty().addListener((o,old,n) -> calc.run());
        deductionField.textProperty().addListener((o,old,n) -> calc.run());
        priceField.textProperty().addListener((o,old,n) -> calc.run());
        loadingField.textProperty().addListener((o,old,n) -> calc.run());
        workersField.textProperty().addListener((o,old,n) -> calc.run());
        fuelField.textProperty().addListener((o,old,n) -> calc.run());
        transportField.textProperty().addListener((o,old,n) -> calc.run());
        otherField.textProperty().addListener((o,old,n) -> calc.run());

        Button saveBtn = new Button("حفظ الشحنة"); saveBtn.getStyleClass().add("btn-primary");
        Label errLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("المورد:"), supplierCombo,
                new Label("المصنع:"), factoryCombo,
                new Label("الوزن الإجمالي (كيلو):"), grossField,
                new Label("خصم الوزن (كيلو):"), deductionField,
                new Label("سعر الكيلو:"), priceField,
                netWeightLbl, totalLbl,
                new Separator(),
                new Label("مصاريف التشغيل:"),
                new HBox(8, new VBox(4, new Label("تحميل:"), loadingField),
                        new VBox(4, new Label("عمال:"), workersField),
                        new VBox(4, new Label("بنزين:"), fuelField)),
                new HBox(8, new VBox(4, new Label("نقل:"), transportField),
                        new VBox(4, new Label("أخرى:"), otherField)),
                profitLbl,
                saveBtn, errLbl
        );
        layout.setPadding(new Insets(20));

        Stage dialog = DialogHelper.create("شحنة جديدة", layout, 480, 580);

        saveBtn.setOnAction(e -> {
            if (supplierCombo.getValue() == null || factoryCombo.getValue() == null) {
                errLbl.setText("اختار المورد والمصنع"); return;
            }
            try {
                double gross = Double.parseDouble(grossField.getText().trim());
                double ded = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                double net = gross - ded;
                double total = net * price;

                String num = generateShipmentNumber();
                String sql = """
                    INSERT INTO shipments (shipment_number, supplier_id, factory_id, shipment_date,
                        gross_weight, deduction_kg, net_weight, price_per_kg, total_amount,
                        cost_loading, cost_workers, cost_fuel, cost_transport, cost_other,
                        status, recorded_by)
                    VALUES (?, ?, ?, date('now'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)
                """;
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, num);
                    stmt.setInt(2, supplierCombo.getValue().getId());
                    stmt.setInt(3, factoryCombo.getValue().getId());
                    stmt.setDouble(4, gross); stmt.setDouble(5, ded);
                    stmt.setDouble(6, net); stmt.setDouble(7, price);
                    stmt.setDouble(8, total);
                    stmt.setDouble(9, parseD(loadingField)); stmt.setDouble(10, parseD(workersField));
                    stmt.setDouble(11, parseD(fuelField)); stmt.setDouble(12, parseD(transportField));
                    stmt.setDouble(13, parseD(otherField));
                    stmt.setInt(14, userId);
                    stmt.executeUpdate();
                }
                loadShipments(tableBox, "all");
                dialog.close();
            } catch (NumberFormatException ex) { errLbl.setText("تأكد من الأرقام");
            } catch (SQLException ex) { errLbl.setText("خطأ: " + ex.getMessage()); }
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
            String status = rs.getString("status");

            Label info = new Label(String.format(
                    "رقم الشحنة: %s%nالمورد: %s%nالمصنع: %s%nالتاريخ: %s%n%nالوزن الإجمالي: %.1f كيلو%nالخصم: %.1f كيلو%nالوزن الصافي: %.1f كيلو%nسعر الكيلو: %.2f ج%nالإجمالي: %.0f ج%n%nمصاريف التشغيل: %.0f ج%nصافي الربح: %.0f ج",
                    rs.getString("shipment_number"),
                    rs.getString("supplier") != null ? rs.getString("supplier") : "—",
                    rs.getString("factory") != null ? rs.getString("factory") : "—",
                    rs.getString("shipment_date"),
                    rs.getDouble("gross_weight"), rs.getDouble("deduction_kg"),
                    rs.getDouble("net_weight"), rs.getDouble("price_per_kg"),
                    total, costs, profit));
            info.setStyle("-fx-font-size: 13px; -fx-line-spacing: 4;");

            // تغيير الحالة
            ComboBox<String> statusCombo = new ComboBox<>(FXCollections.observableArrayList("pending", "completed", "partial"));
            statusCombo.setValue(status);
            Button updateBtn = new Button("تحديث الحالة"); updateBtn.getStyleClass().add("btn-primary");
            Label msg = new Label("");

            updateBtn.setOnAction(e -> {
                try (Connection c = DatabaseManager_online.getConnection();
                     PreparedStatement s = c.prepareStatement("UPDATE shipments SET status = ? WHERE id = ?")) {
                    s.setString(1, statusCombo.getValue());
                    s.setInt(2, shipId);
                    s.executeUpdate();
                    msg.setText("تم التحديث ✓");
                    loadShipments(tableBox, filter);
                } catch (SQLException ex) { msg.setText("خطأ"); }
            });

            VBox layout = new VBox(12, info, new Separator(),
                    new Label("الحالة:"), statusCombo, updateBtn, msg);
            layout.setPadding(new Insets(20));
            DialogHelper.create("تفاصيل الشحنة", layout, 400, 520).show();

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
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

    private static String getStatusText(String status) {
        return switch (status) {
            case "completed" -> "مكتمل";
            case "partial" -> "مدفوع جزئي";
            default -> "في الطريق";
        };
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