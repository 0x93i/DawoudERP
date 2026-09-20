package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.model.Factory;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class FactoriesContent {

    public static Node build(int userId, String username, String role) {
        ListView<Factory> listView = new ListView<>();
        listView.getStyleClass().add("list-view");
        listView.setPrefHeight(400);
        listView.setPlaceholder(new Label("جاري تحميل المصانع..."));
        refreshList(listView);

        Button addBtn = new Button("إضافة مصنع");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setDisable(!role.equals("admin"));

        Button openBtn = new Button("فتح الحساب");
        openBtn.getStyleClass().add("btn-default");
        openBtn.setDisable(true);

        Button deleteBtn = new Button("حذف");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            openBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null || !role.equals("admin"));
        });

        addBtn.setOnAction(e -> {
            TextField nameField = new TextField();
            nameField.setPromptText("اسم المصنع");
            Button saveBtn = new Button("حفظ");
            saveBtn.getStyleClass().add("btn-primary");
            Label errLbl = new Label("");
            VBox layout = new VBox(10, new Label("الاسم:"), nameField, saveBtn, errLbl);
            layout.setPadding(new Insets(20));
            Stage dialog = DialogHelper.create("إضافة مصنع", layout, 320, 180);
            saveBtn.setOnAction(ev -> {
                if (nameField.getText().trim().isEmpty()) { errLbl.setText("الاسم مطلوب"); return; }
                String name = nameField.getText().trim();
                errLbl.setStyle("-fx-text-fill: #5f5e5a;");
                errLbl.setText("جاري الحفظ...");
                AsyncHelper.runVoid(
                        () -> FactoryDAO.addFactory(name, userId),
                        () -> { refreshList(listView); dialog.close(); },
                        error -> { errLbl.setStyle("-fx-text-fill: #b91c1c;"); errLbl.setText("حصل خطأ"); },
                        saveBtn
                );
            });
            dialog.show();
        });

        deleteBtn.setOnAction(e -> {
            Factory selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هتحذف: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        deleteBtn.setDisable(true);
                        AsyncHelper.runVoid(
                                () -> FactoryDAO.deleteFactory(selected.getId()),
                                () -> refreshList(listView),
                                error -> refreshList(listView)
                        );
                    }
                });
            }
        });

        openBtn.setOnAction(e -> {
            Factory selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                MainLayout.loadContent(FactoryDetailContent.build(userId, username, role, selected));
                MainLayout.setTitle("حساب مصنع: " + selected.getName());
            }
        });

        HBox buttons = new HBox(10, addBtn, openBtn, deleteBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        Label cardTitle = new Label("قائمة المصانع");
        cardTitle.getStyleClass().add("card-title");
        card.getChildren().addAll(cardTitle, buttons, listView);

        return new VBox(12, card);
    }

    private static void refreshList(ListView<Factory> listView) {
        AsyncHelper.run(
                FactoryDAO::getAllFactories,
                factories -> listView.setItems(FXCollections.observableArrayList(factories)),
                error -> listView.setPlaceholder(new Label("حصل خطأ في تحميل المصانع"))
        );
    }
}
