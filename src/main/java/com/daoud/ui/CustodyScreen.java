package com.daoud.ui;

import com.daoud.dao.CustodyDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

public class CustodyScreen {

    public static void show(Stage stage, int userId, String username, String role) {

        // ── التاريخ (بيتعرفوا أول عشان الـ listeners تشوفهم) ──
        TextArea custodyHistory = new TextArea();
        custodyHistory.setEditable(false);
        custodyHistory.setPrefHeight(120);

        TextArea disburseHistory = new TextArea();
        disburseHistory.setEditable(false);
        disburseHistory.setPrefHeight(120);

        // ── اختيار المخزن ──
        List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
        ComboBox<Warehouse> warehouseCombo = new ComboBox<>(
                FXCollections.observableArrayList(warehouses));
        warehouseCombo.setPromptText("اختار المخزن");

        Label remainingLabel = new Label("المتبقي من العهد: —");

        // ── إضافة عهدة (أدمن بس) ──
        Label custodyTitle = new Label("── إضافة عهدة جديدة (أدمن فقط) ──");
        TextField custodyAmount = new TextField();
        custodyAmount.setPromptText("المبلغ");
        TextField custodyNotes = new TextField();
        custodyNotes.setPromptText("ملاحظة (اختياري)");
        Button addCustodyBtn = new Button("إضافة عهدة");
        Label custodyMsg = new Label("");

        custodyTitle.setVisible(role.equals("admin"));
        custodyTitle.setManaged(role.equals("admin"));
        custodyAmount.setVisible(role.equals("admin"));
        custodyAmount.setManaged(role.equals("admin"));
        custodyNotes.setVisible(role.equals("admin"));
        custodyNotes.setManaged(role.equals("admin"));
        addCustodyBtn.setVisible(role.equals("admin"));
        addCustodyBtn.setManaged(role.equals("admin"));
        custodyMsg.setVisible(role.equals("admin"));
        custodyMsg.setManaged(role.equals("admin"));

        // ── توزيع ──
        Label disburseTitle = new Label("── تسجيل توزيع من العهدة ──");

        ComboBox<String> recipientTypeCombo = new ComboBox<>(
                FXCollections.observableArrayList("مورد", "أخرى"));
        recipientTypeCombo.setPromptText("نوع المستلم");

        ComboBox<Supplier> supplierCombo = new ComboBox<>();
        supplierCombo.setPromptText("اختار المورد");
        supplierCombo.setVisible(false);
        supplierCombo.setManaged(false);

        TextField otherNotes = new TextField();
        otherNotes.setPromptText("وصف (مثلاً: بنزين)");
        otherNotes.setVisible(false);
        otherNotes.setManaged(false);

        TextField disburseAmount = new TextField();
        disburseAmount.setPromptText("المبلغ");

        Button disburseBtn = new Button("تسجيل التوزيع");
        Label disburseMsg = new Label("");

        // ── الأحداث ──
        recipientTypeCombo.setOnAction(e -> {
            String type = recipientTypeCombo.getValue();
            supplierCombo.setVisible("مورد".equals(type));
            supplierCombo.setManaged("مورد".equals(type));
            otherNotes.setVisible("أخرى".equals(type));
            otherNotes.setManaged("أخرى".equals(type));
        });

        warehouseCombo.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            remainingLabel.setText(String.format("المتبقي من العهد: %.1f جنيه",
                    CustodyDAO.getRemainingCustody(w.getId())));
            supplierCombo.setItems(FXCollections.observableArrayList(
                    WarehouseDAO.getSuppliersByWarehouse(w.getId())));
            refreshHistories(custodyHistory, disburseHistory, w.getId());
        });

        addCustodyBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) { custodyMsg.setText("اختار مخزن"); return; }
            try {
                double amount = Double.parseDouble(custodyAmount.getText().trim());
                CustodyDAO.addCustody(w.getId(), userId, amount, custodyNotes.getText().trim());
                remainingLabel.setText(String.format("المتبقي من العهد: %.1f جنيه",
                        CustodyDAO.getRemainingCustody(w.getId())));
                custodyAmount.clear();
                custodyNotes.clear();
                custodyMsg.setText("تم إضافة العهدة");
                refreshHistories(custodyHistory, disburseHistory, w.getId());
            } catch (NumberFormatException ex) {
                custodyMsg.setText("ادخل رقم صحيح");
            }
        });

        disburseBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) { disburseMsg.setText("اختار مخزن"); return; }
            if (recipientTypeCombo.getValue() == null) { disburseMsg.setText("اختار نوع المستلم"); return; }
            try {
                double amount = Double.parseDouble(disburseAmount.getText().trim());
                double remaining = CustodyDAO.getRemainingCustody(w.getId());
                if (amount > remaining) { disburseMsg.setText("المبلغ أكبر من المتبقي"); return; }

                String type = recipientTypeCombo.getValue().equals("مورد") ? "supplier" : "other";
                int recipientId = 0;
                String notes = "";

                if (type.equals("supplier")) {
                    Supplier s = supplierCombo.getValue();
                    if (s == null) { disburseMsg.setText("اختار المورد"); return; }
                    recipientId = s.getId();
                    notes = s.getName();
                } else {
                    notes = otherNotes.getText().trim();
                }

                CustodyDAO.addDisbursement(w.getId(), userId, type, recipientId, amount, notes);
                remainingLabel.setText(String.format("المتبقي من العهد: %.1f جنيه",
                        CustodyDAO.getRemainingCustody(w.getId())));
                disburseAmount.clear();
                disburseMsg.setText("تم التسجيل");
                refreshHistories(custodyHistory, disburseHistory, w.getId());
            } catch (NumberFormatException ex) {
                disburseMsg.setText("ادخل رقم صحيح");
            }
        });

        Button backBtn = new Button("رجوع");
        backBtn.setOnAction(e -> MainScreen.show(stage, userId, username, role));

        VBox layout = new VBox(10,
                new Label("العهد"),
                new Label("المخزن:"), warehouseCombo,
                remainingLabel,
                new Separator(),
                custodyTitle,
                new Label("المبلغ:"), custodyAmount,
                new Label("ملاحظة:"), custodyNotes,
                addCustodyBtn, custodyMsg,
                new Separator(),
                disburseTitle,
                new Label("نوع المستلم:"), recipientTypeCombo,
                supplierCombo, otherNotes,
                new Label("المبلغ:"), disburseAmount,
                disburseBtn, disburseMsg,
                new Separator(),
                new Label("── سجل العهد ──"), custodyHistory,
                new Label("── سجل التوزيع ──"), disburseHistory,
                backBtn
        );
        layout.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        stage.setScene(new Scene(scroll, 800, 600));
    }

    private static void refreshHistories(TextArea custodyArea, TextArea disburseArea, int warehouseId) {
        custodyArea.setText(String.join("\n", CustodyDAO.getCustodyHistory(warehouseId)));
        disburseArea.setText(String.join("\n", CustodyDAO.getDisbursementHistory(warehouseId)));
    }
}