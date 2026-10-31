package ua.bookloom.llm.profile;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelIdentity;

/**
 * Settles a disagreement between what a name says and what the server's own metadata says: the metadata wins, because
 * a name is a label someone chose and the metadata describes the weights. A fact the metadata does not give is left as
 * the name read it.
 */
@Slf4j
public final class MetadataWins {

    /**
     * What a server's metadata gave, already read by the caller.
     *
     * @param sizeB total parameters in billions, or null when not given
     * @param activeB active parameters of a mixture-of-experts in billions, or null when not given
     */
    public record Facts(@Nullable Double sizeB, @Nullable Double activeB) {}

    /**
     * Merges the metadata into the identity read from the name.
     *
     * @param named the identity the name gave
     * @param metadata the facts the server gave
     * @return the identity with the metadata's sizes where it has them; {@code named} itself when nothing differs
     */
    public ModelIdentity apply(final ModelIdentity named, final Facts metadata) {
        Objects.requireNonNull(named, "named");
        Objects.requireNonNull(metadata, "metadata");
        final Double size = pick("sizeB", named.sizeB(), metadata.sizeB());
        final Double active = pick("activeB", named.activeB(), metadata.activeB());
        if (Objects.equals(size, named.sizeB()) && Objects.equals(active, named.activeB())) {
            return named;
        }
        return new ModelIdentity(
                named.family(),
                named.version(),
                named.line(),
                size,
                named.effB(),
                active,
                named.variants(),
                named.kind(),
                named.baseFamily(),
                named.distillOf(),
                named.quantLabel(),
                named.needsShow());
    }

    private static @Nullable Double pick(
            final String field, final @Nullable Double fromName, final @Nullable Double fromMetadata) {
        if (fromMetadata == null) {
            return fromName;
        }
        if (fromName != null && !fromName.equals(fromMetadata)) {
            log.debug("Metadata wins over the name for {}: name={} metadata={}", field, fromName, fromMetadata);
        }
        return fromMetadata;
    }
}
