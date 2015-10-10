package com.skiwi.rovcontrolsoftware.controllers;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.ds.ipcam.IpCamDevice;
import com.github.sarxos.webcam.ds.ipcam.IpCamDeviceRegistry;
import com.github.sarxos.webcam.ds.ipcam.IpCamMode;
import com.skiwi.rovcontrolsoftware.gamepads.XboxGamepad;
import com.skiwi.rovcontrolsoftware.gamepads.events.AxisMovedEvent;
import com.skiwi.rovcontrolsoftware.gamepads.events.ButtonPressedEvent;
import javafx.application.Platform;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
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
    private static final int CONTROLLER_DELTA = 500;
    private static final int KEYBOARD_DELTA = 5;

    private static final float CONTROLLER_RS_DEADZONE = 0.25f;
    private static final float CONTROLLER_TRIGGER_DEADZONE = 0.10f;
    private static final float CONTROLLER_LS_DEADZONE = 0.25f;

    private static final float CX_DEFAULT = 90f;
    private static final float CY_DEFAULT = 90f;

    private static final int CX_MIN = 10;
    private static final int CX_MAX = 170;
    private static final int CY_MIN = 0;
    private static final int CY_MAX = 180;

    @FXML
    private SwingNode swingNode;

    @FXML
    private ChoiceBox<Configuration> configurationChoiceBox;

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

    @FXML
    private Label gamepadStatusLabel;

    @FXML
    private Label motorLeftLabel;

    @FXML
    private Label motorRightLabel;

    private Scene scene;

    private float rightStickXValue = 0f;
    private float rightStickYValue = 0f;

    private float xAngle;
    private float yAngle;

    private float motorThrottle;

    private float motorLeftModifier;
    private float motorRightModifier;

    private Socket commandSocket;
    private PrintWriter outCommandSocket;
    private BufferedReader inCommandSocket;

    private List<String> bufferedCommands = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cameraUrlTextField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            try {
                IpCamDevice ipCamDevice = IpCamDeviceRegistry.register(new IpCamDevice("Test " + new Random().nextInt(), cameraUrlTextField.getText(), IpCamMode.PUSH));
                Runnable cameraOnlineRunnable = () -> {
                    setCameraStatus(Status.CONNECTING);
                    boolean cameraOnline = ipCamDevice.isOnline();
                    setCameraStatus(cameraOnline ? Status.ONLINE : Status.OFFLINE);
                    if (cameraOnline) {
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
                };
                new Thread(cameraOnlineRunnable).start();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        });

        socketHostTextField.textProperty().addListener((observableValue, oldValue, newValue) -> new Thread(this::updateSocketConnection).start());
        socketPortTextField.textProperty().addListener((observableValue, oldValue, newValue) -> new Thread(this::updateSocketConnection).start());

        setXAngle(CX_DEFAULT);
        setYAngle(CY_DEFAULT);

        motorThrottle = 0f;
        motorLeftModifier = 1f;
        motorRightModifier = 1f;
        updateMotors();

        List<Controller> gamepads = Arrays.stream(ControllerEnvironment.getDefaultEnvironment().getControllers())
                .filter(controller -> controller.getType().equals(Controller.Type.GAMEPAD))
                .collect(Collectors.toList());
        if (!gamepads.isEmpty()) {
            setGamepadStatus(Status.ONLINE);
            Controller controller = gamepads.get(0);

            XboxGamepad xboxGamepad = new XboxGamepad(controller, 1000 / POLL_RATE);

            xboxGamepad.setDeadzone(XboxGamepad.Component.LEFT_STICK_X_AXIS, XboxGamepad.DeadzoneType.RADIAL, CONTROLLER_LS_DEADZONE);
            xboxGamepad.setDeadzone(XboxGamepad.Component.LEFT_STICK_Y_AXIS, XboxGamepad.DeadzoneType.RADIAL, CONTROLLER_LS_DEADZONE);

            xboxGamepad.setDeadzone(XboxGamepad.Component.RIGHT_STICK_X_AXIS, XboxGamepad.DeadzoneType.RADIAL, CONTROLLER_RS_DEADZONE);
            xboxGamepad.setDeadzone(XboxGamepad.Component.RIGHT_STICK_Y_AXIS, XboxGamepad.DeadzoneType.RADIAL, CONTROLLER_RS_DEADZONE);

            xboxGamepad.setDeadzone(XboxGamepad.Component.TRIGGER_AXIS, XboxGamepad.DeadzoneType.LINEAR, CONTROLLER_TRIGGER_DEADZONE);

            xboxGamepad.addListener(XboxGamepad.Component.RIGHT_STICK_X_AXIS, AxisMovedEvent.class, event -> rightStickXValue = event.getNewValue());
            xboxGamepad.addListener(XboxGamepad.Component.RIGHT_STICK_Y_AXIS, AxisMovedEvent.class, event -> rightStickYValue = event.getNewValue());

            xboxGamepad.addListener(XboxGamepad.Component.RIGHT_STICK_BUTTON, ButtonPressedEvent.class, event -> {
                setXAngle(CX_DEFAULT);
                setYAngle(CY_DEFAULT);
            });

            xboxGamepad.addListener(XboxGamepad.Component.TRIGGER_AXIS, AxisMovedEvent.class, event -> {
                motorThrottle = -event.getNewValue();
                updateMotors();
            });
            xboxGamepad.addListener(XboxGamepad.Component.LEFT_STICK_X_AXIS, AxisMovedEvent.class, event -> {
                float value = event.getNewValue();
                if (value >= 0f) {
                    motorLeftModifier = 1f;
                    motorRightModifier = 1f - 2 * value;
                }
                else {
                    motorLeftModifier = 1f + 2 * value;
                    motorRightModifier = 1f;
                }
                updateMotors();
            });

            xboxGamepad.startListening();

            Timer timer = new Timer(true);
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    setXAngle(clamp(xAngle + (rightStickXValue * CONTROLLER_DELTA / POLL_RATE), CX_MIN, CX_MAX));
                    setYAngle(clamp(yAngle - (rightStickYValue * CONTROLLER_DELTA / POLL_RATE), CY_MIN, CY_MAX));
                }
            }, 0, 1000 / (POLL_RATE / 10));
        }
        else {
            setGamepadStatus(Status.OFFLINE);
        }

        configurationChoiceBox.getItems().addAll(
                new Configuration("Test", "http://195.235.198.107:3346/axis-cgi/mjpg/video.cgi", "127.0.0.1", "2001"),
                new Configuration("Ethernet", "http://192.168.1.1:8080/?action=stream", "192.168.1.1", "2001"),
                new Configuration("WiFi", "http://192.168.50.122:8080/?action=stream", "192.168.50.122", "2001")
        );
        configurationChoiceBox.valueProperty().addListener((observableValue, oldValue, newValue) -> {
            cameraUrlTextField.setText(newValue.cameraUrl);
            socketHostTextField.setText(newValue.socketHost);
            socketPortTextField.setText(newValue.socketPort);
        });
        configurationChoiceBox.setValue(configurationChoiceBox.getItems().get(0));
    }

    private void updateSocketConnection() {
        String host = socketHostTextField.getText();
        String port = socketPortTextField.getText();

        setSocketStatus(Status.CONNECTING);
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
        sendCommand("cx " + Math.round(angle));
    }

    private void setYAngle(float angle) {
        yAngle = angle;
        Platform.runLater(() -> yAngleLabel.setText(String.valueOf(Math.round(angle))));
        sendCommand("cy " + Math.round(angle));
    }

    private void updateMotors() {
        float motorLeft = motorThrottle * motorLeftModifier;
        float motorRight = motorThrottle * motorRightModifier;

        Platform.runLater(() -> motorLeftLabel.setText(String.valueOf(Math.round(100 * motorLeft))));
        Platform.runLater(() -> motorRightLabel.setText(String.valueOf(Math.round(100 * motorRight))));

        sendCommand("ml " + Math.round(10000 * motorLeft));
        sendCommand("mr " + Math.round(10000 * motorRight));
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
            case CONNECTING:
                text = "Camera connecting...";
                break;
            default:
                text = "";
        }
        Platform.runLater(() -> cameraStatusLabel.setText(text));
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
            case CONNECTING:
                text = "Socket connecting...";
                break;
            default:
                text = "";
        }
        Platform.runLater(() -> socketStatusLabel.setText(text));
    }

    private void setGamepadStatus(Status status) {
        String text;
        switch (status) {
            case ONLINE:
                text = "Gamepad online";
                break;
            case OFFLINE:
                text = "Gamepad offline";
                break;
            case CONNECTING:
                text = "Gamepad connecting...";
                break;
            default:
                text = "";
        }
        Platform.runLater(() -> gamepadStatusLabel.setText(text));
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
                    setYAngle(clamp(newAngle, CY_MIN , CY_MAX));
                    break;
                case S:
                    newAngle = yAngle - KEYBOARD_DELTA;
                    setYAngle(clamp(newAngle, CY_MIN , CY_MAX));
                    break;
                case D:
                    newAngle = xAngle + KEYBOARD_DELTA;
                    setXAngle(clamp(newAngle, CX_MIN , CX_MAX));
                    break;
                case A:
                    newAngle = xAngle - KEYBOARD_DELTA;
                    setXAngle(clamp(newAngle, CX_MIN, CX_MAX));
                    break;
                default:
                    break;
            }
        });
    }

    private static enum Status {
        ONLINE,
        OFFLINE,
        CONNECTING
    }

    private static class Configuration {
        private String name;
        private String cameraUrl;
        private String socketHost;
        private String socketPort;

        private Configuration(String name, String cameraUrl, String socketHost, String socketPort) {
            this.name = name;
            this.cameraUrl = cameraUrl;
            this.socketHost = socketHost;
            this.socketPort = socketPort;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
