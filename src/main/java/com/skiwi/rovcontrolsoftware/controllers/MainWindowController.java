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
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Frank van Heeswijk
 */
public class MainWindowController implements Initializable {
    private static final int POLL_RATE = 100;   //amount of times polled per second
    private static final int CONTROLLER_DELTA = 100;
    private static final int KEYBOARD_DELTA = 5;

    private static final float CONTROLLER_DEADZONE = 0.25f;

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

    private float xAngle;
    private float yAngle;

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
        setXAngle(90f);
        setYAngle(90f);

        List<Controller> gamepads = Arrays.stream(ControllerEnvironment.getDefaultEnvironment().getControllers())
                .filter(controller -> controller.getType().equals(Controller.Type.GAMEPAD))
                .collect(Collectors.toList());
        if (!gamepads.isEmpty()) {
            Controller controller = gamepads.get(0);

            //Right thumbstick
            Component rightThumbstickX = controller.getComponent(Component.Identifier.Axis.RX);
            Component rightThumbstickY = controller.getComponent(Component.Identifier.Axis.RY);

            Timer timer = new Timer(true);
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    controller.poll();
                    float rightThumbstickXValue = rightThumbstickX.getPollData();
                    float rightThumbstickYValue = rightThumbstickY.getPollData();

                    float magnitude = (float)Math.sqrt(Math.pow(rightThumbstickXValue, 2) + Math.pow(rightThumbstickYValue, 2));
                    if (magnitude < CONTROLLER_DEADZONE) {
                        //do nothing
                    }
                    else {
                        //normalize
                        rightThumbstickXValue /= magnitude;
                        rightThumbstickYValue /= magnitude;

                        //scaling
                        rightThumbstickXValue *= ((magnitude - CONTROLLER_DEADZONE) / (1f - CONTROLLER_DEADZONE));
                        rightThumbstickYValue *= ((magnitude - CONTROLLER_DEADZONE) / (1f - CONTROLLER_DEADZONE));

                        setXAngle(clamp(xAngle + (rightThumbstickXValue * CONTROLLER_DELTA / POLL_RATE), 0f, 180f));
                        setYAngle(clamp(yAngle + (-rightThumbstickYValue * CONTROLLER_DELTA / POLL_RATE), 0f, 180f));
                    }
                }
            };
            timer.scheduleAtFixedRate(timerTask, 0, 1000 / POLL_RATE);
        }
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

    private void setXAngle(float angle) {
        xAngle = angle;
        Platform.runLater(() -> xAngleLabel.setText(String.valueOf(Math.round(angle))));
        sendCommand("x " + Math.round(angle));
    }

    private void setYAngle(float angle) {
        yAngle = angle;
        Platform.runLater(() -> yAngleLabel.setText(String.valueOf(Math.round(angle))));
        sendCommand("y " + Math.round(angle));
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

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    public void setScene(Scene scene) {
        this.scene = scene;

        scene.addEventHandler(KeyEvent.KEY_PRESSED, keyEventHandler -> {
            float newAngle;
            switch (keyEventHandler.getCode()) {
                case W:
                    newAngle = yAngle + KEYBOARD_DELTA;
                    setYAngle(clamp(newAngle, 0f , 180f));
                    break;
                case S:
                    newAngle = yAngle - KEYBOARD_DELTA;
                    setYAngle(clamp(newAngle, 0f , 180f));
                    break;
                case D:
                    newAngle = xAngle + KEYBOARD_DELTA;
                    setXAngle(clamp(newAngle, 0f , 180f));
                    break;
                case A:
                    newAngle = xAngle - KEYBOARD_DELTA;
                    setXAngle(clamp(newAngle, 0f , 180f));
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
