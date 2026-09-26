package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The rest of the brief — tone, policies, balance, quality and the auxiliary-text switches — drawn with the control
 * the reference draws for each and switched off, because nothing downstream reads any of them. Each is looked up in the
 * real scene of a brief that has a book open.
 */
class BookBriefScreenDisabledCardsTest extends BookBriefScreenTestBase {

    @TempDir
    private Path dir;

    @BeforeEach
    void openTheBrief() throws TimeoutException {
        openBookThenShowBrief(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
    }

    static Stream<Arguments> disabledControls() {
        return Stream.of(
                Arguments.of("brief-tone-genre", TextField.class),
                Arguments.of("brief-tone-register", SegmentedButton.class),
                Arguments.of("brief-tone-voice", TextArea.class),
                Arguments.of("brief-tone-audience", TextField.class),
                Arguments.of("brief-policy-names", SegmentedButton.class),
                Arguments.of("brief-policy-foreign", SegmentedButton.class),
                Arguments.of("brief-policy-footnotes", SegmentedButton.class),
                Arguments.of("brief-policy-units", SegmentedButton.class),
                Arguments.of("brief-policy-balance", Slider.class),
                Arguments.of("brief-aux-nav", ToggleSwitch.class),
                Arguments.of("brief-aux-alt", ToggleSwitch.class),
                Arguments.of("brief-aux-metadata", ToggleSwitch.class),
                Arguments.of("brief-aux-frontmatter", ToggleSwitch.class),
                Arguments.of("brief-quality", SegmentedButton.class));
    }

    // IF a control were missing, or drawn as another kind, THEN the brief would not show what it is going to be; and IF
    // it were enabled, THEN it would be a control that silently does nothing.
    @ParameterizedTest
    @MethodSource("disabledControls")
    void control_briefShown_isPresentOfTheDrawnKindAndDisabled(final String id, final Class<?> kind) {
        assertThat(required(id)).isInstanceOf(kind);
        assertThat(required(id).isDisabled()).isTrue();
    }

    // IF a picker had the wrong number of options, THEN it would not match the reference's choices.
    @ParameterizedTest
    @CsvSource({
        "brief-tone-register, 3",
        "brief-policy-names, 3",
        "brief-policy-foreign, 3",
        "brief-policy-footnotes, 2",
        "brief-policy-units, 2",
        "brief-quality, 3"
    })
    void segmentedControl_briefShown_hasTheReferencesNumberOfButtons(final String id, final int count) {
        assertThat(segmented(id).getButtons()).hasSize(count);
    }

    // IF the quality dial were three separate controls or a slider, THEN it would not be the segmented picker the
    // reference draws; and IF its middle were not chosen, THEN the drawn brief would differ from the reference.
    @Test
    void quality_briefShown_isOneSegmentedControlWithTheMiddleButtonSelected() {
        assertThat(selectedStates("brief-quality")).containsExactly(false, true, false);
    }

    // IF an auxiliary-text choice were a checkbox, THEN it would not be the toggle switch the reference draws; and IF
    // its drawn state differed, THEN the brief would misstate the defaults.
    @ParameterizedTest
    @CsvSource({"brief-aux-nav, true", "brief-aux-alt, true", "brief-aux-metadata, true", "brief-aux-frontmatter, false"
    })
    void auxiliaryChoice_briefShown_isAToggleSwitchNotACheckBoxInTheDrawnState(
            final String id, final boolean selected) {
        assertThat(isCheckBox(id)).isFalse();
        assertThat(toggle(id).isSelected()).isEqualTo(selected);
    }

    // IF the multi-line input carried no brief rules, THEN it would show Modena's own greys instead of the surface
    // role.
    @Test
    void voiceInput_briefShown_isPaintedWithTheSurfaceRole() {
        final TextArea voice = (TextArea) required("brief-tone-voice");

        assertThat(voice.getBackground().getFills().get(0).getFill()).isEqualTo(Color.web("#ffffff"));
    }

    // IF the brief read a disabled control, THEN the request would carry something the person never chose.
    @Test
    void request_qualityDialDrawnAtItsMiddle_carriesOnlySourceDestinationTargetAndOverwrite() {
        final Optional<TranslationRequest> request =
                ThemeTestSupport.onFx(() -> briefModel().request());

        assertThat(request)
                .hasValue(new TranslationRequest(
                        dir.resolve("Frankenstein.epub"), dir.resolve("Frankenstein.uk.epub"), "uk", null, false));
    }
}
