package ua.bookloom.pipeline;

import java.util.concurrent.atomic.AtomicInteger;
import ua.bookloom.api.project.IdSource;

/** A deterministic {@link IdSource} for tests: {@code p1}, {@code p2}, ... in the order they are asked for. */
public final class CountingIdSource implements IdSource {

    private final AtomicInteger issued = new AtomicInteger();

    @Override
    public String newId() {
        return "p" + issued.incrementAndGet();
    }

    /** How many ids have been handed out. */
    public int issued() {
        return issued.get();
    }
}
