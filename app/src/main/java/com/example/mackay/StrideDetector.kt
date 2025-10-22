package com.example.mackay

import android.util.Log
import java.util.*

/**
 * 左右跨步動作偵測器
 * 負責偵測左右跨步動作並進行計次
 * 參考Python版本的stride_detector.py邏輯實現
 */
class StrideDetector {
    
    companion object {
        private const val TAG = "StrideDetector"
        
        // 角度範圍設定 - 根據新要求修改
        private const val STANDARD_ANGLE = 120.0  // 標準髖-膝-踝角度
        private const val RESET_THRESHOLD = 145.0  // 重置計數許可的膝蓋角度閾值
        
        // 新的角度範圍設定
        // 成功：120 ± 10% = 108° ~ 132°
        private const val SUCCESS_MIN = STANDARD_ANGLE * 0.90  // 120° * 0.9 = 108°
        private const val SUCCESS_MAX = STANDARD_ANGLE * 1.10  // 120° * 1.1 = 132°
        
        // 失敗：120 ± 15% 但不含成功的部分
        // 失敗範圍1：102° ~ 108° (不含108°)
        // 失敗範圍2：132° ~ 138° (不含132°)
        private const val FAILURE_MIN_LOW = STANDARD_ANGLE * 0.85   // 120° * 0.85 = 102°
        private const val FAILURE_MAX_LOW = SUCCESS_MIN             // 108°
        private const val FAILURE_MIN_HIGH = SUCCESS_MAX            // 132°
        private const val FAILURE_MAX_HIGH = STANDARD_ANGLE * 1.15  // 120° * 1.15 = 138°
        
        // 無效：失敗到重置角度的區間 (138° ~ 145°)
        private const val INVALID_MIN = FAILURE_MAX_HIGH  // 138°
        private const val INVALID_MAX = RESET_THRESHOLD    // 145°
        
        // 平滑設定
        private const val SMOOTHING_SIZE = 5  // 角度平滑窗口大小
    }
    
    // 計數器
    private var leftStrideCount = 0
    private var rightStrideCount = 0
    private var totalStrideCount = 0
    private var leftSuccessCount = 0
    private var rightSuccessCount = 0
    private var leftFailureCount = 0
    private var rightFailureCount = 0
    private var leftInvalidCount = 0
    private var rightInvalidCount = 0
    
    // 防重複計數標誌 - 參考Python版本的can_count邏輯
    private var leftCanCount = true
    private var rightCanCount = true
    
    // 最小角度追蹤 - 參考Python版本
    private var leftMinAngle = Double.POSITIVE_INFINITY
    private var rightMinAngle = Double.POSITIVE_INFINITY
    private var leftMinAngleFrame = 0
    private var rightMinAngleFrame = 0
    
    // 角度歷史記錄用於平滑 - 參考Python版本
    private val leftAngles = mutableListOf<Double>()
    private val rightAngles = mutableListOf<Double>()
    
    // 動作歷史記錄 - 記錄每次動作的結果
    private val actionHistory = mutableListOf<ActionRecord>()
    
    // 幀計數器
    private var frameCount = 0
    
    init {
        Log.i(TAG, "StrideDetector 初始化完成")
        Log.i(TAG, "標準角度: ${STANDARD_ANGLE}°")
        Log.i(TAG, "重置閾值: ${RESET_THRESHOLD}°")
        Log.i(TAG, "成功範圍: ${SUCCESS_MIN.format(1)}° ~ ${SUCCESS_MAX.format(1)}°")
        Log.i(TAG, "失敗範圍: ${FAILURE_MIN_LOW.format(1)}° ~ ${FAILURE_MAX_LOW.format(1)}° 和 ${FAILURE_MIN_HIGH.format(1)}° ~ ${FAILURE_MAX_HIGH.format(1)}°")
        Log.i(TAG, "無效範圍: ${INVALID_MIN.format(1)}° ~ ${INVALID_MAX.format(1)}°")
        Log.i(TAG, "平滑大小: $SMOOTHING_SIZE")
    }
    
    /**
     * 處理單幀的左右跨步動作偵測
     * 參考Python版本的process_frame方法
     */
    fun processFrame(leftAngle: Double?, rightAngle: Double?): ProcessResult {
        try {
            frameCount++
            
            // 處理右腿跨步 - 參考Python版本的右腿邏輯
            val rightResult = rightAngle?.let { angle ->
                processLegAngle(angle, isLeft = false)
            } ?: LegProcessResult.NO_ANGLE
            
            // 處理左腿跨步 - 參考Python版本的左腿邏輯
            val leftResult = leftAngle?.let { angle ->
                processLegAngle(angle, isLeft = true)
            } ?: LegProcessResult.NO_ANGLE
            
            return ProcessResult(
                leftResult = leftResult,
                rightResult = rightResult,
                leftAngle = leftAngle,
                rightAngle = rightAngle
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "處理幀時發生錯誤: ${e.message}", e)
            return ProcessResult(
                leftResult = LegProcessResult.ERROR,
                rightResult = LegProcessResult.ERROR,
                leftAngle = leftAngle,
                rightAngle = rightAngle
            )
        }
    }
    
    /**
     * 處理單腿的角度邏輯
     * 參考Python版本的processLegAngle邏輯
     */
    private fun processLegAngle(angle: Double, isLeft: Boolean): LegProcessResult {
        val smoothAngle = smoothAngle(angle, isLeft)
        
        if (isLeft) {
            // 左腿邏輯 - 參考Python版本
            // 追蹤最小角度
            if (smoothAngle < leftMinAngle) {
                leftMinAngle = smoothAngle
                leftMinAngleFrame = frameCount
            }
            
            // 左腿跨步計數邏輯：當角度在有效範圍內時立即計數（成功+失敗+無效：102°~145°）
            if (isInValidRange(smoothAngle) && leftCanCount) {
                leftStrideCount++
                totalStrideCount++
                leftCanCount = false
                Log.d(TAG, "左腿跨步計數: ${smoothAngle.format(1)}°")
                return LegProcessResult.COUNTED
            }
            
            // 當左腿伸直到閾值以上時，重置計數許可並記錄完整動作結果
            if (smoothAngle > RESET_THRESHOLD) {
                if (!leftCanCount) {
                    // 記錄完整動作的最小角度和判斷結果
                    logCompleteActionResult("左腿", leftMinAngle, leftMinAngleFrame)
                }
                leftCanCount = true
                // 重置最小角度追蹤
                leftMinAngle = Double.POSITIVE_INFINITY
                leftMinAngleFrame = 0
                return LegProcessResult.RESET
            }
            
            return LegProcessResult.TRACKING
            
        } else {
            // 右腿邏輯 - 參考Python版本
            // 追蹤最小角度
            if (smoothAngle < rightMinAngle) {
                rightMinAngle = smoothAngle
                rightMinAngleFrame = frameCount
            }
            
            // 右腿跨步計數邏輯：當角度在有效範圍內時立即計數（成功+失敗+無效：102°~145°）
            if (isInValidRange(smoothAngle) && rightCanCount) {
                rightStrideCount++
                totalStrideCount++
                rightCanCount = false
                Log.d(TAG, "右腿跨步計數: ${smoothAngle.format(1)}°")
                return LegProcessResult.COUNTED
            }
            
            // 當右腿伸直到閾值以上時，重置計數許可並記錄完整動作結果
            if (smoothAngle > RESET_THRESHOLD) {
                if (!rightCanCount) {
                    // 記錄完整動作的最小角度和判斷結果
                    logCompleteActionResult("右腿", rightMinAngle, rightMinAngleFrame)
                }
                rightCanCount = true
                // 重置最小角度追蹤
                rightMinAngle = Double.POSITIVE_INFINITY
                rightMinAngleFrame = 0
                return LegProcessResult.RESET
            }
            
            return LegProcessResult.TRACKING
        }
    }
    
    /**
     * 角度平滑算法 - 參考Python版本的smooth_angle函數
     */
    private fun smoothAngle(angle: Double, isLeft: Boolean): Double {
        val angleList = if (isLeft) leftAngles else rightAngles
        
        // 添加新角度
        angleList.add(angle)
        
        // 保持平滑窗口大小
        if (angleList.size > SMOOTHING_SIZE) {
            angleList.removeAt(0)
        }
        
        // 計算平均值
        return angleList.average()
    }
    
    /**
     * 記錄完整動作的最小角度和判斷結果
     * 參考Python版本的log_complete_action_result方法
     */
    private fun logCompleteActionResult(legSide: String, minAngle: Double, frameNumber: Int) {
        if (minAngle == Double.POSITIVE_INFINITY) {
            return
        }
        
        // 判斷結果並更新計數 - 根據新的角度範圍設定
        val result = when {
            isSuccessRange(minAngle) -> {
                if (legSide == "左腿") {
                    leftSuccessCount++
                } else {
                    rightSuccessCount++
                }
                "成功"
            }
            isFailureRange(minAngle) -> {
                if (legSide == "左腿") {
                    leftFailureCount++
                } else {
                    rightFailureCount++
                }
                "失敗"
            }
            isInvalidRange(minAngle) -> {
                if (legSide == "左腿") {
                    leftInvalidCount++
                } else {
                    rightInvalidCount++
                }
                "無效"
            }
            else -> {
                // 超出所有範圍的動作不計入任何類別
                "超出範圍"
            }
        }
        
        // 記錄完整動作結果
        Log.i(TAG, "${legSide}完整動作最小角度: ${minAngle.format(1)}° (幀: $frameNumber): $result")
        
        // 添加到動作歷史記錄
        actionHistory.add(ActionRecord(
            actionNumber = actionHistory.size + 1,
            legSide = legSide,
            angle = minAngle,
            result = result,
            frameNumber = frameNumber
        ))
    }
    
    /**
     * 判斷角度是否在計數範圍內（參考Python版本：102°~138°）
     */
    private fun isCountRange(angle: Double): Boolean {
        return angle >= FAILURE_MIN_LOW && angle <= FAILURE_MAX_HIGH
    }
    
    /**
     * 判斷角度是否在有效範圍內（成功+失敗+無效）
     */
    private fun isInValidRange(angle: Double): Boolean {
        return isSuccessRange(angle) || isFailureRange(angle) || isInvalidRange(angle)
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
     * 判斷角度是否在無效範圍內
     */
    private fun isInvalidRange(angle: Double): Boolean {
        return angle >= INVALID_MIN && angle <= INVALID_MAX
    }
    
    /**
     * 獲取跨步計數結果
     * 參考Python版本的get_counts方法
     */
    fun getCounts(): StrideCountResult {
        return StrideCountResult(
            leftTotal = leftStrideCount,
            rightTotal = rightStrideCount,
            total = totalStrideCount,
            leftSuccess = leftSuccessCount,
            rightSuccess = rightSuccessCount,
            leftFailure = leftFailureCount,
            rightFailure = rightFailureCount,
            leftInvalid = leftInvalidCount,
            rightInvalid = rightInvalidCount
        )
    }
    
    /**
     * 設定跨步計數閾值
     * 參考Python版本的set_thresholds方法
     */
    fun setThresholds(standardAngle: Double? = null, resetThreshold: Double? = null) {
        if (standardAngle != null) {
            val oldStandard = STANDARD_ANGLE
            Log.i(TAG, "標準角度已更新: ${oldStandard}° -> ${standardAngle}°")
            // 注意：這裡需要重新計算成功和失敗範圍，但由於是const，實際應用中需要重構
        }
        
        if (resetThreshold != null) {
            val oldReset = RESET_THRESHOLD
            Log.i(TAG, "重置閾值已更新: ${oldReset}° -> ${resetThreshold}°")
        }
    }
    
    /**
     * 獲取當前閾值設定
     * 參考Python版本的get_thresholds方法
     */
    fun getThresholds(): ThresholdSettings {
        return ThresholdSettings(
            standardAngle = STANDARD_ANGLE,
            resetThreshold = RESET_THRESHOLD,
            successRange = Pair(SUCCESS_MIN, SUCCESS_MAX),
            failureRange = Pair(FAILURE_MIN_LOW, FAILURE_MAX_HIGH)
        )
    }
    
    /**
     * 重置計數器
     * 參考Python版本的reset方法
     */
    fun reset() {
        // 記錄重置前的計數
        val oldCounts = getCounts()
        Log.i(TAG, "計數器已重置，重置前計數: $oldCounts")
        
        leftStrideCount = 0
        rightStrideCount = 0
        totalStrideCount = 0
        leftSuccessCount = 0
        rightSuccessCount = 0
        leftFailureCount = 0
        rightFailureCount = 0
        leftInvalidCount = 0
        rightInvalidCount = 0
        leftCanCount = true
        rightCanCount = true
        leftAngles.clear()
        rightAngles.clear()
        actionHistory.clear()
        
        // 重置最小角度追蹤
        leftMinAngle = Double.POSITIVE_INFINITY
        rightMinAngle = Double.POSITIVE_INFINITY
        leftMinAngleFrame = 0
        rightMinAngleFrame = 0
    }
    
    /**
     * 生成詳細的調試報告
     * 參考Python版本的generate_debug_report方法
     */
    fun generateDebugReport(): String {
        val counts = getCounts()
        val thresholds = getThresholds()
        
        val totalActions = counts.leftSuccess + counts.leftFailure + counts.leftInvalid + 
                          counts.rightSuccess + counts.rightFailure + counts.rightInvalid
        val totalSuccess = counts.leftSuccess + counts.rightSuccess
        
        val report = """
=== 跨步偵測器調試報告 ===
時間: ${Date().toString()}
處理幀數: $frameCount

=== 計數統計 ===
左腿總計數: ${counts.leftTotal}
右腿總計數: ${counts.rightTotal}
總計數: ${counts.total}

左腿成功: ${counts.leftSuccess}
右腿成功: ${counts.rightSuccess}
總成功: $totalSuccess

左腿失敗: ${counts.leftFailure}
右腿失敗: ${counts.rightFailure}
總失敗: ${counts.leftFailure + counts.rightFailure}

左腿無效: ${counts.leftInvalid}
右腿無效: ${counts.rightInvalid}
總無效: ${counts.leftInvalid + counts.rightInvalid}

=== 閾值設定 ===
標準角度: ${thresholds.standardAngle}°
重置閾值: ${thresholds.resetThreshold}°
成功範圍: ${thresholds.successRange.first.format(1)}° ~ ${thresholds.successRange.second.format(1)}°
失敗範圍: ${FAILURE_MIN_LOW.format(1)}° ~ ${FAILURE_MAX_LOW.format(1)}° 和 ${FAILURE_MIN_HIGH.format(1)}° ~ ${FAILURE_MAX_HIGH.format(1)}°
無效範圍: ${INVALID_MIN.format(1)}° ~ ${INVALID_MAX.format(1)}°

=== 狀態 ===
左腿可計數: $leftCanCount
右腿可計數: $rightCanCount
左腿角度歷史長度: ${leftAngles.size}
右腿角度歷史長度: ${rightAngles.size}

=== 最小角度追蹤 ===
左腿最小角度: ${if (leftMinAngle == Double.POSITIVE_INFINITY) "無" else "${leftMinAngle.format(1)}°"} (幀: $leftMinAngleFrame)
右腿最小角度: ${if (rightMinAngle == Double.POSITIVE_INFINITY) "無" else "${rightMinAngle.format(1)}°"} (幀: $rightMinAngleFrame)

=== 成功率分析 ===
總動作數: $totalActions (成功+失敗+無效)
成功率: ${if (totalActions > 0) String.format("%.1f", totalSuccess / totalActions * 100) else "0.0"}%
左腿成功率: ${if (counts.leftTotal > 0) String.format("%.1f", counts.leftSuccess / counts.leftTotal * 100) else "0.0"}%
右腿成功率: ${if (counts.rightTotal > 0) String.format("%.1f", counts.rightSuccess / counts.rightTotal * 100) else "0.0"}%
"""
        
        Log.i(TAG, report)
        return report
    }
    
    /**
     * 處理結果數據類
     */
    data class ProcessResult(
        val leftResult: LegProcessResult,
        val rightResult: LegProcessResult,
        val leftAngle: Double?,
        val rightAngle: Double?
    )
    
    /**
     * 腿部處理結果枚舉
     */
    enum class LegProcessResult {
        NO_ANGLE,      // 無角度數據
        TRACKING,      // 正在追蹤
        COUNTED,       // 已計數
        RESET,         // 已重置
        ERROR          // 錯誤
    }
    
    /**
     * 跨步計數結果數據類
     */
    data class StrideCountResult(
        val leftTotal: Int,
        val rightTotal: Int,
        val total: Int,
        val leftSuccess: Int,
        val rightSuccess: Int,
        val leftFailure: Int,
        val rightFailure: Int,
        val leftInvalid: Int,
        val rightInvalid: Int
    )
    
    /**
     * 閾值設定數據類
     */
    data class ThresholdSettings(
        val standardAngle: Double,
        val resetThreshold: Double,
        val successRange: Pair<Double, Double>,
        val failureRange: Pair<Double, Double>
    )
    
    /**
     * 動作記錄數據類
     */
    data class ActionRecord(
        val actionNumber: Int,
        val legSide: String,
        val angle: Double,
        val result: String,
        val frameNumber: Int
    )
    
    /**
     * 獲取動作歷史記錄
     */
    fun getActionHistory(): List<ActionRecord> {
        return actionHistory.toList()
    }
}

/**
 * Double擴展函數，用於格式化數字
 */
private fun Double.format(digits: Int) = "%.${digits}f".format(this)
