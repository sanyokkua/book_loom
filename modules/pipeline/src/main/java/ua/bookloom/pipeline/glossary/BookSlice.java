package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Objects;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * The part of an opened book a glossary action reads: the project's index and the ordinals of the segments whose text
 * is evidence. Passing the pair together keeps every reader on the one index instead of on its own copy of the text.
 *
 * @param index the project's book index
 * @param ordinals the ordinals of the segments to read, in reading order
 */
public record BookSlice(BookIndex index, List<Integer> ordinals) {

    /** Rejects a missing component and copies the ordinals. */
    public BookSlice {
        Objects.requireNonNull(index, "index");
        ordinals = List.copyOf(ordinals);
    }

    /**
     * The segments of the book's body, which is what a name scan reads.
     *
     * @param index the project's book index; never null
     * @return the slice of the story segments
     */
    public static BookSlice story(final BookIndex index) {
        Objects.requireNonNull(index, "index");
        return new BookSlice(index, index.story());
    }

    /**
     * The text a model reads of the slice, to look names up in.
     *
     * @return one text per segment, placeholder tokens read as spaces; never null
     */
    public List<String> visibleTexts() {
        return index.visibleTexts(ordinals);
    }
}
