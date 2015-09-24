package com.skiwi.rovcontrolsoftware;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.ds.ipcam.IpCamDevice;
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry;
import com.github.sarxos.webcam.ds.ipcam.IpCamDriver;
import com.github.sarxos.webcam.ds.ipcam.IpCamMode;
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
//        IpCamDeviceRegistry.register(new IpCamDevice("Test", "http://195.235.198.107:3346/axis-cgi/mjpg/video.cgi", IpCamMode.PUSH));
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("MainWindow.fxml"));
        Scene scene = new Scene(root);

        primaryStage.setTitle("ROV Control Application");
        primaryStage.setScene(scene);
        primaryStage.setWidth(800);
        primaryStage.setHeight(800);
        primaryStage.show();
    }
}
