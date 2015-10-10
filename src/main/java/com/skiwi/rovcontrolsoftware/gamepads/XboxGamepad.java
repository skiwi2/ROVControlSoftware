package com.skiwi.rovcontrolsoftware.gamepads;

import com.skiwi.rovcontrolsoftware.gamepads.events.*;
import net.java.games.input.Controller;
import net.java.games.input.EventQueue;

import java.util.*;
import java.util.function.Consumer;

import static com.skiwi.rovcontrolsoftware.gamepads.XboxGamepad.Component.*;

/**
 * @author Frank van Heeswijk
 */
public class XboxGamepad {
    private static final List<List<Component>> RADIAL_DEADZONE_AXES = Arrays.asList(
        Arrays.asList(LEFT_STICK_X_AXIS, LEFT_STICK_Y_AXIS),
        Arrays.asList(RIGHT_STICK_X_AXIS, RIGHT_STICK_Y_AXIS)
    );

    private final Controller gamepad;
    private final int pollDelay;

    private final EventQueue eventQueue;
    private final net.java.games.input.Event event;

    private final Map<Component, Map<Class, List<Consumer<Event>>>> componentEventListeners = new EnumMap<>(Component.class);

    private final Map<Component, Long> buttonLastPressedTime = new EnumMap<>(Component.class);

    private final List<Deadzone> axisDeadzones = new ArrayList<>();
    private final Map<Component, List<Deadzone>> axisToDeadzonesMap = new EnumMap<>(Component.class);

    private final Map<Component, Float> axisValues = new EnumMap<>(Component.class);
    private final Map<Component, Boolean> axisInDeadzone = new EnumMap<>(Component.class);

    private Timer pollTimer;

    public XboxGamepad(Controller gamepad, int pollDelay) {
        this.gamepad = gamepad;
        this.pollDelay = pollDelay;

        this.eventQueue = gamepad.getEventQueue();
        this.event = new net.java.games.input.Event();
    }

    public void startListening() {
        if (pollTimer != null) {
            throw new IllegalStateException("You are already listening to events");
        }
        pollTimer = new Timer(true);
        this.pollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                poll();
            }
        }, 0, pollDelay);
    }

    public void stopListening() {
        if (pollTimer == null) {
            throw new IllegalStateException("You never started listening to events");
        }
        pollTimer.cancel();
        pollTimer = null;
    }

    public <T extends Event> void addListener(Component component, Class<T> eventClass, Consumer<T> eventListener) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(eventClass, "eventClass");
        Objects.requireNonNull(eventListener, "eventListener");
        componentEventListeners.putIfAbsent(component, new HashMap<>());
        Map<Class, List<Consumer<Event>>> eventListenerMap = componentEventListeners.get(component);
        eventListenerMap.putIfAbsent(eventClass, new ArrayList<>());
        List<Consumer<Event>> eventListeners = eventListenerMap.get(eventClass);
        eventListeners.add((Consumer<Event>)eventListener);
    }

    public void setDeadzone(Component component, DeadzoneType deadzoneType, float value) {
        if (value < 0f) {
            throw new IllegalArgumentException("You cannot set a deadzone with a negative value: component = " + component + ", deadzoneType = " + deadzoneType + ", value = " + value);
        }
        switch (deadzoneType) {
            case LINEAR:
                addDeadzone(new Deadzone(component, Arrays.asList(component), deadzoneType, value));
                break;
            case RADIAL:
                List<Component> radialAxes = RADIAL_DEADZONE_AXES.stream()
                        .filter(axes -> axes.contains(component))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Component " + component + " is not part of a radial deadzone axes group"));
                addDeadzone(new Deadzone(component, radialAxes, deadzoneType, value));
                break;
        }
    }

    private void addDeadzone(Deadzone deadzone) {
        axisDeadzones.add(deadzone);
        axisToDeadzonesMap.clear();
        for (Deadzone axisDeadzone : axisDeadzones) {
            for (Component axis : axisDeadzone.axes) {
                axisToDeadzonesMap.putIfAbsent(axis, new ArrayList<>());
                axisToDeadzonesMap.get(axis).add(axisDeadzone);
            }
        }
    }

    private void poll() {
        gamepad.poll();
        while (eventQueue.getNextEvent(event)) {
            net.java.games.input.Component component = event.getComponent();
            float value = event.getValue();
            long timeNanos = event.getNanos();
            if (!component.isAnalog()) {
                if (component.getIdentifier() == net.java.games.input.Component.Identifier.Axis.POV) {
                    //directional pad
                    if (value == 0f) {
                        fireEvent(DPAD_ANY, new ButtonReleasedEvent(timeNanos));
                    }
                    else {
                        Component dpadComponent = dpadToComponent(value);
                        if (dpadComponent == null) {
                            System.out.println("Unable to process dpad value " + value);
                        }
                        else {
                            fireEvent(dpadComponent, new ButtonPressedEvent(timeNanos));
                        }
                    }
                }
                else {
                    //button
                    Component buttonComponent = buttonToComponent(component.getIdentifier());
                    if (buttonComponent == null) {
                        System.out.println("Unable to map button " + buttonComponent);
                    }
                    else {
                        if (value == 1f) {
                            buttonLastPressedTime.put(buttonComponent, timeNanos);
                            fireEvent(buttonComponent, new ButtonPressedEvent(timeNanos));
                        } else if (value == 0f) {
                            fireEvent(buttonComponent, new ButtonReleasedEvent(timeNanos));
                            fireEvent(buttonComponent, new ButtonClickedEvent(timeNanos, timeNanos - buttonLastPressedTime.getOrDefault(buttonComponent, timeNanos)));
                        }
                        else {
                            System.out.println("Unable to process button value " + value + " for " + buttonComponent);
                        }
                    }
                }
            }
            else {
                //no button
                Component axisComponent = axisToComponent(component.getIdentifier());
                if (axisComponent == null) {
                    System.out.println("Unable to map axis " + axisComponent);
                }
                else {
                    axisValues.put(axisComponent, value);
                    List<Deadzone> activeDeadzones = axisToDeadzonesMap.getOrDefault(axisComponent, Collections.emptyList());

                    if (activeDeadzones.isEmpty()) {
                        //no deadzone
                        fireEvent(axisComponent, new AxisMovedEvent(timeNanos, value));
                    }
                    else {
                        //has at least one deadzone associated
                        for (Deadzone deadzone : activeDeadzones) {
                            if (deadzone.deadzoneType == DeadzoneType.LINEAR) {
                                float absoluteValue = Math.abs(value);

                                if (absoluteValue < deadzone.value) {
                                    if (!axisInDeadzone.getOrDefault(axisComponent, false)) {
                                        fireEvent(axisComponent, new AxisMovedEvent(timeNanos, 0f));
                                        axisInDeadzone.put(axisComponent, true);
                                    }
                                    continue;
                                }

                                float newValue = value * ((absoluteValue - deadzone.value) / (1f - deadzone.value));
                                if (axisComponent == deadzone.eventAxis) {
                                    fireEvent(axisComponent, new AxisMovedEvent(timeNanos, newValue));
                                    axisInDeadzone.put(axisComponent, false);
                                }
                            }
                            else if (deadzone.deadzoneType == DeadzoneType.RADIAL) {
                                Component deadzoneXAxis = deadzone.axes.get(0);
                                Component deadzoneYAxis = deadzone.axes.get(1);

                                float xValue = axisValues.getOrDefault(deadzoneXAxis, 0f);
                                float yValue = axisValues.getOrDefault(deadzoneYAxis, 0f);

                                float magnitude = (float)Math.sqrt(Math.pow(xValue, 2) + Math.pow(yValue, 2));
                                if (magnitude < deadzone.value) {
                                    if (!axisInDeadzone.getOrDefault(axisComponent, false)) {
                                        fireEvent(axisComponent, new AxisMovedEvent(timeNanos, 0f));
                                        axisInDeadzone.put(axisComponent, true);
                                    }
                                    continue;
                                }

                                if (deadzoneXAxis == axisComponent && axisComponent == deadzone.eventAxis) {
                                    float newXValue = (xValue / magnitude) * ((magnitude - deadzone.value) / (1f - deadzone.value));
                                    fireEvent(axisComponent, new AxisMovedEvent(timeNanos, newXValue));
                                    axisInDeadzone.put(axisComponent, false);
                                }
                                else if (deadzoneYAxis == axisComponent && axisComponent == deadzone.eventAxis) {
                                    float newYValue = (yValue / magnitude) * ((magnitude - deadzone.value) / (1f - deadzone.value));
                                    fireEvent(axisComponent, new AxisMovedEvent(timeNanos, newYValue));
                                    axisInDeadzone.put(axisComponent, false);
                                }
                            }
                            else {
                                System.out.println("Unknown deadzone type " + deadzone.deadzoneType);
                            }
                        }
                    }
                }
            }
        }
    }

    private static Component dpadToComponent(float value) {
        if (value == 0.125f) {
            return DPAD_UPLEFT;
        }
        else if (value == 0.25f) {
            return DPAD_UP;
        }
        else if (value == 0.375f) {
            return DPAD_UPRIGHT;
        }
        else if (value == 0.5f) {
            return DPAD_RIGHT;
        }
        else if (value == 0.625f) {
            return DPAD_DOWNRIGHT;
        }
        else if (value == 0.75f) {
            return DPAD_DOWN;
        }
        else if (value == 0.875f) {
            return DPAD_DOWNLEFT;
        }
        else if (value == 1f) {
            return DPAD_LEFT;
        }
        else {
            return null;
        }
    }

    private static final Map<net.java.games.input.Component.Identifier, Component> BUTTON_COMPONENT_MAP = new IdentityHashMap<>();
    static {
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._4, LEFT_SHOULDER_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._5, RIGHT_SHOULDER_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._8, LEFT_STICK_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._9, RIGHT_STICK_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._0, A_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._2, X_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._3, Y_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._1, B_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._6, BACK_BUTTON);
        BUTTON_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Button._7, SELECT_BUTTON);
    }

    private static Component buttonToComponent(net.java.games.input.Component.Identifier button) {
        return BUTTON_COMPONENT_MAP.get(button);
    }

    private static final Map<net.java.games.input.Component.Identifier, Component> AXIS_COMPONENT_MAP = new IdentityHashMap<>();
    static {
        AXIS_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Axis.X, LEFT_STICK_X_AXIS);
        AXIS_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Axis.Y, LEFT_STICK_Y_AXIS);
        AXIS_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Axis.RX, RIGHT_STICK_X_AXIS);
        AXIS_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Axis.RY, RIGHT_STICK_Y_AXIS);
        AXIS_COMPONENT_MAP.put(net.java.games.input.Component.Identifier.Axis.Z, TRIGGER_AXIS);
    }

    private static Component axisToComponent(net.java.games.input.Component.Identifier axis) {
        return AXIS_COMPONENT_MAP.get(axis);
    }

    private void fireEvent(Component component, Event event) {
        componentEventListeners.getOrDefault(component, Collections.emptyMap()).getOrDefault(event.getClass(), Collections.emptyList()).forEach(listener -> listener.accept(event));
    }

    public static enum Component {
        LEFT_SHOULDER_BUTTON,
        RIGHT_SHOULDER_BUTTON,
        LEFT_STICK_BUTTON,
        RIGHT_STICK_BUTTON,
        A_BUTTON,
        X_BUTTON,
        Y_BUTTON,
        B_BUTTON,
        BACK_BUTTON,
        SELECT_BUTTON,
        DPAD_ANY,
        DPAD_UP,
        DPAD_UPRIGHT,
        DPAD_RIGHT,
        DPAD_DOWNRIGHT,
        DPAD_DOWN,
        DPAD_DOWNLEFT,
        DPAD_LEFT,
        DPAD_UPLEFT,
        LEFT_STICK_X_AXIS,
        LEFT_STICK_Y_AXIS,
        RIGHT_STICK_X_AXIS,
        RIGHT_STICK_Y_AXIS,
        TRIGGER_AXIS
    }

    private static class Deadzone {
        private Component eventAxis;
        private final List<Component> axes;
        private final DeadzoneType deadzoneType;
        private final float value;

        private Deadzone(Component eventAxis, List<Component> axes, DeadzoneType deadzoneType, float value) {
            this.eventAxis = eventAxis;
            this.axes = axes;
            this.deadzoneType = deadzoneType;
            this.value = value;
        }
    }

    public static enum DeadzoneType {
        LINEAR,
        RADIAL
    }
}
