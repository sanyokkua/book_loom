package ua.bookloom.pipeline.glossary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.util.lang.LanguageTags;

/**
 * The bundled, deliberately small first-name lists ({@code given-names-<language>.txt}, "seed, not exhaustive") and
 * what they are used for: a glossary entry for a person whose first word is a listed name and whose gender is not
 * known gets that name's gender as a suggestion, so the character gender sheet and the later agreement fixes have
 * something to act on. A name that either gender uses is not listed, so the list never guesses; a language with no list
 * suggests nothing, and nothing is fetched.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GivenNames {

    private static final String PREFIX = "given-names-";
    private static final String SUFFIX = ".txt";
    private static final String MALE_LINE = "male:";
    private static final String FEMALE_LINE = "female:";
    private static final int MAX_NAME_WORDS = 3;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, Map<String, Gender>> LOADED = new ConcurrentHashMap<>();

    /**
     * The gender a language's list holds for a term's first word.
     *
     * @param term the non-null glossary term, of at most three words, whose first word starts with a capital
     * @param languageTag the source language's tag, or null when it is not known
     * @return the listed gender, or empty when the term is not a listed first name or the language has no list
     */
    public static Optional<Gender> genderOf(final String term, @Nullable final String languageTag) {
        Objects.requireNonNull(term, "term");
        final Optional<String> language = LanguageTags.normalize(languageTag)
                .map(tag -> Locale.forLanguageTag(tag).getLanguage())
                .filter(tag -> !tag.isEmpty());
        final List<String> words = WHITESPACE.splitAsStream(term.strip()).toList();
        if (language.isEmpty()
                || words.size() > MAX_NAME_WORDS
                || !Character.isUpperCase(words.getFirst().codePointAt(0))) {
            return Optional.empty();
        }
        final Gender gender = LOADED.computeIfAbsent(language.get(), GivenNames::load)
                .get(words.getFirst().toLowerCase(Locale.ROOT));
        log.trace("Given-name lookup first word of '{}' language={} gender={}", term, language.get(), gender);
        return Optional.ofNullable(gender).filter(listed -> listed != Gender.UNKNOWN);
    }

    /**
     * Suggests a gender for a person's entry that has none: a character whose first word is a listed first name gets
     * that gender marked as a suggestion. It never types an entry — a place or an untyped entry named like a person
     * (Victoria Station, Florence) stays what it is — and it never touches a gender somebody set, a locked entry, or an
     * entry the list was already tried on, so a gender the person reset to unknown stays unknown.
     *
     * @param entry the non-null entry
     * @param languageTag the source language's tag, or null when it is not known
     * @return the entry with a suggested gender, or {@code entry} itself when nothing is to be suggested
     */
    public static GlossaryEntry seeded(final GlossaryEntry entry, @Nullable final String languageTag) {
        Objects.requireNonNull(entry, "entry");
        final boolean eligible = entry.gender() == Gender.UNKNOWN
                && !entry.locked()
                && !entry.genderSeedTried()
                && entry.type() == TermType.CHARACTER;
        if (!eligible) {
            return entry;
        }
        final Optional<Gender> listed = genderOf(entry.term(), languageTag);
        if (listed.isEmpty()) {
            return entry;
        }
        log.debug("Glossary entry {} gets the suggested gender {} from the given-name list", entry.id(), listed.get());
        return entry.withSuggestedGender(listed.get());
    }

    /**
     * The entries as {@link #seeded(GlossaryEntry, String)} would leave them, in order.
     *
     * @param entries the non-null entries
     * @param languageTag the source language's tag, or null when it is not known
     * @return a list of the same size; never null
     */
    public static List<GlossaryEntry> seeded(final List<GlossaryEntry> entries, @Nullable final String languageTag) {
        Objects.requireNonNull(entries, "entries");
        return entries.stream().map(entry -> seeded(entry, languageTag)).toList();
    }

    /**
     * Writes a suggested gender to every held character that has none, was never tried and whose first word is a
     * listed first name. Each entry is changed in one atomic step against its current state, so a person's edit made
     * meanwhile is not overwritten.
     *
     * @param glossary the non-null glossary to read and update
     * @param projectId the non-null project whose entries are read
     * @param languageTag the source language's tag, or null when it is not known
     * @return how many entries were changed, or the repository's error
     */
    public static Result<Integer> seedHeld(
            final GlossaryRepository glossary, final String projectId, @Nullable final String languageTag) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(projectId, "projectId");
        return glossary.all(projectId).flatMap(held -> {
            int changed = 0;
            for (final GlossaryEntry entry : held) {
                if (seeded(entry, languageTag).equals(entry)) {
                    continue;
                }
                final Result<Optional<GlossaryEntry>> stored =
                        glossary.update(projectId, entry.term(), current -> seeded(current, languageTag));
                if (stored.isErr()) {
                    return Result.err(Objects.requireNonNull(stored.error(), "error"));
                }
                changed++;
            }
            log.info("Given-name seeding project={} language={} entriesSeeded={}", projectId, languageTag, changed);
            return Result.ok(changed);
        });
    }

    private static Map<String, Gender> load(final String language) {
        final String resource = PREFIX + language + SUFFIX;
        try (InputStream stream = GivenNames.class.getResourceAsStream(resource)) {
            if (stream == null) {
                log.debug("No given-name list {}", resource);
                return Map.of();
            }
            final Map<String, Gender> names =
                    read(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            log.debug("Loaded given-name list {} with {} names", resource, names.size());
            return Map.copyOf(names);
        } catch (IOException cause) {
            throw new UncheckedIOException(resource + " could not be read", cause);
        }
    }

    private static Map<String, Gender> read(final BufferedReader reader) {
        final Map<String, Gender> names = new HashMap<>();
        for (final String line : reader.lines().map(String::strip).toList()) {
            if (line.startsWith(MALE_LINE)) {
                add(names, line.substring(MALE_LINE.length()), Gender.MALE);
            } else if (line.startsWith(FEMALE_LINE)) {
                add(names, line.substring(FEMALE_LINE.length()), Gender.FEMALE);
            }
        }
        return names;
    }

    private static void add(final Map<String, Gender> names, final String list, final Gender gender) {
        WHITESPACE
                .splitAsStream(list.strip())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(name -> names.merge(name, gender, (was, now) -> was == now ? now : Gender.UNKNOWN));
    }
}
