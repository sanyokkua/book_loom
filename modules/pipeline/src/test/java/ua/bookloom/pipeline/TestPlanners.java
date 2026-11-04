package ua.bookloom.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.BookRules;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.RunFrame;
import ua.bookloom.pipeline.context.RunFrames;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** The planner of a run's translate-family calls, built as a run builds it, for tests of the draft step alone. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestPlanners {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();

    /** A planner over a book with no body, the brief's defaults for the languages and the default profile. */
    public static TranslatePlanner planner(@Nullable final String sourceLanguage, final String targetLanguage) {
        return planner(
                BookBrief.defaults(sourceLanguage).withLanguages(sourceLanguage, targetLanguage),
                IndexTestBooks.document(),
                ModelProfile.defaults());
    }

    /** A planner over a book, as a run starting on it builds one, with an empty map at Start. */
    public static TranslatePlanner planner(final BookBrief brief, final Document document, final ModelProfile profile) {
        return planner(TEMPLATES, brief, document, profile);
    }

    /** As {@link #planner(BookBrief, Document, ModelProfile)}, over the given templates. */
    public static TranslatePlanner planner(
            final PromptTemplates templates,
            final BookBrief brief,
            final Document document,
            final ModelProfile profile) {
        final RunFrame frame = frame(templates, brief, document, profile);
        final int window = ModelWindow.of(profile.context(), null);
        return new TranslatePlanner(new ContextBuilder(templates), frame, profile, () -> window, document.units());
    }

    /** A context with no neighbours, an empty map and no memory, for a batch built with nothing around its items. */
    public static TranslateContext empty(final TranslatePlanner planner) {
        return new TranslateContext(
                Window.of(new Focus(List.of(), Map.of())),
                BookMap.of(List.of(), List.of(), planner.frame().brief()),
                List.of(),
                Map.of(),
                List.of());
    }

    /** The run frame of a book, as a run builds it at Start with an empty map. */
    public static RunFrame frame(
            final PromptTemplates templates,
            final BookBrief brief,
            final Document document,
            final ModelProfile profile) {
        return Objects.requireNonNull(
                new RunFrames(templates, new BookRules(templates.languageRules()))
                        .build(
                                brief,
                                document,
                                BookIndex.of(document),
                                BookMap.of(List.of(), List.of(), brief),
                                profile)
                        .data(),
                "run frame");
    }
}
