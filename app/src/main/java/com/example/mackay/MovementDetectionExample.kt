package com.example.mackay

import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * 移动检测使用示例
 * 展示如何在PoseLandmarkerHelper中集成HeelMovementDetector
 */
class MovementDetectionExample {
    
    companion object {
        private const val TAG = "MovementDetectionExample"
    }
    
    private val heelMovementDetector = HeelMovementDetector()
    
    /**
     * 处理姿态检测结果
     * 从PoseLandmarkerResult中提取脚跟和脚尖数据
     */
    fun processPoseResult(result: PoseLandmarkerResult, frame: Int, timestamp: Float) {
        try {
            val landmarks = result.landmarks()
            if (landmarks.isNotEmpty()) {
                val personLandmarks = landmarks[0]  // 取第一个人的关键点
                
                // 提取脚跟和脚尖数据
                val heelData = extractHeelData(personLandmarks, frame, timestamp)
                
                // 检测移动状态
                val movementStatus = heelMovementDetector.detectHeelMovement(heelData)
                
                // 输出检测结果
                logMovementStatus(movementStatus, frame, timestamp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理姿态检测结果时发生错误: ${e.message}", e)
        }
    }
    
    /**
     * 从MediaPipe关键点中提取脚跟和脚尖数据
     */
    private fun extractHeelData(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>, 
                              frame: Int, timestamp: Float): HeelMovementDetector.HeelData {
        
        // MediaPipe关键点索引
        val LEFT_HEEL = 29
        val RIGHT_HEEL = 30
        val LEFT_FOOT_INDEX = 31
        val RIGHT_FOOT_INDEX = 32
        
        // 提取脚跟坐标
        val leftHeel = landmarks[LEFT_HEEL]
        val rightHeel = landmarks[RIGHT_HEEL]
        val leftToe = landmarks[LEFT_FOOT_INDEX]
        val rightToe = landmarks[RIGHT_FOOT_INDEX]
        
        // 计算脚长
        val leftFootLength = calculateFootLength(leftToe, leftHeel)
        val rightFootLength = calculateFootLength(rightToe, rightHeel)
        
        return HeelMovementDetector.HeelData(
            frame = frame,
            timestamp = timestamp,
            // 標準化坐標
            leftHeelX = leftHeel.x(),
            leftHeelY = leftHeel.y(),
            leftHeelZ = leftHeel.z(),
            rightHeelX = rightHeel.x(),
            rightHeelY = rightHeel.y(),
            rightHeelZ = rightHeel.z(),
            leftToeX = leftToe.x(),
            leftToeY = leftToe.y(),
            leftToeZ = leftToe.z(),
            rightToeX = rightToe.x(),
            rightToeY = rightToe.y(),
            rightToeZ = rightToe.z(),
            leftFootLength = leftFootLength,
            rightFootLength = rightFootLength,
            // 世界坐標
            worldLeftHeelZ = leftHeel.z(),  // 注意：這裡暫時使用標準化坐標
            worldRightHeelZ = rightHeel.z(),
            worldLeftToeZ = leftToe.z(),
            worldRightToeZ = rightToe.z(),
            worldLeftShoulderZ = 0.0f,
            worldRightShoulderZ = 0.0f
        )
    }
    
    /**
     * 计算脚长
     */
    private fun calculateFootLength(toe: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
                                   heel: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Float {
        val dx = toe.x() - heel.x()
        val dy = toe.y() - heel.y()
        val dz = toe.z() - heel.z()
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).toFloat()
    }
    
    /**
     * 输出移动状态日志
     */
    private fun logMovementStatus(status: HeelMovementDetector.MovementStatus, 
                                frame: Int, timestamp: Float) {
        Log.d(TAG, """
            === 移动检测结果 (帧: $frame, 时间: $timestamp) ===
            左脚状态: ${status.leftHeelDisplayStatus}
            右脚状态: ${status.rightHeelDisplayStatus}
            左脚移动: ${status.leftHeelMoving}
            右脚移动: ${status.rightHeelMoving}
            左脚静止帧数: ${status.leftHeelStationaryFrames}
            右脚静止帧数: ${status.rightHeelStationaryFrames}
            左脚动作结束: ${status.leftHeelActionEnded}
            右脚动作结束: ${status.rightHeelActionEnded}
            左脚方向: ${status.leftHeelDirection}
            右脚方向: ${status.rightHeelDirection}
            左脚动作结果: ${status.leftHeelActionResult}
            右脚动作结果: ${status.rightHeelActionResult}
        """.trimIndent())
    }
    
    /**
     * 获取统计信息
     */
    fun getStatistics(): String {
        return heelMovementDetector.getStatistics()
    }
    
    /**
     * 重置检测器
     */
    fun reset() {
        heelMovementDetector.reset()
        Log.d(TAG, "移动检测器已重置")
    }
}
