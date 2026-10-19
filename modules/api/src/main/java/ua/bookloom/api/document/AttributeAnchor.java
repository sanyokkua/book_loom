package ua.bookloom.api.document;

import java.util.List;
import java.util.Objects;

/**
 * Addresses one attribute value of an element in a tree-shaped skeleton — an image's {@code alt} — by the element's
 * stable node path plus the attribute's name (ADR-0041).
 *
 * <p><strong>Why not a {@link NodeAnchor}.</strong> An attribute value has no line-break-delimited run to index,
 * and addressing it through a run would either regenerate markup or break the rule that parsing adds nothing to the
 * skeleton; writing it back replaces that one value and nothing else.
 *
 * @param nodePath the root-to-element index path, outermost first, as in {@link NodeAnchor}; never negative entries
 * @param attribute the attribute's qualified name, e.g. {@code "alt"}; never blank
 */
public record AttributeAnchor(List<Integer> nodePath, String attribute) implements SkeletonAnchor {

    /** Validates the path, defensively copies it, and rejects a blank attribute name. */
    public AttributeAnchor {
        Objects.requireNonNull(nodePath, "nodePath");
        Objects.requireNonNull(attribute, "attribute");
        if (attribute.isBlank()) {
            throw new IllegalArgumentException("attribute must not be blank");
        }
        for (final Integer step : nodePath) {
            Objects.requireNonNull(step, "nodePath entry");
            if (step < 0) {
                throw new IllegalArgumentException("nodePath entries must be >= 0, but found " + step);
            }
        }
        nodePath = List.copyOf(nodePath);
    }
}
