package ua.bookloom.ui.control;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SegmentedButton;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepEntry;
import ua.bookloom.ui.state.WaitingCall;

/**
 * The progress dialog's right side: the selected step in the Simple view (one sentence, what it read and what it gave
 * back, in plain words) or the Detailed view, which adds the call line, its clock against the limit, its tokens, and
 * the parsed prompt and reply. Nothing here logs: it is redrawn whenever the work moves on.
 */
final class ProgressStepDetail extends VBox {

    private static final double SPACING = 8;
    private static final String NONE = "none";
    private static final String PARAGRAPHS = "\n\n";
    private static final double MILLIS_PER_SECOND = 1000.0;

    private final ProgressDialogModel model;
    private final Messages messages;
    private final ToggleButton simple;
    private final ToggleButton detailed;
    private final Label say = new Label();
    private final TextBody input = new TextBody();
    private final TextBody output = new TextBody();
    private final Label call = new Label();
    private final FigureLabel clock = new FigureLabel();
    private final Label tokens = new Label();
    private final Label notKept = new Label();
    private final PromptView prompt;
    private final ReplyView reply;
    private @Nullable Instant lastTick;

    ProgressStepDetail(final ProgressDialogModel model, final Messages messages) {
        super(SPACING);
        this.model = Objects.requireNonNull(model, "model");
        this.messages = Objects.requireNonNull(messages, "messages");
        getStyleClass().add("progress-detail");
        setId("progress-detail");
        simple = toggle("progress-mode-simple", MessageKey.PROGRESS_VIEW_SIMPLE, MessageKey.PROGRESS_VIEW_SIMPLE_TIP);
        detailed = toggle(
                "progress-mode-detailed", MessageKey.PROGRESS_VIEW_DETAILED, MessageKey.PROGRESS_VIEW_DETAILED_TIP);
        final SegmentedButton mode = modes();
        prompt = new PromptView("progress-prompt", messages);
        reply = new ReplyView("progress-reply", messages);
        name();
        layOut(mode);
        wireMode(mode.getToggleGroup());
        model.selected().addListener((observed, was, now) -> refresh());
        model.activity().addListener((observed, was, now) -> refresh());
        refresh();
    }

    private void layOut(final SegmentedButton mode) {
        getChildren()
                .addAll(
                        mode,
                        say,
                        heading(MessageKey.PROGRESS_INPUT),
                        input,
                        heading(MessageKey.PROGRESS_OUTPUT),
                        output,
                        call,
                        clock,
                        tokens,
                        notKept,
                        prompt,
                        reply);
    }

    private SegmentedButton modes() {
        final SegmentedButton mode = new SegmentedButton(simple, detailed);
        mode.setId("progress-mode");
        mode.setToggleGroup(new ToggleGroup());
        return mode;
    }

    private void name() {
        say.setId("progress-say");
        say.setWrapText(true);
        input.setId("progress-input");
        output.setId("progress-output");
        call.setId("progress-call");
        clock.setId("progress-clock");
        tokens.setId("progress-tokens");
        notKept.setId("progress-not-kept");
        notKept.setText(messages.get(MessageKey.PROGRESS_DETAIL_NOT_KEPT));
        notKept.setWrapText(true);
    }

    private ToggleButton toggle(final String id, final MessageKey label, final MessageKey tip) {
        final ToggleButton button = Tips.install(messages, new ToggleButton(messages.get(label)), tip);
        button.setId(id);
        return button;
    }

    private Label heading(final MessageKey key) {
        final Label label = new Label(messages.get(key));
        label.getStyleClass().add("progress-heading");
        return label;
    }

    // A segmented control lets its chosen button be pressed off; the view always has one of the two.
    private void wireMode(final ToggleGroup group) {
        group.selectToggle(model.mode().get() == ProgressDialogModel.Mode.DETAILED ? detailed : simple);
        group.selectedToggleProperty().addListener((observed, was, now) -> {
            if (now == null) {
                group.selectToggle(was);
                return;
            }
            model.mode()
                    .set(detailed.equals(now) ? ProgressDialogModel.Mode.DETAILED : ProgressDialogModel.Mode.SIMPLE);
        });
        model.mode().addListener((observed, was, now) -> {
            group.selectToggle(now == ProgressDialogModel.Mode.DETAILED ? detailed : simple);
            refresh();
        });
    }

    private void refresh() {
        final StepEntry step = model.selected().get();
        final CallSnapshot made = step == null ? null : model.callOf(step);
        say.setText(step == null ? "" : StepWords.sentence(messages, step));
        input.setText(orNothing(step == null ? null : inputOf(step, made)));
        output.setText(orNothing(step == null ? null : outputOf(step, made)));
        final boolean technical = model.mode().get() == ProgressDialogModel.Mode.DETAILED;
        showCall(technical, made);
        show(notKept, technical && step != null && model.isPromptDropped(step));
        if (technical && made != null) {
            prompt.show(made.sections(), made.sent(), Map.of());
            reply.show(made.reply(), made.items(), locators(made));
        } else {
            prompt.show(List.of());
            reply.show(null, List.of(), Map.of());
        }
        show(prompt, technical && made != null);
        show(reply, technical && made != null && made.reply() != null);
    }

    private void showCall(final boolean technical, final @Nullable CallSnapshot made) {
        show(call, technical);
        show(clock, technical && made != null);
        show(tokens, technical && made != null && made.usage() != null);
        if (made == null) {
            call.setText(messages.get(MessageKey.PROGRESS_DETAIL_NO_CALL));
            return;
        }
        call.setText(messages.get(
                MessageKey.PROGRESS_DETAIL_CALL,
                made.callId(),
                messages.get(MessageKey.RUN_CALL_KIND, WaitingCall.tokenOf(made.kind())),
                made.segments().size(),
                made.attempt(),
                made.maxAttempts()));
        final Instant at = lastTick;
        tick(at == null ? made.startedAt().plus(made.elapsed()) : at);
        final TokenUsage usage = made.usage();
        if (usage != null) {
            tokens.setText(messages.get(
                    MessageKey.PROGRESS_DETAIL_TOKENS, count(usage.prompt()), count(usage.completion()), speed(usage)));
        }
    }

    /**
     * Moves the selected call's clock; a call still waiting counts from when its attempt went out.
     *
     * @param now the moment the clock reads
     */
    void tick(final Instant now) {
        lastTick = Objects.requireNonNull(now, "now");
        final StepEntry step = model.selected().get();
        final CallSnapshot made = step == null ? null : model.callOf(step);
        if (made == null) {
            return;
        }
        final boolean waiting = made.state() == CallState.WAITING;
        final Duration took = waiting ? Duration.between(made.startedAt(), now) : made.elapsed();
        final Duration limit = made.timeout();
        clock.setText(messages.get(
                MessageKey.PROGRESS_DETAIL_CLOCK,
                waiting ? "waiting" : "other",
                DurationText.clock((int) Math.max(0, took.toSeconds())),
                limit == null ? NONE : DurationText.clock((int) limit.toSeconds())));
    }

    private static @Nullable String inputOf(final StepEntry step, final @Nullable CallSnapshot made) {
        final StepEntry.StepText text = step.text();
        if (text != null && text.before() != null) {
            return text.before();
        }
        return made == null || made.segments().isEmpty()
                ? null
                : made.segments().stream().map(CallSegment::displaySource).collect(Collectors.joining(PARAGRAPHS));
    }

    private static @Nullable String outputOf(final StepEntry step, final @Nullable CallSnapshot made) {
        final StepEntry.StepText text = step.text();
        if (text != null && text.after() != null) {
            return text.after();
        }
        if (made == null) {
            return null;
        }
        final String targets = made.items().stream()
                .map(CallItem::target)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(PARAGRAPHS));
        return targets.isEmpty() ? made.reply() : targets;
    }

    private String orNothing(final @Nullable String text) {
        return text == null || text.isBlank() ? messages.get(MessageKey.PROGRESS_NOTHING) : text;
    }

    private static Map<String, String> locators(final CallSnapshot made) {
        return made.segments().stream()
                .collect(Collectors.toMap(CallSegment::id, CallSegment::locator, (first, second) -> first));
    }

    private static String count(final @Nullable Integer value) {
        return value == null ? "—" : Integer.toString(value);
    }

    private static String speed(final TokenUsage usage) {
        final Integer written = usage.completion();
        final Duration generation = usage.generation();
        if (written == null || generation == null || generation.isZero()) {
            return NONE;
        }
        return String.format(Locale.ROOT, "%.1f", written * MILLIS_PER_SECOND / generation.toMillis());
    }

    private static void show(final Node node, final boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
