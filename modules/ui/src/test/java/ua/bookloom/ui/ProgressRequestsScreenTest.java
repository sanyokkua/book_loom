package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.LiveCalls;

/**
 * A names-pass request in the progress dialog, in the order a pass announces it: the stage, the request, the call
 * waiting with what it asks, then the call answered with what the reply was read as.
 */
class ProgressRequestsScreenTest extends ProgressWorkTestBase {

    private static final Instant SENT = Instant.parse("2026-03-01T09:00:00Z");
    private static final String STAGE = "names-knowledge";
    private static final String WIRE = "{\"model\":\"m\",\"messages\":[]}";
    private static final String RAW = "{\"done\":true}";
    private static final List<String> NAMES = List.of("Faquarl", "Ramuthra");

    private static CallSnapshot waiting() {
        return CallSnapshot.waiting(
                        1,
                        CallKind.NAME_KNOWLEDGE,
                        STAGE,
                        null,
                        List.of(),
                        List.of(),
                        SENT,
                        1,
                        1,
                        Duration.ofMinutes(2))
                .withItems(NAMES.stream().map(CallItem::asked).toList());
    }

    private static CallSnapshot answered() {
        return waiting()
                .answered("{\"items\":[]}", null, Duration.ofSeconds(4))
                .withItems(List.of(CallItem.answered("Faquarl", "male, character"), CallItem.asked("Ramuthra")));
    }

    private static LiveCalls panel(final CallSnapshot call) {
        return new LiveCalls(call, null, Map.of(), null, List.of(), List.of(call), 0, Map.of(1L, "REQUEST:0"));
    }

    private ActivityTracker.Handle sentRequest() throws TimeoutException {
        resizeScene(1024, 800);
        final ActivityTracker.Handle pass = begin(ActivityKind.NAMES_PASS);
        feed(
                pass,
                List.of(
                        new StepsPlanned(StepKind.STAGE, 10, false),
                        new StepChanged(StepKind.STAGE, STAGE, 2, StepState.STARTED, null, null),
                        new StepChanged(StepKind.REQUEST, STAGE, 0, StepState.STARTED, null, null)));
        onFx(() -> pass.calls(panel(waiting())));
        await(this::isDialogShown);
        return pass;
    }

    private String text(final String id) {
        return ThemeTestSupport.onFx(((TextArea) required(id))::getText);
    }

    private List<String> requestLines() {
        return ThemeTestSupport.onFx(() -> required("progress-requests").lookupAll(".list-cell").stream()
                .map(cell -> ((Labeled) cell).getText())
                .filter(Objects::nonNull)
                .toList());
    }

    // IF Simple showed nothing until the model answered, THEN a person would stare at "Nothing yet." for minutes.
    @Test
    void simple_requestWaiting_showsWhatWasAskedAndNoAnswerYet() throws TimeoutException {
        sentRequest();

        await(() -> text("progress-input").contains("Faquarl"));

        assertThat(text("progress-input")).isEqualTo("Faquarl\nRamuthra");
        assertThat(text("progress-output")).isEqualTo("Nothing yet.");
        assertThat(requestLines()).anyMatch(line -> line.startsWith("Request 1 · waiting"));
    }

    @Test
    void simple_requestAnswered_showsTheParsedAnswerNextToWhatWasAsked() throws TimeoutException {
        final ActivityTracker.Handle pass = sentRequest();

        onFx(() -> pass.calls(panel(answered())));

        await(() -> text("progress-output").contains("Faquarl → male, character"));
        assertThat(text("progress-output")).isEqualTo("Faquarl → male, character\nRamuthra → no answer");
        assertThat(requestLines()).anyMatch(line -> line.startsWith("Request 1 · answered · 0:04"));
    }

    // IF the stage row said "no model call" while it listed requests, THEN the dialog would contradict itself.
    @Test
    void detailed_stageWithRequests_neverClaimsItMadeNoCall() throws TimeoutException {
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.ProgressDialogDefaults.class)
                .initialMode()
                .set(ua.bookloom.ui.state.ProgressDialogModel.Mode.DETAILED));
        sentRequest();

        await(() -> ((Labeled) required("progress-call")).getText().startsWith("Call 1"));

        assertThat(((Labeled) required("progress-call")).getText()).doesNotContain("no model call");
        assertThat(required("progress-requests").isVisible()).isTrue();
    }

    // IF a names call kept the generic "What it reads", THEN the person could not tell names from segments.
    @Test
    void simple_namesRequest_headingsNameWhatWasAskedAndWhatTheModelRead() throws TimeoutException {
        sentRequest();

        await(() -> text("progress-input").contains("Faquarl"));

        assertThat(((Labeled) required("progress-input-heading")).getText()).isEqualTo("Names asked about");
        assertThat(((Labeled) required("progress-output-heading")).getText()).isEqualTo("Model's reading");
    }

    // IF the wire JSON showed in Simple, THEN the plain view would stop being plain.
    @Test
    void simple_requestWithWireBody_showsNoWireBlock() throws TimeoutException {
        final ActivityTracker.Handle pass = sentRequest();

        onFx(() -> pass.calls(panel(answered().withRequestBody(WIRE))));
        await(() -> text("progress-output").contains("Faquarl"));

        assertThat(required("progress-wire").isVisible()).isFalse();
    }

    // IF Detailed hid the request until the model answered, THEN a stalled call would show nothing of what was sent.
    @Test
    void detailed_requestWaiting_showsTheRequestAsSentBeforeAnyReply() throws TimeoutException {
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.ProgressDialogDefaults.class)
                .initialMode()
                .set(ua.bookloom.ui.state.ProgressDialogModel.Mode.DETAILED));
        final ActivityTracker.Handle pass = sentRequest();

        onFx(() -> pass.calls(panel(waiting().withRequestBody(WIRE))));
        await(() -> required("progress-wire-request").isVisible());
        onFx(() -> ((TitledPane) required("progress-wire-request")).setExpanded(true));

        await(() -> text("progress-wire-request-body").contains("\"model\": \"m\""));
        assertThat(required("progress-wire-reply").isVisible()).isFalse();
    }

    @Test
    void detailed_requestAnswered_showsTheReplyBodyAsReceivedNextToTheRequest() throws TimeoutException {
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.ProgressDialogDefaults.class)
                .initialMode()
                .set(ua.bookloom.ui.state.ProgressDialogModel.Mode.DETAILED));
        final ActivityTracker.Handle pass = sentRequest();

        onFx(() ->
                pass.calls(panel(answered().withRequestBody(WIRE).answered("ok", null, Duration.ofSeconds(4), RAW))));
        await(() -> required("progress-wire-reply").isVisible());
        onFx(() -> ((TitledPane) required("progress-wire-reply")).setExpanded(true));

        await(() -> text("progress-wire-reply-body").contains("\"done\": true"));
        assertThat(required("progress-wire-request").isVisible()).isTrue();
    }

    // IF a finished stage kept saying "Request 1", THEN the person could not tell how many were still to come.
    @Test
    void requestList_stageEnded_namesTheStagesRequestCount() throws TimeoutException {
        final ActivityTracker.Handle pass = sentRequest();
        feed(
                pass,
                List.of(
                        new StepChanged(StepKind.REQUEST, STAGE, 0, StepState.ENDED, null, StepResult.DONE),
                        new StepChanged(StepKind.STAGE, STAGE, 2, StepState.ENDED, null, StepResult.DONE)));

        await(() -> requestLines().stream().anyMatch(line -> line.startsWith("Request 1 of 1")));
        assertThat(requestLines()).anyMatch(line -> line.startsWith("Request 1 of 1 ·"));
    }

    // IF a request line or the heading above it were cut at a standard size in either language, THEN the person could
    // not read what a request is doing.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({
        "en, 944, 600",
        "en, 1280, 800",
        "en, 1920, 700",
        "uk, 944, 600",
        "uk, 1024, 700",
        "uk, 1280, 800",
        "uk, 1920, 700"
    })
    void requestList_standardSizes_fitsInBothLanguages(final String tag, final double width, final double height)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(tag));
        final ActivityTracker.Handle pass = sentRequest();
        onFx(() -> pass.calls(panel(answered())));
        resizeScene(width, height);
        await(() -> !requestLines().isEmpty());

        FitMatrix.assertFits(scene, new double[] {width, height});
    }
}
