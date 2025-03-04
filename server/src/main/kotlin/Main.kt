package edu.illinois.cs.cs125.gradlegrader.server

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.ryanharter.ktor.moshi.moshi
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.json.toJson
import com.uchuhimo.konf.source.yaml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import mu.KotlinLogging
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonString
import java.io.File
import java.net.URI
import java.time.Instant
import java.util.Properties

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.gradlegrader.server.version"))
}.getProperty("version")

const val NAME = "gradlegrader"
const val DEFAULT_HTTP = "http://0.0.0.0:8888"

object TopLevel : ConfigSpec("") {
    val http by optional(DEFAULT_HTTP)
    val semester by optional<String?>(null)
    val mongodb by required<String>()
    object Mongo : ConfigSpec() {
        val collection by optional(NAME)
    }
}

val configuration = Config {
    addSpec(TopLevel)
}.let {
    if (File("config.yaml").exists() && File("config.yaml").length() > 0) {
        it.from.yaml.file("config.yaml")
    }
    it.from.env()
}

val mongoCollection: MongoCollection<BsonDocument> = configuration[TopLevel.mongodb].run {
    val uri = MongoClientURI(this)
    val database = uri.database ?: error("MONGO must specify database to use")
    val collection = configuration[TopLevel.Mongo.collection]
    MongoClient(uri).getDatabase(database).getCollection(collection, BsonDocument::class.java)
}

@Suppress("unused")
@JsonClass(generateAdapter = true)
class Status {
    var name: String = NAME
    var version: String = VERSION
    var upSince: Instant = Instant.now()
    var statusCount: Int = 0
    var uploadCount: Int = 0
    var failureCount: Int = 0
    var lastUpload: Instant? = null
}
val currentStatus = Status()

@Suppress("unused")
class InstantAdapter {
    @FromJson
    fun instantFromJson(timestamp: String): Instant = Instant.parse(timestamp)

    @ToJson
    fun instantToJson(instant: Instant): String = instant.toString()
}

fun Application.gradlegrader() {
    install(ForwardedHeaders)
    install(CORS) {
        anyHost()
        allowNonSimpleContentTypes = true
    }
    install(ContentNegotiation) {
        moshi {
            add(InstantAdapter())
        }
    }
    routing {
        get("/") {
            call.respond(currentStatus)
            currentStatus.statusCount++
        }
        get("/version") {
            call.respond(VERSION)
        }
        post("/") {
            try {
                mongoCollection.insertOne(
                    BsonDocument.parse(call.receiveText())
                        .append("receivedVersion", BsonString(VERSION))
                        .append("receivedTime", BsonDateTime(Instant.now().toEpochMilli()))
                        .append("receivedIP", BsonString(call.request.origin.remoteHost))
                        .append("receivedSemester", BsonString(configuration[TopLevel.semester])),
                )
                currentStatus.uploadCount++
                currentStatus.lastUpload = Instant.now()
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.warn { "couldn't save upload: $e" }
                call.respond(HttpStatusCode.InternalServerError)
                currentStatus.failureCount++
                return@post
            }
        }
    }
    intercept(ApplicationCallPipeline.Fallback) {
        if (call.response.status() == null) {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

fun main() {
    logger.info(configuration.toJson.toText())

    val uri = URI(configuration[TopLevel.http])
    assert(uri.scheme == "http")

    embeddedServer(Netty, host = uri.host, port = uri.port, module = Application::gradlegrader).start(wait = true)
}
