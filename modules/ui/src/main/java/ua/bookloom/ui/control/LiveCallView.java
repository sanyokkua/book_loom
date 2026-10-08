package ua.bookloom.ui.control;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.SegmentLive;
import ua.bookloom.ui.state.WaitingCall;

/**
 * One block of the live panel: a model call as it was actually sent. Its header says what the call is, where it stands
 * and how long it has taken; its body lists every segment the call carried with the target the run has for it and
 * what became of it, then the reply as received and the filled parts of the prompt in the order they were sent.
 *
 * <p>The header is redrawn on every published change, so a waiting call's clock moves; the body is rebuilt only when
 * the call or its segments' state changed. Showing logs the call's id and state only, never a text.
 */
@Slf4j
public final class LiveCallView extends VBox {

    private static final double SPACING = 8;
    private static final double SEGMENTS_HEIGHT = 220;
    private static final long SECONDS_CAP = Integer.MAX_VALUE;

    private final Messages messages;
    private final ObservableValue<String> sourceName;
    private final ObservableValue<String> targetName;
    private final Label label = new Label();
    private final Label kind = chip();
    private final Label state = chip();
    private final Label attempt = new Label();
    private final Label clock = new Label();
    private final Label timeout = new Label();
    private final Label tokens = new Label();
    private final Label caption = new Label();
    private final VBox rows = new VBox(SPACING);
    private final ReplyPane reply;
    private final PromptContextPane prompt;
    private @Nullable CallSnapshot shownCall;
    private Map<String, SegmentLive> shownSegments = Map.of();

    /**
     * Builds a hidden block.
     *
     * @param id the block's node id; its parts are named {@code <id>-title}, {@code -state}, {@code -clock},
     *     {@code -segments}, {@code -reply} and {@code -prompt}
     * @param title the heading of the block, which the caller may reword as the run ends
     * @param sourceName the heading of every source pane, already translated
     * @param targetName the heading of every target pane, already translated
     * @param messages the catalogue the block is worded from
     */
    public LiveCallView(
            final String id,
            final ObservableValue<String> title,
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        Objects.requireNonNull(id, "id");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.reply = new ReplyPane(id + "-reply", messages);
        this.prompt = new PromptContextPane(id + "-prompt", messages);
        setId(id);
        getStyleClass().add("live-call");
        setPadding(new Insets(SPACING));
        getChildren().addAll(header(id, title), caption, segmentsArea(id), reply, prompt);
        show(null, LiveCalls.EMPTY);
    }

    /**
     * Keeps the reply and the prompt sections open or closed as the person last left them this session.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberSectionsIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        reply.rememberIn(memory);
        prompt.rememberIn(memory);
    }

    /**
     * Shows a call, or hides the block when there is none.
     *
     * @param call the call, or {@code null} to hide the block
     * @param context the published calls, which hold the clock and the segments' targets
     */
    public void show(final @Nullable CallSnapshot call, final LiveCalls context) {
        Objects.requireNonNull(context, "context");
        setVisible(call != null);
        setManaged(call != null);
        if (call == null) {
            shownCall = null;
            return;
        }
        showHeader(call, context.asOf());
        final Map<String, SegmentLive> live = liveOf(call, context);
        if (!call.equals(shownCall) || !live.equals(shownSegments)) {
            log.debug(
                    "live call {} shown {} with {} segments",
                    call.callId(),
                    call.state(),
                    call.segments().size());
            shownCall = call;
            shownSegments = live;
            showBody(call, live);
        }
    }

    private FlowPane header(final String id, final ObservableValue<String> title) {
        final Label heading = new Label();
        heading.textProperty().bind(title);
        heading.getStyleClass().add("live-call-title");
        heading.setId(id + "-title");
        label.getStyleClass().add("muted");
        label.setId(id + "-label");
        kind.setId(id + "-kind");
        kind.getStyleClass().add("chip-neutral");
        state.setId(id + "-state");
        attempt.setId(id + "-attempt");
        clock.setId(id + "-clock");
        timeout.setId(id + "-timeout");
        tokens.setId(id + "-tokens");
        for (final Label stat : List.of(attempt, clock, timeout, tokens)) {
            stat.getStyleClass().add("muted");
        }
        final FlowPane header =
                new FlowPane(SPACING, SPACING, heading, label, kind, state, attempt, clock, timeout, tokens);
        header.setId(id + "-header");
        return header;
    }

    private ScrollPane segmentsArea(final String id) {
        caption.getStyleClass().add("stat-caption");
        caption.setId(id + "-caption");
        final ScrollPane area = new ScrollPane(rows);
        area.setId(id + "-segments");
        area.setFitToWidth(true);
        area.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        area.getStyleClass().add("edge-to-edge");
        area.setPrefHeight(SEGMENTS_HEIGHT);
        area.setMinHeight(SEGMENTS_HEIGHT);
        area.setMaxHeight(SEGMENTS_HEIGHT);
        return area;
    }

    private void showHeader(final CallSnapshot call, final @Nullable Instant asOf) {
        label.setText(call.label());
        kind.setText(messages.get(MessageKey.RUN_CALL_KIND, WaitingCall.tokenOf(call.kind())));
        state.setText(
                messages.get(MessageKey.LIVE_CALL_STATE, call.state().name().toLowerCase(Locale.ROOT)));
        role(state, stateRole(call.state()));
        attempt.setText(messages.get(
                MessageKey.LIVE_CALL_ATTEMPT, String.valueOf(call.attempt()), String.valueOf(call.maxAttempts())));
        clock.setText(clockText(call, asOf));
        set(
                timeout,
                call.timeout() == null ? null : messages.get(MessageKey.LIVE_CALL_TIMEOUT, clockOf(call.timeout())));
        set(tokens, call.usage() == null ? null : tokensText(call.usage()));
    }

    private String clockText(final CallSnapshot call, final @Nullable Instant asOf) {
        if (call.state() == CallState.WAITING) {
            final Duration waited = asOf == null ? Duration.ZERO : Duration.between(call.startedAt(), asOf);
            return messages.get(MessageKey.LIVE_CALL_WAITED, clockOf(waited));
        }
        return messages.get(MessageKey.LIVE_CALL_TOOK, clockOf(call.elapsed()));
    }

    private static String clockOf(final Duration length) {
        return DurationText.clock((int) Math.min(Math.max(length.toSeconds(), 0), SECONDS_CAP));
    }

    private String tokensText(final TokenUsage usage) {
        final Integer in = usage.prompt();
        final Integer out = usage.completion();
        return messages.get(
                MessageKey.LIVE_CALL_TOKENS,
                in == null ? "none" : in.toString(),
                out == null ? "none" : out.toString());
    }

    private void showBody(final CallSnapshot call, final Map<String, SegmentLive> live) {
        caption.setText(
                call.segments().isEmpty()
                        ? messages.get(MessageKey.LIVE_CALL_NO_SEGMENTS)
                        : messages.get(
                                MessageKey.LIVE_CALL_SEGMENTS, call.segments().size()));
        final String id = getId();
        final List<CallSegmentRow> built = new java.util.ArrayList<>();
        for (int index = 0; index < call.segments().size(); index++) {
            final CallSegment segment = call.segments().get(index);
            built.add(new CallSegmentRow(
                    id + "-segment-" + (index + 1),
                    segment,
                    live.get(segment.id()),
                    notesOf(call, segment),
                    call.state(),
                    sourceName,
                    targetName,
                    messages));
        }
        rows.getChildren().setAll(built);
        reply.show(call.reply());
        prompt.show(call.sections(), call.sent());
    }

    private static List<SegmentOutcomeNote> notesOf(final CallSnapshot call, final CallSegment segment) {
        return call.outcomes().stream()
                .filter(note -> note.segmentId().equals(segment.id()))
                .toList();
    }

    private static Map<String, SegmentLive> liveOf(final CallSnapshot call, final LiveCalls context) {
        return call.segments().stream()
                .filter(segment -> context.segments().containsKey(segment.id()))
                .collect(Collectors.toMap(
                        CallSegment::id, segment -> context.segments().get(segment.id()), (a, b) -> a));
    }

    private static String stateRole(final CallState call) {
        return switch (call) {
            case WAITING -> "chip-warn";
            case ANSWERED -> "chip-ok";
            case FAILED -> "chip-err";
            case CANCELLED -> "chip-neutral";
        };
    }

    private static void role(final Label chip, final String role) {
        chip.getStyleClass().removeAll("chip-warn", "chip-ok", "chip-err", "chip-neutral");
        chip.getStyleClass().add(role);
    }

    private static Label chip() {
        final Label chip = new Label();
        chip.getStyleClass().add("chip");
        return chip;
    }

    private static void set(final Label stat, final @Nullable String text) {
        stat.setText(text == null ? "" : text);
        stat.setVisible(text != null);
        stat.setManaged(text != null);
    }
}
