package ua.bookloom.api;

/** Restarts the application, so a setting that applies only at launch (the app language) can take effect. */
public interface AppRestart {

    /** Asks the application to close and start again; a binding that cannot restart does nothing. */
    void restart();
}
