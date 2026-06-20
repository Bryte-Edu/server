package dev.pranav.bryte.server

import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.server.errors.BadRequestException
import dev.pranav.bryte.server.util.ext.getDocumentParser
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ParserExtensionsTest {

    @Test
    fun testUnsupportedDocumentTypeThrowsBadRequest() {
        assertFailsWith<BadRequestException> {
            getDocumentParser(DocumentType.EPUB)
        }
    }
}

