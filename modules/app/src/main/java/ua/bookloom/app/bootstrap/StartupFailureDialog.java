package ua.bookloom.app.bootstrap;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import ua.bookloom.api.AppError;

/**
 * The one surface for every failure that happens before logging exists.
 *
 * <p>Three things can go wrong before there is anything to log to and before any window exists: the directories
 * cannot be created (EC-ENV-2), another instance already holds the lock (EC-ENV-3), and no usable home directory
 * exists at all (EC-ENV-7). Each has to reach the user, and none of them can be written to a file — so all three
 * share one mechanism rather than accumulating three.
 *
 * <p><strong>JavaFX, not Swing.</strong> {@link Platform#startup} boots only the toolkit, without the
 * {@code Application} lifecycle, which is what makes it usable this early. The alternative,
 * {@code javax.swing.JOptionPane}, would drag {@code java.desktop} into the jlink image for the sake of one dialog
 * — working directly against the {@code --strip-debug --no-header-files --no-man-pages --compress zip-6} trim the
 * packaging scripts exist to apply — and would put a second UI toolkit in a project whose UI rules are written
 * entirely about the first.
 *
 * <p><strong>When the toolkit itself will not start</strong>, the process exits with the given status and emits
 * nothing at all: no {@code System.err} line, no crash file. A terminal line is invisible to someone who
 * double-clicked a packaged application, and a fallback write path would be new I/O in the one code region that has
 * just demonstrated it cannot write anywhere. The window is narrow in practice — of the three cases, only EC-ENV-7
 * is genuinely hostile to booting a toolkit; the other two have a working home directory and show the dialog
 * normally.
 */
final class StartupFailureDialog {

    /** How long to wait for the user to dismiss the dialog before giving up and exiting anyway. */
    private static final long MAX_WAIT_SECONDS = 300L;

    private static final double CONTENT_WIDTH = 360;
    private static final double PADDING = 20;
    private static final double SPACING = 12;
    private static final double HEADING_FONT_SIZE = 14;

    private StartupFailureDialog() {
        // Static entry point only.
    }

    /**
     * Shows the failure and terminates the process.
     *
     * @param error the failure to report; its title and message are user-facing, its details and cause are not
     * @param exitCode {@code 0} for the deliberate second-launch refusal, non-zero for a real failure
     */
    static void showAndExit(AppError error, int exitCode) {
        Objects.requireNonNull(error, "error");
        try {
            showBlocking(error);
        } catch (RuntimeException e) {
            // The toolkit could not start — see the class comment. Say nothing: there is nowhere to say it that a
            // packaged-application user would ever look, and inventing a channel would mean new I/O on a machine
            // that has just demonstrated it has none.
        }
        terminate(exitCode);
    }

    /**
     * Shows the failure in a plain {@link Stage}, and waits for the user to dismiss it.
     *
     * <p><strong>Not an {@code Alert}, and this was learned the hard way.</strong> {@code Alert} and {@code Dialog}
     * assume a running {@code Application} context; shown from a bare {@link Platform#startup} runnable with no
     * owner window, the stage appears with the right <em>title</em> and a completely blank body — no header, no
     * message, no button to dismiss it with. It fails in the worst possible shape: enough of a window to look
     * deliberate, with none of the information that would tell the user what happened or let them close it.
     *
     * <p>A {@code Stage} with an explicit {@code Scene} makes no such assumption. The cost is building the layout by
     * hand; the benefit is that the one surface every pre-logging failure shares actually renders.
     */
    private static void showBlocking(AppError error) {
        final CountDownLatch dismissed = new CountDownLatch(1);
        Platform.startup(() -> {
            final Stage stage = new Stage();
            stage.setTitle(error.title());
            stage.setScene(new Scene(content(error, stage)));
            stage.setResizable(false);
            // Dismissing via the window's close button must count as dismissal too, or a user who closes it that
            // way leaves the process waiting out the full timeout for a window that is no longer there.
            stage.setOnHidden(event -> dismissed.countDown());
            stage.show();
            stage.toFront();
        });
        awaitDismissal(dismissed);
        Platform.exit();
    }

    /**
     * Builds the message and its dismiss button.
     *
     * <p>Only {@code title} and {@code message} are shown. {@code details} is allowlisted technical text meant for a
     * log or an expandable panel that does not exist yet, and {@code cause} is internal-only by contract — neither
     * belongs in front of a user who has just been told the application will not start.
     *
     * <p>Styled inline rather than through the token stylesheet, and this is the one place in the project where that
     * is correct: {@code theme.css} lives in {@code :ui}, and this dialog exists precisely for failures that occur
     * before the application — and therefore before anything in {@code :ui} — has loaded.
     */
    private static Parent content(AppError error, Stage stage) {
        final Label heading = new Label(error.title());
        heading.setFont(Font.font(heading.getFont().getFamily(), FontWeight.BOLD, HEADING_FONT_SIZE));

        final Label message = new Label(error.message());
        message.setWrapText(true);
        message.setMaxWidth(CONTENT_WIDTH);

        final Button dismiss = new Button("OK");
        dismiss.setDefaultButton(true);
        dismiss.setOnAction(event -> stage.close());

        final HBox buttons = new HBox(dismiss);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        final VBox root = new VBox(SPACING, heading, message, buttons);
        root.setPadding(new Insets(PADDING));
        root.setPrefWidth(CONTENT_WIDTH + 2 * PADDING);
        return root;
    }

    private static void awaitDismissal(CountDownLatch dismissed) {
        try {
            // Bounded rather than indefinite: a dialog nobody is present to dismiss must not leave a process
            // running forever holding nothing.
            if (!dismissed.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS)) {
                Platform.exit();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void terminate(int exitCode) {
        // Deciding that the process does not continue is a launcher's job, and this is the one place in the
        // application where that is true. Every other failure path returns a Result.
        System.exit(exitCode);
    }
}
