package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.Subscription;

/** Delivers job-thread events without sharing the job-control lock with callbacks. */
@Slf4j
final class JobSubscribers {

    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();

    Subscription add(final JobListener listener) {
        Objects.requireNonNull(listener, "listener");
        final Registration registration = new Registration(listener);
        registrations.add(registration);
        log.debug("Added job subscriber count={}", registrations.size());
        return () -> remove(registration);
    }

    void deliver(final JobEvent event) {
        Objects.requireNonNull(event, "event");
        log.debug(
                "Delivering job event type={} subscribers={}", event.getClass().getSimpleName(), registrations.size());
        for (final Registration registration : registrations) {
            deliverOne(event, registration);
        }
    }

    private void deliverOne(final JobEvent event, final Registration registration) {
        try {
            registration.listener().onEvent(event);
        } catch (Exception cause) {
            registrations.remove(registration);
            log.warn(
                    "Removing subscriber that threw while handling {}",
                    event.getClass().getSimpleName(),
                    cause);
        }
    }

    private void remove(final Registration registration) {
        final boolean removed = registrations.remove(registration);
        log.debug("Removed job subscriber removed={} count={}", removed, registrations.size());
    }

    // Identity is what makes two subscriptions of one listener independent: each handle removes only its own.
    private record Registration(JobListener listener) {
        @Override
        public boolean equals(final Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
