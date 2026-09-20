package com.daoud.ui;

import com.daoud.db.DatabaseManager_online;
import com.daoud.model.Supplier;
import com.daoud.model.Worker;
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

    record SummaryData(double cash, double bank, double wallet, double check) {}

    record InitialData(List<Supplier> suppliers, List<Worker> workers,
                       SummaryData summary, List<VaultRow> rows) {}

    public static Node build(int userId, String username, String role, int treasuryId, String treasuryName) {

        VBox summaryCard = new VBox(10);
        summaryCard.getStyleClass().add("card");
        summaryCard.getChildren().add(new Label("جاري التحميل..."));

        ComboBox<String> directionCombo = new ComboBox<>(FXCollections.observableArrayList("دخول", "خروج"));
        directionCombo.setPromptText("دخول / خروج"); directionCombo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> paymentCombo = new ComboBox<>(FXCollections.observableArrayList("كاش", "بنك", "محفظة", "شيك"));
        paymentCombo.setPromptText("نوع الدفع"); paymentCombo.setMaxWidth(Double.MAX_VALUE);
        TextField amountField = new TextField(); amountField.setPromptText("المبلغ"); amountField.setMaxWidth(Double.MAX_VALUE);
        TextField notesField = new TextField(); notesField.setPromptText("ملاحظة"); notesField.setMaxWidth(Double.MAX_VALUE);

        // ── وجهة الصرف (للخروج فقط) ──
        ComboBox<String> targetTypeCombo = new ComboBox<>(FXCollections.observableArrayList("مصاريف", "مورد", "عامل"));
        targetTypeCombo.setPromptText("الصرف لمين؟"); targetTypeCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Supplier> supplierCombo = new ComboBox<>();
        supplierCombo.setPromptText("اختار المورد"); supplierCombo.setMaxWidth(Double.MAX_VALUE);
        supplierCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        supplierCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Supplier item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        ComboBox<Worker> workerCombo = new ComboBox<>();
        workerCombo.setPromptText("اختار العامل"); workerCombo.setMaxWidth(Double.MAX_VALUE);
        workerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Worker item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        workerCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Worker item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        TextField expenseDetailsField = new TextField();
        expenseDetailsField.setPromptText("تفاصيل المصروف");
        expenseDetailsField.setMaxWidth(Double.MAX_VALUE);

        VBox targetBox = new VBox(8);
        targetBox.setVisible(false); targetBox.setManaged(false);
        VBox targetTypeRow = new VBox(4, new Label("الصرف لمين:"), targetTypeCombo);
        VBox supplierRow = new VBox(4, new Label("المورد:"), supplierCombo);
        VBox workerRow = new VBox(4, new Label("العامل:"), workerCombo);
        VBox expenseRow = new VBox(4, new Label("تفاصيل المصروف:"), expenseDetailsField);
        supplierRow.setVisible(false); supplierRow.setManaged(false);
        workerRow.setVisible(false); workerRow.setManaged(false);
        expenseRow.setVisible(false); expenseRow.setManaged(false);
        targetBox.getChildren().addAll(targetTypeRow, supplierRow, workerRow, expenseRow);

        directionCombo.setOnAction(ev -> {
            boolean isOut = "خروج".equals(directionCombo.getValue());
            targetBox.setVisible(isOut); targetBox.setManaged(isOut);
            if (!isOut) targetTypeCombo.setValue(null);
        });

        targetTypeCombo.setOnAction(ev -> {
            String t = targetTypeCombo.getValue();
            boolean isSup = "مورد".equals(t), isWork = "عامل".equals(t), isExp = "مصاريف".equals(t);
            supplierRow.setVisible(isSup); supplierRow.setManaged(isSup);
            workerRow.setVisible(isWork); workerRow.setManaged(isWork);
            expenseRow.setVisible(isExp); expenseRow.setManaged(isExp);
        });

        Button saveBtn = new Button("تسجيل"); saveBtn.getStyleClass().add("btn-primary");
        Label saveMsg = new Label("");

        List<VaultRow> allRows = new ArrayList<>();
        VBox tableBody = new VBox(0);
        tableBody.getChildren().add(new Label("جاري التحميل..."));
        HBox filtersBox = buildFilters(tableBody, allRows, treasuryId, summaryCard, treasuryName);

        VBox addCard = new VBox(10); addCard.getStyleClass().add("card");
        Label addTitle = new Label("تسجيل معاملة"); addTitle.getStyleClass().add("card-title");
        addCard.getChildren().addAll(addTitle,
                new HBox(8, new VBox(4, new Label("الاتجاه:"), directionCombo), new VBox(4, new Label("نوع الدفع:"), paymentCombo)),
                new VBox(4, new Label("المبلغ:"), amountField),
                targetBox,
                new VBox(4, new Label("ملاحظة:"), notesField), saveBtn, saveMsg);

        HBox.setHgrow(directionCombo, Priority.ALWAYS); HBox.setHgrow(paymentCombo, Priority.ALWAYS);
        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        Label histTitle = new Label("سجل المعاملات"); histTitle.getStyleClass().add("card-title");
        historyCard.getChildren().addAll(histTitle, filtersBox, buildTableHeader(), tableBody);

        // ── تحميل كل بيانات الشاشة مع بعض في الخلفية ──
        AsyncHelper.run(
                () -> new InitialData(
                        com.daoud.dao.SupplierDAO.getAllSuppliers(),
                        loadAllWorkers(),
                        loadSummary(treasuryId),
                        loadRowsFor(treasuryId)),
                data -> {
                    supplierCombo.setItems(FXCollections.observableArrayList(data.suppliers()));
                    workerCombo.setItems(FXCollections.observableArrayList(data.workers()));
                    renderSummary(summaryCard, data.summary(), treasuryName);
                    allRows.clear(); allRows.addAll(data.rows());
                    renderTable(tableBody, allRows, null, treasuryId, summaryCard, treasuryName);
                },
                error -> {
                    summaryCard.getChildren().setAll(new Label("حصل خطأ في التحميل"));
                    tableBody.getChildren().setAll(new Label("حصل خطأ في التحميل"));
                }
        );

        saveBtn.setOnAction(e -> {
            if (directionCombo.getValue() == null) { saveMsg.setText("اختار دخول/خروج"); return; }
            if (paymentCombo.getValue() == null) { saveMsg.setText("اختار نوع الدفع"); return; }

            boolean isOut = "خروج".equals(directionCombo.getValue());
            String category;

            if (isOut) {
                String t = targetTypeCombo.getValue();
                if (t == null) { saveMsg.setText("اختار الصرف لمين"); return; }
                if (t.equals("مورد")) {
                    if (supplierCombo.getValue() == null) { saveMsg.setText("اختار المورد"); return; }
                    category = "مورد: " + supplierCombo.getValue().getName();
                } else if (t.equals("عامل")) {
                    if (workerCombo.getValue() == null) { saveMsg.setText("اختار العامل"); return; }
                    category = "عامل: " + workerCombo.getValue().getName();
                } else {
                    if (expenseDetailsField.getText().trim().isEmpty()) { saveMsg.setText("اكتب تفاصيل المصروف"); return; }
                    category = "مصاريف: " + expenseDetailsField.getText().trim();
                }
            } else {
                category = "دخول";
            }

            double amount;
            try {
                amount = Double.parseDouble(amountField.getText().trim());
            } catch (NumberFormatException ex) { saveMsg.setText("ادخل رقم صحيح"); return; }

            final String finalCategory = category;
            final boolean finalIsOut = isOut;
            final String direction = isOut ? "out" : "in";
            final String paymentType = switch (paymentCombo.getValue()) {
                case "بنك" -> "bank"; case "محفظة" -> "wallet"; case "شيك" -> "check"; default -> "cash";
            };
            final String notes = notesField.getText().trim();

            saveMsg.setText("جاري الحفظ...");
            AsyncHelper.run(
                    () -> {
                        if (finalIsOut) {
                            double available = com.daoud.db.VaultHelper.getBalance(treasuryId, paymentType);
                            if (amount > available) return "INSUFFICIENT:" + available;
                        }
                        String sql = "INSERT INTO vault_transactions (vault_id, transaction_date, direction, payment_type, amount, category, notes, recorded_by) VALUES (?, CURRENT_DATE, ?, ?, ?, ?, ?, ?)";
                        try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, treasuryId); stmt.setString(2, direction); stmt.setString(3, paymentType);
                            stmt.setDouble(4, amount); stmt.setString(5, finalCategory);
                            stmt.setString(6, notes); stmt.setInt(7, userId);
                            stmt.executeUpdate();
                        }
                        return "OK";
                    },
                    result -> {
                        if (result.startsWith("INSUFFICIENT:")) {
                            double available = Double.parseDouble(result.substring("INSUFFICIENT:".length()));
                            saveMsg.setText(String.format("الرصيد غير كافي — المتاح: %.0f جنيه", available));
                            return;
                        }
                        amountField.clear(); notesField.clear(); expenseDetailsField.clear();
                        directionCombo.setValue(null); paymentCombo.setValue(null);
                        targetTypeCombo.setValue(null);
                        supplierCombo.setValue(null); workerCombo.setValue(null);
                        targetBox.setVisible(false); targetBox.setManaged(false);
                        supplierRow.setVisible(false); supplierRow.setManaged(false);
                        workerRow.setVisible(false); workerRow.setManaged(false);
                        expenseRow.setVisible(false); expenseRow.setManaged(false);

                        saveMsg.setText("تم ✓");
                        reload(summaryCard, tableBody, allRows, treasuryId, null, treasuryName);
                    },
                    error -> saveMsg.setText("خطأ أثناء الحفظ"),
                    saveBtn
            );
        });

        return new VBox(12, summaryCard, addCard, historyCard);
    }

    private static List<Worker> loadAllWorkers() {
        // استعلام واحد لكل العمال (بدل استعلام لكل مخزن + استعلام تاني للعمال الحرة)
        List<Worker> list = new ArrayList<>();
        String sql = "SELECT * FROM workers ORDER BY name";
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Worker(
                        rs.getInt("id"), rs.getString("name"), rs.getString("phone"),
                        rs.getInt("warehouse_id"), rs.getDouble("daily_wage")));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return list;
    }

    private static HBox buildFilters(VBox tableBody, List<VaultRow> allRows, int treasuryId, VBox summaryCard, String name) {
        Button allBtn = filterBtn("الكل"); Button inBtn = filterBtn("دخول"); Button outBtn = filterBtn("خروج");
        Button cashBtn = filterBtn("كاش"); Button bankBtn = filterBtn("بنك");
        Button walletBtn = filterBtn("محفظة"); Button checkBtn = filterBtn("شيك");
        setActive(allBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn);
        allBtn.setOnAction(e -> { setActive(allBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, null, treasuryId, summaryCard, name); });
        inBtn.setOnAction(e -> { setActive(inBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "in", treasuryId, summaryCard, name); });
        outBtn.setOnAction(e -> { setActive(outBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "out", treasuryId, summaryCard, name); });
        cashBtn.setOnAction(e -> { setActive(cashBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "cash", treasuryId, summaryCard, name); });
        bankBtn.setOnAction(e -> { setActive(bankBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "bank", treasuryId, summaryCard, name); });
        walletBtn.setOnAction(e -> { setActive(walletBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "wallet", treasuryId, summaryCard, name); });
        checkBtn.setOnAction(e -> { setActive(checkBtn, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn); renderTable(tableBody, allRows, "check", treasuryId, summaryCard, name); });
        HBox box = new HBox(6, allBtn, inBtn, outBtn, cashBtn, bankBtn, walletBtn, checkBtn);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "الاتجاه", "نوع الدفع", "المبلغ", "التصنيف", "ملاحظة", ""};
        double[] widths = {95, 65, 80, 100, 110, 150, 110};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static List<VaultRow> loadRowsFor(int treasuryId) {
        List<VaultRow> rows = new ArrayList<>();
        String sql = "SELECT id, transaction_date, direction, payment_type, amount, COALESCE(category,'') as category, COALESCE(notes,'') as notes FROM vault_transactions WHERE vault_id = ? ORDER BY transaction_date DESC LIMIT 50";
        try (Connection conn = DatabaseManager_online.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, treasuryId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.add(new VaultRow(rs.getInt("id"), rs.getString("transaction_date"),
                        rs.getString("direction"), rs.getString("payment_type"),
                        rs.getDouble("amount"), rs.getString("category"), rs.getString("notes")));
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return rows;
    }

    private static SummaryData loadSummary(int treasuryId) throws SQLException {
        // استعلام واحد بيحسب رصيد كل أنواع الدفع مع بعض (بدل 4 استعلامات منفصلة)
        String sql = """
            SELECT payment_type,
                   COALESCE(SUM(CASE WHEN direction='in' THEN amount ELSE -amount END), 0) AS bal
            FROM vault_transactions
            WHERE vault_id = ?
            GROUP BY payment_type
        """;
        double cash = 0, bank = 0, wallet = 0, check = 0;
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, treasuryId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double bal = rs.getDouble("bal");
                switch (rs.getString("payment_type")) {
                    case "bank" -> bank = bal;
                    case "wallet" -> wallet = bal;
                    case "check" -> check = bal;
                    default -> cash = bal;
                }
            }
        }
        return new SummaryData(cash, bank, wallet, check);
    }

    private static void reload(VBox summaryCard, VBox tableBody, List<VaultRow> allRows,
                               int treasuryId, String filter, String treasuryName) {
        AsyncHelper.run(
                () -> new Object[]{loadSummary(treasuryId), loadRowsFor(treasuryId)},
                result -> {
                    renderSummary(summaryCard, (SummaryData) result[0], treasuryName);
                    @SuppressWarnings("unchecked")
                    List<VaultRow> rows = (List<VaultRow>) result[1];
                    allRows.clear(); allRows.addAll(rows);
                    renderTable(tableBody, allRows, filter, treasuryId, summaryCard, treasuryName);
                },
                error -> { }
        );
    }

    private static void renderSummary(VBox card, SummaryData data, String name) {
        card.getChildren().clear();
        Label title = new Label("خزنة: " + name); title.getStyleClass().add("card-title");
        card.getChildren().add(title);

        String[] typeNames = {"كاش", "بنك", "محفظة", "شيك"};
        double[] balances = {data.cash(), data.bank(), data.wallet(), data.check()};
        String[] colors = {"#3B6D11", "#185FA5", "#854F0B", "#5f5e5a"};
        double grandTotal = data.cash() + data.bank() + data.wallet() + data.check();

        HBox typesRow = new HBox(12);
        for (int i = 0; i < typeNames.length; i++) {
            Label nameLbl = new Label(typeNames[i]); nameLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888780;");
            Label valLbl = new Label(String.format("%.0f جنيه", balances[i]));
            valLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (balances[i] >= 0 ? colors[i] : "#A32D2D") + ";");
            VBox box = new VBox(2, nameLbl, valLbl);
            box.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 10; -fx-background-radius: 8;");
            box.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(box, Priority.ALWAYS);
            typesRow.getChildren().add(box);
        }
        Label totalLbl = new Label(String.format("الإجمالي: %.0f جنيه", grandTotal));
        totalLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (grandTotal >= 0 ? "#3B6D11" : "#A32D2D") + ";");
        card.getChildren().addAll(totalLbl, typesRow);
    }

    private static void renderTable(VBox table, List<VaultRow> rows, String filter,
                                    int treasuryId, VBox summaryCard, String treasuryName) {
        table.getChildren().clear();
        double[] widths = {95, 65, 80, 100, 110, 150, 55, 55};
        boolean odd = true, hasRows = false;

        for (VaultRow row : rows) {
            if (filter != null && !row.direction().equals(filter) && !row.paymentType().equals(filter)) continue;
            hasRows = true;

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            boolean isIn = row.direction().equals("in");
            String payText = switch (row.paymentType()) {
                case "bank" -> "بنك"; case "wallet" -> "محفظة"; case "check" -> "شيك"; default -> "كاش";
            };

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label dirLbl = new Label(isIn ? "دخول" : "خروج");
            dirLbl.setStyle("-fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-background-color: " +
                    (isIn ? "#EAF3DE; -fx-text-fill: #3B6D11" : "#FCEBEB; -fx-text-fill: #A32D2D") + ";");
            dirLbl.setMinWidth(widths[1]); dirLbl.setPrefWidth(widths[1]);

            Label payLbl = new Label(payText);
            payLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            payLbl.setMinWidth(widths[2]); payLbl.setPrefWidth(widths[2]);

            Label amtLbl = new Label(String.format("%.0f جنيه", row.amount()));
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isIn ? "#3B6D11" : "#A32D2D") + ";");
            amtLbl.setMinWidth(widths[3]); amtLbl.setPrefWidth(widths[3]);

            Label catLbl = new Label(row.category());
            catLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            catLbl.setMinWidth(widths[4]); catLbl.setPrefWidth(widths[4]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[5]); notesLbl.setPrefWidth(widths[5]);

            Button editBtn = new Button("تعديل");
            editBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            editBtn.setMinWidth(widths[6]); editBtn.setPrefWidth(widths[6]);

            Button deleteBtn = new Button("حذف");
            deleteBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
            deleteBtn.setMinWidth(widths[7]); deleteBtn.setPrefWidth(widths[7]);

            final VaultRow finalRow = row;

            editBtn.setOnAction(e -> {
                ComboBox<String> dirEdit = new ComboBox<>(FXCollections.observableArrayList("دخول", "خروج"));
                dirEdit.setValue(finalRow.direction().equals("in") ? "دخول" : "خروج");
                ComboBox<String> payEdit = new ComboBox<>(FXCollections.observableArrayList("كاش", "بنك", "محفظة", "شيك"));
                payEdit.setValue(payText);
                TextField amtEdit = new TextField(String.valueOf((int) finalRow.amount()));
                TextField catEdit = new TextField(finalRow.category());
                TextField notEdit = new TextField(finalRow.notes());
                Button saveEdit = new Button("حفظ"); saveEdit.getStyleClass().add("btn-primary");
                Label errLbl = new Label("");

                saveEdit.setOnAction(ev -> {
                    double amt;
                    String dir, pay;
                    try {
                        amt = Double.parseDouble(amtEdit.getText().trim());
                        dir = dirEdit.getValue().equals("دخول") ? "in" : "out";
                        pay = switch (payEdit.getValue()) {
                            case "بنك" -> "bank"; case "محفظة" -> "wallet"; case "شيك" -> "check"; default -> "cash";
                        };
                    } catch (NumberFormatException ex) { errLbl.setText("خطأ"); return; }

                    final double finalAmt = amt; final String finalDir = dir; final String finalPay = pay;
                    AsyncHelper.runVoid(
                            () -> {
                                try (Connection conn = DatabaseManager_online.getConnection();
                                     PreparedStatement stmt = conn.prepareStatement(
                                             "UPDATE vault_transactions SET direction=?, payment_type=?, amount=?, category=?, notes=? WHERE id=?")) {
                                    stmt.setString(1, finalDir); stmt.setString(2, finalPay); stmt.setDouble(3, finalAmt);
                                    stmt.setString(4, catEdit.getText().trim()); stmt.setString(5, notEdit.getText().trim());
                                    stmt.setInt(6, finalRow.id()); stmt.executeUpdate();
                                }
                            },
                            () -> {
                                reload(summaryCard, table, rows, treasuryId, filter, treasuryName);
                                ((javafx.stage.Stage) saveEdit.getScene().getWindow()).close();
                            },
                            error -> errLbl.setText("خطأ"),
                            saveEdit
                    );
                });

                VBox dl = new VBox(10,
                        new HBox(8, new VBox(4, new Label("الاتجاه:"), dirEdit), new VBox(4, new Label("نوع الدفع:"), payEdit)),
                        new Label("المبلغ:"), amtEdit,
                        new Label("التصنيف:"), catEdit,
                        new Label("ملاحظة:"), notEdit,
                        saveEdit, errLbl);
                dl.setPadding(new Insets(20));
                DialogHelper.create("تعديل معاملة", dl, 360, 320).show();
            });

            deleteBtn.setOnAction(e -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف المعاملة دي؟");
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        deleteBtn.setDisable(true);
                        AsyncHelper.runVoid(
                                () -> {
                                    try (Connection conn = DatabaseManager_online.getConnection();
                                         PreparedStatement stmt = conn.prepareStatement("DELETE FROM vault_transactions WHERE id=?")) {
                                        stmt.setInt(1, finalRow.id()); stmt.executeUpdate();
                                    }
                                },
                                () -> reload(summaryCard, table, rows, treasuryId, filter, treasuryName),
                                error -> reload(summaryCard, table, rows, treasuryId, filter, treasuryName)
                        );
                    }
                });
            });

            r.getChildren().addAll(dateLbl, dirLbl, payLbl, amtLbl, catLbl, notesLbl, editBtn, deleteBtn);
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
