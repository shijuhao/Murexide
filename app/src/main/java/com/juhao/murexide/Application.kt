package com.juhao.murexide

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.juhao.murexide.datastore.TokenStorage
import com.juhao.murexide.network.WebSocketManager
import com.juhao.murexide.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        initWebSocket()
        observeNetworkStatus()
    }

    private fun observeNetworkStatus() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("MyApplication", "Network onAvailable, triggering WS reconnect")
                WebSocketManager.getInstance().manualReconnect()
            }

            override fun onLost(network: Network) {
                Log.d("MyApplication", "Network onLost")
                WebSocketManager.getInstance().notifyNetworkLost()
            }
        })
    }

    private fun initWebSocket() {
        val tokenStorage = TokenStorage(this)
        val authRepository = AuthRepository()
        
        applicationScope.launch {
            tokenStorage.tokenFlow.collect { token ->
                if (token != null) {
                    Log.d("MyApplication", "Token found, fetching user info for WS")
                    authRepository.getUserInfo(token).onSuccess { userInfo ->
                        val deviceId = "android_${Build.MODEL}_${Build.ID}"
                        WebSocketManager.getInstance().connect(
                            userId = userInfo.id,
                            token = token,
                            deviceId = deviceId
                        )
                    }.onFailure { e ->
                        Log.e("MyApplication", "Failed to get user info for WS", e)
                    }
                } else {
                    Log.d("MyApplication", "No token, disconnecting WS")
                    WebSocketManager.getInstance().disconnect()
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }
}