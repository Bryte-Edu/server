package dev.pranav.bryte.server.util.ext

import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.server.document.DocParser
import dev.pranav.bryte.server.document.parser.file.FileParser
import dev.pranav.bryte.server.document.parser.web.WebpageParser
import dev.pranav.bryte.server.document.parser.youtube.YouTube
import dev.pranav.bryte.server.errors.BadRequestException

fun getDocumentParser(docType: DocumentType): DocParser<String> {
    return when (docType) {
        DocumentType.PDF, DocumentType.DOCX, DocumentType.PPTX -> FileParser()
        DocumentType.YOUTUBE -> YouTube()
        DocumentType.EPUB, DocumentType.WEBPAGE -> WebpageParser()
        else -> throw BadRequestException("Unsupported document type: $docType")
    }
}
