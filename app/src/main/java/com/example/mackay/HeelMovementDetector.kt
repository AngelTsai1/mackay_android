package com.example.mackay

import android.util.Log
import kotlin.math.abs

/**
 * 脚跟移动检测器
 * 完全复刻Python文件中的移动检测逻辑
 */
class HeelMovementDetector {
    
    companion object {
        private const val TAG = "HeelMovementDetector"
        
        // 移动检测参数 - 调整后的参数
        private const val MOVEMENT_THRESHOLD_RATIO = 0.02f  // 脚长比例阈值 (0.02)
        private const val CONSECUTIVE_MOVING_FRAMES = 5     // 连续移动帧数要求 (调整为4帧)
        private const val STATIONARY_THRESHOLD = 7          // 静止判定阈值 (调整为6帧)
        private const val DETECTION_RESET_THRESHOLD = 5     // 检测重置阈值
        
        // EMA平滑化参数 - 完全按照Python参数
        private const val ENABLE_EMA_SMOOTHING = true  // 是否启用EMA平滑处理
        private const val EMA_ALPHA = 0.3f  // EMA平滑系数 (0.0-1.0，越小越平滑)
        private const val MAX_EMA_HISTORY = 5  // 最大EMA历史记录数
    }
    
    // 移动检测相关数据
    private var previousHeelData: HeelData? = null
    
    // EMA平滑化相关数据
    private var emaLandmarksHistory = mutableListOf<HeelData>()
    
    // 状态转换追踪
    private var leftHeelPreviousState: Boolean? = null
    private var rightHeelPreviousState: Boolean? = null
    
    // 静止状态追踪
    private var leftHeelStationaryDetected = false
    private var rightHeelStationaryDetected = false
    private var leftStationaryCount = 0
    private var rightStationaryCount = 0
    
    // 移动状态追踪 - 完全按照Python状态机
    private var leftHeelMoving: Boolean? = null  // True/False/NaN
    private var rightHeelMoving: Boolean? = null
    private var leftMovingFramesCount = 0  // 移动帧计数
    private var rightMovingFramesCount = 0
    private var leftDetectionCount = 0  // 检测中帧数
    private var rightDetectionCount = 0
    private var leftHeelMovingDetected = false
    private var rightHeelMovingDetected = false
    
    // 动作结束条件追踪
    private var leftHeelHasMoved = false
    private var rightHeelHasMoved = false
    
    // 移动开始帧追踪
    private var leftHeelMovementStartFrame: HeelData? = null
    private var rightHeelMovementStartFrame: HeelData? = null
    
    // 动作结束帧追踪
    private var leftHeelActionEndFrame: HeelData? = null
    private var rightHeelActionEndFrame: HeelData? = null
    
    // 方向判断状态追踪
    private var leftHeelDirectionDetermined = false
    private var rightHeelDirectionDetermined = false
    
    // 当前方向状态（持续记录）
    private var currentLeftDirection = ""
    private var currentRightDirection = ""
    
    // 动作评估结果追踪
    private var leftHeelActionResult = ""
    private var rightHeelActionResult = ""
    
    // 动作统计计数器
    private var leftSuccessCount = 0
    private var leftFailureCount = 0
    private var leftNoCount = 0
    private var rightSuccessCount = 0
    private var rightFailureCount = 0
    private var rightNoCount = 0
    
    // 动作历史记录
    private val actionHistory = mutableListOf<ActionRecord>()
    private var actionCounter = 0
    
    /**
     * 动作记录数据类
     */
    data class ActionRecord(
        val actionNumber: Int,
        val legSide: String,  // "left" 或 "right"
        val direction: String,  // "前進" 或 "後退"
        val result: String,  // "成功"、"失敗" 或 "不計次"
        val timestamp: Float
    )
    
    /**
     * 脚跟数据类
     */
    data class HeelData(
        val frame: Int,
        val timestamp: Float,
        // 标准化坐标 - 用于移动检测和X距离计算
        val leftHeelX: Float?,
        val leftHeelY: Float?,
        val leftHeelZ: Float?,
        val rightHeelX: Float?,
        val rightHeelY: Float?,
        val rightHeelZ: Float?,
        val leftToeX: Float?,
        val leftToeY: Float?,
        val leftToeZ: Float?,
        val rightToeX: Float?,
        val rightToeY: Float?,
        val rightToeZ: Float?,
        val leftFootLength: Float?,
        val rightFootLength: Float?,
        // 世界坐标 - 用于Z距离计算
        val worldLeftHeelZ: Float?,
        val worldRightHeelZ: Float?,
        val worldLeftToeZ: Float?,
        val worldRightToeZ: Float?,
        val worldLeftShoulderZ: Float?,
        val worldRightShoulderZ: Float?
    )
    
    /**
     * 统计结果数据类
     */
    data class CountResult(
        val leftSuccess: Int,
        val leftFailure: Int,
        val leftNoCount: Int,
        val rightSuccess: Int,
        val rightFailure: Int,
        val rightNoCount: Int,
        val totalSuccess: Int,
        val totalFailure: Int,
        val totalNoCount: Int,
        val totalCount: Int,
        val successRate: Double
    )
    
    /**
     * 移动状态结果
     */
    data class MovementStatus(
        val leftHeelMoving: Boolean?,
        val rightHeelMoving: Boolean?,
        val leftHeelStationaryFrames: Int,
        val rightHeelStationaryFrames: Int,
        val leftHeelStateTransition: String,
        val rightHeelStateTransition: String,
        val leftHeelTransitionTimestamp: Float?,
        val rightHeelTransitionTimestamp: Float?,
        val leftHeelActionEnded: Boolean,
        val rightHeelActionEnded: Boolean,
        val leftHeelDisplayStatus: String,
        val rightHeelDisplayStatus: String,
        val leftHeelDirection: String,
        val rightHeelDirection: String,
        val leftHeelActionResult: String,
        val rightHeelActionResult: String
    )
    
    /**
     * 应用EMA平滑化 - 完全按照Python逻辑实现
     */
    private fun applyEMASmoothing(currentHeelData: HeelData): HeelData {
        if (!ENABLE_EMA_SMOOTHING || emaLandmarksHistory.isEmpty()) {
            emaLandmarksHistory.add(currentHeelData)
            return currentHeelData
        }
        
        val previousData = emaLandmarksHistory.last()
        
        // 应用EMA平滑化：smoothed = alpha * current + (1 - alpha) * previous
        val smoothedData = HeelData(
            frame = currentHeelData.frame,
            timestamp = currentHeelData.timestamp,
            // 标准化坐标平滑化
            leftHeelX = smoothValue(currentHeelData.leftHeelX, previousData.leftHeelX),
            leftHeelY = smoothValue(currentHeelData.leftHeelY, previousData.leftHeelY),
            leftHeelZ = smoothValue(currentHeelData.leftHeelZ, previousData.leftHeelZ),
            rightHeelX = smoothValue(currentHeelData.rightHeelX, previousData.rightHeelX),
            rightHeelY = smoothValue(currentHeelData.rightHeelY, previousData.rightHeelY),
            rightHeelZ = smoothValue(currentHeelData.rightHeelZ, previousData.rightHeelZ),
            leftToeX = smoothValue(currentHeelData.leftToeX, previousData.leftToeX),
            leftToeY = smoothValue(currentHeelData.leftToeY, previousData.leftToeY),
            leftToeZ = smoothValue(currentHeelData.leftToeZ, previousData.leftToeZ),
            rightToeX = smoothValue(currentHeelData.rightToeX, previousData.rightToeX),
            rightToeY = smoothValue(currentHeelData.rightToeY, previousData.rightToeY),
            rightToeZ = smoothValue(currentHeelData.rightToeZ, previousData.rightToeZ),
            leftFootLength = smoothValue(currentHeelData.leftFootLength, previousData.leftFootLength),
            rightFootLength = smoothValue(currentHeelData.rightFootLength, previousData.rightFootLength),
            // 世界坐标平滑化
            worldLeftHeelZ = smoothValue(currentHeelData.worldLeftHeelZ, previousData.worldLeftHeelZ),
            worldRightHeelZ = smoothValue(currentHeelData.worldRightHeelZ, previousData.worldRightHeelZ),
            worldLeftToeZ = smoothValue(currentHeelData.worldLeftToeZ, previousData.worldLeftToeZ),
            worldRightToeZ = smoothValue(currentHeelData.worldRightToeZ, previousData.worldRightToeZ),
            worldLeftShoulderZ = smoothValue(currentHeelData.worldLeftShoulderZ, previousData.worldLeftShoulderZ),
            worldRightShoulderZ = smoothValue(currentHeelData.worldRightShoulderZ, previousData.worldRightShoulderZ)
        )
        
        // 更新历史记录
        emaLandmarksHistory.add(smoothedData)
        if (emaLandmarksHistory.size > MAX_EMA_HISTORY) {
            emaLandmarksHistory.removeAt(0)
        }
        
        return smoothedData
    }
    
    /**
     * 平滑化单个值 - 严格遵循Python的NaN传播行为
     */
    private fun smoothValue(current: Float?, previous: Float?): Float? {
        if (current == null || previous == null) return null  // 任何值为null时返回null，与Python的NaN传播一致
        return EMA_ALPHA * current + (1 - EMA_ALPHA) * previous
    }
    
    /**
     * 检测脚跟移动状态并追踪状态转换
     * 完全复刻新的逻辑规范
     */
    fun detectHeelMovement(currentHeelData: HeelData): MovementStatus {
        // 应用EMA平滑化 - 完全按照Python逻辑
        val smoothedHeelData = applyEMASmoothing(currentHeelData)
        
        // 初始化动作结束状态
        var leftActionEnded = false
        var rightActionEnded = false
        
        // 如果没有前一帧数据，无法进行比较
        if (previousHeelData == null) {
            previousHeelData = smoothedHeelData
            return MovementStatus(
                leftHeelMoving = null,
                rightHeelMoving = null,
                leftHeelStationaryFrames = 0,
                rightHeelStationaryFrames = 0,
                leftHeelStateTransition = "",
                rightHeelStateTransition = "",
                leftHeelTransitionTimestamp = null,
                rightHeelTransitionTimestamp = null,
                leftHeelActionEnded = false,
                rightHeelActionEnded = false,
                leftHeelDisplayStatus = "检测中",
                rightHeelDisplayStatus = "检测中",
                leftHeelDirection = "",
                rightHeelDirection = "",
                leftHeelActionResult = "",
                rightHeelActionResult = ""
            )
        }
        
        // 检测左脚移动状态
        leftHeelMoving = detectFootMovement(smoothedHeelData, "left")
        rightHeelMoving = detectFootMovement(smoothedHeelData, "right")
        
        // 更新左脚状态 - 动作结束逻辑已整合到updateFootStatus中
        updateFootStatus("left", leftHeelMoving, smoothedHeelData)
        updateFootStatus("right", rightHeelMoving, smoothedHeelData)
        
        // 检查是否有动作结束（由updateFootStatus内部处理）
        leftActionEnded = leftHeelStationaryDetected && leftHeelHasMoved
        rightActionEnded = rightHeelStationaryDetected && rightHeelHasMoved
        
        // 在动作结束时检测方向和评估动作 - 加入閂鎖機制防止重複評估
        if (leftActionEnded && !leftHeelDirectionDetermined) {
            currentLeftDirection = detectActionDirection("left", smoothedHeelData)
            leftHeelActionResult = evaluateAction("left", smoothedHeelData)
            leftHeelDirectionDetermined = true // 關上閂鎖，防止重複評估
            
            // 记录动作历史
            if (leftHeelActionResult.isNotEmpty()) {
                actionCounter++
                actionHistory.add(ActionRecord(
                    actionNumber = actionCounter,
                    legSide = "左腳",
                    direction = currentLeftDirection,
                    result = leftHeelActionResult,
                    timestamp = smoothedHeelData.timestamp
                ))
            }
        }
        if (rightActionEnded && !rightHeelDirectionDetermined) {
            currentRightDirection = detectActionDirection("right", smoothedHeelData)
            rightHeelActionResult = evaluateAction("right", smoothedHeelData)
            rightHeelDirectionDetermined = true // 關上閂鎖，防止重複評估
            
            // 记录动作历史
            if (rightHeelActionResult.isNotEmpty()) {
                actionCounter++
                actionHistory.add(ActionRecord(
                    actionNumber = actionCounter,
                    legSide = "右腳",
                    direction = currentRightDirection,
                    result = rightHeelActionResult,
                    timestamp = smoothedHeelData.timestamp
                ))
            }
        }
        
        // 更新前一帧数据
        previousHeelData = smoothedHeelData
        
        // 创建并返回MovementStatus对象
        return MovementStatus(
            leftHeelMoving = leftHeelMovingDetected,
            rightHeelMoving = rightHeelMovingDetected,
            leftHeelStationaryFrames = leftStationaryCount,
            rightHeelStationaryFrames = rightStationaryCount,
            leftHeelStateTransition = "",
            rightHeelStateTransition = "",
            leftHeelTransitionTimestamp = null,
            rightHeelTransitionTimestamp = null,
            leftHeelActionEnded = leftActionEnded,
            rightHeelActionEnded = rightActionEnded,
            leftHeelDisplayStatus = when {
                leftHeelStationaryDetected -> "静止"
                leftHeelMovingDetected -> "移动"
                leftHeelMoving == true -> "移动中(${leftMovingFramesCount}/5)"
                leftHeelMoving == false -> "静止中(${leftStationaryCount}/7)"
                else -> "检测中"
            },
            rightHeelDisplayStatus = when {
                rightHeelStationaryDetected -> "静止"
                rightHeelMovingDetected -> "移动"
                rightHeelMoving == true -> "移动中(${rightMovingFramesCount}/5)"
                rightHeelMoving == false -> "静止中(${rightStationaryCount}/7)"
                else -> "检测中"
            },
            leftHeelDirection = currentLeftDirection,
            rightHeelDirection = currentRightDirection,
            leftHeelActionResult = leftHeelActionResult,
            rightHeelActionResult = rightHeelActionResult
        )
    }
    
    /**
     * 重置所有状态
     */
    fun reset() {
        previousHeelData = null
        leftHeelPreviousState = null
        rightHeelPreviousState = null
        leftHeelStationaryDetected = false
        rightHeelStationaryDetected = false
        leftStationaryCount = 0
        rightStationaryCount = 0
        leftMovingFramesCount = 0
        rightMovingFramesCount = 0
        leftDetectionCount = 0
        rightDetectionCount = 0
        leftHeelMovingDetected = false
        rightHeelMovingDetected = false
        
        // 重置状态变量
        leftHeelMoving = null
        rightHeelMoving = null
        leftHeelHasMoved = false
        rightHeelHasMoved = false
        leftHeelMovementStartFrame = null
        rightHeelMovementStartFrame = null
        leftHeelActionEndFrame = null
        rightHeelActionEndFrame = null
        leftHeelDirectionDetermined = false
        rightHeelDirectionDetermined = false
        currentLeftDirection = ""
        currentRightDirection = ""
        leftHeelActionResult = ""
        rightHeelActionResult = ""
        leftSuccessCount = 0
        leftFailureCount = 0
        leftNoCount = 0
        rightSuccessCount = 0
        rightFailureCount = 0
        rightNoCount = 0
        
        // 重置历史记录
        actionHistory.clear()
        actionCounter = 0
        
        Log.d(TAG, "移动检测器状态已重置")
    }
    
    /**
     * 检测单脚移动状态
     */
    private fun detectFootMovement(currentHeelData: HeelData, foot: String): Boolean? {
        val heelX = if (foot == "left") currentHeelData.leftHeelX else currentHeelData.rightHeelX
        val prevHeelX = if (foot == "left") previousHeelData!!.leftHeelX else previousHeelData!!.rightHeelX
        val footLength = if (foot == "left") currentHeelData.leftFootLength else currentHeelData.rightFootLength
        
        // 检查数据是否完整
        if (heelX == null || prevHeelX == null || footLength == null) {
            return null  // NaN情况
        }
        
        // 计算移动差值
        val heelXDiff = abs(heelX - prevHeelX)
        val movementThreshold = footLength * MOVEMENT_THRESHOLD_RATIO
        
        // ∣当前帧x−前一帧x∣≥该只脚长×0.02⟹Heel_Moving=True(移动)
        return heelXDiff >= movementThreshold
    }
    
    /**
     * 更新单脚状态 - 完全按照Python状态机逻辑实现
     */
    private fun updateFootStatus(foot: String, heelMoving: Boolean?, currentHeelData: HeelData) {
        val isLeft = foot == "left"
        
        when (heelMoving) {
            null -> {
                // NaN情况：保持之前的计数，不增加静止帧计数
                Log.d(TAG, "${if (isLeft) "左" else "右"}脚: NaN状态，保持静止帧数=${if (isLeft) leftStationaryCount else rightStationaryCount}")
                
                // 增加检测中帧数
                if (isLeft) {
                    leftDetectionCount++
                    
                    // 当检测中状态>=5帧时，重置移动和静止计数（前面的移动不做数了）
                    if (leftDetectionCount >= DETECTION_RESET_THRESHOLD) {
                        leftStationaryCount = 0
                        leftMovingFramesCount = 0
                        leftHeelMovingDetected = false
                        leftHeelStationaryDetected = false
                        leftHeelHasMoved = false  // 重置移动记录
                        leftHeelDirectionDetermined = false // ✅ 重置閂鎖
                        leftHeelActionEndFrame = null
                        leftHeelMovementStartFrame = null
                        Log.d(TAG, "左脚检测中>=5帧，重置所有计数")
                    }
                } else {
                    rightDetectionCount++
                    
                    // 当检测中状态>=5帧时，重置移动和静止计数（前面的移动不做数了）
                    if (rightDetectionCount >= DETECTION_RESET_THRESHOLD) {
                        rightStationaryCount = 0
                        rightMovingFramesCount = 0
                        rightHeelMovingDetected = false
                        rightHeelStationaryDetected = false
                        rightHeelHasMoved = false  // 重置移动记录
                        rightHeelDirectionDetermined = false // ✅ 重置閂鎖
                        rightHeelActionEndFrame = null
                        rightHeelMovementStartFrame = null
                        Log.d(TAG, "右脚检测中>=5帧，重置所有计数")
                    }
                }
            }
            true -> {
                // Heel_Moving = True：重置静止帧数为0
                if (isLeft) {
                    leftStationaryCount = 0
                    leftMovingFramesCount++
                    leftDetectionCount = 0  // 重置检测中计数
                } else {
                    rightStationaryCount = 0
                    rightMovingFramesCount++
                    rightDetectionCount = 0  // 重置检测中计数
                }
                
                // 检查是否连续3帧移动
                val currentMovingFrames = if (isLeft) leftMovingFramesCount else rightMovingFramesCount
                if (currentMovingFrames >= CONSECUTIVE_MOVING_FRAMES && !(if (isLeft) leftHeelMovingDetected else rightHeelMovingDetected)) {
                    if (isLeft) {
                        leftHeelMovingDetected = true
                        leftHeelStationaryDetected = false // ✅ 強制重置靜止狀態
                        leftHeelHasMoved = true
                        leftHeelMovementStartFrame = currentHeelData
                        leftHeelDirectionDetermined = false // ✅ 重置閂鎖，準備新的動作週期
                    } else {
                        rightHeelMovingDetected = true
                        rightHeelStationaryDetected = false // ✅ 強制重置靜止狀態
                        rightHeelHasMoved = true
                        rightHeelMovementStartFrame = currentHeelData
                        rightHeelDirectionDetermined = false // ✅ 重置閂鎖，準備新的動作週期
                    }
                    Log.d(TAG, "${if (isLeft) "左" else "右"}脚开始移动")
                }
            }
            false -> {
                // Heel_Moving = False：增加静止帧数
                if (isLeft) {
                    leftStationaryCount++
                    leftMovingFramesCount = 0  // 重置移动帧计数
                    leftDetectionCount = 0  // 重置检测中计数
                    leftHeelMovingDetected = false  // ✅ 立即重置移动检测状态
                } else {
                    rightStationaryCount++
                    rightMovingFramesCount = 0  // 重置移动帧计数
                    rightDetectionCount = 0  // 重置检测中计数
                    rightHeelMovingDetected = false  // ✅ 立即重置移动检测状态
                }
                
                // 检查是否达到静止条件：Stationary_Count >= 10
                val currentStationaryCount = if (isLeft) leftStationaryCount else rightStationaryCount
                if (currentStationaryCount >= STATIONARY_THRESHOLD) {
                    if (isLeft) {
                        leftHeelStationaryDetected = true
                        leftHeelMovingDetected = false // ✅ 強制重置移動狀態
                        leftHeelActionEndFrame = currentHeelData
                        
                        // ✅ 正确的结束条件：静止之前有移动 - 完全按照Python逻辑
                        if (leftHeelHasMoved) {
                            // 这是一个动作结束点！在这里触发方向判断和评估
                            Log.d(TAG, "左脚动作结束，触发评估")
                        }
                    } else {
                        rightHeelStationaryDetected = true
                        rightHeelMovingDetected = false // ✅ 強制重置移動狀態
                        rightHeelActionEndFrame = currentHeelData
                        
                        // ✅ 正确的结束条件：静止之前有移动 - 完全按照Python逻辑
                        if (rightHeelHasMoved) {
                            // 这是一个动作结束点！在这里触发方向判断和评估
                            Log.d(TAG, "右脚动作结束，触发评估")
                        }
                    }
                    Log.d(TAG, "${if (isLeft) "左" else "右"}脚进入静止状态")
                }
            }
        }
    }
    
    // 删除错误的checkActionEndCondition函数，将逻辑整合到updateFootStatus中
    
    /**
     * 检测动作方向
     */
    private fun detectActionDirection(foot: String, currentHeelData: HeelData): String {
        val isLeft = foot == "left"
        val movementStartFrame = if (isLeft) leftHeelMovementStartFrame else rightHeelMovementStartFrame
        val actionEndFrame = if (isLeft) leftHeelActionEndFrame else rightHeelActionEndFrame
        
        if (movementStartFrame == null || actionEndFrame == null) {
            return ""
        }
        
        // 获取移动脚和支撑脚的数据
        val movingHeelX = if (isLeft) actionEndFrame.leftHeelX else actionEndFrame.rightHeelX
        val movingToeX = if (isLeft) actionEndFrame.leftToeX else actionEndFrame.rightToeX
        val supportHeelX = if (isLeft) currentHeelData.rightHeelX else currentHeelData.leftHeelX
        val supportToeX = if (isLeft) currentHeelData.rightToeX else currentHeelData.leftToeX
        
        if (movingHeelX == null || movingToeX == null || supportHeelX == null || supportToeX == null) {
            return ""
        }
        
        // 判断脚尖和脚跟的相对位置
        val toeOnLeft = movingToeX < movingHeelX  // 脚尖在左，脚跟在右 (假设1)
        val toeOnRight = movingToeX > movingHeelX  // 脚尖在右，脚跟在左 (假设2)
        
        // 计算移动方向：移动脚脚跟移动的第一帧 - 移动脚脚跟本次动作结束的第一帧
        val startHeelX = if (isLeft) movementStartFrame.leftHeelX else movementStartFrame.rightHeelX
        if (startHeelX == null) return ""
        
        val heelXChange = startHeelX - movingHeelX  // 与Python一致：移动开始帧 - 动作结束帧
        
        return when {
            toeOnLeft && heelXChange > 0 -> "前進"   // 假设1：脚尖在左，脚跟在右，heelXChange>0 → 前進
            toeOnLeft && heelXChange < 0 -> "後退"   // 假设1：脚尖在左，脚跟在右，heelXChange<0 → 後退
            toeOnRight && heelXChange > 0 -> "後退"  // 假设2：脚尖在右，脚跟在左，heelXChange>0 → 後退
            toeOnRight && heelXChange < 0 -> "前進"  // 假设2：脚尖在右，脚跟在左，heelXChange<0 → 前進
            else -> "無明顯移動"
        }
    }
    
    /**
     * 评估动作成功/失败/不计数
     */
    private fun evaluateAction(foot: String, currentHeelData: HeelData): String {
        val isLeft = foot == "left"
        val direction = if (isLeft) currentLeftDirection else currentRightDirection
        val actionEndFrame = if (isLeft) leftHeelActionEndFrame else rightHeelActionEndFrame
        
        if (actionEndFrame == null) return ""
        
        // 获取运动脚和支撑脚的数据
        val movingHeelX = if (isLeft) actionEndFrame.leftHeelX else actionEndFrame.rightHeelX
        val movingToeX = if (isLeft) actionEndFrame.leftToeX else actionEndFrame.rightToeX
        val supportHeelX = if (isLeft) currentHeelData.rightHeelX else currentHeelData.leftHeelX
        val supportToeX = if (isLeft) currentHeelData.rightToeX else currentHeelData.leftToeX
        val footLength = if (isLeft) currentHeelData.leftFootLength else currentHeelData.rightFootLength
        
        // 获取世界坐标z数据 - 完全按照Python文件
        val movingHeelZ = if (isLeft) currentHeelData.worldLeftHeelZ else currentHeelData.worldRightHeelZ
        val movingToeZ = if (isLeft) currentHeelData.worldLeftToeZ else currentHeelData.worldRightToeZ
        val supportHeelZ = if (isLeft) currentHeelData.worldRightHeelZ else currentHeelData.worldLeftHeelZ
        val supportToeZ = if (isLeft) currentHeelData.worldRightToeZ else currentHeelData.worldLeftToeZ
        
        // 获取肩部世界坐标z数据 - 完全按照Python文件
        val leftShoulderZ = currentHeelData.worldLeftShoulderZ
        val rightShoulderZ = currentHeelData.worldRightShoulderZ
        
        if (movingHeelX == null || movingToeX == null || supportHeelX == null || supportToeX == null ||
            footLength == null || movingHeelZ == null || movingToeZ == null || 
            supportHeelZ == null || supportToeZ == null || leftShoulderZ == null || rightShoulderZ == null) {
            return ""
        }
        
        // 计算阈值
        val noCountThreshold = footLength * 0.2f
        val successThreshold = footLength * 0.3f
        val shoulderZDistance = abs(leftShoulderZ - rightShoulderZ)
        val zDistanceThreshold = shoulderZDistance * 1.1f  // 不计数和成功都使用1.1，与Python一致
        
        // [前進、後退通用] 不計次條件
        val heelToHeelDistance = abs(movingHeelX - supportHeelX)
        val heelToeZDistance = abs(movingHeelZ - supportToeZ)
        
        if (heelToHeelDistance - noCountThreshold <= 0 && heelToeZDistance <= zDistanceThreshold) {
            if (isLeft) leftNoCount++ else rightNoCount++
            return "不計次(腳跟到腳跟距離:${String.format("%.3f", heelToHeelDistance)}-${String.format("%.3f", noCountThreshold)}<=0,\n腳跟腳尖Z距離差:${String.format("%.3f", heelToeZDistance)}<=${String.format("%.3f", zDistanceThreshold)})"
        }
        
        return when {
            "前進" in direction -> evaluateForwardAction(
                movingHeelX, movingToeX, supportToeX, supportToeZ, 
                movingHeelZ, successThreshold, zDistanceThreshold, isLeft
            )
            "後退" in direction -> evaluateBackwardAction(
                movingToeX, movingToeZ, supportHeelX, supportHeelZ,
                successThreshold, zDistanceThreshold, isLeft
            )
            else -> ""
        }
    }
    
    /**
     * 评估前进动作 - 完全按照Python逻辑实现
     */
    private fun evaluateForwardAction(
        movingHeelX: Float, movingToeX: Float, supportToeX: Float, supportToeZ: Float,
        movingHeelZ: Float, successThreshold: Float, zDistanceThreshold: Float, isLeft: Boolean
    ): String {
        val heelToToeDistance = abs(movingHeelX - supportToeX)
        val heelToeZDistance = abs(movingHeelZ - supportToeZ)
        
        // 成功条件：同时满足两条件
        if (heelToToeDistance - successThreshold <= 0 && heelToeZDistance <= zDistanceThreshold) {
            // 更新成功计数器
            if (isLeft) leftSuccessCount++ else rightSuccessCount++
            return "成功"
        }
        
        // 失败条件：先增加失败计数（与Python逻辑一致）
        if (isLeft) leftFailureCount++ else rightFailureCount++
        
        // 然后判断具体的失败类型，并显示详细数值
        return when {
            // 失败条件1：距离>0 但 z距离差<=阈值
            heelToToeDistance - successThreshold > 0 && heelToeZDistance <= zDistanceThreshold -> {
                val distanceDiff = heelToToeDistance - successThreshold
                "失敗(前進1:腳跟到腳尖距離:${String.format("%.3f", heelToToeDistance)}-${String.format("%.3f", successThreshold)}>0,\n腳跟腳尖Z距離差:${String.format("%.3f", heelToeZDistance)}<=${String.format("%.3f", zDistanceThreshold)})"
            }
            // 失败条件2：距离>0 且 z距离差>阈值
            heelToToeDistance - successThreshold > 0 && heelToeZDistance > zDistanceThreshold -> {
                val distanceDiff = heelToToeDistance - successThreshold
                "失敗(前進2:腳跟到腳尖距離:${String.format("%.3f", heelToToeDistance)}-${String.format("%.3f", successThreshold)}>0,\n腳跟腳尖Z距離差:${String.format("%.3f", heelToeZDistance)}>${String.format("%.3f", zDistanceThreshold)})"
            }
            // 失败条件3：距离<=0 但 z距离差>阈值
            heelToToeDistance - successThreshold <= 0 && heelToeZDistance > zDistanceThreshold -> {
                val distanceDiff = heelToToeDistance - successThreshold
                "失敗(前進3:腳跟到腳尖距離:${String.format("%.3f", heelToToeDistance)}-${String.format("%.3f", successThreshold)}<=0,\n腳跟腳尖Z距離差:${String.format("%.3f", heelToeZDistance)}>${String.format("%.3f", zDistanceThreshold)})"
            }
            else -> {
                "失敗(前進:未知原因)"
            }
        }
    }
    
    /**
     * 评估后退动作 - 完全按照Python逻辑实现
     */
    private fun evaluateBackwardAction(
        movingToeX: Float, movingToeZ: Float, supportHeelX: Float, supportHeelZ: Float,
        successThreshold: Float, zDistanceThreshold: Float, isLeft: Boolean
    ): String {
        val toeToHeelDistance = abs(movingToeX - supportHeelX)
        val toeHeelZDistance = abs(movingToeZ - supportHeelZ)
        
        // 成功条件：同时满足两条件
        if (toeToHeelDistance - successThreshold <= 0 && toeHeelZDistance <= zDistanceThreshold) {
            // 更新成功计数器
            if (isLeft) leftSuccessCount++ else rightSuccessCount++
            return "成功"
        }
        
        // 失败条件：先增加失败计数（与Python逻辑一致）
        if (isLeft) leftFailureCount++ else rightFailureCount++
        
        // 然后判断具体的失败类型，并显示详细数值
        return when {
            // 失败条件1：距离>0 但 z距离差<=阈值
            toeToHeelDistance - successThreshold > 0 && toeHeelZDistance <= zDistanceThreshold -> {
                "失敗(後退1:腳尖到腳跟距離:${String.format("%.3f", toeToHeelDistance)}-${String.format("%.3f", successThreshold)}>0,\n腳尖腳跟Z距離差:${String.format("%.3f", toeHeelZDistance)}<=${String.format("%.3f", zDistanceThreshold)})"
            }
            // 失败条件2：距离>0 且 z距离差>阈值
            toeToHeelDistance - successThreshold > 0 && toeHeelZDistance > zDistanceThreshold -> {
                "失敗(後退2:腳尖到腳跟距離:${String.format("%.3f", toeToHeelDistance)}-${String.format("%.3f", successThreshold)}>0,\n腳尖腳跟Z距離差:${String.format("%.3f", toeHeelZDistance)}>${String.format("%.3f", zDistanceThreshold)})"
            }
            // 失败条件3：距离<=0 但 z距离差>阈值
            toeToHeelDistance - successThreshold <= 0 && toeHeelZDistance > zDistanceThreshold -> {
                "失敗(後退3:腳尖到腳跟距離:${String.format("%.3f", toeToHeelDistance)}-${String.format("%.3f", successThreshold)}<=0,\n腳尖腳跟Z距離差:${String.format("%.3f", toeHeelZDistance)}>${String.format("%.3f", zDistanceThreshold)})"
            }
            else -> {
                "失敗(後退:未知原因)"
            }
        }
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): String {
        return """
            左脚统计: 成功=$leftSuccessCount, 失败=$leftFailureCount, 不计数=$leftNoCount
            右脚统计: 成功=$rightSuccessCount, 失败=$rightFailureCount, 不计数=$rightNoCount
        """.trimIndent()
    }
    
    /**
     * 获取成功计数
     */
    fun getLeftSuccessCount(): Int = leftSuccessCount
    fun getRightSuccessCount(): Int = rightSuccessCount
    
    /**
     * 获取失败计数
     */
    fun getLeftFailureCount(): Int = leftFailureCount
    fun getRightFailureCount(): Int = rightFailureCount
    
    /**
     * 获取不计数计数
     */
    fun getLeftNoCount(): Int = leftNoCount
    fun getRightNoCount(): Int = rightNoCount
    
    /**
     * 获取统计结果
     */
    fun getCounts(): CountResult {
        val totalSuccess = leftSuccessCount + rightSuccessCount
        val totalFailure = leftFailureCount + rightFailureCount
        val totalNoCount = leftNoCount + rightNoCount
        val totalCount = totalSuccess + totalFailure  // 总次数只计算成功+失败，不计算不计数
        val successRate = if (totalCount > 0) {
            (totalSuccess * 100.0) / totalCount
        } else {
            0.0
        }
        
        return CountResult(
            leftSuccess = leftSuccessCount,
            leftFailure = leftFailureCount,
            leftNoCount = leftNoCount,
            rightSuccess = rightSuccessCount,
            rightFailure = rightFailureCount,
            rightNoCount = rightNoCount,
            totalSuccess = totalSuccess,
            totalFailure = totalFailure,
            totalNoCount = totalNoCount,
            totalCount = totalCount,
            successRate = successRate
        )
    }
    
    /**
     * 获取动作历史记录
     */
    fun getActionHistory(): List<ActionRecord> {
        return actionHistory.toList()
    }
}
