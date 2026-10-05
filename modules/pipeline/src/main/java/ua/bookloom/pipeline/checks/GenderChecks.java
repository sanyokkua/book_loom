package ua.bookloom.pipeline.checks;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The gender checks a language file can name with {@code genderCheck=<name>}. A language that names none, or names
 * one nobody has written, gets the check that finds nothing.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GenderChecks {

    private static final GenderCheck NONE = (target, narrator) -> List.of();
    private static final Map<String, GenderCheck> BY_NAME = Map.of("uk", new UkrainianGenderCheck());

    /**
     * The check a language file named.
     *
     * @param name the file's {@code genderCheck} value, or null when the file has none
     * @return the named check, else one that finds nothing; never null
     */
    public static GenderCheck named(@Nullable final String name) {
        final GenderCheck check = name == null ? null : BY_NAME.get(name);
        log.debug("Gender check name={} found={}", name, check != null);
        return check == null ? NONE : check;
    }
}
