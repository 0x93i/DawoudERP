package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.model.Factory;
import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;

public class FactoryDetailScreen {

    public static void show(Stage stage, int userId, String username, String role, Factory factory) {

        // ── التاريخ (بيتعرف أول) ──
        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setPrefHeight(200);

        Label title = new Label("حساب المصنع: " + factory.getName());
        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, factory.getId());
        refreshHistory(historyArea, factory.getId());

        // ── تسجيل شحنة ──
        Label shipTitle = new Label("── تسجيل شحنة جديدة ──");

        TextField supplierNameField = new TextField();
        supplierNameField.setPromptText("اسم المورد (اختياري)");

        TextField grossWeightField = new TextField();
        grossWeightField.setPromptText("الوزن الإجمالي (كيلو)");

        TextField deductionPctField = new TextField();
        deductionPctField.setPromptText("نسبة الخصم %");

        TextField priceField = new TextField();
        priceField.setPromptText("سعر الكيلو");

        Label deductionKgLabel = new Label("كمية الخصم: —");
        Label netWeightLabel = new Label("الوزن الصافي: —");
        Label totalLabel = new Label("الإجمالي: —");

        Runnable calcShipment = () -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double pct = deductionPctField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionPctField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double deductionKg = gross * (pct / 100.0);
                double net = gross - deductionKg;
                double total = net * price;
                deductionKgLabel.setText(String.format("كمية الخصم: %.2f كيلو", deductionKg));
                netWeightLabel.setText(String.format("الوزن الصافي: %.2f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", total));
            } catch (NumberFormatException ex) {
                deductionKgLabel.setText("كمية الخصم: —");
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
            }
        };

        grossWeightField.textProperty().addListener((o, old, n) -> calcShipment.run());
        deductionPctField.textProperty().addListener((o, old, n) -> calcShipment.run());
        priceField.textProperty().addListener((o, old, n) -> calcShipment.run());

        Button saveShipBtn = new Button("تسجيل الشحنة");
        Label shipMsg = new Label("");

        saveShipBtn.setOnAction(e -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double pct = deductionPctField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionPctField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                FactoryDAO.addShipment(factory.getId(), supplierNameField.getText().trim(), gross, pct, price, userId);
                grossWeightField.clear();
                deductionPctField.clear();
                priceField.clear();
                supplierNameField.clear();
                shipMsg.setText("تم التسجيل ✓");
                refreshBalance(balanceLabel, factory.getId());
                refreshHistory(historyArea, factory.getId());
            } catch (NumberFormatException ex) {
                shipMsg.setText("تأكد من الأرقام");
            }
        });

        // ── دفعة من المصنع ──
        Label payTitle = new Label("── استلام دفعة من المصنع ──");

        TextField payAmount = new TextField();
        payAmount.setPromptText("المبلغ");

        TextField payNotes = new TextField();
        payNotes.setPromptText("ملاحظة (اختياري)");

        Button savePayBtn = new Button("تسجيل الدفعة");
        Label payMsg = new Label("");

        savePayBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(payAmount.getText().trim());
                FactoryDAO.addPayment(factory.getId(), amount, payNotes.getText().trim(), userId);
                payAmount.clear();
                payNotes.clear();
                payMsg.setText("تم التسجيل ✓");
                refreshBalance(balanceLabel, factory.getId());
                refreshHistory(historyArea, factory.getId());
            } catch (NumberFormatException ex) {
                payMsg.setText("ادخل رقم صحيح");
            }
        });

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> FactoriesScreen.show(stage, userId, username, role));

        VBox layout = new VBox(8,
                title, balanceLabel,
                new Separator(),
                shipTitle,
                new Label("اسم المورد:"), supplierNameField,
                new Label("الوزن الإجمالي:"), grossWeightField,
                new Label("نسبة الخصم %:"), deductionPctField,
                new Label("سعر الكيلو:"), priceField,
                deductionKgLabel, netWeightLabel, totalLabel,
                saveShipBtn, shipMsg,
                new Separator(),
                payTitle,
                new Label("المبلغ:"), payAmount,
                new Label("ملاحظة:"), payNotes,
                savePayBtn, payMsg,
                new Separator(),
                new Label("── السجل ──"), historyArea,
                backBtn
        );
        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll, 800, 600));
    }

    private static void refreshBalance(Label lbl, int factoryId) {
        double balance = FactoryDAO.getFactoryBalance(factoryId);
        if (balance >= 0) {
            lbl.setText(String.format("المصنع مدين لعم داود: %.2f جنيه", balance));
        } else {
            lbl.setText(String.format("عم داود مدين للمصنع: %.2f جنيه", Math.abs(balance)));
        }
    }

    private static void refreshHistory(TextArea area, int factoryId) {
        StringBuilder sb = new StringBuilder();
        String sql = """
            SELECT 'شحنة' as type, shipment_date as date, total_amount as amount,
                   supplier_name as notes, net_weight, deduction_pct
            FROM factory_shipments WHERE factory_id = ?
            UNION ALL
            SELECT 'دفعة' as type, payment_date as date, amount,
                   COALESCE(notes,'') as notes, 0, 0
            FROM factory_payments WHERE factory_id = ?
            ORDER BY date DESC LIMIT 30
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, factoryId);
            stmt.setInt(2, factoryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type = rs.getString("type");
                if (type.equals("شحنة")) {
                    sb.append(String.format("%s | شحنة | صافي: %.1f كيلو | خصم: %.1f%% | %.1f جنيه%s%n",
                            rs.getString("date"),
                            rs.getDouble("net_weight"),
                            rs.getDouble("deduction_pct"),
                            rs.getDouble("amount"),
                            rs.getString("notes").isEmpty() ? "" : " | " + rs.getString("notes")));
                } else {
                    sb.append(String.format("%s | دفعة | %.1f جنيه%s%n",
                            rs.getString("date"),
                            rs.getDouble("amount"),
                            rs.getString("notes").isEmpty() ? "" : " | " + rs.getString("notes")));
                }
            }
        } catch (SQLException e) {
            sb.append("خطأ في تحميل السجل");
        }
        area.setText(sb.toString());
    }
}