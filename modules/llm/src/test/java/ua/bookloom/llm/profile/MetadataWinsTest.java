package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelIdentity;

class MetadataWinsTest {

    private final ModelIdentity named =
            new ModelIdentityParser().parse("gemma4:26b").identity();

    @Test
    void apply_metadataDisagreesWithName_metadataWins() {
        final ModelIdentity merged = new MetadataWins().apply(named, new MetadataWins.Facts(25.2, 3.8));

        assertThat(merged.sizeB()).isEqualTo(25.2);
        assertThat(merged.activeB()).isEqualTo(3.8);
        assertThat(merged.family()).isEqualTo("gemma");
    }

    @Test
    void apply_metadataSilent_keepsTheNameReading() {
        final ModelIdentity merged = new MetadataWins().apply(named, new MetadataWins.Facts(null, null));

        assertThat(merged).isSameAs(named);
    }
}
