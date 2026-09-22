package com.daoud.ui;

import com.daoud.dao.FloorDAO;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.*;

import java.util.List;

public class FloorsContent {

    public static Node build(int userId, String username, String role) {

        VBox tableBody = new VBox(0);
        tableBody.getChildren().add(new Label("جاري التحميل..."));

        AsyncHelper.run(
                FloorDAO::getAllFloors,
                floors -> renderTable(tableBody, floors, userId, username, role),
                error -> tableBody.getChildren().setAll(new Label("حصل خطأ في تحميل الأرضيات"))
        );

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        Label title = new Label("الأرضيات"); title.getStyleClass().add("card-title");
        card.getChildren().addAll(title, buildHeader(), tableBody);

        return new VBox(12, card);
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
