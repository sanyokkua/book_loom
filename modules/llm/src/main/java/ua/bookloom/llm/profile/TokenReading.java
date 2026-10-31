package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/** What reading the word tokens of a name collected; mutable and confined to one parse. */
final class TokenReading {

    @Nullable
    Double size;

    @Nullable
    Double effective;

    @Nullable
    Double active;

    @Nullable
    Integer experts;

    @Nullable
    Double expertSize;

    @Nullable
    String context;

    @Nullable
    String date;

    @Nullable
    String release;

    @Nullable
    String format;

    @Nullable
    String monthDay;

    @Nullable
    String dateEight;

    boolean qat;

    boolean imatrix;

    boolean dwq;

    boolean dynamic;

    /** Position in {@link #pending} where a "distill" word was met, or -1. */
    int distillIndex = -1;

    boolean unquantized;

    final Set<String> variants = new TreeSet<>();

    final List<String> spec = new ArrayList<>();

    /** Words left over for the family, version and line. */
    final List<PendingWord> pending = new ArrayList<>();
}
