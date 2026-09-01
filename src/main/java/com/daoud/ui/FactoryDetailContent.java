package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.model.Factory;
import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FactoryDetailContent {

    record HistoryRow(String type, String date, String netWeight, String pct, String amount, String notes) {}

    public static Node build(int userId, String username, String role, Factory factory) {

        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, factory.getId());

        // ── جدول السجل مع فلاتر ──
        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        HBox filtersBox = buildFilters(tableBody, allRows);
        loadRows(allRows, factory.getId());
        renderTable(tableBody, allRows, null);

        // ── شحنة ──
        TextField supplierNameField = new TextField(); supplierNameField.setPromptText("اسم المورد (اختياري)");
        TextField grossWeightField = new TextField(); grossWeightField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionKgField = new TextField(); deductionKgField.setPromptText("كمية الخصم (كيلو)");
        TextField priceField = new TextField(); priceField.setPromptText("سعر الكيلو");

        Label deductionPctLabel = new Label("نسبة الخصم: —");
        Label netWeightLabel = new Label("الوزن الصافي: —");
        Label totalLabel = new Label("الإجمالي: —");

        Runnable calc = () -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double dk = deductionKgField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionKgField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double net = gross - dk;
                double pct = gross > 0 ? (dk / gross) * 100.0 : 0;
                deductionPctLabel.setText(String.format("نسبة الخصم: %.2f%%", pct));
                netWeightLabel.setText(String.format("الوزن الصافي: %.2f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", net * price));
            } catch (NumberFormatException ex) {
                deductionPctLabel.setText("نسبة الخصم: —");
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
            }
        };

        grossWeightField.textProperty().addListener((o,old,n) -> calc.run());
        deductionKgField.textProperty().addListener((o,old,n) -> calc.run());
        priceField.textProperty().addListener((o,old,n) -> calc.run());

        Button saveShipBtn = new Button("تسجيل الشحنة"); saveShipBtn.getStyleClass().add("btn-primary");
        Label shipMsg = new Label("");

        saveShipBtn.setOnAction(e -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double dk = deductionKgField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionKgField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                double pct = gross > 0 ? (dk / gross) * 100.0 : 0;
                FactoryDAO.addShipment(factory.getId(), supplierNameField.getText().trim(), gross, pct, price, userId);
                grossWeightField.clear(); deductionKgField.clear(); priceField.clear(); supplierNameField.clear();
                deductionPctLabel.setText("نسبة الخصم: —");
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
                shipMsg.setText("تم ✓");
                refreshBalance(balanceLabel, factory.getId());
                allRows.clear();
                loadRows(allRows, factory.getId());
                renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { shipMsg.setText("تأكد من الأرقام"); }
        });

        // ── دفعة ──
        TextField payAmount = new TextField(); payAmount.setPromptText("المبلغ");
        TextField payNotes = new TextField(); payNotes.setPromptText("ملاحظة");
        Button savePayBtn = new Button("تسجيل الدفعة"); savePayBtn.getStyleClass().add("btn-default");
        Label payMsg = new Label("");

        savePayBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(payAmount.getText().trim());
                FactoryDAO.addPayment(factory.getId(), amount, payNotes.getText().trim(), userId);
                payAmount.clear(); payNotes.clear();
                payMsg.setText("تم ✓");
                refreshBalance(balanceLabel, factory.getId());
                allRows.clear();
                loadRows(allRows, factory.getId());
                renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { payMsg.setText("ادخل رقم"); }
        });

        Button backBtn = new Button("← رجوع للمصانع"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(FactoriesContent.build(userId, username, role));
            MainLayout.setTitle("المصانع");
        });

        // ── Cards ──
        VBox balanceCard = new VBox(8); balanceCard.getStyleClass().add("card");
        balanceCard.getChildren().addAll(new Label("المصنع: " + factory.getName()), balanceLabel);

        VBox shipCard = new VBox(8); shipCard.getStyleClass().add("card");
        shipCard.getChildren().addAll(
                new Label("تسجيل شحنة:"),
                new HBox(8,
                        new VBox(4, new Label("المورد:"), supplierNameField),
                        new VBox(4, new Label("سعر الكيلو:"), priceField)),
                new HBox(8,
                        new VBox(4, new Label("الوزن الإجمالي:"), grossWeightField),
                        new VBox(4, new Label("كمية الخصم (كيلو):"), deductionKgField)),
                new HBox(16, deductionPctLabel, netWeightLabel, totalLabel),
                saveShipBtn, shipMsg);

        VBox payCard = new VBox(8); payCard.getStyleClass().add("card");
        payCard.getChildren().addAll(
                new Label("استلام دفعة من المصنع:"),
                new HBox(8,
                        new VBox(4, new Label("المبلغ:"), payAmount),
                        new VBox(4, new Label("ملاحظة:"), payNotes)),
                savePayBtn, payMsg);

        // ── History Card ──
        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات");
        histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        return new VBox(12, backBtn, balanceCard, shipCard, payCard, historyCard);
    }

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows) {
        Button allBtn = filterBtn("الكل");
        Button shipBtn = filterBtn("شحنات");
        Button payBtn = filterBtn("دفعات");

        setActiveFilter(allBtn, allBtn, shipBtn, payBtn);

        allBtn.setOnAction(e -> {
            setActiveFilter(allBtn, allBtn, shipBtn, payBtn);
            renderTable(tableBody, allRows, null);
        });
        shipBtn.setOnAction(e -> {
            setActiveFilter(shipBtn, allBtn, shipBtn, payBtn);
            renderTable(tableBody, allRows, "شحنة");
        });
        payBtn.setOnAction(e -> {
            setActiveFilter(payBtn, allBtn, shipBtn, payBtn);
            renderTable(tableBody, allRows, "دفعة");
        });

        HBox box = new HBox(8, allBtn, shipBtn, payBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private static void setActiveFilter(Button active, Button... all) {
        for (Button b : all) {
            if (b == active) {
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            } else {
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
            }
        }
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "الوزن الصافي", "نسبة الخصم", "المبلغ", "ملاحظة"};
        double[] widths = {100, 80, 110, 100, 110, 150};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<HistoryRow> rows, int factoryId) {
        String sql = """
            SELECT 'شحنة' as type, shipment_date as date, net_weight, deduction_pct, total_amount as amount,
                   COALESCE(supplier_name,'') as notes
            FROM factory_shipments WHERE factory_id = ?
            UNION ALL
            SELECT 'دفعة' as type, payment_date as date, 0, 0, amount,
                   COALESCE(notes,'') as notes
            FROM factory_payments WHERE factory_id = ?
            ORDER BY date DESC LIMIT 50
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factoryId); stmt.setInt(2, factoryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                boolean isShip = rs.getString("type").equals("شحنة");
                rows.add(new HistoryRow(
                        rs.getString("type"),
                        rs.getString("date"),
                        isShip ? String.format("%.1f كيلو", rs.getDouble("net_weight")) : "—",
                        isShip ? String.format("%.1f%%", rs.getDouble("deduction_pct")) : "—",
                        String.format("%.0f جنيه", rs.getDouble("amount")),
                        rs.getString("notes")
                ));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType) {
        table.getChildren().clear();
        double[] widths = {100, 80, 110, 100, 110, 150};
        boolean odd = true;
        boolean hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox();
            String bg = odd ? "#ffffff" : "#fafaf8";
            r.setStyle("-fx-background-color: " + bg + "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isShip = row.type().equals("شحنة");
            String amountColor = isShip ? "#1a1a18" : "#3B6D11";

            String[] vals = {row.date(), row.type(), row.netWeight(), row.pct(), row.amount(), row.notes()};
            for (int i = 0; i < vals.length; i++) {
                Label lbl = new Label(vals[i]);
                String style = "-fx-font-size: 12px; -fx-text-fill: " + (i == 4 ? amountColor : "#1a1a18") + ";";
                if (i == 4) style += " -fx-font-weight: bold;";
                if (i == 1) style += " -fx-background-color: " + (isShip ? "#EAF3DE" : "#E6F1FB") + "; -fx-text-fill: " + (isShip ? "#3B6D11" : "#185FA5") + "; -fx-padding: 2 8; -fx-background-radius: 4;";
                lbl.setStyle(style);
                lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
                r.getChildren().add(lbl);
            }
            table.getChildren().add(r);
        }

        if (!hasRows) {
            Label empty = new Label("مفيش بيانات");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
        }
    }

    private static void refreshBalance(Label lbl, int factoryId) {
        double balance = FactoryDAO.getFactoryBalance(factoryId);
        lbl.setText(String.format(balance >= 0 ? "المصنع مدين لعم داود: %.2f جنيه" : "عم داود مدين للمصنع: %.2f جنيه", Math.abs(balance)));
        lbl.getStyleClass().removeAll("label-balance-positive", "label-balance-negative");
        lbl.getStyleClass().add(balance >= 0 ? "label-balance-positive" : "label-balance-negative");
    }
}