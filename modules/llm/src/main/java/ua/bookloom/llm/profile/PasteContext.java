package ua.bookloom.llm.profile;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the wrapping around a model id said (the command, the link, the router suffix) and the id left inside it;
 * mutable and confined to one parse.
 */
final class PasteContext {

    /** The id to read, with the wrapping taken off. */
    String text;

    @Nullable
    String inputForm;

    @Nullable
    String repo;

    @Nullable
    String revision;

    @Nullable
    String subfolder;

    @Nullable
    String serverAlias;

    @Nullable
    String alias;

    final List<String> routerVariants = new ArrayList<>();

    /** True when a link named a page that is not a model (a dataset, a space). */
    boolean notModel;

    PasteContext(final String text) {
        this.text = text;
    }
}
