package com.skiwi.rovcontrolsoftware;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.ds.ipcam.IpCamDriver;
import com.skiwi.rovcontrolsoftware.controllers.MainWindowController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.MalformedURLException;

/**
 * @author Frank van Heeswijk
 */
public class ROVControlSoftwareApplication extends Application {
    static {
        Webcam.setDriver(new IpCamDriver());
    }

    public static void main(String[] args) throws MalformedURLException {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);
        MainWindowController mainWindowController = fxmlLoader.getController();
        mainWindowController.setScene(scene);

        primaryStage.setTitle("ROV Control Application");
        primaryStage.setScene(scene);
        primaryStage.setWidth(800);
        primaryStage.setHeight(800);
        primaryStage.show();
    }
}
