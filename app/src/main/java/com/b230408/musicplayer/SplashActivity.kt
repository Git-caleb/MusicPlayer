package com.b230408.musicplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import com.b230408.musicplayer.ui.pages.SplashUI
import com.b230408.musicplayer.ui.theme.MusicPlayerTheme

/**
 * 启动界面Activity
 * 显示开屏动画后跳转到主界面
 */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MusicPlayerTheme {
                SplashUI(
                    onAnimationComplete = {
                        // 动画完成后跳转到主界面
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                )
            }
        }
    }
}
