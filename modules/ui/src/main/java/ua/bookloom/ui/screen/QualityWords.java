package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ModelBatchSize;

/**
 * The words that say what a quality setting does in fixing calls, proofreading and extra model calls, so Generation's
 * table and the Book Brief's hint can never describe the same dial differently.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class QualityWords {

    private final Messages messages;
    private final DialProfiles profiles;
    private final ModelBatchSize batchSize;

    DialProfile profile(final QualityDial dial) {
        return profiles.of(Objects.requireNonNull(dial, "dial"));
    }

    String fixing(final DialProfile profile) {
        return messages.get(
                profile.repair() == RepairMode.ONE
                        ? MessageKey.SETTINGS_GENERATION_QUALITY_REPAIR_ONE
                        : MessageKey.SETTINGS_GENERATION_QUALITY_REPAIR_MARKS_ONLY);
    }

    String proofread(final DialProfile profile) {
        return messages.get(
                switch (profile.proofreadCoverage()) {
                    case NONE -> MessageKey.SETTINGS_GENERATION_QUALITY_PROOFREAD_NONE;
                    case MARKED_AND_DOUBTFUL -> MessageKey.SETTINGS_GENERATION_QUALITY_PROOFREAD_MARKED;
                    case MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH ->
                        MessageKey.SETTINGS_GENERATION_QUALITY_PROOFREAD_WHOLE;
                });
    }

    String extra(final DialProfile profile) {
        final ProofreadCoverage coverage = profile.proofreadCoverage();
        return switch (coverage) {
            case NONE -> messages.get(MessageKey.SETTINGS_GENERATION_QUALITY_EXTRA_NONE);
            case MARKED_AND_DOUBTFUL -> messages.get(MessageKey.SETTINGS_GENERATION_QUALITY_EXTRA_MARKED);
            case MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH ->
                messages.get(MessageKey.SETTINGS_GENERATION_QUALITY_EXTRA_WHOLE, batchSize.current());
        };
    }

    String hint(final QualityDial dial) {
        final DialProfile profile = profile(dial);
        return messages.get(
                MessageKey.BRIEF_QUALITY_HINT,
                dial.name().toLowerCase(Locale.ROOT),
                fixing(profile),
                proofread(profile),
                extra(profile));
    }
}
