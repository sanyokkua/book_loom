package ua.bookloom.document.md;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.MarkdownFixtures;

/** Opens, retargets and writes a Markdown file for the auxiliary-text tests; the file is always {@code book.md}. */
final class MarkdownAuxHarness {

    static final String FILE = "book.md";

    private final Path dir;
    private final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();

    MarkdownAuxHarness(Path dir) {
        this.dir = dir;
    }

    Document open(String markdown) {
        return new MarkdownReader(registry).read(MarkdownFixtures.write(dir.resolve(FILE), markdown));
    }

    String export(Document document, String targetLanguage) {
        final Path out = new MarkdownWriter(registry).write(document, dir.resolve("out.md"), targetLanguage);
        try {
            return new String(Files.readAllBytes(out), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The auxiliary unit's segments. */
    static List<Segment> auxOf(Document document) {
        return document.units().get(document.units().size() - 1).segments();
    }

    static List<String> auxSources(Document document) {
        return auxOf(document).stream().map(Segment::sourceInner).toList();
    }

    static List<Segment> bodyOf(Document document) {
        return document.units().get(0).segments();
    }

    /** Restores {@code masked} through the real unmask, so the target is what a translation run would store. */
    static String restored(Segment segment, String masked) {
        return Objects.requireNonNull(DocumentServices.newService()
                .unmask(BookFormat.MARKDOWN, segment, masked)
                .data());
    }

    /** Rebuilds {@code document} with {@code targetInner} set on each segment whose id is a key of {@code targets}. */
    static Document withTargets(Document document, Map<String, String> targets) {
        final List<Unit> units = document.units().stream()
                .map(unit -> new Unit(
                        unit.id(),
                        unit.order(),
                        unit.href(),
                        unit.mediaType(),
                        unit.skeleton(),
                        unit.segments().stream()
                                .map(s -> retargeted(s, targets))
                                .toList()))
                .toList();
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                units);
    }

    private static Segment retargeted(Segment segment, Map<String, String> targets) {
        final String target = targets.get(segment.id());
        if (target == null) {
            return segment;
        }
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                segment.masked(),
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                target,
                segment.status(),
                segment.confidence(),
                segment.declaredLanguage(),
                segment.pairs(),
                segment.lineBreakTokens());
    }
}
