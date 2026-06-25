package com.juhao.murexide.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QiniuImageUploader(
    context: Context,
    private val userToken: String,
    private val enableWebp: Boolean = false,
    private val webpQuality: Int = 95,
    private val debug: Boolean = false
) {
    companion object {
        private const val DEFAULT_UPLOAD_HOST = "upload-z2.qiniup.com"
        private const val BUCKET = "chat-68"
        private const val TOKEN_URL = "https://chat-go.jwzhd.com/v1/misc/qiniu-token"
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "bin")
        private const val CONNECTION_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 30L
        private const val WRITE_TIMEOUT_SECONDS = 30L
        private const val IMAGE_BASE_URL = "https://chat-img.jwznb.com/"
    }

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun debugLog(message: String) {
        if (debug) {
            println("[QiniuImageUploader] $message")
        }
    }

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
            .url(TOKEN_URL)
            .header("token", userToken)
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) {
                    throw IOException("Failed to get upload token: ${response.code}")
                }

                val body = response.body?.string() ?: throw IOException("Empty response")
                
                try {
                    val codeRegex = """"code"\s*:\s*(\d+)""".toRegex()
                    val code = codeRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (code != 1) {
                        throw IOException("Failed to get upload token: code=$code")
                    }

                    val tokenRegex = """"token"\s*:\s*"([^"]+)"""".toRegex()
                    tokenRegex.find(body)?.groupValues?.get(1)
                        ?: throw IOException("Token not found in response")
                } catch (e: Exception) {
                    throw IOException("Failed to parse response: ${e.message}")
                }
            } finally {
                response.close()
            }
        }
    }

    private suspend fun queryUploadHost(uploadToken: String): String {
        val ak = uploadToken.substringBefore(":")
        val url = "https://api.qiniu.com/v4/query?ak=$ak&bucket=$BUCKET"

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(Request.Builder().url(url).get().build()).execute()
                try {
                    if (!response.isSuccessful) return@withContext DEFAULT_UPLOAD_HOST

                    val body = response.body?.string() ?: return@withContext DEFAULT_UPLOAD_HOST
                    
                    val domainRegex = """"domains"\s*:\s*\[\s*"([^"]+)"""".toRegex()
                    val domain = domainRegex.find(body)?.groupValues?.get(1)
                        ?.replace(Regex("^https?://"), "")
                        ?.substringBefore("/")

                    domain ?: DEFAULT_UPLOAD_HOST
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                debugLog("Failed to query upload host: ${e.message}")
                DEFAULT_UPLOAD_HOST
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
                
                if (success && outputFile.exists() && outputFile.length() > 0) {
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                debugLog("WebP conversion failed: ${e.message}")
                false
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun parseUploadResponse(responseBody: String): String {
        try {
            val keyRegex = """"key"\s*:\s*"([^"]+)"""".toRegex()
            val key = keyRegex.find(responseBody)?.groupValues?.get(1)
            if (key != null) {
                return key
            }
            
            val hashRegex = """"hash"\s*:\s*"([^"]+)"""".toRegex()
            val hash = hashRegex.find(responseBody)?.groupValues?.get(1)
            if (hash != null) {
                return hash
            }
            
            if (responseBody.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
                return "$IMAGE_BASE_URL$responseBody"
            }
            
            if (responseBody.startsWith("http://") || responseBody.startsWith("https://")) {
                return responseBody
            }
            
            throw IOException("Cannot parse upload response: $responseBody")
        } catch (e: Exception) {
            throw IOException("Failed to parse response: ${e.message}")
        }
    }

    suspend fun upload(
        input: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
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
                    debugLog("Downloading file: $inputPath")
                    val downloadFile = File(cacheDir, "imgutil_${timestamp}_${uniqueId}.bin")
                    
                    val request = Request.Builder().url(inputPath).build()
                    val response = client.newCall(request).execute()
                    try {
                        if (!response.isSuccessful) {
                            return@withContext Result.failure(IOException("Download failed: ${response.code}"))
                        }
                        
                        response.body?.byteStream()?.use { inputStream ->
                            val totalBytes = response.body?.contentLength() ?: -1L
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
                        } ?: throw IOException("Downloaded file is empty")
                    } finally {
                        response.close()
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
    
                val originalExt = if (isRemoteUrl) {
                    getFileExtension(inputPath)
                } else {
                    inputPath.substringAfterLast(".", "bin")
                }
    
                val uploadFile: File
                val ext: String
                
                if (enableWebp) {
                    val webpFile = File(cacheDir, "imgutil_${timestamp}_${uniqueId}.webp")
                    filesToClean.add(webpFile)
                    
                    if (convertToWebp(srcFile, webpFile)) {
                        uploadFile = webpFile
                        ext = "webp"
                    } else {
                        debugLog("WebP conversion failed, using original file")
                        uploadFile = srcFile
                        ext = originalExt
                    }
                } else {
                    uploadFile = srcFile
                    ext = originalExt
                }
    
                onProgress(0.5f)
    
                val safeExt = ext.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { "bin" }
                if (!ALLOWED_EXTENSIONS.contains(safeExt.lowercase())) {
                    debugLog("Unsupported extension: $safeExt, using bin")
                }
    
                val md5 = md5Hex(uploadFile)
                val key = "$md5.$safeExt"
    
                debugLog("Uploading file: key=$key, size=${uploadFile.length()}")
    
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
    
                var request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()
    
                var response = client.newCall(request).execute()
                try {
                    var responseBody = response.body?.string() ?: ""
    
                    if (!response.isSuccessful && responseBody.contains("no such domain")) {
                        debugLog("Domain error, switching to default domain")
                        val fallbackUrl = "https://$DEFAULT_UPLOAD_HOST"
                        val fallbackRequest = Request.Builder()
                            .url(fallbackUrl)
                            .post(requestBody)
                            .build()
                        
                        val fallbackResponse = client.newCall(fallbackRequest).execute()
                        try {
                            val fallbackBody = fallbackResponse.body?.string() ?: ""
                            if (!fallbackResponse.isSuccessful) {
                                return@withContext Result.failure(IOException("Upload failed: ${fallbackResponse.code} - $fallbackBody"))
                            }
                            val imageUrl = parseUploadResponse(fallbackBody)
                            onProgress(1f)
                            return@withContext Result.success(imageUrl)
                        } finally {
                            fallbackResponse.close()
                        }
                    }
    
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Upload failed: ${response.code} - $responseBody"))
                    }
    
                    val imageUrl = parseUploadResponse(responseBody)
                    onProgress(1f)
                    Result.success(imageUrl)
                } finally {
                    response.close()
                }
    
            } catch (e: CancellationException) {
                debugLog("Upload cancelled")
                Result.failure(e)
            } catch (e: Exception) {
                debugLog("Upload failed: ${e.message}")
                Result.failure(e)
            } finally {
                filesToClean.forEach { file ->
                    try {
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }
}