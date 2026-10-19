package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Stands in for the composition root's one binding {@link PipelineModule} needs and does not own: the review mode the
 * review desk reads. Tests that build an injector from the pipeline module alone install it beside it.
 */
public final class ReviewModeTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ReviewMode.class).toInstance(ReviewMode.UNATTENDED);
    }
}
