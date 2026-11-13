package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;

/** The run frame's system blocks stay byte-for-byte as shipped. */
class RunFramesTest {

    private static final Document MARKED_BOOK =
            FrameFixtures.book(FrameFixtures.NARRATION, FrameFixtures.DIALOGUE, FrameFixtures.MARKED);

    // The hash of the builder's frame over this book, taken before the rules-layout arm: the frame must keep sending
    // exactly the bytes it sent then.
    @Test
    void frame_builderLayout_systemBytesUnchanged() {
        final RunFrame frame = FrameFixtures.frame(MARKED_BOOK);

        assertThat(frame.frameHash()).isEqualTo("12172d53e9f6ec00ed67a1659f65fd2b037a83cc4bc64c69459514d12ff9d55d");
    }
}
