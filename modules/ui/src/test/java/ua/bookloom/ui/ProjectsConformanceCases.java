package ua.bookloom.ui;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Preparation;
import ua.bookloom.ui.ConformanceCases.Screen;

/** The conformance cases of the Projects screen, with projects listed and with none; listed in {@link ConformanceCases}. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProjectsConformanceCases {

    static final List<Screen> SCREENS = List.of(
            // A book group is a card: surface fill, border line.
            new Screen(
                    "PROJECTS",
                    ViewNames.PROJECTS,
                    Preparation.PROJECTS_LISTED,
                    Overlay.NONE,
                    List.of(
                            new Part("#projects-screen .card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#projects-screen .card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "PROJECTS_EMPTY",
                    ViewNames.PROJECTS,
                    Preparation.PROJECTS_EMPTY,
                    Overlay.NONE,
                    List.of(
                            new Part("#projects-empty-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#projects-empty-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))));
}
