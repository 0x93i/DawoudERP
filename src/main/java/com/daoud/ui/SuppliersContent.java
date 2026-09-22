package com.daoud.ui;

import com.daoud.dao.SupplierDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import com.daoud.db.DatabaseManager_online;
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

    /** نتيجة تحميل الشاشة كلها مرة واحدة في الخلفية. */
    private record InitialData(List<Warehouse> warehouses, List<SupplierRow> rows) {}

    public static Node build(int userId, String username, String role) {

        VBox root = new VBox(12);

        VBox loadingCard = new VBox(10);
        loadingCard.getStyleClass().add("card");
        Label loadingLbl = new Label("جاري تحميل الموردين...");
        loadingLbl.setStyle("-fx-text-fill: #888780; -fx-padding: 20; -fx-font-size: 13px;");
        loadingCard.getChildren().add(loadingLbl);
        root.getChildren().add(loadingCard);

        // ── تحميل بيانات الشاشة (المخازن + الموردين) في الخلفية عشان الواجهة متفريزش ──
        AsyncHelper.run(
                () -> {
                    List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
                    List<SupplierRow> rows = new ArrayList<>();
                    loadSupplierRows(rows, userId, role);
                    return new InitialData(warehouses, rows);
                },
                data -> {
                    root.getChildren().setAll(buildCard(data.warehouses(), data.rows(), userId, username, role));
                },
                error -> {
                    Label errLbl = new Label("حصل خطأ في تحميل الموردين، حاول تفتح الشاشة تاني");
                    errLbl.setStyle("-fx-text-fill: #b91c1c; -fx-padding: 20; -fx-font-size: 13px;");
                    root.getChildren().setAll(errLbl);
                }
        );

        return root;
    }

    private static Node buildCard(List<Warehouse> warehouses, List<SupplierRow> allRows,
                                   int userId, String username, String role) {

        VBox tableBody = new VBox(0);

        // ── فلاتر ──
        List<Button> allFilterBtns = new ArrayList<>();

        Button allBtn = filterBtn("الكل");
        Button warehouseBtn = filterBtn("مخازن");
        Button generalBtn = filterBtn("عام");
        allFilterBtns.add(allBtn);
        allFilterBtns.add(warehouseBtn);
        allFilterBtns.add(generalBtn);

        // فلتر لكل مخزن
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
        if (role.equals("admin")) {
            // استعلام واحد بيرجع كل الموردين + اسم مخزن كل واحد فيهم مع بعض
            // (بدل ما كان بيعمل استعلام منفصل لكل مورد لوحده - ده كان أبطأ حاجة في الشاشة)
            rows.addAll(loadAllSupplierRowsWithWarehouse());
        } else {
            // مسؤول المخزن أصلاً بيشوف موردين مخزنه بس، يعني اسم المخزن معروف من غير أي استعلام إضافي
            Warehouse w = WarehouseDAO.getWarehouseByManager(userId);
            if (w != null) {
                List<Supplier> suppliers = WarehouseDAO.getSuppliersByWarehouse(w.getId());
                for (Supplier s : suppliers) rows.add(new SupplierRow(s, w.getName()));
            }
        }
    }

    private static List<SupplierRow> loadAllSupplierRowsWithWarehouse() {
        List<SupplierRow> rows = new ArrayList<>();
        String sql = """
            SELECT s.id, s.name, s.phone, s.sector, s.floor_amount, s.floor_date, s.supplier_no,
                   (SELECT w.name FROM warehouses w
                    JOIN warehouse_suppliers ws ON w.id = ws.warehouse_id
                    WHERE ws.supplier_id = s.id LIMIT 1) AS warehouse_name
            FROM suppliers s
            ORDER BY s.supplier_no NULLS LAST, s.name
        """;
        try (Connection conn = DatabaseManager_online.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Supplier s = new Supplier(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("sector"),
                        rs.getDouble("floor_amount"),
                        rs.getString("floor_date"),
                        rs.getInt("supplier_no")
                );
                rows.add(new SupplierRow(s, rs.getString("warehouse_name")));
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        return rows;
    }

    private static HBox buildTableHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #f5f5f3; -fx-padding: 8 10;");
        String[] cols = {"رقم", "اسم المورد", "التليفون", "القطاع", "الأرضية", "النوع", "", "", ""};
        double[] widths = {50, 150, 110, 110, 90, 130, 110, 70, 70};
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
        double[] widths = {50, 150, 110, 110, 90, 130, 110, 70, 70};
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

            Label numLbl = new Label(row.supplier().getSupplierNo() > 0 ? String.valueOf(row.supplier().getSupplierNo()) : "—");
            numLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #5f5e5a;");
            numLbl.setMinWidth(widths[0]); numLbl.setPrefWidth(widths[0]);

            Label nameLbl = new Label(row.supplier().getName());
            nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a1a18;");
            nameLbl.setMinWidth(widths[1]); nameLbl.setPrefWidth(widths[1]);

            Label phoneLbl = new Label(row.supplier().getPhone() != null && !row.supplier().getPhone().isEmpty() ?
                    row.supplier().getPhone() : "—");
            phoneLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            phoneLbl.setMinWidth(widths[2]); phoneLbl.setPrefWidth(widths[2]);

            Label sectorLbl = new Label(row.supplier().getSector() != null && !row.supplier().getSector().isEmpty() ?
                    row.supplier().getSector() : "—");
            sectorLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f5e5a;");
            sectorLbl.setMinWidth(widths[3]); sectorLbl.setPrefWidth(widths[3]);

            Label floorLbl = new Label(String.format("%.0f جنيه", row.supplier().getFloorAmount()));
            floorLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a1a18;");
            floorLbl.setMinWidth(widths[4]); floorLbl.setPrefWidth(widths[4]);

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
            typeLbl.setMinWidth(widths[5]); typeLbl.setPrefWidth(widths[5]);

            Button openBtn = new Button("فتح الحساب");
            openBtn.setStyle("-fx-background-color: #3B6D11; -fx-text-fill: white; -fx-font-size: 11px; " +
                    "-fx-padding: 4 10; -fx-background-radius: 4; -fx-cursor: hand;");
            openBtn.setMinWidth(widths[6]); openBtn.setPrefWidth(widths[6]);
            final SupplierRow finalRow = row;
            openBtn.setOnAction(e -> {
                MainLayout.loadContent(SupplierDetailContent.build(userId, username, role, finalRow.supplier()));
                MainLayout.setTitle("حساب: " + finalRow.supplier().getName());
            });

            Button editBtn = new Button("تعديل");
            editBtn.setStyle("-fx-background-color: #EAF3DE; -fx-text-fill: #3B6D11; -fx-font-size: 11px; " +
                    "-fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
            editBtn.setMinWidth(widths[7]); editBtn.setPrefWidth(widths[7]);
            editBtn.setDisable(!role.equals("admin"));
            editBtn.setOnAction(e -> showEditDialog(userId, username, role, finalRow, rows, table, filter));

            Button deleteBtn = new Button("حذف");
            deleteBtn.setStyle("-fx-background-color: #FCEBEB; -fx-text-fill: #A32D2D; -fx-font-size: 11px; " +
                    "-fx-padding: 4 8; -fx-background-radius: 4; -fx-cursor: hand;");
            deleteBtn.setMinWidth(widths[8]); deleteBtn.setPrefWidth(widths[8]);
            deleteBtn.setDisable(!role.equals("admin"));
            deleteBtn.setOnAction(e -> {
                String msg = isWarehouse
                        ? "هتحذف \"" + finalRow.supplier().getName() + "\" نهائيًا وهيتشال من مخزن \"" + finalRow.warehouseName() + "\"؟"
                        : "هتحذف \"" + finalRow.supplier().getName() + "\" نهائيًا؟ سجلات الشحنات بتاعته هتفضل موجودة.";
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, msg);
                confirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK) {
                        deleteBtn.setDisable(true);
                        AsyncHelper.run(
                                () -> {
                                    SupplierDAO.deleteSupplierFully(finalRow.supplier().getId());
                                    List<SupplierRow> freshRows = new ArrayList<>();
                                    loadSupplierRows(freshRows, userId, role);
                                    return freshRows;
                                },
                                freshRows -> {
                                    rows.clear();
                                    rows.addAll(freshRows);
                                    renderTable(table, rows, filter, userId, username, role);
                                },
                                error -> {
                                    deleteBtn.setDisable(false);
                                    new Alert(Alert.AlertType.ERROR, "حصل خطأ أثناء الحذف").showAndWait();
                                }
                        );
                    }
                });
            });

            r.getChildren().addAll(numLbl, nameLbl, phoneLbl, sectorLbl, floorLbl, typeLbl, openBtn, editBtn, deleteBtn);
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

            final String phone = phoneField.getText().trim();
            final String sector = sectorField.getText().trim();
            final double finalFloor = floor;
            final String selected = warehouseCombo.getValue();

            errorLbl.setStyle("-fx-text-fill: #5f5e5a;");
            errorLbl.setText("جاري الحفظ...");

            // ── الحفظ + إعادة تحميل الجدول في الخلفية عشان الـ dialog ميهنجش ──
            AsyncHelper.run(
                    () -> {
                        SupplierDAO.addSupplier(name, phone, sector, finalFloor, userId);

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

                        List<SupplierRow> freshRows = new ArrayList<>();
                        loadSupplierRows(freshRows, userId, role);
                        return freshRows;
                    },
                    freshRows -> {
                        allRows.clear();
                        allRows.addAll(freshRows);
                        renderTable(tableBody, allRows, null, userId, username, role);
                        dialog.close();
                    },
                    error -> {
                        errorLbl.setStyle("-fx-text-fill: #b91c1c;");
                        errorLbl.setText("حصل خطأ أثناء الحفظ، حاول تاني");
                    },
                    saveBtn
            );
        });

        dialog.show();
    }

    private static void showEditDialog(int userId, String username, String role, SupplierRow row,
                                       List<SupplierRow> allRows, VBox tableBody, String filter) {
        Supplier supplier = row.supplier();

        TextField nameField = new TextField(supplier.getName());
        TextField phoneField = new TextField(supplier.getPhone() != null ? supplier.getPhone() : "");
        TextField sectorField = new TextField(supplier.getSector() != null ? supplier.getSector() : "");

        ComboBox<String> warehouseCombo = new ComboBox<>();
        warehouseCombo.getItems().add("مورد عام (بدون مخزن)");
        warehouseCombo.setMaxWidth(Double.MAX_VALUE);
        warehouseCombo.setDisable(true);
        warehouseCombo.setPromptText("جاري تحميل المخازن...");

        Button saveBtn = new Button("حفظ"); saveBtn.getStyleClass().add("btn-primary");
        Label errorLbl = new Label("");

        VBox layout = new VBox(10,
                new Label("الاسم:"), nameField,
                new Label("التليفون:"), phoneField,
                new Label("القطاع:"), sectorField,
                new Label("المخزن:"), warehouseCombo,
                saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        Stage dialog = DialogHelper.create("تعديل مورد", layout, 360, 400);

        // تحميل المخازن + المخزن الحالي بتاع المورد في الخلفية
        AsyncHelper.run(
                () -> new Object[]{WarehouseDAO.getAllWarehouses(), WarehouseDAO.getWarehouseBySupplier(supplier.getId())},
                result -> {
                    @SuppressWarnings("unchecked")
                    List<Warehouse> warehouses = (List<Warehouse>) result[0];
                    int currentWarehouseId = (int) result[1];
                    for (Warehouse w : warehouses) warehouseCombo.getItems().add(w.getId() + "|" + w.getName());
                    warehouseCombo.setValue("مورد عام (بدون مخزن)");
                    if (currentWarehouseId != -1) {
                        for (String item : warehouseCombo.getItems()) {
                            if (item.startsWith(currentWarehouseId + "|")) { warehouseCombo.setValue(item); break; }
                        }
                    }
                    warehouseCombo.setDisable(false);
                },
                error -> warehouseCombo.setPromptText("حصل خطأ في تحميل المخازن")
        );

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("الاسم مطلوب"); return; }
            String phone = phoneField.getText().trim();
            String sector = sectorField.getText().trim();
            String selected = warehouseCombo.getValue();

            errorLbl.setStyle("-fx-text-fill: #5f5e5a;");
            errorLbl.setText("جاري الحفظ...");

            AsyncHelper.run(
                    () -> {
                        SupplierDAO.updateSupplier(supplier.getId(), name, phone, sector);

                        int currentWarehouseId = WarehouseDAO.getWarehouseBySupplier(supplier.getId());
                        if (currentWarehouseId != -1) {
                            WarehouseDAO.removeSupplierFromWarehouse(currentWarehouseId, supplier.getId());
                        }
                        if (selected != null && selected.contains("|")) {
                            int newWarehouseId = Integer.parseInt(selected.split("\\|")[0]);
                            WarehouseDAO.assignSupplierToWarehouse(newWarehouseId, supplier.getId());
                        }

                        List<SupplierRow> freshRows = new ArrayList<>();
                        loadSupplierRows(freshRows, userId, role);
                        return freshRows;
                    },
                    freshRows -> {
                        allRows.clear();
                        allRows.addAll(freshRows);
                        renderTable(tableBody, allRows, filter, userId, username, role);
                        dialog.close();
                    },
                    error -> {
                        errorLbl.setStyle("-fx-text-fill: #b91c1c;");
                        errorLbl.setText("حصل خطأ أثناء الحفظ");
                    },
                    saveBtn
            );
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
