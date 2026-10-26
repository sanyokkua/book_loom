package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.ui.control.StateMarker.Meaning;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunStateView.BannerView;
import ua.bookloom.ui.state.RunStateView.RunAction;

/**
 * The sixteen rows of the README's "Run states" table: for each, the title bar's text, the AI-model chip's colour and
 * word, the toolbar's run actions (id, label, whether they can be pressed) and the banner with its actions. One table
 * drives every surface, so this is where they are proven to agree with the README. The ids below leave out their
 * common {@code translating-} prefix.
 */
class RunStateMappingTest {

    private static final Messages ENGLISH = new Messages((LocaleProvider) () -> Locale.ENGLISH);
    private static final String PREFIX = "translating-";

    private static String toolbar(final RunStateView view) {
        return view.toolbar().stream().map(RunStateMappingTest::action).collect(Collectors.joining("; "));
    }

    private static String action(final RunAction action) {
        return action.id().replace(PREFIX, "") + ":" + action.label().in(ENGLISH) + ":"
                + (action.enabled() ? "on" : "off");
    }

    private static String bannerKey(final RunStateView view) {
        final BannerView banner = view.banner();
        return banner == null ? "-" : banner.key();
    }

    private static String bannerActions(final RunStateView view) {
        final BannerView banner = view.banner();
        return banner == null || banner.actions().isEmpty()
                ? "-"
                : banner.actions().stream()
                        .map(found -> found.id().replace(PREFIX, ""))
                        .collect(Collectors.joining(" "));
    }

    // The README's sixteen rows, by name: 74 of 142 decided (51 %), 6 to review, 4 in flight, Ollama with gemma4:e4b.
    static RunInputs inputsFor(final String row) {
        return switch (row) {
            case "Ready" -> RunInputs.in(RunState.IDLE).decided(0).toReview(0);
            case "Ready to continue" -> RunInputs.in(RunState.IDLE);
            case "Cannot start yet" -> RunInputs.in(RunState.IDLE).model(RunInputs.NO_MODEL);
            case "Translating" -> RunInputs.in(RunState.RUNNING);
            case "Pausing" -> RunInputs.in(RunState.PAUSING);
            case "Paused" -> RunInputs.in(RunState.PAUSED);
            case "Waiting for the AI model" ->
                RunInputs.in(RunState.PAUSED).recovery(RecoveryWaiting.Status.WAITING, ErrorCode.unreachable);
            case "Paused after an error" ->
                RunInputs.in(RunState.PAUSED).recovery(RecoveryWaiting.Status.HELD, ErrorCode.unreachable);
            case "Stopped trying" ->
                RunInputs.in(RunState.PAUSED).recovery(RecoveryWaiting.Status.GAVE_UP, ErrorCode.unreachable);
            case "Model missing" ->
                RunInputs.in(RunState.PAUSED).recovery(RecoveryWaiting.Status.GAVE_UP, ErrorCode.modelUnavailable);
            case "Stuck call" -> RunInputs.in(RunState.RUNNING).waiting(220, 360);
            case "Stopping" -> RunInputs.in(RunState.STOPPING);
            case "Stopped" -> RunInputs.in(RunState.STOPPED);
            case "Finished, to review" -> RunInputs.in(RunState.COMPLETED).decided(142);
            case "Finished, nothing to review" ->
                RunInputs.in(RunState.COMPLETED).decided(142).toReview(0);
            case "Failed" -> RunInputs.in(RunState.FAILED);
            default -> throw new IllegalArgumentException(row);
        };
    }

    // IF a surface worked out its own idea of the state, THEN the title bar, the chip and the toolbar could disagree.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Ready                       | READY              | Ready                             | WAITING   | AI model ready
            Ready to continue           | READY_TO_CONTINUE  | Ready · 51 % done                 | WAITING   | AI model ready
            Cannot start yet            | CANNOT_START       | Ready                             | FAILED    | No AI model
            Translating                 | TRANSLATING        | 51 % done · about 1 h 10 min left | WORKING   | Model answering
            Pausing                     | PAUSING            | Pausing...                          | WORKING   | Model answering
            Paused                      | PAUSED             | Paused at 51 %                    | WAITING   | Model idle
            Waiting for the AI model    | WAITING            | Waiting for Ollama                | FAILED    | Cannot reach Ollama
            Paused after an error       | PAUSED_AFTER_ERROR | Paused at 51 %                    | FAILED    | Cannot reach Ollama
            Stopped trying              | STOPPED_TRYING     | Paused at 51 %                    | FAILED    | Ollama: no answer for 12 h
            Model missing               | MODEL_MISSING      | Paused at 51 %                    | FAILED    | Model missing
            Stuck call                  | STUCK              | 51 % done · about 1 h 10 min left | ATTENTION | No answer for 3:40
            Stopping                    | STOPPING           | Stopping...                         | WORKING   | Model answering
            Stopped                     | STOPPED            | Stopped at 51 %                   | WAITING   | Model idle
            Finished, to review         | FINISHED_TO_REVIEW | Finished                          | WAITING   | Model idle
            Finished, nothing to review | FINISHED_NONE      | Finished                          | WAITING   | Model idle
            Failed                      | FAILED             | Did not finish                    | FAILED    | Error
            """)
    void of_eachReadmeRow_givesItsTitleAndChip(
            final String name, final RunRow row, final String title, final Meaning chip, final String chipWord) {
        final RunStateView view = inputsFor(name).view();

        assertThat(view.row()).isEqualTo(row);
        assertThat(view.title().in(ENGLISH)).isEqualTo(title);
        assertThat(view.chip().meaning()).isEqualTo(chip);
        assertThat(view.chip().word().in(ENGLISH)).isEqualTo(chipWord);
    }

    // IF a row offered an action its state cannot honour, or hid one it can, THEN the person would press the wrong
    // thing.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Ready                       | start:Start translation:on
            Ready to continue           | start:Continue translation:on; review-flagged:Review (6):on
            Cannot start yet            | start:Start translation:off
            Translating                 | pause:Pause:on; stop:Stop:on; review-flagged:Review (6):on
            Pausing                     | pause:Pause:off; stop:Stop:on
            Paused                      | resume:Resume:on; stop:Stop:on; review-flagged:Review (6):on
            Waiting for the AI model    | pause:Pause:on; stop:Stop:on
            Paused after an error       | resume:Resume:on; stop:Stop:on
            Stopped trying              | resume:Try again now:on; stop:Stop:on
            Model missing               | resume:Try again now:on; stop:Stop:on
            Stuck call                  | pause:Pause:on; stop:Stop:on; review-flagged:Review (6):on
            Stopping                    | pause:Pause:off; stop:Stop:off
            Stopped                     | resume:Continue translation:on; review-flagged:Review (6):on
            Finished, to review         | review-flagged:Review (6):on; continue:Continue to Export:on
            Finished, nothing to review | review-flagged:Review (0):on; continue:Continue to Export:on
            Failed                      | start:Continue translation:on; review-flagged:Review (6):on
            """)
    void toolbar_eachReadmeRow_offersItsActions(final String name, final String toolbar) {
        assertThat(toolbar(inputsFor(name).view())).isEqualTo(toolbar);
    }

    // IF a banner were missing or offered the wrong way out, THEN the person would not know what happened or what to
    // do.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            Ready                       | -              | -
            Ready to continue           | welcome        | -
            Cannot start yet            | cannotStart    | open-settings
            Translating                 | -              | -
            Pausing                     | -              | -
            Paused                      | -              | -
            Waiting for the AI model    | waiting        | retry-now skip-segment open-settings
            Paused after an error       | pausedError    | retry-now skip-segment open-settings
            Stopped trying              | gaveUp         | retry-now skip-segment open-settings
            Model missing               | modelMissing   | choose-model
            Stuck call                  | stuck          | -
            Stopping                    | -              | -
            Stopped                     | -              | -
            Finished, to review         | finishedReview | review-segments
            Finished, nothing to review | finishedNone   | -
            Failed                      | failed         | show-details
            """)
    void banner_eachReadmeRow_hasItsBannerAndActions(final String name, final String banner, final String actions) {
        final RunStateView view = inputsFor(name).view();

        assertThat(bannerKey(view)).isEqualTo(banner);
        assertThat(bannerActions(view)).isEqualTo(actions);
    }

    // IF the title bar's control did something else than the toolbar's, THEN the two would disagree about the run.
    @ParameterizedTest
    @CsvSource({
        "RUNNING, pause:Pause:on",
        "PAUSING, pause:Pause:off",
        "PAUSED, resume:Resume:on",
        "STOPPING, resume:Continue translation:off",
        "STOPPED, resume:Continue translation:on",
        "COMPLETED, -",
        "FAILED, -",
        "IDLE, -"
    })
    void titleControl_eachRunState_isTheToolbarsPauseOrResume(final RunState state, final String expected) {
        final RunAction control = RunInputs.in(state).view().titleControl();

        assertThat(control == null ? "-" : action(control)).isEqualTo(expected);
    }

    // IF a stuck call were declared a second early, THEN a slow model would look broken; at a minute it is stuck.
    @ParameterizedTest
    @CsvSource({"0, TRANSLATING", "59, TRANSLATING", "60, STUCK", "220, STUCK"})
    void of_attemptWaited_isStuckFromSixtySeconds(final long seconds, final RunRow expected) {
        assertThat(RunInputs.in(RunState.RUNNING).waiting(seconds, 360).view().row())
                .isEqualTo(expected);
    }

    // IF the chip counted the call's whole wait or a second clock, THEN it would not match the call line's one clock.
    @Test
    void chip_reviewCallAtOneThirtyOfFour_readsNoAnswerForOneThirty() {
        final RunStateView view =
                RunInputs.in(RunState.RUNNING).waiting(90, 240).view();

        assertThat(view.chip().word().in(ENGLISH)).isEqualTo("No answer for 1:30");
    }

    @Test
    void banner_stuckCall_saysThereIsNothingToDoAndWhenItTriesAgain() {
        final BannerView banner =
                RunInputs.in(RunState.RUNNING).waiting(220, 360).view().banner();

        assertThat(banner).isNotNull();
        assertThat(banner.title().in(ENGLISH))
                .isEqualTo("The model is taking longer than usual (3:40). BookLoom tries again by itself at 6:00."
                        + " You do not need to do anything.");
    }

    // A program that answers with an error (HTTP 5xx) or a reply that cannot be read was reached: the banner and the
    // chip say what it answered, never "cannot reach".
    @ParameterizedTest
    @CsvSource({"upstream", "discoveryFailed"})
    void banner_waitingOnAProgramThatAnswersWithAnError_saysItAnsweredNotThatItCannotBeReached(final ErrorCode code) {
        final RunStateView view = RunInputs.in(RunState.PAUSED)
                .recovery(RecoveryWaiting.Status.WAITING, code)
                .view();

        final BannerView banner = Objects.requireNonNull(view.banner());
        assertThat(banner.title().in(ENGLISH)).isEqualTo("Ollama answered with an error");
        assertThat(Objects.requireNonNull(banner.text()).in(ENGLISH))
                .isEqualTo("Ollama answers, but with an error of its own or a reply BookLoom cannot read."
                        + " BookLoom tries again by itself in 0:12.");
        assertThat(view.chip().word().in(ENGLISH)).isEqualTo("Ollama reports an error");
    }

    @Test
    void banner_waitingForOllama_namesTheProgramTheNextTryAndTheRealCount() {
        final BannerView banner = RunInputs.in(RunState.PAUSED)
                .recovery(RecoveryWaiting.Status.WAITING, ErrorCode.unreachable)
                .view()
                .banner();

        assertThat(banner).isNotNull();
        assertThat(banner.title().in(ENGLISH)).isEqualTo("BookLoom cannot reach Ollama");
        assertThat(banner.text()).isNotNull();
        assertThat(banner.text().in(ENGLISH))
                .isEqualTo("Is the Ollama app open? BookLoom tries again by itself in 0:12.");
        assertThat(banner.actions().get(1).label().in(ENGLISH)).isEqualTo("Skip these 4 segments");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Ready to continue | Welcome back — 74 of 142 segments are done.",
                "Cannot start yet | Cannot start yet — no AI model is chosen.",
                "Paused after an error | Paused. Ollama could not be reached.",
                "Stopped trying | Ollama has not answered for 12 hours — BookLoom stopped trying by itself.",
                "Model missing | The AI model gemma4:e4b is not available — check that it is installed.",
                "Finished, to review | Translation finished. 6 segments need your review. You can export now and review later.",
                "Finished, nothing to review | Translation finished. Nothing needs your review.",
                "Failed | The translation stopped because of an error. Finished segments are saved."
            })
    void banner_eachRow_readsAsTheReadmeSays(final String row, final String expected) {
        final BannerView banner = inputsFor(row).view().banner();

        assertThat(banner).isNotNull();
        assertThat(banner.title().in(ENGLISH)).isEqualTo(expected);
    }

    // IF a finished run's banner showed on every screen, THEN it would follow the person long after it mattered.
    @Test
    void banner_finished_isShownOnTranslatingOnly() {
        final BannerView banner =
                RunInputs.in(RunState.COMPLETED).decided(142).view().banner();

        assertThat(banner).isNotNull();
        assertThat(banner.translatingOnly()).isTrue();
    }

    // IF a refused run read as a failed translation, THEN a refusal in place would offer the failure's details.
    @Test
    void banner_failedRefusedWithValidation_isToldInPlaceWithTheRefusal() {
        final RunStateView view = RunInputs.in(RunState.FAILED)
                .notice(new RunNotice.Refused(AppError.of(ErrorCode.validation, "Refused", "The brief has no target.")))
                .view();

        assertThat(view.row()).isEqualTo(RunRow.CANNOT_START);
        assertThat(view.banner()).isNotNull();
        assertThat(view.banner().title().in(ENGLISH)).isEqualTo("The run was refused");
        assertThat(view.banner().text()).isNotNull();
        assertThat(view.banner().text().in(ENGLISH)).isEqualTo("The brief has no target.");
    }

    // IF the banner showed the failure's message as built, THEN a Ukrainian display would show an English line.
    @Test
    void banner_providerErrorUnderUkrainian_isWordedFromTheCatalogue() {
        final Messages ukrainian = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));
        final RunStateView view = RunInputs.in(RunState.IDLE)
                .notice(new RunNotice.ProviderError(AppError.of(ErrorCode.auth, "Rejected", "The key was refused.")))
                .view();

        assertThat(view.banner()).isNotNull();
        assertThat(view.banner().text()).isNotNull();
        assertThat(view.banner().text().in(ukrainian))
                .isEqualTo("Ключ не прийнято. Перевірте його в Налаштуваннях › Постачальники.");
    }

    // IF Start waited silently for the names pass, THEN the person would not know what holds it.
    @Test
    void start_namesPassRunning_isOffWithWaitUntilTheNamesPassEnds() {
        final RunAction start = RunInputs.in(RunState.IDLE)
                .decided(0)
                .heldBy(ActivityKind.NAMES_PASS)
                .view()
                .toolbar()
                .getFirst();

        assertThat(start.enabled()).isFalse();
        assertThat(start.tip().in(ENGLISH)).isEqualTo("Wait until the names pass ends");
    }

    @Test
    void start_noModel_tipNamesWhatIsMissing() {
        final RunAction start = RunInputs.in(RunState.IDLE)
                .model(RunInputs.NO_MODEL)
                .view()
                .toolbar()
                .getFirst();

        assertThat(start.tip().in(ENGLISH))
                .isEqualTo("Choose an AI model in Settings › Providers, then start the translation.");
    }

    @Test
    void skip_oneSegmentInFlight_readsSkipThisSegment() {
        final BannerView banner = RunInputs.in(RunState.PAUSED)
                .recovery(RecoveryWaiting.Status.WAITING, ErrorCode.unreachable)
                .inFlight(1)
                .view()
                .banner();

        assertThat(banner).isNotNull();
        assertThat(banner.actions().get(1).label().in(ENGLISH)).isEqualTo("Skip this segment");
    }

    @ParameterizedTest
    @CsvSource({"RUNNING, true", "PAUSING, true", "STOPPING, true", "PAUSED, false", "STOPPED, false"})
    void isAtWork_eachState_locksModelActionsOnlyWhileARequestIsOut(final RunState state, final boolean atWork) {
        assertThat(RunInputs.in(state).view().isAtWork()).isEqualTo(atWork);
    }

    @Test
    void title_underUkrainian_readsInUkrainian() {
        final Messages ukrainian = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));

        assertThat(RunInputs.in(RunState.PAUSED).view().title().in(ukrainian)).isEqualTo("Пауза на 51 %");
    }
}
