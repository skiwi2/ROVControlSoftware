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
import javafx.scene.control.ChoiceBox;
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
    private static final int CONTROLLER_DELTA = 500;
    private static final int KEYBOARD_DELTA = 5;

    private static final float CONTROLLER_RS_DEADZONE = 0.25f;
    private static final float CONTROLLER_TRIGGER_DEADZONE = 0.10f;
    private static final float CONTROLLER_LS_DEADZONE = 0.25f;

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

        setXAngle(90f);
        setYAngle(90f);

        setMotors(0f, 1f, 1f);

        List<Controller> gamepads = Arrays.stream(ControllerEnvironment.getDefaultEnvironment().getControllers())
                .filter(controller -> controller.getType().equals(Controller.Type.GAMEPAD))
                .collect(Collectors.toList());
        if (!gamepads.isEmpty()) {
            setGamepadStatus(Status.ONLINE);
            Controller controller = gamepads.get(0);

            //Right thumbstick
            Component rightThumbstickX = controller.getComponent(Component.Identifier.Axis.RX);
            Component rightThumbstickY = controller.getComponent(Component.Identifier.Axis.RY);

            Component leftThumbstickX = controller.getComponent(Component.Identifier.Axis.X);
            Component leftThumbstickY = controller.getComponent(Component.Identifier.Axis.Y);

            //Triggers
            Component trigger = controller.getComponent(Component.Identifier.Axis.Z);

            Timer timer = new Timer(true);
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    controller.poll();
                    float rightThumbstickXValue = rightThumbstickX.getPollData();
                    float rightThumbstickYValue = rightThumbstickY.getPollData();

                    float rightThumbstickMagnitude = (float)Math.sqrt(Math.pow(rightThumbstickXValue, 2) + Math.pow(rightThumbstickYValue, 2));
                    if (rightThumbstickMagnitude < CONTROLLER_RS_DEADZONE) {
                        //do nothing
                    }
                    else {
                        //normalize
                        rightThumbstickXValue /= rightThumbstickMagnitude;
                        rightThumbstickYValue /= rightThumbstickMagnitude;

                        //scaling
                        rightThumbstickXValue *= ((rightThumbstickMagnitude - CONTROLLER_RS_DEADZONE) / (1f - CONTROLLER_RS_DEADZONE));
                        rightThumbstickYValue *= ((rightThumbstickMagnitude - CONTROLLER_RS_DEADZONE) / (1f - CONTROLLER_RS_DEADZONE));

                        setXAngle(clamp(xAngle + (rightThumbstickXValue * CONTROLLER_DELTA / POLL_RATE), CX_MIN, CX_MAX));
                        setYAngle(clamp(yAngle + (-rightThumbstickYValue * CONTROLLER_DELTA / POLL_RATE), CY_MIN, CY_MAX));
                    }

                    float triggerValue = trigger.getPollData();
                    if (Math.abs(triggerValue) < CONTROLLER_TRIGGER_DEADZONE) {
                        triggerValue = 0f;
                    }
                    else {
                        //scaling
                        triggerValue *= ((Math.abs(triggerValue) - CONTROLLER_TRIGGER_DEADZONE) / (1f - CONTROLLER_TRIGGER_DEADZONE));
                        triggerValue *= -1; //pushing right trigger accelerates, left trigger decelerates
                    }

                    float leftThumbstickXValue = leftThumbstickX.getPollData();
                    float leftThumbstickYValue = leftThumbstickY.getPollData();

                    float leftModifier;
                    float rightModifier;

                    float leftThumbstickMagnitude = (float)Math.sqrt(Math.pow(leftThumbstickXValue, 2) + Math.pow(leftThumbstickYValue, 2));
                    if (leftThumbstickMagnitude < CONTROLLER_LS_DEADZONE) {
                        leftModifier = 1f;
                        rightModifier = 1f;
                    }
                    else {
                        //normalize
                        leftThumbstickXValue /= leftThumbstickMagnitude;

                        //scaling
                        leftThumbstickXValue *= ((leftThumbstickMagnitude - CONTROLLER_LS_DEADZONE) / (1f - CONTROLLER_LS_DEADZONE));

                        if (leftThumbstickXValue >= 0f) {
                            leftModifier = 1f;
                            rightModifier = 1f - 2 * leftThumbstickXValue;
                        }
                        else {
                            leftModifier = 1f + 2 * leftThumbstickXValue;
                            rightModifier = 1f;
                        }
                    }

                    setMotors(triggerValue, leftModifier, rightModifier);
                }
            };
            timer.scheduleAtFixedRate(timerTask, 0, 1000 / POLL_RATE);
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

    private void setMotors(float throttle, float leftModifier, float rightModifier) {
        motorThrottle = throttle;
        motorLeftModifier = leftModifier;
        motorRightModifier = rightModifier;

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
