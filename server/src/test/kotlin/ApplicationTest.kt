package dev.pranav.bryte.server

import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.server.document.parser.file.FileParser
import dev.pranav.bryte.server.document.parser.web.WebpageParser
import dev.pranav.bryte.server.document.parser.youtube.YouTube
import dev.pranav.bryte.server.errors.BadRequestException
import dev.pranav.bryte.server.errors.ExternalServiceException
import dev.pranav.bryte.server.errors.ForbiddenException
import dev.pranav.bryte.server.errors.UnauthorizedException
import dev.pranav.bryte.server.plugins.jwkToECPublicKey
import dev.pranav.bryte.server.util.ext.getDocumentParser
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ApplicationTest {

    @Test
    fun testApiExceptionsMapToExpectedStatusCodes() {
        assertEquals(HttpStatusCode.BadRequest, BadRequestException("bad").statusCode)
        assertEquals(HttpStatusCode.Unauthorized, UnauthorizedException().statusCode)
        assertEquals(HttpStatusCode.Forbidden, ForbiddenException().statusCode)
        assertEquals(HttpStatusCode.BadGateway, ExternalServiceException("upstream").statusCode)
    }

    @Test
    fun testGetDocumentParserReturnsExpectedImplementations() {
        assertIs<FileParser>(getDocumentParser(DocumentType.PDF))
        assertIs<FileParser>(getDocumentParser(DocumentType.DOCX))
        assertIs<FileParser>(getDocumentParser(DocumentType.PPTX))
        assertIs<YouTube>(getDocumentParser(DocumentType.YOUTUBE))
        assertIs<WebpageParser>(getDocumentParser(DocumentType.WEBPAGE))

    }

    @Test
    fun testGetDocumentParserRejectsUnsupportedTypes() {
        assertFailsWith<BadRequestException> { getDocumentParser(DocumentType.EPUB) }
    }

    @Test
    fun testJwkToECPublicKeyBuildsEcPublicKey() {
        val publicKey = jwkToECPublicKey(
            mapOf(
                "kty" to "EC",
                "crv" to "P-256",
                "x" to JWK_X,
                "y" to JWK_Y
            )
        )

        assertEquals("EC", publicKey.algorithm)
    }

}
