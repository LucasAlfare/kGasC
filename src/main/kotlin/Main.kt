import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Data class representing a committer for a GitHub commit.
 *
 * @property name The name of the committer.
 * @property email The email of the committer.
 */
@Serializable
private data class Committer(val name: String, val email: String)

/**
 * Data class representing the payload for uploading a file to GitHub.
 *
 * @property message The commit message.
 * @property committer The committer information.
 * @property content The content of the file, encoded in Base64.
 * @property sha The SHA of the file if it already exists.
 */
@Serializable
private data class UploadRequestDTO(
  val message: String,
  val committer: Committer,
  val content: String,
  val sha: String? = null
)

/**
 * Data class used only to receive the SHA of a file from GitHub.
 *
 * @property sha The SHA of the file.
 */
@Serializable
private data class GithubFileShaResponseDTO(val sha: String)

/**
 * Data class representing the response for a file request from GitHub.
 *
 * @property sha The SHA of the file.
 * @property content The content of the file, encoded in Base64.
 */
@Serializable
private data class GitHubFileRequestDTO(val sha: String, val content: String)

/**
 * Helper object for interacting with GitHub repositories.
 */
object GithubHelper {

  /**
   * HTTP client for making requests to the GitHub API.
   */
  private lateinit var client: HttpClient

  /**
   * Uploads a file to a specified GitHub repository.
   *
   * @param githubToken The GitHub API token.
   * @param username The GitHub username.
   * @param repository The GitHub repository name.
   * @param inputFilePath The local file path to upload.
   * @param targetPathInRepository The target path in the repository where the file will be uploaded.
   * @param commitMessage The commit message for the upload.
   * @return True if the file was uploaded successfully, false otherwise.
   */
  @OptIn(ExperimentalEncodingApi::class, ExperimentalEncodingApi::class)
  suspend fun uploadFileToGithub(
    githubToken: String,
    username: String,
    repository: String,
    inputFilePath: String,
    targetPathInRepository: String,
    commitMessage: String = "upload file via API"
  ): Boolean {
    // TODO: perform validations
    val file = File(inputFilePath)
    val fileContentBase64 = Base64.encode(file.readBytes())
    val finalTargetPath = "$targetPathInRepository/${file.name}"

    if (!::client.isInitialized || !client.isActive) {
      initClient()
    }

    // Check if the file already exists to get the SHA
    val sha = getFileSha(githubToken, username, repository, finalTargetPath)

    val putResponse =
      client.put("https://api.github.com/repos/$username/$repository/contents/$finalTargetPath") {
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
            sha = sha
          )
        )
      }

    val isGoodResponse = putResponse.status in arrayOf(HttpStatusCode.Created, HttpStatusCode.OK)

    if (isGoodResponse) {
      println("File was successfully uploaded!")
    } else {
      println("Error uploading file to the github repository: ${putResponse.status}")
    }

    client.close()

    return isGoodResponse
  }

  /**
   * Downloads a file from a specified GitHub repository.
   *
   * @param githubToken The GitHub API token.
   * @param username The GitHub username.
   * @param repository The GitHub repository name.
   * @param targetFilePathInRepository The target path in the repository from where the file will be downloaded.
   * @param outputFilePath The local file path where the downloaded file will be saved.
   * @return True if the file was downloaded successfully, false otherwise.
   */
  @OptIn(ExperimentalEncodingApi::class)
  suspend fun downloadFileFromGithub(
    githubToken: String,
    username: String,
    repository: String,
    targetFilePathInRepository: String,
    outputFilePath: String = targetFilePathInRepository.split("/").last()
  ): Boolean {
    if (!::client.isInitialized || !client.isActive) {
      initClient()
    }

    val getResponse =
      client.get("https://api.github.com/repos/$username/$repository/contents/$targetFilePathInRepository") {
        header(HttpHeaders.Authorization, "Bearer $githubToken")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header(key = "X-GitHub-Api-Version", value = "2022-11-28")
      }

    if (getResponse.status == HttpStatusCode.OK) {
      val body = getResponse.body<GitHubFileRequestDTO>()

      // Experimental Base64 doesn't support processing strings directly, then we turn it to [ByteArray] after `trim()`
      val decodedContent = Base64.decode(body.content.trim().toByteArray())
      File(outputFilePath).writeBytes(decodedContent)
      println("File downloaded successfully to $outputFilePath")
    } else {
      println("Error downloading file with the specified parameters.")
    }

    client.close()

    return getResponse.status == HttpStatusCode.OK
  }

  /**
   * Retrieves the SHA of a file in the specified GitHub repository.
   *
   * @param githubToken The GitHub API token.
   * @param username The GitHub username.
   * @param repository The GitHub repository name.
   * @param targetPathInRepository The target path in the repository.
   * @return The SHA of the file if it exists, null otherwise.
   */
  private suspend fun getFileSha(
    githubToken: String,
    username: String,
    repository: String,
    targetPathInRepository: String
  ): String? {
    return try {
      val response =
        client.get("https://api.github.com/repos/$username/$repository/contents/$targetPathInRepository") {
          header(HttpHeaders.Authorization, "Bearer $githubToken")
          header(HttpHeaders.Accept, "application/vnd.github+json")
          header(key = "X-GitHub-Api-Version", value = "2022-11-28")
        }

      response.body<GithubFileShaResponseDTO>().sha
    } catch (e: Exception) {
      null
    }
  }

  /**
   * Initializes the HTTP client.
   */
  private fun initClient() {
    client = HttpClient(CIO) {
      install(ContentNegotiation) {
        json(Json {
          isLenient = false
          prettyPrint = true
          ignoreUnknownKeys = true
        })
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

    if (uploadResult) {
      GithubHelper.downloadFileFromGithub(
        githubToken = "TOKEN",
        username = "OWNER NAME",
        repository = "REPOSITORY NAME",
        targetFilePathInRepository = "RELATIVE PATH IN THE REPO FOR WHERE TO TAKE THE DESIRED FILE",
        outputFilePath = "NAME OF THE FILE AFTER DOWNLOADED" //optional, default is original name
      )
    }
  }.onFailure {
    println("Did you really tried to run a explicit failable code? 💀")
  }
}