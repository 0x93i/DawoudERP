package com.daoud.ui;

import com.daoud.dao.WorkerDAO;
import com.daoud.model.Worker;
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

public class WorkerDetailContent {

    record HistoryRow(String type, int recordId, String date, String amount, String method, String notes) {}

    private record Summary(int days, double earned, double withdrawn) {}
    private record WorkerData(Summary summary, List<HistoryRow> rows) {}

    public static Node build(int userId, String username, String role, Worker worker) {

        List<HistoryRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        tableBody.getChildren().add(new Label("جاري التحميل..."));

        Label summaryLabel = new Label("جاري التحميل...");
        Label phoneLbl = new Label("التليفون: " + (worker.getPhone() != null && !worker.getPhone().isEmpty() ? worker.getPhone() : "—"));

        HBox filtersBox = buildFilters(tableBody, allRows, worker.getId(), summaryLabel);
        reload(summaryLabel, tableBody, allRows, worker.getId(), null);

        // ── تسجيل يوم ──
        TextField wageTodayField = new TextField(String.valueOf(worker.getDailyWage()));
        wageTodayField.setPromptText("الأجر اليوم");
        wageTodayField.setMaxWidth(Double.MAX_VALUE);
        Button attendBtn = new Button("تسجيل يوم عمل"); attendBtn.getStyleClass().add("btn-primary");
        Label attendMsg = new Label("");

        attendBtn.setOnAction(e -> {
            try {
                double wage = Double.parseDouble(wageTodayField.getText().trim());
                attendMsg.setText("جاري الحفظ...");
                AsyncHelper.runVoid(
                        () -> WorkerDAO.recordAttendance(worker.getId(), wage, userId),
                        () -> {
                            attendMsg.setText("تم ✓");
                            reload(summaryLabel, tableBody, allRows, worker.getId(), null);
                        },
                        error -> attendMsg.setText("حصل خطأ"),
                        attendBtn
                );
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
                String method = paymentMethodCombo.getValue();
                String notes = withdrawNotes.getText().trim();

                withdrawMsg.setText("جاري الحفظ...");
                AsyncHelper.runVoid(
                        () -> {
                            WorkerDAO.addWithdrawal(worker.getId(), amount, method, userId, notes);
                            int vaultId = com.daoud.db.VaultHelper.getWarehouseTreasuryId(worker.getWarehouseId());
                            com.daoud.db.VaultHelper.record(vaultId, "out",
                                    com.daoud.db.VaultHelper.toPaymentType(method),
                                    amount, "سحب عامل: " + worker.getName(),
                                    notes, userId);
                        },
                        () -> {
                            withdrawAmount.clear(); withdrawNotes.clear();
                            withdrawMsg.setText("تم ✓");
                            reload(summaryLabel, tableBody, allRows, worker.getId(), null);
                        },
                        error -> withdrawMsg.setText("حصل خطأ"),
                        withdrawBtn
                );
            } catch (NumberFormatException ex) { withdrawMsg.setText("ادخل رقم"); }
        });

        // ── تسوية ──
        Button settleBtn = new Button("عمل تسوية"); settleBtn.getStyleClass().add("btn-default");
        settleBtn.setVisible(role.equals("admin")); settleBtn.setManaged(role.equals("admin"));
        settleBtn.setOnAction(e -> {
            settleBtn.setDisable(true);
            AsyncHelper.run(
                    () -> loadSummary(worker.getId()),
                    summary -> {
                        settleBtn.setDisable(false);
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                                String.format("أيام: %d | أجر: %.1f | مسحوب: %.1f | متبقي: %.1f%nتأكيد؟",
                                        summary.days(), summary.earned(), summary.withdrawn(),
                                        summary.earned() - summary.withdrawn()));
                        confirm.showAndWait().ifPresent(r -> {
                            if (r == ButtonType.OK) {
                                settleBtn.setDisable(true);
                                AsyncHelper.runVoid(
                                        () -> WorkerDAO.settleWorker(worker.getId()),
                                        () -> reload(summaryLabel, tableBody, allRows, worker.getId(), null),
                                        error -> reload(summaryLabel, tableBody, allRows, worker.getId(), null),
                                        settleBtn
                                );
                            }
                        });
                    },
                    error -> settleBtn.setDisable(false)
            );
        });

        Button backBtn = new Button("← رجوع للعمال"); backBtn.getStyleClass().add("btn-default");
        backBtn.setOnAction(e -> {
            MainLayout.loadContent(WorkersContent.build(userId, username, role));
            MainLayout.setTitle("العمال");
        });

        VBox infoCard = new VBox(6); infoCard.getStyleClass().add("card");
        infoCard.getChildren().addAll(new Label("العامل: " + worker.getName()), phoneLbl, summaryLabel);

        VBox attendCard = new VBox(8); attendCard.getStyleClass().add("card");
        attendCard.getChildren().addAll(new Label("تسجيل يوم عمل:"),
                new VBox(4, new Label("الأجر اليوم:"), wageTodayField),
                attendBtn, attendMsg);

        VBox withdrawCard = new VBox(8); withdrawCard.getStyleClass().add("card");
        withdrawCard.getChildren().addAll(new Label("سحب فلوس:"),
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

    private static HBox buildFilters(VBox tableBody, List<HistoryRow> allRows, int workerId, Label summaryLabel) {
        Button allBtn = filterBtn("الكل");
        Button attendBtn = filterBtn("حضور");
        Button withdrawBtn = filterBtn("سحب");
        setActive(allBtn, allBtn, attendBtn, withdrawBtn);
        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, null, workerId, summaryLabel); });
        attendBtn.setOnAction(e -> { setActive(attendBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, "حضور", workerId, summaryLabel); });
        withdrawBtn.setOnAction(e -> { setActive(withdrawBtn, allBtn, attendBtn, withdrawBtn); renderTable(tableBody, allRows, "سحب", workerId, summaryLabel); });
        HBox box = new HBox(8, allBtn, attendBtn, withdrawBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "النوع", "المبلغ", "طريقة الدفع", "ملاحظة", ""};
        double[] widths = {100, 75, 100, 110, 150, 110};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    // ── تحميل الملخص + سجل المعاملات مع بعض في الخلفية ──
    private static void reload(Label summaryLabel, VBox tableBody, List<HistoryRow> allRows, int workerId, String filterType) {
        AsyncHelper.run(
                () -> new WorkerData(loadSummary(workerId), loadRows(workerId)),
                data -> {
                    setSummaryText(summaryLabel, data.summary());
                    allRows.clear();
                    allRows.addAll(data.rows());
                    renderTable(tableBody, allRows, filterType, workerId, summaryLabel);
                },
                error -> summaryLabel.setText("حصل خطأ في تحميل البيانات")
        );
    }

    private static Summary loadSummary(int workerId) {
        int days = WorkerDAO.getWorkDays(workerId);
        double earned = WorkerDAO.getTotalEarned(workerId);
        double withdrawn = WorkerDAO.getTotalWithdrawn(workerId);
        return new Summary(days, earned, withdrawn);
    }

    private static void setSummaryText(Label lbl, Summary s) {
        lbl.setText(String.format("أيام: %d | أجر: %.1f | مسحوب: %.1f | متبقي: %.1f جنيه",
                s.days(), s.earned(), s.withdrawn(), s.earned() - s.withdrawn()));
    }

    private static List<HistoryRow> loadRows(int workerId) {
        List<HistoryRow> rows = new ArrayList<>();
        // حضور
        String sql1 = "SELECT id, work_date, daily_wage FROM worker_attendance WHERE worker_id = ? ORDER BY work_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql1)) {
            stmt.setInt(1, workerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow("حضور", rs.getInt("id"), rs.getString("work_date"),
                        String.format("%.1f جنيه", rs.getDouble("daily_wage")), "—", ""));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        // سحب
        String sql2 = "SELECT id, withdrawal_date, amount, payment_method, notes FROM worker_withdrawals WHERE worker_id = ? ORDER BY withdrawal_date DESC LIMIT 30";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
            stmt.setInt(1, workerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new HistoryRow("سحب", rs.getInt("id"), rs.getString("withdrawal_date"),
                        String.format("%.1f جنيه", rs.getDouble("amount")),
                        rs.getString("payment_method") != null ? rs.getString("payment_method") : "كاش",
                        rs.getString("notes") != null ? rs.getString("notes") : ""));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }

        rows.sort((a, b) -> b.date().compareTo(a.date()));
        return rows;
    }

    private static void renderTable(VBox table, List<HistoryRow> rows, String filterType,
                                    int workerId, Label summaryLabel) {
        table.getChildren().clear();
        double[] widths = {100, 75, 100, 110, 150, 55, 55};
        boolean odd = true, hasRows = false;

        for (HistoryRow row : rows) {
            if (filterType != null && !row.type().equals(filterType)) continue;
            hasRows = true;

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
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
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " +
                    (isAttend ? "#3B6D11" : "#A32D2D") + ";");
            amtLbl.setMinWidth(widths[2]); amtLbl.setPrefWidth(widths[2]);

            Label methodLbl = new Label(isAttend ? "—" : row.method());
            methodLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            methodLbl.setMinWidth(widths[3]); methodLbl.setPrefWidth(widths[3]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[4]); notesLbl.setPrefWidth(widths[4]);

            Button editBtn = new Button("تعديل");
            editBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            editBtn.setMinWidth(widths[5]); editBtn.setPrefWidth(widths[5]);

            Button deleteBtn = new Button("حذف");
            deleteBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            deleteBtn.setMinWidth(widths[6]); deleteBtn.setPrefWidth(widths[6]);

            final HistoryRow finalRow = row;

            // التعديل
            editBtn.setOnAction(e -> {
                TextField amtEdit = new TextField(row.amount().replace(" جنيه", "").trim());
                amtEdit.setPromptText("المبلغ / الأجر");
                Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                Label errLbl = new Label("");

                if (!isAttend) {
                    // تعديل سحب
                    TextField notesEdit = new TextField(row.notes());
                    ComboBox<String> methodEdit = new ComboBox<>(
                            FXCollections.observableArrayList("كاش", "فيزا", "محفظة", "شيك", "تحويل بنكي"));
                    methodEdit.setValue(row.method());

                    saveEdit.setOnAction(ev -> {
                        try {
                            double amt = Double.parseDouble(amtEdit.getText().trim());
                            String method = methodEdit.getValue();
                            String notes = notesEdit.getText().trim();
                            AsyncHelper.runVoid(
                                    () -> {
                                        try (Connection conn = DatabaseManager_online.getConnection();
                                             PreparedStatement stmt = conn.prepareStatement(
                                                     "UPDATE worker_withdrawals SET amount=?, payment_method=?, notes=? WHERE id=?")) {
                                            stmt.setDouble(1, amt); stmt.setString(2, method);
                                            stmt.setString(3, notes); stmt.setInt(4, finalRow.recordId());
                                            stmt.executeUpdate();
                                        }
                                    },
                                    () -> {
                                        reload(summaryLabel, table, rows, workerId, filterType);
                                        ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                                    },
                                    error -> errLbl.setText("خطأ"),
                                    saveEdit
                            );
                        } catch (NumberFormatException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10, new Label("المبلغ:"), amtEdit,
                            new Label("طريقة الدفع:"), methodEdit,
                            new Label("ملاحظة:"), notesEdit, saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل سحب", dl, 320, 260).show();
                } else {
                    // تعديل حضور
                    saveEdit.setOnAction(ev -> {
                        try {
                            double amt = Double.parseDouble(amtEdit.getText().trim());
                            AsyncHelper.runVoid(
                                    () -> {
                                        try (Connection conn = DatabaseManager_online.getConnection();
                                             PreparedStatement stmt = conn.prepareStatement(
                                                     "UPDATE worker_attendance SET daily_wage=? WHERE id=?")) {
                                            stmt.setDouble(1, amt); stmt.setInt(2, finalRow.recordId());
                                            stmt.executeUpdate();
                                        }
                                    },
                                    () -> {
                                        reload(summaryLabel, table, rows, workerId, filterType);
                                        ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                                    },
                                    error -> errLbl.setText("خطأ"),
                                    saveEdit
                            );
                        } catch (NumberFormatException ex) { errLbl.setText("خطأ"); }
                    });

                    VBox dl = new VBox(10, new Label("الأجر:"), amtEdit, saveEdit, errLbl);
                    dl.setPadding(new Insets(20));
                    DialogHelper.create("تعديل حضور", dl, 280, 180).show();
                }
            });

            // الحذف
            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف السجل ده؟");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        String delSql = finalRow.type().equals("حضور") ?
                                "DELETE FROM worker_attendance WHERE id = ?" :
                                "DELETE FROM worker_withdrawals WHERE id = ?";
                        deleteBtn.setDisable(true);
                        AsyncHelper.runVoid(
                                () -> {
                                    try (Connection conn = DatabaseManager_online.getConnection();
                                         PreparedStatement stmt = conn.prepareStatement(delSql)) {
                                        stmt.setInt(1, finalRow.recordId());
                                        stmt.executeUpdate();
                                    }
                                },
                                () -> reload(summaryLabel, table, rows, workerId, filterType),
                                error -> reload(summaryLabel, table, rows, workerId, filterType)
                        );
                    }
                });
            });

            r.getChildren().addAll(dateLbl, typeLbl, amtLbl, methodLbl, notesLbl, editBtn, deleteBtn);
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
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
        }
    }
}
