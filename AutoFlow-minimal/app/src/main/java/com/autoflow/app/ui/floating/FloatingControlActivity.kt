package com.autoflow.app.ui.floating

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.IsSoftwareRendered
import androidx.compose.ui.window.WindowManager
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.core.automation.AutomationService
import com.autoflow.app.core.script.ScriptExecutor
import com.autoflow.app.core.script.ScriptModels
import com.autoflow.app.ui.common.AutoFlowTheme
import com.google.gson.Gson
import kotlinx.coroutines.flow.collectLatest

class FloatingControlActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FloatingControlActivity"
    }

    private var scriptExecutor: ScriptExecutor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it a floating window
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            window.attributes = params
        }

        scriptExecutor = ScriptExecutor(this)

        setContent {
            AutoFlowTheme {
                FloatingControlScreen(
                    onClose = { finish() },
                    onOpenSettings = { openOverlaySettings() },
                    executor = scriptExecutor
                )
            }
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay settings", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scriptExecutor?.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingControlScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    executor: ScriptExecutor?
) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(0) }
    var logs by remember { mutableStateOf(listOf<String>()) }

    // Load the predefined script
    val script = remember {
        createPredefinedScript()
    }

    // Collect execution state
    LaunchedEffect(executor) {
        executor?.executionState?.collectLatest { state ->
            isRunning = state.isRunning
            currentStep = state.currentStep
            logs = state.logs.toList()
        }
    }

    // Execution listener
    remember(executor) {
        executor?.listener = object : ScriptExecutor.ExecutionListener {
            override fun onStepStart(step: com.autoflow.app.core.script.ScriptStep, stepIndex: Int) {
                Log.d(TAG, "Step $stepIndex: ${step.description}")
            }

            override fun onStepComplete(step: com.autoflow.app.core.script.ScriptStep, stepIndex: Int) {
                Log.d(TAG, "Step $stepIndex completed")
            }

            override fun onError(error: String, stepIndex: Int) {
                Log.e(TAG, "Error at step $stepIndex: $error")
            }

            override fun onComplete() {
                Log.d(TAG, "Script completed")
            }

            override fun onLog(message: String) {
                logs = logs + message
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoFlow Control", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isRunning) "Running" else "Ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRunning) {
                            Text(
                                text = "Step $currentStep",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isRunning) {
                            Button(
                                onClick = { executor?.stop() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    executor?.execute(script)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Script Info
            Text(
                text = "Script: Dragon Ball Legends Setup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${script.steps.size} steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Logs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            // Quick Tips
            Text(
                text = "Make sure to have template images in assets/templates/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Create the predefined script based on user's instructions
 */
private fun createPredefinedScript(): ScriptModels.AutomationScript {
    return ScriptModels.AutomationScript(
        name = "Dragon Ball Legends Setup",
        description = "Auto setup script for Dragon Ball Legends game",
        steps = listOf(
            // Steps 1-19: App initialization and permissions
            ScriptModels.FindAndClickStep(
                templateName = "icon_niaotui",
                timeout = 10000,
                retryCount = 5,
                description = "Click '鸟腿包饭激战脚' icon"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_sulian",
                timeout = 5000,
                retryCount = 3,
                description = "Click '稍后提示'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_chushou",
                timeout = 5000,
                retryCount = 3,
                description = "Click '收起'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_niaoniao_add",
                timeout = 5000,
                retryCount = 3,
                description = "Click '鸟鸟剧情原作' add button"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_juqing",
                timeout = 5000,
                retryCount = 3,
                description = "Click '剧情'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_zhongxian",
                timeout = 5000,
                retryCount = 3,
                description = "Click '重现'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "toggle_kaiguan",
                timeout = 5000,
                retryCount = 3,
                description = "Click '调整设置' switch"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_jiahao_right",
                timeout = 5000,
                retryCount = 3,
                description = "Click right add button"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_green_play",
                timeout = 5000,
                retryCount = 3,
                description = "Click green play button"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_trial",
                timeout = 5000,
                retryCount = 3,
                description = "Click '试用'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_qu授权_pingmu",
                timeout = 5000,
                retryCount = 3,
                description = "Click '录制/投射屏幕' authorize"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_lijikaishi",
                timeout = 5000,
                retryCount = 3,
                description = "Click '立即开始'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_qu授权_wuai",
                timeout = 5000,
                retryCount = 3,
                description = "Click '无障碍服务' authorize"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_niaotui_script",
                timeout = 5000,
                retryCount = 3,
                description = "Click '鸟腿包饭激战脚本'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "toggle_script_switch",
                timeout = 5000,
                retryCount = 3,
                description = "Toggle script service switch"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_yunxu",
                timeout = 5000,
                retryCount = 3,
                description = "Click '允许'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_back",
                timeout = 5000,
                retryCount = 3,
                description = "Click back"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_back",
                timeout = 5000,
                retryCount = 3,
                description = "Click back again"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_wancheng",
                timeout = 5000,
                retryCount = 3,
                description = "Click '完成'"
            ),

            // Steps 20-46: Game configuration
            ScriptModels.FindAndClickStep(
                templateName = "btn_A_gamecenter",
                timeout = 5000,
                retryCount = 3,
                description = "Click game center A"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_godku",
                timeout = 5000,
                retryCount = 3,
                description = "Click Godku Project"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_arrow_right",
                timeout = 5000,
                retryCount = 3,
                description = "Click right arrow"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_fantizi",
                timeout = 5000,
                retryCount = 3,
                description = "Click '繁体字'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_ok",
                timeout = 5000,
                retryCount = 3,
                description = "Click 'OK'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_yes",
                timeout = 5000,
                retryCount = 3,
                description = "Click '是'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_tap",
                timeout = 5000,
                retryCount = 10,
                description = "Click 'TAP'"
            ),

            // Conditional: retry if error appears
            ScriptModels.FindAndClickStep(
                templateName = "btn_chongshi",
                timeout = 3000,
                retryCount = 1,
                description = "Click '重试' if appears"
            ),

            ScriptModels.FindAndClickStep(
                templateName = "btn_bandai",
                timeout = 5000,
                retryCount = 3,
                description = "Click 'BANDAI NAMCO'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_tongyi",
                timeout = 5000,
                retryCount = 3,
                description = "Click '同意'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_quanbujieshou",
                timeout = 5000,
                retryCount = 3,
                description = "Click '全部接受'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_tongyi2",
                timeout = 5000,
                retryCount = 3,
                description = "Click '同意'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_kaishixinyouxi",
                timeout = 5000,
                retryCount = 3,
                description = "Click '开始新游戏'"
            ),

            // Puzzle step - requires template from screen capture
            ScriptModels.FindAndClickStep(
                templateName = "btn_ok_puzzle",
                timeout = 30000,
                retryCount = 10,
                description = "Complete puzzle and click OK"
            ),

            ScriptModels.FindAndClickStep(
                templateName = "btn_quanbuxiazai",
                timeout = 10000,
                retryCount = 3,
                description = "Click '全部下载'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_yes2",
                timeout = 5000,
                retryCount = 3,
                description = "Click '是'"
            ),

            // Loop: tap until yellow plus appears
            ScriptModels.LoopStep(
                count = 20,
                steps = listOf(
                    ScriptModels.FindAndClickStep(
                        templateName = "btn_tiaoiguo",
                        timeout = 2000,
                        retryCount = 1,
                        description = "Click '跳过'"
                    ),
                    ScriptModels.FindAndClickStep(
                        templateName = "btn_tap2",
                        timeout = 2000,
                        retryCount = 1,
                        description = "Click 'TAP'"
                    ),
                    ScriptModels.WaitStep(
                        duration = 1000,
                        description = "Wait for next screen"
                    )
                ),
                description = "Loop tap until plus appears"
            ),

            ScriptModels.FindAndClickStep(
                templateName = "btn_white_menu",
                timeout = 5000,
                retryCount = 3,
                description = "Click white menu box"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_pve_mods",
                timeout = 5000,
                retryCount = 3,
                description = "Click 'PVE Mods'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "toggle_instant_win",
                timeout = 5000,
                retryCount = 3,
                description = "Toggle 'Instant Win'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "toggle_ai_challenge",
                timeout = 5000,
                retryCount = 3,
                description = "Toggle 'AI Challenge'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_white_menu",
                timeout = 5000,
                retryCount = 3,
                description = "Click white menu again"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_miscs",
                timeout = 5000,
                retryCount = 3,
                description = "Click 'Miscs'"
            ),
            ScriptModels.SwipeSliderStep(
                templateName = "slider_speedhack",
                startPercent = 0f,
                endPercent = 1f,
                duration = 1000L,
                description = "Slide Speedhack to max"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_minimize",
                timeout = 5000,
                retryCount = 3,
                description = "Click 'Minimize'"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_blue_A",
                timeout = 5000,
                retryCount = 3,
                description = "Click blue A button"
            ),
            ScriptModels.FindAndClickStep(
                templateName = "btn_green_play2",
                timeout = 5000,
                retryCount = 3,
                description = "Click green play button"
            )
        )
    )
}
