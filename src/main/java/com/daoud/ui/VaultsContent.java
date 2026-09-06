package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VaultsContent {

    record VaultInfo(int id, String name, String ownerType, double total) {}

    public static Node build(int userId, String username, String role) {

        VBox content = new VBox(12);

        try (Connection conn = DatabaseManager_online.getConnection()) {
            String sql;
            if (role.equals("admin")) {
                sql = "SELECT id, name, owner_type FROM vaults ORDER BY owner_type DESC, name";
            } else {
                sql = "SELECT t.id, t.name, t.owner_type FROM vaults t " +
                        "JOIN warehouses w ON t.owner_id = w.id AND t.owner_type = 'warehouse' " +
                        "WHERE w.manager_user_id = ?";
            }

            PreparedStatement stmt = conn.prepareStatement(sql);
            if (!role.equals("admin")) stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            List<VaultInfo> vaults = new ArrayList<>();
            while (rs.next()) {
                int tid = rs.getInt("id");
                double total = 0;
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE -amount END), 0) " +
                                "FROM vault_transactions WHERE vault_id = ?");
                ps.setInt(1, tid);
                ResultSet tr = ps.executeQuery();
                if (tr.next()) total = tr.getDouble(1);
                vaults.add(new VaultInfo(tid, rs.getString("name"), rs.getString("owner_type"), total));
            }

            // عرض الخزن كـ cards
            HBox cardsRow = new HBox(12);
            for (VaultInfo v : vaults) {
                Label nameLbl = new Label(v.name());
                nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");

                Label totalLbl = new Label(String.format("%.0f جنيه", v.total()));
                totalLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " +
                        (v.total() >= 0 ? "#3B6D11" : "#A32D2D") + ";");

                Label typeLbl = new Label(v.ownerType().equals("admin") ? "خزنة رئيسية" : "خزنة مخزن");
                typeLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");

                Button openBtn = new Button("فتح الخزنة"); openBtn.getStyleClass().add("btn-primary");
                final VaultInfo vault = v;
                openBtn.setOnAction(e -> {
                    MainLayout.loadContent(VaultContent.build(userId, username, role, vault.id(), vault.name()));
                    MainLayout.setTitle(vault.name());
                });

                VBox card = new VBox(6, typeLbl, nameLbl, totalLbl, openBtn);
                card.getStyleClass().add("card");
                card.setAlignment(Pos.CENTER_RIGHT);
                card.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(card, Priority.ALWAYS);
                cardsRow.getChildren().add(card);
            }

            content.getChildren().add(cardsRow);

        } catch (SQLException e) {
            content.getChildren().add(new Label("خطأ: " + e.getMessage()));
        }

        return content;
    }
}