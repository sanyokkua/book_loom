package ua.bookloom.ui.control;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
 * <p>The header is redrawn on every published change, so a waiting call's clock moves; its clock, timeout and token
 * figures are {@link FigureLabel}s, so a tick lays out only them. The body is updated only when the call or its
 * segments' state changed, and in place: a row per segment is kept and reused, and only a call with more segments adds
 * rows. The block can take another role ({@link #takeRole}) without being rebuilt. Showing logs nothing, as it runs on
 * every publication; the calls it shows are logged where they are worked out.
 */
public final class LiveCallView extends VBox {

    private static final double SPACING = 8;
    private static final double SEGMENTS_HEIGHT = 220;
    private static final long SECONDS_CAP = Integer.MAX_VALUE;

    private final Messages messages;
    private final ObservableValue<String> sourceName;
    private final ObservableValue<String> targetName;
    private final Label heading = new Label();
    private final Label label = new Label();
    private final Label kind = chip();
    private final Label state = chip();
    private final Label attempt = new Label();
    private final Label clock = new FigureLabel();
    private final Label timeout = new FigureLabel();
    private final Label tokens = new FigureLabel();
    private final Label caption = new Label();
    private final VBox rows = new VBox(SPACING);
    private final List<CallSegmentRow> built = new ArrayList<>();
    private final FlowPane header;
    private final ScrollPane segments;
    private final InputPane input;
    private final Label replyNote = new Label();
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
        this.input = new InputPane(id + "-input", messages);
        this.reply = new ReplyPane(id + "-reply", messages);
        this.prompt = new PromptContextPane(id + "-prompt", messages);
        this.header = header();
        this.segments = segmentsArea();
        getStyleClass().add("live-call");
        setPadding(new Insets(SPACING));
        replyNote.getStyleClass().addAll("live-body", "live-placeholder");
        replyNote.setWrapText(true);
        replyNote.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(header, caption, segments, input, replyNote, reply, prompt);
        name(id);
        heading.textProperty().bind(Objects.requireNonNull(title, "title"));
        show(null, LiveCalls.EMPTY);
    }

    /**
     * Gives the block another role — the current call's or the previous one's — keeping everything it shows: its ids
     * take the new name, its heading the new title, and its reply and prompt sections open or close as the person
     * last left the sections of that role.
     *
     * @param id the block's new node id, named as in the constructor
     * @param title the heading of the new role
     */
    public void takeRole(final String id, final ObservableValue<String> title) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        name(id);
        heading.textProperty().bind(title);
    }

    /**
     * Whether this block shows a call.
     *
     * @param callId the call's id
     * @return {@code true} if the block shows that call, {@code false} if it shows another or none
     */
    public boolean isShowing(final long callId) {
        final CallSnapshot call = shownCall;
        return call != null && call.callId() == callId;
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
            shownCall = call;
            shownSegments = live;
            showBody(call, live);
        }
    }

    private void name(final String id) {
        setId(id);
        heading.setId(id + "-title");
        label.setId(id + "-label");
        kind.setId(id + "-kind");
        state.setId(id + "-state");
        attempt.setId(id + "-attempt");
        clock.setId(id + "-clock");
        timeout.setId(id + "-timeout");
        tokens.setId(id + "-tokens");
        header.setId(id + "-header");
        caption.setId(id + "-caption");
        segments.setId(id + "-segments");
        for (int index = 0; index < built.size(); index++) {
            built.get(index).rename(rowId(index));
        }
        input.rename(id + "-input");
        replyNote.setId(id + "-reply-note");
        reply.rename(id + "-reply");
        prompt.rename(id + "-prompt");
    }

    private String rowId(final int index) {
        return getId() + "-segment-" + (index + 1);
    }

    private FlowPane header() {
        heading.getStyleClass().add("live-call-title");
        label.getStyleClass().add("muted");
        kind.getStyleClass().add("chip-neutral");
        for (final Label stat : List.of(attempt, clock, timeout, tokens)) {
            stat.getStyleClass().add("muted");
        }
        return new FlowPane(SPACING, SPACING, heading, label, kind, state, attempt, clock, timeout, tokens);
    }

    private ScrollPane segmentsArea() {
        caption.getStyleClass().add("stat-caption");
        final ScrollPane area = new ScrollPane(rows);
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
        final boolean aboutNone = call.segments().isEmpty();
        caption.setText(
                aboutNone
                        ? summaryOf(call)
                        : messages.get(
                                MessageKey.LIVE_CALL_SEGMENTS, call.segments().size()));
        segments.setVisible(!aboutNone);
        segments.setManaged(!aboutNone);
        input.show(aboutNone ? call.sent() : null);
        showRows(call, live);
        showReplyNote(call);
        reply.show(call.reply());
        prompt.show(call.sections(), call.sent());
    }

    // What a call about no segment sent and got back, in a line, where the segments' box would stand empty.
    private String summaryOf(final CallSnapshot call) {
        final int sent =
                call.sent().stream().map(message -> message.content().length()).reduce(0, Integer::sum);
        final String received = call.reply();
        return received == null
                ? messages.get(MessageKey.LIVE_CALL_SUMMARY_PENDING, sent)
                : messages.get(MessageKey.LIVE_CALL_SUMMARY, sent, received.length());
    }

    private void showRows(final CallSnapshot call, final Map<String, SegmentLive> live) {
        final int count = call.segments().size();
        while (built.size() < count) {
            final CallSegmentRow row = new CallSegmentRow(rowId(built.size()), sourceName, targetName, messages);
            built.add(row);
            rows.getChildren().add(row);
        }
        if (built.size() > count) {
            built.subList(count, built.size()).clear();
            rows.getChildren().remove(count, rows.getChildren().size());
        }
        for (int index = 0; index < count; index++) {
            final CallSegment segment = call.segments().get(index);
            built.get(index).show(segment, live.get(segment.id()), notesOf(call, segment), call.state());
        }
    }

    // A call that has not answered says what it is doing, so the reply's place is never silently empty.
    private void showReplyNote(final CallSnapshot call) {
        final boolean noteShown = call.reply() == null;
        replyNote.setVisible(noteShown);
        replyNote.setManaged(noteShown);
        if (noteShown) {
            replyNote.setText(messages.get(
                    call.state() == CallState.WAITING
                            ? MessageKey.LIVE_CALL_REPLY_WAITING
                            : MessageKey.LIVE_CALL_REPLY_NONE));
        }
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

    // Changed only when the role changes: a changed class list restyles and re-measures the chip on every tick.
    private static void role(final Label chip, final String role) {
        if (!chip.getStyleClass().contains(role)) {
            chip.getStyleClass().removeAll("chip-warn", "chip-ok", "chip-err", "chip-neutral");
            chip.getStyleClass().add(role);
        }
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
