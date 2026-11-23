package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.dao.SupplierDAO;
import com.daoud.model.Factory;
import com.daoud.model.ReportRow;
import com.daoud.model.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.daoud.db.DatabaseManager_online;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * شاشة "التقارير": تقرير مصنع (بسعر بيع البضاعة للمصنع) أو تقرير مورد
 * (بسعر شراء البضاعة من المورد)، لفترة معيّنة، قابل للعرض على الشاشة
 * وللتصدير لإكسيل وللطباعة.
 */
public class ReportsContent {

    public static Node build(int userId, String username, String role) {

        RadioButton factoryRadio = new RadioButton("تقرير مصنع");
        RadioButton supplierRadio = new RadioButton("تقرير مورد");
        ToggleGroup typeGroup = new ToggleGroup();
        factoryRadio.setToggleGroup(typeGroup);
        supplierRadio.setToggleGroup(typeGroup);
        factoryRadio.setSelected(true);

        ComboBox<Object> entityCombo = new ComboBox<>();
        entityCombo.setMaxWidth(Double.MAX_VALUE);
        entityCombo.setPromptText("اختار مصنع/مورد");

        DatePicker fromPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker toPicker = new DatePicker(LocalDate.now());
        fromPicker.setMaxWidth(Double.MAX_VALUE);
        toPicker.setMaxWidth(Double.MAX_VALUE);

        Label entityLoadErr = new Label("");
        entityLoadErr.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: 11px;");

        Runnable loadEntities = () -> {
            entityCombo.getItems().clear();
            boolean isFactory = factoryRadio.isSelected();
            AsyncHelper.run(
                    () -> isFactory
                            ? new ArrayList<Object>(FactoryDAO.getAllFactories())
                            : new ArrayList<Object>(SupplierDAO.getAllSuppliers()),
                    items -> {
                        entityCombo.getItems().setAll(items);
                        if (!items.isEmpty()) entityCombo.getSelectionModel().selectFirst();
                    },
                    error -> entityLoadErr.setText("حصل خطأ في تحميل القائمة")
            );
        };

        factoryRadio.setOnAction(e -> loadEntities.run());
        supplierRadio.setOnAction(e -> loadEntities.run());
        loadEntities.run();

        // ── نتيجة التقرير ──
        VBox tableBody = new VBox(0);
        Label totalsLabel = new Label("");
        totalsLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a18; -fx-padding: 8 10 0 10;");

        Label reportTitleLabel = new Label("مفيش تقرير معروض دلوقتي");
        reportTitleLabel.getStyleClass().add("card-title");

        List<ReportRow> currentRows = new ArrayList<>();
        boolean[] currentIsFactory = {true};

        Button viewBtn = new Button("عرض التقرير"); viewBtn.getStyleClass().add("btn-primary");
        Label viewMsg = new Label("");

        Button exportBtn = new Button("تصدير لإكسيل"); exportBtn.getStyleClass().add("btn-default");
        Button printBtn = new Button("طباعة"); printBtn.getStyleClass().add("btn-default");
        exportBtn.setDisable(true);
        printBtn.setDisable(true);

        HBox reportHeader = buildReportHeader(true);
        VBox reportCard = new VBox(8); reportCard.getStyleClass().add("card");
        reportCard.getChildren().addAll(reportTitleLabel, reportHeader, tableBody, totalsLabel);

        viewBtn.setOnAction(e -> {
            Object entity = entityCombo.getValue();
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (entity == null) { viewMsg.setText("اختار مصنع أو مورد الأول"); return; }
            if (from == null || to == null) { viewMsg.setText("حدد الفترة (من - لحد)"); return; }
            if (from.isAfter(to)) { viewMsg.setText("تاريخ \"من\" لازم يكون قبل تاريخ \"لحد\""); return; }

            boolean isFactory = factoryRadio.isSelected();
            viewMsg.setText("جاري التحميل...");

            AsyncHelper.run(
                    () -> {
                        if (isFactory) {
                            Factory f = (Factory) entity;
                            return FactoryDAO.getFactoryReport(f.getId(), from, to);
                        } else {
                            Supplier s = (Supplier) entity;
                            return SupplierDAO.getSupplierReport(s.getId(), s.getName(), from, to);
                        }
                    },
                    rows -> {
                        currentRows.clear();
                        currentRows.addAll(rows);
                        currentIsFactory[0] = isFactory;
                        updateReportHeader(reportHeader, isFactory);
                        reportTitleLabel.setText((isFactory ? "تقرير مصنع: " : "تقرير مورد: ") + entity +
                                "   (من " + from + " لحد " + to + ")");
                        renderReportTable(tableBody, totalsLabel, currentRows, isFactory);
                        viewMsg.setText(rows.isEmpty() ? "مفيش بيانات في الفترة دي" : "تم ✓ (" + rows.size() + " سجل)");
                        exportBtn.setDisable(rows.isEmpty());
                        printBtn.setDisable(rows.isEmpty());
                    },
                    error -> viewMsg.setText("حصل خطأ في تحميل التقرير"),
                    viewBtn
            );
        });

        exportBtn.setOnAction(e -> {
            Stage owner = (Stage) exportBtn.getScene().getWindow();
            exportToXlsx(owner, reportTitleLabel.getText(), currentRows, currentIsFactory[0]);
        });

        printBtn.setOnAction(e -> {
            Stage owner = (Stage) printBtn.getScene().getWindow();
            printNode(owner, reportCard);
        });

        // ── الفورم ──
        HBox typeRow = new HBox(16, factoryRadio, supplierRadio);
        typeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox filtersCard = new VBox(10); filtersCard.getStyleClass().add("card");
        Label filtersTitle = new Label("إعدادات التقرير"); filtersTitle.getStyleClass().add("card-title");
        filtersCard.getChildren().addAll(
                filtersTitle,
                typeRow,
                new VBox(4, new Label("المصنع/المورد:"), entityCombo, entityLoadErr),
                new HBox(10,
                        new VBox(4, new Label("من تاريخ:"), fromPicker),
                        new VBox(4, new Label("لحد تاريخ:"), toPicker)),
                new HBox(10, viewBtn, exportBtn, printBtn),
                viewMsg
        );

        return new VBox(12, filtersCard, reportCard);
    }

    private static HBox buildReportHeader(boolean isFactory) {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "الملاحظات", partyColumnTitle(isFactory), "الوزن القائم", "نسبة الخصم", "كمية الخصم", "سعر الكيلو", "المبلغ"};
        double[] widths = {90, 110, 130, 100, 90, 100, 100, 100};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    /** بيحدّث عنوان عمود "المورد/المصنع" في الهيدر حسب نوع التقرير المعروض دلوقتي. */
    private static void updateReportHeader(HBox header, boolean isFactory) {
        if (header.getChildren().size() > 2 && header.getChildren().get(2) instanceof Label lbl) {
            lbl.setText(partyColumnTitle(isFactory));
        }
    }

    private static String partyColumnTitle(boolean isFactory) {
        // تقرير مصنع: كل صف فيه اسم المورد اللي ودّى البضاعة دي.
        // تقرير مورد: كل صف فيه اسم المصنع اللي البضاعة دي راحتله.
        return isFactory ? "المورد الموصل" : "المصنع المستلم";
    }

    private static void renderReportTable(VBox table, Label totalsLabel, List<ReportRow> rows, boolean isFactory) {
        table.getChildren().clear();
        double[] widths = {90, 110, 130, 100, 90, 100, 100, 100};
        boolean odd = true;
        double totalGross = 0, totalAmount = 0;

        for (int i = 0; i < rows.size(); i++) {
            ReportRow row = rows.get(i);
            final int rowIndex = i;
            totalGross += row.grossWeight();
            totalAmount += row.amount();

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            // خانة الملاحظات مفتوحة للكتابة: أي تعديل بيتسجل في قاعدة البيانات
            // تلقائيًا لما تخرج من الخانة (أو تدوس Enter).
            TextField notesField = new TextField(row.notes());
            notesField.setStyle("-fx-font-size: 12px;");
            notesField.setMinWidth(widths[1]); notesField.setPrefWidth(widths[1]);
            Runnable saveIfChanged = () -> {
                String newNotes = notesField.getText();
                ReportRow current = rows.get(rowIndex);
                if (!newNotes.equals(current.notes())) {
                    rows.set(rowIndex, new ReportRow(current.id(), current.date(), newNotes, current.partyName(),
                            current.grossWeight(), current.pct(), current.dedKg(), current.price(), current.amount()));
                    saveReportNote(isFactory, current.id(), newNotes);
                }
            };
            notesField.setOnAction(ev -> saveIfChanged.run());
            notesField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (!isFocused) saveIfChanged.run();
            });

            Label partyLbl = new Label(row.partyName());
            partyLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            partyLbl.setMinWidth(widths[2]); partyLbl.setPrefWidth(widths[2]);

            Label grossLbl = new Label(String.format("%.1f كيلو", row.grossWeight()));
            grossLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            grossLbl.setMinWidth(widths[3]); grossLbl.setPrefWidth(widths[3]);

            Label pctLbl = new Label(String.format("%.1f%%", row.pct()));
            pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            pctLbl.setMinWidth(widths[4]); pctLbl.setPrefWidth(widths[4]);

            Label dedKgLbl = new Label(String.format("%.1f كيلو", row.dedKg()));
            dedKgLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            dedKgLbl.setMinWidth(widths[5]); dedKgLbl.setPrefWidth(widths[5]);

            Label priceLbl = new Label(String.format("%.2f جنيه", row.price()));
            priceLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            priceLbl.setMinWidth(widths[6]); priceLbl.setPrefWidth(widths[6]);

            Label amtLbl = new Label(String.format("%.0f جنيه", row.amount()));
            amtLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
            amtLbl.setMinWidth(widths[7]); amtLbl.setPrefWidth(widths[7]);

            r.getChildren().addAll(dateLbl, notesField, partyLbl, grossLbl, pctLbl, dedKgLbl, priceLbl, amtLbl);
            table.getChildren().add(r);
        }

        if (rows.isEmpty()) {
            Label empty = new Label("مفيش بيانات في الفترة المحددة");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
            table.getChildren().add(empty);
            totalsLabel.setText("");
        } else {
            totalsLabel.setText(String.format("الإجمالي: %.1f كيلو  —  %.0f جنيه", totalGross, totalAmount));
        }
    }

    /** بيحفظ الملاحظة الجديدة على نفس سطر الحركة في جدوله الأصلي (حسب نوع التقرير). */
    private static void saveReportNote(boolean isFactory, int rowId, String notes) {
        String table = isFactory ? "factory_shipments" : "supplier_transactions";
        String sql = "UPDATE " + table + " SET notes = ? WHERE id = ?";
        AsyncHelper.runVoid(
                () -> {
                    try (Connection conn = DatabaseManager_online.getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, notes);
                        stmt.setInt(2, rowId);
                        stmt.executeUpdate();
                    }
                },
                () -> {},
                error -> System.err.println("خطأ في حفظ الملاحظة: " + error.getMessage())
        );
    }

    // ── تصدير التقرير لملف إكسيل حقيقي (.xlsx) بدل CSV ──
    // استخدمنا .xlsx بدل CSV عشان CSV بتتفتح غلط (كل البيانات في عمود واحد)
    // على أي جهاز الفاصلة الافتراضية فيه مش "," (زي أجهزة كتير بالعربي بتبقى
    // "؛")، بالإضافة إن .xlsx بيحافظ على تنسيق فعلي للأعمدة والعناوين.
    private static void exportToXlsx(Stage owner, String title, List<ReportRow> rows, boolean isFactory) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("حفظ التقرير");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ملف إكسيل", "*.xlsx"));
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        chooser.setInitialFileName((safeName.isEmpty() ? "تقرير" : safeName) + ".xlsx");
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("تقرير");
            sheet.setRightToLeft(true);

            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            org.apache.poi.ss.usermodel.CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);

            org.apache.poi.ss.usermodel.Font headerFont = wb.createFont();
            headerFont.setBold(true);
            org.apache.poi.ss.usermodel.CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.ss.usermodel.CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(headerFont);

            int r = 0;
            org.apache.poi.ss.usermodel.Row titleRow = sheet.createRow(r++);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            r++; // صف فاضي

            String[] cols = {"التاريخ", "الملاحظات", partyColumnTitle(isFactory), "الوزن القائم (كيلو)",
                    "نسبة الخصم (%)", "كمية الخصم (كيلو)", "سعر الكيلو (جنيه)", "المبلغ (جنيه)"};
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            double totalGross = 0, totalAmount = 0;
            for (ReportRow row : rows) {
                totalGross += row.grossWeight();
                totalAmount += row.amount();
                org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(row.date());
                dataRow.createCell(1).setCellValue(row.notes());
                dataRow.createCell(2).setCellValue(row.partyName());
                dataRow.createCell(3).setCellValue(row.grossWeight());
                dataRow.createCell(4).setCellValue(row.pct());
                dataRow.createCell(5).setCellValue(row.dedKg());
                dataRow.createCell(6).setCellValue(row.price());
                dataRow.createCell(7).setCellValue(row.amount());
            }

            org.apache.poi.ss.usermodel.Row totalsRow = sheet.createRow(r);
            org.apache.poi.ss.usermodel.Cell totalsLbl = totalsRow.createCell(0);
            totalsLbl.setCellValue("الإجمالي");
            totalsLbl.setCellStyle(boldStyle);
            org.apache.poi.ss.usermodel.Cell totalsGrossCell = totalsRow.createCell(3);
            totalsGrossCell.setCellValue(totalGross);
            totalsGrossCell.setCellStyle(boldStyle);
            org.apache.poi.ss.usermodel.Cell totalsAmountCell = totalsRow.createCell(7);
            totalsAmountCell.setCellValue(totalAmount);
            totalsAmountCell.setCellStyle(boldStyle);

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                wb.write(out);
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "حصل خطأ أثناء حفظ الملف: " + ex.getMessage()).showAndWait();
        }
    }

    // ── طباعة التقرير زي ما هو ظاهر على الشاشة ──
    private static void printNode(Stage owner, Node node) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Alert(Alert.AlertType.ERROR, "مفيش طابعة متاحة على الجهاز").showAndWait();
            return;
        }
        boolean proceed = job.showPrintDialog(owner);
        if (!proceed) return;
        boolean ok = job.printPage(node);
        if (ok) job.endJob();
        else new Alert(Alert.AlertType.ERROR, "حصل خطأ أثناء الطباعة").showAndWait();
    }
}