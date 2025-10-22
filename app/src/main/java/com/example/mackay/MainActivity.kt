package com.example.mackay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    
    private lateinit var highKickButton: MaterialButton
    private lateinit var sideStepButton: MaterialButton
    private lateinit var toeToHeelButton: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupClickListeners()
    }
    
    private fun initViews() {
        highKickButton = findViewById(R.id.highKickButton)
        sideStepButton = findViewById(R.id.sideStepButton)
        toeToHeelButton = findViewById(R.id.toeToHeelButton)
    }
    
    private fun setupClickListeners() {
        highKickButton.setOnClickListener {
            startPoseActivity("高抬腳(側面)")
        }
        
        sideStepButton.setOnClickListener {
            startPoseActivity("左右跨步")
        }
        
        toeToHeelButton.setOnClickListener {
            startPoseActivity("腳尖對齊腳跟")
        }
    }
    
    private fun startPoseActivity(exerciseType: String) {
        val intent = Intent(this, PoseActivity::class.java)
        intent.putExtra("exercise_type", exerciseType)
        startActivity(intent)
    }
}
