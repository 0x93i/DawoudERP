package com.daoud.ui;

import com.daoud.dao.FloorDAO;
import com.daoud.dao.SupplierDAO;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class FloorDetailContent {

    public static Node build(int userId, String username, String role, int supplierId, String supplierName) {

        Label balanceLabel = new Label("جاري التحميل...");
        balanceLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        List<FloorDAO.FloorTransaction> allRows = new java.util.ArrayList<>();
        VBox tableBody = new VBox(0);
        tableBody.getChildren().add(new Label("جاري التحميل..."));

        Button backBtn = new Button("← رجوع للأرضيات"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(FloorsContent.build(userId, username, role));
            MainLayout.setTitle("الأرضيات");
        });

        Button openAccountBtn = new Button("فتح حساب المورد"); openAccountBtn.getStyleClass().add("btn-default");
        openAccountBtn.setOnAction(e -> {
            // نجيب بيانات المورد كاملة عشان نفتح صفحته العادية
            AsyncHelper.run(
                    () -> SupplierDAO.getAllSuppliers().stream()
                            .filter(s -> s.getId() == supplierId).findFirst().orElse(null),
                    supplier -> {
                        if (supplier != null) {
                            MainLayout.loadContent(SupplierDetailContent.build(userId, username, role, supplier));
                            MainLayout.setTitle("حساب مورد: " + supplier.getName());
                        }
                    },
                    error -> {}
            );
        });

        VBox infoCard = new VBox(8); infoCard.getStyleClass().add("card");
        Label title = new Label("أرضية: " + supplierName); title.getStyleClass().add("card-title");
        infoCard.getChildren().addAll(title, balanceLabel, new HBox(8, openAccountBtn));

        // ── فورم زيادة/نقصان (أدمن بس) ──
        VBox adjustCard = new VBox(10); adjustCard.getStyleClass().add("card");
        adjustCard.setVisible(role.equals("admin")); adjustCard.setManaged(role.equals("admin"));

        ComboBox<String> directionCombo = new ComboBox<>(FXCollections.observableArrayList("زيادة", "نقصان"));
        directionCombo.setValue("زيادة"); directionCombo.setMaxWidth(Double.MAX_VALUE);
        TextField amountField = new TextField(); amountField.setPromptText("المبلغ"); amountField.setMaxWidth(Double.MAX_VALUE);
        TextField notesField = new TextField(); notesField.setPromptText("ملاحظة (اختياري)"); notesField.setMaxWidth(Double.MAX_VALUE);
        Button saveBtn = new Button("تسجيل"); saveBtn.getStyleClass().add("btn-primary");
        Label saveMsg = new Label("");

        saveBtn.setOnAction(e -> {
            double amount;
            try {
                amount = Double.parseDouble(amountField.getText().trim());
            } catch (NumberFormatException ex) { saveMsg.setText("ادخل رقم صحيح"); return; }
            if (amount <= 0) { saveMsg.setText("ادخل رقم أكبر من صفر"); return; }

            String direction = directionCombo.getValue().equals("زيادة") ? "in" : "out";
            String notes = notesField.getText().trim();

            saveMsg.setText("جاري الحفظ...");
            AsyncHelper.runVoid(
                    () -> FloorDAO.adjust(supplierId, direction, amount, notes, userId),
                    () -> {
                        amountField.clear(); notesField.clear();
                        saveMsg.setText("تم ✓");
                        reload(balanceLabel, tableBody, allRows, supplierId);
                    },
                    error -> saveMsg.setText("حصل خطأ أثناء الحفظ"),
                    saveBtn
            );
        });

        Label adjustTitle = new Label("تعديل رصيد الأرضية"); adjustTitle.getStyleClass().add("card-title");
        adjustCard.getChildren().addAll(adjustTitle,
                new HBox(8,
                        new VBox(4, new Label("النوع:"), directionCombo),
                        new VBox(4, new Label("المبلغ:"), amountField)),
                new VBox(4, new Label("ملاحظة:"), notesField),
                saveBtn, saveMsg);

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل حركات الأرضية"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, buildTableHeader(), tableBody);

        reload(balanceLabel, tableBody, allRows, supplierId);

        return new VBox(12, backBtn, infoCard, adjustCard, historyCard);
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "المبلغ", "ملاحظة", "بواسطة"};
        double[] widths = {95, 80, 100, 200, 120};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void reload(Label balanceLabel, VBox tableBody, List<FloorDAO.FloorTransaction> allRows, int supplierId) {
        AsyncHelper.run(
                () -> new Object[]{FloorDAO.getFloorAmount(supplierId), FloorDAO.getHistory(supplierId)},
                result -> {
                    double balance = (double) result[0];
                    @SuppressWarnings("unchecked")
                    List<FloorDAO.FloorTransaction> rows = (List<FloorDAO.FloorTransaction>) result[1];
                    balanceLabel.setText(String.format("%.0f جنيه", balance));
                    balanceLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " +
                            (balance >= 0 ? "#3B6D11" : "#A32D2D") + ";");
                    allRows.clear(); allRows.addAll(rows);
                    renderTable(tableBody, allRows);
                },
                error -> balanceLabel.setText("حصل خطأ")
        );
    }

    private static void renderTable(VBox table, List<FloorDAO.FloorTransaction> rows) {
        table.getChildren().clear();
        double[] widths = {95, 80, 100, 200, 120};
        boolean odd = true;

        if (rows.isEmpty()) {
            Label empty = new Label("مفيش حركات لسه");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
            return;
        }

        for (FloorDAO.FloorTransaction row : rows) {
            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isIn = row.direction().equals("in");

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label dirLbl = new Label(isIn ? "زيادة" : "نقصان");
            dirLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isIn ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#FCEBEB; -fx-text-fill: #A32D2D") + ";");
            dirLbl.setMinWidth(widths[1]); dirLbl.setPrefWidth(widths[1]);

            Label amtLbl = new Label(String.format("%.0f جنيه", row.amount()));
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isIn ? "#3B6D11" : "#A32D2D") + ";");
            amtLbl.setMinWidth(widths[2]); amtLbl.setPrefWidth(widths[2]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[3]); notesLbl.setPrefWidth(widths[3]);

            Label byLbl = new Label(row.recordedByName());
            byLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            byLbl.setMinWidth(widths[4]); byLbl.setPrefWidth(widths[4]);

            r.getChildren().addAll(dateLbl, dirLbl, amtLbl, notesLbl, byLbl);
            table.getChildren().add(r);
        }
    }
}
