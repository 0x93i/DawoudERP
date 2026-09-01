package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.sql.*;

public class DashboardContent {

    public static Node build(int userId, String username, String role) {

        // ── Stats Cards ──
        Label totalInLabel = new Label("—");
        Label totalOutLabel = new Label("—");
        Label balanceLabel = new Label("—");
        Label factoryDebtLabel = new Label("—");

        loadStats(totalInLabel, totalOutLabel, balanceLabel, factoryDebtLabel);

        VBox card1 = statCard("إجمالي الداخل (هذا الأسبوع)", totalInLabel, "طن");
        VBox card2 = statCard("إجمالي الخارج للمصانع", totalOutLabel, "طن");
        VBox card3 = statCard("رصيد المخازن الحالي", balanceLabel, "طن");
        VBox card4 = statCard("مستحقات من المصانع", factoryDebtLabel, "جنيه");

        HBox statsRow = new HBox(12, card4, card3, card2, card1);
        statsRow.setAlignment(Pos.CENTER);

        // ── آخر المعاملات ──
        VBox transactionsCard = new VBox(8);
        transactionsCard.getStyleClass().add("card");
        Label transTitle = new Label("آخر المعاملات");
        transTitle.getStyleClass().add("card-title");

        VBox transList = new VBox(4);
        loadRecentTransactions(transList);

        transactionsCard.getChildren().addAll(transTitle, transList);

        // ── مستحقات الموردين ──
        VBox suppliersCard = new VBox(8);
        suppliersCard.getStyleClass().add("card");
        Label suppTitle = new Label("أرصدة الموردين");
        suppTitle.getStyleClass().add("card-title");

        VBox suppList = new VBox(4);
        loadSupplierBalances(suppList);

        suppliersCard.getChildren().addAll(suppTitle, suppList);

        HBox bottomRow = new HBox(12, suppliersCard, transactionsCard);
        HBox.setHgrow(transactionsCard, Priority.ALWAYS);
        HBox.setHgrow(suppliersCard, Priority.ALWAYS);

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
            ResultSet r1 = conn.createStatement().executeQuery(
                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) FROM warehouse_stock_entries WHERE entry_date >= date('now', '-7 days')");
            if (r1.next()) totalIn.setText(String.format("%.1f", r1.getDouble(1)));

            // إجمالي الخارج
            ResultSet r2 = conn.createStatement().executeQuery(
                    "SELECT COALESCE((SUM(weight_green)+SUM(weight_colored)+SUM(weight_white)+SUM(weight_waste))/1000.0, 0) FROM warehouse_stock_exits");
            if (r2.next()) totalOut.setText(String.format("%.1f", r2.getDouble(1)));

            // رصيد المخازن
            ResultSet r3in = conn.createStatement().executeQuery(
                    "SELECT COALESCE(SUM(total_weight)/1000.0, 0) FROM warehouse_stock_entries");
            ResultSet r3out = conn.createStatement().executeQuery(
                    "SELECT COALESCE((SUM(weight_green)+SUM(weight_colored)+SUM(weight_white)+SUM(weight_waste))/1000.0, 0) FROM warehouse_stock_exits");
            double in = r3in.next() ? r3in.getDouble(1) : 0;
            double out = r3out.next() ? r3out.getDouble(1) : 0;
            balance.setText(String.format("%.1f", in - out));

            // مستحقات المصانع
            ResultSet r4ship = conn.createStatement().executeQuery(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM factory_shipments");
            ResultSet r4pay = conn.createStatement().executeQuery(
                    "SELECT COALESCE(SUM(amount), 0) FROM factory_payments");
            double ships = r4ship.next() ? r4ship.getDouble(1) : 0;
            double pays = r4pay.next() ? r4pay.getDouble(1) : 0;
            factoryDebt.setText(String.format("%.0f", ships - pays));
            factoryDebt.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " +
                    ((ships - pays) > 0 ? "#3B6D11" : "#A32D2D") + ";");

        } catch (SQLException e) {
            System.err.println("Dashboard error: " + e.getMessage());
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

                Label typeLbl = new Label(rs.getString("type"));
                typeLbl.setStyle("-fx-font-size: 11px; -fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-padding: 2 8; -fx-background-radius: 4;");

                Label nameLbl = new Label(rs.getString("name"));
                nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                Label amountLbl = new Label(String.format("%.0f جنيه", rs.getDouble("amount")));
                amountLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #3B6D11;");

                Label dateLbl = new Label(rs.getString("date"));
                dateLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");

                row.getChildren().addAll(dateLbl, amountLbl, spacer, nameLbl, typeLbl);
                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("خطأ في تحميل البيانات"));
        }
    }

    private static void loadSupplierBalances(VBox container) {
        String sql = "SELECT id, name, floor_amount FROM suppliers ORDER BY name LIMIT 8";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int supplierId = rs.getInt("id");
                String name = rs.getString("name");

                double floor = rs.getDouble("floor_amount");
                double purchases = 0, withdrawals = 0;

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM((gross_weight-deduction_kg)*price_per_kg),0) FROM supplier_transactions WHERE supplier_id=?");
                ps.setInt(1, supplierId);
                ResultSet pr = ps.executeQuery();
                if (pr.next()) purchases = pr.getDouble(1);

                PreparedStatement ws = conn.prepareStatement(
                        "SELECT COALESCE(SUM(amount),0) FROM supplier_withdrawals WHERE supplier_id=?");
                ws.setInt(1, supplierId);
                ResultSet wr = ws.executeQuery();
                if (wr.next()) withdrawals = wr.getDouble(1);

                double balance = floor - purchases + withdrawals;

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_RIGHT);
                row.setStyle("-fx-padding: 6 0; -fx-border-color: transparent transparent #f0f0f0 transparent;");

                Label nameLbl = new Label(name);
                nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

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
            container.getChildren().add(new Label("خطأ"));
        }
    }
}