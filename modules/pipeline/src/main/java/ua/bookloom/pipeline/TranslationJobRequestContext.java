package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** Resolves request-derived source-language and source-format details for a translation job. */
final class TranslationJobRequestContext {

    private TranslationJobRequestContext() {}

    static @Nullable String sourceLanguage(final TranslationRequest request, @Nullable final String declaredLanguage) {
        return request.sourceLanguage() == null ? declaredLanguage : request.sourceLanguage();
    }

    static CallFrame callFrame(final TranslationRequest request, @Nullable final String declaredLanguage) {
        return new CallFrame(
                sourceLanguage(request, declaredLanguage),
                request.targetLanguage(),
                StyleSheet.from(BookBrief.defaults(null)),
                ForeignPassagePolicy.KEEP);
    }

    static BookFormat sourceFormat(final TranslationRequest request) {
        final Path name = Objects.requireNonNull(request.source().getFileName(), "source file name");
        return BookFormat.ofFileName(name.toString()).orElse(BookFormat.TXT);
    }
}
