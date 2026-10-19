package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;

/** A brief with some of its choices replaced and the rest kept, for the view model's setters. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefRebuild {

    static BookBrief of(
            final BookBrief brief,
            final @Nullable Register register,
            final @Nullable NamePolicy names,
            final @Nullable ForeignPassagePolicy foreign,
            final @Nullable FootnotePolicy footnotes,
            final @Nullable UnitPolicy units,
            final @Nullable Integer balance,
            final @Nullable AlsoTranslate alsoTranslate) {
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                register == null ? brief.register() : register,
                brief.voiceEra(),
                brief.audience(),
                names == null ? brief.names() : names,
                foreign == null ? brief.foreignPassages() : foreign,
                footnotes == null ? brief.footnotes() : footnotes,
                units == null ? brief.units() : units,
                balance == null ? brief.balance() : balance,
                alsoTranslate == null ? brief.alsoTranslate() : alsoTranslate,
                brief.dial(),
                brief.narrator());
    }
}
