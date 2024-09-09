package me.him188.ani.danmaku.server.util

import org.bson.types.Decimal128
import java.math.BigDecimal


fun BigDecimal.toDecimal128(): Decimal128 = Decimal128(this)


val ONE = Decimal128(1)
val ZERO: Decimal128 get() = Decimal128.POSITIVE_ZERO

fun Decimal128(value: String): Decimal128 = Decimal128(BigDecimal(value))

operator fun Decimal128.minus(other: Decimal128): Decimal128 =
    Decimal128(this.bigDecimalValue().subtract(other.bigDecimalValue()))

operator fun Decimal128.plus(other: Decimal128): Decimal128 =
    Decimal128(this.bigDecimalValue().add(other.bigDecimalValue()))

operator fun Decimal128.times(other: Decimal128): Decimal128 =
    Decimal128(this.bigDecimalValue().times(other.bigDecimalValue()))

operator fun Decimal128.div(other: Decimal128): Decimal128 =
    Decimal128(this.bigDecimalValue().div(other.bigDecimalValue()))

operator fun Decimal128.unaryMinus(): Decimal128 = Decimal128(this.bigDecimalValue().negate())
