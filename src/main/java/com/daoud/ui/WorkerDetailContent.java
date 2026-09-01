package com.daoud.ui;

import com.daoud.dao.WorkerDAO;
import com.daoud.model.Worker;
import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkerDetailContent {

    record HistoryRow(String type, String date, String amount, String method, String notes) {}

    public static Node build(int userId, String username, String role, Worker worker) {

        // ── جدول السجل ──
        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        HBox filtersBox = buildFilters(tableBody, allRows);
        loadRows(allRows, worker.getId());
        renderTable(tableBody, allRows, null);

        Label summaryLabel = new Label();
        refreshSummary(summaryLabel, worker.getId());

        Label phoneLbl = new Label("التليفون: " + (worker.getPhone() != null && !worker.getPhone().isEmpty() ? worker.getPhone() : "—"));

        // ── تسجيل يوم ──
        TextField wageTodayField = new TextField(String.valueOf(worker.getDailyWage()));
        wageTodayField.setPromptText("الأجر اليوم");
        wageTodayField.setMaxWidth(Double.MAX_VALUE);
        Button attendBtn = new Button("تسجيل يوم عمل"); attendBtn.getStyleClass().add("btn-primary");
        Label attendMsg = new Label("");

        attendBtn.setOnAction(e -> {
            try {
                double wage = Double.parseDouble(wageTodayField.getText().trim());
                WorkerDAO.recordAttendance(worker.getId(), wage, userId);
                attendMsg.setText("تم ✓");
                refreshSummary(summaryLabel, worker.getId());
                allRows.clear(); loadRows(allRows, worker.getId()); renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { attendMsg.setText("ادخل رقم"); }
        });

        // ── سحب ──
        TextField withdrawAmount = new TextField(); withdrawAmount.setPromptText("المبلغ");
        TextField withdrawNotes = new TextField(); withdrawNotes.setPromptText("ملاحظة");
        withdrawAmount.setMaxWidth(Double.MAX_VALUE);
        withdrawNotes.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> paymentMethodCombo = new ComboBox<>(
                FXCollections.observableArrayList("كاش", "فيزا", "محفظة", "شيك", "تحويل بنكي"));
        paymentMethodCombo.setValue("كاش");
        Button withdrawBtn = new Button("تسجيل السحب"); withdrawBtn.getStyleClass().add("btn-default");
        Label withdrawMsg = new Label("");

        withdrawBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(withdrawAmount.getText().trim());
                WorkerDAO.addWithdrawal(worker.getId(), amount,
                        paymentMethodCombo.getValue(), userId, withdrawNotes.getText().trim());
                withdrawAmount.clear(); withdrawNotes.clear();
                withdrawMsg.setText("تم ✓");
                refreshSummary(summaryLabel, worker.getId());
                allRows.clear(); loadRows(allRows, worker.getId()); renderTable(tableBody, allRows, null);
            } catch (NumberFormatException ex) { withdrawMsg.setText("ادخل رقم"); }
        });

        // ── تسوية ──
        Button settleBtn = new Button("عمل تسوية"); settleBtn.getStyleClass().add("btn-default");
        settleBtn.setVisible(role.equals("admin")); settleBtn.setManaged(role.equals("admin"));
        settleBtn.setOnAction(e -> {
            double earned = WorkerDAO.getTotalEarned(worker.getId());
            double withdrawn = WorkerDAO.getTotalWithdrawn(worker.getId());
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    String.format("أيام: %d | أجر: %.1f | مسحوب: %.1f | متبقي: %.1f%nتأكيد؟",
                            WorkerDAO.getWorkDays(worker.getId()), earned, withdrawn, earned - withdrawn));
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    WorkerDAO.settleWorker(worker.getId());
                    refreshSummary(summaryLabel, worker.getId());
                    allRows.clear(); loadRows(allRows, worker.getId()); renderTable(tableBody, allRows, null);
                }
            });
        });

        Button backBtn = new Button("← رجوع للعمال"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(WorkersContent.build(userId, username, role));
            MainLayout.setTitle("العمال");
        });

        // ── Cards ──
        VBox infoCard = new VBox(6); infoCard.getStyleClass().add("card");
        infoCard.getChildren().addAll(
                new Label("العامل: " + worker.getName()),
                phoneLbl, summaryLabel);

        VBox attendCard = new VBox(8); attendCard.getStyleClass().add("card");
        Label attendTitle = new Label("تسجيل يوم عمل"); attendTitle.getStyleClass().add("card-title");
        attendCard.getChildren().addAll(
                attendTitle,
                new VBox(4, new Label("الأجر اليوم:"), wageTodayField),
                attendBtn, attendMsg);

        VBox withdrawCard = new VBox(8); withdrawCard.getStyleClass().add("card");
        Label withdrawTitle = new Label("سحب فلوس"); withdrawTitle.getStyleClass().add("card-title");
        withdrawCard.getChildren().addAll(
                withdrawTitle,
                new HBox(8,
                        new VBox(4, new Label("المبلغ:"), withdrawAmount),
                        new VBox(4, new Label("طريقة الدفع:"), paymentMethodCombo)),
                new VBox(4, new Label("ملاحظة:"), withdrawNotes),
                withdrawBtn, withdrawMsg);

        HBox.setHgrow(withdrawAmount, Priority.ALWAYS);

        VBox settleCard = new VBox(8); settleCard.getStyleClass().add("card");
        settleCard.getChildren().add(settleBtn);
        settleCard.setVisible(role.equals("admin")); settleCard.setManaged(role.equals("admin"));

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        HBox topRow = new HBox(12, attendCard, withdrawCard);
        HBox.setHgrow(attendCard, Priority.ALWAYS);
        HBox.setHgrow(withdrawCard, Priority.ALWAYS);

        return new VBox(12, backBtn, infoCard, topRow, settleCard, historyCard);
    }

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows) {
        Button allBtn = filterBtn("الكل");
        Button attendBtn = filterBtn("حضور");
        Button withdrawBtn = filterBtn("سحب");

        setActive(allBtn, allBtn, attendBtn, withdrawBtn);

        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, null); });
        attendBtn.setOnAction(e -> { setActive(attendBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, "حضور"); });
        withdrawBtn.setOnAction(e -> { setActive(withdrawBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, "سحب"); });

        HBox box = new HBox(8, allBtn, attendBtn, withdrawBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "المبلغ", "طريقة الدفع", "ملاحظة"};
        double[] widths = {100, 80, 110, 120, 180};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void loadRows(List<HistoryRow> rows, int workerId) {
        String sql = """
            SELECT 'حضور' as type, work_date as date, daily_wage as amount,
                   '' as method, '' as notes
            FROM worker_attendance WHERE worker_id = ?
            UNION ALL
            SELECT 'سحب' as type, withdrawal_date as date, amount,
                   COALESCE(payment_method,'كاش') as method,
                   COALESCE(notes,'') as notes
            FROM worker_withdrawals WHERE worker_id = ?
            ORDER BY date DESC LIMIT 50
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId); stmt.setInt(2, workerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow(
                        rs.getString("type"),
                        rs.getString("date"),
                        String.format("%.1f جنيه", rs.getDouble("amount")),
                        rs.getString("method"),
                        rs.getString("notes")
                ));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType) {
        table.getChildren().clear();
        double[] widths = {100, 80, 110, 120, 180};
        boolean odd = true;
        boolean hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox();
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") + "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isAttend = row.type().equals("حضور");

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label typeLbl = new Label(row.type());
            typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isAttend ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#E6F1FB; -fx-text-fill: #185FA5") + ";");
            typeLbl.setMinWidth(widths[1]); typeLbl.setPrefWidth(widths[1]);

            Label amtLbl = new Label(row.amount());
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isAttend ? "#3B6D11" : "#A32D2D") + ";");
            amtLbl.setMinWidth(widths[2]); amtLbl.setPrefWidth(widths[2]);

            Label methodLbl = new Label(isAttend ? "—" : row.method());
            methodLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            methodLbl.setMinWidth(widths[3]); methodLbl.setPrefWidth(widths[3]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[4]); notesLbl.setPrefWidth(widths[4]);

            r.getChildren().addAll(dateLbl, typeLbl, amtLbl, methodLbl, notesLbl);
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

    private static void refreshSummary(Label lbl, int workerId) {
        int days = WorkerDAO.getWorkDays(workerId);
        double earned = WorkerDAO.getTotalEarned(workerId);
        double withdrawn = WorkerDAO.getTotalWithdrawn(workerId);
        lbl.setText(String.format("أيام: %d | أجر: %.1f | مسحوب: %.1f | متبقي: %.1f جنيه",
                days, earned, withdrawn, earned - withdrawn));
    }
}