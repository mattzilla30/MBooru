package com.mattzilla30.mbooru.data.model.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = AuthCredentialSerializer::class)
data class AuthCredential(val value: String) {
    var credentialReference: String? = null
    override fun toString() = "AuthCredential(redacted)"
}
object AuthCredentialSerializer : KSerializer<AuthCredential> {
    override val descriptor = PrimitiveSerialDescriptor("AuthCredential", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AuthCredential) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = AuthCredential(decoder.decodeString())
}
