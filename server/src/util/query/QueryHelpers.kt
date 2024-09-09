@file:Suppress(
    "NOTHING_TO_INLINE",
    "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE" // 让 `value` 必须兼容 property 类型
)

package me.him188.ani.danmaku.server.util.query

import com.mongodb.client.model.*
import com.mongodb.client.model.mql.MqlBoolean
import com.mongodb.client.model.mql.MqlDocument
import com.mongodb.client.model.mql.MqlValue
import com.mongodb.client.model.mql.MqlValues
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.AggregateFlow
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.danmaku.server.util.toDecimal128
import org.bson.BsonDocument
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.Decimal128
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


fun convertToBson(value: Any?): Any? {
    if (value is JsonElement) return value.toBsonDocument()
    return value
}

interface BsonExtensions {
    infix fun Bson.and(other: Bson?): Bson {
        if (other == null) return this
        return Filters.and(this, other)
    }

    infix fun Bson.or(other: Bson): Bson {
        return Filters.or(this, other)
    }
}

open class BsonUpdateScope : BsonExtensions {
    @PublishedApi
    internal val sets: MutableList<Bson> = mutableListOf()

    // inline property to get compiler optimization

    inline fun <T> KProperty<List<T>>.add(value: @kotlin.internal.NoInfer T): List<Bson> {
        sets.add(Updates.addToSet(this.name, convertToBson(value)))
        return sets
    }

    inline fun <T> set(name: KProperty<T>, value: @kotlin.internal.NoInfer T): List<Bson> {
        sets.add(Updates.set(name.name, convertToBson(value)))
        return sets
    }

    inline fun <T> setOnInsert(name: KProperty<T>, value: @kotlin.internal.NoInfer T): List<Bson> {
        sets.add(Updates.setOnInsert(name.name, convertToBson(value)))
        return sets
    }

    inline infix fun <T> KProperty<T>.setTo(value: @kotlin.internal.NoInfer T): List<Bson> {
        sets.add(Updates.set(name, convertToBson(value)))
        return sets
    }

    inline operator fun <T : Number> KProperty<T>.plusAssign(value: @kotlin.internal.NoInfer T) {
        sets.add(Updates.inc(name, value))
    }

    inline operator fun KProperty<Int>.minusAssign(value: Int) {
        sets.add(Updates.inc(name, -value))
    }

    inline operator fun KProperty<Long>.minusAssign(value: Long) {
        sets.add(Updates.inc(name, -value))
    }

    inline operator fun KProperty<Float>.minusAssign(value: Float) {
        sets.add(Updates.inc(name, -value))
    }

    inline operator fun KProperty<Double>.minusAssign(value: Double) {
        sets.add(Updates.inc(name, -value))
    }

    inline fun <T> setIfNotNull(name: KProperty<T>, value: @kotlin.internal.NoInfer T?): List<Bson> {
        value ?: return sets
        sets.add(Updates.set(name.name, convertToBson(value)))
        return sets
    }
}

@Suppress("INAPPLICABLE_JVM_NAME")
interface BsonFilterScope : BsonExtensions {
    infix fun KProperty<String?>.matches(regex: String): Bson {
        return Filters.regex(this.name, regex)
    }

    @kotlin.internal.LowPriorityInOverloadResolution
    @JvmName("eqNullable")
    infix fun <TItem> KProperty<TItem>.eq(value: @kotlin.internal.NoInfer TItem?): Bson {
        return Filters.eq(this.name, convertToBson(value))
    }

    infix fun <TItem> KProperty<TItem>.eq(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.eq(this.name, convertToBson(value))
    }

    infix fun <TItem> KProperty<TItem>.neq(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.not(Filters.eq(this.name, convertToBson(value)))
    }

    infix fun <TItem> KProperty<TItem>.`in`(values: Iterable<@kotlin.internal.NoInfer TItem>): Bson {
        return Filters.`in`(this.name, values)
    }

    infix fun <TItem : Comparable<TItem>> KProperty<TItem>.gt(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.gt(this.name, value)
    }

    infix fun <TItem : Comparable<TItem>> KProperty<TItem>.gte(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.gte(this.name, value)
    }

    infix fun <TItem : Comparable<TItem>> KProperty<TItem>.lt(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.lt(this.name, value)
    }

    infix fun <TItem : Comparable<TItem>> KProperty<TItem>.lte(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.lte(this.name, value)

    }

    @JvmName("notNullGt")
    infix fun <TItem : Comparable<TItem>> KProperty<TItem?>.gt(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.and(Filters.exists(this.name, true), Filters.gt(this.name, value))
    }

    @JvmName("notNullGte")
    infix fun <TItem : Comparable<TItem>> KProperty<TItem?>.gte(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.and(Filters.exists(this.name, true), Filters.gte(this.name, value))
    }

    @JvmName("notNullLt")
    infix fun <TItem : Comparable<TItem>> KProperty<TItem?>.lt(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.and(Filters.exists(this.name, true), Filters.lt(this.name, value))
    }

    @JvmName("notNullLte")
    infix fun <TItem : Comparable<TItem>> KProperty<TItem?>.lte(value: @kotlin.internal.NoInfer TItem): Bson {
        return Filters.and(Filters.exists(this.name, true), Filters.lte(this.name, value))
    }
}

interface BsonSortScope : BsonExtensions {

    fun ascending(vararg names: String): Bson = Sorts.ascending(*names)

    fun ascending(vararg names: KProperty<*>): Bson {
        return ascending(*names.map { it.name }.toTypedArray())
    }

    fun descending(vararg names: String): Bson {
        return Sorts.descending(*names)
    }

    fun descending(vararg names: KProperty<*>): Bson {
        return descending(*names.map { it.name }.toTypedArray())
    }
}

class BsonScope : BsonUpdateScope(), BsonFilterScope, BsonSortScope

inline fun <R> bson(block: BsonScope.() -> R): R =
    BsonScope().block()

inline fun bsonUpdate(block: BsonScope.() -> Unit): List<@JvmWildcard Bson> = BsonScope().apply(block).sets


inline fun <T : Any> FindFlow<T>.sortBy(block: BsonSortScope.() -> Bson): FindFlow<T> =
    sort(BsonScope().block())

inline fun FindOneAndUpdateOptions.sortBy(block: BsonSortScope.() -> Bson) =
    sort(BsonScope().block())

inline fun <T : Any> MongoCollection<T>.findBy(block: BsonFilterScope.() -> Bson): FindFlow<T> =
    find(BsonScope().block())

suspend inline fun <T : Any> MongoCollection<T>.findOneBy(block: BsonFilterScope.() -> Bson): T? =
    find(BsonScope().block()).firstOrNull()

inline val <reified E : Enum<E>> Enum<E>.serializedValue: String
    get() = json.encodeToJsonElement(this).jsonPrimitive.content

interface MqlExpressionScope {
    val current: MqlDocument get() = MqlValues.current()

    fun field(name: String): MqlValue = current.getField(name)
    fun field(prop: KProperty<*>): MqlValue = current.getField(prop.name)
//    infix fun KProperty<Boolean>.eq(value: Boolean): MqlBoolean = current.getField(this.name).eq(MqlValues.of(value))
//    infix fun KProperty<String>.eq(value: String): MqlBoolean = current.getField(this.name).eq(MqlValues.of(value))
//    infix fun KProperty<Int>.eq(value: Int): MqlBoolean = current.getField(this.name).eq(MqlValues.of(value))
//    infix fun KProperty<Long>.eq(value: Long): MqlBoolean = current.getField(this.name).eq(MqlValues.of(value))

    infix fun KProperty<Int>.eq(value: Int): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<Long>.eq(value: Long): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<Double>.eq(value: Double): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<Decimal128>.eq(value: Decimal128): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<BigDecimal>.eq(value: BigDecimal): MqlBoolean =
        field(name).eq(MqlValues.of(value.toDecimal128()))

    infix fun KProperty<Instant>.eq(value: Instant): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<kotlinx.datetime.Instant>.eq(value: kotlinx.datetime.Instant): MqlBoolean =
        field(name).eq(MqlValues.of(value.toJavaInstant()))

    infix fun KProperty<Boolean>.eq(value: Boolean): MqlBoolean = field(name).eq(MqlValues.of(value))
    infix fun KProperty<String>.eq(value: String): MqlBoolean = field(name).eq(MqlValues.of(value))
}

inline infix fun <reified E : Enum<E>> KProperty<E>.eq(value: E): MqlBoolean =
    MqlValues.current().getField(name).eq(MqlValues.of(value.serializedValue))

class BsonAggregateScope {
    @Suppress("PropertyName")
    val DOLLAR = "$"
    val operations = mutableListOf<Bson>()

    class MatchScope : BsonFilterScope

    inline fun match(block: MatchScope.() -> Bson) {
        operations.add(Aggregates.match(MatchScope().run(block)))
    }

    class GroupScope : MqlExpressionScope {
        val fields = mutableListOf<BsonField>()

        fun sum(name: String, expression: String) {
            fields.add(Accumulators.sum(name, expression))
        }

        fun sum(name: String, mql: MqlValue) {
            fields.add(Accumulators.sum(name, mql))
        }

        fun sum(name: String, prop: KProperty<*>) {
            fields.add(Accumulators.sum(name, "$" + prop.name))
        }

        /**
         * @see Accumulators
         */
        fun field(bson: BsonField) {
            fields.add(bson)
        }
    }

    inline fun group(
        id: Any? = null,
        block: GroupScope.() -> Unit,
    ) {
        operations.add(Aggregates.group(id, GroupScope().apply(block).fields))
    }

    inline fun stage(
        name: String,
        @Language("mongodb-json") expr: String,
    ) {
        operations.add(
            BsonDocument(
                "\$$name",
                BsonDocument.parse(expr.replace("@", "$"))
            )
        )
    }

    inline fun group(@Language("mongodb-json") expr: String) = stage("group", expr)
    inline fun match(@Language("mongodb-json") expr: String) = stage("match", expr)
    inline fun project(@Language("mongodb-json") expr: String) = stage("project", expr)

    @JvmInline
    value class ProjectionScope(
        val fields: MutableList<Bson> = mutableListOf(),
    ) {
        inline fun include(prop: KProperty<*>) {
            fields.add(Projections.include(prop.name))
        }

        inline fun exclude(prop: KProperty<*>) {
            fields.add(Projections.exclude(prop.name))
        }

        inline fun excludeId() {
            fields.add(Projections.excludeId())
        }
    }

    inline fun project(
        block: ProjectionScope.() -> Unit,
    ) {
        operations.add(
            Aggregates.project(
                Projections.fields(ProjectionScope().apply(block).fields)
            )
        )
    }
}

inline fun <reified R : Any> MongoCollection<out Any>.aggregateBy(block: BsonAggregateScope.() -> Unit): AggregateFlow<R> =
    aggregate(BsonAggregateScope().apply(block).operations, R::class.java)

/**
 * @param R 返回类型
 */
inline fun <R : Any> MongoCollection<*>.aggregateByTo(
    resultClass: KClass<R>,
    block: BsonAggregateScope.() -> Unit,
): AggregateFlow<R> =
    aggregate(BsonAggregateScope().apply(block).operations, resultClass.java)

/**
 * @param R 返回类型
 */
inline fun <reified R : Any> MongoCollection<*>.aggregateByTo(
    block: BsonAggregateScope.() -> Unit,
): AggregateFlow<R> =
    aggregate(BsonAggregateScope().apply(block).operations, R::class.java)


suspend inline fun <T : Any> MongoCollection<T>.updateOneBy(
    filter: BsonFilterScope.() -> Bson,
    options: UpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): UpdateResult {
    val list = bsonUpdate(update)
    return this.updateOne(bson(filter), Updates.combine(list), UpdateOptions().apply(options))
}

suspend inline fun <T : Any> MongoCollection<T>.updateOneById(
    id: String,
    options: UpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): UpdateResult = updateOneBy({ Filters.eq("_id", id) }, options, update)

suspend inline fun <T : Any> MongoCollection<T>.updateManyBy(
    filter: BsonFilterScope.() -> Bson,
    options: UpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): UpdateResult {
    val list = bsonUpdate(update)
    return this.updateMany(bson(filter), Updates.combine(list), UpdateOptions().apply(options))
}

suspend inline fun <T : Any> MongoCollection<T>.updateManyById(
    id: String,
    options: UpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): UpdateResult = updateManyBy({ Filters.eq("_id", id) }, options, update)

suspend inline fun <T : Any> MongoCollection<T>.findOneAndUpdateBy(
    filter: BsonFilterScope.() -> Bson,
    options: FindOneAndUpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): T? {
    val list = bsonUpdate(update)
    return this.findOneAndUpdate(bson(filter), Updates.combine(list), FindOneAndUpdateOptions().apply(options))
}

suspend inline fun <T : Any> MongoCollection<T>.findOneByIdAndUpdateBy(
    id: String,
    options: FindOneAndUpdateOptions.() -> Unit = {},
    update: BsonUpdateScope.() -> Unit,
): T? = findOneAndUpdateBy({ Filters.eq("_id", id) }, options, update)

suspend inline fun <T : Any> MongoCollection<T>.findOneAndDeleteBy(
    options: FindOneAndDeleteOptions = FindOneAndDeleteOptions(),
    filter: BsonFilterScope.() -> Bson,
): T? = this.findOneAndDelete(bson(filter), options)

suspend inline fun <T : Any> MongoCollection<T>.deleteOneBy(
    options: DeleteOptions = DeleteOptions(),
    filter: BsonFilterScope.() -> Bson,
): DeleteResult = this.deleteOne(bson(filter), options)

suspend inline fun <T : Any> MongoCollection<T>.deleteOneById(
    id: String?,
    options: DeleteOptions = DeleteOptions(),
): DeleteResult = this.deleteOneBy(options) { Filters.eq("_id", id) }

suspend inline fun <T : Any> MongoCollection<T>.deleteManyBy(
    options: DeleteOptions = DeleteOptions(),
    filter: BsonFilterScope.() -> Bson,
): DeleteResult = this.deleteMany(bson(filter), options)

suspend inline fun <T : Any> MongoCollection<T>.countBy(
    options: CountOptions = CountOptions(),
    filter: BsonFilterScope.() -> Bson,
): Long = this.countDocuments(bson(filter), options)


@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    allowTrailingComma = true
}

fun JsonElement.toBsonDocument(): Document = Document.parse(
    json.encodeToString(JsonElement.serializer(), this)
)
