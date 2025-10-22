package com.example.mackay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {
    
    private lateinit var previewView: PreviewView
    private lateinit var statusText: android.widget.TextView
    private lateinit var poseOverlayView: PoseOverlayView
    private lateinit var backButton: com.google.android.material.button.MaterialButton
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var exerciseType: String = ""
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pose)
        
        // 设置新的返回键处理
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("PoseActivity", "OnBackPressedDispatcher 被調用")
                showExerciseSummaryDialog()
            }
        })
        
        // 获取传递的动作类型
        exerciseType = intent.getStringExtra("exercise_type") ?: ""
        
        initViews()
        setupClickListeners()
        
        // 检查权限并启动相机
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        poseOverlayView = findViewById(R.id.poseOverlayView)
        backButton = findViewById(R.id.backButton)
    }
    
    private fun setupClickListeners() {
        backButton.setOnClickListener {
            Log.d("PoseActivity", "返回按鈕被點擊")
            showExerciseSummaryDialog()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            setupPoseLandmarker()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun setupPoseLandmarker() {
        try {
            Log.d("PoseActivity", "Starting PoseLandmarkerHelper initialization...")
            
            // 检查模型文件是否存在
            val assets = assets
            try {
                assets.open("pose_landmarker_full.task").close()
                Log.d("PoseActivity", "Model file found")
            } catch (e: Exception) {
                Log.e("PoseActivity", "Model file not found: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, "模型文件未找到", Toast.LENGTH_LONG).show()
                }
                poseLandmarkerHelper = null
                return
            }
            
            poseLandmarkerHelper = PoseLandmarkerHelper(
                minPoseDetectionConfidence = 0.3f,  // 回到穩定的置信度
                minPoseTrackingConfidence = 0.3f,  // 回到穩定的置信度
                minPosePresenceConfidence = 0.3f,  // 回到穩定的置信度
                currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,  // 使用CPU避免閃退
                runningMode = RunningMode.LIVE_STREAM,
                context = this,
                poseLandmarkerHelperListener = this
            )
            Log.d("PoseActivity", "PoseLandmarkerHelper initialized successfully")
        } catch (e: Exception) {
            Log.e("PoseActivity", "Failed to initialize PoseLandmarkerHelper: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this, "Pose检测初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
            poseLandmarkerHelper = null
        }
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        // 创建预览用例
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // 创建图像捕获用例
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        
        // 创建图像分析器用于Pose检测
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, PoseAnalyzer())
            }
        
        // 选择前相机
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        
        try {
            // 解绑之前的用例
            cameraProvider.unbindAll()
            
            // 绑定用例到相机
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalyzer
            )
            
            updateStatusText()
            
        } catch (exc: Exception) {
            Toast.makeText(this, "相机启动失败: ${exc.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun updateStatusText() {
        statusText.visibility = android.view.View.GONE
    }
    
    // Pose分析器
    private inner class PoseAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            try {
                Log.d("PoseActivity", "PoseAnalyzer: Processing frame ${imageProxy.imageInfo.timestamp}")
                
                if (poseLandmarkerHelper != null) {
                    Log.d("PoseActivity", "PoseAnalyzer: Calling detectLiveStream")
                    poseLandmarkerHelper?.detectLiveStream(imageProxy, true) // 始终使用前相机
                } else {
                    Log.e("PoseActivity", "PoseAnalyzer: poseLandmarkerHelper is null")
                    runOnUiThread {
                        statusText.text = "Pose检测器未初始化"
                    }
                }
            } catch (e: Exception) {
                Log.e("PoseActivity", "Error in PoseAnalyzer: ${e.message}", e)
                runOnUiThread {
                    statusText.text = "Pose检测错误: ${e.message}"
                }
            }
        }
    }
    
    // LandmarkerListener实现
    override fun onError(error: String, errorCode: Int) {
        Log.e("PoseActivity", "Pose检测错误: $error")
        runOnUiThread {
            Toast.makeText(this, "Pose检测错误: $error", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            Log.d("PoseActivity", "收到Pose检测结果，结果数量: ${resultBundle.results.size}")
            if (resultBundle.results.isNotEmpty()) {
                val result = resultBundle.results[0]
                val landmarks = result.landmarks()
                Log.d("PoseActivity", "检测到的人体数量: ${landmarks.size}")
                if (landmarks.isNotEmpty()) {
                    Log.d("PoseActivity", "第一个人的关键点数量: ${landmarks[0].size}")
                }
                
                poseOverlayView.setResults(
                    result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM,
                    isMirrored = true  // 使用前置攝像頭，需要鏡像處理
                )
                
                // 設置運動類型
                poseOverlayView.setExerciseType(exerciseType)
            } else {
                Log.d("PoseActivity", "没有检测到人体")
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            // 停止相機
            camera?.let {
                cameraProvider?.unbindAll()
            }
        } catch (e: Exception) {
            Log.e("PoseActivity", "停止相機時發生錯誤: ${e.message}")
        }
    }
    
    override fun onStop() {
        super.onStop()
        try {
            // 清理相機資源
            camera?.let {
                cameraProvider?.unbindAll()
            }
        } catch (e: Exception) {
            Log.e("PoseActivity", "清理相機資源時發生錯誤: ${e.message}")
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        // 顯示統計視窗而不是直接返回
        Log.d("PoseActivity", "onBackPressed 被調用")
        showExerciseSummaryDialog()
    }
    
    /**
     * 顯示運動統計視窗
     */
    private fun showExerciseSummaryDialog() {
        try {
            Log.d("PoseActivity", "開始顯示統計視窗")
            
            // 停止相機錄影
            stopCamera()
            
            // 創建統計視窗
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("運動統計")
            
            // 獲取統計數據
            val counts = poseOverlayView.getCounts()
            val actionHistory = poseOverlayView.getActionHistory()
            Log.d("PoseActivity", "統計數據: counts=$counts, actionHistory=$actionHistory")
            
            var message = ""
            
            // 處理左右跨步的統計數據
            if (counts is StrideDetector.StrideCountResult) {
                val totalCount = counts.leftSuccess + counts.leftFailure + counts.leftInvalid + 
                               counts.rightSuccess + counts.rightFailure + counts.rightInvalid
                val totalSuccess = counts.leftSuccess + counts.rightSuccess
                val totalFailure = counts.leftFailure + counts.rightFailure
                val totalInvalid = counts.leftInvalid + counts.rightInvalid
                
                val successRate = if (totalCount > 0) {
                    String.format("%.1f", totalSuccess * 100.0 / totalCount)
                } else {
                    "0.0"
                }
                
                message += "總次數: $totalCount\n"
                message += "成功次數: $totalSuccess\n"
                message += "失敗次數: $totalFailure\n"
                message += "無效次數: $totalInvalid\n"
                message += "成功率: ${successRate}%\n\n"
                
                // 顯示每次動作的詳細信息
                if (actionHistory != null && actionHistory is List<*>) {
                    message += "動作記錄:\n"
                    (actionHistory as List<StrideDetector.ActionRecord>).forEach { record ->
                        message += "第${record.actionNumber}次動作 (${record.legSide}): ${String.format("%.1f", record.angle)}° - ${record.result}\n"
                    }
                }
            }
            // 處理腳尖對齊腳跟的統計數據
            else if (counts is HeelMovementDetector.CountResult) {
                val successRate = String.format("%.1f", counts.successRate)
                
                message += "總次數: ${counts.totalCount}\n"
                message += "成功次數: ${counts.totalSuccess}\n"
                message += "失敗次數: ${counts.totalFailure}\n"
                message += "不計次: ${counts.totalNoCount}\n"
                message += "成功率: ${successRate}%\n\n"
                
                // 顯示每次動作的詳細信息
                if (actionHistory != null && actionHistory is List<*>) {
                    message += "動作記錄:\n"
                    actionHistory.forEach { record ->
                        if (record is HeelMovementDetector.ActionRecord) {
                            message += "第${record.actionNumber}次動作 (${record.legSide}): ${record.direction} - ${record.result}\n"
                        }
                    }
                }
            }
            // 處理高抬腳的統計數據
            else if (counts is HighKneeCounter.CountResult) {
                val totalCount = counts.totalSuccessCount + counts.totalFailureCount + counts.totalInvalidCount
                val successRate = if (totalCount > 0) {
                    (counts.totalSuccessCount * 100.0 / totalCount).toInt()
                } else {
                    0
                }
                
                message += "總次數: $totalCount\n"
                message += "成功次數: ${counts.totalSuccessCount}\n"
                message += "失敗次數: ${counts.totalFailureCount}\n"
                message += "無效次數: ${counts.totalInvalidCount}\n"
                message += "成功率: ${successRate}%\n\n"
                
                val minAnglesLog = poseOverlayView.getMinAnglesLog()
                if (minAnglesLog != null) {
                    message += "左腿動作: ${minAnglesLog.leftMinAngles.size} 次\n"
                    message += "右腿動作: ${minAnglesLog.rightMinAngles.size} 次\n\n"
                    
                    message += "左腿角度:\n"
                    minAnglesLog.leftMinAngles.forEachIndexed { index, angle ->
                        val status = getAngleStatus(angle)
                        message += "  第${index + 1}次: ${String.format("%.1f", angle)}° ($status)\n"
                    }
                    
                    message += "右腿角度:\n"
                    minAnglesLog.rightMinAngles.forEachIndexed { index, angle ->
                        val status = getAngleStatus(angle)
                        message += "  第${index + 1}次: ${String.format("%.1f", angle)}° ($status)\n"
                    }
                }
            }
            // 處理腳尖對齊腳跟的簡單統計數據（備用方案）
            else if (counts is ToeToHeelCounter.CountResult) {
                message += "左腳對齊次數: ${counts.leftAlignmentCount}\n"
                message += "右腳對齊次數: ${counts.rightAlignmentCount}\n"
                message += "總對齊次數: ${counts.totalAlignmentCount}\n\n"
            }
            
            builder.setMessage(message)
            builder.setPositiveButton("確定") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            builder.setCancelable(false)
            
            Log.d("PoseActivity", "準備顯示視窗")
            val dialog = builder.create()
            dialog.show()
            Log.d("PoseActivity", "視窗已顯示")
            
        } catch (e: Exception) {
            Log.e("PoseActivity", "顯示統計視窗時發生錯誤: ${e.message}", e)
            // 如果出錯，直接返回
            finish()
        }
    }
    
    /**
     * 停止相機錄影
     */
    private fun stopCamera() {
        try {
            Log.d("PoseActivity", "停止相機錄影")
            camera?.let {
                cameraProvider?.unbindAll()
            }
        } catch (e: Exception) {
            Log.e("PoseActivity", "停止相機時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 判斷角度狀態
     */
    private fun getAngleStatus(angle: Double): String {
        return when {
            angle >= 72.0 && angle <= 108.0 -> "成功"
            (angle >= 54.0 && angle < 72.0) || (angle > 108.0 && angle <= 126.0) -> "失敗"
            else -> "無效"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // 清理PoseOverlayView資源
            poseOverlayView.cleanup()
            
            // 清理MediaPipe資源
            poseLandmarkerHelper?.clearPoseLandmarker()
            
            // 停止相機
            camera?.let {
                cameraProvider?.unbindAll()
            }
            
            // 關閉執行器
            if (!cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
            }
        } catch (e: Exception) {
            Log.e("PoseActivity", "清理資源時發生錯誤: ${e.message}")
        }
    }
}

