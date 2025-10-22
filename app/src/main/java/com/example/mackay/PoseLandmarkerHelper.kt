package com.example.mackay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL, // 使用FULL模型
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        try {
            poseLandmarker?.close()
        } catch (e: Exception) {
            Log.e(TAG, "關閉PoseLandmarker時發生錯誤: ${e.message}")
        } finally {
            poseLandmarker = null
        }
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        // 使用FULL模型
        val modelName = "pose_landmarker_full.task"
        baseOptionBuilder.setModelAssetPath(modelName)

        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (poseLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }
            else -> {
                // no-op
            }
        }

        try {
            Log.d(TAG, "Setting up PoseLandmarker with model: $modelName")
            
            // 检查模型文件是否存在
            val assets = context.assets
            try {
                assets.open(modelName).close()
                Log.d(TAG, "Model file $modelName found in assets")
            } catch (e: Exception) {
                Log.e(TAG, "Model file $modelName not found in assets", e)
                poseLandmarkerHelperListener?.onError(
                    "模型文件未找到: $modelName"
                )
                return
            }
            
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            Log.d(TAG, "Creating PoseLandmarker from options")
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "PoseLandmarker created successfully")
            
            // 测试PoseLandmarker是否可用
            if (poseLandmarker != null) {
                Log.d(TAG, "PoseLandmarker is ready for detection")
            } else {
                Log.e(TAG, "PoseLandmarker is null after creation")
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}", e)
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize: ${e.message}"
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "PoseLandmarker failed to load model with error: ${e.message}", e)
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize: ${e.message}", GPU_ERROR
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing PoseLandmarker: ${e.message}", e)
            poseLandmarkerHelperListener?.onError(
                "Pose Landmarker failed to initialize: ${e.message}"
            )
        }
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        try {
            if (runningMode != RunningMode.LIVE_STREAM) {
                Log.e(TAG, "Running mode is not LIVE_STREAM")
                return
            }
            
            if (poseLandmarker == null) {
                Log.e(TAG, "PoseLandmarker is null, cannot detect")
                return
            }
        
        val frameTime = SystemClock.uptimeMillis()
        Log.d(TAG, "Starting pose detection for frame at time: $frameTime")

        // 使用YUV到RGB转换的方法
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmapBuffer = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
        
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectLiveStream: ${e.message}", e)
            poseLandmarkerHelperListener?.onError("Pose detection error: ${e.message}")
        }
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        if (poseLandmarker == null) {
            Log.e(TAG, "PoseLandmarker is null in detectAsync")
            return
        }
        Log.d(TAG, "Calling detectAsync with frameTime: $frameTime")
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        
        val landmarks = result.landmarks()
        Log.d(TAG, "Pose检测完成，检测到的人体数量: ${landmarks.size}")
        if (landmarks.isNotEmpty()) {
            Log.d(TAG, "第一个人的关键点数量: ${landmarks[0].size}")
        }

        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
