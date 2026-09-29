package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;

/**
 * The protected-span check of a stored target, which never went through the span gate for the segment as it is
 * masked now: a term locked after the entry was stored may be rendered otherwise in it. Each span's text must occur
 * exactly as often as its token occurs in the masked source, as the gate requires of a reply; the spans are then
 * hidden again behind their tokens, so the target can pass the same gate a reply passes.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RestoredCheck {

    static Result<String> check(final String storedMaskedTarget, final ProtectedMask mask) {
        final List<String> sourceTokens = Tokens.inOrder(mask.maskedText());
        String remasked = storedMaskedTarget;
        for (final List<ProtectedSpan> group : groups(mask)) {
            final ProtectedSpan first = group.getFirst();
            final List<Hit> hits = occurrences(remasked, first);
            final int expected = group.stream()
                    .mapToInt(span -> Collections.frequency(sourceTokens, span.token()))
                    .sum();
            if (hits.size() != expected) {
                return refused(first, hits.size(), expected);
            }
            remasked = hide(remasked, hits, group);
        }
        log.debug(
                "Stored target kept every protected span spans={}", mask.spans().size());
        return Result.ok(remasked);
    }

    /**
     * The spans grouped by what they restore, kept runs first and then locked renderings longest first, so a
     * rendering that is part of a longer one is counted only where the longer one is not.
     */
    private static List<List<ProtectedSpan>> groups(final ProtectedMask mask) {
        final Map<String, List<ProtectedSpan>> byText = new LinkedHashMap<>();
        mask.spans()
                .forEach(span -> byText.computeIfAbsent(span.check() + ":" + span.restored(), key -> new ArrayList<>())
                        .add(span));
        return byText.values().stream()
                .sorted(Comparator.<List<ProtectedSpan>, Boolean>comparing(
                                group -> group.getFirst().check() != CheckName.KEPT_RUN)
                        .thenComparing(group -> group.getFirst().restored().length(), Comparator.reverseOrder()))
                .toList();
    }

    private static List<Hit> occurrences(final String text, final ProtectedSpan span) {
        final List<Hit> hits = new ArrayList<>();
        if (span.check() == CheckName.KEPT_RUN) {
            int from = text.indexOf(span.restored());
            while (from >= 0) {
                hits.add(new Hit(from, from + span.restored().length()));
                from = text.indexOf(span.restored(), from + span.restored().length());
            }
            return hits;
        }
        final Matcher matcher = WholeWord.pattern(span.restored()).matcher(text);
        while (matcher.find()) {
            hits.add(new Hit(matcher.start(), matcher.end()));
        }
        return hits;
    }

    private static String hide(final String text, final List<Hit> hits, final List<ProtectedSpan> group) {
        final StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (int index = 0; index < hits.size(); index++) {
            out.append(text, cursor, hits.get(index).start())
                    .append(group.get(index).token());
            cursor = hits.get(index).end();
        }
        return out.append(text, cursor, text.length()).toString();
    }

    private static Result<String> refused(final ProtectedSpan span, final int found, final int expected) {
        final String note = "The protected text of " + span.token() + " occurs " + found
                + " times in the stored target instead of " + expected + ".";
        final QaFinding finding = Objects.requireNonNull(
                CheckResult.hardGateFailed(span.check(), note).finding());
        log.debug(
                "Stored target refused token={} check={} found={} expected={} finding={} severity={}",
                span.token(),
                span.check(),
                found,
                expected,
                finding.kind(),
                finding.severity());
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Protected text missing from the stored translation",
                "The stored translation does not render a locked name or a kept foreign phrase as it now must.",
                SafeDetails.empty().withQaFindings(List.of(finding.kind())).render(),
                null));
    }

    /** Where one occurrence of a span's text sits in the target. */
    private record Hit(int start, int end) {}
}
