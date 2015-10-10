package com.skiwi.rovcontrolsoftware.gamepads.events;

/**
 * @author Frank van Heeswijk
 */
public class ButtonReleasedEvent implements ButtonEvent {
    private final long time;

    public ButtonReleasedEvent(long time) {
        this.time = time;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "ButtonReleasedEvent(" + time + ")";
    }
}
