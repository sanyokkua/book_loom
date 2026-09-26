package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.OptionalInt;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The one registry of screens: which have a view resource, which are inert, how they are grouped and numbered. Runs
 * on the headless toolkit because "its resource loads" is only true once an FXML file has actually been parsed.
 */
class ViewNamesTest extends ApplicationTest {

    @Override
    public void start(final Stage stage) {
        // No window is needed; the toolkit only has to be running for a node to be constructed.
    }

    // IF the export constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder: it names a controller that only the application's injector can supply.)
    @Test
    void resource_export_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.EXPORT.resource()).hasValue("/ua/bookloom/ui/screen/export.fxml");
        assertThat(ViewNames.EXPORT.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/export.fxml"))
                .isNotNull();
    }

    // IF the translating constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder: it names a controller that only the application's injector can supply.)
    @Test
    void resource_translating_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.TRANSLATING.resource()).hasValue("/ua/bookloom/ui/screen/translating.fxml");
        assertThat(ViewNames.TRANSLATING.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/translating.fxml"))
                .isNotNull();
    }

    // IF the import constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder either: it names a controller that only the application's injector can supply.)
    @Test
    void resource_import_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.IMPORT.resource()).hasValue("/ua/bookloom/ui/screen/import.fxml");
        assertThat(ViewNames.IMPORT.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/import.fxml"))
                .isNotNull();
    }

    // IF the brief constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder: it names a controller that only the application's injector can supply.)
    @Test
    void resource_bookBrief_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.BOOK_BRIEF.resource()).hasValue("/ua/bookloom/ui/screen/book-brief.fxml");
        assertThat(ViewNames.BOOK_BRIEF.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/book-brief.fxml"))
                .isNotNull();
    }

    // IF the structure constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder: it names a controller that only the application's injector can supply.)
    @Test
    void resource_structure_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.STRUCTURE.resource()).hasValue("/ua/bookloom/ui/screen/structure.fxml");
        assertThat(ViewNames.STRUCTURE.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/structure.fxml"))
                .isNotNull();
    }

    // IF the settings constant pointed at a misspelt path, THEN navigating to it would load nothing. (Its file is no
    // longer a placeholder: it names a controller that only the application's injector can supply, so it is not loaded
    // here.)
    @Test
    void resource_settings_isItsFxmlFileAndTheConstantIsAvailable() {
        assertThat(ViewNames.SETTINGS.resource()).hasValue("/ua/bookloom/ui/screen/settings.fxml");
        assertThat(ViewNames.SETTINGS.isAvailable()).isTrue();
        assertThat(ViewNames.class.getResource("/ua/bookloom/ui/screen/settings.fxml"))
                .isNotNull();
    }

    // IF an inert constant carried a resource, THEN activating it could reach a view the specification says is absent.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"PROJECTS", "NAMES_STYLE", "REVIEW"})
    void resource_inertConstant_isAbsentAndTheConstantIsUnavailable(final ViewNames view) {
        assertThat(view.resource()).isEmpty();
        assertThat(view.isAvailable()).isFalse();
    }

    // IF a constant were mapped to a neighbour's key, THEN its navigation label and breadcrumb would name the wrong
    // screen.
    @ParameterizedTest
    @CsvSource({
        "PROJECTS, NAV_PROJECTS",
        "IMPORT, NAV_IMPORT",
        "BOOK_BRIEF, NAV_BRIEF",
        "STRUCTURE, NAV_STRUCTURE",
        "NAMES_STYLE, NAV_NAMES_AND_STYLE",
        "TRANSLATING, NAV_TRANSLATING",
        "REVIEW, NAV_REVIEW",
        "EXPORT, NAV_EXPORT",
        "SETTINGS, NAV_SETTINGS",
    })
    void messageKey_everyConstant_isItsOwnNavigationKey(final ViewNames view, final MessageKey expected) {
        assertThat(view.messageKey()).isEqualTo(expected);
    }

    // IF the step numbers drifted from reading order, THEN the navigation column would number the workflow wrongly.
    @ParameterizedTest
    @CsvSource({
        "IMPORT, 1",
        "BOOK_BRIEF, 2",
        "STRUCTURE, 3",
        "NAMES_STYLE, 4",
        "TRANSLATING, 5",
        "REVIEW, 6",
        "EXPORT, 7",
    })
    void step_workflowConstant_isItsPositionInTheWorkflow(final ViewNames view, final int expected) {
        assertThat(view.step()).isEqualTo(OptionalInt.of(expected));
    }

    // IF projects or a non-workflow entry were numbered, THEN the column would show a step that is not one.
    @ParameterizedTest
    @EnumSource(
            value = ViewNames.class,
            names = {"PROJECTS", "SETTINGS"})
    void step_unnumberedConstant_isEmpty(final ViewNames view) {
        assertThat(view.step()).isEmpty();
    }

    // IF an entry sat in the wrong group, THEN it would render under the wrong heading.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "WORKFLOW | PROJECTS IMPORT BOOK_BRIEF STRUCTURE NAMES_STYLE TRANSLATING REVIEW EXPORT",
                "APPLICATION | SETTINGS",
            })
    void group_everyConstant_belongsToExactlyItsGroupInReadingOrder(final NavGroup group, final String expected) {
        assertThat(Arrays.stream(ViewNames.values())
                        .filter(view -> view.group() == group)
                        .map(ViewNames::name))
                .containsExactly(expected.split(" "));
    }

    @Test
    void values_declarationOrder_isTheReadingOrderOfTheNavigation() {
        // IF the order changed, THEN the generated navigation column and "next step" would follow the wrong sequence.
        assertThat(Arrays.stream(ViewNames.values()).map(ViewNames::name))
                .containsExactly(
                        "PROJECTS",
                        "IMPORT",
                        "BOOK_BRIEF",
                        "STRUCTURE",
                        "NAMES_STYLE",
                        "TRANSLATING",
                        "REVIEW",
                        "EXPORT",
                        "SETTINGS");
    }
}
