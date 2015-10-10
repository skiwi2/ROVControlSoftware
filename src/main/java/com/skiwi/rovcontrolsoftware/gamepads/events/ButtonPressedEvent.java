package com.skiwi.rovcontrolsoftware.gamepads.events;

/**
 * @author Frank van Heeswijk
 */
public class ButtonPressedEvent implements ButtonEvent {
    private final long time;

    public ButtonPressedEvent(long time) {
        this.time = time;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "ButtonPressedEvent(" + time + ")";
    }
}
