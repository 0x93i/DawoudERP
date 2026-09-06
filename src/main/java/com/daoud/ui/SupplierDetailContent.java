package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.model.Supplier;
import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierDetailContent {

//    record HistoryRow(String type, String date, String net, String amount, String method, String notes) {}
record HistoryRow(String type, String date, String net, String amount, String pct, String method, String notes) {}

    public static Node build(int userId, String username, String role, Supplier supplier) {

        // ── جدول السجل ──
        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        HBox filtersBox = buildFilters(tableBody, allRows);
        loadRows(allRows, supplier.getId());
        renderTable(tableBody, allRows, null);

        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, supplier.getId());

        Label floorLabel = new Label("الأرضية: " + supplier.getFloorAmount() + " جنيه");
        Label phoneLabel = new Label("التليفون: " + (supplier.getPhone() != null && !supplier.getPhone().isEmpty() ? supplier.getPhone() : "—"));

        // ── تسجيل بضاعة ──
        TextField grossWeightField = new TextField(); grossWeightField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionField = new TextField(); deductionField.setPromptText("خصم الوزن (كيلو)");
        TextField priceField = new TextField(); priceField.setPromptText("سعر الكيلو");

        Label pctLabel = new Label("نسبة الخصم: —");
        Label netWeightLabel = new Label("الوزن الصافي: —");
        Label totalLabel = new Label("الإجمالي: —");

        pctLabel.setStyle("-fx-text-fill: #888780; -fx-font-size: 12px;");
        netWeightLabel.setStyle("-fx-text-fill: #3B6D11; -fx-font-weight: bold; -fx-font-size: 12px;");
        totalLabel.setStyle("-fx-text-fill: #3B6D11; -fx-font-weight: bold; -fx-font-size: 12px;");

        Runnable calc = () -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double ded = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double net = gross - ded;
                double pct = gross > 0 ? (ded / gross) * 100.0 : 0;
                pctLabel.setText(String.format("نسبة الخصم: %.2f%%", pct));
                netWeightLabel.setText(String.format("الوزن الصافي: %.2f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", net * price));
            } catch (NumberFormatException ex) {
                pctLabel.setText("نسبة الخصم: —");
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
            }
        };

        grossWeightField.textProperty().addListener((o,old,n) -> calc.run());
        deductionField.textProperty().addListener((o,old,n) -> calc.run());
        priceField.textProperty().addListener((o,old,n) -> calc.run());

        Button saveDailyBtn = new Button("حفظ البضاعة"); saveDailyBtn.getStyleClass().add("btn-primary");
        Label dailyError = new Label("");

        saveDailyBtn.setOnAction(e -> {
            try {
                double gross = Double.parseDouble(grossWeightField.getText().trim());
                double deduction = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                String sql = "INSERT INTO supplier_transactions (supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) VALUES (?, date('now'), ?, ?, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, supplier.getId()); stmt.setDouble(2, gross);
                    stmt.setDouble(3, deduction); stmt.setDouble(4, price); stmt.setInt(5, userId);
                    stmt.executeUpdate();
                }
                grossWeightField.clear(); deductionField.clear(); priceField.clear();
                pctLabel.setText("نسبة الخصم: —");
                netWeightLabel.setText("الوزن الصافي: —");
                totalLabel.setText("الإجمالي: —");
                dailyError.setText("تم الحفظ ✓");
                refreshBalance(balanceLabel, supplier.getId());
                allRows.clear(); loadRows(allRows, supplier.getId()); renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { dailyError.setText("تأكد من الأرقام");
            } catch (SQLException ex) { dailyError.setText("خطأ: " + ex.getMessage()); }
        });

        // ── سحب فلوس ──
        TextField withdrawField = new TextField(); withdrawField.setPromptText("المبلغ");
        TextField withdrawNotes = new TextField(); withdrawNotes.setPromptText("ملاحظة (اختياري)");
        ComboBox<String> paymentMethodCombo = new ComboBox<>(
                FXCollections.observableArrayList("كاش", "فيزا", "محفظة", "شيك", "تحويل بنكي"));
        paymentMethodCombo.setValue("كاش");
        Button saveWithdrawBtn = new Button("تسجيل السحب"); saveWithdrawBtn.getStyleClass().add("btn-default");
        Label withdrawError = new Label("");

        saveWithdrawBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(withdrawField.getText().trim());
                String method = paymentMethodCombo.getValue();
                String sql = "INSERT INTO supplier_withdrawals (supplier_id, amount, withdrawal_date, payment_method, recorded_by, notes) VALUES (?, ?, date('now'), ?, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, supplier.getId()); stmt.setDouble(2, amount);
                    stmt.setString(3, method); stmt.setInt(4, userId);
                    stmt.setString(5, withdrawNotes.getText().trim());
                    stmt.executeUpdate();
                    // تسجيل في خزنة المخزن
                    int warehouseId = com.daoud.dao.WarehouseDAO.getWarehouseBySupplier(supplier.getId());
                    int treasuryId = VaultHelper.getWarehouseTreasuryId(warehouseId);
                    VaultHelper.record(treasuryId, "out",
                            VaultHelper.toPaymentType(paymentMethodCombo.getValue()),
                            amount, "سحب مورد: " + supplier.getName(), withdrawNotes.getText().trim(), userId);
                }
                withdrawField.clear(); withdrawNotes.clear();
                withdrawError.setText("تم ✓");
                refreshBalance(balanceLabel, supplier.getId());
                allRows.clear(); loadRows(allRows, supplier.getId()); renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { withdrawError.setText("ادخل رقم");
            } catch (SQLException ex) { withdrawError.setText("خطأ: " + ex.getMessage()); }
        });

        // ── تسوية ──
        Button settleBtn = new Button("تسوية أسبوعية"); settleBtn.getStyleClass().add("btn-default");
        settleBtn.setVisible(role.equals("admin")); settleBtn.setManaged(role.equals("admin"));
        settleBtn.setOnAction(e -> {
            double balance = SupplierDAO.getSupplierBalance(supplier.getId());
            TextField paidField = new TextField(); paidField.setPromptText("المبلغ المدفوع");
            TextField newFloorField = new TextField("0"); newFloorField.setPromptText("أرضية جديدة");
            ComboBox<String> methodCombo = new ComboBox<>(
                    FXCollections.observableArrayList("كاش", "فيزا", "محفظة", "شيك", "تحويل بنكي"));
            methodCombo.setValue("كاش");
            Button confirmBtn = new Button("تأكيد"); confirmBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            Label balLbl = new Label(String.format("الرصيد الحالي: %.2f جنيه", balance));

            confirmBtn.setOnAction(ev -> {
                try {
                    double paid = Double.parseDouble(paidField.getText().trim());
                    double newFloor = Double.parseDouble(newFloorField.getText().trim());
                    SupplierDAO.settleSupplier(supplier.getId(), paid, newFloor, userId);
                    refreshBalance(balanceLabel, supplier.getId());
                    floorLabel.setText("الأرضية: " + newFloor + " جنيه");
                    allRows.clear(); loadRows(allRows, supplier.getId()); renderTable(tableBody, allRows, null);
                } catch (NumberFormatException ex) { errLbl.setText("أرقام صحيحة"); }
            });

            VBox dl = new VBox(10, balLbl,
                    new Label("المبلغ المدفوع:"), paidField,
                    new Label("طريقة الدفع:"), methodCombo,
                    new Label("أرضية جديدة:"), newFloorField,
                    confirmBtn, errLbl);
            dl.setPadding(new Insets(20));
            DialogHelper.create("تسوية أسبوعية", dl, 340, 320).show();
        });

        Button backBtn = new Button("← رجوع للموردين"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(SuppliersContent.build(userId, username, role));
            MainLayout.setTitle("الموردين");
        });

        // ── Cards ──
        VBox infoCard = new VBox(6); infoCard.getStyleClass().add("card");
        infoCard.getChildren().addAll(
                new Label("المورد: " + supplier.getName()),
                phoneLabel, floorLabel, balanceLabel);

        VBox purchaseCard = new VBox(8); purchaseCard.getStyleClass().add("card");
        Label purchTitle = new Label("تسجيل بضاعة"); purchTitle.getStyleClass().add("card-title");
        purchaseCard.getChildren().addAll(
                purchTitle,
                new HBox(8,
                        new VBox(4, new Label("الوزن الإجمالي:"), grossWeightField),
                        new VBox(4, new Label("خصم الوزن:"), deductionField),
                        new VBox(4, new Label("سعر الكيلو:"), priceField)),
                new HBox(16, pctLabel, netWeightLabel, totalLabel),
                saveDailyBtn, dailyError);

        grossWeightField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(grossWeightField, Priority.ALWAYS);
        deductionField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(deductionField, Priority.ALWAYS);
        priceField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(priceField, Priority.ALWAYS);

        VBox withdrawCard = new VBox(8); withdrawCard.getStyleClass().add("card");
        Label withTitle = new Label("سحب فلوس"); withTitle.getStyleClass().add("card-title");
        withdrawCard.getChildren().addAll(
                withTitle,
                new HBox(8,
                        new VBox(4, new Label("المبلغ:"), withdrawField),
                        new VBox(4, new Label("طريقة الدفع:"), paymentMethodCombo)),
                new VBox(4, new Label("ملاحظة:"), withdrawNotes),
                saveWithdrawBtn, withdrawError);

        withdrawField.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(withdrawField, Priority.ALWAYS);
        withdrawNotes.setMaxWidth(Double.MAX_VALUE);

        VBox settleCard = new VBox(8); settleCard.getStyleClass().add("card");
        settleCard.getChildren().add(settleBtn);
        settleCard.setVisible(role.equals("admin")); settleCard.setManaged(role.equals("admin"));

        // ── History Card ──
        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        HBox topRow = new HBox(12, purchaseCard, withdrawCard);
        HBox.setHgrow(purchaseCard, Priority.ALWAYS);
        HBox.setHgrow(withdrawCard, Priority.ALWAYS);

        return new VBox(12, backBtn, infoCard, topRow, settleCard, historyCard);
    }

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows) {
        Button allBtn = filterBtn("الكل");
        Button tradeBtn = filterBtn("بضاعة");
        Button withdrawBtn = filterBtn("سحب");

        setActive(allBtn, allBtn, tradeBtn, withdrawBtn);

        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, null); });
        tradeBtn.setOnAction(e -> { setActive(tradeBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, "بضاعة"); });
        withdrawBtn.setOnAction(e -> { setActive(withdrawBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, "سحب"); });

        HBox box = new HBox(8, allBtn, tradeBtn, withdrawBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "الوزن الصافي", "نسبة الخصم", "المبلغ", "طريقة الدفع", "ملاحظة"};
        double[] widths = {95, 70, 100, 95, 100, 100, 130};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<HistoryRow> rows, int supplierId) {
        String sql = """
            SELECT 'بضاعة' as type, transaction_date as date,
                   (gross_weight - deduction_kg) as net,
                   ((gross_weight - deduction_kg) * price_per_kg) as amount,
                   CASE WHEN gross_weight > 0 THEN (deduction_kg / gross_weight * 100) ELSE 0 END as pct,
                   '' as method, '' as notes
            FROM supplier_transactions WHERE supplier_id = ?
            UNION ALL
            SELECT 'سحب' as type, withdrawal_date as date,
                   0 as net, amount, 0 as pct,
                   COALESCE(payment_method,'كاش') as method,
                   COALESCE(notes,'') as notes
            FROM supplier_withdrawals WHERE supplier_id = ?
            ORDER BY date DESC LIMIT 50
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId); stmt.setInt(2, supplierId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                boolean isTrade = rs.getString("type").equals("بضاعة");
                rows.add(new HistoryRow(
                        rs.getString("type"),
                        rs.getString("date"),
                        isTrade ? String.format("%.1f كيلو", rs.getDouble("net")) : "—",
                        String.format("%.0f جنيه", rs.getDouble("amount")),
                        isTrade ? String.format("%.1f%%", rs.getDouble("pct")) : "—",
                        isTrade ? "—" : rs.getString("method"),
                        rs.getString("notes")
                ));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType) {
        table.getChildren().clear();
        double[] widths = {95, 70, 100, 95, 100, 100, 130};
        boolean odd = true;
        boolean hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox();
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") + "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isTrade = row.type().equals("بضاعة");
            String[] vals = {row.date(), row.type(), row.net(), row.amount(), row.method(), row.notes()};

            // type badge
            Label typeLbl = new Label(row.type());
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isTrade ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#E6F1FB; -fx-text-fill: #185FA5") + ";");
            typeLbl.setMinWidth(widths[1]); typeLbl.setPrefWidth(widths[1]);

            // date
            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            r.getChildren().addAll(dateLbl, typeLbl);

            // net weight
            Label netLbl = new Label(row.net());
            netLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            netLbl.setMinWidth(widths[2]); netLbl.setPrefWidth(widths[2]);

            // amount
            Label amtLbl = new Label(row.amount());
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isTrade ? "#1a1a18" : "#3B6D11") + ";");
            amtLbl.setMinWidth(widths[4]); amtLbl.setPrefWidth(widths[4]);

            // method (نسبة الخصم للبضاعة، طريقة الدفع للسحب)
            Label pctLbl = new Label(isTrade ? row.pct() : "—");
            pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            pctLbl.setMinWidth(widths[3]);
            pctLbl.setPrefWidth(widths[3]);

            Label methodLbl = new Label(isTrade ? "—" : row.method());
            methodLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            methodLbl.setMinWidth(widths[5]);
            methodLbl.setPrefWidth(widths[5]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[6]); notesLbl.setPrefWidth(widths[6]);

//            r.getChildren().addAll(netLbl, amtLbl, pctLbl, methodLbl, notesLbl);
            r.getChildren().addAll(
                    netLbl,
                    pctLbl,
                    amtLbl,
                    methodLbl,
                    notesLbl
            );
            table.getChildren().add(r);
        }

        if (!hasRows) {
            Label empty = new Label("مفيش معاملات");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
        }
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private static void setActive(Button active, Button... all) {
        for (Button b : all) {
            if (b == active) {
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            } else {
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
            }
        }
    }

    private static void refreshBalance(Label lbl, int supplierId) {
        double balance = SupplierDAO.getSupplierBalance(supplierId);
        lbl.setText(String.format("الرصيد: %.2f جنيه", balance));
        lbl.getStyleClass().removeAll("label-balance-positive", "label-balance-negative");
        lbl.getStyleClass().add(balance >= 0 ? "label-balance-positive" : "label-balance-negative");
    }
}