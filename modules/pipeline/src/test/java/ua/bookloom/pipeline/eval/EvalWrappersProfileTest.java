package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;

/** An eval wrapper must show the run the wrapped model's profile, or the run starts its batches at the default size. */
class EvalWrappersProfileTest {

    private static final int SMALL_MODEL_BATCH = 4;

    private static ChatModel smallModel() {
        final ModelProfile base = ModelProfile.defaults();
        final ModelProfile small = new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                SMALL_MODEL_BATCH,
                base.tier());
        return new ChatModel() {
            @Override
            public Result<ChatResponse> chat(final ChatRequest request) {
                throw new UnsupportedOperationException("not called");
            }

            @Override
            public ModelProfile profile() {
                return small;
            }
        };
    }

    @Test
    void profile_stageProbe_isTheWrappedModelsProfile() {
        assertThat(new StageProbe(smallModel()).profile().batchSize()).isEqualTo(SMALL_MODEL_BATCH);
    }

    @Test
    void profile_sequenceFinishRecorder_isTheWrappedModelsProfile() {
        assertThat(new SequenceFinishRecorder(smallModel()).profile().batchSize())
                .isEqualTo(SMALL_MODEL_BATCH);
    }
}
