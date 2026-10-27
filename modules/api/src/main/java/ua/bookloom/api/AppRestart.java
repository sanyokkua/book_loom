package ua.bookloom.api;

/** Restarts the application, so a setting that applies only at launch (the app language) can take effect. */
public interface AppRestart {

    /**
     * Asks the application to close and start again.
     *
     * @return {@code ok(true)} when a new instance was started and this one is closing; {@code ok(false)} when this
     *     binding cannot restart; an error when the restart was tried and failed
     */
    Result<Boolean> restart();
}
