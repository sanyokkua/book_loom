package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Whether the conformance cases cover every screen the application ships; a screen added later fails until it does. */
class ScreenConformanceCoverageTest {

    // IF a screen were added to the application without a conformance case, THEN it would ship unchecked against the
    // reference rendering; this fails until the case exists.
    @Test
    void screens_shippedSet_coversEverySeenScreenAndEveryDialog() {
        assertThat(ConformanceCases.SCREENS.stream()
                        .map(ConformanceCases.Screen::name)
                        .distinct())
                .contains(
                        "SHELL",
                        "SHELL_RUN_STATUS",
                        "SETTINGS",
                        "IMPORT",
                        "BOOK_BRIEF",
                        "STRUCTURE",
                        "TRANSLATING",
                        "EXPORT",
                        "ABOUT_DIALOG",
                        "ERROR_DIALOG",
                        "REPLACE_RUN_DIALOG");
    }

    // IF a reachable view had no case here, THEN a screen added later would not be measured against the mockup.
    @Test
    void screens_everyAvailableView_hasAtLeastOneConformanceCase() {
        final Set<ViewNames> available =
                Arrays.stream(ViewNames.values()).filter(ViewNames::isAvailable).collect(Collectors.toSet());
        final Set<ViewNames> covered = ConformanceCases.SCREENS.stream()
                .map(ConformanceCases.Screen::view)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(available).hasSize(6);
        assertThat(covered).containsAll(available);
    }
}
