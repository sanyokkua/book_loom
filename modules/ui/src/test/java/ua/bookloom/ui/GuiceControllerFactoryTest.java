package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.net.URL;
import java.util.Locale;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.i18n.Messages;

/**
 * Controllers are constructed by the injector, so a controller's constructor dependencies are the application's real
 * singletons. A test-only FXML and controller stand in for the screens that do not exist yet.
 */
class GuiceControllerFactoryTest extends ApplicationTest {

    private static final String FIXTURE = "/ua/bookloom/ui/fixture/guice-controller.fxml";

    private Injector injector;
    private GuiceControllerFactory factory;

    @Override
    public void start(final Stage stage) {
        // No window is needed; the toolkit only has to be running for the fixture's nodes to be constructed.
    }

    @BeforeEach
    void createFactory() {
        injector = UiTestInjector.create(Locale.ENGLISH);
        factory = injector.getInstance(GuiceControllerFactory.class);
    }

    private FXMLLoader loaderThroughTheFactory() {
        final URL url = GuiceControllerFactoryTest.class.getResource(FIXTURE);
        assertThat(url)
                .as("the test-only fixture must be on the test classpath")
                .isNotNull();
        final FXMLLoader loader =
                new FXMLLoader(url, injector.getInstance(Messages.class).bundle());
        loader.setControllerFactory(factory);
        return loader;
    }

    @Test
    void load_controllerNeedingAService_receivesTheInjectorsSingleton() throws Exception {
        // IF the loader built the controller itself, THEN it could not satisfy the Messages constructor at all.
        final FXMLLoader loader = loaderThroughTheFactory();
        final Parent root = loader.load();

        final GuiceFixtureController controller = loader.getController();
        assertThat(root).isNotNull();
        assertThat(controller.messages()).isSameAs(injector.getInstance(Messages.class));
    }

    @Test
    void load_controllerWithAnFxmlField_hasItInjectedAndItsKeyResolved() throws Exception {
        // IF Guice construction bypassed FXML injection, THEN the labelled field would be null.
        final FXMLLoader loader = loaderThroughTheFactory();
        loader.load();

        final GuiceFixtureController controller = loader.getController();
        assertThat(controller.greeting()).isNotNull();
        assertThat(controller.greeting().getText()).isEqualTo("Import book");
    }

    @Test
    void load_twice_buildsADistinctControllerEachTime() throws Exception {
        // IF controllers were singletons, THEN two screens of one kind would share their widgets.
        final FXMLLoader first = loaderThroughTheFactory();
        first.load();
        final FXMLLoader second = loaderThroughTheFactory();
        second.load();

        assertThat(second.<GuiceFixtureController>getController())
                .isNotSameAs(first.<GuiceFixtureController>getController());
    }

    @Test
    void call_aType_returnsWhatTheInjectorBuilds() {
        // IF the factory kept its own cache, THEN a controller could outlive the injector's scope rules.
        final Object built = factory.call(GuiceFixtureController.class);

        assertThat(built).isInstanceOf(GuiceFixtureController.class);
    }

    @Test
    void factory_requestedTwice_isTheSameSingleton() {
        // IF the factory were rebuilt per request, THEN it would not be the one sanctioned injector consumer.
        assertThat(injector.getInstance(GuiceControllerFactory.class)).isSameAs(factory);
    }
}
