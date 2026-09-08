package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.db.VaultHelper;
import com.daoud.model.Factory;
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

public class FactoryDetailContent {

    record HistoryRow(String type, int recordId, String date, String netWeight, String pct, String amount, String notes) {}

    public static Node build(int userId, String username, String role, Factory factory) {

        Label balanceLabel = new Label();
        refreshBalance(balanceLabel, factory.getId());

        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        HBox filtersBox = buildFilters(tableBody, allRows, factory.getId(), balanceLabel);
        loadRows(allRows, factory.getId());
        renderTable(tableBody, allRows, null, factory.getId(), balanceLabel);

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
                allRows.clear(); loadRows(allRows, factory.getId());
                renderTable(tableBody, allRows, null, factory.getId(), balanceLabel);
            } catch (NumberFormatException ex) { shipMsg.setText("تأكد من الأرقام"); }
        });

        // ── دفعة من المصنع ──
        TextField payAmount = new TextField(); payAmount.setPromptText("المبلغ");
        TextField payNotes = new TextField(); payNotes.setPromptText("ملاحظة");

        ComboBox<String> payMethodCombo = new ComboBox<>();
        payMethodCombo.getItems().addAll("كاش", "بنك", "محفظة", "شيك");
        payMethodCombo.setValue("كاش");
        payMethodCombo.setMaxWidth(Double.MAX_VALUE);

        Button savePayBtn = new Button("تسجيل الدفعة"); savePayBtn.getStyleClass().add("btn-default");
        Label payMsg = new Label("");

        savePayBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(payAmount.getText().trim());
                FactoryDAO.addPayment(factory.getId(), amount, payNotes.getText().trim(), userId);
                VaultHelper.record(VaultHelper.getAdminTreasuryId(), "in",
                        VaultHelper.toPaymentType(payMethodCombo.getValue()),
                        amount, "دفعة من مصنع: " + factory.getName(),
                        payNotes.getText().trim(), userId);
                payAmount.clear(); payNotes.clear();
                payMsg.setText("تم ✓");
                refreshBalance(balanceLabel, factory.getId());
                allRows.clear(); loadRows(allRows, factory.getId());
                renderTable(tableBody, allRows, null, factory.getId(), balanceLabel);
            } catch (NumberFormatException ex) { payMsg.setText("ادخل رقم"); }
        });

        Button backBtn = new Button("← رجوع للمصانع"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(FactoriesContent.build(userId, username, role));
            MainLayout.setTitle("المصانع");
        });

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
                        new VBox(4, new Label("طريقة الدفع:"), payMethodCombo)),
                new VBox(4, new Label("ملاحظة:"), payNotes),
                savePayBtn, payMsg);

        payAmount.setMaxWidth(Double.MAX_VALUE);
        payNotes.setMaxWidth(Double.MAX_VALUE);

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        return new VBox(12, backBtn, balanceCard, shipCard, payCard, historyCard);
    }

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows, int factoryId, Label balanceLabel) {
        Button allBtn = filterBtn("الكل");
        Button shipBtn = filterBtn("شحنات");
        Button payBtn = filterBtn("دفعات");
        setActiveFilter(allBtn, allBtn, shipBtn, payBtn);
        allBtn.setOnAction(e -> { setActiveFilter(allBtn, allBtn, shipBtn, payBtn); renderTable(tableBody, allRows, null, factoryId, balanceLabel); });
        shipBtn.setOnAction(e -> { setActiveFilter(shipBtn, allBtn, shipBtn, payBtn); renderTable(tableBody, allRows, "شحنة", factoryId, balanceLabel); });
        payBtn.setOnAction(e -> { setActiveFilter(payBtn, allBtn, shipBtn, payBtn); renderTable(tableBody, allRows, "دفعة", factoryId, balanceLabel); });
        HBox box = new HBox(8, allBtn, shipBtn, payBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "الوزن الصافي", "نسبة الخصم", "المبلغ", "ملاحظة", ""};
        double[] widths = {95, 75, 105, 95, 100, 130, 110};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<HistoryRow> rows, int factoryId) {
        // شحنات
        String sql1 = "SELECT id, shipment_date, net_weight, deduction_pct, total_amount, COALESCE(supplier_name,'') as notes " +
                "FROM factory_shipments WHERE factory_id = ? ORDER BY shipment_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql1)) {
            stmt.setInt(1, factoryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow("شحنة", rs.getInt("id"), rs.getString("shipment_date"),
                        String.format("%.1f كيلو", rs.getDouble("net_weight")),
                        String.format("%.1f%%", rs.getDouble("deduction_pct")),
                        String.format("%.0f جنيه", rs.getDouble("total_amount")),
                        rs.getString("notes")));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        // دفعات
        String sql2 = "SELECT id, payment_date, amount, COALESCE(notes,'') as notes " +
                "FROM factory_payments WHERE factory_id = ? ORDER BY payment_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
            stmt.setInt(1, factoryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow("دفعة", rs.getInt("id"), rs.getString("payment_date"),
                        "—", "—",
                        String.format("%.0f جنيه", rs.getDouble("amount")),
                        rs.getString("notes")));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        rows.sort((a, b) -> b.date().compareTo(a.date()));
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType,
                                    int factoryId, Label balanceLabel) {
        table.getChildren().clear();
        double[] widths = {95, 75, 105, 95, 100, 130, 55, 55};
        boolean odd = true, hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isShip = row.type().equals("شحنة");

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label typeLbl = new Label(row.type());
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isShip ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#E6F1FB; -fx-text-fill: #185FA5") + ";");
            typeLbl.setMinWidth(widths[1]); typeLbl.setPrefWidth(widths[1]);

            Label netLbl = new Label(row.netWeight());
            netLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            netLbl.setMinWidth(widths[2]); netLbl.setPrefWidth(widths[2]);

            Label pctLbl = new Label(row.pct());
            pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            pctLbl.setMinWidth(widths[3]); pctLbl.setPrefWidth(widths[3]);

            Label amtLbl = new Label(row.amount());
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                    (isShip ? "#1a1a18" : "#3B6D11") + ";");
            amtLbl.setMinWidth(widths[4]); amtLbl.setPrefWidth(widths[4]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[5]); notesLbl.setPrefWidth(widths[5]);

            // زر التعديل
            Button editBtn = new Button("تعديل");
            editBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            editBtn.setMinWidth(widths[6]); editBtn.setPrefWidth(widths[6]);

            // زر الحذف
            Button deleteBtn = new Button("حذف");
            deleteBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            deleteBtn.setMinWidth(widths[7]); deleteBtn.setPrefWidth(widths[7]);

            final HistoryRow finalRow = row;

            // التعديل
            editBtn.setOnAction(e -> {
                if (finalRow.type().equals("شحنة")) {
                    TextField gf = new TextField(); gf.setPromptText("الوزن الإجمالي");
                    TextField df = new TextField(); df.setPromptText("كمية الخصم");
                    TextField pf = new TextField(); pf.setPromptText("سعر الكيلو");
                    TextField sf = new TextField(); sf.setPromptText("اسم المورد");

                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                                 "SELECT gross_weight, deduction_kg, price_per_kg, supplier_name FROM factory_shipments WHERE id = ?")) {
                        stmt.setInt(1, finalRow.recordId());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            gf.setText(String.valueOf(rs.getDouble("gross_weight")));
                            df.setText(String.valueOf(rs.getDouble("deduction_kg")));
                            pf.setText(String.valueOf(rs.getDouble("price_per_kg")));
                            sf.setText(rs.getString("supplier_name") != null ? rs.getString("supplier_name") : "");
                        }
                    } catch (SQLException ex) { System.err.println(ex.getMessage()); }

                    Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                    Label errLbl = new Label("");

                    saveEdit.setOnAction(ev -> {
                        try {
                            double g = Double.parseDouble(gf.getText().trim());
                            double d = Double.parseDouble(df.getText().trim());
                            double p = Double.parseDouble(pf.getText().trim());
                            double net = g - d;
                            double pct = g > 0 ? (d / g * 100) : 0;
                            double total = net * p;
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(
                                         "UPDATE factory_shipments SET gross_weight=?, deduction_kg=?, net_weight=?, deduction_pct=?, price_per_kg=?, total_amount=?, supplier_name=? WHERE id=?")) {
                                stmt.setDouble(1, g); stmt.setDouble(2, d);
                                stmt.setDouble(3, net); stmt.setDouble(4, pct);
                                stmt.setDouble(5, p); stmt.setDouble(6, total);
                                stmt.setString(7, sf.getText().trim());
                                stmt.setInt(8, finalRow.recordId());
                                stmt.executeUpdate();
                            }
                            rows.clear(); loadRows(rows, factoryId);
                            renderTable(table, rows, filterType, factoryId, balanceLabel);
                            refreshBalance(balanceLabel, factoryId);
                            ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                        } catch (NumberFormatException | SQLException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10,
                            new Label("الوزن الإجمالي:"), gf,
                            new Label("كمية الخصم:"), df,
                            new Label("سعر الكيلو:"), pf,
                            new Label("المورد:"), sf,
                            saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل شحنة", dl, 320, 320).show();

                } else {
                    TextField amtEdit = new TextField(); amtEdit.setPromptText("المبلغ");
                    TextField notesEdit = new TextField(); notesEdit.setPromptText("ملاحظة");

                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(
                                 "SELECT amount, notes FROM factory_payments WHERE id = ?")) {
                        stmt.setInt(1, finalRow.recordId());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            amtEdit.setText(String.valueOf(rs.getDouble("amount")));
                            notesEdit.setText(rs.getString("notes") != null ? rs.getString("notes") : "");
                        }
                    } catch (SQLException ex) { System.err.println(ex.getMessage()); }

                    Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                    Label errLbl = new Label("");

                    saveEdit.setOnAction(ev -> {
                        try {
                            double amt = Double.parseDouble(amtEdit.getText().trim());
                            try (Connection conn = DatabaseManager_online.getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(
                                         "UPDATE factory_payments SET amount=?, notes=? WHERE id=?")) {
                                stmt.setDouble(1, amt);
                                stmt.setString(2, notesEdit.getText().trim());
                                stmt.setInt(3, finalRow.recordId());
                                stmt.executeUpdate();
                            }
                            rows.clear(); loadRows(rows, factoryId);
                            renderTable(table, rows, filterType, factoryId, balanceLabel);
                            refreshBalance(balanceLabel, factoryId);
                            ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                        } catch (NumberFormatException | SQLException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10,
                            new Label("المبلغ:"), amtEdit,
                            new Label("ملاحظة:"), notesEdit,
                            saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل دفعة", dl, 320, 220).show();
                }
            });

            // الحذف
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف السجل ده؟");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        String delSql = finalRow.type().equals("شحنة") ?
                                "DELETE FROM factory_shipments WHERE id = ?" :
                                "DELETE FROM factory_payments WHERE id = ?";
                        try (Connection conn = DatabaseManager_online.getConnection();
                             PreparedStatement stmt = conn.prepareStatement(delSql)) {
                            stmt.setInt(1, finalRow.recordId());
                            stmt.executeUpdate();
                            rows.remove(finalRow);
                            renderTable(table, rows, filterType, factoryId, balanceLabel);
                            refreshBalance(balanceLabel, factoryId);
                        } catch (SQLException ex) { System.err.println(ex.getMessage()); }
                    }
                });
            });

            r.getChildren().addAll(dateLbl, typeLbl, netLbl, pctLbl, amtLbl, notesLbl, editBtn, deleteBtn);
            table.getChildren().add(r);
        }

        if (!hasRows) {
            Label empty = new Label("مفيش بيانات");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
        }
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private static void setActiveFilter(Button active, Button... all) {
        for (Button b : all) {
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
        }
    }

    private static void refreshBalance(Label lbl, int factoryId) {
        double balance = FactoryDAO.getFactoryBalance(factoryId);
        lbl.setText(String.format(balance >= 0 ? "المصنع مدين لعم داود: %.2f جنيه" : "عم داود مدين للمصنع: %.2f جنيه", Math.abs(balance)));
        lbl.getStyleClass().removeAll("label-balance-positive", "label-balance-negative");
        lbl.getStyleClass().add(balance >= 0 ? "label-balance-positive" : "label-balance-negative");
    }
}