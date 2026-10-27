package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedSetupAssistant;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A proposal whose answer needs the person's decision: when the work answers with an offer the activity stays
 * registered with it, and when it answers with none (failure, stop) the activity ends as any other.
 */
class SetupHelperDecisionTest extends ExportViewModelTestBase {

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<Result<String>> answered = new AtomicReference<>();

    private SetupHelper helper() {
        return new SetupHelper(
                new ScriptedSetupAssistant(),
                models,
                settings,
                new DirectExecutor(),
                activities,
                capabilities,
                new HiddenWork(activities, new RecordingToasts(), new Messages((LocaleProvider) () -> Locale.ENGLISH)));
    }

    private ResultOffer offer() {
        return new ResultOffer(MessageKey.BRIEF_APPLY, true, () -> events.add("apply"), () -> events.add("discard"));
    }

    private Optional<SetupHelper.Refusal> start(final Result<String> work, final Optional<ResultOffer> offer) {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        final Optional<SetupHelper.Refusal> refusal =
                onFx(() -> helper().runForDecision(ActivityKind.SUGGEST_STYLE, (model, progress) -> work, result -> {
                    answered.set(result);
                    return offer;
                }));
        WaitForAsyncUtils.waitForFxEvents();
        return refusal;
    }

    // IF the helper ended the activity on an answer that has an offer, THEN the dialog would close under the person.
    @Test
    void runForDecision_answerWithAnOffer_keepsTheActivityRegisteredWithIt() {
        final Optional<SetupHelper.Refusal> refusal = start(Result.ok("answer"), Optional.of(offer()));

        assertThat(refusal).isEmpty();
        assertThat(java.util.Objects.requireNonNull(answered.get()).data()).isEqualTo("answer");
        assertThat(onFx(() -> activities.running().size())).isEqualTo(1);
        assertThat(onFx(() ->
                        activities.running().getFirst().progress().outcome().offer()))
                .isNotNull();
        assertThat(events).isEmpty();
    }

    @Test
    void runForDecision_answerWithNoOffer_endsTheActivity() {
        start(Result.err(AppError.of(ErrorCode.validation, "Unreadable", "It could not be read.")), Optional.empty());

        assertThat(java.util.Objects.requireNonNull(answered.get()).isErr()).isTrue();
        assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
    }

    // IF Esc on a held answer interrupted the finished work, THEN the person would see a cancelled call they never
    // made.
    @Test
    void runForDecision_stopWhileOfferHeld_discardsOnceAndEndsTheActivity() {
        start(Result.ok("answer"), Optional.of(offer()));
        final long id = onFx(() -> activities.running().getFirst().id());

        onFx(() -> {
            activities.stop(id);
            return null;
        });

        assertThat(events).containsExactly("discard");
        assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
    }

    @Test
    void runForDecision_noModelChosen_isRefusedAndStartsNothing() {
        final Optional<SetupHelper.Refusal> refusal = onFx(() -> helper().runForDecision(
                        ActivityKind.SUGGEST_STYLE,
                        (model, progress) -> Result.ok("x"),
                        result -> Optional.of(offer())));

        assertThat(refusal).contains(SetupHelper.Refusal.NO_MODEL);
        assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
    }
}
