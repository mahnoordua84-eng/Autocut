package com.example.engine

import android.util.Log

object NativeEngineLoader {
  private var isLoaded = false
  private var isInitialized = false

  init {
    loadLibrary()
  }

  fun loadLibrary(): Boolean {
    if (isLoaded) return true
    return try {
      System.loadLibrary("ah_engine")
      isLoaded = true
      Log.i("NativeEngineLoader", "Successfully loaded libah_engine.so")
      try {
        isInitialized = nativeInit()
      } catch (e: Throwable) {
        Log.w("NativeEngineLoader", "nativeInit call skipped or failed", e)
      }
      true
    } catch (e: UnsatisfiedLinkError) {
      Log.e("NativeEngineLoader", "Failed to load libah_engine.so", e)
      false
    } catch (e: Exception) {
      Log.e("NativeEngineLoader", "Unexpected error loading libah_engine.so", e)
      false
    }
  }

  fun isEngineLoaded(): Boolean = isLoaded
  fun isEngineInitialized(): Boolean = isInitialized

  private external fun nativeInit(): Boolean
}
