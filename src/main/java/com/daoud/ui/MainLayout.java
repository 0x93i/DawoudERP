package com.daoud.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MainLayout {

    private static Button activeNavBtn = null;
    private static Label topbarTitle;
    private static VBox contentArea;

    public static void show(Stage stage, int userId, String username, String role) {

        // ── Sidebar ──
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        Label logo = new Label("X-MAN ERP");
        logo.getStyleClass().add("sidebar-logo");
        logo.setMaxWidth(Double.MAX_VALUE);
        sidebar.getChildren().add(logo);

        if (role.equals("admin")) {
            sidebar.getChildren().addAll(
                    navSection("رئيسي"),
                    navBtn("لوحة التحكم", () -> {
                        setTitle("لوحة التحكم");
                        loadContent(DashboardContent.build(userId, username, role));
                    }),
                    navBtn("الشحنات", () -> {
                        setTitle("الشحنات");
                        loadContent(ShipmentsContent.build(userId, username, role));
                    }),
                    navBtn("الموردين", () -> {
                        setTitle("الموردين");
                        loadContent(SuppliersContent.build(userId, username, role));
                    }),
                    navBtn("المخازن", () -> {
                        setTitle("المخازن");
                        loadContent(WarehousesContent.build(stage, userId, username, role));
                    }),
                    navBtn("المصانع", () -> {
                        setTitle("المصانع");
                        loadContent(FactoriesContent.build(userId, username, role));
                    }),
                    navSection("الأشخاص"),
                    navBtn("العمال", () -> {
                        setTitle("العمال");
                        loadContent(WorkersContent.build(userId, username, role));
                    }),
                    navSection("حسابات"),
                    navBtn("العهد", () -> {
                        setTitle("العهد");
                        loadContent(CustodyContent.build(userId, username, role));
                    }),
                    navBtn("إدارة المستخدمين", () -> {
                        setTitle("إدارة المستخدمين");
                        loadContent(UsersContent.build(userId, username, role));
                    })
            );
        } else {
            sidebar.getChildren().addAll(
                    navSection("رئيسي"),
                    navBtn("المخازن", () -> {
                        setTitle("المخازن");
                        loadContent(WarehousesContent.build(stage, userId, username, role));
                    }),
                    navSection("الأشخاص"),
                    navBtn("العمال", () -> {
                        setTitle("العمال");
                        loadContent(WorkersContent.build(userId, username, role));
                    })
            );
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button logoutBtn = new Button("خروج");
        logoutBtn.getStyleClass().add("btn-danger");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> LoginScreen.show(stage));
        VBox logoutBox = new VBox(logoutBtn);
        logoutBox.setPadding(new Insets(12));
        sidebar.getChildren().addAll(spacer, logoutBox);

        // ── Topbar ──
        HBox topbar = new HBox();
        topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT);

        topbarTitle = new Label("لوحة التحكم");
        topbarTitle.getStyleClass().add("topbar-title");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label badgeAdmin = new Label(role.equals("admin") ? "أدمن — " + username : "مسؤول مخزن — " + username);
        badgeAdmin.getStyleClass().add("badge-admin");

        topbar.getChildren().addAll(topbarTitle, topSpacer, badgeAdmin);

        // ── Content ──
        contentArea = new VBox();
        contentArea.getStyleClass().add("content-area");

        ScrollPane scrollPane = new ScrollPane(contentArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #eeecea;");

        VBox rightSide = new VBox(topbar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox root = new HBox(sidebar, rightSide);
        HBox.setHgrow(rightSide, Priority.ALWAYS);
        root.setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(
                MainLayout.class.getResource("/style.css").toExternalForm()
        );
        scene.getRoot().setNodeOrientation(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT);
        stage.setScene(scene);
        stage.setTitle("برنامج عم داود");
        stage.show();

        // افتح الشاشة المناسبة حسب الدور
        if (role.equals("admin")) {
            loadContent(DashboardContent.build(userId, username, role));
            setTitle("لوحة التحكم");
        } else {
            loadContent(WarehousesContent.build(null, userId, username, role));
            setTitle("المخازن");
        }
    }

    private static Button navBtn(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            if (activeNavBtn != null)
                activeNavBtn.getStyleClass().remove("nav-btn-active");
            btn.getStyleClass().add("nav-btn-active");
            activeNavBtn = btn;
            action.run();
        });
        return btn;
    }

    private static Label navSection(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("nav-label");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    public static void setTitle(String title) {
        if (topbarTitle != null) topbarTitle.setText(title);
    }

    public static void loadContent(Node content) {
        if (contentArea != null)
            contentArea.getChildren().setAll(content);
    }
}