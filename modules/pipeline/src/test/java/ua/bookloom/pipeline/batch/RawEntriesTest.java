package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Complete id and target objects are found in a cut reply, notes allowed, and long targets are read without recursion. */
class RawEntriesTest {

    @Test
    void find_cutReply_givesTheCompleteObjectsOnly() {
        final String reply = "{\"items\":[{\"id\":\"1\",\"target\":\"One\"},{ \"id\" : 2 , \"notes\" : \"n\\\"q\" , "
                + "\"target\" : \"Two \\\"x\\\"\" },{\"id\":\"3\",\"target\":\"Cut";

        assertThat(RawEntries.find(reply))
                .containsExactly(new RawEntries.Raw("1", "One"), new RawEntries.Raw("2", "Two \\\"x\\\""));
    }

    @Test
    void find_brokenShapes_giveNothing() {
        assertThat(RawEntries.find("{\"id\":\"1\",\"target\":1}")).isEmpty();
        assertThat(RawEntries.find("{\"id\":\"1\" \"target\":\"x\"}")).isEmpty();
        assertThat(RawEntries.find("{\"id\":\"1\",\"notes\":\"open,\"target\":\"x\"}"))
                .isEmpty();
        assertThat(RawEntries.find("{\"id\":\"1\",\"target\":\"a\\")).isEqualTo(List.of());
    }

    @Test
    void find_veryLongTarget_isReadWithoutRecursion() {
        final String target = "phrase ".repeat(200_000);
        final String reply = "{\"id\":\"1\",\"target\":\"" + target + "\"}";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(RawEntries.find(reply)).containsExactly(new RawEntries.Raw("1", target)));
    }

    @Test
    void find_manyHeadsWithoutTarget_isLinear() {
        final String reply = "{\"id\":\"1\",".repeat(100_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThat(RawEntries.find(reply)).isEmpty());
    }
}
