package ua.bookloom.pipeline.audit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.SuspiciousSegment;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.eval.ExportedBooks;

/**
 * Local-only tool: audits a book that was already exported against its source, with no model and no project. Run
 * {@code BOOKLOOM_AUDIT_SOURCE=<source> BOOKLOOM_AUDIT_EXPORT=<exported> [BOOKLOOM_AUDIT_GLOSSARY=<csv>]
 * [BOOKLOOM_AUDIT_FROM=en] [BOOKLOOM_AUDIT_TO=uk] ./gradlew :pipeline:corpus --tests '*ExportedBookAuditTool*' -i}
 * and read the {@code AUDIT} lines: one per doubted segment, its id and the checks that fired, never its text (a lost glossary name is
 * named on an {@code AUDIT-NAME} line, since it is the glossary's own word). The
 * exported book is opened as a book of its own and its segments are paired with the source's by id, which is how an
 * export keeps them. It is tagged {@code corpus}, so the gate never runs it, and does nothing without the variables.
 */
@Tag("corpus")
class ExportedBookAuditTool {

    private static final String SOURCE = "BOOKLOOM_AUDIT_SOURCE";
    private static final String EXPORT = "BOOKLOOM_AUDIT_EXPORT";
    private static final String GLOSSARY = "BOOKLOOM_AUDIT_GLOSSARY";

    @Test
    void audit_exportedBookAgainstItsSource_printsTheSuspiciousSegments() {
        final String source = System.getenv(SOURCE);
        final String exported = System.getenv(EXPORT);
        assumeTrue(source != null && exported != null, "set BOOKLOOM_AUDIT_SOURCE and BOOKLOOM_AUDIT_EXPORT");
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Document original = ExportedBooks.open(documents, Path.of(Objects.requireNonNull(source)));
        final Document written = ExportedBooks.open(documents, Path.of(Objects.requireNonNull(exported)));
        final String from = System.getenv().getOrDefault("BOOKLOOM_AUDIT_FROM", "en");
        final String to = System.getenv().getOrDefault("BOOKLOOM_AUDIT_TO", "uk");
        final BookBrief brief = withTarget(BookBrief.defaults(from), to);
        final FinalAudit.Book book = new FinalAudit.Book(brief, original, glossary(), WordValidator.none());

        final Map<String, List<QaFinding>> doubted = FinalAudit.scan(book, ExportedBooks.recordsOf(original, written));
        final List<SuspiciousSegment> suspicious = FinalAudit.named(doubted, original);

        System.out.println("AUDIT suspicious=" + suspicious.size());
        suspicious.forEach(segment ->
                System.out.println("AUDIT " + segment.segmentId() + " " + segment.locator() + " " + segment.checks()));
        doubted.values().stream()
                .flatMap(List::stream)
                .filter(finding -> finding.raisedBy().equals("audit:name-missing"))
                .forEach(finding -> System.out.println("AUDIT-NAME " + finding.note()));
    }

    private static BookBrief withTarget(final BookBrief brief, final String target) {
        return new BookBrief(
                brief.sourceLanguage(),
                target,
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }

    private static List<GlossaryEntry> glossary() {
        final String file = System.getenv(GLOSSARY);
        return ExportedBooks.glossary(file == null ? null : Path.of(file));
    }
}
