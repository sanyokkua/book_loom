package ua.bookloom.llm.profile;

import java.util.List;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Sorts the word tokens of a name into sizes, dates, variants, flags and formats; what no rule claims is left over
 * for the family. The order of the rules is part of the grammar: a word is claimed by the first rule that fits.
 */
@Slf4j
final class NameTokenReader {

    TokenReading read(final List<String> tokens, final @Nullable String sidecar) {
        final TokenReading reading = new TokenReading();
        for (final String token : tokens) {
            if (token.equals(sidecar) || readFlag(token, sidecar, reading) || readNumber(token, reading)) {
                continue;
            }
            if (!readDated(token, reading) && !readVariant(token, reading)) {
                reading.pending.add(new PendingWord(token, afterSize(reading)));
            }
        }
        log.debug("Read tokens={} pending={}", tokens, reading.pending);
        return reading;
    }

    private static boolean afterSize(final TokenReading reading) {
        return reading.size != null || reading.effective != null || reading.experts != null;
    }

    private boolean readFlag(final String token, final @Nullable String sidecar, final TokenReading reading) {
        if (NameVocabulary.FORMATS.containsKey(token)) {
            reading.format = NameVocabulary.FORMATS.get(token);
        } else if (NameVocabulary.SPEC.contains(token)) {
            reading.spec.add(token);
        } else if ("i1".equals(token) || "imatrix".equals(token)) {
            reading.imatrix = true;
            reading.variants.add("imatrix");
        } else if (isMarker(token, sidecar, reading)) {
            log.trace("Marker word {}", token);
        } else if ("distill".equals(token) || "distilled".equals(token)) {
            reading.variants.add("distill");
            reading.distillIndex = reading.pending.size();
        } else {
            return false;
        }
        return true;
    }

    private static boolean isMarker(final String token, final @Nullable String sidecar, final TokenReading reading) {
        switch (token) {
            case "qat" -> {
                reading.qat = true;
                reading.variants.add("qat");
            }
            case "unquantized" -> reading.unquantized = true;
            case "dwq" -> reading.dwq = true;
            case "ud" -> reading.dynamic = true;
            case "model" -> {
                return sidecar != null;
            }
            default -> {
                return NameVocabulary.IGNORED.contains(token)
                        || NameVocabulary.GROUP_SIZE.matcher(token).matches();
            }
        }
        return true;
    }

    private boolean readNumber(final String token, final TokenReading reading) {
        final Matcher effective = NameVocabulary.EFFECTIVE.matcher(token);
        final Matcher active = NameVocabulary.ACTIVE.matcher(token);
        final Matcher experts = NameVocabulary.EXPERTS.matcher(token);
        final Matcher size = NameVocabulary.SIZE.matcher(token);
        final Matcher count = NameVocabulary.EXPERT_COUNT.matcher(token);
        final Matcher millions = NameVocabulary.MILLIONS.matcher(token);
        if (effective.matches()) {
            reading.effective = Double.valueOf(effective.group(1));
        } else if (active.matches() && (reading.size != null || !reading.pending.isEmpty())) {
            reading.active = Double.valueOf(active.group(1));
        } else if (experts.matches()) {
            reading.experts = Integer.valueOf(experts.group(1));
            reading.expertSize = Double.valueOf(experts.group(2));
        } else if (size.matches()) {
            reading.size = Double.valueOf(size.group(1));
        } else if (count.matches() && reading.size != null && reading.experts == null) {
            reading.experts = Integer.valueOf(count.group(1));
            reading.active = reading.size;
            reading.size = null;
        } else if (millions.matches() && Integer.parseInt(millions.group(1)) >= NameVocabulary.MILLIONS_FLOOR) {
            reading.size = Integer.parseInt(millions.group(1)) / (double) NameVocabulary.MILLIONS_PER_BILLION;
        } else {
            return false;
        }
        return true;
    }

    private boolean readDated(final String token, final TokenReading reading) {
        final Matcher release = NameVocabulary.RELEASE.matcher(token);
        if (NameVocabulary.CONTEXT.matcher(token).matches()) {
            reading.context = token;
        } else if (NameVocabulary.DATE.matcher(token).matches()) {
            reading.date = token;
        } else if (NameVocabulary.DATE_EIGHT.matcher(token).matches()) {
            reading.dateEight = token;
        } else if (release.matches()) {
            reading.release = release.group(1);
        } else if (NameVocabulary.MONTH_DAY.matcher(token).matches() && !reading.pending.isEmpty()) {
            reading.monthDay = token;
        } else {
            return false;
        }
        return true;
    }

    private boolean readVariant(final String token, final TokenReading reading) {
        final String variant = NameVocabulary.VARIANTS.get(token);
        if (variant != null) {
            reading.variants.add(variant);
        }
        return variant != null;
    }
}
