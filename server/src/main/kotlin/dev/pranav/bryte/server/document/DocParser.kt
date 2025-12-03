package dev.pranav.bryte.server.document

/**
 * An interface for document parsers.
 *
 * @param Input The type of input the parser accepts.
 */
interface DocParser<Input> {
    /**
     * Parses the given input and returns a [ParsedDocument] if successful.
     *
     * @param input The input to be parsed.
     * @return The parsed document or null if parsing fails.
     */
    suspend fun parseDocument(input: Input): ParsedDocument?
}