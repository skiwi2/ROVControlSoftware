package com.skiwi.rovcontrolsoftware.gamepads.events;

/**
 * @author Frank van Heeswijk
 */
public class ButtonClickedEvent implements ButtonEvent {
    private final long time;
    private final long clickTime;

    public ButtonClickedEvent(long time, long clickTime) {
        this.time = time;
        this.clickTime = clickTime;
    }

    public long getClickTime() {
        return clickTime;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "ButtonClickedEvent(" + time + ", " + clickTime + ")";
    }
}
