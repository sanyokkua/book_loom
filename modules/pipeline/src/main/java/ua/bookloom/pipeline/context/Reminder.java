package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.narrator.DialogueShare;
import ua.bookloom.pipeline.narrator.NarratorDetector;

/**
 * The short reminder that ends block 13 (design D9): at most three lines, each only when an item of the call needs
 * it, so a small model reads the rule it is about to break last.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Reminder {

    private static final String MARK = "⟦";
    // The dialogue tally reads a paragraph whose length the narration keeps, so the marks go first.
    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");

    /**
     * The reminder of one call.
     *
     * @param items the non-null items the call translates
     * @param frame the non-null run frame, which holds the lines' wording and the languages
     * @return the lines in the order marks, dialogue, narrator; never null, empty when no item needs one
     */
    public static List<String> of(final List<FocusItem> items, final RunFrame frame) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(frame, "frame");
        final @Nullable String source = frame.brief().sourceLanguage();
        final boolean marks =
                items.stream().anyMatch(item -> item.maskedSource().contains(MARK));
        final boolean dialogue = items.stream()
                .anyMatch(item ->
                        DialogueShare.of(List.of(unmarked(item)), source).spoken() > 0);
        final boolean narrator = !frame.reminders().narrator().isEmpty()
                && items.stream().anyMatch(item -> NarratorDetector.isFirstPerson(item.maskedSource(), source));
        final List<String> lines = new ArrayList<>();
        add(lines, marks, frame.reminders().marks());
        add(lines, dialogue, frame.reminders().dialogue());
        add(lines, narrator, frame.reminders().narrator());
        log.debug(
                "Reminder for {} items: marks={} dialogue={} narrator={} lines={}",
                items.size(),
                marks,
                dialogue,
                narrator,
                lines.size());
        return List.copyOf(lines);
    }

    private static String unmarked(final FocusItem item) {
        return TOKEN.matcher(item.maskedSource()).replaceAll("");
    }

    private static void add(final List<String> lines, final boolean wanted, final String line) {
        if (wanted && !line.isBlank()) {
            lines.add(line);
        }
    }
}
