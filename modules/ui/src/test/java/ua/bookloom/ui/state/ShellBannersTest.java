package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

/** The holder the pinned banner slot reads. */
class ShellBannersTest {

    private final ShellBanners banners = new ShellBanners();

    // IF a second publish under one key added a second node, THEN a banner would show twice.
    @Test
    void publish_sameKeyAgain_replacesInPlace() {
        final Region first = new Region();
        final Region second = new Region();
        final Region other = new Region();
        banners.publish("run", first);
        banners.publish("hidden-work", other);

        banners.publish("run", second);

        assertThat(banners.banners()).containsExactly(second, other);
    }

    // IF a withdrawn banner stayed, THEN the slot would keep a message that no longer applies.
    @Test
    void withdraw_publishedKey_removesOnlyThatBanner() {
        final Region run = new Region();
        final Region other = new Region();
        banners.publish("run", run);
        banners.publish("hidden-work", other);

        banners.withdraw("run");

        assertThat(banners.banners()).containsExactly(other);
    }

    @Test
    void withdraw_unknownKey_changesNothing() {
        banners.publish("run", new Region());

        banners.withdraw("nothing");

        assertThat(banners.banners()).hasSize(1);
    }
}
