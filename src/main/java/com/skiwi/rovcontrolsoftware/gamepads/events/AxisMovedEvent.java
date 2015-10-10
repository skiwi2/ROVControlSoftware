package com.skiwi.rovcontrolsoftware.gamepads.events;

/**
 * @author Frank van Heeswijk
 */
public class AxisMovedEvent implements AxisEvent {
    private final long time;
    private final float newValue;

    public AxisMovedEvent(long time, float newValue) {
        this.time = time;
        this.newValue = newValue;
    }

    @Override
    public long getTime() {
        return time;
    }

    public float getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return "AxisMovedEvent(" + time + ", " + newValue + ")";
    }
}
