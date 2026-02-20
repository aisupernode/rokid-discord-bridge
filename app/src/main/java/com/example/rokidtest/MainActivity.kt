package com.example.rokidtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rokid.glasses.transport.sdk.api.TransportSDK
import com.rokid.glasses.transport.sdk.api.callback.IResult
import com.rokid.glasses.transport.sdk.api.callback.IResultCallback
import com.rokid.glasses.transport.sdk.api.wifip2p.client.callback.IWifiP2PListener
import com.rokid.glasses.transport.sdk.api.wifip2p.groupowner.bean.DataType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {
    
    companion object {
        const val TAG = "RokidTest"
        const val PERMISSION_REQUEST_CODE = 100
    }
    
    private lateinit var statusText: TextView
    private lateinit var initButton: Button
    private lateinit var connectButton: Button
    private lateinit var sendButton: Button
    
    private var isInitialized = false
    private var isConnected = false
    
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        initButton = findViewById(R.id.initButton)
        connectButton = findViewById(R.id.connectButton)
        sendButton = findViewById(R.id.sendButton)
        
        initButton.setOnClickListener { checkPermissionsAndInit() }
        connectButton.setOnClickListener { testConnection() }
        sendButton.setOnClickListener { sendTestMessage() }
        
        updateStatus("就绪 - 请点击初始化 SDK")
    }
    
    private fun checkPermissionsAndInit() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            initSDK()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initSDK()
            } else {
                updateStatus("权限被拒绝，无法初始化")
            }
        }
    }
    
    private fun initSDK() {
        updateStatus("正在初始化 SDK...")
        
        try {
            // 初始化 Transport SDK
            // isGroupOwner = false 表示手机作为客户端
            TransportSDK.initSDK(
                isGroupOwner = false,
                ownerId = "mobile_test_001",
                port = 12001
            ) { result ->
                runOnUiThread {
                    when (result) {
                        is IResult.Success -> {
                            isInitialized = true
                            updateStatus("SDK 初始化成功！")
                            Log.d(TAG, "SDK 初始化成功")
                        }
                        is IResult.Failure -> {
                            updateStatus("SDK 初始化失败: ${result.exception.message}")
                            Log.e(TAG, "SDK 初始化失败", result.exception)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            updateStatus("初始化异常: ${e.message}")
            Log.e(TAG, "初始化异常", e)
        }
    }
    
    private fun testConnection() {
        if (!isInitialized) {
            Toast.makeText(this, "请先初始化 SDK", Toast.LENGTH_SHORT).show()
            return
        }
        
        updateStatus("正在测试连接...")
        
        try {
            val wifiClient = TransportSDK.getGlassesEngineService()
                ?.getAbsWifiP2PClientService()
            
            if (wifiClient == null) {
                updateStatus("WiFi P2P 服务不可用")
                return
            }
            
            // 添加连接监听器
            wifiClient.addWifiP2PListener(object : IWifiP2PListener {
                override fun onConnect() {
                    runOnUiThread {
                        isConnected = true
                        updateStatus("已连接到眼镜！")
                        Log.d(TAG, "WiFi P2P 已连接")
                    }
                }
                
                override fun onDisconnect() {
                    runOnUiThread {
                        isConnected = false
                        updateStatus("与眼镜断开连接")
                        Log.d(TAG, "WiFi P2P 已断开")
                    }
                }
                
                override fun onAudioStream(buffer: ByteBuffer) {
                    // 接收到眼镜传来的音频数据
                    Log.d(TAG, "收到音频流，大小: ${buffer.remaining()} bytes")
                }
                
                override fun onTextMessage(msg: String) {
                    runOnUiThread {
                        updateStatus("收到消息: $msg")
                        Log.d(TAG, "收到文本消息: $msg")
                    }
                }
                
                override fun onException(e: Exception) {
                    runOnUiThread {
                        updateStatus("连接异常: ${e.message}")
                        Log.e(TAG, "连接异常", e)
                    }
                }
            })
            
            updateStatus("WiFi P2P 监听器已添加，等待眼镜连接...")
            
        } catch (e: Exception) {
            updateStatus("连接测试异常: ${e.message}")
            Log.e(TAG, "连接测试异常", e)
        }
    }
    
    private fun sendTestMessage() {
        if (!isConnected) {
            Toast.makeText(this, "请先连接到眼镜", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val wifiClient = TransportSDK.getGlassesEngineService()
                ?.getAbsWifiP2PClientService()
            
            if (wifiClient == null) {
                updateStatus("WiFi P2P 服务不可用")
                return
            }
            
            // 发送测试消息
            val testMessage = """{"cmd":"DISPLAY","text":"Hello Rokid!","timestamp":${System.currentTimeMillis()}}"""
            
            wifiClient.sendTextMessage(testMessage)
            updateStatus("已发送: $testMessage")
            Log.d(TAG, "发送消息: $testMessage")
            
        } catch (e: Exception) {
            updateStatus("发送失败: ${e.message}")
            Log.e(TAG, "发送失败", e)
        }
    }
    
    private fun updateStatus(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        statusText.text = "[$timestamp] $message\n${statusText.text}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isInitialized) {
            TransportSDK.destroy()
        }
    }
}
