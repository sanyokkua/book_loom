package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ServerEngine;

/** The run log's profile line names the reasoning value a draft is actually sent. */
class ProfileLogTest {

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ProfileLog.class);

    @BeforeEach
    void attach() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logged);
    }

    // gpt-oss always reasons and is held at its lowest level, which is sent as "low", not left out.
    @Test
    void runStart_gptOss_namesTheLowLevelSent() {
        final String model = "gpt-oss:20b";
        final ResolvedProfile resolved = new ModelProfiles(new BuiltInRows())
                .resolve(ServerEngine.OLLAMA, model, ModelFacts.unknown(), ModelSettings.defaults("p", model));

        ProfileLog.runStart(model, resolved, null);

        assertThat(logged.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .contains(" reasoning=ALWAYS(low) ");
    }
}
