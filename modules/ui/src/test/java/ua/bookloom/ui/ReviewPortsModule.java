package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import java.util.function.Supplier;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.NamesPass;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewMode;

/** The review-side ports, the launch-time review mode, and the in-memory rolling summaries the live card reads. */
final class ReviewPortsModule extends AbstractModule {

    private final GlossaryService glossary;
    private final LexiconService lexicon;
    private final NamesPass namesPass;
    private final ReviewDesk desk;
    private final Supplier<ReviewMode> reviewMode;
    private final MemorySummaries summaries = new MemorySummaries();

    ReviewPortsModule(
            final GlossaryService glossary,
            final LexiconService lexicon,
            final NamesPass namesPass,
            final ReviewDesk desk,
            final Supplier<ReviewMode> reviewMode) {
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
        bind(ReviewMode.class).toProvider(reviewMode::get);
        bind(SummaryRepository.class).toInstance(summaries);
    }
}
