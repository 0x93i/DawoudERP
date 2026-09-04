package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.sql.*;

public class DashboardContent {

    public static Node build(int userId, String username, String role) {

        // ── Stats ──
        Label totalInLabel = new Label("—");
        Label totalOutLabel = new Label("—");
        Label balanceLabel = new Label("—");
        Label factoryDebtLabel = new Label("—");

        loadStats(totalInLabel, totalOutLabel, balanceLabel, factoryDebtLabel);

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
        loadRecentTransactions(transList);
        transCard.getChildren().addAll(transTitle, transList);

        // ── أرصدة الموردين ──
        VBox suppCard = new VBox(8); suppCard.getStyleClass().add("card");
        Label suppTitle = new Label("أرصدة الموردين"); suppTitle.getStyleClass().add("card-title");
        VBox suppList = new VBox(4);
        loadSupplierBalances(suppList);
        suppCard.getChildren().addAll(suppTitle, suppList);

        HBox bottomRow = new HBox(12, suppCard, transCard);
        HBox.setHgrow(transCard, Priority.ALWAYS);
        HBox.setHgrow(suppCard, Priority.ALWAYS);

        VBox content = new VBox(14, statsRow, bottomRow);
        content.setPadding(new Insets(4));
        return content;
    }

    private static VBox statCard(String title, Label valueLabel, String unit) {
        valueLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
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

    private static void loadStats(Label totalIn, Label totalOut, Label balance, Label factoryDebt) {
        try (Connection conn = DatabaseManager_online.getConnection()) {

            // إجمالي الداخل هذا الأسبوع
            try (PreparedStatement ps = conn.prepareStatement(
//                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) FROM warehouse_stock_entries WHERE entry_date >= CURRENT_DATE - INTERVAL '7 days'");
                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) " +
                            "FROM warehouse_stock_entries " +
                            "WHERE entry_date::date >= CURRENT_DATE - INTERVAL '7 days'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalIn.setText(String.format("%.1f", rs.getDouble(1)));
            }

            // إجمالي الخارج
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE((SUM(weight_green)+SUM(weight_colored)+SUM(weight_white)+SUM(weight_waste))/1000.0, 0) FROM warehouse_stock_exits");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalOut.setText(String.format("%.1f", rs.getDouble(1)));
            }

            // رصيد المخازن
            double in = 0, out = 0;
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
            balance.setText(String.format("%.1f", in - out));

            // مستحقات المصانع
            double ships = 0, pays = 0;
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
            double debt = ships - pays;
            factoryDebt.setText(String.format("%.0f", debt));
            factoryDebt.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " +
                    (debt > 0 ? "#3B6D11" : "#A32D2D") + ";");

        } catch (SQLException e) {
            System.err.println("Dashboard stats error: " + e.getMessage());
        }
    }

    private static void loadRecentTransactions(VBox container) {
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
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_RIGHT);
                row.setStyle("-fx-padding: 6 0; -fx-border-color: transparent transparent #f0f0f0 transparent;");

                String type = rs.getString("type");
                String color = type.equals("بضاعة") ? "#EAF3DE; -fx-text-fill: #3B6D11" :
                        type.equals("سحب مورد") ? "#FCEBEB; -fx-text-fill: #A32D2D" :
                        "#E6F1FB; -fx-text-fill: #185FA5";

                Label typeLbl = new Label(type);
                typeLbl.setStyle("-fx-font-size: 11px; -fx-background-color: " + color + "; -fx-padding: 2 8; -fx-background-radius: 4;");

                Label nameLbl = new Label(rs.getString("name"));
                nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

                Label amountLbl = new Label(String.format("%.0f جنيه", rs.getDouble("amount")));
                amountLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3B6D11;");

                Label dateLbl = new Label(rs.getString("date"));
                dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");

                row.getChildren().addAll(dateLbl, amountLbl, spacer, nameLbl, typeLbl);
                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            System.err.println("Recent transactions error: " + e.getMessage());
        }
    }

    private static void loadSupplierBalances(VBox container) {
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name FROM suppliers ORDER BY name LIMIT 8")) {
            while (rs.next()) {
                int supplierId = rs.getInt("id");
                String name = rs.getString("name");
                double balance = SupplierDAO.getSupplierBalance(supplierId);

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_RIGHT);
                row.setStyle("-fx-padding: 6 0; -fx-border-color: transparent transparent #f0f0f0 transparent;");

                Label nameLbl = new Label(name);
                nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

                Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

                Label balLbl = new Label(String.format("%.0f جنيه", Math.abs(balance)));
                balLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                        (balance >= 0 ? "#3B6D11" : "#A32D2D") + ";");

                Label statusLbl = new Label(balance >= 0 ? "مدين" : "دائن");
                statusLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                        (balance >= 0 ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#FCEBEB; -fx-text-fill: #A32D2D") + ";");

                row.getChildren().addAll(statusLbl, balLbl, spacer, nameLbl);
                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            System.err.println("Supplier balances error: " + e.getMessage());
        }
    }
}