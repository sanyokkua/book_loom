package ua.bookloom.app.bootstrap;

import java.nio.file.Path;
import java.util.List;

/**
 * What {@link RunLogFiles} decided for a launch.
 *
 * @param file this launch's run log
 * @param failed names (or the directory) housekeeping could not list or delete; empty when all went well
 */
record PreparedRunLog(Path file, List<String> failed) {}
