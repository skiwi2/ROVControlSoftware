package com.skiwi.rovcontrolsoftware.controllers;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.ds.ipcam.IpCamDevice;
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry;
import com.github.sarxos.webcam.ds.ipcam.IpCamMode;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;

/**
 * @author Frank van Heeswijk
 */
public class MainWindowController implements Initializable {
    @FXML
    private SwingNode swingNode;

    @FXML
    private TextField urlTextField;

    @FXML
    private Label xAngleLabel;

    @FXML
    private Label yAngleLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        urlTextField.textProperty().addListener((observableValue, oldValue, newValue) -> {
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
        xAngleLabel.setText(String.valueOf(angle));
    }

    private void setYAngle(int angle) {
        yAngleLabel.setText(String.valueOf(angle));
    }
}
