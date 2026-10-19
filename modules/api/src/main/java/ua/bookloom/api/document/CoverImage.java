package ua.bookloom.api.document;

import java.util.Arrays;
import java.util.Objects;

/**
 * A book's cover image, extracted by {@link BookInspector#profile} ({@code specs/document-round-trip/spec.md}
 * "Extract the book's cover image").
 *
 * <p>{@code bytes} is defensively copied on the way in and on the way out, and {@code equals}/{@code hashCode} are
 * computed over its contents rather than array identity, because a {@code byte[]} component would otherwise let a
 * caller-held array corrupt this record after construction, or make two covers with identical image data compare
 * unequal.
 *
 * @param resourcePath the cover's path/name within the source container
 * @param mediaType the cover's declared media type (for example {@code image/jpeg})
 * @param bytes the cover image's raw bytes, defensively copied
 */
@SuppressWarnings("ArrayRecordComponent") // bytes is deliberately a byte[]; defensively copied in and out below.
public record CoverImage(String resourcePath, String mediaType, byte[] bytes) {

    /**
     * Validates the non-nullable components and defensively copies {@code bytes} so a caller-held mutable array
     * cannot corrupt this record after construction.
     */
    public CoverImage {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(bytes, "bytes");
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Returns a defensive copy of this cover's bytes, so mutating the returned array cannot change this record.
     *
     * @return a copy of the cover image's raw bytes
     */
    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CoverImage that)) {
            return false;
        }
        return resourcePath.equals(that.resourcePath)
                && mediaType.equals(that.mediaType)
                && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourcePath, mediaType, Arrays.hashCode(bytes));
    }
}
