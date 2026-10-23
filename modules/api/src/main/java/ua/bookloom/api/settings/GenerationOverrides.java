package ua.bookloom.api.settings;

import java.util.Optional;

/**
 * Names the environment variable or system property that decides a Generation setting for this launch, so Settings
 * can say that its own choice is not the one in force.
 */
public interface GenerationOverrides {

    /**
     * The source of the thinking level.
     *
     * @return the variable or property name, or empty when the stored choice decides
     */
    Optional<String> reasoning();

    /**
     * The source of the sampling mode.
     *
     * @return the variable or property name, or empty when the stored choice decides
     */
    Optional<String> sampling();
}
