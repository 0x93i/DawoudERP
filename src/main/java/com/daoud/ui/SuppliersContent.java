package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import com.daoud.db.DatabaseManager_online;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SuppliersContent {

    record SupplierRow(Supplier supplier, String warehouseName) {}

    public static Node build(int userId, String username, String role) {

        VBox tableBody = new VBox(0);
        List<SupplierRow> allRows = new ArrayList<>();
        loadSupplierRows(allRows, userId, role);

        // ── فلاتر ──
        List<Button> allFilterBtns = new ArrayList<>();

        Button allBtn = filterBtn("الكل");
        Button warehouseBtn = filterBtn("مخازن");
        Button generalBtn = filterBtn("عام");
        allFilterBtns.add(allBtn);
        allFilterBtns.add(warehouseBtn);
        allFilterBtns.add(generalBtn);

        // فلتر لكل مخزن
        List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
        List<Button> warehouseBtns = new ArrayList<>();
        for (Warehouse w : warehouses) {
            Button wBtn = filterBtn("🏭 " + w.getName());
            allFilterBtns.add(wBtn);
            warehouseBtns.add(wBtn);
            final String wName = w.getName();
            wBtn.setOnAction(e -> {
                setActiveFromList(wBtn, allFilterBtns);
                renderTable(tableBody, allRows, "wh:" + wName, userId, username, role);
            });
        }

        Button[] allBtnsArr = allFilterBtns.toArray(new Button[0]);
        setActiveFromList(allBtn, allFilterBtns);

        allBtn.setOnAction(e -> {
            setActiveFromList(allBtn, allFilterBtns);
            renderTable(tableBody, allRows, null, userId, username, role);
        });
        warehouseBtn.setOnAction(e -> {
            setActiveFromList(warehouseBtn, allFilterBtns);
            renderTable(tableBody, allRows, "warehouse", userId, username, role);
        });
        generalBtn.setOnAction(e -> {
            setActiveFromList(generalBtn, allFilterBtns);
            renderTable(tableBody, allRows, "general", userId, username, role);
        });

        renderTable(tableBody, allRows, null, userId, username, role);

        HBox filtersBox = new HBox(6);
        filtersBox.setAlignment(Pos.CENTER_RIGHT);
        filtersBox.getChildren().addAll(allBtn, warehouseBtn, generalBtn);
        filtersBox.getChildren().addAll(warehouseBtns);

        Button addBtn = new Button("إضافة مورد");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setDisable(!role.equals("admin"));
        addBtn.setOnAction(e -> showAddDialog(userId, username, role, allRows, tableBody, allFilterBtns));

        HBox topBar = new HBox(10, filtersBox,
                new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                addBtn);
        topBar.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label cardTitle = new Label("قائمة الموردين");
        cardTitle.getStyleClass().add("card-title");
        card.getChildren().addAll(cardTitle, topBar, buildTableHeader(), tableBody);

        return new VBox(12, card);
    }

    private static void loadSupplierRows(List<SupplierRow> rows, int userId, String role) {
        rows.clear();
        List<Supplier> suppliers;
        if (role.equals("admin")) {
            suppliers = SupplierDAO.getAllSuppliers();
        } else {
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            suppliers = w != null ? WarehouseDAO.getSuppliersByWarehouse(w.getId()) : new ArrayList<>();
        }
        for (Supplier s : suppliers) {
            String warehouseName = getSupplierWarehouseName(s.getId());
            rows.add(new SupplierRow(s, warehouseName));
        }
    }

    private static String getSupplierWarehouseName(int supplierId) {
        String sql = "SELECT w.name FROM warehouses w " +
                "JOIN warehouse_suppliers ws ON w.id = ws.warehouse_id " +
                "WHERE ws.supplier_id = ? LIMIT 1";
        try (Connection conn = DatabaseManager_online.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, supplierId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"اسم المورد", "التليفون", "القطاع", "الأرضية", "النوع", ""};
        double[] widths = {160, 120, 120, 100, 140, 120};
        for (int i = 0; i < cols.length; i++) {
            Label lbl = new Label(cols[i]);
            lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #888780;");
            lbl.setMinWidth(widths[i]); lbl.setPrefWidth(widths[i]);
            header.getChildren().add(lbl);
        }
        return header;
    }

    private static void renderTable(VBox table, List<SupplierRow> rows, String filter,
                                    int userId, String username, String role) {
        table.getChildren().clear();
        double[] widths = {160, 120, 120, 100, 140, 120};
        boolean odd = true, hasRows = false;

        for (SupplierRow row : rows) {
            boolean isWarehouse = row.warehouseName() != null;

            if (filter != null) {
                if (filter.equals("warehouse") && !isWarehouse) continue;
                if (filter.equals("general") && isWarehouse) continue;
                if (filter.startsWith("wh:") && !filter.equals("wh:" + row.warehouseName())) continue;
            }
            hasRows = true;

            HBox r = new HBox(4);
            r.setStyle("-fx-background-color: " + (odd ? "#ffffff" : "#fafaf8") +
                    "; -fx-padding: 8 10; -fx-border-color: transparent transparent #f0f0f0 transparent; -fx-cursor: hand;");
            odd = !odd;

            Label nameLbl = new Label(row.supplier().getName());
            nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
            nameLbl.setMinWidth(widths[0]); nameLbl.setPrefWidth(widths[0]);

            Label phoneLbl = new Label(row.supplier().getPhone() != null && !row.supplier().getPhone().isEmpty() ?
                    row.supplier().getPhone() : "—");
            phoneLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            phoneLbl.setMinWidth(widths[1]); phoneLbl.setPrefWidth(widths[1]);

            Label sectorLbl = new Label(row.supplier().getSector() != null && !row.supplier().getSector().isEmpty() ?
                    row.supplier().getSector() : "—");
            sectorLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            sectorLbl.setMinWidth(widths[2]); sectorLbl.setPrefWidth(widths[2]);

            Label floorLbl = new Label(String.format("%.0f جنيه", row.supplier().getFloorAmount()));
            floorLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            floorLbl.setMinWidth(widths[3]); floorLbl.setPrefWidth(widths[3]);

            Label typeLbl;
            if (isWarehouse) {
                typeLbl = new Label("🏭 " + row.warehouseName());
                typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 4; " +
                        "-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11;");
            } else {
                typeLbl = new Label("🌍 مورد عام");
                typeLbl.setStyle("-fx-font-size: 11px; -fx-padding: 3 10; -fx-background-radius: 4; " +
                        "-fx-background-color: #E6F1FB; -fx-text-fill: #185FA5;");
            }
            typeLbl.setMinWidth(widths[4]); typeLbl.setPrefWidth(widths[4]);

            Button openBtn = new Button("فتح الحساب");
            openBtn.setStyle("-fx-background-color: #3B6D11; -fx-text-fill: white; -fx-font-size: 11px; " +
                    "-fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
            openBtn.setMinWidth(widths[5]); openBtn.setPrefWidth(widths[5]);
            final SupplierRow finalRow = row;
            openBtn.setOnAction(e -> {
                MainLayout.loadContent(SupplierDetailContent.build(userId, username, role, finalRow.supplier()));
                MainLayout.setTitle("حساب: " + finalRow.supplier().getName());
            });

            r.getChildren().addAll(nameLbl, phoneLbl, sectorLbl, floorLbl, typeLbl, openBtn);
            table.getChildren().add(r);
        }

        if (!hasRows) {
            Label empty = new Label("مفيش موردين");
            empty.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
            table.getChildren().add(empty);
        }
    }

    private static void showAddDialog(int userId, String username, String role,
                                      List<SupplierRow> allRows, VBox tableBody,
                                      List<Button> allFilterBtns) {
        TextField nameField = new TextField(); nameField.setPromptText("اسم المورد");
        TextField phoneField = new TextField(); phoneField.setPromptText("رقم التليفون");
        TextField sectorField = new TextField(); sectorField.setPromptText("القطاع");
        TextField floorField = new TextField(); floorField.setPromptText("الأرضية");

        List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
        ComboBox<String> warehouseCombo = new ComboBox<>();
        warehouseCombo.getItems().add("مورد عام (بدون مخزن)");
        for (Warehouse w : warehouses) warehouseCombo.getItems().add(w.getId() + "|" + w.getName());
        warehouseCombo.setValue("مورد عام (بدون مخزن)");
        warehouseCombo.setMaxWidth(Double.MAX_VALUE);

        Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
        Label errorLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("الاسم:"), nameField,
                new Label("التليفون:"), phoneField,
                new Label("القطاع:"), sectorField,
                new Label("الأرضية:"), floorField,
                new Label("المخزن:"), warehouseCombo,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));

        Stage dialog = DialogHelper.create("إضافة مورد جديد", layout, 360, 380);

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("الاسم مطلوب"); return; }
            double floor = 0;
            try {
                if (!floorField.getText().trim().isEmpty())
                    floor = Double.parseDouble(floorField.getText().trim());
            } catch (NumberFormatException ex) { errorLbl.setText("الأرضية لازم رقم"); return; }

            SupplierDAO.addSupplier(name, phoneField.getText().trim(), sectorField.getText().trim(), floor, userId);

            String selected = warehouseCombo.getValue();
            if (selected != null && selected.contains("|")) {
                int warehouseId = Integer.parseInt(selected.split("\\|")[0]);
                List<Supplier> all = SupplierDAO.getAllSuppliers();
                for (Supplier s : all) {
                    if (s.getName().equals(name)) {
                        WarehouseDAO.assignSupplierToWarehouse(warehouseId, s.getId());
                        break;
                    }
                }
            }

            loadSupplierRows(allRows, userId, role);
            renderTable(tableBody, allRows, null, userId, username, role);
            dialog.close();
        });

        dialog.show();
    }

    private static Button filterBtn(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
        return btn;
    }

    private static void setActiveFromList(Button active, List<Button> all) {
        for (Button b : all) {
            if (b == active)
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #3B6D11; -fx-text-fill: white; -fx-cursor: hand;");
            else
                b.setStyle("-fx-font-size: 11px; -fx-padding: 4 12; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: white; -fx-border-color: #c0c0c0; -fx-text-fill: #1a1a18; -fx-cursor: hand;");
        }
    }
}