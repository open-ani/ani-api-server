package me.him188.ani.danmaku.server.ktor.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthScheme
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

internal fun Application.configureSwaggerUI() {
    install(SwaggerUI) {
        info {
            title = "Ani"
            version = "1.0.0"
            description = "Ani API"
        }
        server {
            url = "https://danmaku.api.myani.org/"
        }
        security {
            defaultSecuritySchemeNames = setOf("auth-jwt")
            securityScheme("auth-jwt") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
            }
        }
        schemas { 
            
        }
//        schemas {
//            generator = { type ->
//                type
//                    .processKotlinxSerialization {
//                        customProcessor<Instant> {
//                            createDefaultPrimitiveTypeData<Instant>(
//                                mutableMapOf(
//                                    "format" to "Iso8601 Instant",
//                                    "type" to "string",
//                                ),
//                            )
//                        }
//                    }
//                    .connectSubTypes()
//                    .generateSwaggerSchema()
//                    .withTitle(TitleType.SIMPLE)
//                    .handleCoreAnnotations()
//                    .handleSwaggerAnnotations()
//                    .customizeTypes { typeData, typeSchema ->
//                        typeData.annotations.find { it.name == "type_format_annotation" }?.also { annotation ->
//                            typeSchema.format = annotation.values["format"]?.toString()
//                            typeSchema.type = annotation.values["type"]?.toString()
//                        }
//                    }
//                    .handleSchemaAnnotations()
//                    .compileReferencingRoot()
//            }
//        }
//        schemas {
////            overwrite<kotlinx.datetime.Instant>(typeOf<String>())
////            schema("kotlinx.datetime.Instant", typeOf<String>())
//        }
    }
    routing {
        route("openapi.json") {
            openApiSpec()
        }
        route("swagger") {
            swaggerUI("/openapi.json")
        }
    }
}
//
//private inline fun <reified T> createDefaultPrimitiveTypeData(values: MutableMap<String, Any?>): PrimitiveTypeData {
//    return PrimitiveTypeData(
//        id = TypeId.build(T::class.qualifiedName!!),
//        simpleName = T::class.simpleName!!,
//        qualifiedName = T::class.qualifiedName!!,
//        annotations = mutableListOf(
//            AnnotationData(
//                name = "date",
//                values = values,
//                annotation = null,
//            ),
//        ),
//    )
//}
//
