package dev.pranav.bryte.server.util.ext

import dev.pranav.bryte.server.document.DocParser
import dev.pranav.bryte.server.document.DocumentType
import dev.pranav.bryte.server.document.parser.file.FileParser
import dev.pranav.bryte.server.document.parser.youtube.YouTube
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

suspend fun RoutingContext.getDocumentParser(docType: DocumentType): DocParser<String> {
        return when (docType) {
            DocumentType.PDF, DocumentType.DOCX, DocumentType.PPTX -> FileParser()
            DocumentType.YOUTUBE -> YouTube()
            else -> call.respond(HttpStatusCode.BadRequest, "Unsupported document type").let { throw IllegalArgumentException("Unsupported document type") }
        }
    }