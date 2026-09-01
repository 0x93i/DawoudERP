package com.daoud.ui;

import com.daoud.dao.FactoryDAO;
import com.daoud.model.Factory;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;

public class FactoriesScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label title = new Label("المصانع");

        ListView<Factory> listView = new ListView<>();
        refreshList(listView);

        Button addBtn = new Button("إضافة مصنع");
        Button openBtn = new Button("فتح حساب المصنع");
        Button deleteBtn = new Button("حذف المصنع");
        Button backBtn = new Button("رجوع");

        openBtn.setDisable(true);
        deleteBtn.setDisable(true);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            openBtn.setDisable(selected == null);
            deleteBtn.setDisable(selected == null || !role.equals("admin"));
        });

        addBtn.setOnAction(e -> showAddDialog(userId, listView));

        deleteBtn.setOnAction(e -> {
            Factory selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "هتحذف المصنع: " + selected.getName() + "؟");
                confirm.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.OK) {
                        FactoryDAO.deleteFactory(selected.getId());
                        refreshList(listView);
                    }
                });
            }
        });

        openBtn.setOnAction(e -> {
            Factory selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null)
                FactoryDetailScreen.show(stage, userId, username, role, selected);
        });

        backBtn.setOnAction(e -> MainScreen.show(stage, userId, username, role));

        HBox buttons = new HBox(10, addBtn, openBtn, deleteBtn, backBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox layout = new VBox(12, title, listView, buttons);
        layout.setPadding(new Insets(20));
        stage.setScene(new Scene(layout, 800, 600));
    }

    private static void refreshList(ListView<Factory> listView) {
        listView.setItems(FXCollections.observableArrayList(FactoryDAO.getAllFactories()));
    }

    private static void showAddDialog(int userId, ListView<Factory> listView) {
        Stage dialog = new Stage();
        dialog.setTitle("إضافة مصنع جديد");

        TextField nameField = new TextField();
        nameField.setPromptText("اسم المصنع");

        Button saveBtn = new Button("حفظ");
        Label errorLbl = new Label("");

        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLbl.setText("اسم المصنع مطلوب"); return; }
            FactoryDAO.addFactory(name, userId);
            refreshList(listView);
            dialog.close();
        });

        VBox layout = new VBox(10, new Label("اسم المصنع:"), nameField, saveBtn, errorLbl);
        layout.setPadding(new Insets(20));
        dialog.setScene(new Scene(layout, 320, 180));
        dialog.show();
    }
}