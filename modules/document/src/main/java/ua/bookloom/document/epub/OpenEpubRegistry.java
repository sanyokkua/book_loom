package ua.bookloom.document.epub;

import com.google.inject.Singleton;
import ua.bookloom.document.model.OpenDocumentRegistry;

/** The open-EPUB-document registry: what {@link EpubReader} parsed, held until {@link EpubWriter} needs it. */
@Singleton
final class OpenEpubRegistry extends OpenDocumentRegistry<ParsedEpub> {}
