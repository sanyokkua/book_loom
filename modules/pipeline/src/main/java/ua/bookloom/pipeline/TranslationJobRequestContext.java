package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Resolves request-derived source-language and source-format details for a translation job. */
final class TranslationJobRequestContext {

    private TranslationJobRequestContext() {}

    static @Nullable String sourceLanguage(final TranslationRequest request, @Nullable final String declaredLanguage) {
        return request.sourceLanguage() == null ? declaredLanguage : request.sourceLanguage();
    }

    static BookFormat sourceFormat(final TranslationRequest request) {
        final Path name = Objects.requireNonNull(request.source().getFileName(), "source file name");
        return BookFormat.ofFileName(name.toString()).orElse(BookFormat.TXT);
    }
}
