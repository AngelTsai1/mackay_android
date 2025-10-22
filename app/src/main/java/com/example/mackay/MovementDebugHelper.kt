package com.example.mackay

import android.util.Log

/**
 * 移動檢測調試助手
 * 用於測試和驗證移動檢測功能
 */
class MovementDebugHelper {
    
    companion object {
        private const val TAG = "MovementDebugHelper"
    }
    
    private val heelMovementDetector = HeelMovementDetector()
    private var frameCount = 0
    
    /**
     * 模擬腳跟移動數據進行測試
     */
    fun simulateMovementTest() {
        Log.d(TAG, "開始移動檢測測試...")
        
        // 重置檢測器
        heelMovementDetector.reset()
        frameCount = 0
        
        // 模擬靜止狀態（前10幀）
        for (i in 1..10) {
            frameCount++
            val heelData = createTestHeelData(
                leftHeelX = 0.5f,  // 靜止位置
                rightHeelX = 0.6f,
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 $frameCount - 靜止測試: 左=${status.leftHeelDisplayStatus}, 右=${status.rightHeelDisplayStatus}")
        }
        
        // 模擬左腳移動（11-20幀）
        for (i in 11..20) {
            frameCount++
            val heelData = createTestHeelData(
                leftHeelX = 0.5f + (i - 10) * 0.01f,  // 逐漸移動
                rightHeelX = 0.6f,  // 右腳保持靜止
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 $frameCount - 左腳移動測試: 左=${status.leftHeelDisplayStatus}, 右=${status.rightHeelDisplayStatus}")
        }
        
        // 模擬右腳移動（21-30幀）
        for (i in 21..30) {
            frameCount++
            val heelData = createTestHeelData(
                leftHeelX = 0.6f,  // 左腳保持靜止
                rightHeelX = 0.6f + (i - 20) * 0.01f,  // 逐漸移動
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 $frameCount - 右腳移動測試: 左=${status.leftHeelDisplayStatus}, 右=${status.rightHeelDisplayStatus}")
        }
        
        // 模擬雙腳移動（31-40幀）
        for (i in 31..40) {
            frameCount++
            val heelData = createTestHeelData(
                leftHeelX = 0.6f + (i - 30) * 0.01f,  // 左腳移動
                rightHeelX = 0.7f + (i - 30) * 0.01f,  // 右腳移動
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 $frameCount - 雙腳移動測試: 左=${status.leftHeelDisplayStatus}, 右=${status.rightHeelDisplayStatus}")
        }
        
        // 模擬回到靜止狀態（41-50幀）
        for (i in 41..50) {
            frameCount++
            val heelData = createTestHeelData(
                leftHeelX = 0.7f,  // 保持靜止
                rightHeelX = 0.8f,  // 保持靜止
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 $frameCount - 回到靜止測試: 左=${status.leftHeelDisplayStatus}, 右=${status.rightHeelDisplayStatus}")
        }
        
        Log.d(TAG, "移動檢測測試完成")
        Log.d(TAG, "統計信息: ${heelMovementDetector.getStatistics()}")
    }
    
    /**
     * 創建測試用的腳跟數據
     */
    private fun createTestHeelData(
        leftHeelX: Float,
        rightHeelX: Float,
        leftFootLength: Float,
        rightFootLength: Float
    ): HeelMovementDetector.HeelData {
        return HeelMovementDetector.HeelData(
            frame = frameCount,
            timestamp = frameCount.toFloat() / 30f,  // 假設30fps
            // 標準化坐標
            leftHeelX = leftHeelX,
            leftHeelY = 0.8f,
            leftHeelZ = 0.0f,
            rightHeelX = rightHeelX,
            rightHeelY = 0.8f,
            rightHeelZ = 0.0f,
            leftToeX = leftHeelX + 0.05f,
            leftToeY = 0.8f,
            leftToeZ = 0.0f,
            rightToeX = rightHeelX + 0.05f,
            rightToeY = 0.8f,
            rightToeZ = 0.0f,
            leftFootLength = leftFootLength,
            rightFootLength = rightFootLength,
            // 世界坐標
            worldLeftHeelZ = 0.0f,
            worldRightHeelZ = 0.0f,
            worldLeftToeZ = 0.0f,
            worldRightToeZ = 0.0f,
            worldLeftShoulderZ = 0.0f,
            worldRightShoulderZ = 0.0f
        )
    }
    
    /**
     * 測試移動閾值計算
     */
    fun testMovementThreshold() {
        Log.d(TAG, "測試移動閾值計算...")
        
        val footLengths = listOf(0.05f, 0.1f, 0.15f, 0.2f)
        
        footLengths.forEach { footLength ->
            val threshold = footLength * 0.015f
            Log.d(TAG, "腳長: $footLength, 移動閾值: $threshold")
        }
    }
    
    /**
     * 測試連續幀數檢測
     */
    fun testConsecutiveFramesDetection() {
        Log.d(TAG, "測試連續幀數檢測...")
        
        heelMovementDetector.reset()
        
        // 測試需要3幀連續移動才觸發
        val testMovements = listOf(
            false, false, true, true, false,  // 只有2幀移動，不應該觸發
            true, true, true, false, false,   // 3幀移動，應該觸發
            false, false, false, false, false // 回到靜止
        )
        
        testMovements.forEachIndexed { index, isMoving ->
            val heelData = createTestHeelData(
                leftHeelX = if (isMoving) 0.5f + index * 0.01f else 0.5f,
                rightHeelX = 0.6f,
                leftFootLength = 0.1f,
                rightFootLength = 0.1f
            )
            
            val status = heelMovementDetector.detectHeelMovement(heelData)
            Log.d(TAG, "幀 ${index + 1} - 移動: $isMoving, 狀態: ${status.leftHeelDisplayStatus}")
        }
    }
}
