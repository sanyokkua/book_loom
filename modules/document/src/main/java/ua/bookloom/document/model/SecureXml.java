package ua.bookloom.document.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdom2.input.SAXBuilder;

/**
 * A {@link SAXBuilder} configured to never resolve an external DTD or entity — offline invariant, not a style
 * preference. An XML parser that fetches a remote DTD makes a network call the offline invariant forbids, and is
 * exactly the XXE pattern SpotBugs/FindSecBugs flags unless every one of these is disabled.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecureXml {

    /**
     * A {@link SAXBuilder} that resolves no external DTD and no external entity.
     *
     * @return a freshly configured builder; never shared, because {@link SAXBuilder} is not thread-safe
     */
    public static SAXBuilder builder() {
        final SAXBuilder builder = new SAXBuilder();
        builder.setExpandEntities(false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return builder;
    }
}
