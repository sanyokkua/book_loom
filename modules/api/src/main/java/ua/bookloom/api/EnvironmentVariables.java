package ua.bookloom.api;

/** Tells whether a credential's environment variable exists, without ever handing out its value. */
public interface EnvironmentVariables {

    /**
     * Checks one variable.
     *
     * @param name the variable's name; a blank name is never set
     * @return {@code true} if the variable has a non-blank value, {@code false} otherwise
     */
    boolean isSet(String name);
}
