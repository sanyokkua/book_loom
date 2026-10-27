package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.state.ActivityKind;

/**
 * The names pass in the progress dialog lists its eight stages, not its requests: each row has the stage's own name and
 * a count of the requests it sent, and a stage not started says so. No stage knows its total up front, so a count
 * never reads "of N".
 */
class ProgressStageRowsTest extends ProgressWorkTestBase {

    private static final List<String> EN_NAMES = List.of(
            "Find candidates in the book",
            "Decide the names",
            "Find repeated words",
            "Clean up repeated words",
            "Read repeated words in context",
            "Suggest translations for names",
            "Suggest translations for repeated words",
            "Write the results");

    private static final List<String> UK_NAMES = List.of(
            "Знайти кандидатів у книжці",
            "Визначити імена",
            "Знайти слова, що повторюються",
            "Прибрати зайві повторювані слова",
            "Прочитати повторювані слова в контексті",
            "Запропонувати переклади імен",
            "Запропонувати переклади повторюваних слів",
            "Записати результати");

    private void passAtStage(final int[] requests, final int running) throws TimeoutException {
        final var names = begin(ActivityKind.NAMES_PASS);
        feed(names, PassEvents.upTo(requests, running, 49));
        await(this::isDialogShown);
        await(() -> drawnRows() == 8);
    }

    private List<String> names() {
        return IntStream.range(0, 8).mapToObj(this::stepName).toList();
    }

    private List<String> results() {
        return IntStream.range(0, 8).mapToObj(this::stepResult).toList();
    }

    // IF the list showed one row per request, THEN a 40-request pass would bury its eight steps.
    @Test
    void stageRows_namesPassAtFifthStep_showsEightRowsAndCounts() throws TimeoutException {
        passAtStage(new int[] {0, 6, 0, 3, 25}, 4);

        assertThat(names()).containsExactlyElementsOf(EN_NAMES);
        assertThat(results())
                .containsExactly(
                        "no request",
                        "6 requests",
                        "no request",
                        "3 requests",
                        "25 requests so far",
                        "Not started",
                        "Not started",
                        "Not started");
    }

    // IF a stage that sent nothing read "0 requests", THEN a skipped stage would look like a failed one.
    @Test
    void stageRows_finishedStageWithNoCall_readsNoRequest() throws TimeoutException {
        passAtStage(new int[] {0, 1, 4}, 2);

        assertThat(stepResult(0)).isEqualTo("no request");
        assertThat(stepResult(1)).isEqualTo("1 request");
        assertThat(stepResult(2)).isEqualTo("4 requests so far");
    }

    // IF the count were worded by hand, THEN Ukrainian would read "5 запити".
    @Test
    void stageRows_ukrainianInterface_countsWithTheRightPluralForm() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));

        passAtStage(new int[] {1, 3, 5, 22, 2}, 4);

        assertThat(names()).containsExactlyElementsOf(UK_NAMES);
        assertThat(results().subList(0, 5))
                .containsExactly("1 запит", "3 запити", "5 запитів", "22 запити", "2 запити досі");
    }

    // IF a count read "25 of 30", THEN it would promise a total the pass never knew.
    @Test
    void stageRows_requestsCounted_neverNameATotal() throws TimeoutException {
        passAtStage(new int[] {0, 6, 0, 3, 25}, 4);

        assertThat(results()).noneMatch(line -> line.contains(" of "));
    }

    // IF Escape cancelled the pass from the new rows, THEN minutes of work would be lost to one key.
    @Test
    void stageRows_escapePressed_hidesTheDialogAndTheWorkRuns() throws TimeoutException {
        passAtStage(new int[] {0, 6}, 1);

        escape();

        await(() -> !isDialogShown());
        assertThat(cancelled).isEmpty();
    }

    @Test
    void stageRows_namesPass_offersContinueInBackground() throws TimeoutException {
        passAtStage(new int[] {0, 6}, 1);

        assertThat(((Button) required("progress-background")).getText()).isEqualTo("Continue in background");
    }
}
