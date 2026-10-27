package ua.bookloom.pipeline.project;

import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.util.lang.LanguageTags;

/** What a newly imported book starts with: the source language its text suggests and the quality Settings chose. */
// Checkstyle parses source text before Lombok creates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NewBookBrief {

    static BookBrief of(final LanguageEvidence evidence, final SettingsRepository settings) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(settings, "settings");
        return BookBrief.defaults(preselectSource(evidence)).withDial(defaultDial(settings));
    }

    private static QualityDial defaultDial(final SettingsRepository settings) {
        final Result<Optional<QualityDial>> stored = settings.get(AppSetting.DEFAULT_DIAL);
        if (stored.isErr()) {
            log.warn(
                    "default quality unreadable, using Balanced code={}",
                    Objects.requireNonNull(stored.error()).code());
            return QualityDial.BALANCED;
        }
        final QualityDial dial = Objects.requireNonNull(stored.data()).orElse(QualityDial.BALANCED);
        log.debug("default quality for a new book dial={}", dial);
        return dial;
    }

    private static @Nullable String preselectSource(final LanguageEvidence evidence) {
        final String chosen =
                switch (evidence.verdict()) {
                    case MISMATCH -> evidence.contentMajority();
                    case MATCH -> LanguageTags.normalize(evidence.declared()).orElse(null);
                    case ABSENT -> evidence.contentMajority();
                    case UNRECOGNIZED -> null;
                };
        log.debug(
                "source preselection verdict={} declared={} contentMajority={} chosen={}",
                evidence.verdict(),
                evidence.declared(),
                evidence.contentMajority(),
                chosen);
        return chosen;
    }
}
