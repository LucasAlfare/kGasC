import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class Committer(val name: String, val email: String)

@Serializable
private data class UploadRequestDTO(
  val message: String,
  val committer: Committer,
  val content: String
)

@Serializable
data class UploadResponseContent(
  val name: String,
  val path: String,
  @SerialName("download_url") val downloadUrl: String
)

@Serializable
data class UploadResponseDTO(
  val content: UploadResponseContent
)

object GithubHelper {

  private lateinit var client: HttpClient

  @OptIn(ExperimentalEncodingApi::class, ExperimentalEncodingApi::class)
  suspend fun uploadFileToGithub(
    githubToken: String,
    username: String,
    repository: String,
    inputFilePath: String,
    targetPathInRepository: String, // omits file name, will be the same of input file
    commitMessage: String = "Upload file via my custom API wrapper 🛠"
  ): UploadResponseDTO? {
    // TODO: perform validations
    if (!::client.isInitialized || !client.isActive) initClient()
    val file = File(inputFilePath)
    val fileContentBase64 = Base64.encode(file.readBytes())
    val finalTargetPath = "$targetPathInRepository/${file.name}"
    val response = client.put(
      urlString = "https://api.github.com/repos/$username/$repository/contents/$finalTargetPath"
    ) {
      header(HttpHeaders.Authorization, "Bearer $githubToken")
      header(HttpHeaders.Accept, "application/vnd.github+json")
      header(key = "X-GitHub-Api-Version", value = "2022-11-28")
      contentType(ContentType.Application.Json)
      setBody(
        UploadRequestDTO(
          message = commitMessage,
          committer = Committer(
            name = "kGit Helper",
            email = "souluquinha@hotmail.com"
          ),
          content = fileContentBase64,
        )
      )
    }

    return (if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
      response.body<UploadResponseDTO>()
    } else {
      null
    }).apply {
      println("Request result was: $this")
      client.close()
    }
  }

  suspend fun downloadFile(
    fileUrl: String,
    outputFileName: String? = null // points to some PATH
  ): Boolean {
    if (!::client.isInitialized || !client.isActive) initClient()
    val getResponse = client.get(fileUrl)
    return (if (getResponse.status == HttpStatusCode.OK) {
      val url = Url(outputFileName ?: fileUrl)
      val file = File(url.pathSegments.last())
      getResponse.bodyAsChannel().copyAndClose(file.writeChannel()) > 0L
    } else {
      false
    }).apply {
      println("Download result was: $this")
      client.close()
    }
  }

  private fun initClient() {
    client = HttpClient(CIO) {
      install(ContentNegotiation) {
        json(
          Json {
            isLenient = false
            prettyPrint = true
            ignoreUnknownKeys = true
          }
        )
      }
    }
  }
}

@Deprecated("Not really deprecated, but is just a quick live example of how to use the functions. :)")
suspend fun usageExample() {
  // this will really fail
  runCatching {
    val uploadResult = GithubHelper.uploadFileToGithub(
      githubToken = "TOKEN",
      username = "OWNER NAME",
      repository = "REPOSITORY NAME",
      inputFilePath = "RELATIVE PATH FOR THE DESIRED FILE",
      targetPathInRepository = "PATH WHERE TO PLACE THE INPUT FILE",
      commitMessage = "A FANCY COMMIT MESSAGE" //optional, default "upload file via kGasC"
    )

    if (uploadResult != null) {
      GithubHelper.downloadFile(
        fileUrl = "THE DOWNLOAD FILE DIRECT URL"
      )
    }
  }.onFailure {
    println("Did you really tried to run a explicit failable code? 💀")
  }
}