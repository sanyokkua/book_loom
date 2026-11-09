package ua.bookloom.pipeline.context;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.project.ChapterBoundaries;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** An invented ru→uk book and the services the context-builder tests build its run frame with. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FrameFixtures {

    static final String NARRATION = "Утром туман лежал над рекой, и лодки стояли у берега.";
    static final String DIALOGUE = "— Подожди меня, — сказала она и пошла к воротам.";
    static final String MARKED = "В ⟦g0⟧тот⟦g1⟧ день было 1881 шагов до моста.";

    static final BookRules BOOK_RULES = new BookRules(LanguageRules.bundled());
    static final PromptTemplates TEMPLATES = new PromptTemplates();
    static final RunFrames RUN_FRAMES = new RunFrames(TEMPLATES, BOOK_RULES);
    static final ContextBuilder BUILDER = new ContextBuilder(TEMPLATES);

    static Document book(final String... paragraphs) {
        return IndexTestBooks.document(IndexTestBooks.unit("ch1", UnitRole.BODY, paragraphs));
    }

    static BookBrief brief(final String source, final String target) {
        return BookBrief.defaults(source).withLanguages(source, target);
    }

    static BookMap emptyMap(final BookBrief brief) {
        return BookMap.of(List.of(), List.of(), brief);
    }

    static RunFrame frame(final Document document, final BookBrief brief, final ModelProfile profile) {
        return frame(document, IndexTestBooks.navigation(document), brief, profile);
    }

    static RunFrame frame(
            final Document document,
            final List<StructureNode> structure,
            final BookBrief brief,
            final ModelProfile profile) {
        final ChapterBoundaries chapters = ChapterBoundaries.of(document, structure, brief.sourceLanguage());
        return Objects.requireNonNull(RUN_FRAMES
                .build(brief, document, BookIndex.of(document), chapters, emptyMap(brief), profile)
                .data());
    }

    static RunFrame frame(final Document document) {
        return frame(document, brief("ru", "uk"), ModelProfile.defaults());
    }

    static Focus focus(final String... maskedSources) {
        final List<FocusItem> items = IntStream.range(0, maskedSources.length)
                .mapToObj(index -> new FocusItem("ch1:" + index, maskedSources[index]))
                .toList();
        return new Focus(items, Map.of());
    }

    static ModelProfile withQuirks(final Set<ModelProfile.Quirk> quirks) {
        final ModelProfile base = ModelProfile.defaults();
        final Set<ModelProfile.Quirk> all = EnumSet.copyOf(base.quirks());
        all.addAll(quirks);
        return new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                all,
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                base.batchSize(),
                base.tier());
    }

    static ModelProfile withReasoning(final ReasoningControl.Kind kind) {
        final ModelProfile base = ModelProfile.defaults();
        return new ModelProfile(
                new ReasoningControl(kind, List.of(), null, null),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                base.batchSize(),
                base.tier());
    }

    static ModelProfile withOverhead(final int templateOverhead) {
        final ModelProfile base = ModelProfile.defaults();
        return new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                templateOverhead,
                base.batchSize(),
                base.tier());
    }
}
