package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;

/** The review-side ports and the launch-time review mode, which no test of the window scripts yet. */
final class ReviewPortsModule extends AbstractModule {

    private final GlossaryService glossary;
    private final LexiconService lexicon;
    private final NamesPass namesPass;
    private final ReviewDesk desk;
    private final ReviewMode reviewMode;

    ReviewPortsModule(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final NamesPass namesPass,
            final ReviewDesk desk,
            final ReviewMode reviewMode) {
        this.glossary = glossary;
        this.lexicon = lexicon;
        this.namesPass = namesPass;
        this.desk = desk;
        this.reviewMode = reviewMode;
    }

    @Override
    protected void configure() {
        bind(GlossaryService.class).toInstance(glossary);
        bind(LexiconService.class).toInstance(lexicon);
        bind(NamesPass.class).toInstance(namesPass);
        bind(ReviewDesk.class).toInstance(desk);
        bind(ReviewMode.class).toInstance(reviewMode);
    }
}
