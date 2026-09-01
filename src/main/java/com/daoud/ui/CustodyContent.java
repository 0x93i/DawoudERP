package com.daoud.ui;

import com.daoud.dao.CustodyDAO;
import com.daoud.dao.WarehouseDAO;
import com.daoud.model.Supplier;
import com.daoud.model.Warehouse;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class CustodyContent {

    public static Node build(int userId, String username, String role) {

        TextArea custodyHistory = new TextArea();
        custodyHistory.setEditable(false);
        custodyHistory.setPrefHeight(120);

        TextArea disburseHistory = new TextArea();
        disburseHistory.setEditable(false);
        disburseHistory.setPrefHeight(120);

        List<Warehouse> warehouses = WarehouseDAO.getAllWarehouses();
        ComboBox<Warehouse> warehouseCombo = new ComboBox<>(FXCollections.observableArrayList(warehouses));
        warehouseCombo.setPromptText("اختار المخزن");

        Label remainingLabel = new Label("المتبقي: —");

        // ── إضافة عهدة (أدمن بس) ──
        TextField custodyAmount = new TextField(); custodyAmount.setPromptText("المبلغ");
        TextField custodyNotes = new TextField(); custodyNotes.setPromptText("ملاحظة");
        Button addCustodyBtn = new Button("إضافة عهدة"); addCustodyBtn.getStyleClass().add("btn-primary");
        Label custodyMsg = new Label("");

        custodyAmount.setVisible(role.equals("admin")); custodyAmount.setManaged(role.equals("admin"));
        custodyNotes.setVisible(role.equals("admin")); custodyNotes.setManaged(role.equals("admin"));
        addCustodyBtn.setVisible(role.equals("admin")); addCustodyBtn.setManaged(role.equals("admin"));
        custodyMsg.setVisible(role.equals("admin")); custodyMsg.setManaged(role.equals("admin"));

        // ── توزيع ──
        ComboBox<String> recipientTypeCombo = new ComboBox<>(FXCollections.observableArrayList("مورد", "أخرى"));
        recipientTypeCombo.setPromptText("نوع المستلم");

        ComboBox<Supplier> supplierCombo = new ComboBox<>();
        supplierCombo.setPromptText("اختار المورد");
        supplierCombo.setVisible(false); supplierCombo.setManaged(false);

        TextField otherNotes = new TextField(); otherNotes.setPromptText("وصف");
        otherNotes.setVisible(false); otherNotes.setManaged(false);

        TextField disburseAmount = new TextField(); disburseAmount.setPromptText("المبلغ");
        Button disburseBtn = new Button("تسجيل التوزيع"); disburseBtn.getStyleClass().add("btn-default");
        Label disburseMsg = new Label("");

        recipientTypeCombo.setOnAction(e -> {
            String type = recipientTypeCombo.getValue();
            supplierCombo.setVisible("مورد".equals(type)); supplierCombo.setManaged("مورد".equals(type));
            otherNotes.setVisible("أخرى".equals(type)); otherNotes.setManaged("أخرى".equals(type));
        });

        warehouseCombo.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) return;
            remainingLabel.setText(String.format("المتبقي: %.1f جنيه", CustodyDAO.getRemainingCustody(w.getId())));
            supplierCombo.setItems(FXCollections.observableArrayList(WarehouseDAO.getSuppliersByWarehouse(w.getId())));
            custodyHistory.setText(String.join("\n", CustodyDAO.getCustodyHistory(w.getId())));
            disburseHistory.setText(String.join("\n", CustodyDAO.getDisbursementHistory(w.getId())));
        });

        addCustodyBtn.setOnAction(e -> {
            Warehouse w = warehouseCombo.getValue();
            if (w == null) { custodyMsg.setText("اختار مخزن"); return; }
            try {
                double amount = Double.parseDouble(custodyAmount.getText().trim());
                CustodyDAO.addCustody(w.getId(), userId, amount, custodyNotes.getText().trim());
                remainingLabel.setText(String.format("المتبقي: %.1f جنيه", CustodyDAO.getRemainingCustody(w.getId())));
                custodyAmount.clear(); custodyNotes.clear();
                custodyMsg.setText("تم ✓");
                custodyHistory.setText(String.join("\n", CustodyDAO.getCustodyHistory(w.getId())));
            } catch (NumberFormatException ex) { custodyMsg.setText("ادخل رقم"); }
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
                    recipientId = s.getId(); notes = s.getName();
                } else { notes = otherNotes.getText().trim(); }
                CustodyDAO.addDisbursement(w.getId(), userId, type, recipientId, amount, notes);
                remainingLabel.setText(String.format("المتبقي: %.1f جنيه", CustodyDAO.getRemainingCustody(w.getId())));
                disburseAmount.clear(); disburseMsg.setText("تم ✓");
                disburseHistory.setText(String.join("\n", CustodyDAO.getDisbursementHistory(w.getId())));
            } catch (NumberFormatException ex) { disburseMsg.setText("ادخل رقم"); }
        });

        // ── Cards ──
        VBox warehouseCard = new VBox(8); warehouseCard.getStyleClass().add("card");
        warehouseCard.getChildren().addAll(new Label("المخزن:"), warehouseCombo, remainingLabel);

        VBox custodyCard = new VBox(8); custodyCard.getStyleClass().add("card");
        Label custodyTitle = new Label("إضافة عهدة (أدمن فقط)");
        custodyTitle.setVisible(role.equals("admin")); custodyTitle.setManaged(role.equals("admin"));
        custodyCard.getChildren().addAll(custodyTitle, new Label("المبلغ:"), custodyAmount, new Label("ملاحظة:"), custodyNotes, addCustodyBtn, custodyMsg);

        VBox disburseCard = new VBox(8); disburseCard.getStyleClass().add("card");
        disburseCard.getChildren().addAll(
                new Label("تسجيل توزيع:"),
                new Label("نوع المستلم:"), recipientTypeCombo,
                supplierCombo, otherNotes,
                new Label("المبلغ:"), disburseAmount,
                disburseBtn, disburseMsg);

        VBox historyCard = new VBox(8); historyCard.getStyleClass().add("card");
        historyCard.getChildren().addAll(
                new Label("سجل العهد:"), custodyHistory,
                new Label("سجل التوزيع:"), disburseHistory);

        return new VBox(12, warehouseCard, custodyCard, disburseCard, historyCard);
    }
}