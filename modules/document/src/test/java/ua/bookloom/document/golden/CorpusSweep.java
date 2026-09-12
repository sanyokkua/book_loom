package ua.bookloom.document.golden;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;

/**
 * The corpus verification's probe mechanics, over one book and over a whole corpus directory (design.md D6, task
 * group 11). Package-private and living in {@code ua.bookloom.document.golden}: reusing the format comparators
 * here — {@link EpubCanonicalAssert} (public) and the package-private {@link Fb2CanonicalAssert} /
 * {@link MarkdownAstAssert} — without widening either comparator's visibility is exactly why this class is not a
 * production or a separate test-support module.
 *
 * <p><strong>Records, does not assert.</strong> Every comparator here throws {@link AssertionError} on mismatch;
 * this class always catches it and turns it into a recorded outcome, because a run that stops at the first bad
 * book cannot answer "did anything regress" (design.md D6). The per-book stat gathering ({@link CorpusOpenStats}),
 * the mutation/marker-strip check ({@link CorpusMutation}), the mask-then-restore probe ({@link CorpusMaskProbe})
 * and the shared {@link Document} flattening helpers ({@link CorpusDocuments}) live in their own files so this
 * one stays inside the 400-line house limit.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusSweep {

    private static final String DEFAULT_TARGET_LANGUAGE = "uk";
    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(".epub", ".fb2.zip", ".fb2", ".md", ".markdown", ".txt");

    /**
     * Every corpus file this sweep can attempt to open, sorted for a stable report order.
     *
     * @param corpusDir the configured corpus directory
     * @return every file under {@code corpusDir} whose name carries a supported extension
     */
    static List<Path> discoverBooks(Path corpusDir) {
        Objects.requireNonNull(corpusDir, "corpusDir");
        try (Stream<Path> walk = Files.walk(corpusDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(CorpusSweep::hasSupportedExtension)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to walk the configured corpus directory " + corpusDir, e);
        }
    }

    private static boolean hasSupportedExtension(Path file) {
        final String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /**
     * Runs every probe over every book under {@code corpusDir} and writes the report under {@code reportDir}.
     * Never stops at the first bad book (task 11's own requirement): {@link #verifyOneBook} never throws, so one
     * unopenable or malformed book only ever changes that book's own recorded outcome.
     *
     * @param corpusDir the configured corpus directory, already confirmed to exist
     * @param reportDir where to write the report and each book's own scratch work directory; created if absent
     * @return every book's outcome, in the same stable order the report was written in
     * @throws IOException if the report files cannot be created or written
     */
    static List<CorpusBookOutcome> verifyCorpus(Path corpusDir, Path reportDir) throws IOException {
        Objects.requireNonNull(corpusDir, "corpusDir");
        Objects.requireNonNull(reportDir, "reportDir");
        final List<Path> books = discoverBooks(corpusDir);
        final Path workRoot = reportDir.resolve("work");
        final List<CorpusBookOutcome> outcomes = new ArrayList<>();
        try (CorpusReportSink sink = CorpusReportSink.open(reportDir)) {
            for (int index = 0; index < books.size(); index++) {
                final CorpusBookOutcome outcome =
                        verifyOneBook(books.get(index), corpusDir, workRoot.resolve(Integer.toString(index)));
                sink.record(outcome);
                outcomes.add(outcome);
            }
        }
        return List.copyOf(outcomes);
    }

    /**
     * Runs every probe over one book. Never throws: an unexpected failure anywhere in the probes themselves is
     * caught and recorded as a P0 failure, so one bad book never stops the caller's loop over the rest of the
     * corpus.
     *
     * @param book the book file to verify, inside the (read-only) configured corpus directory
     * @param workDir this book's own scratch directory, outside the corpus directory — every probe that writes
     *     an output writes under here, in its own {@code p1}/{@code p2}/{@code p3} subdirectory, keeping the
     *     source's original file name in every one of them (trap 3)
     * @return this book's recorded outcome
     */
    static CorpusBookOutcome verifyOneBook(Path book, Path corpusDir, Path workDir) {
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(corpusDir, "corpusDir");
        Objects.requireNonNull(workDir, "workDir");
        final long start = System.nanoTime();
        try {
            return verifyOneBookUnguarded(book, corpusDir, workDir, start);
        } catch (RuntimeException harnessFailure) {
            return notOpenedOutcome(
                    book, corpusDir, "harness:" + harnessFailure.getClass().getSimpleName(), elapsedMs(start));
        }
    }

    /**
     * A book's identity in the report: its path relative to the configured corpus directory, with {@code /}
     * separators on every platform.
     *
     * <p>Deliberately not the bare file name. The corpus is a tree, and it genuinely contains two different books
     * sharing one base name — so a bare name collapses two rows into one, and, worse, cannot be diffed row-for-row
     * against a previous run's report, which is the whole point of keeping one (design.md D6a).
     */
    private static String identityOf(Path book, Path corpusDir) {
        return corpusDir.relativize(book).toString().replace(java.io.File.separatorChar, '/');
    }

    private static CorpusBookOutcome verifyOneBookUnguarded(Path book, Path corpusDir, Path workDir, long start) {
        final Result<Document> opened = DocumentServices.newService().open(book);
        if (opened.isErr()) {
            return notOpenedOutcome(book, corpusDir, codeOf(opened), elapsedMs(start));
        }
        final Document p0 = Objects.requireNonNull(opened.data());
        final IdentityProbe identity = probeIdentity(book, p0, workDir);
        final CorpusFixedPointOutcome fixedPoint = probeFixedPoint(identity, p0.format(), workDir);
        final CorpusMutationOutcome mutation = probeMutation(book, p0, workDir);
        final CorpusIdempotenceOutcome idempotence = probeIdempotence(identity, p0);
        final CorpusMaskOutcome mask = probeMask(p0);
        return new CorpusBookOutcome(
                identityOf(book, corpusDir),
                CorpusOpenStats.openedOutcomeOf(book, p0, elapsedMs(start)),
                identity.outcome(),
                fixedPoint,
                mutation,
                idempotence,
                mask,
                new CorpusResourceOutcome(elapsedMs(start), sizeOf(book)));
    }

    private static CorpusBookOutcome notOpenedOutcome(Path book, Path corpusDir, String errorCode, long elapsedMs) {
        return new CorpusBookOutcome(
                identityOf(book, corpusDir),
                new CorpusOpenOutcome.Failed(errorCode),
                new CorpusIdentityOutcome.NotAttempted(),
                new CorpusFixedPointOutcome.NotAttempted(),
                new CorpusMutationOutcome.NotAttempted(),
                new CorpusIdempotenceOutcome.NotAttempted(),
                new CorpusMaskOutcome.NotAttempted(),
                new CorpusResourceOutcome(elapsedMs, sizeOf(book)));
    }

    // --- P1 identity ------------------------------------------------------------------------------------------

    /** Carries what P2 and P4 need from a successful P1 write, alongside its recorded outcome. */
    private record IdentityProbe(
            CorpusIdentityOutcome outcome,
            @Nullable Path output,
            @Nullable String targetLanguage) {}

    private static IdentityProbe probeIdentity(Path book, Document p0, Path workDir) {
        final DocumentService service = DocumentServices.newService();
        final Result<Document> reopened = service.open(book);
        if (reopened.isErr()) {
            return new IdentityProbe(new CorpusIdentityOutcome.OpenFailed(codeOf(reopened)), null, null);
        }
        final Document d1 = Objects.requireNonNull(reopened.data());
        final String targetLanguage = targetLanguageOf(d1);
        final Path output = probeDir(workDir, "p1").resolve(book.getFileName());
        final Result<Path> written = service.write(d1, output, targetLanguage);
        if (written.isErr()) {
            return new IdentityProbe(new CorpusIdentityOutcome.WriteFailed(codeOf(written)), null, null);
        }
        final CanonicalComparison comparison = compareCanonical(p0.format(), book, output, targetLanguage);
        final CorpusIdentityOutcome outcome = new CorpusIdentityOutcome.Written(
                bytesEqual(book, output), comparison.ok(), comparison.failureMessage());
        return new IdentityProbe(outcome, output, targetLanguage);
    }

    // --- P2 fixed point ---------------------------------------------------------------------------------------

    private static CorpusFixedPointOutcome probeFixedPoint(IdentityProbe identity, BookFormat format, Path workDir) {
        if (!(identity.outcome() instanceof CorpusIdentityOutcome.Written) || identity.output() == null) {
            return new CorpusFixedPointOutcome.NotAttempted();
        }
        final Path output1 = identity.output();
        final DocumentService service = DocumentServices.newService();
        final Result<Document> reopened = service.open(output1);
        if (reopened.isErr()) {
            return new CorpusFixedPointOutcome.ReopenFailed(codeOf(reopened));
        }
        final Document d2 = Objects.requireNonNull(reopened.data());
        final Path output2 = probeDir(workDir, "p2").resolve(output1.getFileName());
        final Result<Path> written = service.write(d2, output2, targetLanguageOf(d2));
        if (written.isErr()) {
            return new CorpusFixedPointOutcome.WriteFailed(codeOf(written));
        }
        final CanonicalComparison comparison = compareCanonical(format, output1, output2, targetLanguageOf(d2));
        return new CorpusFixedPointOutcome.Written(bytesEqual(output1, output2), comparison.ok());
    }

    // --- P3 mutation ------------------------------------------------------------------------------------------

    private static CorpusMutationOutcome probeMutation(Path book, Document p0, Path workDir) {
        final DocumentService service = DocumentServices.newService();
        final Result<Document> reopened = service.open(book);
        if (reopened.isErr()) {
            return new CorpusMutationOutcome.OpenFailed(codeOf(reopened));
        }
        final Document d3 = Objects.requireNonNull(reopened.data());
        final Path output = probeDir(workDir, "p3").resolve(book.getFileName());
        final Result<Path> written = service.write(CorpusMutation.withMarkerTargets(d3), output, targetLanguageOf(d3));
        if (written.isErr()) {
            final AppError error = Objects.requireNonNull(written.error());
            return new CorpusMutationOutcome.WriteFailed(error.code().name(), error.code() == ErrorCode.validation);
        }
        final Result<Document> reopenedOutput = DocumentServices.newService().open(output);
        if (reopenedOutput.isErr()) {
            return new CorpusMutationOutcome.ReopenFailed(codeOf(reopenedOutput));
        }
        return CorpusMutation.completedOutcomeOf(p0, d3, Objects.requireNonNull(reopenedOutput.data()));
    }

    // --- P4 idempotence ---------------------------------------------------------------------------------------

    private static CorpusIdempotenceOutcome probeIdempotence(IdentityProbe identity, Document p0) {
        if (!(identity.outcome() instanceof CorpusIdentityOutcome.Written) || identity.output() == null) {
            return new CorpusIdempotenceOutcome.NotAttempted();
        }
        final Result<Document> reopened = DocumentServices.newService().open(identity.output());
        if (reopened.isErr()) {
            return new CorpusIdempotenceOutcome.ReopenFailed(codeOf(reopened));
        }
        final Document back = Objects.requireNonNull(reopened.data());
        return new CorpusIdempotenceOutcome.Completed(
                CorpusDocuments.tuplesOf(back).equals(CorpusDocuments.tuplesOf(p0)),
                CorpusDocuments.sourceById(back).equals(CorpusDocuments.sourceById(p0)));
    }

    // --- Mask-then-restore probe (task 10.1) -------------------------------------------------------------------

    /**
     * Gives every segment of {@code p0} its own masked form back as its own target and restores it — the
     * requirement <em>Verify the round trip against a local real-book corpus on demand</em>'s
     * "restores every segment from its own masked form" clause. {@code unmask} needs no document registry, so
     * this probe operates directly on {@code p0} rather than re-opening the book the way the write-based probes
     * must (trap 5 does not apply here).
     */
    private static CorpusMaskOutcome probeMask(Document p0) {
        return CorpusMaskProbe.run(DocumentServices.newService(), p0);
    }

    // --- Shared helpers ---------------------------------------------------------------------------------------

    /** Package-visible (not {@code private}) so the harness's catch-and-record behaviour can be proven directly. */
    record CanonicalComparison(boolean ok, @Nullable String failureMessage) {}

    /**
     * Judges P1/P2 on the canonical comparison, never on raw bytes (trap 4): jsoup rewrites an XML prolog to a
     * comment, so raw-byte equality reports a difference on nearly every EPUB — permitted under DD-43 and
     * meaningless.
     */
    static CanonicalComparison compareCanonical(BookFormat format, Path expected, Path actual, String targetLanguage) {
        try {
            switch (format) {
                case EPUB -> EpubCanonicalAssert.assertCanonicalEqual(expected, actual);
                case FB2 -> Fb2CanonicalAssert.assertCanonicalEqual(expected, actual, targetLanguage);
                case MARKDOWN -> MarkdownAstAssert.assertReParseEqual(expected, actual);
                case TXT -> assertBytesEqualForTxt(expected, actual);
            }
            return new CanonicalComparison(true, null);
        } catch (AssertionError comparisonFailure) {
            return new CanonicalComparison(false, comparisonFailure.getMessage());
        }
    }

    /** TXT's canonical comparison IS exact bytes (DD-43); thrown as an {@link AssertionError} for uniform handling. */
    private static void assertBytesEqualForTxt(Path expected, Path actual) {
        if (!bytesEqual(expected, actual)) {
            throw new AssertionError("TXT output is not byte-identical to " + expected.getFileName());
        }
    }

    private static boolean bytesEqual(Path left, Path right) {
        try {
            return java.util.Arrays.equals(Files.readAllBytes(left), Files.readAllBytes(right));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Each probe writes into its own directory under {@code workDir}, and the caller resolves the source's
     * original file name inside it (trap 3) — never a renamed one, since for Markdown/TXT/FB2 the file name is
     * the unit href and every segment id derives from it.
     */
    private static Path probeDir(Path workDir, String probe) {
        final Path dir = workDir.resolve(probe);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create probe directory " + dir, e);
        }
        return dir;
    }

    private static String targetLanguageOf(Document document) {
        return document.declaredLang() == null ? DEFAULT_TARGET_LANGUAGE : document.declaredLang();
    }

    /** Takes the failed {@link Result} itself, not its {@code error()}, so the parameter is never nullable at
     * the type level — {@code Result}'s own invariant guarantees {@code error()} is non-null on this path. */
    private static String codeOf(Result<?> failedResult) {
        return Objects.requireNonNull(failedResult.error(), "error").code().name();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1L;
        }
    }
}
