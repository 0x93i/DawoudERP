package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DashboardContent {

    private record Stats(double totalIn, double totalOut, double balance, double factoryDebt) {}
    private record TransactionRow(String type, String date, String name, double amount) {}
    private record SupplierBalanceRow(String name, double balance) {}
    private record DashboardData(Stats stats, List<TransactionRow> transactions, List<SupplierBalanceRow> balances) {}

    public static Node build(int userId, String username, String role) {

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        Label loadingLbl = new Label("جاري تحميل لوحة التحكم...");
        loadingLbl.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
        root.getChildren().add(loadingLbl);

        // ── كل استعلامات اللوحة بتتحمل مرة واحدة في الخلفية عشان الواجهة متفريزش ──
        AsyncHelper.run(
                DashboardContent::loadDashboardData,
                data -> root.getChildren().setAll(buildContent(data)),
                error -> {
                    Label errLbl = new Label("حصل خطأ في تحميل لوحة التحكم، حاول تفتحها تاني");
                    errLbl.setStyle("-fx-text-fill: #b91c1c; -fx-padding: 20; -fx-font-size: 13px;");
                    root.getChildren().setAll(errLbl);
                }
        );

        return root;
    }

    private static Node buildContent(DashboardData data) {
        Label totalInLabel = new Label(String.format("%.1f", data.stats().totalIn()));
        Label totalOutLabel = new Label(String.format("%.1f", data.stats().totalOut()));
        Label balanceLabel = new Label(String.format("%.1f", data.stats().balance()));
        Label factoryDebtLabel = new Label(String.format("%.0f", data.stats().factoryDebt()));
        factoryDebtLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " +
                (data.stats().factoryDebt() > 0 ? "#3B6D11" : "#A32D2D") + ";");

        HBox statsRow = new HBox(12,
                statCard("إجمالي الداخل (هذا الأسبوع)", totalInLabel, "طن"),
                statCard("إجمالي الخارج للمصانع", totalOutLabel, "طن"),
                statCard("رصيد المخازن الحالي", balanceLabel, "طن"),
                statCard("مستحقات من المصانع", factoryDebtLabel, "جنيه")
        );
        statsRow.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        // ── آخر المعاملات ──
        VBox transCard = new VBox(8); transCard.getStyleClass().add("card");
        Label transTitle = new Label("آخر المعاملات"); transTitle.getStyleClass().add("card-title");
        VBox transList = new VBox(4);
        for (TransactionRow t : data.transactions()) transList.getChildren().add(transactionRow(t));
        transCard.getChildren().addAll(transTitle, transList);

        // ── أرصدة الموردين ──
        VBox suppCard = new VBox(8); suppCard.getStyleClass().add("card");
        Label suppTitle = new Label("أرصدة الموردين"); suppTitle.getStyleClass().add("card-title");
        VBox suppList = new VBox(4);
        for (SupplierBalanceRow b : data.balances()) suppList.getChildren().add(balanceRow(b));
        suppCard.getChildren().addAll(suppTitle, suppList);

        HBox bottomRow = new HBox(12, suppCard, transCard);
        HBox.setHgrow(transCard, Priority.ALWAYS);
        HBox.setHgrow(suppCard, Priority.ALWAYS);

        VBox content = new VBox(14, statsRow, bottomRow);
        content.setPadding(new Insets(4));
        return content;
    }

    private static VBox statCard(String title, Label valueLabel, String unit) {
        valueLabel.setStyle(valueLabel.getStyle().isEmpty() ?
                "-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;" : valueLabel.getStyle());
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");
        Label unitLbl = new Label(unit);
        unitLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
        VBox card = new VBox(4, titleLbl, valueLabel, unitLbl);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private static HBox transactionRow(TransactionRow t) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setStyle("-fx-padding: 6 0; -fx-border-color: transparent transparent #f0f0f0 transparent;");

        String color = t.type().equals("بضاعة") ? "#EAF3DE; -fx-text-fill: #3B6D11" :
                t.type().equals("سحب مورد") ? "#FCEBEB; -fx-text-fill: #A32D2D" :
                "#E6F1FB; -fx-text-fill: #185FA5";

        Label typeLbl = new Label(t.type());
        typeLbl.setStyle("-fx-font-size: 11px; -fx-background-color: " + color + "; -fx-padding: 2 8; -fx-background-radius: 4;");

        Label nameLbl = new Label(t.name());
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Label amountLbl = new Label(String.format("%.0f جنيه", t.amount()));
        amountLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3B6D11;");

        Label dateLbl = new Label(t.date());
        dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");

        row.getChildren().addAll(dateLbl, amountLbl, spacer, nameLbl, typeLbl);
        return row;
    }

    private static HBox balanceRow(SupplierBalanceRow b) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setStyle("-fx-padding: 6 0; -fx-border-color: transparent transparent #f0f0f0 transparent;");

        Label nameLbl = new Label(b.name());
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Label balLbl = new Label(String.format("%.0f جنيه", Math.abs(b.balance())));
        balLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                (b.balance() >= 0 ? "#3B6D11" : "#A32D2D") + ";");

        Label statusLbl = new Label(b.balance() >= 0 ? "مدين" : "دائن");
        statusLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                (b.balance() >= 0 ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#FCEBEB; -fx-text-fill: #A32D2D") + ";");

        row.getChildren().addAll(statusLbl, balLbl, spacer, nameLbl);
        return row;
    }

    // ────────────────────────────────────────────────────────────
    // كل الاستعلامات دي بتتنفذ في الخلفية (thread تاني) - مفيهاش أي حاجة بتلمس الواجهة
    // ────────────────────────────────────────────────────────────

    private static DashboardData loadDashboardData() throws SQLException {
        return new DashboardData(loadStats(), loadRecentTransactions(), loadSupplierBalances());
    }

    private static Stats loadStats() throws SQLException {
        double totalIn = 0, totalOut = 0, in = 0, out = 0, ships = 0, pays = 0;

        try (Connection conn = DatabaseManager_online.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) " +
                            "FROM warehouse_stock_entries " +
                            "WHERE entry_date::date >= CURRENT_DATE - INTERVAL '7 days'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalIn = rs.getDouble(1);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE((SUM(weight_green)+SUM(weight_colored)+SUM(weight_white)+SUM(weight_waste))/1000.0, 0) FROM warehouse_stock_exits");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalOut = rs.getDouble(1);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) FROM warehouse_stock_entries");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) in = rs.getDouble(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE((SUM(weight_green)+SUM(weight_colored)+SUM(weight_white)+SUM(weight_waste))/1000.0, 0) FROM warehouse_stock_exits");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) out = rs.getDouble(1);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM factory_shipments");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) ships = rs.getDouble(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) FROM factory_payments");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) pays = rs.getDouble(1);
            }
        }

        return new Stats(totalIn, totalOut, in - out, ships - pays);
    }

    private static List<TransactionRow> loadRecentTransactions() throws SQLException {
        List<TransactionRow> list = new ArrayList<>();
        String sql = """
            SELECT 'بضاعة' as type, transaction_date as date,
                   s.name as name,
                   ((gross_weight - deduction_kg) * price_per_kg) as amount
            FROM supplier_transactions st
            JOIN suppliers s ON st.supplier_id = s.id
            UNION ALL
            SELECT 'سحب مورد' as type, withdrawal_date as date,
                   s.name as name, amount
            FROM supplier_withdrawals sw
            JOIN suppliers s ON sw.supplier_id = s.id
            UNION ALL
            SELECT 'شحنة مصنع' as type, shipment_date as date,
                   f.name as name, total_amount as amount
            FROM factory_shipments fs
            JOIN factories f ON fs.factory_id = f.id
            ORDER BY date DESC LIMIT 8
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new TransactionRow(rs.getString("type"), rs.getString("date"),
                        rs.getString("name"), rs.getDouble("amount")));
            }
        }
        return list;
    }

    private static List<SupplierBalanceRow> loadSupplierBalances() throws SQLException {
        List<SupplierBalanceRow> list = new ArrayList<>();
        // استعلام واحد بيحسب رصيد كل مورد (أرضية - مشتريات + سحوبات) بدل ما كان بيعمل
        // 3 استعلامات منفصلة لكل مورد لوحده (كان ده أبطأ حاجة في اللوحة كلها)
        String sql = """
            SELECT s.name,
                   s.floor_amount
                   - COALESCE((SELECT SUM((st.gross_weight - st.deduction_kg) * st.price_per_kg)
                               FROM supplier_transactions st WHERE st.supplier_id = s.id), 0)
                   + COALESCE((SELECT SUM(sw.amount)
                               FROM supplier_withdrawals sw WHERE sw.supplier_id = s.id), 0) AS balance
            FROM suppliers s
            ORDER BY s.name
            LIMIT 8
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new SupplierBalanceRow(rs.getString("name"), rs.getDouble("balance")));
            }
        }
        return list;
    }
}
