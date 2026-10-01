package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.SpanFinder.Found;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * Hides a segment's protected spans behind tokens before drafting and puts them back through a hard gate. The model
 * never sees a locked name, so it cannot misspell it, nor a foreign phrase the book marks, so it cannot translate
 * it; the tokens are numbered above the segment's own so the two never collide.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtectedSpans {

    /**
     * Masks one segment.
     *
     * @param segment the segment whose masked text is protected; never null
     * @param sourceLanguage the run's source language tag, or {@code null} when none is known — then no foreign run
     *     is kept, since no language differs from an unknown one
     * @param policy the foreign-passage policy; only {@link ForeignPassagePolicy#KEEP} keeps a run
     * @param glossary every glossary entry; only locked ones with a non-blank target are hidden
     * @return the text to show the model, the spans to restore and the locked terms present
     */
    public static ProtectedMask mask(
            final Segment segment,
            @Nullable final String sourceLanguage,
            final ForeignPassagePolicy policy,
            final List<GlossaryEntry> glossary) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(glossary, "glossary");
        final String text = segment.masked();
        log.debug(
                "Masking segment={} sourceLanguage={} policy={} glossaryEntries={}",
                segment.id(),
                sourceLanguage,
                policy,
                glossary.size());
        final List<Found> runs = SpanFinder.keptRuns(segment, sourceLanguage, policy);
        final List<Found> terms = SpanFinder.lockedTerms(text, runs, glossary);
        final List<Found> all = new ArrayList<>(runs);
        all.addAll(terms);
        all.sort(Comparator.comparingInt(Found::start));
        final ProtectedMask mask = foldDropCaps(segment, build(text, all, terms));
        log.debug(
                "Masked segment={} keptRuns={} lockedTerms={} tokens={}",
                segment.id(),
                runs.size(),
                terms.size(),
                mask.spans().stream().map(ProtectedSpan::token).toList());
        if (log.isTraceEnabled()) {
            log.trace("Masked text segment={} text={}", segment.id(), mask.maskedText());
        }
        return mask;
    }

    /**
     * Wraps a gate so a reply is refused unless it returns every hidden token exactly once.
     *
     * @param masksBySegmentId each masked segment's mask; a segment with none goes straight to {@code inner}
     * @param inner the gate that checks the document's own markup on the text with the spans put back
     * @return the wrapping gate; a failed span answers {@link ua.bookloom.pipeline.heal.GateResult.GateFailed} with a
     *     high finding of the span's check, and success answers whatever {@code inner} answers for the restored text
     */
    public static GateFunction gate(final Map<String, ProtectedMask> masksBySegmentId, final GateFunction inner) {
        return new ProtectedGate(masksBySegmentId, inner);
    }

    /**
     * Checks a translation-memory target against the spans a segment is masked with now, before it is reused: each
     * span's text must occur in the target as many times as its token occurs in the masked source — whole-word for a
     * locked rendering, verbatim for a kept run — because the entry may predate a term's lock.
     *
     * @param storedMaskedTarget the entry's masked target, spans restored and the document's own tokens in place;
     *     never null
     * @param mask the segment's mask for the chunk; never null
     * @return the target with each span hidden again behind its token, ready for the span gate; or {@code validation}
     *     naming the finding the span gate raises for that span — {@code glossary} for a locked term, {@code markup}
     *     for a kept run
     */
    public static Result<String> checkRestored(final String storedMaskedTarget, final ProtectedMask mask) {
        Objects.requireNonNull(storedMaskedTarget, "storedMaskedTarget");
        Objects.requireNonNull(mask, "mask");
        log.debug(
                "Checking a stored target against spans={} tokens={}",
                mask.spans().size(),
                mask.spans().stream().map(ProtectedSpan::token).toList());
        return RestoredCheck.check(storedMaskedTarget, mask);
    }

    private static ProtectedMask foldDropCaps(final Segment segment, final ProtectedMask built) {
        final List<String> folded = DropCaps.tokensOf(segment);
        if (folded.isEmpty()) {
            return built;
        }
        log.debug("Folding drop caps out of segment={} tokens={}", segment.id(), folded);
        return new ProtectedMask(
                DropCaps.without(built.maskedText(), folded), built.spans(), built.presentLocked(), folded);
    }

    private static ProtectedMask build(final String text, final List<Found> ordered, final List<Found> terms) {
        final StringBuilder shown = new StringBuilder();
        final List<ProtectedSpan> spans = new ArrayList<>();
        int number = Tokens.highestIndex(text) + 1;
        int cursor = 0;
        for (final Found found : ordered) {
            final String token = Tokens.of(number++);
            shown.append(text, cursor, found.start()).append(token);
            spans.add(new ProtectedSpan(token, found.restored(), found.check()));
            cursor = found.end();
        }
        shown.append(text, cursor, text.length());
        return new ProtectedMask(shown.toString(), spans, presentLocked(terms));
    }

    private static List<LockedRendering> presentLocked(final List<Found> terms) {
        final Map<String, String> byTerm = new LinkedHashMap<>();
        terms.stream()
                .sorted(Comparator.comparingInt(Found::start))
                .forEach(found -> byTerm.putIfAbsent(Objects.requireNonNull(found.term()), found.restored()));
        return byTerm.entrySet().stream()
                .map(entry -> new LockedRendering(entry.getKey(), entry.getValue()))
                .toList();
    }
}
