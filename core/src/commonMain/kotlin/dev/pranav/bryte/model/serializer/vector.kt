package dev.pranav.bryte.model.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

object VectorSerializer : KSerializer<List<Double>?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Vector", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<Double>? {
        val stringValue = decoder.decodeString()

        // Handle empty string or whitespace as null
        if (stringValue.isBlank()) {
            return null
        }

        return try {
            Json.decodeFromString(ListSerializer(Double.serializer()), stringValue)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
    }

    override fun serialize(encoder: Encoder, value: List<Double>?) {
        if (value == null) {
            encoder.encodeString("")
        } else {
            val stringValue = Json.encodeToString(ListSerializer(Double.serializer()), value)
            encoder.encodeString(stringValue)
        }
    }
}