package ua.bookloom.ui.state;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;

/** A brief with some of its choices replaced and the rest kept, for the view model's setters. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefRebuild {

    /** The choices a rebuild replaces; a {@code null} one is kept as the brief holds it. */
    private record Choices(
            @Nullable Register register,
            @Nullable NamePolicy names,
            @Nullable ForeignPassagePolicy foreign,
            @Nullable FootnotePolicy footnotes,
            @Nullable UnitPolicy units,
            @Nullable Integer balance,
            @Nullable AlsoTranslate alsoTranslate) {}

    static BookBrief of(final BookBrief brief, final Register register) {
        return rebuilt(brief, new Choices(register, null, null, null, null, null, null));
    }

    static BookBrief of(final BookBrief brief, final NamePolicy names) {
        return rebuilt(brief, new Choices(null, names, null, null, null, null, null));
    }

    static BookBrief of(final BookBrief brief, final ForeignPassagePolicy foreign) {
        return rebuilt(brief, new Choices(null, null, foreign, null, null, null, null));
    }

    static BookBrief of(final BookBrief brief, final FootnotePolicy footnotes) {
        return rebuilt(brief, new Choices(null, null, null, footnotes, null, null, null));
    }

    static BookBrief of(final BookBrief brief, final UnitPolicy units) {
        return rebuilt(brief, new Choices(null, null, null, null, units, null, null));
    }

    static BookBrief of(final BookBrief brief, final AlsoTranslate alsoTranslate) {
        return rebuilt(brief, new Choices(null, null, null, null, null, null, alsoTranslate));
    }

    static BookBrief ofBalance(final BookBrief brief, final int balance) {
        return rebuilt(brief, new Choices(null, null, null, null, null, balance, null));
    }

    private static BookBrief rebuilt(final BookBrief brief, final Choices choices) {
        return new BookBrief(
                        brief.sourceLanguage(),
                        brief.targetLanguage(),
                        brief.genre(),
                        choices.register() == null ? brief.register() : choices.register(),
                        brief.voiceEra(),
                        brief.audience(),
                        choices.names() == null ? brief.names() : choices.names(),
                        choices.foreign() == null ? brief.foreignPassages() : choices.foreign(),
                        choices.footnotes() == null ? brief.footnotes() : choices.footnotes(),
                        choices.units() == null ? brief.units() : choices.units(),
                        choices.balance() == null ? brief.balance() : choices.balance(),
                        choices.alsoTranslate() == null ? brief.alsoTranslate() : choices.alsoTranslate(),
                        brief.dial(),
                        brief.narrator())
                .withKeysOf(brief);
    }

    // An edited field is the person's now, so it is no longer marked as the model's guess.
    static BookBrief confirmed(final BookBrief brief, final @Nullable BriefField edited) {
        if (edited == null || !brief.likely().contains(edited)) {
            return brief;
        }
        final Set<BriefField> remaining = new HashSet<>(brief.likely());
        remaining.remove(edited);
        return brief.withLikely(remaining);
    }

    static @Nullable String blankToNull(final String text) {
        Objects.requireNonNull(text, "text");
        return text.isBlank() ? null : text;
    }
}
