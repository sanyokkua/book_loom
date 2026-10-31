package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading a pasted link or command must stay offline. A real listener on loopback would see any connection a reader
 * opened; a command that would leave a file behind shows whether anything ran it.
 */
class PastedTextIsNeverActedOnTest {

    private static final int WAIT_MILLIS = 300;

    @TempDir
    Path folder;

    @Test
    void readPasted_linkToAListeningServer_opensNoConnection() throws IOException {
        try (ServerSocket listener = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(WAIT_MILLIS);
            final String link =
                    "http://127.0.0.1:" + listener.getLocalPort() + "/unsloth/Qwen3-8B-GGUF?show_file_info=a.gguf";
            final String hfLink = "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/main/a.gguf";

            new ModelInputNormalizer().normalise(link);
            new ModelInputNormalizer().normalise(hfLink);
            new ModelIdentityParser().parse(link);

            assertThatThrownBy(listener::accept).isInstanceOf(SocketTimeoutException.class);
        }
    }

    @Test
    void readPasted_commandWithAnInjectedSecondCommand_runsNothing() {
        final Path marker = folder.resolve("ran.txt");
        final String command = "ollama run gemma4:e4b; touch " + marker + " && lms get x";

        new ModelInputNormalizer().normalise(command);
        new ModelIdentityParser().parse(command);

        assertThat(marker).doesNotExist();
    }
}
