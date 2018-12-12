package net.arksea.pusher.tools;

import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 *
 * Created by xiaohaixing on 2018/11/2.
 */
public class ErrorDialog {
    private static Alert exAlert;
    private static Alert alert;
    private static TextArea textArea;
    static {
        exAlert = new Alert(Alert.AlertType.WARNING);
        exAlert.setTitle("Exception Dialog");
        Label label = new Label("Exception stacktrace:");
        textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);
        exAlert.getDialogPane().setExpandableContent(expContent);

        alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Error Dialog");
    }
    public static void show(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        textArea.setText(sw.toString());
        exAlert.setHeaderText(ex.getMessage());
        exAlert.setContentText("");
        exAlert.showAndWait();
    }
    public static void show(String msg, Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        textArea.setText(sw.toString());
        exAlert.setHeaderText(msg);
        exAlert.setContentText(ex.getMessage());
        exAlert.showAndWait();
    }
    public static void show(String msg) {
        alert.setHeaderText(msg);
        alert.setContentText("");
        alert.showAndWait();
    }
    public static void show(String msg, String content) {
        alert.setHeaderText(msg);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
