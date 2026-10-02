package ua.bookloom.app.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;

/** Reads the run options of one translate command — {@link RunOptions} — out of its argument list. */
@Slf4j
final class RunOptionsParser {

    /** An outage limit written as hours, minutes and seconds: {@code 12h}, {@code 90m}, {@code 1h30m}, {@code 45s}. */
    private static final Pattern SHORT_DURATION = Pattern.compile("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$");

    private static final int HOURS = 1;
    private static final int MINUTES = 2;
    private static final int SECONDS = 3;

    private final Set<String> seen = new HashSet<>();
    private @Nullable QualityDial quality;
    private @Nullable NamePolicy names;
    private boolean reviewNames;
    private Duration maxOutage = RunOptions.DEFAULTS.maxOutage();
    private boolean partialExport = true;
    private @Nullable Path report;

    /**
     * Consumes the option at {@code index} when it is a run option.
     *
     * @param args the whole argument list; never null
     * @param index the position of the option
     * @return the index after the option, the reason it is invalid, or empty when it is not a run option
     */
    Optional<Result<Integer>> consume(List<String> args, int index) {
        Objects.requireNonNull(args, "args");
        final String option = args.get(index);
        final Optional<Result<Integer>> consumed =
                switch (option) {
                    case "--review-names" -> Optional.of(flag(option, index, () -> reviewNames = true));
                    case "--no-partial" -> Optional.of(flag(option, index, () -> partialExport = false));
                    case "--quality", "--names", "--max-outage", "--report" -> Optional.of(valued(args, index, option));
                    default -> Optional.empty();
                };
        consumed.ifPresent(result ->
                log.debug("translate parser option={} outcome={}", option, result.isOk() ? "accepted" : "rejected"));
        return consumed;
    }

    /** The options consumed so far. */
    RunOptions options() {
        return new RunOptions(quality, names, reviewNames, maxOutage, partialExport, report);
    }

    private Result<Integer> flag(String option, int index, Runnable set) {
        if (!seen.add(option)) {
            return invalid(option + " was repeated");
        }
        set.run();
        return Result.ok(index + 1);
    }

    private Result<Integer> valued(List<String> args, int index, String option) {
        if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
            return invalid(option + " needs a value");
        }
        if (!seen.add(option)) {
            return invalid(option + " was repeated");
        }
        final String value = args.get(index + 1);
        final Result<Boolean> set =
                switch (option) {
                    case "--quality" -> setQuality(value);
                    case "--names" -> setNames(value);
                    case "--max-outage" -> setMaxOutage(value);
                    default -> setReport(value);
                };
        return set.isErr() ? Result.err(Objects.requireNonNull(set.error())) : Result.ok(index + 2);
    }

    private Result<Boolean> setQuality(String value) {
        quality = switch (value.toLowerCase(Locale.ROOT)) {
            case "fast" -> QualityDial.FAST;
            case "balanced" -> QualityDial.BALANCED;
            case "max" -> QualityDial.MAX;
            default -> null;
        };
        return quality == null ? invalid("--quality must be fast, balanced or max") : Result.ok(Boolean.TRUE);
    }

    private Result<Boolean> setNames(String value) {
        names = switch (value.toLowerCase(Locale.ROOT)) {
            case "translate" -> NamePolicy.TRANSLATE;
            case "transliterate" -> NamePolicy.TRANSLITERATE;
            case "keep" -> NamePolicy.KEEP_ORIGINAL;
            default -> null;
        };
        return names == null ? invalid("--names must be translate, transliterate or keep") : Result.ok(Boolean.TRUE);
    }

    private Result<Boolean> setMaxOutage(String value) {
        final @Nullable Duration parsed = durationOf(value.toLowerCase(Locale.ROOT));
        if (parsed == null || parsed.isNegative() || parsed.isZero()) {
            return invalid("--max-outage must be a positive duration such as 12h, 90m or 1h30m");
        }
        maxOutage = parsed;
        return Result.ok(Boolean.TRUE);
    }

    private Result<Boolean> setReport(String value) {
        try {
            report = Path.of(value);
            return Result.ok(Boolean.TRUE);
        } catch (InvalidPathException invalidPath) {
            return invalid("--report path is invalid");
        }
    }

    private static @Nullable Duration durationOf(String value) {
        final Matcher matcher = SHORT_DURATION.matcher(value);
        if (!value.isEmpty() && matcher.matches()) {
            return Duration.ofHours(part(matcher, HOURS))
                    .plusMinutes(part(matcher, MINUTES))
                    .plusSeconds(part(matcher, SECONDS));
        }
        try {
            return Duration.parse(value.toUpperCase(Locale.ROOT));
        } catch (DateTimeParseException notIso) {
            return null;
        }
    }

    private static long part(Matcher matcher, int group) {
        final @Nullable String digits = matcher.group(group);
        return digits == null ? 0 : Long.parseLong(digits);
    }

    private static <T> Result<T> invalid(String reason) {
        log.debug("translate parser branch=validation outcome=rejected reason={}", reason);
        return Result.err(AppError.of(ErrorCode.validation, "Invalid command arguments", reason));
    }
}
