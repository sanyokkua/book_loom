package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesPass;

/**
 * The services behind Names &amp; style: the glossary, the recurring terms and the autonomous names pass.
 *
 * @param glossary the glossary service
 * @param lexicon the recurring-terms service
 * @param namesPass the autonomous names pass over both tables
 */
public record NamesServices(GlossaryService glossary, LexiconService lexicon, NamesPass namesPass) {

    /** Rejects a missing service. */
    @Inject
    public NamesServices {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(lexicon, "lexicon");
        Objects.requireNonNull(namesPass, "namesPass");
    }
}
