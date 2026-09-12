package ua.bookloom.document.fb2;

import com.google.inject.Singleton;
import ua.bookloom.document.model.OpenDocumentRegistry;

/** The open-FB2-document registry: what {@link Fb2Reader} parsed, held until {@link Fb2Writer} needs it. */
@Singleton
public final class OpenFb2Registry extends OpenDocumentRegistry<ParsedFb2> {}
