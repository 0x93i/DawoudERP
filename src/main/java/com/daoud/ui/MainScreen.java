package com.daoud.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainScreen {

    public static void show(Stage stage, int userId, String username, String role) {
        Label welcome = new Label("أهلاً " + username + " — " + (role.equals("admin") ? "أدمن" : "مسؤول مخزن"));

        Button suppliersBtn = new Button("الموردين");
        Button warehouseBtn = new Button("المخازن");
        Button custodyBtn = new Button("العهد");
        Button usersBtn = new Button("إدارة المستخدمين");
        Button workersBtn = new Button("العمال");
        Button factoriesBtn = new Button("المصانع");
        Button logoutBtn = new Button("خروج");

        suppliersBtn.setOnAction(e -> SuppliersScreen.show(stage, userId, username, role));
        warehouseBtn.setOnAction(e -> WarehousesScreen.show(stage, userId, username, role));
        usersBtn.setOnAction(e -> UsersScreen.show(stage, userId, username, role));
        logoutBtn.setOnAction(e -> LoginScreen.show(stage));
        custodyBtn.setOnAction(e -> CustodyScreen.show(stage, userId, username, role));
        workersBtn.setOnAction(e -> WorkersScreen.show(stage, userId, username, role));
        factoriesBtn.setOnAction(e -> FactoriesScreen.show(stage, userId, username, role));
        VBox layout;
        if (role.equals("admin")) {
            layout = new VBox(16, welcome, suppliersBtn, warehouseBtn, custodyBtn, usersBtn, workersBtn, factoriesBtn, logoutBtn);
        } else {
            layout = new VBox(16, welcome, suppliersBtn, warehouseBtn, custodyBtn, workersBtn, factoriesBtn, usersBtn, logoutBtn);
        }

        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));

        Scene scene = new Scene(layout, 800, 600);
        stage.setScene(scene);
    }
}