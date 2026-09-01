package com.daoud.ui;

import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class DialogHelper {
    public static Stage create(String title, Pane content, int width, int height) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        Scene scene = new Scene(content, width, height);
        scene.getRoot().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        dialog.setScene(scene);
        return dialog;
    }
}