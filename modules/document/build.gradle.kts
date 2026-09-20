// :document — parse/mask/reassemble EPUB, FB2, Markdown and TXT. FX-free.
//
// `implementation` rather than `api` throughout: nothing here is part of an exported surface another module
// compiles against, so no transitive leaks downstream (05_Dependencies/02_DEPENDENCY_POLICY.md#narrowest-scope).

plugins {
    id("bookloom.java-conventions")
    id("bookloom.spotless-conventions")
    id("bookloom.test-conventions")
    id("bookloom.coverage-conventions")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":util"))
    implementation(libs.guice)

    // The SLF4J facade, used through Lombok's `@Slf4j` by `DocumentService` — the port boundary that first
    // classifies and logs a failure (`.claude/rules/logging.md`).
    implementation(libs.slf4j.api)

    // EPUB read/write (design.md D4): jsoup for XHTML content bodies, JDOM2 for the OPF/container XML and FB2.
    implementation(libs.jsoup)
    implementation(libs.jdom2)

    // Markdown analysis (design.md D4). The AST is used to locate each leaf block's byte span; the renderer is
    // never invoked, because export splices translated spans into a copy of the original bytes. GFM tables is the
    // only extension added — tables are block-level and change segmentation, while strikethrough and autolink are
    // inline and change no block boundary.
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)

    // Charset detection on the import path (01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom). Declared here
    // and nowhere else: `:document` may depend only on `:api` and `:util`, so it cannot borrow `:pipeline`'s copy.
    implementation(libs.icu4j)

    testRuntimeOnly(libs.logback.classic)
}
