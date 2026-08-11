package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.DocumentPort;

/**
 * Proves Guice can actually build the port.
 *
 * <p>This exists because the failure it catches is a <strong>runtime</strong> one. {@code DocumentService} now
 * takes eight collaborators across four packages, each reached by an implicit binding, and Guice can only
 * construct a class whose package is {@code opens} to it and whose constructor it can see. A missing {@code opens}
 * or a service without an {@code @Inject} constructor compiles perfectly and fails when a user opens their first
 * book — so a compile-clean build is not evidence that the graph resolves.
 */
class DocumentModuleTest {

    @Test
    void configure_documentModule_buildsAPortWithEveryFormatWired() {
        final Injector injector = Guice.createInjector(new DocumentModule());

        final DocumentPort port = injector.getInstance(DocumentPort.class);

        assertThat(port).isInstanceOf(DocumentService.class);
    }

    /**
     * A reader and its writer must share one registry, or a document opened through the port could never be found
     * again to write. Each registry is a Guice singleton, and this asserts that rather than trusting it.
     */
    @Test
    void configure_openDocumentRegistries_areSingletonsSharedBetweenReaderAndWriter() {
        final Injector injector = Guice.createInjector(new DocumentModule());

        assertThat(injector.getInstance(ua.bookloom.document.fb2.OpenFb2Registry.class))
                .isSameAs(injector.getInstance(ua.bookloom.document.fb2.OpenFb2Registry.class));
        assertThat(injector.getInstance(ua.bookloom.document.md.OpenMarkdownRegistry.class))
                .isSameAs(injector.getInstance(ua.bookloom.document.md.OpenMarkdownRegistry.class));
        assertThat(injector.getInstance(ua.bookloom.document.txt.OpenTxtRegistry.class))
                .isSameAs(injector.getInstance(ua.bookloom.document.txt.OpenTxtRegistry.class));
    }
}
