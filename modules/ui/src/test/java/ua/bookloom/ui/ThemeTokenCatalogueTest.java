package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * The published token catalogue is carried whole, by its published names, with a light and a dark value each.
 *
 * <p>Everything expected here is written out from {@code 01_Product/09_THEMING.md#token-catalog} and
 * {@code #status-colours} (and, for the name set, read from the mockup's light block). Nothing is derived from
 * {@code theme.css}, so a wrong value or a missing role fails instead of agreeing with itself. The colour values
 * are asserted on what a real node resolves, not on the text of the stylesheet.
 */
class ThemeTokenCatalogueTest extends ApplicationTest {

    /** Set by {@code modules/ui/build.gradle.kts}, which also declares the mockup as an input of the test task. */
    private static final Path MOCKUP = Path.of(Objects.requireNonNull(
            System.getProperty("bookloom.mockup"), "system property bookloom.mockup is set by the :ui test task"));

    private static final Pattern COLOUR_DECLARATION = Pattern.compile("(?<![\\w-])-color-([a-z0-9-]+)\\s*:");
    private static final Pattern MOCKUP_LIGHT_BLOCK =
            Pattern.compile(":root\\[data-theme=\"light\"]\\s*\\{([^}]*)}", Pattern.DOTALL);
    private static final Pattern MOCKUP_DECLARATION = Pattern.compile("--([a-z0-9-]+)\\s*:");
    private static final Pattern STATUS_ROLE = Pattern.compile("(ok|warn|err|info)(-bg|-bd)?");

    /** The 41 roles that are looked-up colours, exactly as published. */
    private static final Set<String> COLOUR_ROLES = Set.of(
            "bg",
            "surface",
            "surface-2",
            "surface-alt",
            "border",
            "border-cool",
            "divider",
            "text",
            "text-strong",
            "muted",
            "muted-2",
            "primary",
            "primary-hover",
            "primary-press",
            "primary-fg",
            "primary-soft",
            "sand-soft",
            "sand-strong",
            "selected",
            "nav-bg",
            "nav-bg-2",
            "nav-fg",
            "nav-fg-muted",
            "nav-active-bg",
            "nav-active-fg",
            "nav-accent",
            "title-bg",
            "title-fg",
            "focus",
            "ok",
            "ok-bg",
            "ok-bd",
            "warn",
            "warn-bg",
            "warn-bd",
            "err",
            "err-bg",
            "err-bd",
            "info",
            "info-bg",
            "info-bd");

    /** The three elevation roles: published as roles, expressed as style classes rather than colours (D10). */
    private static final Set<String> ELEVATION_ROLES = Set.of("shadow-sm", "shadow", "shadow-lg");

    private static final Set<String> BRAND_ANCHORS =
            Set.of("brand-charcoal", "brand-slate", "brand-sand", "brand-cognac");

    // Assigned in start(), which ApplicationTest runs before every test.
    @SuppressWarnings("NullAway.Init")
    private Scene scene;

    @Override
    public void start(final Stage stage) {
        scene = ThemeTestSupport.themedScene();
        stage.setScene(scene);
        stage.show();
    }

    // ===== the role names ================================================================================

    // IF the stylesheet is read for its role names, THEN there are exactly 44: 41 colours plus 3 elevations.
    @Test
    void catalogue_declaredRoles_areExactlyFortyFour() {
        assertThat(catalogueNames()).hasSize(44);
    }

    // IF the 41 colour roles are counted on .root without the brand anchors, THEN there are exactly 41.
    @Test
    void catalogue_colourRolesOnRoot_areExactlyFortyOne() {
        assertThat(colourNames(rootBlock())).hasSize(41);
    }

    // IF the catalogue's roles are classified, THEN exactly 12 are status roles (4 statuses x 3 shades).
    @Test
    void catalogue_statusRoles_areTwelve() {
        assertThat(catalogueNames())
                .filteredOn(name -> STATUS_ROLE.matcher(name).matches())
                .hasSize(12);
    }

    // IF the catalogue is compared with the published role list, THEN they are the same 44 names.
    @Test
    void catalogue_names_equalThePublishedList() {
        final Set<String> published = new TreeSet<>(COLOUR_ROLES);
        published.addAll(ELEVATION_ROLES);

        assertThat(catalogueNames()).containsExactlyInAnyOrderElementsOf(published);
    }

    // IF the catalogue is compared with the mockup's light block, THEN there is no role in only one of them.
    @Test
    void catalogue_names_equalTheMockupLightBlockDeclarations() throws IOException {
        final Set<String> mockup = mockupLightNames();

        assertThat(mockup).as("the mockup's light block declares 44 roles").hasSize(44);
        assertThat(catalogueNames()).containsExactlyInAnyOrderElementsOf(mockup);
    }

    // IF the dark block is read, THEN it defines the same 41 colour names as the light block, no more, no fewer.
    @Test
    void darkBlock_colourNames_equalTheLightBlockNames() {
        assertThat(colourNames(darkBlock())).containsExactlyInAnyOrderElementsOf(colourNames(rootBlock()));
    }

    // IF the three shadow roles are looked for among the looked-up colours, THEN neither block declares them.
    @Test
    void catalogue_shadowRoles_areNotDeclaredAsLookedUpColours() {
        assertThat(colourNames(rootBlock())).doesNotContainAnyElementsOf(ELEVATION_ROLES);
        assertThat(colourNames(darkBlock())).doesNotContainAnyElementsOf(ELEVATION_ROLES);
    }

    // IF the .root block is read for the brand anchors, THEN exactly the four published ones are declared.
    @Test
    void catalogue_brandAnchors_areExactlyTheFourPublished() {
        assertThat(allDeclaredNames(rootBlock()))
                .filteredOn(name -> name.startsWith("brand-"))
                .containsExactlyInAnyOrderElementsOf(BRAND_ANCHORS);
    }

    // IF a role name is checked for a hue word, THEN only the brand anchors and the two published sand tints have one.
    @Test
    void catalogue_roleNames_neverNameAHueOutsideTheBrandAnchors() {
        final Set<String> allowedSandRoles = Set.of("brand-sand", "sand-soft", "sand-strong");

        assertThat(allDeclaredNames(rootBlock()))
                .filteredOn(name -> name.contains("cognac") || name.contains("slate") || name.contains("charcoal"))
                .as("cognac/slate/charcoal appear only in brand anchors")
                .allMatch(name -> name.startsWith("brand-"));
        assertThat(allDeclaredNames(rootBlock()))
                .filteredOn(name -> name.contains("sand"))
                .as("sand appears only in the brand anchor and the two published sand tints")
                .allMatch(allowedSandRoles::contains);
        assertThat(allDeclaredNames(darkBlock()))
                .as("the dark block redeclares no hue-named role")
                .noneMatch(name -> name.contains("cognac") || name.contains("slate") || name.contains("charcoal"));
    }

    // ===== the values ====================================================================================

    // IF an undefined role is looked up, THEN the probe reports nothing: the probe can fail, so a pass means a value.
    @Test
    void resolveRole_undefinedRole_resolvesToNothing() {
        assertThat(ThemeTestSupport.resolveRole(scene, "no-such-role")).isNull();
    }

    // IF a brand anchor is looked up, THEN it resolves to its published, theme-independent value in both blocks.
    @ParameterizedTest
    @CsvSource({
        "brand-charcoal, #3a4a52",
        "brand-slate, #b2babd",
        "brand-sand, #e7d6c0",
        "brand-cognac, #a58075",
    })
    void brandAnchor_underLightAndDark_resolvesToThePublishedValue(final String anchor, final String value) {
        ThemeTestSupport.applyTheme(scene, false);
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, anchor), value, anchor + " light");
        ThemeTestSupport.applyTheme(scene, true);
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, anchor), value, anchor + " dark");
    }

    // IF a role is looked up under the light block and then under the dark block, THEN it resolves to the value
    // the published table gives for that block (rgba written the way the table writes it).
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            bg            | #f4f1ea                 | #283237
            surface       | #ffffff                 | #33424a
            surface-2     | #f6f1e8                 | #2e3b41
            surface-alt   | #fbf9f4                 | #38474f
            border        | #ddd5c8                 | #48585f
            border-cool   | #cdd2d3                 | #48585f
            divider       | #eae4d8                 | #3c4a51
            text          | #2c3941                 | #e6ebed
            text-strong   | #22303a                 | #f4f7f8
            muted         | #6f7c82                 | #9fb0b7
            muted-2       | #95928a                 | #7f9098
            primary       | #a58075                 | #c4917e
            primary-hover | #916b60                 | #d4a593
            primary-press | #7e5b51                 | #b47e6b
            primary-fg    | #ffffff                 | #241c19
            primary-soft  | #f0e5dd                 | #3f3733
            sand-soft     | #f2e9db                 | #3a3a37
            sand-strong   | #dcc4a3                 | #6d5f4a
            selected      | #f0e6d6                 | rgba(231,214,192,.12)
            nav-bg        | #3a4a52                 | #20292e
            nav-bg-2      | #324148                 | #1c2429
            nav-fg        | #cdd5d8                 | #cdd6da
            nav-fg-muted  | #859399                 | #7d8f97
            nav-active-bg | rgba(231,214,192,.13)   | rgba(231,214,192,.10)
            nav-active-fg | #f3ede3                 | #f3ede3
            nav-accent    | #c99a86                 | #c99a86
            title-bg      | #324148                 | #1c2429
            title-fg      | #dfe4e6                 | #dfe4e6
            focus         | #a58075                 | #c4917e
            ok            | #5f8a6b                 | #7faa8a
            ok-bg         | #e7efe8                 | #2b3a33
            ok-bd         | #bcd4c1                 | #3f5a49
            warn          | #bd863a                 | #d0a25a
            warn-bg       | #f6ecd8                 | #3a3327
            warn-bd       | #e4cfa2                 | #5c4d31
            err           | #b0574c                 | #cc7a6f
            err-bg        | #f7e4df                 | #3b2b28
            err-bd        | #e4b6ad                 | #5e3f39
            info          | #4d6b78                 | #7a9dab
            info-bg       | #e5edf0                 | #293940
            info-bd       | #b7cbd2                 | #3d525b
            """)
    void role_underLightAndDark_resolvesToThePublishedValues(final String role, final String light, final String dark) {
        ThemeTestSupport.applyTheme(scene, false);
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, role), light, role + " under light");

        ThemeTestSupport.applyTheme(scene, true);
        ThemeTestSupport.assertSameColour(ThemeTestSupport.resolveRole(scene, role), dark, role + " under dark");
    }

    /** Guards the hard-coded lists themselves: 41 colours, 3 elevations, 12 statuses among them. */
    @Test
    void publishedLists_asTranscribed_haveTheSpecifiedSizes() {
        final List<String> statuses = COLOUR_ROLES.stream()
                .filter(name -> STATUS_ROLE.matcher(name).matches())
                .toList();

        assertThat(COLOUR_ROLES).hasSize(41);
        assertThat(ELEVATION_ROLES).hasSize(3);
        assertThat(statuses).hasSize(12);
    }

    // ===== helpers =======================================================================================

    private static Map<String, String> rules() {
        return ThemeTestSupport.rules(ThemeTestSupport.themeCss());
    }

    private static String rootBlock() {
        return rules().getOrDefault(".root", "");
    }

    private static String darkBlock() {
        return rules().getOrDefault(".root." + Theme.DARK_STYLE_CLASS, "");
    }

    private static Set<String> allDeclaredNames(final String block) {
        final Set<String> names = new TreeSet<>();
        final Matcher matcher = COLOUR_DECLARATION.matcher(block);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static Set<String> colourNames(final String block) {
        final Set<String> names = allDeclaredNames(block);
        names.removeIf(name -> name.startsWith("brand-"));
        return names;
    }

    /** The colour roles on {@code .root} plus one role per {@code .root .elevation*} class, by published name. */
    private static Set<String> catalogueNames() {
        final Set<String> names = new TreeSet<>(colourNames(rootBlock()));
        final Map<String, String> rules = rules();
        addIfDeclared(names, rules, ".root .elevation-sm", "shadow-sm");
        addIfDeclared(names, rules, ".root .elevation", "shadow");
        addIfDeclared(names, rules, ".root .elevation-lg", "shadow-lg");
        return names;
    }

    private static void addIfDeclared(
            final Set<String> names, final Map<String, String> rules, final String selector, final String role) {
        if (rules.containsKey(selector)) {
            names.add(role);
        }
    }

    private static Set<String> mockupLightNames() throws IOException {
        final Matcher block = MOCKUP_LIGHT_BLOCK.matcher(Files.readString(MOCKUP));
        assertThat(block.find()).as("the mockup has a light theme block").isTrue();
        final Set<String> names = new TreeSet<>();
        final Matcher declaration = MOCKUP_DECLARATION.matcher(block.group(1));
        while (declaration.find()) {
            names.add(declaration.group(1));
        }
        return names;
    }
}
