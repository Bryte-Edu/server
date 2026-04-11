package dev.pranav.bryte.server.errors

import io.ktor.http.*

open class ApiException(
    val statusCode: HttpStatusCode,
    override val message: String
) : RuntimeException(message)

class BadRequestException(message: String) : ApiException(HttpStatusCode.BadRequest, message)

class UnauthorizedException(message: String = "Unauthorized") : ApiException(HttpStatusCode.Unauthorized, message)

class ForbiddenException(message: String = "Forbidden") : ApiException(HttpStatusCode.Forbidden, message)

class ExternalServiceException(message: String) : ApiException(HttpStatusCode.BadGateway, message)

