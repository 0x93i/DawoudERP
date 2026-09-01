package com.daoud.ui;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HistoryTable {

    public static class Row {
        public final String[] values;
        public final String type;
        public Row(String type, String... values) {
            this.type = type;
            this.values = values;
        }
    }

    public static VBox build(String[] headers, double[] widths, List<Row> allRows, String[] filterTypes) {

        // ── الفلاتر ──
        HBox filtersBox = new HBox(8);
        filtersBox.setAlignment(Pos.CENTER_RIGHT);

        VBox tableBox = new VBox(0);
        tableBox.setStyle("-fx-border-color: #e0e0e0; -fx-border-radius: 0 0 6 6;");

        // زر "الكل"
        Button allBtn = filterBtn("الكل", true);
        filtersBox.getChildren().add(allBtn);

        // أزرار الفلاتر
        for (String type : filterTypes) {
            Button btn = filterBtn(type, false);
            filtersBox.getChildren().add(btn);
        }

        // رسم الجدول
        Runnable[] renderAll = {null};
        renderAll[0] = () -> renderTable(tableBox, headers, widths, allRows, null);
        renderAll[0].run();

        // أحداث الفلاتر
        allBtn.setOnAction(e -> {
            setActive(allBtn, filtersBox);
            renderTable(tableBox, headers, widths, allRows, null);
        });

        for (int i = 0; i < filterTypes.length; i++) {
            final String type = filterTypes[i];
            Button btn = (Button) filtersBox.getChildren().get(i + 1);
            btn.setOnAction(e -> {
                setActive(btn, filtersBox);
                renderTable(tableBox, headers, widths, allRows, r -> r.type.equals(type));
            });
        }

        // ── Header ──
        HBox header = buildHeader(headers, widths);

        VBox card = new VBox(0);
        card.getStyleClass().add("card");

        HBox topBar = new HBox(filtersBox);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setStyle("-fx-padding: 0 0 10 0;");

        card.getChildren().addAll(topBar, header, tableBox);
        return card;
    }

    private static void renderTable(VBox table, String[] headers, double[] widths,
                                    List<Row> rows, Predicate<Row> filter) {
        table.getChildren().clear();
        List<Row> filtered = filter == null ? rows : rows.stream().filter(filter).collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label("مفيش بيانات");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 16; -fx-font-size: 13px;");
            table.getChildren().add(empty);
            return;
        }

        boolean odd = true;
        for (Row row : filtered) {
            HBox r = new HBox();
            String bg = odd ? "#ffffff" : "#fafaf8";
            r.setStyle("-fx-background-color: " + bg + "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            for (int i = 0; i < row.values.length && i < widths.length; i++) {
                Label lbl = new Label(row.values[i]);
                lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
                lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
                r.getChildren().add(lbl);
            }
            table.getChildren().add(r);
        }
    }

    private static HBox buildHeader(String[] headers, double[] widths) {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10; -fx-background-radius: 6 6 0 0;");
        for (int i = 0; i < headers.length; i++) {
            Label lbl = new Label(headers[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static Button filterBtn(String text, boolean active) {
        Button btn = new Button(text);
        btn.getStyleClass().add(active ? "btn-primary" : "btn-default");
        btn.setStyle(btn.getStyle() + "-fx-font-size: 11px; -fx-padding: 4 10;");
        return btn;
    }

    private static void setActive(Button active, HBox box) {
        for (javafx.scene.Node n : box.getChildren()) {
            if (n instanceof Button b) {
                b.getStyleClass().removeAll("btn-primary", "btn-default");
                b.getStyleClass().add(b == active ? "btn-primary" : "btn-default");
            }
        }
    }
}