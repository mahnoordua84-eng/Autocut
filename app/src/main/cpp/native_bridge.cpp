#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <vector>
#include "render/GpuRenderEngine.h"

#ifdef LOG_TAG
#undef LOG_TAG
#endif
#define LOG_TAG "NativeRenderBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Stride per layer in the packed float array
static constexpr int LAYER_STRIDE = 35;

static std::mutex gEngineMutex;
static ah_engine::GpuRenderEngine* gRenderEngine = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jint width,
    jint height) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine == nullptr) {
        gRenderEngine = new ah_engine::GpuRenderEngine();
    }

    if (!gRenderEngine->isInitialized()) {
        if (!gRenderEngine->init()) {
            LOGE("NativeRenderBridge: Failed to initialize GpuRenderEngine");
            return JNI_FALSE;
        }
    }

    gRenderEngine->resize(width, height);
    LOGI("NativeRenderBridge initialized successfully (%dx%d)", width, height);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeResize(
    JNIEnv* env,
    jobject /* thiz */,
    jint width,
    jint height) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine != nullptr && gRenderEngine->isInitialized()) {
        gRenderEngine->resize(width, height);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRenderFrame(
    JNIEnv* env,
    jobject /* thiz */,
    jfloatArray layerDataArray,
    jint layerCount) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine == nullptr || !gRenderEngine->isInitialized() || layerCount <= 0 || layerDataArray == nullptr) {
        return;
    }

    jsize arrayLength = env->GetArrayLength(layerDataArray);
    if (arrayLength < layerCount * LAYER_STRIDE) {
        LOGE("NativeRenderBridge: Layer data array length mismatch (expected at least %d, got %d)",
             layerCount * LAYER_STRIDE, arrayLength);
        return;
    }

    jfloat* layerData = env->GetFloatArrayElements(layerDataArray, nullptr);
    if (layerData == nullptr) {
        return;
    }

    std::vector<ah_engine::RenderLayer> layers;
    layers.reserve(layerCount);

    for (int i = 0; i < layerCount; ++i) {
        int base = i * LAYER_STRIDE;

        ah_engine::RenderLayer layer;
        layer.id = static_cast<uint32_t>(layerData[base + 0]);
        layer.textureId = static_cast<GLuint>(layerData[base + 1]);

        // Validate texture ID
        if (layer.textureId == 0) {
            continue;
        }

        layer.type = static_cast<ah_engine::LayerType>(static_cast<int>(layerData[base + 2]));
        layer.isVisible = (layerData[base + 3] > 0.5f);
        layer.zOrder = static_cast<int>(layerData[base + 4]);

        layer.posX = layerData[base + 5];
        layer.posY = layerData[base + 6];
        layer.scaleX = layerData[base + 7];
        layer.scaleY = layerData[base + 8];
        layer.rotation = layerData[base + 9];
        layer.width = layerData[base + 10];
        layer.height = layerData[base + 11];
        layer.opacity = layerData[base + 12];

        layer.uOffset = layerData[base + 13];
        layer.vOffset = layerData[base + 14];
        layer.uScale = layerData[base + 15];
        layer.vScale = layerData[base + 16];

        layer.blendMode = static_cast<ah_engine::BlendMode>(static_cast<int>(layerData[base + 17]));
        layer.useCustomMatrix = (layerData[base + 18] > 0.5f);

        if (layer.useCustomMatrix) {
            for (int m = 0; m < 16; ++m) {
                layer.transformMatrix[m] = layerData[base + 19 + m];
            }
        }

        layers.push_back(layer);
    }

    env->ReleaseFloatArrayElements(layerDataArray, layerData, JNI_ABORT);

    gRenderEngine->renderFrame(layers);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeBeginOffscreen(
    JNIEnv* env,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine != nullptr && gRenderEngine->isInitialized()) {
        gRenderEngine->beginOffscreen();
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeEndOffscreen(
    JNIEnv* env,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine != nullptr && gRenderEngine->isInitialized()) {
        return static_cast<jint>(gRenderEngine->endOffscreen());
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeOnContextLost(
    JNIEnv* env,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine != nullptr) {
        gRenderEngine->onContextLost();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_composition_gpu_NativeRenderBridge_nativeRelease(
    JNIEnv* env,
    jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(gEngineMutex);

    if (gRenderEngine != nullptr) {
        gRenderEngine->release();
        delete gRenderEngine;
        gRenderEngine = nullptr;
        LOGI("NativeRenderBridge released engine instance");
    }
}
