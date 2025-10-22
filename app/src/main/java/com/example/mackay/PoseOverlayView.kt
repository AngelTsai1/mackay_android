package com.example.mackay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()
    private var textPaint = Paint()
    private var angleTextPaint = Paint()
    private var countTextPaint = Paint()
    
    // 角度計算相關
    private var leftAngle: Double = 0.0
    private var rightAngle: Double = 0.0
    private var leftAngleValid = false
    private var rightAngleValid = false
    
    // 角度穩定性檢查
    private val leftAngleHistory = mutableListOf<Double>()
    private val rightAngleHistory = mutableListOf<Double>()
    private val maxHistorySize = 5
    
    // 關鍵點平滑處理
    private val landmarkSmoothingSize = 3
    private val leftHipHistory = mutableListOf<Pair<Float, Float>>()
    private val leftKneeHistory = mutableListOf<Pair<Float, Float>>()
    private val leftAnkleHistory = mutableListOf<Pair<Float, Float>>()
    private val rightHipHistory = mutableListOf<Pair<Float, Float>>()
    private val rightKneeHistory = mutableListOf<Pair<Float, Float>>()
    private val rightAnkleHistory = mutableListOf<Pair<Float, Float>>()
    
    // 計次管理器
    private val highKneeCounter = HighKneeCounter()
    private val sideStepCounter = SideStepCounter()
    private val toeToHeelCounter = ToeToHeelCounter()
    private val strideDetector = StrideDetector()
    
    // 移動檢測器
    private val heelMovementDetector = HeelMovementDetector()
    private var currentMovementStatus: HeelMovementDetector.MovementStatus? = null
    
    // 運動類型
    private var exerciseType: String = "高抬腳(側面)"

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f
    
    // 添加鏡像標記，用於處理左右顛倒問題
    private var isMirrored: Boolean = false
    
    // 关键点名称映射
    private val landmarkNames = arrayOf(
        "鼻子", "左眼内", "左眼", "左眼外", "右眼内", "右眼", "右眼外", "左耳", "右耳",
        "嘴左", "嘴右", "左肩", "右肩", "左肘", "右肘", "左腕", "右腕", "左小指", "右小指",
        "左食指", "右食指", "左拇指", "右拇指", "左髋", "右髋", "左膝", "右膝", "左踝", "右踝",
        "左脚跟", "右脚跟", "左脚趾", "右脚趾"
    )
    
    // 左右對稱的關鍵點索引映射（用於鏡像處理）
    private val leftRightPairs = mapOf(
        // 眼睛
        1 to 4, 2 to 5, 3 to 6,  // 左眼 -> 右眼
        4 to 1, 5 to 2, 6 to 3,  // 右眼 -> 左眼
        // 耳朵
        7 to 8, 8 to 7,  // 左耳 <-> 右耳
        // 嘴巴
        9 to 10, 10 to 9,  // 嘴左 <-> 嘴右
        // 肩膀
        11 to 12, 12 to 11,  // 左肩 <-> 右肩
        // 手肘
        13 to 14, 14 to 13,  // 左肘 <-> 右肘
        // 手腕
        15 to 16, 16 to 15,  // 左腕 <-> 右腕
        // 手指
        17 to 18, 18 to 17,  // 左小指 <-> 右小指
        19 to 20, 20 to 19,  // 左食指 <-> 右食指
        21 to 22, 22 to 21,  // 左拇指 <-> 右拇指
        // 髖關節
        23 to 24, 24 to 23,  // 左髋 <-> 右髋
        // 膝蓋
        25 to 26, 26 to 25,  // 左膝 <-> 右膝
        // 腳踝
        27 to 28, 28 to 27,  // 左踝 <-> 右踝
        // 腳跟
        29 to 30, 30 to 29,  // 左脚跟 <-> 右脚跟
        // 腳趾
        31 to 32, 32 to 31   // 左脚趾 <-> 右脚趾
    )

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        textPaint.reset()
        invalidate()
        initPaints()
    }
    
    /**
     * 設置運動類型
     */
    fun setExerciseType(type: String) {
        exerciseType = type
        android.util.Log.d("PoseOverlayView", "運動類型設置為: $exerciseType")
    }

    private fun initPaints() {
        linePaint.color = Color.GREEN
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH * 1.5f // 增大连接线粗细
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.RED
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH * 2 // 增大关键点大小
        pointPaint.style = Paint.Style.FILL
        
        // 设置文本绘制属性
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        textPaint.isAntiAlias = true
        textPaint.style = Paint.Style.FILL
        
        // 设置角度文本绘制属性 - 螢幕左側顯示，字體更大
        angleTextPaint.color = Color.WHITE
        angleTextPaint.textSize = 75f  // 增大字體
        angleTextPaint.isAntiAlias = true
        angleTextPaint.style = Paint.Style.FILL
        angleTextPaint.isFakeBoldText = true
        
        // 设置计次文本绘制属性 - 黑底白字，字体加大
        countTextPaint.color = Color.RED
        countTextPaint.textSize = 64f
        countTextPaint.isAntiAlias = true
        countTextPaint.style = Paint.Style.FILL
        countTextPaint.isFakeBoldText = true
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        
        // 始終顯示計次信息
        drawCountInfo(canvas)
        
        // 添加调试信息（只在腳尖對齊腳跟模式下显示）
        if (results == null) {
            if (exerciseType == "腳尖對齊腳跟") {
                canvas.drawText("等待Pose检测...", 50f, 100f, textPaint)
            }
            return
        }
        
        results?.let { poseLandmarkerResult ->
            val landmarks = poseLandmarkerResult.landmarks()
            if (landmarks.isEmpty()) {
                if (exerciseType == "腳尖對齊腳跟") {
                    canvas.drawText("未检测到人体", 50f, 100f, textPaint)
                }
                return@let
            }
            
            for(landmark in landmarks) {
                // 計算角度
                calculateAngles(landmark)
                
                // 根據運動類型處理不同的計次邏輯
                when (exerciseType) {
                    "高抬腳(側面)" -> {
                        // 高抬腳使用角度檢測
                        if (leftAngleValid && rightAngleValid) {
                            highKneeCounter.processAngles(leftAngle, rightAngle)
                        } else if (leftAngleValid) {
                            highKneeCounter.processAngles(leftAngle, null)
                        } else if (rightAngleValid) {
                            highKneeCounter.processAngles(null, rightAngle)
                        }
                    }
                    "左右跨步" -> {
                        // 左右跨步使用角度檢測（髖-膝-踝角度）
                        if (leftAngleValid && rightAngleValid) {
                            strideDetector.processFrame(leftAngle, rightAngle)
                        } else if (leftAngleValid) {
                            strideDetector.processFrame(leftAngle, null)
                        } else if (rightAngleValid) {
                            strideDetector.processFrame(null, rightAngle)
                        }
                    }
                    "腳尖對齊腳跟" -> {
                        // 腳尖對齊腳跟使用腳尖和腳跟位置檢測
                        val leftToeX = if (landmark.size > 31) landmark[getCorrectLandmarkIndex(31)].x() else null
                        val leftToeY = if (landmark.size > 31) landmark[getCorrectLandmarkIndex(31)].y() else null
                        val leftToeZ = if (landmark.size > 31) landmark[getCorrectLandmarkIndex(31)].z() else null
                        val leftHeelX = if (landmark.size > 29) landmark[getCorrectLandmarkIndex(29)].x() else null
                        val leftHeelY = if (landmark.size > 29) landmark[getCorrectLandmarkIndex(29)].y() else null
                        val leftHeelZ = if (landmark.size > 29) landmark[getCorrectLandmarkIndex(29)].z() else null
                        val rightToeX = if (landmark.size > 32) landmark[getCorrectLandmarkIndex(32)].x() else null
                        val rightToeY = if (landmark.size > 32) landmark[getCorrectLandmarkIndex(32)].y() else null
                        val rightToeZ = if (landmark.size > 32) landmark[getCorrectLandmarkIndex(32)].z() else null
                        val rightHeelX = if (landmark.size > 30) landmark[getCorrectLandmarkIndex(30)].x() else null
                        val rightHeelY = if (landmark.size > 30) landmark[getCorrectLandmarkIndex(30)].y() else null
                        val rightHeelZ = if (landmark.size > 30) landmark[getCorrectLandmarkIndex(30)].z() else null
                        
                        // 計算腳長
                        val leftFootLength = if (leftToeX != null && leftToeY != null && leftToeZ != null && 
                                               leftHeelX != null && leftHeelY != null && leftHeelZ != null) {
                            kotlin.math.sqrt(
                                (leftToeX - leftHeelX) * (leftToeX - leftHeelX) + 
                                (leftToeY - leftHeelY) * (leftToeY - leftHeelY) + 
                                (leftToeZ - leftHeelZ) * (leftToeZ - leftHeelZ)
                            ).toFloat()
                        } else null
                        
                        val rightFootLength = if (rightToeX != null && rightToeY != null && rightToeZ != null && 
                                                rightHeelX != null && rightHeelY != null && rightHeelZ != null) {
                            kotlin.math.sqrt(
                                (rightToeX - rightHeelX) * (rightToeX - rightHeelX) + 
                                (rightToeY - rightHeelY) * (rightToeY - rightHeelY) + 
                                (rightToeZ - rightHeelZ) * (rightToeZ - rightHeelZ)
                            ).toFloat()
                        } else null
                        
                        // 獲取世界坐標z數據 - 注意：MediaPipe Android API可能不直接提供世界坐標
                        // 這裡暫時使用標準化坐標作為占位符，需要根據實際API調整
                        val worldLeftHeelZ = if (landmark.size > 29) landmark[getCorrectLandmarkIndex(29)].z() else null
                        val worldRightHeelZ = if (landmark.size > 30) landmark[getCorrectLandmarkIndex(30)].z() else null
                        val worldLeftToeZ = if (landmark.size > 31) landmark[getCorrectLandmarkIndex(31)].z() else null
                        val worldRightToeZ = if (landmark.size > 32) landmark[getCorrectLandmarkIndex(32)].z() else null
                        val worldLeftShoulderZ = if (landmark.size > 11) landmark[getCorrectLandmarkIndex(11)].z() else null
                        val worldRightShoulderZ = if (landmark.size > 12) landmark[getCorrectLandmarkIndex(12)].z() else null
                        
                        // 創建腳跟數據
                        val heelData = HeelMovementDetector.HeelData(
                            frame = 0, // 這裡可以從外部傳入
                            timestamp = System.currentTimeMillis().toFloat() / 1000f,
                            // 標準化坐標
                            leftHeelX = leftHeelX,
                            leftHeelY = leftHeelY,
                            leftHeelZ = leftHeelZ,
                            rightHeelX = rightHeelX,
                            rightHeelY = rightHeelY,
                            rightHeelZ = rightHeelZ,
                            leftToeX = leftToeX,
                            leftToeY = leftToeY,
                            leftToeZ = leftToeZ,
                            rightToeX = rightToeX,
                            rightToeY = rightToeY,
                            rightToeZ = rightToeZ,
                            leftFootLength = leftFootLength,
                            rightFootLength = rightFootLength,
                            // 世界坐標
                            worldLeftHeelZ = worldLeftHeelZ,
                            worldRightHeelZ = worldRightHeelZ,
                            worldLeftToeZ = worldLeftToeZ,
                            worldRightToeZ = worldRightToeZ,
                            worldLeftShoulderZ = worldLeftShoulderZ,
                            worldRightShoulderZ = worldRightShoulderZ
                        )
                        
                        // 檢測移動狀態
                        currentMovementStatus = heelMovementDetector.detectHeelMovement(heelData)
                        
                        // 原有的對齊檢測
                        toeToHeelCounter.processToeHeelPositions(
                            leftToeX, leftToeY, leftHeelX, leftHeelY,
                            rightToeX, rightToeY, rightHeelX, rightHeelY
                        )
                    }
                }
                
                // 绘制连接线
                PoseLandmarker.POSE_LANDMARKS.forEach {
                    val startX = poseLandmarkerResult.landmarks().get(0).get(it!!.start()).x() * imageWidth * scaleFactor + offsetX
                    val startY = poseLandmarkerResult.landmarks().get(0).get(it.start()).y() * imageHeight * scaleFactor + offsetY
                    val endX = poseLandmarkerResult.landmarks().get(0).get(it.end()).x() * imageWidth * scaleFactor + offsetX
                    val endY = poseLandmarkerResult.landmarks().get(0).get(it.end()).y() * imageHeight * scaleFactor + offsetY
                    
                    canvas.drawLine(startX, startY, endX, endY, linePaint)
                }
                
                // 绘制关键点和标注
                for((index, normalizedLandmark) in landmark.withIndex()) {
                    val x = normalizedLandmark.x() * imageWidth * scaleFactor + offsetX
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor + offsetY
                    
                    // 绘制关键点
                    canvas.drawPoint(x, y, pointPaint)
                    
                    // 绘制关键点编号和名称（只在非腳尖對齊腳跟模式下显示）
                    if (exerciseType != "腳尖對齊腳跟") {
                        val correctIndex = getCorrectLandmarkIndex(index)
                        val correctName = getCorrectLandmarkName(index)
                        val label = "${correctIndex}: ${correctName}"
                        canvas.drawText(label, x + 10f, y - 10f, textPaint)
                    }
                }
                
                // 绘制角度信息（只在需要角度的动作中显示）
                if (exerciseType != "腳尖對齊腳跟") {
                    drawAngleInfo(canvas, landmark)
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.LIVE_STREAM,
        isMirrored: Boolean = false
    ) {
        android.util.Log.d("PoseOverlayView", "setResults called with ${poseLandmarkerResults.landmarks().size} landmarks")
        results = poseLandmarkerResults
        this.isMirrored = isMirrored

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // 对于LIVE_STREAM模式，使用FILL模式来匹配预览
                val scaleX = width.toFloat() / imageWidth
                val scaleY = height.toFloat() / imageHeight
                max(scaleX, scaleY) // 使用较大的缩放比例来填充整个预览区域
            }
        }
        
        // 计算偏移量以居中显示
        offsetX = (width - imageWidth * scaleFactor) / 2f
        offsetY = (height - imageHeight * scaleFactor) / 2f
        invalidate()
    }
    
    /**
     * 獲取正確的關鍵點索引（處理鏡像情況）
     */
    private fun getCorrectLandmarkIndex(originalIndex: Int): Int {
        return if (isMirrored && leftRightPairs.containsKey(originalIndex)) {
            leftRightPairs[originalIndex] ?: originalIndex
        } else {
            originalIndex
        }
    }
    
    /**
     * 獲取正確的關鍵點名稱（處理鏡像情況）
     */
    private fun getCorrectLandmarkName(originalIndex: Int): String {
        val correctIndex = getCorrectLandmarkIndex(originalIndex)
        return if (correctIndex < landmarkNames.size) {
            landmarkNames[correctIndex]
        } else {
            "未知"
        }
    }

    /**
     * 計算左右腿的角度
     */
    private fun calculateAngles(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) {
        try {
            // 檢查關鍵點索引是否有效
            if (landmarks.size < 33) {
                leftAngleValid = false
                rightAngleValid = false
                return
            }
            
            // 獲取正確的關鍵點索引（處理鏡像情況）- 修改為髖-膝-踝角度
            val leftHipIndex = getCorrectLandmarkIndex(23)
            val leftKneeIndex = getCorrectLandmarkIndex(25)
            val leftAnkleIndex = getCorrectLandmarkIndex(27)
            val rightHipIndex = getCorrectLandmarkIndex(24)
            val rightKneeIndex = getCorrectLandmarkIndex(26)
            val rightAnkleIndex = getCorrectLandmarkIndex(28)
            
            // 左腿角度計算 - 髖-膝-踝角度
            val leftHip = landmarks[leftHipIndex]
            val leftKnee = landmarks[leftKneeIndex]
            val leftAnkle = landmarks[leftAnkleIndex]
            
            // 檢查左腿關鍵點是否都在螢幕上
            val leftHipInScreen = isLandmarkInScreen(leftHip)
            val leftKneeInScreen = isLandmarkInScreen(leftKnee)
            val leftAnkleInScreen = isLandmarkInScreen(leftAnkle)
            
            if (leftHipInScreen && leftKneeInScreen && leftAnkleInScreen &&
                AngleCalculator.isLandmarkValid(leftHip) && 
                AngleCalculator.isLandmarkValid(leftKnee) && 
                AngleCalculator.isLandmarkValid(leftAnkle)) {
                
                // 平滑關鍵點坐標
                val smoothLeftHip = smoothLandmark(leftHipHistory, leftHip.x(), leftHip.y())
                val smoothLeftKnee = smoothLandmark(leftKneeHistory, leftKnee.x(), leftKnee.y())
                val smoothLeftAnkle = smoothLandmark(leftAnkleHistory, leftAnkle.x(), leftAnkle.y())
                
                // 使用平滑後的坐標計算髖-膝-踝角度
                val newLeftAngle = calculateHipKneeAnkleAngle(
                    smoothLeftHip.first, smoothLeftHip.second,
                    smoothLeftKnee.first, smoothLeftKnee.second,
                    smoothLeftAnkle.first, smoothLeftAnkle.second
                )
                leftAngle = newLeftAngle
                leftAngleValid = true
                
                // 更新左腳角度歷史
                updateAngleHistory(leftAngleHistory, newLeftAngle)
            } else {
                leftAngleValid = false
                android.util.Log.d("PoseOverlayView", "左腿關鍵點不在螢幕上或無效")
            }
            
            // 右腿角度計算 - 髖-膝-踝角度
            val rightHip = landmarks[rightHipIndex]
            val rightKnee = landmarks[rightKneeIndex]
            val rightAnkle = landmarks[rightAnkleIndex]
            
            // 檢查右腿關鍵點是否都在螢幕上
            val rightHipInScreen = isLandmarkInScreen(rightHip)
            val rightKneeInScreen = isLandmarkInScreen(rightKnee)
            val rightAnkleInScreen = isLandmarkInScreen(rightAnkle)
            
            if (rightHipInScreen && rightKneeInScreen && rightAnkleInScreen &&
                AngleCalculator.isLandmarkValid(rightHip) && 
                AngleCalculator.isLandmarkValid(rightKnee) && 
                AngleCalculator.isLandmarkValid(rightAnkle)) {
                
                // 平滑關鍵點坐標
                val smoothRightHip = smoothLandmark(rightHipHistory, rightHip.x(), rightHip.y())
                val smoothRightKnee = smoothLandmark(rightKneeHistory, rightKnee.x(), rightKnee.y())
                val smoothRightAnkle = smoothLandmark(rightAnkleHistory, rightAnkle.x(), rightAnkle.y())
                
                // 使用平滑後的坐標計算髖-膝-踝角度
                val newRightAngle = calculateHipKneeAnkleAngle(
                    smoothRightHip.first, smoothRightHip.second,
                    smoothRightKnee.first, smoothRightKnee.second,
                    smoothRightAnkle.first, smoothRightAnkle.second
                )
                rightAngle = newRightAngle
                rightAngleValid = true
                
                // 更新右腳角度歷史
                updateAngleHistory(rightAngleHistory, newRightAngle)
            } else {
                rightAngleValid = false
                android.util.Log.d("PoseOverlayView", "右腿關鍵點不在螢幕上或無效")
            }
            
            // 添加額外的驗證邏輯來防止誤判
            validateAngles()
            
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "計算角度時發生錯誤: ${e.message}")
            leftAngleValid = false
            rightAngleValid = false
        }
    }
    
    /**
     * 檢查關鍵點是否在螢幕範圍內
     */
    private fun isLandmarkInScreen(landmark: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Boolean {
        val x = landmark.x()
        val y = landmark.y()
        
        // 檢查是否在螢幕範圍內 (0.0 到 1.0)
        return x >= 0.0f && x <= 1.0f && y >= 0.0f && y <= 1.0f
    }
    
    /**
     * 更新角度歷史記錄
     */
    private fun updateAngleHistory(history: MutableList<Double>, newAngle: Double) {
        history.add(newAngle)
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }
    
    /**
     * 平滑關鍵點坐標
     */
    private fun smoothLandmark(history: MutableList<Pair<Float, Float>>, x: Float, y: Float): Pair<Float, Float> {
        history.add(Pair(x, y))
        if (history.size > landmarkSmoothingSize) {
            history.removeAt(0)
        }
        
        if (history.size < 2) {
            return Pair(x, y)
        }
        
        // 計算平均值
        val avgX = history.map { it.first }.average().toFloat()
        val avgY = history.map { it.second }.average().toFloat()
        
        return Pair(avgX, avgY)
    }
    
    /**
     * 計算髖-膝-踝角度（與Python版本一致）
     */
    private fun calculateHipKneeAnkleAngle(
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float,
        ankleX: Float, ankleY: Float
    ): Double {
        try {
            // 計算向量：髖-膝 和 膝-踝
            val hipKneeX = hipX - kneeX
            val hipKneeY = hipY - kneeY
            val ankleKneeX = ankleX - kneeX
            val ankleKneeY = ankleY - kneeY
            
            // 計算點積
            val dotProduct = hipKneeX * ankleKneeX + hipKneeY * ankleKneeY
            
            // 計算向量長度
            val hipKneeLength = kotlin.math.sqrt((hipKneeX * hipKneeX + hipKneeY * hipKneeY).toDouble())
            val ankleKneeLength = kotlin.math.sqrt((ankleKneeX * ankleKneeX + ankleKneeY * ankleKneeY).toDouble())
            
            if (hipKneeLength == 0.0 || ankleKneeLength == 0.0) {
                return 0.0
            }
            
            // 計算夾角（以膝蓋為頂點）
            val cosAngle = dotProduct / (hipKneeLength * ankleKneeLength)
            val clampedCos = cosAngle.coerceIn(-1.0, 1.0)
            val angleRadians = kotlin.math.acos(clampedCos)
            val angleDegrees = Math.toDegrees(angleRadians)
            
            return angleDegrees
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "計算髖-膝-踝角度時發生錯誤: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * 直接從坐標計算角度（保留舊函數以防其他地方使用）
     */
    private fun calculateAngleFromCoordinates(
        shoulderX: Float, shoulderY: Float,
        hipX: Float, hipY: Float,
        kneeX: Float, kneeY: Float
    ): Double {
        try {
            // 計算向量
            val shoulderHipX = shoulderX - hipX
            val shoulderHipY = shoulderY - hipY
            val kneeHipX = kneeX - hipX
            val kneeHipY = kneeY - hipY
            
            // 計算點積
            val dotProduct = shoulderHipX * kneeHipX + shoulderHipY * kneeHipY
            
            // 計算向量長度
            val shoulderHipLength = kotlin.math.sqrt((shoulderHipX * shoulderHipX + shoulderHipY * shoulderHipY).toDouble())
            val kneeHipLength = kotlin.math.sqrt((kneeHipX * kneeHipX + kneeHipY * kneeHipY).toDouble())
            
            if (shoulderHipLength == 0.0 || kneeHipLength == 0.0) {
                return 0.0
            }
            
            // 計算夾角
            val cosAngle = dotProduct / (shoulderHipLength * kneeHipLength)
            val clampedCos = cosAngle.coerceIn(-1.0, 1.0)
            val angleRadians = kotlin.math.acos(clampedCos)
            val angleDegrees = Math.toDegrees(angleRadians)
            
            return angleDegrees
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "計算角度時發生錯誤: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * 檢查角度穩定性
     */
    private fun isAngleStable(history: List<Double>): Boolean {
        if (history.size < 3) return true
        
        val variance = history.map { kotlin.math.abs(it - history.average()) }.average()
        return variance < 15.0 // 角度變化小於15度認為是穩定的
    }
    
    /**
     * 驗證角度合理性，防止誤判
     */
    private fun validateAngles() {
        // 特殊處理：當右腳抬起時，左腳landmark容易抖動
        // 如果右腳角度很小（表示抬起），且左腳角度也在變化，則優先信任右腳
        if (leftAngleValid && rightAngleValid) {
            val angleDiff = kotlin.math.abs(leftAngle - rightAngle)
            
            // 檢查角度穩定性
            val leftStable = isAngleStable(leftAngleHistory)
            val rightStable = isAngleStable(rightAngleHistory)
            
            // 如果角度差異超過60度，進行進一步分析
            if (angleDiff > 60.0) {
                val minAngle = kotlin.math.min(leftAngle, rightAngle)
                val maxAngle = kotlin.math.max(leftAngle, rightAngle)
                
                // 特殊情況：右腳抬起時左腳landmark抖動
                if (rightAngle < 60.0 && leftAngle > 120.0) {
                    // 右腳明顯抬起，左腳角度正常，信任右腳
                    if (!leftStable) {
                        // 如果左腳角度不穩定，可能是抖動，標記為無效
                        leftAngleValid = false
                        android.util.Log.d("PoseOverlayView", "右腳抬起，左腳角度抖動，標記左腳為無效: 右${String.format("%.1f", rightAngle)}° 左${String.format("%.1f", leftAngle)}°")
                    } else {
                        android.util.Log.d("PoseOverlayView", "右腳抬起，左腳角度正常，信任右腳: 右${String.format("%.1f", rightAngle)}° 左${String.format("%.1f", leftAngle)}°")
                    }
                } else if (leftAngle < 60.0 && rightAngle > 120.0) {
                    // 左腳明顯抬起，右腳角度正常，信任左腳
                    if (!rightStable) {
                        // 如果右腳角度不穩定，可能是抖動，標記為無效
                        rightAngleValid = false
                        android.util.Log.d("PoseOverlayView", "左腳抬起，右腳角度抖動，標記右腳為無效: 左${String.format("%.1f", leftAngle)}° 右${String.format("%.1f", rightAngle)}°")
                    } else {
                        android.util.Log.d("PoseOverlayView", "左腳抬起，右腳角度正常，信任左腳: 左${String.format("%.1f", leftAngle)}° 右${String.format("%.1f", rightAngle)}°")
                    }
                } else if (minAngle < 30.0) {
                    // 如果最小角度小於30度，可能是誤判
                    if (maxAngle > 120.0) {
                        // 保留較大的角度，標記較小的角度為無效
                        if (leftAngle < rightAngle) {
                            leftAngleValid = false
                            android.util.Log.d("PoseOverlayView", "左腿角度可能誤判，已標記為無效: ${String.format("%.1f", leftAngle)}°")
                        } else {
                            rightAngleValid = false
                            android.util.Log.d("PoseOverlayView", "右腿角度可能誤判，已標記為無效: ${String.format("%.1f", rightAngle)}°")
                        }
                    }
                }
            }
        }
        
        // 檢查角度是否在合理範圍內
        if (leftAngleValid && (leftAngle < 20.0 || leftAngle > 200.0)) {
            leftAngleValid = false
            android.util.Log.d("PoseOverlayView", "左腿角度超出合理範圍，已標記為無效: ${String.format("%.1f", leftAngle)}°")
        }
        
        if (rightAngleValid && (rightAngle < 20.0 || rightAngle > 200.0)) {
            rightAngleValid = false
            android.util.Log.d("PoseOverlayView", "右腿角度超出合理範圍，已標記為無效: ${String.format("%.1f", rightAngle)}°")
        }
    }
    
    /**
     * 繪製角度信息
     */
    private fun drawAngleInfo(canvas: Canvas, landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) {
        try {
            // 在螢幕左側顯示角度信息
            val leftX = 20f  // 螢幕左側固定位置
            var currentY = 250f  // 大幅提高起始Y位置
            
            // 繪製左腿角度
            if (leftAngleValid) {
                // 根據角度範圍設定顏色
                angleTextPaint.color = AngleCalculator.getAngleColor(leftAngle)
                val angleText = "左腳: ${AngleCalculator.formatAngle(leftAngle)}°"
                canvas.drawText(angleText, leftX, currentY, angleTextPaint)
                currentY += 60f  // 下一個位置
            } else {
                angleTextPaint.color = Color.RED
                canvas.drawText("左腳: 無效", leftX, currentY, angleTextPaint)
                currentY += 60f
            }
            
            // 繪製右腿角度
            if (rightAngleValid) {
                // 根據角度範圍設定顏色
                angleTextPaint.color = AngleCalculator.getAngleColor(rightAngle)
                val angleText = "右腳: ${AngleCalculator.formatAngle(rightAngle)}°"
                canvas.drawText(angleText, leftX, currentY, angleTextPaint)
                currentY += 60f
            } else {
                angleTextPaint.color = Color.RED
                canvas.drawText("右腳: 無效", leftX, currentY, angleTextPaint)
                currentY += 60f
            }
            
            // 移除角度範圍說明（不適用於當前動作）
            
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "繪製角度信息時發生錯誤: ${e.message}")
        }
    }

    /**
     * 繪製計次信息 - 根據運動類型顯示不同信息
     */
    private fun drawCountInfo(canvas: Canvas) {
        try {
            val infoY = 50f  // 提高起始位置
            when (exerciseType) {
                "高抬腳(側面)" -> {
                    val counts = highKneeCounter.getCounts()
                    val countText = "成功: ${counts.totalSuccessCount} | 失敗: ${counts.totalFailureCount} | 左: ${counts.leftSuccessCount}/${counts.leftFailureCount} | 右: ${counts.rightSuccessCount}/${counts.rightFailureCount}"
                    canvas.drawText(countText, 20f, infoY, countTextPaint)
                }
                "左右跨步" -> {
                    val counts = strideDetector.getCounts()
                    val successText = "左成功: ${counts.leftSuccess} | 右成功: ${counts.rightSuccess}"
                    val failureText = "左失敗: ${counts.leftFailure} | 右失敗: ${counts.rightFailure}"
                    canvas.drawText(successText, 20f, infoY, countTextPaint)
                    canvas.drawText(failureText, 20f, infoY + 80f, countTextPaint)  // 大幅增加間距
                }
                "腳尖對齊腳跟" -> {
                    // 顯示成功/失敗/不計次計數
                    val successText = "左成功: ${heelMovementDetector.getLeftSuccessCount()} | 右成功: ${heelMovementDetector.getRightSuccessCount()}"
                    val failureText = "左失敗: ${heelMovementDetector.getLeftFailureCount()} | 右失敗: ${heelMovementDetector.getRightFailureCount()}"
                    val noCountText = "左不計次: ${heelMovementDetector.getLeftNoCount()} | 右不計次: ${heelMovementDetector.getRightNoCount()}"
                    canvas.drawText(successText, 20f, infoY, countTextPaint)
                    canvas.drawText(failureText, 20f, infoY + 60f, countTextPaint)
                    canvas.drawText(noCountText, 20f, infoY + 120f, countTextPaint)
                    
                    // 顯示移動狀態（用於調試）
                    currentMovementStatus?.let { status ->
                        val movementText = "左腳: ${status.leftHeelDisplayStatus} | 右腳: ${status.rightHeelDisplayStatus}"
                        canvas.drawText(movementText, 20f, infoY + 180f, countTextPaint)
                        
                        if (status.leftHeelActionEnded || status.rightHeelActionEnded) {
                            val actionText = "動作結束 - 左: ${status.leftHeelActionEnded} | 右: ${status.rightHeelActionEnded}"
                            canvas.drawText(actionText, 20f, infoY + 240f, countTextPaint)
                        }
                        
                        if (status.leftHeelDirection.isNotEmpty() || status.rightHeelDirection.isNotEmpty()) {
                            val directionText = "方向 - 左: ${status.leftHeelDirection} | 右: ${status.rightHeelDirection}"
                            canvas.drawText(directionText, 20f, infoY + 300f, countTextPaint)
                        }
                        
                        if (status.leftHeelActionResult.isNotEmpty() || status.rightHeelActionResult.isNotEmpty()) {
                            val resultText = "結果 - 左: ${status.leftHeelActionResult} | 右: ${status.rightHeelActionResult}"
                            canvas.drawText(resultText, 20f, infoY + 360f, countTextPaint)
                        }
                    }
                }
                else -> {
                    canvas.drawText("未知運動類型", 20f, infoY, countTextPaint)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "繪製計次信息時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 重置計數器
     */
    fun resetCounter() {
        when (exerciseType) {
            "高抬腳(側面)" -> highKneeCounter.reset()
            "左右跨步" -> strideDetector.reset()
            "腳尖對齊腳跟" -> toeToHeelCounter.reset()
        }
    }
    
    /**
     * 獲取計數結果
     */
    fun getCounts(): Any {
        return when (exerciseType) {
            "高抬腳(側面)" -> highKneeCounter.getCounts()
            "左右跨步" -> strideDetector.getCounts()
            "腳尖對齊腳跟" -> heelMovementDetector.getCounts()
            else -> highKneeCounter.getCounts()
        }
    }
    
    /**
     * 獲取最小角度日誌（僅適用於高抬腳）
     */
    fun getMinAnglesLog(): HighKneeCounter.MinAnglesLog? {
        return if (exerciseType == "高抬腳(側面)") {
            highKneeCounter.getMinAnglesLog()
        } else {
            null
        }
    }
    
    /**
     * 獲取動作歷史記錄
     */
    fun getActionHistory(): Any? {
        return when (exerciseType) {
            "左右跨步" -> strideDetector.getActionHistory()
            "腳尖對齊腳跟" -> heelMovementDetector.getActionHistory()
            else -> null
        }
    }

    /**
     * 清理資源
     */
    fun cleanup() {
        try {
            results = null
            when (exerciseType) {
                "高抬腳(側面)" -> highKneeCounter.reset()
                "左右跨步" -> sideStepCounter.reset()
                "腳尖對齊腳跟" -> {
                    toeToHeelCounter.reset()
                    heelMovementDetector.reset()
                    currentMovementStatus = null
                }
            }
            leftAngleHistory.clear()
            rightAngleHistory.clear()
            
            // 清理關鍵點歷史
            leftHipHistory.clear()
            leftKneeHistory.clear()
            leftAnkleHistory.clear()
            rightHipHistory.clear()
            rightKneeHistory.clear()
            rightAnkleHistory.clear()
        } catch (e: Exception) {
            android.util.Log.e("PoseOverlayView", "清理資源時發生錯誤: ${e.message}")
        }
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}