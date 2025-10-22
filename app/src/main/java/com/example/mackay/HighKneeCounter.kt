package com.example.mackay

import android.util.Log

/**
 * 高抬腿計次管理器
 * 實現防止重複計次、最小角度追蹤和雙腿獨立計次
 */
class HighKneeCounter {
    
    companion object {
        private const val TAG = "HighKneeCounter"
        
        // 角度範圍設定
        private const val STANDARD_ANGLE = 90.0
        private const val RESET_ANGLE = 145.0
        private const val SUCCESS_MIN = 72.0  // 標準角度 ± 20%
        private const val SUCCESS_MAX = 108.0
        private const val FAILURE_MIN_LOW = 54.0   // 54°-72°
        private const val FAILURE_MAX_LOW = 72.0
        private const val FAILURE_MIN_HIGH = 108.0 // 108°-126°
        private const val FAILURE_MAX_HIGH = 126.0
    }
    
    // 計數器
    private var leftSuccessCount = 0
    private var rightSuccessCount = 0
    private var leftFailureCount = 0
    private var rightFailureCount = 0
    private var leftInvalidCount = 0
    private var rightInvalidCount = 0
    private var totalSuccessCount = 0
    private var totalFailureCount = 0
    private var totalInvalidCount = 0
    
    // 計次許可控制 - 防止重複計次
    private var leftCanCount = true
    private var rightCanCount = true
    
    // 最小角度追蹤
    private var leftCurrentMinAngle = Double.POSITIVE_INFINITY
    private var rightCurrentMinAngle = Double.POSITIVE_INFINITY
    private var leftMinAnglesLog = mutableListOf<Double>()  // 記錄每個動作週期的最小角度
    private var rightMinAnglesLog = mutableListOf<Double>()
    private var leftInMotion = false  // 是否在動作中
    private var rightInMotion = false
    
    /**
     * 處理角度數據並進行計次邏輯
     */
    fun processAngles(leftAngle: Double?, rightAngle: Double?) {
        // 如果兩個角度都無效，直接返回，不進行任何處理
        if (leftAngle == null && rightAngle == null) {
            Log.d(TAG, "兩個角度都無效，跳過計次邏輯")
            return
        }
        
        // 處理左腿
        leftAngle?.let { angle ->
            processLegAngle(angle, isLeft = true)
        } ?: run {
            // 左腿角度無效，如果正在動作中則結束動作但不計次
            if (leftInMotion) {
                leftInMotion = false
                leftCurrentMinAngle = Double.POSITIVE_INFINITY
                Log.d(TAG, "左腿角度無效，結束動作但不計次")
            }
        }
        
        // 處理右腿
        rightAngle?.let { angle ->
            processLegAngle(angle, isLeft = false)
        } ?: run {
            // 右腿角度無效，如果正在動作中則結束動作但不計次
            if (rightInMotion) {
                rightInMotion = false
                rightCurrentMinAngle = Double.POSITIVE_INFINITY
                Log.d(TAG, "右腿角度無效，結束動作但不計次")
            }
        }
    }
    
    /**
     * 處理單腿的角度邏輯
     */
    private fun processLegAngle(angle: Double, isLeft: Boolean) {
        if (isLeft) {
            // 左腿邏輯
            if (angle < RESET_ANGLE) {
                // 開始動作或持續動作中
                if (!leftInMotion) {
                    leftInMotion = true
                    leftCurrentMinAngle = angle
                    Log.d(TAG, "左腿動作開始，當前角度: ${angle.format(1)}°")
                } else {
                    // 更新最小角度
                    if (angle < leftCurrentMinAngle) {
                        leftCurrentMinAngle = angle
                    }
                }
            } else {
                // 動作結束：角度 >= reset_angle
                if (leftInMotion) {
                    leftInMotion = false
                    leftMinAnglesLog.add(leftCurrentMinAngle)
                    Log.d(TAG, "左腿動作結束，最小角度: ${leftCurrentMinAngle.format(1)}°")
                    
                    // 根據最小角度判斷成功或失敗（只有在can_count為true時才計次）
                    Log.d(TAG, "左腿動作結束檢查: can_count=$leftCanCount, 最小角度=${leftCurrentMinAngle.format(1)}°")
                    if (leftCanCount) {
                        Log.d(TAG, "左腿角度判斷: ${leftCurrentMinAngle.format(1)}°")
                        Log.d(TAG, "  - 成功範圍(72-108): ${isSuccessRange(leftCurrentMinAngle)}")
                        Log.d(TAG, "  - 失敗範圍(54-72或108-126): ${isFailureRange(leftCurrentMinAngle)}")
                        
                        when {
                            isSuccessRange(leftCurrentMinAngle) -> {
                                leftSuccessCount++
                                totalSuccessCount++
                                leftCanCount = false
                                Log.d(TAG, "  → 左腿動作成功！最小角度: ${leftCurrentMinAngle.format(1)}°")
                            }
                            isFailureRange(leftCurrentMinAngle) -> {
                                leftFailureCount++
                                totalFailureCount++
                                leftCanCount = false
                                Log.d(TAG, "  → 左腿動作失敗！最小角度: ${leftCurrentMinAngle.format(1)}°")
                            }
                            else -> {
                                leftInvalidCount++
                                totalInvalidCount++
                                leftCanCount = false
                                Log.d(TAG, "  → 左腿動作無效！最小角度: ${leftCurrentMinAngle.format(1)}°")
                            }
                        }
                    } else {
                        Log.d(TAG, "  → 左腿動作不計次（can_count=false），最小角度: ${leftCurrentMinAngle.format(1)}°")
                    }
                    leftCurrentMinAngle = Double.POSITIVE_INFINITY
                }
                
                // 當左腿伸直到指定角度以上時，重置計次許可
                if (angle > RESET_ANGLE) {
                    leftCanCount = true
                }
            }
        } else {
            // 右腿邏輯
            if (angle < RESET_ANGLE) {
                // 開始動作或持續動作中
                if (!rightInMotion) {
                    rightInMotion = true
                    rightCurrentMinAngle = angle
                    Log.d(TAG, "右腿動作開始，當前角度: ${angle.format(1)}°")
                } else {
                    // 更新最小角度
                    if (angle < rightCurrentMinAngle) {
                        rightCurrentMinAngle = angle
                    }
                }
            } else {
                // 動作結束：角度 >= reset_angle
                if (rightInMotion) {
                    rightInMotion = false
                    rightMinAnglesLog.add(rightCurrentMinAngle)
                    Log.d(TAG, "右腿動作結束，最小角度: ${rightCurrentMinAngle.format(1)}°")
                    
                    // 根據最小角度判斷成功或失敗（只有在can_count為true時才計次）
                    Log.d(TAG, "右腿動作結束檢查: can_count=$rightCanCount, 最小角度=${rightCurrentMinAngle.format(1)}°")
                    if (rightCanCount) {
                        Log.d(TAG, "右腿角度判斷: ${rightCurrentMinAngle.format(1)}°")
                        Log.d(TAG, "  - 成功範圍(72-108): ${isSuccessRange(rightCurrentMinAngle)}")
                        Log.d(TAG, "  - 失敗範圍(54-72或108-126): ${isFailureRange(rightCurrentMinAngle)}")
                        
                        when {
                            isSuccessRange(rightCurrentMinAngle) -> {
                                rightSuccessCount++
                                totalSuccessCount++
                                rightCanCount = false
                                Log.d(TAG, "  → 右腿動作成功！最小角度: ${rightCurrentMinAngle.format(1)}°")
                            }
                            isFailureRange(rightCurrentMinAngle) -> {
                                rightFailureCount++
                                totalFailureCount++
                                rightCanCount = false
                                Log.d(TAG, "  → 右腿動作失敗！最小角度: ${rightCurrentMinAngle.format(1)}°")
                            }
                            else -> {
                                rightInvalidCount++
                                totalInvalidCount++
                                rightCanCount = false
                                Log.d(TAG, "  → 右腿動作無效！最小角度: ${rightCurrentMinAngle.format(1)}°")
                            }
                        }
                    } else {
                        Log.d(TAG, "  → 右腿動作不計次（can_count=false），最小角度: ${rightCurrentMinAngle.format(1)}°")
                    }
                    rightCurrentMinAngle = Double.POSITIVE_INFINITY
                }
                
                // 當右腿伸直到指定角度以上時，重置計次許可
                if (angle > RESET_ANGLE) {
                    rightCanCount = true
                }
            }
        }
    }
    
    /**
     * 判斷角度是否在成功範圍內
     */
    private fun isSuccessRange(angle: Double): Boolean {
        return angle >= SUCCESS_MIN && angle <= SUCCESS_MAX
    }
    
    /**
     * 判斷角度是否在失敗範圍內
     */
    private fun isFailureRange(angle: Double): Boolean {
        return (angle >= FAILURE_MIN_LOW && angle < FAILURE_MAX_LOW) || 
               (angle > FAILURE_MIN_HIGH && angle <= FAILURE_MAX_HIGH)
    }
    
    /**
     * 獲取計數結果
     */
    fun getCounts(): CountResult {
        return CountResult(
            leftSuccessCount = leftSuccessCount,
            rightSuccessCount = rightSuccessCount,
            totalSuccessCount = totalSuccessCount,
            leftFailureCount = leftFailureCount,
            rightFailureCount = rightFailureCount,
            totalFailureCount = totalFailureCount,
            leftInvalidCount = leftInvalidCount,
            rightInvalidCount = rightInvalidCount,
            totalInvalidCount = totalInvalidCount
        )
    }
    
    /**
     * 重置計數器
     */
    fun reset() {
        leftSuccessCount = 0
        rightSuccessCount = 0
        leftFailureCount = 0
        rightFailureCount = 0
        leftInvalidCount = 0
        rightInvalidCount = 0
        totalSuccessCount = 0
        totalFailureCount = 0
        totalInvalidCount = 0
        leftCanCount = true
        rightCanCount = true
        
        // 重置最小角度追蹤
        leftCurrentMinAngle = Double.POSITIVE_INFINITY
        rightCurrentMinAngle = Double.POSITIVE_INFINITY
        leftMinAnglesLog.clear()
        rightMinAnglesLog.clear()
        leftInMotion = false
        rightInMotion = false
        
        Log.d(TAG, "計數器已重置")
    }
    
    /**
     * 獲取最小角度日誌
     */
    fun getMinAnglesLog(): MinAnglesLog {
        return MinAnglesLog(
            leftMinAngles = leftMinAnglesLog.toList(),
            rightMinAngles = rightMinAnglesLog.toList(),
            totalLeftActions = leftMinAnglesLog.size,
            totalRightActions = rightMinAnglesLog.size
        )
    }
    
    /**
     * 打印最小角度摘要
     */
    fun printMinAnglesSummary() {
        Log.d(TAG, "\n=== 最小角度摘要 ===")
        if (leftMinAnglesLog.isNotEmpty()) {
            Log.d(TAG, "左腿動作最小角度: ${leftMinAnglesLog.map { "${it.format(1)}°" }}")
            Log.d(TAG, "左腿平均最小角度: ${(leftMinAnglesLog.sum() / leftMinAnglesLog.size).format(1)}°")
            Log.d(TAG, "左腿最小角度: ${leftMinAnglesLog.minOrNull()?.format(1)}°")
            Log.d(TAG, "左腿最大最小角度: ${leftMinAnglesLog.maxOrNull()?.format(1)}°")
        } else {
            Log.d(TAG, "左腿: 無動作記錄")
        }
        
        if (rightMinAnglesLog.isNotEmpty()) {
            Log.d(TAG, "右腿動作最小角度: ${rightMinAnglesLog.map { "${it.format(1)}°" }}")
            Log.d(TAG, "右腿平均最小角度: ${(rightMinAnglesLog.sum() / rightMinAnglesLog.size).format(1)}°")
            Log.d(TAG, "右腿最小角度: ${rightMinAnglesLog.minOrNull()?.format(1)}°")
            Log.d(TAG, "右腿最大最小角度: ${rightMinAnglesLog.maxOrNull()?.format(1)}°")
        } else {
            Log.d(TAG, "右腿: 無動作記錄")
        }
        Log.d(TAG, "==================\n")
    }
    
    /**
     * 計數結果數據類
     */
    data class CountResult(
        val leftSuccessCount: Int,
        val rightSuccessCount: Int,
        val totalSuccessCount: Int,
        val leftFailureCount: Int,
        val rightFailureCount: Int,
        val totalFailureCount: Int,
        val leftInvalidCount: Int,
        val rightInvalidCount: Int,
        val totalInvalidCount: Int
    )
    
    /**
     * 最小角度日誌數據類
     */
    data class MinAnglesLog(
        val leftMinAngles: List<Double>,
        val rightMinAngles: List<Double>,
        val totalLeftActions: Int,
        val totalRightActions: Int
    )
}

/**
 * Double擴展函數，用於格式化數字
 */
private fun Double.format(digits: Int) = "%.${digits}f".format(this)
