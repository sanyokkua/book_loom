package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.ProbeCall;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TestDetail.CheckRow;

/**
 * Words the Advanced features test for its card: one row per check that says what was asked, what came back and the
 * verdict, with each call's time and speed under it; and the calls' bodies laid one after another, each under a
 * heading that names its check and its number.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvancedRows {

    private static final Set<String> OUTCOMES = Set.of("honoured", "ignored", "refused");
    private static final String HONOURED = "honoured";
    private static final String IGNORED = "ignored";
    private static final int STATUS_CLASS = 100;
    private static final int SUCCESS_CLASS = 2;

    /**
     * The rows of the checks a finished stage ran.
     *
     * @param messages the catalogue the words come from
     * @param note the stage's note in the verifier's machine words, or {@code null} when it has none
     * @param calls the calls the checks made, in the order sent
     * @return one row per check the note names, in the checks' order; never null
     */
    public static List<CheckRow> of(final Messages messages, final @Nullable String note, final List<ProbeCall> calls) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(calls, "calls");
        final List<CheckRow> rows = new ArrayList<>();
        for (final Map.Entry<AdvancedCheck, String> found :
                AdvancedCheck.read(note).entrySet()) {
            rows.add(row(messages, found.getKey(), found.getValue(), calls));
        }
        log.debug("advanced rows worded: {} checks, {} calls", rows.size(), calls.size());
        return List.copyOf(rows);
    }

    private static CheckRow row(
            final Messages messages, final AdvancedCheck check, final String value, final List<ProbeCall> calls) {
        final String outcome = outcomeOf(check, value);
        final String field =
                OUTCOMES.contains(value.toLowerCase(Locale.ROOT)) || AdvancedCheck.NO_FIELD.equals(value) ? "" : value;
        final List<String> lines = new ArrayList<>();
        final List<ProbeCall> own =
                calls.stream().filter(call -> call.check() == check).toList();
        for (int index = 0; index < own.size(); index++) {
            lines.add(callLine(messages, index + 1, own.get(index)));
        }
        return new CheckRow(
                check.token(),
                messages.code(MessageKey.SETTINGS_ADVANCED_CHECK, check.token()),
                messages.code(MessageKey.SETTINGS_TEST_ADVANCED_ASKED, check.token()),
                messages.code(MessageKey.SETTINGS_TEST_ADVANCED_ANSWERED, check.token() + "_" + outcome, field),
                messages.code(MessageKey.SETTINGS_ADVANCED_OUTCOME, outcome),
                lines);
    }

    // The thinking and cap checks name the field that worked instead of an outcome; none named means none worked.
    private static String outcomeOf(final AdvancedCheck check, final String value) {
        final String lower = value.toLowerCase(Locale.ROOT);
        if (OUTCOMES.contains(lower)) {
            return lower;
        }
        return check == AdvancedCheck.CAP && AdvancedCheck.NO_FIELD.equals(value) ? IGNORED : HONOURED;
    }

    private static String callLine(final Messages messages, final int number, final ProbeCall call) {
        final String time = CheckSentences.durationText(messages, call.elapsed());
        final Double speed = call.tokensPerSecond();
        if (speed != null) {
            return messages.get(MessageKey.SETTINGS_TEST_ADVANCED_CALL_SPEED, number, time, speed);
        }
        return call.httpStatus() == 0 || isSuccess(call.httpStatus())
                ? messages.get(MessageKey.SETTINGS_TEST_ADVANCED_CALL, number, time)
                : messages.get(MessageKey.SETTINGS_TEST_ADVANCED_CALL_STATUS, number, time, call.httpStatus());
    }

    private static boolean isSuccess(final int status) {
        return status / STATUS_CLASS == SUCCESS_CLASS;
    }

    /**
     * The calls' bodies one after another, each under a heading such as "Structured answers, call 2".
     *
     * @param messages the catalogue the headings come from
     * @param bodies the check of each call and the text shown for it, in the order sent
     * @return the text; empty when there are no calls
     */
    public static String listed(final Messages messages, final List<Map.Entry<AdvancedCheck, String>> bodies) {
        Objects.requireNonNull(messages, "messages");
        final StringBuilder text = new StringBuilder();
        final Map<AdvancedCheck, Integer> numbers = new EnumMap<>(AdvancedCheck.class);
        for (final Map.Entry<AdvancedCheck, String> body : bodies) {
            final int number = numbers.merge(body.getKey(), 1, Integer::sum);
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append(messages.get(
                            MessageKey.SETTINGS_TEST_CALL_HEADING,
                            messages.code(
                                    MessageKey.SETTINGS_ADVANCED_CHECK,
                                    body.getKey().token()),
                            number))
                    .append('\n')
                    .append(body.getValue());
        }
        return text.toString();
    }
}
