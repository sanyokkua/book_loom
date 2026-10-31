package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;

/**
 * What the test dialog shows for one test.
 *
 * @param severity how the verdict banner looks
 * @param verdict the sentence in the banner
 * @param requestLine the method and URL, or an empty string when nothing was sent
 * @param curl the request as a curl command with the key hidden, or an empty string when nothing was sent
 * @param body the request body, or the no-body text when there was none; empty when nothing was sent
 * @param response the reply text, or the no-body text when there was none; empty when nothing was sent
 * @param metrics the figures between the body and the response, in the order shown
 * @param rawBody the request body as sent, empty when there was none; this, not the placeholder, is what Copy takes
 * @param rawResponse the reply as received, empty when there was none
 * @param checks the Advanced features checks, one worded row each; empty for every other test and while it runs
 */
public record TestDetail(
        Severity severity,
        String verdict,
        String requestLine,
        String curl,
        String body,
        String response,
        List<Metric> metrics,
        String rawBody,
        String rawResponse,
        List<CheckRow> checks) {

    /** A detail with no check rows, as every test but the Advanced features one shows. */
    public TestDetail(
            final Severity severity,
            final String verdict,
            final String requestLine,
            final String curl,
            final String body,
            final String response,
            final List<Metric> metrics,
            final String rawBody,
            final String rawResponse) {
        this(severity, verdict, requestLine, curl, body, response, metrics, rawBody, rawResponse, List.of());
    }

    /** How the banner over the dialog looks. */
    public enum Severity {
        /** The test passed. */
        OK,
        /** The test passed with a note, or was not attempted. */
        WARN,
        /** The test failed. */
        ERR,
        /** The test is running or was not run. */
        INFO
    }

    /**
     * One figure of the middle column.
     *
     * @param name the short name the figure is identified by, such as {@code time}
     * @param label the label in the person's language
     * @param value the figure as text
     */
    public record Metric(String name, String label, String value) {}

    /**
     * One Advanced features check as the card explains it.
     *
     * @param token the check's machine word, such as {@code schema}, which the row's id is made from
     * @param name the check's name
     * @param asked what the request asked the server for
     * @param answered what came back
     * @param verdict honoured, ignored or refused
     * @param calls one line per call the check made: its time, and its speed when the reply said
     */
    public record CheckRow(
            String token, String name, String asked, String answered, String verdict, List<String> calls) {

        /** Rejects a missing part and copies the calls. */
        public CheckRow {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(asked, "asked");
            Objects.requireNonNull(answered, "answered");
            Objects.requireNonNull(verdict, "verdict");
            calls = List.copyOf(calls);
        }
    }

    /** Rejects a missing part and copies the metrics. */
    public TestDetail {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(requestLine, "requestLine");
        Objects.requireNonNull(curl, "curl");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(rawBody, "rawBody");
        Objects.requireNonNull(rawResponse, "rawResponse");
        metrics = List.copyOf(metrics);
        checks = List.copyOf(checks);
    }
}
