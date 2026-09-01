package com.daoud.ui;

import com.daoud.dao.WorkerDAO;
import com.daoud.model.Worker;
import com.daoud.db.DatabaseManager_online;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;

public class WorkerDetailScreen {

    public static void show(Stage stage, int userId, String username, String role, Worker worker) {

        // ── التاريخ (بيتعرف أول) ──
        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setPrefHeight(180);

        Label title = new Label("حساب العامل: " + worker.getName());
        Label wageLabel = new Label("الأجر اليومي: " + worker.getDailyWage() + " جنيه");
        Label summaryLabel = new Label();
        refreshSummary(summaryLabel, worker.getId());
        refreshHistory(historyArea, worker.getId());

        // ── تسجيل يوم عمل ──
        Label attendTitle = new Label("── تسجيل يوم عمل ──");
        TextField wageTodayField = new TextField(String.valueOf(worker.getDailyWage()));
        wageTodayField.setPromptText("الأجر اليوم");
        Button attendBtn = new Button("تسجيل يوم");
        Label attendMsg = new Label("");

        attendBtn.setOnAction(e -> {
            try {
                double wage = Double.parseDouble(wageTodayField.getText().trim());
                WorkerDAO.recordAttendance(worker.getId(), wage, userId);
                attendMsg.setText("تم التسجيل");
                refreshSummary(summaryLabel, worker.getId());
                refreshHistory(historyArea, worker.getId());
            } catch (NumberFormatException ex) {
                attendMsg.setText("ادخل رقم صحيح");
            }
        });

        // ── سحب فلوس ──
        Label withdrawTitle = new Label("── سحب فلوس ──");
        TextField withdrawAmount = new TextField();
        withdrawAmount.setPromptText("المبلغ");
        TextField withdrawNotes = new TextField();
        withdrawNotes.setPromptText("ملاحظة (اختياري)");
        Button withdrawBtn = new Button("تسجيل السحب");
        Label withdrawMsg = new Label("");

        withdrawBtn.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(withdrawAmount.getText().trim());
                WorkerDAO.addWithdrawal(worker.getId(), amount, "كاش", userId, withdrawNotes.getText().trim());                withdrawAmount.clear();
                withdrawNotes.clear();
                withdrawMsg.setText("تم التسجيل");
                refreshSummary(summaryLabel, worker.getId());
                refreshHistory(historyArea, worker.getId());
            } catch (NumberFormatException ex) {
                withdrawMsg.setText("ادخل رقم صحيح");
            }
        });

        // ── التسوية (أدمن بس) ──
        Label settleTitle = new Label("── تسوية ──");
        Button settleBtn = new Button("عمل تسوية");
        settleTitle.setVisible(role.equals("admin"));
        settleTitle.setManaged(role.equals("admin"));
        settleBtn.setVisible(role.equals("admin"));
        settleBtn.setManaged(role.equals("admin"));

        settleBtn.setOnAction(e -> {
            double earned = WorkerDAO.getTotalEarned(worker.getId());
            double withdrawn = WorkerDAO.getTotalWithdrawn(worker.getId());
            double remaining = earned - withdrawn;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    String.format("إجمالي الأيام: %d%nإجمالي الأجر: %.1f%nالمسحوب: %.1f%nالمتبقي للدفع: %.1f%n%nتأكيد التسوية؟",
                            WorkerDAO.getWorkDays(worker.getId()), earned, withdrawn, remaining));
            confirm.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    WorkerDAO.settleWorker(worker.getId());
                    refreshSummary(summaryLabel, worker.getId());
                    refreshHistory(historyArea, worker.getId());
                }
            });
        });

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> WorkersScreen.show(stage, userId, username, role));

        VBox layout = new VBox(8,
                title, wageLabel, summaryLabel,
                new Separator(),
                attendTitle,
                new Label("الأجر اليوم:"), wageTodayField,
                attendBtn, attendMsg,
                new Separator(),
                withdrawTitle,
                new Label("المبلغ:"), withdrawAmount,
                new Label("ملاحظة:"), withdrawNotes,
                withdrawBtn, withdrawMsg,
                new Separator(),
                settleTitle, settleBtn,
                new Separator(),
                new Label("── السجل ──"), historyArea,
                backBtn
        );
        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll, 800, 600));
    }

    private static void refreshSummary(Label lbl, int workerId) {
        int days = WorkerDAO.getWorkDays(workerId);
        double earned = WorkerDAO.getTotalEarned(workerId);
        double withdrawn = WorkerDAO.getTotalWithdrawn(workerId);
        double remaining = earned - withdrawn;
        lbl.setText(String.format("أيام العمل: %d | إجمالي الأجر: %.1f | المسحوب: %.1f | المتبقي: %.1f جنيه",
                days, earned, withdrawn, remaining));
    }

    private static void refreshHistory(TextArea area, int workerId) {
        StringBuilder sb = new StringBuilder();
        String sql = """
            SELECT 'حضور' as type, work_date as date, daily_wage as amount, '' as notes
            FROM worker_attendance WHERE worker_id = ?
            UNION ALL
            SELECT 'سحب' as type, withdrawal_date as date, amount, COALESCE(notes,'')
            FROM worker_withdrawals WHERE worker_id = ?
            ORDER BY date DESC LIMIT 30
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, workerId);
            stmt.setInt(2, workerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                sb.append(String.format("%s | %s | %.1f جنيه %s%n",
                        rs.getString("date"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("notes").isEmpty() ? "" : "| " + rs.getString("notes")));
            }
        } catch (SQLException e) {
            sb.append("خطأ في تحميل السجل");
        }
        area.setText(sb.toString());
    }
}