package me.him188.ani.danmaku.server.data.serialization

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bson.BsonDateTime
import org.bson.BsonDecimal128
import org.bson.BsonInt32
import org.bson.codecs.kotlinx.BsonDecoder
import org.bson.codecs.kotlinx.BsonEncoder
import org.bson.types.Decimal128
import java.util.*

object JavaInstantAsBsonDateTime : KSerializer<java.time.Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantAsBsonDateTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: java.time.Instant) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toEpochMilli()))
            else -> throw SerializationException("Instant is not supported by ${encoder::class}")
        }
    }

    override fun deserialize(decoder: Decoder): java.time.Instant {
        return when (decoder) {
            is BsonDecoder -> java.time.Instant.ofEpochMilli(decoder.decodeBsonValue().asDateTime().value)
            else -> throw SerializationException("Instant is not supported by ${decoder::class}")
        }
    }
}

object KotlinxInstantAsBsonDateTime : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantAsBsonDateTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toEpochMilliseconds()))
            else -> throw SerializationException("Instant is not supported by ${encoder::class}")
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        return when (decoder) {
            is BsonDecoder -> Instant.fromEpochMilliseconds(decoder.decodeBsonValue().asDateTime().value)
            else -> throw SerializationException("Instant is not supported by ${decoder::class}")
        }
    }
}

object DateAsBsonDateTime : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("InstantAsBsonDateTime", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Date) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDateTime(value.toInstant().toEpochMilli()))
            else -> throw SerializationException("Instant is not supported by ${encoder::class}")
        }
    }

    override fun deserialize(decoder: Decoder) = when (decoder) {
        is BsonDecoder -> Date.from(java.time.Instant.ofEpochMilli(decoder.decodeBsonValue().asDateTime().value))
        else -> throw SerializationException("Instant is not supported by ${decoder::class}")
    }
}

// 序列化为 Int32, 兼容旧db
object LongAsInt : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LongAsInt", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        return when (decoder) {
            is BsonDecoder -> {
                val value = decoder.decodeBsonValue()
                when {
                    value.isInt32 -> value.asInt32().value.toLong()
                    value.isInt64 -> value.asInt64().value
                    else -> throw SerializationException("Expected integer type, but found: ${value}}")
                }
            }

            else -> throw SerializationException("Long is not supported by ${decoder::class}")
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonInt32(value.toInt()))
            else -> throw SerializationException("Long is not supported by ${encoder::class}")
        }
    }

}

object BigDecimalAsBsonDecimal128 : KSerializer<java.math.BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimalAsDecimal128", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): java.math.BigDecimal {
        return when (decoder) {
            is BsonDecoder -> java.math.BigDecimal(decoder.decodeBsonValue().asDecimal128().value.toString())
            else -> throw SerializationException("BigDecimal is not supported by ${decoder::class}")
        }
    }

    override fun serialize(encoder: Encoder, value: java.math.BigDecimal) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDecimal128(Decimal128(value)))
            else -> throw SerializationException("BigDecimal is not supported by ${encoder::class}")
        }
    }
}

object Decimal128AsBsonDecimal128 : KSerializer<Decimal128> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimalAsDecimal128", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Decimal128 {
        return when (decoder) {
            is BsonDecoder -> decoder.decodeBsonValue().asDecimal128().value
            else -> throw SerializationException("BigDecimal is not supported by ${decoder::class}")
        }
    }

    override fun serialize(encoder: Encoder, value: Decimal128) {
        when (encoder) {
            is BsonEncoder -> encoder.encodeBsonValue(BsonDecimal128(value))
            else -> throw SerializationException("BigDecimal is not supported by ${encoder::class}")
        }
    }
}
