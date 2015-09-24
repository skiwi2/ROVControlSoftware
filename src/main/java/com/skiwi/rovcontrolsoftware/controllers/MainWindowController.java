package com.skiwi.rovcontrolsoftware.controllers;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.ds.ipcam.IpCamDevice;
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry;
import com.github.sarxos.webcam.ds.ipcam.IpCamMode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * @author Frank van Heeswijk
 */
public class MainWindowController implements Initializable {
    private static final int DELTA = 5;

    @FXML
    private SwingNode swingNode;

    @FXML
    private TextField urlTextField;

    @FXML
    private Label xAngleLabel;

    @FXML
    private Label yAngleLabel;

    private Scene scene;

    private int xAngle;
    private int yAngle;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        urlTextField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            //TODO unregistering causes a big problem if it is still in use
            IpCamDeviceRegistry.unregisterAll();
            try {
                IpCamDeviceRegistry.register(new IpCamDevice("Test " + new Random().nextInt(), urlTextField.getText(), IpCamMode.PUSH));
                Platform.runLater(() -> swingNode.setContent(new WebcamPanel(Webcam.getDefault())));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });

        urlTextField.setText("http://195.235.198.107:3346/axis-cgi/mjpg/video.cgi");
        setXAngle(90);
        setYAngle(90);
    }

    private void setXAngle(int angle) {
        xAngle = angle;
        xAngleLabel.setText(String.valueOf(angle));
    }

    private void setYAngle(int angle) {
        yAngle = angle;
        yAngleLabel.setText(String.valueOf(angle));
    }

    public void setScene(Scene scene) {
        this.scene = scene;

        scene.addEventHandler(KeyEvent.KEY_PRESSED, keyEventHandler -> {
            int newAngle;
            switch (keyEventHandler.getCode()) {
                case W:
                    newAngle = yAngle + DELTA;
                    if (newAngle >= 180) {
                        newAngle = 180;
                    }
                    setYAngle(newAngle);
                    break;
                case S:
                    newAngle = yAngle - DELTA;
                    if (newAngle <= 0) {
                        newAngle = 0;
                    }
                    setYAngle(newAngle);
                    break;
                case D:
                    newAngle = xAngle + DELTA;
                    if (newAngle >= 180) {
                        newAngle = 180;
                    }
                    setXAngle(newAngle);
                    break;
                case A:
                    newAngle = xAngle - DELTA;
                    if (newAngle <= 0) {
                        newAngle = 0;
                    }
                    setXAngle(newAngle);
                    break;
                default:
                    break;
            }
        });
    }
}
