package ua.bookloom.app;

import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.Result;

/** The restart binding until the window brings the real one: it logs the request and does nothing. */
@Slf4j
final class NoOpAppRestart implements AppRestart {

    @Override
    public Result<Boolean> restart() {
        log.debug("Restart requested; no restart is wired, nothing done");
        return Result.ok(false);
    }
}
