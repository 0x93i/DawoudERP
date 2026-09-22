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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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

        Button viewBtn = new Button("عرض التقرير"); viewBtn.getStyleClass().add("btn-primary");
        Label viewMsg = new Label("");

        Button exportBtn = new Button("تصدير لإكسيل"); exportBtn.getStyleClass().add("btn-default");
        Button printBtn = new Button("طباعة"); printBtn.getStyleClass().add("btn-default");
        exportBtn.setDisable(true);
        printBtn.setDisable(true);

        VBox reportCard = new VBox(8); reportCard.getStyleClass().add("card");
        reportCard.getChildren().addAll(reportTitleLabel, buildReportHeader(), tableBody, totalsLabel);

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
                        reportTitleLabel.setText((isFactory ? "تقرير مصنع: " : "تقرير مورد: ") + entity +
                                "   (من " + from + " لحد " + to + ")");
                        renderReportTable(tableBody, totalsLabel, rows);
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
            exportToCsv(owner, reportTitleLabel.getText(), currentRows);
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

    private static HBox buildReportHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"التاريخ", "الملاحظات", "اسم المورد", "الوزن القائم", "نسبة الخصم", "كمية الخصم", "سعر الكيلو", "المبلغ"};
        double[] widths = {90, 110, 130, 100, 90, 100, 100, 100};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void renderReportTable(VBox table, Label totalsLabel, List<ReportRow> rows) {
        table.getChildren().clear();
        double[] widths = {90, 110, 130, 100, 90, 100, 100, 100};
        boolean odd = true;
        double totalGross = 0, totalAmount = 0;

        for (ReportRow row : rows) {
            totalGross += row.grossWeight();
            totalAmount += row.amount();

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 7 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            Label dateLbl = new Label(row.date());
            dateLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            dateLbl.setMinWidth(widths[0]); dateLbl.setPrefWidth(widths[0]);

            Label notesLbl = new Label(row.notes());
            notesLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
            notesLbl.setMinWidth(widths[1]); notesLbl.setPrefWidth(widths[1]);

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

            r.getChildren().addAll(dateLbl, notesLbl, partyLbl, grossLbl, pctLbl, dedKgLbl, priceLbl, amtLbl);
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

    // ── تصدير التقرير لملف إكسيل (CSV بترميز UTF-8 عشان العربي يظهر صح) ──
    private static void exportToCsv(Stage owner, String title, List<ReportRow> rows) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("حفظ التقرير");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel/CSV", "*.csv"));
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        chooser.setInitialFileName((safeName.isEmpty() ? "تقرير" : safeName) + ".csv");
        File file = chooser.showSaveDialog(owner);
        if (file == null) return;

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM عشان إكسيل يفتح العربي صح
            w.write(title.replace(",", " ") + "\n\n");
            w.write("التاريخ,الملاحظات,اسم المورد,الوزن القائم (كيلو),نسبة الخصم (%),كمية الخصم (كيلو),سعر الكيلو (جنيه),المبلغ (جنيه)\n");
            double totalGross = 0, totalAmount = 0;
            for (ReportRow row : rows) {
                totalGross += row.grossWeight();
                totalAmount += row.amount();
                w.write(String.format("%s,%s,%s,%.1f,%.1f,%.1f,%.2f,%.0f\n",
                        csv(row.date()), csv(row.notes()), csv(row.partyName()),
                        row.grossWeight(), row.pct(), row.dedKg(), row.price(), row.amount()));
            }
            w.write("\nالإجمالي,,,," + String.format("%.1f", totalGross) + ",,," + String.format("%.0f", totalAmount) + "\n");
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "حصل خطأ أثناء حفظ الملف: " + ex.getMessage()).showAndWait();
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
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
