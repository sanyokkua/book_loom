package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.document.epub.NavigationParser.NavEntry;
import ua.bookloom.document.epub.NavigationParser.Source;
import ua.bookloom.document.model.RawEntry;

/**
 * {@link NavigationParser} (task 4.4): entry paths across siblings and nesting, and the NCX's own {@code
 * navPoint} id preserved — the parser task 6.3 reuses unchanged.
 */
class NavigationParserTest {

    private static final String OPF_HEADER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Nav Fixture</dc:title></metadata>
            """;

    private static Map<String, RawEntry> byName(RawEntry... entries) {
        final Map<String, RawEntry> byName = new LinkedHashMap<>();
        for (final RawEntry entry : entries) {
            byName.put(entry.name(), entry);
        }
        return byName;
    }

    private static RawEntry entry(String name, String content) {
        return new RawEntry(name, 0, 0, content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String NAV_OPF_XML = OPF_HEADER + """
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
                <item id="c02" href="c02.xhtml" media-type="application/xhtml+xml"/>
                <item id="c03" href="c03.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c01"/><itemref idref="c02"/><itemref idref="c03"/></spine>
            </package>
            """;

    private static final String NAV_XHTML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body>
              <nav epub:type="toc">
                <ol>
                  <li><a href="c01.xhtml">Chapter 1</a>
                    <ol><li><a href="c01.xhtml#s1">Section 1.1</a></li></ol>
                  </li>
                  <li><a href="c02.xhtml">Chapter 2</a></li>
                  <li><a href="c03.xhtml">Chapter 3</a></li>
                </ol>
              </nav>
            </body>
            </html>
            """;

    private static final String NCX_OPF_XML = OPF_HEADER + """
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c01" href="c01.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx"><itemref idref="c01"/></spine>
            </package>
            """;

    private static final String NCX_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="np-4"><navLabel><text>Preface</text></navLabel><content src="c01.xhtml"/></navPoint>
              </navMap>
            </ncx>
            """;

    @Test
    void parse_navThreeEntriesOneNestedChild_givesDottedEntryPaths() {
        final ParsedOpf opf = OpfParser.parse(NAV_OPF_XML.getBytes(StandardCharsets.UTF_8), "OEBPS/content.opf");
        final Map<String, RawEntry> byName = byName(entry("OEBPS/nav.xhtml", NAV_XHTML));

        final NavigationParser.Result result = NavigationParser.parse(opf, byName);

        assertThat(result.source()).isEqualTo(Source.NAV);
        final List<NavEntry> entries = result.entries();
        assertThat(entries).extracting(NavEntry::entryPath).containsExactly("1", "2", "3");
        assertThat(entries.get(0).children()).extracting(NavEntry::entryPath).containsExactly("1.1");
    }

    @Test
    void parse_ncxNavPoint_keepsOwnIdAndLabel() {
        final ParsedOpf opf = OpfParser.parse(NCX_OPF_XML.getBytes(StandardCharsets.UTF_8), "OEBPS/content.opf");
        final Map<String, RawEntry> byName = byName(entry("OEBPS/toc.ncx", NCX_XML));

        final NavigationParser.Result result = NavigationParser.parse(opf, byName);

        assertThat(result.source()).isEqualTo(Source.NCX);
        assertThat(result.entries()).hasSize(1);
        final NavEntry entry = result.entries().get(0);
        assertThat(entry.navPointId()).isEqualTo("np-4");
        assertThat(entry.label()).isEqualTo("Preface");
    }
}
