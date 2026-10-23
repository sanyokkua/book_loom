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
        String rawResponse) {

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
    }
}
