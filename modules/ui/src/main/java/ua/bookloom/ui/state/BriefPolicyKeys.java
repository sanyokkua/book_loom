package ua.bookloom.ui.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.project.Address;
import ua.bookloom.api.project.AddressForm;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.QuoteStyle;

/**
 * The policy keys the target language's file sets (design D6): which choices the Translation policies card offers,
 * worked out again whenever the brief changes, and the person's picks, each of which confirms its field. FX thread
 * only.
 */
@Slf4j
public final class BriefPolicyKeys {

    /** Hands an edit to the view model, which applies it, clears the field from the likely set and saves it. */
    @FunctionalInterface
    interface Edit {
        void apply(String field, String value, BriefField edited, UnaryOperator<BookBrief> edit);
    }

    private final LanguageSupport languages;
    private final Edit edit;
    private final ReadOnlyObjectWrapper<BriefChoices> choices = new ReadOnlyObjectWrapper<>(BriefChoices.NONE);

    BriefPolicyKeys(final LanguageSupport languages, final Edit edit) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.edit = Objects.requireNonNull(edit, "edit");
    }

    /**
     * What the open book's target language offers.
     *
     * @return a read-only property; {@link BriefChoices#NONE} while no target is chosen
     */
    public ReadOnlyObjectProperty<BriefChoices> choices() {
        return choices.getReadOnlyProperty();
    }

    void derive(final @Nullable BookBrief brief) {
        final String target = brief == null ? null : brief.targetLanguage();
        final BriefChoices offered = target == null ? BriefChoices.NONE : languages.briefChoices(target);
        if (!offered.equals(choices.get())) {
            log.debug(
                    "target {} offers apostrophes {}, quotes {}, address {}, conventions {}",
                    target,
                    offered.apostrophes(),
                    offered.quoteStyles().size(),
                    offered.address() != null,
                    offered.conventions().size());
        }
        choices.set(offered);
    }

    /**
     * Sets the apostrophe of the translated book.
     *
     * @param apostrophe one of the offered characters
     */
    public void setApostrophe(final String apostrophe) {
        Objects.requireNonNull(apostrophe, "apostrophe");
        edit.apply("apostrophe", apostrophe, BriefField.APOSTROPHE, brief -> brief.withApostrophe(apostrophe));
    }

    /**
     * Sets the quotation marks of the translated book.
     *
     * @param style one of the offered styles
     */
    public void setQuoteStyle(final QuoteStyle style) {
        Objects.requireNonNull(style, "style");
        edit.apply("quotation marks", style.toString(), BriefField.QUOTE_STYLE, brief -> brief.withQuoteStyle(style));
    }

    /**
     * Sets the default address between strangers; the family's is kept, or the file's default when none is stored.
     *
     * @param form the form strangers use
     */
    public void setStrangersAddress(final AddressForm form) {
        Objects.requireNonNull(form, "form");
        changeAddress("strangers", form, address -> new Address(form, address.family()));
    }

    /**
     * Sets the default address inside a family; the strangers' is kept, or the file's default when none is stored.
     *
     * @param form the form a family uses
     */
    public void setFamilyAddress(final AddressForm form) {
        Objects.requireNonNull(form, "form");
        changeAddress("family", form, address -> new Address(address.strangers(), form));
    }

    private void changeAddress(final String who, final AddressForm form, final UnaryOperator<Address> change) {
        final Address fallback = choices.get().defaultAddress();
        if (fallback == null) {
            log.debug("address for {} not changed: the target offers no address choice", who);
            return;
        }
        edit.apply(
                "address for " + who,
                form.name(),
                BriefField.DEFAULT_ADDRESS,
                brief -> brief.withDefaultAddress(
                        change.apply(Objects.requireNonNullElse(brief.defaultAddress(), fallback))));
    }

    /**
     * Sets the answer to one convention the target language declares.
     *
     * @param key the convention's key, such as {@code hardG}
     * @param value one of the convention's choices
     */
    public void setConvention(final String key, final String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        edit.apply("convention " + key, value, BriefField.CONVENTIONS, brief -> {
            final Map<String, String> answers = new HashMap<>(brief.conventions());
            answers.put(key, value);
            return brief.withConventions(answers);
        });
    }
}
