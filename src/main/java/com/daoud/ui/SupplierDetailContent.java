package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.db.VaultHelper;
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

    record HistoryRow(String type, int recordId, String date, String net,
                      String amount, String pct, String method, String notes) {}

    public static Node build(int userId, String username, String role, Supplier supplier) {

        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);

        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, supplier.getId());

        Label floorLabel = new Label("الأرضية: " + supplier.getFloorAmount() + " جنيه");
        Label phoneLabel = new Label("التليفون: " + (supplier.getPhone() != null && !supplier.getPhone().isEmpty() ? supplier.getPhone() : "—"));

        loadRows(allRows, supplier.getId());
        HBox filtersBox = buildFilters(tableBody, allRows, supplier.getId(), balanceLabel);
        renderTable(tableBody, allRows, null, supplier.getId(), balanceLabel);

        // ── تسجيل بضاعة ──
        TextField grossWeightField = new TextField(); grossWeightField.setPromptText("الوزن الإجمالي (كيلو)");
        TextField deductionField = new TextField(); deductionField.setPromptText("نسبة الخصم %");
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
                double pct = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
                double dedKg = gross * (pct / 100.0);
                double net = gross - dedKg;
                pctLabel.setText(String.format("كمية الخصم: %.2f كيلو", dedKg));
                netWeightLabel.setText(String.format("الوزن الصافي: %.2f كيلو", net));
                totalLabel.setText(String.format("الإجمالي: %.2f جنيه", net * price));
            } catch (NumberFormatException ex) {
                pctLabel.setText("كمية الخصم: —");
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
                double pct = deductionField.getText().trim().isEmpty() ? 0 : Double.parseDouble(deductionField.getText().trim());
                double price = Double.parseDouble(priceField.getText().trim());
                double deduction = gross * (pct / 100.0);

                String sql = "INSERT INTO supplier_transactions (supplier_id, transaction_date, gross_weight, deduction_kg, price_per_kg, recorded_by) VALUES (?, CURRENT_DATE, ?, ?, ?, ?)";
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
                allRows.clear(); loadRows(allRows, supplier.getId());
                renderTable(tableBody, allRows, null, supplier.getId(), balanceLabel);
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
                String sql = "INSERT INTO supplier_withdrawals (supplier_id, amount, withdrawal_date, payment_method, recorded_by, notes) VALUES (?, ?, CURRENT_DATE, ?, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, supplier.getId()); stmt.setDouble(2, amount);
                    stmt.setString(3, method); stmt.setInt(4, userId);
                    stmt.setString(5, withdrawNotes.getText().trim());
                    stmt.executeUpdate();
                    int warehouseId = com.daoud.dao.WarehouseDAO.getWarehouseBySupplier(supplier.getId());
                    int treasuryId = VaultHelper.getWarehouseTreasuryId(warehouseId);
                    VaultHelper.record(treasuryId, "out",
                            VaultHelper.toPaymentType(paymentMethodCombo.getValue()),
                            amount, "سحب مورد: " + supplier.getName(), withdrawNotes.getText().trim(), userId);
                }
                withdrawField.clear(); withdrawNotes.clear();
                withdrawError.setText("تم ✓");
                refreshBalance(balanceLabel, supplier.getId());
                allRows.clear(); loadRows(allRows, supplier.getId());
                renderTable(tableBody, allRows, null, supplier.getId(), balanceLabel);
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
                    if (paid > 0) {
                        int warehouseId = com.daoud.dao.WarehouseDAO.getWarehouseBySupplier(supplier.getId());
                        int vaultId = com.daoud.db.VaultHelper.getWarehouseTreasuryId(warehouseId);
                        com.daoud.db.VaultHelper.record(vaultId, "out",
                                com.daoud.db.VaultHelper.toPaymentType(methodCombo.getValue()),
                                paid, "تسوية مورد: " + supplier.getName(), "", userId);
                    }
                    refreshBalance(balanceLabel, supplier.getId());
                    floorLabel.setText("الأرضية: " + newFloor + " جنيه");
                    allRows.clear(); loadRows(allRows, supplier.getId());
                    renderTable(tableBody, allRows, null, supplier.getId(), balanceLabel);
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
        infoCard.getChildren().addAll(new Label("المورد: " + supplier.getName()), phoneLabel, floorLabel, balanceLabel);

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

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        HBox topRow = new HBox(12, purchaseCard, withdrawCard);
        HBox.setHgrow(purchaseCard, Priority.ALWAYS);
        HBox.setHgrow(withdrawCard, Priority.ALWAYS);

        return new VBox(12, backBtn, infoCard, topRow, settleCard, historyCard);
    }

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows, int supplierId, Label balanceLabel) {
        Button allBtn = filterBtn("الكل");
        Button tradeBtn = filterBtn("بضاعة");
        Button withdrawBtn = filterBtn("سحب");
        setActive(allBtn, allBtn, tradeBtn, withdrawBtn);
        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, null, supplierId, balanceLabel); });
        tradeBtn.setOnAction(e -> { setActive(tradeBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, "بضاعة", supplierId, balanceLabel); });
        withdrawBtn.setOnAction(e -> { setActive(withdrawBtn, allBtn, tradeBtn, withdrawBtn); renderTable(tableBody, allRows, "سحب", supplierId, balanceLabel); });
        HBox box = new HBox(8, allBtn, tradeBtn, withdrawBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "الوزن الصافي", "نسبة الخصم", "المبلغ", "طريقة الدفع", "ملاحظة", ""};
        double[] widths = {90, 65, 90, 80, 90, 85, 100, 110};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<HistoryRow> rows, int supplierId) {
        String sql1 = "SELECT id, transaction_date, gross_weight, deduction_kg, price_per_kg " +
                "FROM supplier_transactions WHERE supplier_id = ? ORDER BY transaction_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql1)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                double gross = rs.getDouble("gross_weight");
                double ded = rs.getDouble("deduction_kg");
                double price = rs.getDouble("price_per_kg");
                double net = gross - ded;
                double pct = gross > 0 ? (ded / gross * 100) : 0;
                rows.add(new HistoryRow("بضاعة", rs.getInt("id"), rs.getString("transaction_date"),
                        String.format("%.1f كيلو", net), String.format("%.0f جنيه", net * price),
                        String.format("%.1f%%", pct), "—", ""));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        String sql2 = "SELECT id, withdrawal_date, amount, payment_method, notes " +
                "FROM supplier_withdrawals WHERE supplier_id = ? ORDER BY withdrawal_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow("سحب", rs.getInt("id"), rs.getString("withdrawal_date"),
                        "—", String.format("%.0f جنيه", rs.getDouble("amount")),
                        "—", rs.getString("payment_method") != null ? rs.getString("payment_method") : "كاش",
                        rs.getString("notes") != null ? rs.getString("notes") : ""));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        rows.sort((a, b) -> b.date().compareTo(a.date()));
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType,
                                    int supplierId, Label balanceLabel) {
        table.getChildren().clear();
        double[] widths = {90, 65, 90, 80, 90, 85, 100, 55, 55};
        boolean odd = true, hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isTrade = row.type().equals("بضاعة");

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label typeLbl = new Label(row.type());
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isTrade ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#E6F1FB; -fx-text-fill: #185FA5") + ";");
            typeLbl.setMinWidth(widths[1]); typeLbl.setPrefWidth(widths[1]);

            Label netLbl = new Label(row.net());
            netLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            netLbl.setMinWidth(widths[2]); netLbl.setPrefWidth(widths[2]);

            Label pctLbl = new Label(row.pct());
            pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            pctLbl.setMinWidth(widths[3]); pctLbl.setPrefWidth(widths[3]);

            Label amtLbl = new Label(row.amount());
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                    (isTrade ? "#1a1a18" : "#3B6D11") + ";");
            amtLbl.setMinWidth(widths[4]); amtLbl.setPrefWidth(widths[4]);

            Label methodLbl = new Label(isTrade ? "—" : row.method());
            methodLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            methodLbl.setMinWidth(widths[5]); methodLbl.setPrefWidth(widths[5]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[6]); notesLbl.setPrefWidth(widths[6]);

            // زر التعديل
            Button editBtn = new Button("تعديل");
            editBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            editBtn.setMinWidth(widths[7]); editBtn.setPrefWidth(widths[7]);

            // زر الحذف
            Button deleteBtn = new Button("حذف");
            deleteBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            deleteBtn.setMinWidth(widths[8]); deleteBtn.setPrefWidth(widths[8]);

            final HistoryRow finalRow = row;

            // ── التعديل ──
            editBtn.setOnAction(e -> {
                if (finalRow.type().equals("بضاعة")) {
                    // تعديل بضاعة
                    TextField gField = new TextField(); gField.setPromptText("الوزن الإجمالي");
                    TextField dField = new TextField(); dField.setPromptText("خصم الوزن");
                    TextField pField = new TextField(); pField.setPromptText("سعر الكيلو");

                    // جيب القيم الحالية
                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                                 "SELECT gross_weight, deduction_kg, price_per_kg FROM supplier_transactions WHERE id = ?")) {
                        stmt.setInt(1, finalRow.recordId());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            gField.setText(String.valueOf(rs.getDouble("gross_weight")));
                            dField.setText(String.valueOf(rs.getDouble("deduction_kg")));
                            pField.setText(String.valueOf(rs.getDouble("price_per_kg")));
                        }
                    } catch (SQLException ex) { System.err.println(ex.getMessage()); }

                    Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                    Label errLbl = new Label("");

                    saveEdit.setOnAction(ev -> {
                        try {
                            double g = Double.parseDouble(gField.getText().trim());
                            double d = Double.parseDouble(dField.getText().trim());
                            double p = Double.parseDouble(pField.getText().trim());
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(
                                         "UPDATE supplier_transactions SET gross_weight=?, deduction_kg=?, price_per_kg=? WHERE id=?")) {
                                stmt.setDouble(1, g); stmt.setDouble(2, d);
                                stmt.setDouble(3, p); stmt.setInt(4, finalRow.recordId());
                                stmt.executeUpdate();
                            }
                            allRows_refresh(rows, supplierId, table, filterType, balanceLabel);
                            ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                        } catch (NumberFormatException | SQLException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10,
                            new Label("الوزن الإجمالي:"), gField,
                            new Label("خصم الوزن:"), dField,
                            new Label("سعر الكيلو:"), pField,
                            saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل بضاعة", dl, 320, 280).show();

                } else {
                    // تعديل سحب
                    TextField amtField = new TextField(); amtField.setPromptText("المبلغ");
                    ComboBox<String> methodEdit = new ComboBox<>(
                            FXCollections.observableArrayList("كاش", "فيزا", "محفظة", "شيك", "تحويل بنكي"));
                    TextField notesEdit = new TextField(); notesEdit.setPromptText("ملاحظة");

                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                                 "SELECT amount, payment_method, notes FROM supplier_withdrawals WHERE id = ?")) {
                        stmt.setInt(1, finalRow.recordId());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            amtField.setText(String.valueOf(rs.getDouble("amount")));
                            methodEdit.setValue(rs.getString("payment_method"));
                            notesEdit.setText(rs.getString("notes") != null ? rs.getString("notes") : "");
                        }
                    } catch (SQLException ex) { System.err.println(ex.getMessage()); }

                    Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                    Label errLbl = new Label("");

                    saveEdit.setOnAction(ev -> {
                        try {
                            double amt = Double.parseDouble(amtField.getText().trim());
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(
                                         "UPDATE supplier_withdrawals SET amount=?, payment_method=?, notes=? WHERE id=?")) {
                                stmt.setDouble(1, amt);
                                stmt.setString(2, methodEdit.getValue());
                                stmt.setString(3, notesEdit.getText().trim());
                                stmt.setInt(4, finalRow.recordId());
                                stmt.executeUpdate();
                            }
                            allRows_refresh(rows, supplierId, table, filterType, balanceLabel);
                            ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                        } catch (NumberFormatException | SQLException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10,
                            new Label("المبلغ:"), amtField,
                            new Label("طريقة الدفع:"), methodEdit,
                            new Label("ملاحظة:"), notesEdit,
                            saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل سحب", dl, 320, 260).show();
                }
            });

            // ── الحذف ──
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف السجل ده؟");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        String delSql = finalRow.type().equals("بضاعة") ?
                                "DELETE FROM supplier_transactions WHERE id = ?" :
                                "DELETE FROM supplier_withdrawals WHERE id = ?";
                        try (Connection conn = DatabaseManager_online.getConnection();
                             PreparedStatement stmt = conn.prepareStatement(delSql)) {
                            stmt.setInt(1, finalRow.recordId());
                            stmt.executeUpdate();
                            rows.remove(finalRow);
                            renderTable(table, rows, filterType, supplierId, balanceLabel);
                            refreshBalance(balanceLabel, supplierId);
                        } catch (SQLException ex) { System.err.println(ex.getMessage()); }
                    }
                });
            });

            r.getChildren().addAll(dateLbl, typeLbl, netLbl, pctLbl, amtLbl, methodLbl, notesLbl, editBtn, deleteBtn);
            table.getChildren().add(r);
        }

        if (!hasRows) {
            Label empty = new Label("مفيش معاملات");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
        }
    }

    private static void allRows_refresh(List<HistoryRow> rows, int supplierId, VBox table,
                                        String filterType, Label balanceLabel) {
        rows.clear();
        loadRows(rows, supplierId);
        renderTable(table, rows, filterType, supplierId, balanceLabel);
        refreshBalance(balanceLabel, supplierId);
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private static void setActive(Button active, Button... all) {
        for (Button b : all) {
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
        }
    }

    private static void refreshBalance(Label lbl, int supplierId) {
        double balance = SupplierDAO.getSupplierBalance(supplierId);
        lbl.setText(String.format("الرصيد: %.2f جنيه", balance));
        lbl.getStyleClass().removeAll("label-balance-positive", "label-balance-negative");
        lbl.getStyleClass().add(balance >= 0 ? "label-balance-positive" : "label-balance-negative");
    }

}