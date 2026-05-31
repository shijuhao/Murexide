package com.juhao.murexide

import android.app.Application
import android.os.Build
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val imageLoader = ImageLoader.Builder(this)
            .componentRegistry {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
        
        Coil.setImageLoader(imageLoader)
    }
}