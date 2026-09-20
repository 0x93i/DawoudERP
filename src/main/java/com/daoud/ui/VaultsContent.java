package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import com.daoud.db.VaultHelper;
import javafx.collections.FXCollections;
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
        Label loadingLbl = new Label("جاري تحميل الخزن...");
        loadingLbl.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
        content.getChildren().add(loadingLbl);

        AsyncHelper.run(
                () -> loadVaults(userId, role),
                vaults -> content.getChildren().setAll(buildContent(vaults, userId, username, role)),
                error -> content.getChildren().setAll(new Label("حصل خطأ في تحميل الخزن"))
        );

        return content;
    }

    private static List<VaultInfo> loadVaults(int userId, String role) throws SQLException {
        List<VaultInfo> vaults = new ArrayList<>();

        // استعلام واحد بيحسب رصيد كل خزنة مع بعض (بدل استعلام منفصل لكل خزنة لوحدها)
        String sql;
        if (role.equals("admin")) {
            sql = """
                SELECT v.id, v.name, v.owner_type,
                       COALESCE((SELECT SUM(CASE WHEN vt.direction='in' THEN vt.amount ELSE -vt.amount END)
                                 FROM vault_transactions vt WHERE vt.vault_id = v.id), 0) AS total
                FROM vaults v
                ORDER BY v.owner_type DESC, v.name
            """;
        } else {
            sql = """
                SELECT v.id, v.name, v.owner_type,
                       COALESCE((SELECT SUM(CASE WHEN vt.direction='in' THEN vt.amount ELSE -vt.amount END)
                                 FROM vault_transactions vt WHERE vt.vault_id = v.id), 0) AS total
                FROM vaults v
                JOIN warehouses w ON v.owner_id = w.id AND v.owner_type = 'warehouse'
                WHERE w.manager_user_id = ?
            """;
        }

        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!role.equals("admin")) stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                vaults.add(new VaultInfo(rs.getInt("id"), rs.getString("name"),
                        rs.getString("owner_type"), rs.getDouble("total")));
            }
        }
        return vaults;
    }

    private static Node buildContent(List<VaultInfo> vaults, int userId, String username, String role) {
        VBox content = new VBox(12);

        // ── Cards الخزن ──
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

        // ── تحويل بين الخزن (أدمن فقط) ──
        if (role.equals("admin") && vaults.size() > 1) {
            VBox transferCard = new VBox(10); transferCard.getStyleClass().add("card");
            Label transferTitle = new Label("تحويل بين الخزن"); transferTitle.getStyleClass().add("card-title");

            ComboBox<String> fromCombo = new ComboBox<>();
            ComboBox<String> toCombo = new ComboBox<>();
            for (VaultInfo v : vaults) {
                fromCombo.getItems().add(v.id() + "|" + v.name());
                toCombo.getItems().add(v.id() + "|" + v.name());
            }
            fromCombo.setPromptText("من خزنة"); fromCombo.setMaxWidth(Double.MAX_VALUE);
            toCombo.setPromptText("إلى خزنة"); toCombo.setMaxWidth(Double.MAX_VALUE);

            ComboBox<String> payTypeCombo = new ComboBox<>(
                    FXCollections.observableArrayList("كاش", "بنك", "محفظة", "شيك"));
            payTypeCombo.setValue("كاش"); payTypeCombo.setMaxWidth(Double.MAX_VALUE);

            TextField amountField = new TextField(); amountField.setPromptText("المبلغ");
            amountField.setMaxWidth(Double.MAX_VALUE);
            TextField notesField = new TextField(); notesField.setPromptText("ملاحظة");
            notesField.setMaxWidth(Double.MAX_VALUE);

            Button transferBtn = new Button("تحويل"); transferBtn.getStyleClass().add("btn-primary");
            Label transferMsg = new Label("");

            transferBtn.setOnAction(e -> {
                if (fromCombo.getValue() == null || toCombo.getValue() == null) {
                    transferMsg.setText("اختار الخزنتين"); return;
                }
                if (fromCombo.getValue().equals(toCombo.getValue())) {
                    transferMsg.setText("الخزنتين متماثلتين"); return;
                }
                try {
                    double amount = Double.parseDouble(amountField.getText().trim());
                    int fromId = Integer.parseInt(fromCombo.getValue().split("\\|")[0]);
                    int toId = Integer.parseInt(toCombo.getValue().split("\\|")[0]);
                    String payType = VaultHelper.toPaymentType(payTypeCombo.getValue());
                    String notes = notesField.getText().trim();
                    String fromName = fromCombo.getValue().split("\\|")[1];
                    String toName = toCombo.getValue().split("\\|")[1];

                    transferMsg.setText("جاري التحويل...");
                    AsyncHelper.runVoid(
                            () -> {
                                // خروج من الخزنة الأولى
                                VaultHelper.record(fromId, "out", payType, amount, "تحويل إلى: " + toName, notes, userId);
                                // دخول للخزنة التانية
                                VaultHelper.record(toId, "in", payType, amount, "تحويل من: " + fromName, notes, userId);
                            },
                            () -> MainLayout.loadContent(build(userId, username, role)),
                            error -> transferMsg.setText("حصل خطأ أثناء التحويل"),
                            transferBtn
                    );

                } catch (NumberFormatException ex) { transferMsg.setText("ادخل رقم صحيح"); }
            });

            transferCard.getChildren().addAll(
                    transferTitle,
                    new HBox(8,
                            new VBox(4, new Label("من:"), fromCombo),
                            new VBox(4, new Label("إلى:"), toCombo),
                            new VBox(4, new Label("نوع الدفع:"), payTypeCombo)),
                    new HBox(8,
                            new VBox(4, new Label("المبلغ:"), amountField),
                            new VBox(4, new Label("ملاحظة:"), notesField)),
                    transferBtn, transferMsg);

            content.getChildren().add(transferCard);
        }

        return content;
    }
}
