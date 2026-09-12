package ua.bookloom.document.md;

import com.google.inject.Singleton;
import ua.bookloom.document.model.OpenDocumentRegistry;

/** The open-Markdown-document registry: what {@link MarkdownReader} parsed, held until {@link MarkdownWriter} needs it. */
@Singleton
public final class OpenMarkdownRegistry extends OpenDocumentRegistry<ParsedMarkdown> {}
