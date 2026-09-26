package ua.bookloom.ui.state;

/** How one of the dashboard's run controls presents itself. */
public enum ControlState {
    /** Not part of the screen in this state: neither seen nor pressable. */
    HIDDEN(false, false),
    /** Seen but dimmed, because its request is already pending or its moment has not come. */
    DISABLED(true, false),
    /** Seen and pressable. */
    ENABLED(true, true);

    private final boolean shown;
    private final boolean enabled;

    ControlState(final boolean shown, final boolean enabled) {
        this.shown = shown;
        this.enabled = enabled;
    }

    /**
     * Whether the control is part of the screen.
     *
     * @return {@code true} if a person can see it, {@code false} otherwise
     */
    public boolean isShown() {
        return shown;
    }

    /**
     * Whether the control can be pressed.
     *
     * @return {@code true} if a press is honoured, {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
}
