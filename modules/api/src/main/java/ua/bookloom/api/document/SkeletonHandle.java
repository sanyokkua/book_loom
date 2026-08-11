package ua.bookloom.api.document;

import java.util.Objects;

/**
 * An opaque identity for a unit's parsed skeleton (its DOM/AST), resolvable only by {@code :document}.
 *
 * <p><strong>Why opaque.</strong> {@code api-is-framework-free} forbids a parser type — a jsoup {@code Element}, a
 * JDOM2 {@code Document} — from appearing in {@code :api}, and the rule is right: a parser type in a record
 * component would put that parser on all eight modules' compile classpath, and {@code :ui} would be able to reach
 * into a book's DOM it has no business touching (design.md D1). So the skeleton itself never crosses this
 * boundary; only this handle does, and only {@code :document} knows how to resolve it back to the tree it names.
 *
 * @param opaqueId an identity {@code :document} assigns and alone interprets; carries no meaning here
 */
public record SkeletonHandle(String opaqueId) {

    public SkeletonHandle {
        Objects.requireNonNull(opaqueId, "opaqueId");
    }
}
