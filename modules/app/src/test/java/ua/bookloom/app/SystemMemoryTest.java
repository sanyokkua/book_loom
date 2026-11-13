package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemMemoryTest {

    @Test
    void totalBytes_thisComputer_isPositive() {
        assertThat(new SystemMemory().totalBytes()).isPositive();
    }
}
