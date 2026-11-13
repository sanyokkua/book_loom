package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.QuoteStyle;

/**
 * The policy keys a target language's file offers the Book Brief (design D6): a row is shown only for what the file
 * sets, so the screen needs no code per language. In each list the first choice is the file's default, which a brief
 * that stores no value follows.
 *
 * @param apostrophes the apostrophe characters offered, default first; empty when the file sets none
 * @param quoteStyles the quotation-mark styles offered, default first; empty when the file sets none
 * @param address the words of the two forms of "you", or null when the file lists no address words
 * @param conventions the conventions the file declares, in its order; empty when it declares none
 */
public record BriefChoices(
        List<String> apostrophes,
        List<QuoteStyle> quoteStyles,
        @Nullable AddressWords address,
        List<DeclaredConvention> conventions) {

    /** What a language with no file, or a file that sets none of the keys, offers. */
    public static final BriefChoices NONE = new BriefChoices(List.of(), List.of(), null, List.of());

    // Polite to strangers, familiar inside a family: the usage of every language the files list address words for.
    private static final Address DEFAULT_ADDRESS = new Address(AddressForm.FORMAL, AddressForm.INFORMAL);

    /** Copies the lists. */
    public BriefChoices {
        apostrophes = List.copyOf(Objects.requireNonNull(apostrophes, "apostrophes"));
        quoteStyles = List.copyOf(Objects.requireNonNull(quoteStyles, "quoteStyles"));
        conventions = List.copyOf(Objects.requireNonNull(conventions, "conventions"));
    }

    /**
     * The words a person reads on the two address choices.
     *
     * @param informal the familiar form as the file writes it first, such as {@code ти}; not blank
     * @param formal the polite form as the file writes it first, such as {@code Ви}; not blank
     */
    public record AddressWords(String informal, String formal) {

        /** Rejects a missing word. */
        public AddressWords {
            Objects.requireNonNull(informal, "informal");
            Objects.requireNonNull(formal, "formal");
        }
    }

    /**
     * One convention a language declares, such as whether a hard g in a foreign name is written {@code г} or {@code ґ}.
     *
     * @param key the convention's name in the file, such as {@code hardG}; the brief stores the answer under it
     * @param choices the values offered, at least two
     * @param fallback the file's default, one of {@code choices}
     */
    public record DeclaredConvention(String key, List<String> choices, String fallback) {

        /** Rejects a convention with nothing to choose or a default it does not offer. */
        public DeclaredConvention {
            Objects.requireNonNull(key, "key");
            choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
            Objects.requireNonNull(fallback, "fallback");
            if (choices.size() < 2 || !choices.contains(fallback)) {
                throw new IllegalArgumentException("convention " + key
                        + " needs two choices and a default among them, but had " + choices + " and " + fallback);
            }
        }
    }

    /**
     * Whether the language offers no row at all.
     *
     * @return {@code true} if no key is set, {@code false} otherwise
     */
    public boolean isEmpty() {
        return apostrophes.isEmpty() && quoteStyles.isEmpty() && address == null && conventions.isEmpty();
    }

    /**
     * The apostrophe a brief that stores none follows.
     *
     * @return the file's default, or null when the file offers no apostrophe
     */
    public @Nullable String defaultApostrophe() {
        return apostrophes.isEmpty() ? null : apostrophes.getFirst();
    }

    /**
     * The quotation marks a brief that stores none follows.
     *
     * @return the file's default, or null when the file offers no quotation marks
     */
    public @Nullable QuoteStyle defaultQuoteStyle() {
        return quoteStyles.isEmpty() ? null : quoteStyles.getFirst();
    }

    /**
     * The form of address a brief that stores none follows.
     *
     * @return polite for strangers and familiar for family, or null when the file lists no address words
     */
    public @Nullable Address defaultAddress() {
        return address == null ? null : DEFAULT_ADDRESS;
    }
}
