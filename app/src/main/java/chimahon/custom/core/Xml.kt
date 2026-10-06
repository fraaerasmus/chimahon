package chimahon.custom.core

import javax.xml.parsers.DocumentBuilderFactory

/** An XML parser factory for documents from outside the app: no doctype, no external entities. */
fun hardenedDocumentBuilderFactory(namespaceAware: Boolean): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
        isValidating = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }
