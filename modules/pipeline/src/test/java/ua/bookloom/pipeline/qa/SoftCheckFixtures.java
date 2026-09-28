package ua.bookloom.pipeline.qa;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;

/** Shared {@link SoftCheckInput} builders for the per-check soft-check test classes. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SoftCheckFixtures {

    /** An input for {@link ScriptCheck} and {@link EchoCheck}, whose logic reads every field but the locked renderings. */
    static SoftCheckInput scriptEcho(
            final String source,
            final String target,
            @Nullable final String sourceLang,
            final String targetLang,
            final NamePolicy namePolicy,
            final ForeignPassagePolicy foreignPolicy,
            @Nullable final String declaredLanguage,
            final List<String> glossaryTerms) {
        return new SoftCheckInput(
                source,
                target,
                sourceLang,
                targetLang,
                foreignPolicy,
                namePolicy,
                declaredLanguage,
                glossaryTerms,
                List.of());
    }

    /** An input for {@link LengthCheck}, which reads only the two display texts and the two language tags. */
    static SoftCheckInput length(
            final String source, final String target, @Nullable final String sourceLang, final String targetLang) {
        return new SoftCheckInput(
                source,
                target,
                sourceLang,
                targetLang,
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of());
    }

    /** An input for {@link RepetitionCheck}, which reads only the target display text. */
    static SoftCheckInput repetition(final String target) {
        return new SoftCheckInput(
                "source",
                target,
                "en",
                "uk",
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of());
    }

    /** An input for {@link RefusalGate}, which reads only the two display texts and the two language tags. */
    static SoftCheckInput refusal(
            final String source, final String target, @Nullable final String sourceLang, final String targetLang) {
        return new SoftCheckInput(
                source,
                target,
                sourceLang,
                targetLang,
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of());
    }

    /** An input for {@link GlossaryCheck}, which reads only the target display text and the locked renderings. */
    static SoftCheckInput glossary(final String target, final List<LockedRendering> lockedRenderings) {
        return new SoftCheckInput(
                "source",
                target,
                "en",
                "uk",
                ForeignPassagePolicy.TRANSLATE,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                lockedRenderings);
    }
}
