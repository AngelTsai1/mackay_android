# Mackay 運動檢測系統

## 項目概述

這是一個基於 MediaPipe 的 Android 運動檢測應用程式，專門用於檢測和計次三種不同的運動動作：高抬腳、左右跨步和腳尖對齊腳跟。本項目是主系統的一個功能模組，主要負責運動檢測的核心邏輯實現。

## 功能特色

### 支援的運動類型

1. **高抬腳(側面)** - 檢測腿部抬高的角度並計次
2. **左右跨步** - 檢測跨步動作的髖-膝-踝角度
3. **腳尖對齊腳跟** - 檢測腳部移動和對齊動作

### 核心功能

- 即時人體姿態檢測
- 智能角度計算和動作識別
- 防重複計次機制
- 詳細的統計數據記錄
- 動作歷史追蹤
- 成功率分析

## 技術架構

### 主要技術棧

- **Android SDK**: 最低支援 API 24 (Android 7.0)
- **Kotlin**: 主要開發語言
- **MediaPipe**: Google 的機器學習框架，用於人體姿態檢測
- **CameraX**: Android 相機 API，用於即時影像處理
- **Material Design**: 現代化 UI 設計

### 核心組件

#### 1. 主要 Activity

- **MainActivity**: 運動選擇介面
- **PoseActivity**: 運動檢測和計次介面

#### 2. 檢測器類別

- **PoseLandmarkerHelper**: MediaPipe 姿態檢測核心
- **PoseOverlayView**: 姿態覆蓋層和視覺化
- **HighKneeCounter**: 高抬腳計次邏輯
- **StrideDetector**: 左右跨步檢測
- **HeelMovementDetector**: 腳跟移動檢測
- **AngleCalculator**: 角度計算工具

#### 3. 輔助工具

- **MovementDebugHelper**: 調試輔助工具
- **ToeToHeelCounter**: 腳尖對齊腳跟計次

## 詳細功能說明

### 1. 高抬腳(側面) 檢測

#### 檢測邏輯
- 使用髖-膝-踝角度進行檢測
- 標準角度：90° ± 20% (72°-108°)
- 失敗範圍：54°-72° 或 108°-126°
- 無效範圍：其他角度

#### 計次機制
- 防重複計次：每次動作完成後需要腿伸直到 145° 以上才能重新計次
- 最小角度追蹤：記錄每個動作週期的最小角度
- 雙腿獨立計次：左右腿分別計次

#### 統計數據
```kotlin
data class CountResult(
    val leftSuccessCount: Int,      // 左腿成功次數
    val rightSuccessCount: Int,     // 右腿成功次數
    val totalSuccessCount: Int,     // 總成功次數
    val leftFailureCount: Int,      // 左腿失敗次數
    val rightFailureCount: Int,     // 右腿失敗次數
    val totalFailureCount: Int,     // 總失敗次數
    val leftInvalidCount: Int,      // 左腿無效次數
    val rightInvalidCount: Int,     // 右腿無效次數
    val totalInvalidCount: Int      // 總無效次數
)
```

### 2. 左右跨步 檢測

#### 檢測邏輯
- 使用髖-膝-踝角度進行檢測
- 標準角度：120° ± 10% (108°-132°)
- 失敗範圍：102°-108° 或 132°-138°
- 無效範圍：138°-145°

#### 計次機制
- 角度平滑化：使用 5 幀滑動窗口平滑角度數據
- 最小角度追蹤：記錄每個動作的最小角度
- 動作歷史記錄：詳細記錄每次動作的結果

#### 統計數據
```kotlin
data class StrideCountResult(
    val leftTotal: Int,             // 左腿總計數
    val rightTotal: Int,            // 右腿總計數
    val total: Int,                 // 總計數
    val leftSuccess: Int,           // 左腿成功
    val rightSuccess: Int,          // 右腿成功
    val leftFailure: Int,           // 左腿失敗
    val rightFailure: Int,          // 右腿失敗
    val leftInvalid: Int,           // 左腿無效
    val rightInvalid: Int           // 右腿無效
)
```

### 3. 腳尖對齊腳跟 檢測

#### 檢測邏輯
- 使用腳跟和腳尖的 3D 座標進行檢測
- 移動檢測：腳跟 X 軸移動距離 ≥ 腳長 × 0.02
- 靜止檢測：連續 7 幀靜止
- 方向判斷：前進/後退動作識別

#### 評估標準
- **成功條件**：腳跟到腳尖距離 ≤ 腳長 × 0.3 且 Z 軸距離差 ≤ 肩寬 × 1.1
- **失敗條件**：不滿足成功條件但滿足基本移動要求
- **不計次條件**：腳跟到腳跟距離 ≤ 腳長 × 0.2 且 Z 軸距離差 ≤ 肩寬 × 1.1

#### 統計數據
```kotlin
data class CountResult(
    val leftSuccess: Int,           // 左腳成功
    val leftFailure: Int,           // 左腳失敗
    val leftNoCount: Int,           // 左腳不計次
    val rightSuccess: Int,          // 右腳成功
    val rightFailure: Int,          // 右腳失敗
    val rightNoCount: Int,          // 右腳不計次
    val totalSuccess: Int,          // 總成功
    val totalFailure: Int,          // 總失敗
    val totalNoCount: Int,          // 總不計次
    val totalCount: Int,            // 總計數
    val successRate: Double         // 成功率
)
```

## 使用方法

### 1. 環境要求

- Android Studio Arctic Fox 或更高版本
- Android SDK API 24 或更高
- 支援相機的 Android 設備
- 至少 2GB RAM

### 2. 安裝步驟

1. 克隆專案到本地
2. 使用 Android Studio 開啟專案
3. 等待 Gradle 同步完成
4. 連接 Android 設備或啟動模擬器
5. 點擊 "Run" 按鈕編譯並安裝

### 3. 使用流程

#### 基本使用
1. 啟動應用程式
2. 選擇要進行的運動類型
3. 允許相機權限
4. 按照指示進行運動
5. 點擊返回按鈕查看統計結果

#### 運動指導

**高抬腳(側面)**
- 站立姿勢，側面對相機
- 抬起一腿，膝蓋彎曲約 90°
- 保持姿勢 1-2 秒後放下
- 重複動作

**左右跨步**
- 站立姿勢，側面對相機
- 向側面跨步，保持髖-膝-踝角度約 120°
- 回到起始位置
- 重複動作

**腳尖對齊腳跟**
- 站立姿勢，正面對相機
- 移動一腳，使腳尖對齊另一腳的腳跟
- 保持姿勢 1-2 秒
- 回到起始位置
- 重複動作

## 技術實現細節

### 1. 相機處理

```kotlin
// 相機初始化
private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
    cameraProviderFuture.addListener({
        cameraProvider = cameraProviderFuture.get()
        setupPoseLandmarker()
        bindCameraUseCases()
    }, ContextCompat.getMainExecutor(this))
}

// 相機用例綁定
private fun bindCameraUseCases() {
    val preview = Preview.Builder().build()
    val imageCapture = ImageCapture.Builder().build()
    val imageAnalyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { it.setAnalyzer(cameraExecutor, PoseAnalyzer()) }
    
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
        .build()
    
    camera = cameraProvider?.bindToLifecycle(
        this, cameraSelector, preview, imageCapture, imageAnalyzer
    )
}
```

### 2. MediaPipe 整合

```kotlin
// 姿態檢測器初始化
private fun setupPoseLandmarker() {
    poseLandmarkerHelper = PoseLandmarkerHelper(
        minPoseDetectionConfidence = 0.3f,
        minPoseTrackingConfidence = 0.3f,
        minPosePresenceConfidence = 0.3f,
        currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
        currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
        runningMode = RunningMode.LIVE_STREAM,
        context = this,
        poseLandmarkerHelperListener = this
    )
}
```

### 3. 角度計算

```kotlin
// 髖-膝-踝角度計算
private fun calculateHipKneeAnkleAngle(
    hipX: Float, hipY: Float,
    kneeX: Float, kneeY: Float,
    ankleX: Float, ankleY: Float
): Double {
    val hipKneeX = hipX - kneeX
    val hipKneeY = hipY - kneeY
    val ankleKneeX = ankleX - kneeX
    val ankleKneeY = ankleY - kneeY
    
    val dotProduct = hipKneeX * ankleKneeX + hipKneeY * ankleKneeY
    val hipKneeLength = sqrt((hipKneeX * hipKneeX + hipKneeY * hipKneeY).toDouble())
    val ankleKneeLength = sqrt((ankleKneeX * ankleKneeX + ankleKneeY * ankleKneeY).toDouble())
    
    if (hipKneeLength == 0.0 || ankleKneeLength == 0.0) return 0.0
    
    val cosAngle = dotProduct / (hipKneeLength * ankleKneeLength)
    val clampedCos = cosAngle.coerceIn(-1.0, 1.0)
    val angleRadians = acos(clampedCos)
    return Math.toDegrees(angleRadians)
}
```

### 4. 狀態管理

```kotlin
// 運動狀態追蹤
private fun updateFootStatus(foot: String, heelMoving: Boolean?, currentHeelData: HeelData) {
    when (heelMoving) {
        null -> {
            // NaN 情況處理
            if (isLeft) {
                leftDetectionCount++
                if (leftDetectionCount >= DETECTION_RESET_THRESHOLD) {
                    // 重置所有計數
                    resetCounts()
                }
            }
        }
        true -> {
            // 移動狀態處理
            updateMovingStatus()
        }
        false -> {
            // 靜止狀態處理
            updateStationaryStatus()
        }
    }
}
```

## 數據結構

### 1. 關鍵點索引

MediaPipe 提供 33 個關鍵點，本系統使用以下關鍵點：

```kotlin
// 關鍵點索引映射
private val landmarkNames = arrayOf(
    "鼻子", "左眼内", "左眼", "左眼外", "右眼内", "右眼", "右眼外", "左耳", "右耳",
    "嘴左", "嘴右", "左肩", "右肩", "左肘", "右肘", "左腕", "右腕", "左小指", "右小指",
    "左食指", "右食指", "左拇指", "右拇指", "左髋", "右髋", "左膝", "右膝", "左踝", "右踝",
    "左脚跟", "右脚跟", "左脚趾", "右脚趾"
)
```

### 2. 角度範圍設定

```kotlin
// 高抬腳角度設定
private const val STANDARD_ANGLE = 90.0
private const val RESET_ANGLE = 145.0
private const val SUCCESS_MIN = 72.0
private const val SUCCESS_MAX = 108.0
private const val FAILURE_MIN_LOW = 54.0
private const val FAILURE_MAX_LOW = 72.0
private const val FAILURE_MIN_HIGH = 108.0
private const val FAILURE_MAX_HIGH = 126.0

// 左右跨步角度設定
private const val STANDARD_ANGLE = 120.0
private const val RESET_THRESHOLD = 145.0
private const val SUCCESS_MIN = 108.0  // 120° * 0.9
private const val SUCCESS_MAX = 132.0  // 120° * 1.1
```

### 3. 移動檢測參數

```kotlin
// 腳跟移動檢測參數
private const val MOVEMENT_THRESHOLD_RATIO = 0.02f  // 腳長比例閾值
private const val CONSECUTIVE_MOVING_FRAMES = 5     // 連續移動幀數要求
private const val STATIONARY_THRESHOLD = 7          // 靜止判定閾值
private const val DETECTION_RESET_THRESHOLD = 5     // 檢測重置閾值
```

## 整合指南

### 1. 主系統整合

本模組設計為可獨立運行的功能模組，可以通過以下方式整合到主系統：

#### 方法一：直接整合
```kotlin
// 在主系統中啟動運動檢測
val intent = Intent(this, PoseActivity::class.java)
intent.putExtra("exercise_type", "高抬腳(側面)")
startActivity(intent)
```

#### 方法二：服務整合
```kotlin
// 創建運動檢測服務
class ExerciseDetectionService : Service() {
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    
    override fun onBind(intent: Intent?): IBinder? {
        return ExerciseDetectionBinder()
    }
    
    fun startDetection(exerciseType: String) {
        // 啟動檢測邏輯
    }
}
```

### 2. 數據接口

#### 獲取統計數據
```kotlin
// 獲取計次結果
val counts = poseOverlayView.getCounts()
when (exerciseType) {
    "高抬腳(側面)" -> {
        val result = counts as HighKneeCounter.CountResult
        val successRate = result.totalSuccessCount.toDouble() / 
                         (result.totalSuccessCount + result.totalFailureCount + result.totalInvalidCount)
    }
    "左右跨步" -> {
        val result = counts as StrideDetector.StrideCountResult
        // 處理跨步結果
    }
    "腳尖對齊腳跟" -> {
        val result = counts as HeelMovementDetector.CountResult
        // 處理腳跟移動結果
    }
}
```

#### 獲取動作歷史
```kotlin
// 獲取動作歷史記錄
val actionHistory = poseOverlayView.getActionHistory()
actionHistory?.forEach { record ->
    when (record) {
        is StrideDetector.ActionRecord -> {
            // 處理跨步動作記錄
            Log.d("Action", "第${record.actionNumber}次${record.legSide}動作: ${record.angle}° - ${record.result}")
        }
        is HeelMovementDetector.ActionRecord -> {
            // 處理腳跟移動記錄
            Log.d("Action", "第${record.actionNumber}次${record.legSide}動作: ${record.direction} - ${record.result}")
        }
    }
}
```

### 3. 自定義配置

#### 調整角度閾值
```kotlin
// 自定義高抬腳角度設定
highKneeCounter.setThresholds(
    standardAngle = 90.0,
    resetAngle = 145.0,
    successMin = 72.0,
    successMax = 108.0
)

// 自定義跨步角度設定
strideDetector.setThresholds(
    standardAngle = 120.0,
    resetThreshold = 145.0
)
```

#### 調整檢測參數
```kotlin
// 自定義移動檢測參數
heelMovementDetector.setParameters(
    movementThresholdRatio = 0.02f,
    consecutiveMovingFrames = 5,
    stationaryThreshold = 7
)
```

## 調試和測試

### 1. 日誌輸出

系統提供詳細的日誌輸出，可以通過以下方式查看：

```kotlin
// 啟用調試日誌
Log.d("PoseActivity", "Pose檢測結果: ${resultBundle.results.size}")
Log.d("HighKneeCounter", "左腿角度: ${leftAngle.format(1)}°")
Log.d("StrideDetector", "跨步計數: ${strideCount}")
Log.d("HeelMovementDetector", "移動狀態: ${movementStatus}")
```

### 2. 調試工具

```kotlin
// 生成調試報告
val debugReport = strideDetector.generateDebugReport()
Log.i("StrideDetector", debugReport)

// 獲取詳細統計
val statistics = heelMovementDetector.getStatistics()
Log.d("HeelMovementDetector", statistics)
```

### 3. 測試建議

1. **角度測試**：使用量角器驗證角度計算準確性
2. **計次測試**：進行標準動作測試計次準確性
3. **邊界測試**：測試極端角度和快速動作
4. **穩定性測試**：長時間運行測試系統穩定性

## 常見問題

### 1. 相機權限問題
- 確保在 AndroidManifest.xml 中聲明相機權限
- 在運行時請求相機權限

### 2. MediaPipe 模型問題
- 確保 `pose_landmarker_full.task` 文件在 `assets` 目錄中
- 檢查模型文件完整性

### 3. 角度計算問題
- 確保關鍵點檢測準確
- 檢查角度計算公式
- 驗證角度範圍設定

### 4. 計次準確性問題
- 調整角度閾值
- 檢查防重複計次邏輯
- 驗證動作完成條件

## 性能優化

### 1. 記憶體管理
- 及時清理 MediaPipe 資源
- 限制歷史數據大小
- 避免記憶體洩漏

### 2. 計算優化
- 使用角度平滑化減少抖動
- 優化關鍵點檢測頻率
- 減少不必要的計算

### 3. 電池優化
- 使用 CPU 模式減少耗電
- 適當調整檢測頻率
- 及時釋放相機資源

## 未來擴展

### 1. 新運動類型
- 可以輕鬆添加新的運動檢測類型
- 實現統一的運動檢測接口
- 支援自定義運動參數

### 2. 數據分析
- 添加運動軌跡分析
- 實現運動質量評估
- 提供運動建議

### 3. 用戶體驗
- 添加語音指導
- 實現運動示範
- 提供個性化設定

## 技術支援

如有技術問題或需要支援，請聯繫開發團隊或查看相關文檔。

---

**注意**：本項目僅為功能模組，需要整合到主系統中才能完整運行。在整合過程中，請確保遵循主系統的架構規範和數據格式要求。
