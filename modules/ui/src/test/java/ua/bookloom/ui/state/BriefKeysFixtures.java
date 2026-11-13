package ua.bookloom.ui.state;

import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.QuoteStyle;

/** A brief that declares every policy key and marks fields likely, to prove a change keeps them. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefKeysFixtures {

    static final QuoteStyle QUOTES = new QuoteStyle("«", "»", "„", "“");
    static final Address ADDRESS = new Address(AddressForm.FORMAL, AddressForm.INFORMAL);
    static final Map<String, String> CONVENTIONS = Map.of("hyphenSpacing", "spaced");
    static final Set<BriefField> LIKELY = Set.of(BriefField.GENRE, BriefField.REGISTER, BriefField.NARRATOR);

    static BookBrief withKeys(final BookBrief brief) {
        return brief.withApostrophe("’")
                .withQuoteStyle(QUOTES)
                .withDefaultAddress(ADDRESS)
                .withConventions(CONVENTIONS)
                .withLikely(LIKELY);
    }
}
