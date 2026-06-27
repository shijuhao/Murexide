package com.juhao.murexide.utils

import android.net.Uri
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable

@Serializable
data class QiniuUploadResponse(
    val key: String,
    val hash: String = "",
    val fsize: Long = 0L
)

class QiniuUploader(
    context: Context,
    private val userToken: String,
    private val enableWebp: Boolean = false,
    private val uploadType: Int = 1, //1图片2视频3文件
    private val webpQuality: Int = 95,
    private val debug: Boolean = false
) {
    companion object {
        // 图片配置
        private const val IMAGE_DEFAULT_UPLOAD_HOST = "upload-z2.qiniup.com"
        private const val IMAGE_BUCKET = "chat-68"
        
        // 视频配置
        private const val VIDEO_DEFAULT_UPLOAD_HOST = "upload-cn-east-2.qiniup.com"
        private const val VIDEO_BUCKET = "chat68-video"
        
        // 文件配置
        private const val FILE_DEFAULT_UPLOAD_HOST = "upload-z2.qiniup.com"
        private const val FILE_BUCKET = "chat68-file"
        
        private val ALLOWED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        private val ALLOWED_VIDEO_EXTENSIONS = setOf("mp4", "avi", "mov", "mkv", "flv", "wmv", "3gp")
        
        private const val CONNECTION_TIMEOUT_SECONDS = 60L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
    }
    
    private val tokenUrl = when (uploadType) {
        1 -> "https://chat-go.jwzhd.com/v1/misc/qiniu-token"
        2 -> "https://chat-go.jwzhd.com/v1/misc/qiniu-token-video"
        else -> "https://chat-go.jwzhd.com/v1/misc/qiniu-token2"
    }
    
    private val defaultUploadHost: String
        get() = when (uploadType) {
            1 -> IMAGE_DEFAULT_UPLOAD_HOST
            2 -> VIDEO_DEFAULT_UPLOAD_HOST
            else -> FILE_DEFAULT_UPLOAD_HOST
        }
    
    private val bucket: String
        get() = when (uploadType) {
            1 -> IMAGE_BUCKET
            2 -> VIDEO_BUCKET
            else -> FILE_BUCKET
        }
    
    private val allowedExtensions: Set<String>?
        get() = when (uploadType) {
            1 -> ALLOWED_IMAGE_EXTENSIONS
            2 -> ALLOWED_VIDEO_EXTENSIONS
            else -> null
        }
    
    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun isUrl(input: String): Boolean {
        return input.startsWith("http://") || input.startsWith("https://")
    }

    private fun getFileExtension(path: String): String {
        var cleanPath = path
        val queryIndex = cleanPath.indexOf('?')
        if (queryIndex != -1) {
            cleanPath = cleanPath.substring(0, queryIndex)
        }
        val fragmentIndex = cleanPath.indexOf('#')
        if (fragmentIndex != -1) {
            cleanPath = cleanPath.substring(0, fragmentIndex)
        }
        
        val ext = cleanPath.substringAfterLast(".", "")
        return if (ext.isNotEmpty() && ext.all { it.isLetterOrDigit() }) ext else "bin"
    }

    private fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun getUploadToken(): String {
        if (userToken.isEmpty()) {
            throw IllegalStateException("user_token is empty")
        }
    
        val request = Request.Builder()
            .url(tokenUrl)
            .header("token", userToken)
            .get()
            .build()
    
        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            response.use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to get upload token: ${response.code}")
                }
    
                val body = response.body.string()
    
                try {
                    val json = Json.parseToJsonElement(body).jsonObject
                    val token = json["data"]
                        ?.jsonObject
                        ?.get("token")
                        ?.jsonPrimitive
                        ?.content
                        ?: throw IOException("Token not found in response")
                    
                    token
                } catch (e: Exception) {
                    throw IOException("Failed to parse response: ${e.message}")
                }
            }
        }
    }

    private suspend fun queryUploadHost(uploadToken: String): String {
        val ak = uploadToken.substringBefore(":")
        val url = "https://api.qiniu.com/v4/query?ak=$ak&bucket=$bucket"
    
        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(url).get().build()).execute()
                response.use { response ->
                    if (!response.isSuccessful) return@withContext defaultUploadHost
    
                    val body = response.body.string()
    
                    try {
                        val json = Json.parseToJsonElement(body).jsonObject
                        val hosts = json["hosts"]?.jsonArray ?: return@withContext defaultUploadHost
                        
                        if (hosts.isNotEmpty()) {
                            val firstHost = hosts[0].jsonObject
                            val up = firstHost["up"]?.jsonObject ?: return@withContext defaultUploadHost
                            val domains = up["domains"]?.jsonArray ?: return@withContext defaultUploadHost
                            
                            if (domains.isNotEmpty()) {
                                val domain = domains[0].jsonPrimitive.content
                                return@withContext domain
                            }
                        }
                    } catch (_: Exception) {}
    
                    defaultUploadHost
                }
            } catch (e: Exception) {
                defaultUploadHost
            }
        }
    }

    private suspend fun convertToWebp(inputFile: File, outputFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null
            try {
                bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
                if (bitmap == null) {
                    return@withContext false
                }

                val quality = webpQuality.coerceIn(1, 100)
                val success = bitmap.compress(
                    Bitmap.CompressFormat.WEBP,
                    quality,
                    outputFile.outputStream()
                )

                success && outputFile.exists() && outputFile.length() > 0
            } catch (e: Exception) {
                false
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private suspend fun prepareFile(file: File): Pair<File, String> {
        return when (uploadType) {
            1 -> {
                // 图片处理
                if (enableWebp) {
                    val webpFile = File(appContext.cacheDir, "${file.nameWithoutExtension}.webp")
                    if (convertToWebp(file, webpFile)) {
                        return webpFile to "webp"
                    }
                }
                val ext = file.extension.ifEmpty { "bin" }
                file to ext
            }
            2 -> {
                // 视频处理
                val ext = file.extension.ifEmpty { "mp4" }
                file to ext
            }
            else -> {
                // 文件处理
                val ext = file.extension.ifEmpty { "bin" }
                file to ext
            }
        }
    }

    private fun parseUploadResponse(responseBody: String): QiniuUploadResponse {
        return try {
            Json.decodeFromString<QiniuUploadResponse>(responseBody)
        } catch (e: Exception) {
            throw IOException("Failed to parse response: ${e.message}")
        }
    }

    suspend fun upload(
        input: String,
        onProgress: (Float) -> Unit = {}
    ): Result<QiniuUploadResponse> {
        return withContext(Dispatchers.IO) {
            val filesToClean = mutableListOf<File>()
            
            try {
                coroutineContext.ensureActive()
                
                val inputPath = input.trim()
                if (inputPath.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Input is empty"))
                }
    
                val cacheDir = appContext.cacheDir
                val timestamp = System.currentTimeMillis()
                val uniqueId = UUID.randomUUID().toString().take(8)
                
                val isRemoteUrl = isUrl(inputPath)
                val srcFile = if (isRemoteUrl) {
                    val downloadFile = File(cacheDir, "imgutil_${timestamp}_${uniqueId}.bin")
                    
                    val request = Request.Builder().url(inputPath).build()
                    val response = client.newCall(request).execute()
                    response.use { response ->
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(IOException("Download failed: ${response.code}"))
                        }

                        response.body.byteStream().use { inputStream ->
                            val totalBytes = response.body.contentLength()
                            var uploadedBytes = 0L
                            val buffer = ByteArray(8192)

                            downloadFile.outputStream().use { outputStream ->
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    coroutineContext.ensureActive()
                                    outputStream.write(buffer, 0, bytesRead)
                                    uploadedBytes += bytesRead
                                    if (totalBytes > 0) {
                                        val progress = uploadedBytes.toFloat() / totalBytes
                                        withContext(Dispatchers.Main) {
                                            onProgress(progress)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    filesToClean.add(downloadFile)
                    downloadFile
                } else {
                    val file = File(inputPath)
                    if (!file.exists()) {
                        return@withContext Result.failure(IOException("File not found: $inputPath"))
                    }
                    onProgress(0.3f)
                    file
                }
    
                coroutineContext.ensureActive()
    
                val (uploadFile, ext) = prepareFile(srcFile)
                if (uploadFile != srcFile) {
                    filesToClean.add(uploadFile)
                }
    
                onProgress(0.5f)
    
                val safeExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { "bin" }
                if (uploadType != 3) {
                    allowedExtensions?.let {
                        if (!it.contains(safeExt.lowercase())) {
                            if (uploadType == 1) {
                                return@withContext Result.failure(
                                    IllegalArgumentException("Unsupported image format: $safeExt")
                                )
                            }
                        }
                    }
                }
    
                val md5 = md5Hex(uploadFile)
                val key = "$md5.$safeExt"
    
                val uploadToken = try {
                    getUploadToken()
                } catch (e: Exception) {
                    return@withContext Result.failure(IOException("Failed to get upload token: ${e.message}"))
                }
    
                onProgress(0.7f)
    
                val host = queryUploadHost(uploadToken)
                val uploadUrl = "https://$host"
    
                val mediaType = "application/octet-stream".toMediaTypeOrNull()
                    ?: throw IllegalStateException("Invalid media type")
    
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("token", uploadToken)
                    .addFormDataPart("key", key)
                    .addFormDataPart(
                        "file",
                        uploadFile.name,
                        uploadFile.asRequestBody(mediaType)
                    )
                    .build()
    
                onProgress(0.8f)
    
                coroutineContext.ensureActive()
    
                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()
    
                val response = client.newCall(request).execute()
                response.use { response ->
                    val responseBody = response.body.string()

                    if (!response.isSuccessful && responseBody.contains("no such domain")) {
                        val fallbackUrl = "https://$defaultUploadHost"
                        val fallbackRequest = Request.Builder()
                            .url(fallbackUrl)
                            .post(requestBody)
                            .build()

                        val fallbackResponse = client.newCall(fallbackRequest).execute()
                        fallbackResponse.use { fallbackResponse ->
                            val fallbackBody = fallbackResponse.body.string()
                            if (!fallbackResponse.isSuccessful) {
                                return@withContext Result.failure(IOException("Upload failed: ${fallbackResponse.code} - $fallbackBody"))
                            }
                            val uploadResponse = parseUploadResponse(fallbackBody)
                            onProgress(1f)
                            return@withContext Result.success(uploadResponse)
                        }
                    }

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Upload failed: ${response.code} - $responseBody"))
                    }

                    val uploadResponse = parseUploadResponse(responseBody)
                    onProgress(1f)
                    Result.success(uploadResponse)
                }
    
            } catch (e: CancellationException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                filesToClean.forEach { file ->
                    try {
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
    
    suspend fun uploadFromUri(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<QiniuUploadResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(context, uri)
                val cacheFile = File(context.cacheDir, fileName)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(IOException("无法读取文件"))
                
                val result = upload(cacheFile.absolutePath, onProgress)
                cacheFile.delete()
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
    }
}