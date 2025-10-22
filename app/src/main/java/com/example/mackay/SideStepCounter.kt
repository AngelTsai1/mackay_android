package com.example.mackay

import android.util.Log

/**
 * 左右跨步計次管理器
 * 檢測側向跨步動作，基於髖關節的左右移動
 */
class SideStepCounter {
    
    companion object {
        private const val TAG = "SideStepCounter"
        
        // 跨步檢測參數
        private const val MIN_HIP_DISTANCE = 0.05f  // 最小髖關節移動距離
        private const val MAX_HIP_DISTANCE = 0.3f  // 最大髖關節移動距離
        private const val CENTER_THRESHOLD = 0.02f // 中心位置閾值
        private const val MOTION_FRAMES = 5        // 動作持續幀數
    }
    
    // 計數器
    private var leftStepCount = 0
    private var rightStepCount = 0
    private var totalStepCount = 0
    
    // 跨步狀態追蹤
    private var leftHipHistory = mutableListOf<Float>()
    private var rightHipHistory = mutableListOf<Float>()
    private var leftInStep = false
    private var rightInStep = false
    private var leftStepDirection = 0  // -1: 向左, 0: 中心, 1: 向右
    private var rightStepDirection = 0
    
    // 中心位置基準
    private var leftCenterPosition = 0.5f
    private var rightCenterPosition = 0.5f
    
    /**
     * 處理髖關節位置數據並進行跨步檢測
     */
    fun processHipPositions(leftHipX: Float?, rightHipX: Float?) {
        // 如果兩個髖關節位置都無效，直接返回
        if (leftHipX == null && rightHipX == null) {
            Log.d(TAG, "兩個髖關節位置都無效，跳過跨步檢測")
            return
        }
        
        // 處理左髖關節
        leftHipX?.let { x ->
            processHipPosition(x, isLeft = true)
        } ?: run {
            // 左髖關節位置無效，如果正在跨步中則結束跨步但不計次
            if (leftInStep) {
                leftInStep = false
                leftStepDirection = 0
                Log.d(TAG, "左髖關節位置無效，結束跨步但不計次")
            }
        }
        
        // 處理右髖關節
        rightHipX?.let { x ->
            processHipPosition(x, isLeft = false)
        } ?: run {
            // 右髖關節位置無效，如果正在跨步中則結束跨步但不計次
            if (rightInStep) {
                rightInStep = false
                rightStepDirection = 0
                Log.d(TAG, "右髖關節位置無效，結束跨步但不計次")
            }
        }
    }
    
    /**
     * 處理單側髖關節位置邏輯
     */
    private fun processHipPosition(hipX: Float, isLeft: Boolean) {
        if (isLeft) {
            // 左髖關節邏輯
            leftHipHistory.add(hipX)
            if (leftHipHistory.size > MOTION_FRAMES) {
                leftHipHistory.removeAt(0)
            }
            
            // 計算移動距離
            val currentPosition = hipX
            val centerPosition = leftCenterPosition
            val distance = kotlin.math.abs(currentPosition - centerPosition)
            
            // 檢測跨步動作
            if (distance > MIN_HIP_DISTANCE && distance < MAX_HIP_DISTANCE) {
                if (!leftInStep) {
                    leftInStep = true
                    leftStepDirection = if (currentPosition < centerPosition) -1 else 1
                    Log.d(TAG, "左跨步開始，方向: ${if (leftStepDirection == -1) "左" else "右"}，距離: ${String.format("%.3f", distance)}")
                }
            } else if (distance <= CENTER_THRESHOLD) {
                // 回到中心位置，結束跨步
                if (leftInStep) {
                    leftInStep = false
                    leftStepCount++
                    totalStepCount++
                    Log.d(TAG, "左跨步完成，總計: $leftStepCount")
                    leftStepDirection = 0
                }
            }
        } else {
            // 右髖關節邏輯
            rightHipHistory.add(hipX)
            if (rightHipHistory.size > MOTION_FRAMES) {
                rightHipHistory.removeAt(0)
            }
            
            // 計算移動距離
            val currentPosition = hipX
            val centerPosition = rightCenterPosition
            val distance = kotlin.math.abs(currentPosition - centerPosition)
            
            // 檢測跨步動作
            if (distance > MIN_HIP_DISTANCE && distance < MAX_HIP_DISTANCE) {
                if (!rightInStep) {
                    rightInStep = true
                    rightStepDirection = if (currentPosition < centerPosition) -1 else 1
                    Log.d(TAG, "右跨步開始，方向: ${if (rightStepDirection == -1) "左" else "右"}，距離: ${String.format("%.3f", distance)}")
                }
            } else if (distance <= CENTER_THRESHOLD) {
                // 回到中心位置，結束跨步
                if (rightInStep) {
                    rightInStep = false
                    rightStepCount++
                    totalStepCount++
                    Log.d(TAG, "右跨步完成，總計: $rightStepCount")
                    rightStepDirection = 0
                }
            }
        }
    }
    
    /**
     * 更新中心位置基準
     */
    fun updateCenterPositions(leftHipX: Float?, rightHipX: Float?) {
        leftHipX?.let { leftCenterPosition = it }
        rightHipX?.let { rightCenterPosition = it }
        Log.d(TAG, "更新中心位置 - 左: ${String.format("%.3f", leftCenterPosition)}, 右: ${String.format("%.3f", rightCenterPosition)}")
    }
    
    /**
     * 獲取計數結果
     */
    fun getCounts(): CountResult {
        return CountResult(
            leftStepCount = leftStepCount,
            rightStepCount = rightStepCount,
            totalStepCount = totalStepCount
        )
    }
    
    /**
     * 重置計數器
     */
    fun reset() {
        leftStepCount = 0
        rightStepCount = 0
        totalStepCount = 0
        leftHipHistory.clear()
        rightHipHistory.clear()
        leftInStep = false
        rightInStep = false
        leftStepDirection = 0
        rightStepDirection = 0
        leftCenterPosition = 0.5f
        rightCenterPosition = 0.5f
        Log.d(TAG, "跨步計數器已重置")
    }
    
    /**
     * 計數結果數據類
     */
    data class CountResult(
        val leftStepCount: Int,
        val rightStepCount: Int,
        val totalStepCount: Int
    )
}

