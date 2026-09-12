package ua.bookloom.document.txt;

import com.google.inject.Singleton;
import java.nio.charset.Charset;
import java.util.Objects;
import ua.bookloom.document.model.OpenDocumentRegistry;

/** The open-plain-text-document registry: what {@link TxtReader} parsed, held until {@link TxtWriter} needs it. */
@Singleton
public final class OpenTxtRegistry extends OpenDocumentRegistry<OpenTxtRegistry.ParsedTxt> {

    /**
     * The open document's state: the original bytes, never mutated, plus the encoding they were read with.
     *
     * @param originalBytes the source file's bytes exactly as read — the buffer every span indexes and the one
     *     reassembly copies from
     * @param charset the encoding the text was decoded with, and the one a target string must encode back into
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: the immutable source buffer, defensively copied both
    // ways; equals()/hashCode() are never used for this internal carrier.
    record ParsedTxt(byte[] originalBytes, Charset charset) {

        ParsedTxt {
            Objects.requireNonNull(originalBytes, "originalBytes");
            Objects.requireNonNull(charset, "charset");
            originalBytes = originalBytes.clone();
        }

        @Override
        public byte[] originalBytes() {
            return originalBytes.clone();
        }
    }
}
