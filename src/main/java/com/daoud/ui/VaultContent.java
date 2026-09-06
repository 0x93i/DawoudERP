package com.daoud.ui;

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

public class VaultContent {

    record VaultRow(int id, String date, String direction, String paymentType,
                    double amount, String category, String notes) {}

    public static Node build(int userId, String username, String role, int treasuryId, String treasuryName) {

        // ── ملخص الخزنة ──
        VBox summaryCard = new VBox(10);
        summaryCard.getStyleClass().add("card");
        refreshSummary(summaryCard, treasuryId, treasuryName);

        // ── إضافة معاملة ──
        ComboBox<String> directionCombo = new ComboBox<>(
                FXCollections.observableArrayList("دخول", "خروج"));
        directionCombo.setPromptText("دخول / خروج");
        directionCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> paymentCombo = new ComboBox<>(
                FXCollections.observableArrayList("كاش", "بنك", "محفظة", "شيك"));
        paymentCombo.setPromptText("نوع الدفع");
        paymentCombo.setMaxWidth(Double.MAX_VALUE);

        TextField amountField = new TextField(); amountField.setPromptText("المبلغ");
        amountField.setMaxWidth(Double.MAX_VALUE);
        TextField categoryField = new TextField(); categoryField.setPromptText("التصنيف (مثلاً: راتب، مشتريات)");
        categoryField.setMaxWidth(Double.MAX_VALUE);
        TextField notesField = new TextField(); notesField.setPromptText("ملاحظة");
        notesField.setMaxWidth(Double.MAX_VALUE);

        Button saveBtn = new Button("تسجيل"); saveBtn.getStyleClass().add("btn-primary");
        Label saveMsg = new Label("");

        // جدول السجل
        List<VaultRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        HBox filtersBox = buildFilters(tableBody, allRows);
        loadRows(allRows, treasuryId);
        renderTable(tableBody, allRows, null);

        saveBtn.setOnAction(e -> {
            if (directionCombo.getValue() == null) { saveMsg.setText("اختار دخول/خروج"); return; }
            if (paymentCombo.getValue() == null) { saveMsg.setText("اختار نوع الدفع"); return; }
            try {
                double amount = Double.parseDouble(amountField.getText().trim());
                String direction = directionCombo.getValue().equals("دخول") ? "in" : "out";
                String paymentType = switch (paymentCombo.getValue()) {
                    case "بنك" -> "bank";
                    case "محفظة" -> "wallet";
                    case "شيك" -> "check";
                    default -> "cash";
                };

                String sql = "INSERT INTO vault_transactions " +
                        "(vault_id, transaction_date, direction, payment_type, amount, category, notes, recorded_by) " +
                        "VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?)";
                try (Connection conn = DatabaseManager_online.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, treasuryId);
                    stmt.setString(2, direction);
                    stmt.setString(3, paymentType);
                    stmt.setDouble(4, amount);
                    stmt.setString(5, categoryField.getText().trim());
                    stmt.setString(6, notesField.getText().trim());
                    stmt.setInt(7, userId);
                    stmt.executeUpdate();
                }

                amountField.clear(); categoryField.clear(); notesField.clear();
                directionCombo.setValue(null); paymentCombo.setValue(null);
                saveMsg.setText("تم ✓");
                refreshSummary(summaryCard, treasuryId, treasuryName);
                allRows.clear(); loadRows(allRows, treasuryId); renderTable(tableBody, allRows, null);

            } catch (NumberFormatException ex) { saveMsg.setText("ادخل رقم صحيح");
            } catch (SQLException ex) { saveMsg.setText("خطأ: " + ex.getMessage()); }
        });

        VBox addCard = new VBox(10); addCard.getStyleClass().add("card");
        Label addTitle = new Label("تسجيل معاملة"); addTitle.getStyleClass().add("card-title");
        addCard.getChildren().addAll(
                addTitle,
                new HBox(8,
                        new VBox(4, new Label("الاتجاه:"), directionCombo),
                        new VBox(4, new Label("نوع الدفع:"), paymentCombo)),
                new HBox(8,
                        new VBox(4, new Label("المبلغ:"), amountField),
                        new VBox(4, new Label("التصنيف:"), categoryField)),
                new VBox(4, new Label("ملاحظة:"), notesField),
                saveBtn, saveMsg);

        HBox.setHgrow(directionCombo, Priority.ALWAYS);
        HBox.setHgrow(paymentCombo, Priority.ALWAYS);
        HBox.setHgrow(amountField, Priority.ALWAYS);
        HBox.setHgrow(categoryField, Priority.ALWAYS);

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        return new VBox(12, summaryCard, addCard, historyCard);
    }

    private static void refreshSummary(VBox card, int treasuryId, String name) {
        card.getChildren().clear();
        Label title = new Label("خزنة: " + name); title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        try (Connection conn = DatabaseManager_online.getConnection()) {
            // إجمالي لكل نوع
            String[] types = {"cash", "bank", "wallet", "check"};
            String[] typeNames = {"كاش", "بنك", "محفظة", "شيك"};
            String[] colors = {"#3B6D11", "#185FA5", "#854F0B", "#5f5e5a"};

            double grandTotal = 0;
            HBox typesRow = new HBox(12);

            for (int i = 0; i < types.length; i++) {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE -amount END), 0) " +
                                "FROM vault_transactions WHERE vault_id = ? AND payment_type = ?");
                ps.setInt(1, treasuryId); ps.setString(2, types[i]);
                ResultSet rs = ps.executeQuery();
                double bal = rs.next() ? rs.getDouble(1) : 0;
                grandTotal += bal;

                Label nameLbl = new Label(typeNames[i]);
                nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");
                Label valLbl = new Label(String.format("%.0f جنيه", bal));
                valLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " +
                        (bal >= 0 ? colors[i] : "#A32D2D") + ";");
                VBox box = new VBox(2, nameLbl, valLbl);
                box.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 10; -fx-background-radius: 8;");
                box.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(box, Priority.ALWAYS);
                typesRow.getChildren().add(box);
            }

            Label totalLbl = new Label(String.format("الإجمالي: %.0f جنيه", grandTotal));
            totalLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " +
                    (grandTotal >= 0 ? "#3B6D11" : "#A32D2D") + ";");

            card.getChildren().addAll(totalLbl, typesRow);

        } catch (SQLException e) {
            card.getChildren().add(new Label("خطأ في تحميل البيانات"));
        }
    }

    private static HBox buildFilters(VBox tableBody, List<VaultRow> allRows) {
        Button allBtn = filterBtn("الكل");
        Button inBtn = filterBtn("دخول");
        Button outBtn = filterBtn("خروج");
        Button cashBtn = filterBtn("كاش");
        Button bankBtn = filterBtn("بنك");
        Button walletBtn = filterBtn("محفظة");
        Button checkBtn = filterBtn("شيك");

        setActive(allBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn);

        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, null); });
        inBtn.setOnAction(e -> { setActive(inBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "in"); });
        outBtn.setOnAction(e -> { setActive(outBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "out"); });
        cashBtn.setOnAction(e -> { setActive(cashBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "cash"); });
        bankBtn.setOnAction(e -> { setActive(bankBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "bank"); });
        walletBtn.setOnAction(e -> { setActive(walletBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "wallet"); });
        checkBtn.setOnAction(e -> { setActive(checkBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "check"); });

        HBox box = new HBox(6, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "الاتجاه", "نوع الدفع", "المبلغ", "التصنيف", "ملاحظة"};
        double[] widths = {100, 70, 90, 110, 120, 180};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<VaultRow> rows, int treasuryId) {
        String sql = "SELECT id, transaction_date, direction, payment_type, amount, " +
                "COALESCE(category,'') as category, COALESCE(notes,'') as notes " +
                "FROM vault_transactions WHERE vault_id = ? ORDER BY transaction_date DESC LIMIT 50";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, treasuryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new VaultRow(
                        rs.getInt("id"),
                        rs.getString("transaction_date"),
                        rs.getString("direction"),
                        rs.getString("payment_type"),
                        rs.getDouble("amount"),
                        rs.getString("category"),
                        rs.getString("notes")
                ));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
    }

    private static void renderTable(VBox table, List<VaultRow> rows, String filter) {
        table.getChildren().clear();
        double[] widths = {100, 70, 90, 110, 120, 180};
        boolean odd = true, hasRows = false;

        for (VaultRow row : rows) {
            if (filter != null && !row.direction().equals(filter) && !row.paymentType().equals(filter)) continue;
            hasRows = true;

            HBox r = new HBox();
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isIn = row.direction().equals("in");

            String dirText = isIn ? "دخول" : "خروج";
            String dirColor = isIn ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#FCEBEB; -fx-text-fill: #A32D2D";

            String payText = switch (row.paymentType()) {
                case "bank" -> "بنك";
                case "wallet" -> "محفظة";
                case "check" -> "شيك";
                default -> "كاش";
            };

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label dirLbl = new Label(dirText);
            dirLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " + dirColor + ";");
            dirLbl.setMinWidth(widths[1]); dirLbl.setPrefWidth(widths[1]);

            Label payLbl = new Label(payText);
            payLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            payLbl.setMinWidth(widths[2]); payLbl.setPrefWidth(widths[2]);

            Label amtLbl = new Label(String.format("%.0f جنيه", row.amount()));
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                    (isIn ? "#3B6D11" : "#A32D2D") + ";");
            amtLbl.setMinWidth(widths[3]); amtLbl.setPrefWidth(widths[3]);

            Label catLbl = new Label(row.category());
            catLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            catLbl.setMinWidth(widths[4]); catLbl.setPrefWidth(widths[4]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[5]); notesLbl.setPrefWidth(widths[5]);

            r.getChildren().addAll(dateLbl, dirLbl, payLbl, amtLbl, catLbl, notesLbl);
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
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: white; -fx-border-color: #c0c0c0;");
        return btn;
    }

    private static void setActive(Button active, Button... all) {
        for (Button b : all) {
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: #3B6D11; -fx-text-fill: white;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18;");
        }
    }
}