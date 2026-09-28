package ua.bookloom.document.epub;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Rewrites the language a translated EPUB declares (task 4.6, design.md D14 §1): the package's {@code dc:language},
 * its {@code dcterms:language} meta and {@code xml:lang}, and the {@code html}/{@code body} language attributes of
 * every spine document and of the navigation document outside the spine.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EpubLanguageRewriter {

    private static final Namespace OPF_NS = Namespace.getNamespace("http://www.idpf.org/2007/opf");
    // A prefixed namespace, not Namespace.getNamespace(uri) alone (which chooses no-prefix/default): an appended
    // <language> element must serialize as <dc:language>, not <language xmlns="…">, however Namespace.equals
    // (URI-only) still matches an existing dc:-prefixed element when reading (task 4.6, the reported bug).
    private static final Namespace DC_NS = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/");
    private static final String LANGUAGE_ELEMENT_NAME = "language";
    private static final String LEGACY_DC_METADATA_ELEMENT_NAME = "dc-metadata";
    private static final String DCTERMS_LANGUAGE_META = "dcterms:language";

    /**
     * Replaces the first {@code dc:language} in the OPF's metadata with {@code targetLanguage} — found directly
     * under {@code metadata} or, failing that, nested inside a legacy {@code dc-metadata} wrapper — adding one
     * (with its {@code dc:} prefix) when none is present anywhere, then rewrites every other language-carrying
     * attribute the spec names wherever its value equals the effective source language (task 4.6, design.md D14
     * §1): the {@code dcterms:language} meta, the package's own {@code xml:lang}, and each XHTML spine content
     * document's {@code html}/{@code body} {@code xml:lang}/{@code lang}. The navigation document is out of this
     * task's scope (task 6.3 continues it). The effective source language is {@code sourceLanguage} when given,
     * else the package's own {@code dc:language} value read <strong>before</strong> the replacement above.
     */
    static void rewrite(
            ParsedEpub parsed,
            Document document,
            @Nullable String sourceLanguage,
            String targetLanguage,
            Set<String> changedResources) {
        final org.jdom2.Document opfDocument = parsed.opfDocument();
        final Element metadata = opfDocument.getRootElement().getChild("metadata", OPF_NS);
        if (metadata == null) {
            throw new CorruptContainerException("OPF has no <metadata> element");
        }
        final Optional<Element> languageElement = findLanguageElement(metadata);
        final String declaredLanguage = languageElement.map(Element::getText).orElse(null);
        final String effectiveSource = sourceLanguage != null ? sourceLanguage : declaredLanguage;
        log.debug(
                "Rewriting EPUB languages passedSource={} declaredSource={} effectiveSource={} target={}",
                sourceLanguage,
                declaredLanguage,
                effectiveSource,
                targetLanguage);

        replaceOrAppendLanguageElement(metadata, languageElement, targetLanguage);
        rewriteDctermsLanguageMeta(metadata, effectiveSource, targetLanguage);
        rewritePackageLangAttribute(opfDocument.getRootElement(), effectiveSource, targetLanguage);
        rewriteContentDocumentLanguages(parsed, document, effectiveSource, targetLanguage, changedResources);
    }

    private static Optional<Element> findLanguageElement(Element metadata) {
        final List<Element> direct = metadata.getChildren(LANGUAGE_ELEMENT_NAME, DC_NS);
        if (!direct.isEmpty()) {
            return Optional.of(direct.get(0));
        }
        for (final Element child : metadata.getChildren()) {
            if (LEGACY_DC_METADATA_ELEMENT_NAME.equals(child.getName())) {
                final List<Element> nested = child.getChildren(LANGUAGE_ELEMENT_NAME, DC_NS);
                if (!nested.isEmpty()) {
                    return Optional.of(nested.get(0));
                }
            }
        }
        return Optional.empty();
    }

    private static void replaceOrAppendLanguageElement(
            Element metadata, Optional<Element> languageElement, String targetLanguage) {
        if (languageElement.isPresent()) {
            languageElement.get().setText(targetLanguage);
            log.debug("Replaced existing dc:language nested={}", isNestedInLegacyWrapper(languageElement.get()));
            return;
        }
        metadata.addContent(new Element(LANGUAGE_ELEMENT_NAME, DC_NS).setText(targetLanguage));
        log.debug("Appended a missing dc:language element target={}", targetLanguage);
    }

    private static boolean isNestedInLegacyWrapper(Element languageElement) {
        final Element parent = languageElement.getParentElement();
        return parent != null && LEGACY_DC_METADATA_ELEMENT_NAME.equals(parent.getName());
    }

    private static void rewriteDctermsLanguageMeta(Element metadata, @Nullable String effectiveSource, String target) {
        for (final Element meta : metadata.getChildren("meta", OPF_NS)) {
            if (DCTERMS_LANGUAGE_META.equals(meta.getAttributeValue("property"))
                    && sameLanguage(effectiveSource, meta.getText())) {
                log.debug("Rewrote dcterms:language meta from={} to={}", meta.getText(), target);
                meta.setText(target);
            }
        }
    }

    private static void rewritePackageLangAttribute(
            Element packageElement, @Nullable String effectiveSource, String target) {
        final org.jdom2.Attribute langAttribute = packageElement.getAttribute("lang", Namespace.XML_NAMESPACE);
        if (langAttribute != null && sameLanguage(effectiveSource, langAttribute.getValue())) {
            log.debug("Rewrote package xml:lang from={} to={}", langAttribute.getValue(), target);
            langAttribute.setValue(target);
        }
    }

    private static void rewriteContentDocumentLanguages(
            ParsedEpub parsed,
            Document document,
            @Nullable String effectiveSource,
            String target,
            Set<String> changedResources) {
        for (final Unit unit : document.units()) {
            if (!unit.isAuxiliary()) {
                rewriteRootLanguages(EpubWriter.treeFor(parsed, unit), effectiveSource, target);
            }
        }
        final org.jsoup.nodes.Document navigation = parsed.navigation().navTree();
        final String navPath = parsed.navigation().navPath();
        if (navigation != null && navPath != null && rewriteRootLanguages(navigation, effectiveSource, target)) {
            log.debug("navigation document {} follows the target language", navPath);
            changedResources.add(navPath);
        }
    }

    /** Rewrites the {@code html} and {@code body} language attributes; reports whether any value changed. */
    private static boolean rewriteRootLanguages(
            org.jsoup.nodes.Document tree, @Nullable String effectiveSource, String target) {
        final org.jsoup.nodes.Element html = tree.selectFirst("html");
        if (html == null) {
            return false;
        }
        final boolean htmlChanged = rewriteJsoupLangAttributes(html, effectiveSource, target, "html");
        final boolean bodyChanged = rewriteJsoupLangAttributes(tree.body(), effectiveSource, target, "body");
        return htmlChanged || bodyChanged;
    }

    private static boolean rewriteJsoupLangAttributes(
            org.jsoup.nodes.Element element, @Nullable String effectiveSource, String target, String elementName) {
        final boolean xmlLang = rewriteJsoupAttribute(element, "xml:lang", effectiveSource, target, elementName);
        final boolean lang = rewriteJsoupAttribute(element, "lang", effectiveSource, target, elementName);
        return xmlLang || lang;
    }

    private static boolean rewriteJsoupAttribute(
            org.jsoup.nodes.Element element,
            String attributeName,
            @Nullable String effectiveSource,
            String target,
            String elementName) {
        if (element.hasAttr(attributeName) && sameLanguage(effectiveSource, element.attr(attributeName))) {
            log.debug("Rewrote {} {} from={} to={}", elementName, attributeName, element.attr(attributeName), target);
            element.attr(attributeName, target);
            return true;
        }
        return false;
    }

    /**
     * Compares a language value against the effective source language after {@link LanguageTags#normalize}, so
     * {@code en-US} and {@code en} match; falls back to a direct case-insensitive comparison of the raw values when
     * either side names no catalogued language (e.g. {@code la}), so two uncatalogued tags are never spuriously
     * treated as equal to each other or to the source merely because both fail to normalize.
     */
    private static boolean sameLanguage(@Nullable String effectiveSource, @Nullable String candidate) {
        if (effectiveSource == null || candidate == null) {
            return false;
        }
        final Optional<String> normalizedSource = LanguageTags.normalize(effectiveSource);
        final Optional<String> normalizedCandidate = LanguageTags.normalize(candidate);
        if (normalizedSource.isPresent() && normalizedCandidate.isPresent()) {
            return normalizedSource.get().equals(normalizedCandidate.get());
        }
        return effectiveSource.equalsIgnoreCase(candidate);
    }
}
