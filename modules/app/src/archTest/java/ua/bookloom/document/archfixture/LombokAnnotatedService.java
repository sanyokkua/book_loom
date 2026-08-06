package ua.bookloom.document.archfixture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NEGATIVE control for {@code records-first}: a Lombok-bearing service class outside every carrier package, which
 * the rule must NOT flag.
 *
 * <p>This is the accepted DD-05 / ADR-0014 hybrid — records for data, Lombok
 * {@code @RequiredArgsConstructor}/{@code @Slf4j}/{@code @Builder} on services — and it is also the most likely
 * way {@code records-first} could go wrong: a scope widened from {@code ..api..}/{@code ..dto..} to
 * {@code ua.bookloom..} would turn every service in the codebase red while still passing every positive test.
 */
@Slf4j
@RequiredArgsConstructor
public final class LombokAnnotatedService {

    private final String name;

    public String describe() {
        log.debug("describing {}", name);
        return name;
    }
}
