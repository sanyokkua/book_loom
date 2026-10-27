package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

class AppErrorsTest {

    // IF the thrown text reached the details, THEN a secret or a book's text inside it could be shown or copied.
    @Test
    void unexpected_causeWithText_keepsTheCauseButNoDetails() {
        final IllegalStateException cause = new IllegalStateException("token=abc123");

        final AppError error = AppErrors.unexpected("The run stopped because of an unexpected error.", cause);

        assertThat(error.code()).isEqualTo(ErrorCode.internal);
        assertThat(error.title()).isEqualTo("Unexpected error");
        assertThat(error.message()).isEqualTo("The run stopped because of an unexpected error.");
        assertThat(error.details()).isNull();
        assertThat(error.cause()).isSameAs(cause);
    }
}
