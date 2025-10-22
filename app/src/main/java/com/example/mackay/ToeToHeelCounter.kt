package com.example.mackay

import android.util.Log

/**
 * 腳尖對齊腳跟計次管理器
 * 檢測腳尖和腳跟的對齊動作
 */
class ToeToHeelCounter {
    
    companion object {
        private const val TAG = "ToeToHeelCounter"
        
        // 對齊檢測參數
        private const val ALIGNMENT_THRESHOLD = 0.02f  // 對齊閾值
        private const val MIN_DISTANCE = 0.1f         // 最小腳尖腳跟距離
        private const val MAX_DISTANCE = 0.4f         // 最大腳尖腳跟距離
        private const val STABLE_FRAMES = 3           // 穩定幀數
    }
    
    // 計數器
    private var leftAlignmentCount = 0
    private var rightAlignmentCount = 0
    private var totalAlignmentCount = 0
    
    // 對齊狀態追蹤
    private var leftAlignmentHistory = mutableListOf<Boolean>()
    private var rightAlignmentHistory = mutableListOf<Boolean>()
    private var leftWasAligned = false
    private var rightWasAligned = false
    
    /**
     * 處理腳尖和腳跟位置數據並進行對齊檢測
     */
    fun processToeHeelPositions(
        leftToeX: Float?, leftToeY: Float?,
        leftHeelX: Float?, leftHeelY: Float?,
        rightToeX: Float?, rightToeY: Float?,
        rightHeelX: Float?, rightHeelY: Float?
    ) {
        // 處理左腳對齊
        if (leftToeX != null && leftToeY != null && leftHeelX != null && leftHeelY != null) {
            val isAligned = checkAlignment(leftToeX, leftToeY, leftHeelX, leftHeelY)
            processAlignment(isAligned, isLeft = true)
        } else {
            // 左腳數據無效，如果正在對齊中則結束對齊但不計次
            if (leftWasAligned) {
                leftWasAligned = false
                Log.d(TAG, "左腳數據無效，結束對齊但不計次")
            }
        }
        
        // 處理右腳對齊
        if (rightToeX != null && rightToeY != null && rightHeelX != null && rightHeelY != null) {
            val isAligned = checkAlignment(rightToeX, rightToeY, rightHeelX, rightHeelY)
            processAlignment(isAligned, isLeft = false)
        } else {
            // 右腳數據無效，如果正在對齊中則結束對齊但不計次
            if (rightWasAligned) {
                rightWasAligned = false
                Log.d(TAG, "右腳數據無效，結束對齊但不計次")
            }
        }
    }
    
    /**
     * 檢查腳尖和腳跟是否對齊
     */
    private fun checkAlignment(toeX: Float, toeY: Float, heelX: Float, heelY: Float): Boolean {
        // 計算腳尖和腳跟的距離
        val distance = kotlin.math.sqrt((toeX - heelX) * (toeX - heelX) + (toeY - heelY) * (toeY - heelY))
        
        // 檢查距離是否在合理範圍內
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            return false
        }
        
        // 檢查是否在垂直線上對齊（Y座標相近）
        val yDifference = kotlin.math.abs(toeY - heelY)
        val isVerticallyAligned = yDifference < ALIGNMENT_THRESHOLD
        
        // 檢查是否在水平線上對齊（X座標相近）
        val xDifference = kotlin.math.abs(toeX - heelX)
        val isHorizontallyAligned = xDifference < ALIGNMENT_THRESHOLD
        
        // 對齊條件：垂直或水平對齊
        return isVerticallyAligned || isHorizontallyAligned
    }
    
    /**
     * 處理對齊狀態
     */
    private fun processAlignment(isAligned: Boolean, isLeft: Boolean) {
        if (isLeft) {
            // 左腳對齊邏輯
            leftAlignmentHistory.add(isAligned)
            if (leftAlignmentHistory.size > STABLE_FRAMES) {
                leftAlignmentHistory.removeAt(0)
            }
            
            // 檢查是否穩定對齊
            val isStableAligned = leftAlignmentHistory.size >= STABLE_FRAMES && 
                                 leftAlignmentHistory.all { it }
            
            if (isStableAligned && !leftWasAligned) {
                leftWasAligned = true
                Log.d(TAG, "左腳開始對齊")
            } else if (!isStableAligned && leftWasAligned) {
                leftWasAligned = false
                leftAlignmentCount++
                totalAlignmentCount++
                Log.d(TAG, "左腳對齊完成，總計: $leftAlignmentCount")
            }
        } else {
            // 右腳對齊邏輯
            rightAlignmentHistory.add(isAligned)
            if (rightAlignmentHistory.size > STABLE_FRAMES) {
                rightAlignmentHistory.removeAt(0)
            }
            
            // 檢查是否穩定對齊
            val isStableAligned = rightAlignmentHistory.size >= STABLE_FRAMES && 
                                 rightAlignmentHistory.all { it }
            
            if (isStableAligned && !rightWasAligned) {
                rightWasAligned = true
                Log.d(TAG, "右腳開始對齊")
            } else if (!isStableAligned && rightWasAligned) {
                rightWasAligned = false
                rightAlignmentCount++
                totalAlignmentCount++
                Log.d(TAG, "右腳對齊完成，總計: $rightAlignmentCount")
            }
        }
    }
    
    /**
     * 獲取計數結果
     */
    fun getCounts(): CountResult {
        return CountResult(
            leftAlignmentCount = leftAlignmentCount,
            rightAlignmentCount = rightAlignmentCount,
            totalAlignmentCount = totalAlignmentCount
        )
    }
    
    /**
     * 重置計數器
     */
    fun reset() {
        leftAlignmentCount = 0
        rightAlignmentCount = 0
        totalAlignmentCount = 0
        leftAlignmentHistory.clear()
        rightAlignmentHistory.clear()
        leftWasAligned = false
        rightWasAligned = false
        Log.d(TAG, "腳尖對齊腳跟計數器已重置")
    }
    
    /**
     * 計數結果數據類
     */
    data class CountResult(
        val leftAlignmentCount: Int,
        val rightAlignmentCount: Int,
        val totalAlignmentCount: Int
    )
}

