package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.Character.UnicodeScript;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import ua.bookloom.util.lang.Language;
import ua.bookloom.util.lang.Languages;

/**
 * Guard (design D4): every target the application stores for a language that asks for stress marks to be removed is
 * plain — the bundled data of that language (its language file, the pair files and labels into it, its word lists) and
 * every committed glossary file. A mark there would match no text in a book and fail every comparison of a name. A
 * pair file also holds the source language's text ({@code Dvořák}), so only a mark on a letter of the stripping
 * language's own script counts.
 */
class StoredTargetsStressGuardTest {

    private static final Path BUNDLED = Path.of("src/main/resources/ua/bookloom/pipeline");
    private static final Path FIXTURES = Path.of("src/test/resources");
    private static final Pattern MARK = Pattern.compile("[\u0301\u0300]");

    @Test
    void storedTargets_ofLanguagesThatStripStress_holdNoAcuteOrGrave() throws IOException {
        final Set<String> stripping = Languages.all().stream()
                .map(Language::tag)
                .filter(TargetText::stripsStress)
                .collect(Collectors.toUnmodifiableSet());
        final Set<UnicodeScript> scripts = stripping.stream()
                .flatMap(tag -> Languages.scriptOf(tag).stream())
                .flatMap(script -> script.letterScripts().stream())
                .collect(Collectors.toUnmodifiableSet());
        final List<Path> files =
                Stream.concat(dataOf(stripping), glossaryFiles()).toList();

        assertThat(stripping).contains("uk", "ru");
        assertThat(files).isNotEmpty();
        assertThat(files).filteredOn(file -> holdsAMark(file, scripts)).isEmpty();
    }

    private static Stream<Path> dataOf(final Set<String> tags) throws IOException {
        try (Stream<Path> walk = Files.walk(BUNDLED)) {
            return walk.filter(Files::isRegularFile).filter(file -> isDataOf(file, tags)).toList().stream();
        }
    }

    // uk.txt, given-names-uk.txt, en-uk.properties: a file of the language itself or one that writes into it.
    private static boolean isDataOf(final Path file, final Set<String> tags) {
        final String name = file.getFileName().toString();
        final String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return tags.stream().anyMatch(tag -> stem.equals(tag) || stem.endsWith("-" + tag));
    }

    private static Stream<Path> glossaryFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(FIXTURES)) {
            return walk.filter(file -> file.getFileName().toString().equals("glossary.csv")).toList().stream();
        }
    }

    private static boolean holdsAMark(final Path file, final Set<UnicodeScript> scripts) {
        try {
            final String text =
                    Normalizer.normalize(Files.readString(file, StandardCharsets.UTF_8), Normalizer.Form.NFD);
            return MARK.matcher(text)
                    .results()
                    .anyMatch(mark ->
                            mark.start() > 0 && scripts.contains(UnicodeScript.of(text.codePointBefore(mark.start()))));
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
