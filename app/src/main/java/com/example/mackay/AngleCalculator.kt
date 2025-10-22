package com.example.mackay

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

/**
 * 角度計算工具類
 * 用於計算人體關鍵點之間的角度
 */
class AngleCalculator {
    
    companion object {
        private const val TAG = "AngleCalculator"
        
        /**
         * 計算三點形成的角度
         * @param point1 第一個點 (肩膀)
         * @param point2 第二個點 (髖關節) - 角度頂點
         * @param point3 第三個點 (膝蓋)
         * @return 角度值（度）
         */
        fun calculateTrunkThighAngle(
            point1: NormalizedLandmark,
            point2: NormalizedLandmark,
            point3: NormalizedLandmark
        ): Double {
            try {
                // 獲取三點座標
                val x1 = point1.x().toDouble()
                val y1 = point1.y().toDouble()
                val x2 = point2.x().toDouble()
                val y2 = point2.y().toDouble()
                val x3 = point3.x().toDouble()
                val y3 = point3.y().toDouble()
                
                // 計算向量
                val vector1X = x1 - x2
                val vector1Y = y1 - y2
                val vector2X = x3 - x2
                val vector2Y = y3 - y2
                
                // 計算向量長度
                val length1 = sqrt(vector1X * vector1X + vector1Y * vector1Y)
                val length2 = sqrt(vector2X * vector2X + vector2Y * vector2Y)
                
                // 避免除零錯誤
                if (length1 == 0.0 || length2 == 0.0) {
                    Log.w(TAG, "向量長度為零，無法計算角度")
                    return 0.0
                }
                
                // 計算點積
                val dotProduct = vector1X * vector2X + vector1Y * vector2Y
                
                // 計算角度（弧度轉度）
                val cosAngle = dotProduct / (length1 * length2)
                
                // 限制cos值在[-1, 1]範圍內，避免數值誤差
                val clampedCos = cosAngle.coerceIn(-1.0, 1.0)
                val angleRadians = acos(clampedCos)
                val angleDegrees = Math.toDegrees(angleRadians)
                
                Log.d(TAG, "計算角度: ${angleDegrees.format(1)}°")
                return angleDegrees
                
            } catch (e: Exception) {
                Log.e(TAG, "計算角度時發生錯誤: ${e.message}")
                return 0.0
            }
        }
        
        /**
         * 計算兩點之間的距離
         */
        fun calculateDistance(
            point1: NormalizedLandmark,
            point2: NormalizedLandmark
        ): Double {
            val dx = point1.x() - point2.x()
            val dy = point1.y() - point2.y()
            return sqrt((dx * dx + dy * dy).toDouble())
        }
        
        /**
         * 檢查關鍵點是否有效（置信度檢查）
         */
        fun isLandmarkValid(landmark: NormalizedLandmark, minConfidence: Float = 0.3f): Boolean {
            // 檢查關鍵點是否在合理範圍內
            val x = landmark.x()
            val y = landmark.y()
            
            // 如果關鍵點在螢幕範圍外，認為無效
            if (x < 0.0f || x > 1.0f || y < 0.0f || y > 1.0f) {
                return false
            }
            
            // 暫時只使用位置檢查
            return true
        }
        
        /**
         * 格式化角度顯示
         */
        fun formatAngle(angle: Double): String {
            return "${angle.format(1)}°"
        }
        
        /**
         * 根據角度範圍決定顏色 - 左右跨步角度範圍
         */
        fun getAngleColor(angle: Double): Int {
            return when {
                // 成功範圍：108° ~ 132°
                angle >= 108.0 && angle <= 132.0 -> 0xFF00FF00.toInt() // 綠色 - 成功範圍
                // 失敗範圍：102° ~ 108° 和 132° ~ 138°
                (angle >= 102.0 && angle < 108.0) || (angle > 132.0 && angle <= 138.0) -> 0xFF1055C9.toInt() // 藍色 - 失敗範圍
                // 無效範圍：138° ~ 145°
                angle >= 138.0 && angle <= 145.0 -> 0xFFFFA500.toInt() // 橙色 - 無效範圍
                else -> 0xFFFFFFFF.toInt() // 白色 - 其他範圍
            }
        }
        
        /**
         * 判斷角度是否在成功範圍內 - 左右跨步角度範圍
         */
        fun isSuccessRange(angle: Double): Boolean {
            return angle >= 108.0 && angle <= 132.0
        }
        
        /**
         * 判斷角度是否在失敗範圍內 - 左右跨步角度範圍
         */
        fun isFailureRange(angle: Double): Boolean {
            return (angle >= 102.0 && angle < 108.0) || (angle > 132.0 && angle <= 138.0)
        }
    }
}

/**
 * Double擴展函數，用於格式化數字
 */
private fun Double.format(digits: Int) = "%.${digits}f".format(this)
