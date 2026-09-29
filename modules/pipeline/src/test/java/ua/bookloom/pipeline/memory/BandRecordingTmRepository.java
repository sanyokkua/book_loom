package ua.bookloom.pipeline.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.TmEntry;

/** A repository that answers through a real one and remembers the length band each candidate query asked for. */
@RequiredArgsConstructor
final class BandRecordingTmRepository implements TmRepository {

    private final TmRepository delegate;
    private final List<List<Integer>> bands = new ArrayList<>();

    List<List<Integer>> bands() {
        return List.copyOf(bands);
    }

    @Override
    public Result<TmEntry> put(final TmEntry entry) {
        return delegate.put(entry);
    }

    @Override
    public Result<List<TmEntry>> exact(final String projectId, final String sourceHash) {
        return delegate.exact(projectId, sourceHash);
    }

    @Override
    public Result<Optional<TmEntry>> context(final String projectId, final String sourceHash, final String contextKey) {
        return delegate.context(projectId, sourceHash, contextKey);
    }

    @Override
    public Result<List<TmEntry>> candidates(final String projectId, final int minChars, final int maxChars) {
        bands.add(List.of(minChars, maxChars));
        return delegate.candidates(projectId, minChars, maxChars);
    }
}
