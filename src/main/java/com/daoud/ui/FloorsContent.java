package com.daoud.ui;

import com.daoud.dao.FloorDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Warehouse;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

public class FloorsContent {

    private record FloorsData(List<FloorDAO.FloorRow> floors, List<Warehouse> warehouses) {}

    public static Node build(int userId, String username, String role) {

        VBox totalsRow = new VBox(0);
        VBox tableBody = new VBox(0);
        tableBody.getChildren().add(new Label("جاري التحميل..."));

        HBox filtersBox = new HBox(6);
        filtersBox.setAlignment(Pos.CENTER_RIGHT);

        List<FloorDAO.FloorRow> allFloors = new ArrayList<>();
        List<Warehouse> warehouses = new ArrayList<>();

        AsyncHelper.run(
                () -> new FloorsData(FloorDAO.getAllFloors(), WarehouseDAO.getAllWarehouses()),
                data -> {
                    allFloors.clear(); allFloors.addAll(data.floors());
                    warehouses.clear(); warehouses.addAll(data.warehouses());
                    buildFilters(filtersBox, allFloors, warehouses, totalsRow, tableBody, userId, username, role);
                    applyFilter(null, allFloors, warehouses, totalsRow, tableBody, userId, username, role);
                },
                error -> tableBody.getChildren().setAll(new Label("حصل خطأ في تحميل الأرضيات"))
        );

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        Label title = new Label("الأرضيات"); title.getStyleClass().add("card-title");
        card.getChildren().addAll(title, filtersBox, totalsRow, buildHeader(), tableBody);

        return new VBox(12, card);
    }

    // filter: null = الكل، -1 = عام، أو id المخزن
    private static void buildFilters(HBox filtersBox, List<FloorDAO.FloorRow> allFloors, List<Warehouse> warehouses,
                                     VBox totalsRow, VBox tableBody, int userId, String username, String role) {
        filtersBox.getChildren().clear();

        List<Button> buttons = new ArrayList<>();
        Button allBtn = filterBtn("الكل");
        Button generalBtn = filterBtn("موردين عام");
        buttons.add(allBtn); buttons.add(generalBtn);

        allBtn.setOnAction(e -> {
            setActive(allBtn, buttons);
            applyFilter(null, allFloors, warehouses, totalsRow, tableBody, userId, username, role);
        });
        generalBtn.setOnAction(e -> {
            setActive(generalBtn, buttons);
            applyFilter(-1, allFloors, warehouses, totalsRow, tableBody, userId, username, role);
        });

        for (Warehouse w : warehouses) {
            Button wBtn = filterBtn(w.getName());
            buttons.add(wBtn);
            wBtn.setOnAction(e -> {
                setActive(wBtn, buttons);
                applyFilter(w.getId(), allFloors, warehouses, totalsRow, tableBody, userId, username, role);
            });
        }

        filtersBox.getChildren().addAll(buttons);
        setActive(allBtn, buttons);
    }

    private static void applyFilter(Integer warehouseFilter, List<FloorDAO.FloorRow> allFloors, List<Warehouse> warehouses,
                                    VBox totalsRow, VBox tableBody, int userId, String username, String role) {
        List<FloorDAO.FloorRow> filtered = new ArrayList<>();
        for (FloorDAO.FloorRow f : allFloors) {
            if (warehouseFilter == null || f.warehouseId() == warehouseFilter) filtered.add(f);
        }
        renderTotals(totalsRow, filtered, warehouseFilter, warehouses);
        renderTable(tableBody, filtered, userId, username, role);
    }

    private static void renderTotals(VBox totalsRow, List<FloorDAO.FloorRow> rows, Integer warehouseFilter, List<Warehouse> warehouses) {
        totalsRow.getChildren().clear();

        double total = 0;
        for (FloorDAO.FloorRow f : rows) total += f.floorAmount();

        String label;
        if (warehouseFilter == null) label = "إجمالي أرضيات كل الموردين";
        else if (warehouseFilter == -1) label = "إجمالي أرضيات الموردين العام";
        else {
            String name = "المخزن";
            for (Warehouse w : warehouses) if (w.getId() == warehouseFilter) { name = w.getName(); break; }
            label = "إجمالي أرضيات موردي: " + name;
        }

        Label nameLbl = new Label(label);
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888780;");
        Label totalLbl = new Label(String.format("%.0f جنيه", total));
        totalLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " +
                (total >= 0 ? "#3B6D11" : "#A32D2D") + ";");

        VBox box = new VBox(2, nameLbl, totalLbl);
        box.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 10 14; -fx-background-radius: 8;");
        totalsRow.getChildren().add(box);
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: white; -fx-border-color: #c0c0c0;");
        return btn;
    }

    private static void setActive(Button active, List<Button> all) {
        for (Button b : all) {
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: #3B6D11; -fx-text-fill: white;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 10; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18;");
        }
    }

    private static HBox buildHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10; -fx-background-radius: 6;");
        String[] cols = {"رقم", "المورد", "رصيد الأرضية", ""};
        double[] widths = {50, 260, 150, 100};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void renderTable(VBox table, List<FloorDAO.FloorRow> floors, int userId, String username, String role) {
        table.getChildren().clear();

        if (floors.isEmpty()) {
            Label empty = new Label("مفيش موردين");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
            table.getChildren().add(empty);
            return;
        }

        boolean odd = true;
        for (FloorDAO.FloorRow f : floors) {
            HBox row = new HBox(4);
            row.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 9 10; -fx-border-color: transparent transparent #f0f0f0 transparent;");
            odd = !odd;

            Label numLbl = new Label(f.supplierNo() > 0 ? String.valueOf(f.supplierNo()) : "—");
            numLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #5f5e5a;");
            numLbl.setMinWidth(50); numLbl.setPrefWidth(50);

            Label nameLbl = new Label(f.name());
            nameLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #1a1a18;");
            nameLbl.setMinWidth(260); nameLbl.setPrefWidth(260);

            Label amountLbl = new Label(String.format("%.0f جنيه", f.floorAmount()));
            amountLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " +
                    (f.floorAmount() >= 0 ? "#3B6D11" : "#A32D2D") + ";");
            amountLbl.setMinWidth(150); amountLbl.setPrefWidth(150);

            Button openBtn = new Button("فتح");
            openBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 14; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
            openBtn.setMinWidth(100); openBtn.setPrefWidth(100);
            openBtn.setOnAction(e -> {
                MainLayout.loadContent(FloorDetailContent.build(userId, username, role, f.supplierId(), f.name()));
                MainLayout.setTitle("أرضية: " + f.name());
            });

            row.getChildren().addAll(numLbl, nameLbl, amountLbl, openBtn);
            table.getChildren().add(row);
        }
    }
}
