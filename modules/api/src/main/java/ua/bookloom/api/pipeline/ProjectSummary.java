package ua.bookloom.api.pipeline;

import ua.bookloom.api.project.Project;

/**
 * One stored project and how much of it is decided.
 *
 * @param project the stored project
 * @param decidedSegments how many of its stored segments are not pending; {@code 0} when its source file is unreadable
 *     or its content changed since, because stored decisions of another text mean nothing for it
 */
public record ProjectSummary(Project project, int decidedSegments) {}
