package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.model.Supplier;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;

import com.daoud.db.DatabaseManager_online;

public class SupplierDetailScreen {

    public static void show(Stage stage, int userId, String username, String role, Supplier supplier) {

        Label title = new Label("حساب المورد: " + supplier.getName());
        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setPrefHeight(150);
        Label floorLabel = new Label("الأرضية: " + supplier.getFloorAmount() + " جنيه");
        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, supplier.getId());

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> SuppliersScreen.show(stage, userId, username, role));

        // ── التسجيل اليومي ──
        Label dailyTitle = new Label("── تسجيل بضاعة جديدة ──");

        TextField grossWeightField = new TextField();
        grossWeightField.setPromptText("الوزن الاجمالي (كيلو)");

        TextField deductionField = new TextField();
        deductionField.setPromptText("خصم الوزن (كيلو)");

        TextField priceField = new TextField();
        priceField.setPromptText("سعر الكيلو");

        Label netWeightLabel = new Label("الوزن الصافي: —");
        Label totalLabel = new Label("الإجمالي: —");

        grossWeightField.textProperty().addListener((obs, o, n) -> updateCalc(grossWeightField, deductionField, priceField, netWeightLabel, totalLabel));
        deductionField.textProperty().addListener((obs, o, n) -> updateCalc(grossWeightField, deductionField, priceField, netWeightLabel, totalLabel));
        priceField.textProperty().addListener((obs, o, n) -> updateCalc(grossWeightField, deductionField, priceField, netWeightLabel, totalLabel));

        Button saveDailyBtn = new Button("حفظ البضاعة");
        Label dailyError = new Label("");

        saveDailyBtn.setOnAction(e -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double deduction = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                double netWeight = gross - deduction;

                Connection conn = DatabaseManager_online.getConnection();

                // 1. سجل معاملة المورد
                String sql = "INSERT INTO supplier_transactions (supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, supplier.getId());
                stmt.setDouble(2, gross);
                stmt.setDouble(3, deduction);
                stmt.setDouble(4, price);
                stmt.setInt(5, userId);
                stmt.executeUpdate();

                // 2. سجل دخول البضاعة في المخزن تلقائي
                String warehouseSql = """
            SELECT warehouse_id FROM warehouse_suppliers WHERE supplier_id = ? LIMIT 1
        """;
                PreparedStatement ws = conn.prepareStatement(warehouseSql);
                ws.setInt(1, supplier.getId());
                ResultSet wrs = ws.executeQuery();
                if (wrs.next()) {
                    int warehouseId = wrs.getInt("warehouse_id");
                    String entrySql = """
                INSERT INTO warehouse_stock_entries 
                (warehouse_id, supplier_id, entry_date, total_weight, recorded_by)
                VALUES (?, ?, CURRENT_DATE, ?, ?)
            """;
                    PreparedStatement es = conn.prepareStatement(entrySql);
                    es.setInt(1, warehouseId);
                    es.setInt(2, supplier.getId());
                    es.setDouble(3, netWeight);
                    es.setInt(4, userId);
                    es.executeUpdate();
                }

                grossWeightField.clear();
                deductionField.clear();
                priceField.clear();
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
                dailyError.setText("تم الحفظ ✓");
                refreshBalance(balanceLabel, supplier.getId());
                refreshHistory(historyArea, supplier.getId());

            } catch (NumberFormatException ex) {
                dailyError.setText("تأكد من الأرقام");
            } catch (SQLException ex) {
                dailyError.setText("خطأ: " + ex.getMessage());
            }
        });

        // ── السحب ──
        Label withdrawTitle = new Label("── سحب فلوس ──");

        TextField withdrawField = new TextField();
        withdrawField.setPromptText("المبلغ");

        TextField withdrawNotes = new TextField();
        withdrawNotes.setPromptText("ملاحظة (اختياري)");

        Button saveWithdrawBtn = new Button("تسجيل السحب");
        Label withdrawError = new Label("");

        saveWithdrawBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(withdrawField.getText().trim());
                String sql = "INSERT INTO supplier_withdrawals (supplier_id, amount, withdrawal_date, recorded_by, notes) VALUES (?, ?, date('now'), ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, supplier.getId());
                    stmt.setDouble(2, amount);
                    stmt.setInt(3, userId);
                    stmt.setString(4, withdrawNotes.getText().trim());
                    stmt.executeUpdate();
                }
                withdrawField.clear();
                withdrawNotes.clear();
                withdrawError.setText("تم التسجيل");
                refreshBalance(balanceLabel, supplier.getId());
                refreshHistory(historyArea, supplier.getId());
            } catch (NumberFormatException ex) {
                withdrawError.setText("ادخل رقم صحيح");
            } catch (SQLException ex) {
                withdrawError.setText("خطأ: " + ex.getMessage());
            }
        });

        // ── التسوية الأسبوعية ──
        Label settleTitle = new Label("── تسوية أسبوعية ──");
        Button settleBtn = new Button("عمل تسوية");
        // زر التسوية لازم يتخفى لو مش أدمن
        settleTitle.setVisible(role.equals("admin"));
        settleTitle.setManaged(role.equals("admin"));
        settleBtn.setVisible(role.equals("admin"));
        settleBtn.setManaged(role.equals("admin"));

        settleBtn.setOnAction(e -> {
            double currentBalance = SupplierDAO.getSupplierBalance(supplier.getId());

            Stage dialog = new Stage();
            dialog.setTitle("تسوية أسبوعية");

            VBox dLayout = new VBox(12);
            dLayout.setPadding(new Insets(20));

            Label errLbl = new Label("");
            Button confirmBtn = new Button("تأكيد التسوية");

            if (currentBalance >= 0) {
                // عم داود له فلوس عند المورد → يسيبها أرضية
                Label balLbl = new Label(String.format("الرصيد: %.2f جنيه — المورد مدين لعم داود", currentBalance));
                Label infoLbl = new Label("الرصيد هيتحول أرضية للأسبوع الجاي");

                confirmBtn.setOnAction(ev -> {
                    SupplierDAO.settleSupplier(supplier.getId(), 0, currentBalance, userId);
                    refreshBalance(balanceLabel, supplier.getId());
                    refreshHistory(historyArea, supplier.getId());
                    floorLabel.setText(String.format("الأرضية: %.2f جنيه", currentBalance));
                    dialog.close();
                });

                dLayout.getChildren().addAll(balLbl, infoLbl, confirmBtn, errLbl);

            } else {
                // المورد له فلوس عند عم داود → عم داود يدفعله
                double owedToSupplier = Math.abs(currentBalance);
                Label balLbl = new Label(String.format("المطلوب دفعه للمورد: %.2f جنيه", owedToSupplier));

                TextField paidField = new TextField(String.valueOf(owedToSupplier));
                paidField.setPromptText("المبلغ اللي هتدفعه");

                TextField newFloorField = new TextField("0");
                newFloorField.setPromptText("أرضية جديدة (اختياري)");

                Label summaryLbl = new Label();

                Runnable updateSummary = () -> {
                    try {
                        double paid = Double.parseDouble(paidField.getText().trim());
                        double newFloor = Double.parseDouble(newFloorField.getText().trim());
                        summaryLbl.setText(String.format("هتدفع: %.2f | أرضية جديدة: %.2f جنيه", paid, newFloor));
                    } catch (NumberFormatException ex) {
                        summaryLbl.setText("تأكد من الأرقام");
                    }
                };

                paidField.textProperty().addListener((obs, o, n) -> updateSummary.run());
                newFloorField.textProperty().addListener((obs, o, n) -> updateSummary.run());
                updateSummary.run();

                confirmBtn.setOnAction(ev -> {
                    try {
                        double paid = Double.parseDouble(paidField.getText().trim());
                        double newFloor = Double.parseDouble(newFloorField.getText().trim());
                        SupplierDAO.settleSupplier(supplier.getId(), paid, newFloor, userId);
                        refreshBalance(balanceLabel, supplier.getId());
                        refreshHistory(historyArea, supplier.getId());
                        floorLabel.setText(String.format("الأرضية: %.2f جنيه", newFloor));
                        dialog.close();
                    } catch (NumberFormatException ex) {
                        errLbl.setText("ادخل أرقام صحيحة");
                    }
                });

                dLayout.getChildren().addAll(
                        balLbl,
                        new Label("المبلغ المدفوع:"), paidField,
                        new Label("أرضية جديدة:"), newFloorField,
                        summaryLbl, confirmBtn, errLbl
                );
            }

            dialog.setScene(new Scene(dLayout, 350, 300));
            dialog.show();
        });

        // ── تاريخ المعاملات ──
        Label historyTitle = new Label("── آخر المعاملات ──");
        refreshHistory(historyArea, supplier.getId());

        VBox layout = new VBox(8,
                title, floorLabel, balanceLabel,
                new Separator(),
                dailyTitle,
                new Label("الوزن الاجمالي:"), grossWeightField,
                new Label("خصم الوزن:"), deductionField,
                new Label("سعر الكيلو:"), priceField,
                netWeightLabel, totalLabel,
                saveDailyBtn, dailyError,
                new Separator(),
                withdrawTitle,
                new Label("المبلغ:"), withdrawField,
                new Label("ملاحظة:"), withdrawNotes,
                saveWithdrawBtn, withdrawError,
                new Separator(),
                settleTitle, settleBtn,
                new Separator(),
                historyTitle, historyArea,
                backBtn
        );

        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);

        Scene scene = new Scene(scroll, 800, 600);
        stage.setScene(scene);
    }

    private static void updateCalc(TextField grossF, TextField dedF, TextField priceF,
                                   Label netLbl, Label totalLbl) {
        try {
            double gross = Double.parseDouble(grossF.getText().trim());
            double ded = dedF.getText().trim().isEmpty() ? 0 : Double.parseDouble(dedF.getText().trim());
            double price = Double.parseDouble(priceF.getText().trim());
            double net = gross - ded;
            double total = net * price;
            netLbl.setText(String.format("الوزن الصافي: %.2f كيلو", net));
            totalLbl.setText(String.format("الإجمالي: %.2f جنيه", total));
        } catch (NumberFormatException e) {
            netLbl.setText("الوزن الصافي: —");
            totalLbl.setText("الإجمالي: —");
        }
    }

    private static void refreshBalance(Label lbl, int supplierId) {
        double balance = SupplierDAO.getSupplierBalance(supplierId);
        lbl.setText(String.format("الرصيد الحالي: %.2f جنيه", balance));
    }

    private static void refreshHistory(TextArea area, int supplierId) {
        StringBuilder sb = new StringBuilder();
        String sql = """
            SELECT 'بضاعة' as type, transaction_date as date,
                   (gross_weight - deduction_kg) as net,
                   ((gross_weight - deduction_kg) * price_per_kg) as amount
            FROM supplier_transactions WHERE supplier_id = ?
            UNION ALL
            SELECT 'سحب' as type, withdrawal_date as date, 0, amount
            FROM supplier_withdrawals WHERE supplier_id = ?
            UNION ALL
            SELECT 'تسوية' as type, settlement_date as date, 0, amount_paid
            FROM supplier_settlements WHERE supplier_id = ?
            ORDER BY date DESC LIMIT 30
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            stmt.setInt(2, supplierId);
            stmt.setInt(3, supplierId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type = rs.getString("type");
                String date = rs.getString("date");
                if (type.equals("بضاعة")) {
                    sb.append(String.format("%s | بضاعة | صافي: %.1f كيلو | %.1f جنيه%n",
                            date, rs.getDouble("net"), rs.getDouble("amount")));
                } else if (type.equals("سحب")) {
                    sb.append(String.format("%s | سحب | %.1f جنيه%n",
                            date, rs.getDouble("amount")));
                } else {
                    sb.append(String.format("%s | تسوية | دُفع: %.1f جنيه%n",
                            date, rs.getDouble("amount")));
                }
            }
        } catch (SQLException e) {
            sb.append("خطأ في تحميل التاريخ");
        }
        area.setText(sb.toString());
    }
}