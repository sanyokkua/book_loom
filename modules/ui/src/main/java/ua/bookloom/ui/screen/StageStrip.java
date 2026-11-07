package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.ui.control.StageWords;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunStage;
import ua.bookloom.ui.state.StateMirror;

/**
 * The run's three stages on the Translating strip, Prepare · Translate · Proofread, the one in work marked, with its
 * round at Max and its code-only reading at Fast. Shown while a run is at work or paused; the row wraps rather than
 * squeezes in a narrow window. Its listener logs nothing: the stage changes a handful of times a run, and the strip's
 * other parts follow the same rule.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StageStrip {

    private static final double GAP = 6;
    private static final String IN_WORK = "chip-info";
    private static final String NOT_IN_WORK = "chip-neutral";
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final List<JobStage> STAGES = List.of(JobStage.PREP, JobStage.TRANSLATE, JobStage.PROOFREAD);

    static FlowPane build(final StateMirror mirror, final Messages messages) {
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(messages, "messages");
        final List<Label> parts =
                STAGES.stream().map(stage -> part(stage, messages)).toList();
        final FlowPane row = new FlowPane(GAP, GAP / 2);
        row.setId("translating-stages");
        row.getChildren().setAll(parts);
        Tips.install(messages, row, MessageKey.SHELL_RUN_STAGE_TIP);
        final ChangeListener<@Nullable RunStage> onStage = (observed, was, now) -> show(row, parts, now, messages);
        // The row keeps its listener, which the mirror holds weakly, so a replaced screen is not kept alive.
        row.getProperties().put(ChangeListener.class, onStage);
        mirror.live().stage().addListener(new WeakChangeListener<>(onStage));
        show(row, parts, mirror.live().stage().get(), messages);
        return row;
    }

    private static Label part(final JobStage stage, final Messages messages) {
        final Label label = new Label(StageWords.name(messages, stage));
        label.setId("translating-stage-" + stage.name().toLowerCase(Locale.ROOT));
        label.getStyleClass().addAll("chip", NOT_IN_WORK);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static void show(
            final FlowPane row, final List<Label> parts, final @Nullable RunStage stage, final Messages messages) {
        Visibility.show(row, stage != null);
        for (int index = 0; index < STAGES.size(); index++) {
            final JobStage named = STAGES.get(index);
            final Label part = parts.get(index);
            final boolean inWork = stage != null && stage.stage() == named;
            part.setText(
                    stage != null && inWork ? StageWords.inWork(messages, stage) : StageWords.name(messages, named));
            part.getStyleClass().removeAll(IN_WORK, NOT_IN_WORK);
            part.getStyleClass().add(inWork ? IN_WORK : NOT_IN_WORK);
            part.pseudoClassStateChanged(CURRENT, inWork);
        }
    }
}
