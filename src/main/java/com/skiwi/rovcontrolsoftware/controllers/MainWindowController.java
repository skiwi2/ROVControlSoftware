package com.skiwi.rovcontrolsoftware.controllers;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.ds.ipcam.IpCamDevice;
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry;
import com.github.sarxos.webcam.ds.ipcam.IpCamMode;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
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
    private TextField cameraUrlTextField;

    @FXML
    private TextField socketHostTextField;

    @FXML
    private TextField socketPortTextField;

    @FXML
    private Label xAngleLabel;

    @FXML
    private Label yAngleLabel;

    @FXML
    private Label cameraStatusLabel;

    @FXML
    private Label socketStatusLabel;

    private Scene scene;

    private int xAngle;
    private int yAngle;

    private Socket commandSocket;
    private PrintWriter outCommandSocket;
    private BufferedReader inCommandSocket;

    private List<String> bufferedCommands = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cameraUrlTextField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            try {
                IpCamDevice ipCamDevice = IpCamDeviceRegistry.register(new IpCamDevice("Test " + new Random().nextInt(), cameraUrlTextField.getText(), IpCamMode.PUSH));
                setCameraStatus(ipCamDevice.isOnline() ? Status.ONLINE : Status.OFFLINE);
                if (ipCamDevice.isOnline()) {
                    List<Webcam> webcams = Webcam.getWebcams();
                    Webcam addedWebcam = webcams.get(webcams.size() - 1);
                    addedWebcam.addWebcamListener(new WebcamListener() {
                        @Override
                        public void webcamOpen(WebcamEvent we) {
                            Platform.runLater(() -> swingNode.setContent(new WebcamPanel(we.getSource())));
                        }

                        @Override
                        public void webcamClosed(WebcamEvent we) {
                        }

                        @Override
                        public void webcamDisposed(WebcamEvent we) {
                        }

                        @Override
                        public void webcamImageObtained(WebcamEvent we) {
                        }
                    });
                    addedWebcam.open(false);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });

        socketHostTextField.textProperty().addListener((observableValue, oldValue, newValue) -> Platform.runLater(this::updateSocketConnection));
        socketPortTextField.textProperty().addListener((observableValue, oldValue, newValue) -> Platform.runLater(this::updateSocketConnection));

        cameraUrlTextField.setText("http://195.235.198.107:3346/axis-cgi/mjpg/video.cgi");
//        cameraUrlTextField.setText("http://192.168.1.1:8080/?action=stream");
        socketHostTextField.setText("127.0.0.1");
//        socketHostTextField.setText("192.168.1.1");
        socketPortTextField.setText("2001");
        setXAngle(90);
        setYAngle(90);
    }

    private void updateSocketConnection() {
        String host = socketHostTextField.getText();
        String port = socketPortTextField.getText();

        try {
            if (commandSocket != null) {
                commandSocket.close();
            }

            commandSocket = new Socket(host, Integer.parseInt(port));
            outCommandSocket = new PrintWriter(commandSocket.getOutputStream(), true);
            inCommandSocket = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));

            bufferedCommands.forEach(outCommandSocket::println);

            setSocketStatus(Status.ONLINE);
        } catch (IOException e) {
            setSocketStatus(Status.OFFLINE);
        }
    }

    private void setXAngle(int angle) {
        xAngle = angle;
        xAngleLabel.setText(String.valueOf(angle));
        sendCommand("x " + angle);
    }

    private void setYAngle(int angle) {
        yAngle = angle;
        yAngleLabel.setText(String.valueOf(angle));
        sendCommand("y " + angle);
    }

    private void sendCommand(String command) {
        if (outCommandSocket == null) {
            bufferedCommands.add(command);
        }
        else {
            outCommandSocket.println(command);
        }
    }

    private void setCameraStatus(Status status) {
        String text;
        switch (status) {
            case ONLINE:
                text = "Camera online";
                break;
            case OFFLINE:
                text = "Camera offline";
                break;
            default:
                text = "";
        }
        cameraStatusLabel.setText(text);
    }

    private void setSocketStatus(Status status) {
        String text;
        switch (status) {
            case ONLINE:
                text = "Socket online";
                break;
            case OFFLINE:
                text = "Socket offline";
                break;
            default:
                text = "";
        }
        socketStatusLabel.setText(text);
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

    private static enum Status {
        ONLINE,
        OFFLINE
    }
}
