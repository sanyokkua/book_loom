package ua.bookloom.document.txt;

import com.google.inject.Singleton;
import java.nio.charset.Charset;
import java.util.Arrays;
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
    @SuppressWarnings(
            "ArrayRecordComponent") // copied on entry and exit; equality reads the content, toString the length.
    record ParsedTxt(byte[] originalBytes, Charset charset) {
        @Override
        public boolean equals(final Object other) {
            return other instanceof ParsedTxt(var oOriginalBytes, var oCharset)
                    && Arrays.equals(originalBytes, oOriginalBytes)
                    && Objects.equals(charset, oCharset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(originalBytes), charset);
        }

        @Override
        public String toString() {
            return "ParsedTxt[originalBytes=" + originalBytes.length + ", charset=" + charset + "]";
        }

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
