package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.LeaveDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;

/**
 * Asks before the person leaves a screen whose model work shows its result only there (a glossary scan or review, the
 * provider's inference test): stop it and leave, or leave and let it run on. Any other navigation passes at once.
 * FX thread only.
 */
@Slf4j
@Singleton
public final class LeaveGuard {

    private final ActivityTracker activities;
    private final LeaveDialog dialog;
    private final Messages messages;

    /**
     * Wires the guard to what runs and to the question it asks.
     *
     * @param activities the running work
     * @param dialog the question put to the person
     * @param messages the catalogue the work is named from
     */
    @Inject
    public LeaveGuard(final ActivityTracker activities, final LeaveDialog dialog, final Messages messages) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.dialog = Objects.requireNonNull(dialog, "dialog");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Lets a navigation away from {@code source} go ahead, or asks first.
     *
     * @param source the screen being left, or {@code null} before the first navigation
     * @param proceed what completes the navigation once the person chose to leave
     * @return {@code true} if the navigation may go ahead now, {@code false} if a question was asked instead
     */
    public boolean admits(final @Nullable ViewNames source, final Runnable proceed) {
        Objects.requireNonNull(proceed, "proceed");
        final Predicate<ActivityKind> leftBehind = kind -> kind.isLeaveSensitive() && kind.home() == source;
        final List<ActivityKind> sensitive = activities.running().stream()
                .map(ActivityTracker.Activity::kind)
                .filter(leftBehind)
                .toList();
        if (sensitive.isEmpty()) {
            return true;
        }
        log.debug("leaving {} would leave {} behind: asking first", source, sensitive);
        dialog.ask(
                messages.get(sensitive.getFirst().label()),
                () -> {
                    log.info("leaving {}: stopping {}", source, sensitive);
                    activities.stopWhere(leftBehind);
                    proceed.run();
                },
                () -> {
                    log.info("leaving {} with {} still running", source, sensitive);
                    proceed.run();
                });
        return false;
    }
}
