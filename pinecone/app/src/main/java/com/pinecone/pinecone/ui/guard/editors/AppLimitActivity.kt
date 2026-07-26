package com.pinecone.pinecone.ui.guard.editors

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.data.model.AppLimit
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.*

class AppLimitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "App 限制", onBack = { finish() }) {
                    AppLimitEditor()
                }
            }
        }
    }
}

data class AppInfo(val pkg: String, val label: String)

@Composable
private fun AppLimitEditor() {
    val context = LocalContext.current
    val pm = context.packageManager

    // Load installed launchable apps (exclude system apps)
    val allApps = remember {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString() }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString()) }
    }

    // Currently restricted apps from GuardClientHolder
    val currentLimits = remember {
        GuardClientHolder.cachedRules.appLimits.associate { it.packageName to it.dailyMinutes }
    }

    var selected by remember {
        mutableStateOf(currentLimits.keys.toSet())
    }

    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Current restrictions
        Text("已限制的 App", fontSize = 18.sp, fontWeight = FontWeight.Medium,
            color = Color.White, modifier = Modifier.padding(bottom = 12.dp))

        if (selected.isEmpty()) {
            Text("暂无限制", fontSize = 16.sp, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(allApps.filter { it.pkg in selected }) { app ->
                    Text("• ${app.label}", fontSize = 16.sp, color = PineTextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Add button
        Button(onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()) {
            Text("+ 添加 App 限制", fontSize = 18.sp)
        }
    }

    // App picker dialog
    if (showPicker) {
        var tempSelected by remember { mutableStateOf(selected.toSet()) }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择要限制的 App") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(allApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    tempSelected = if (app.pkg in tempSelected)
                                        tempSelected - app.pkg
                                    else
                                        tempSelected + app.pkg
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.pkg in tempSelected,
                                onCheckedChange = {
                                    tempSelected = if (it) tempSelected + app.pkg
                                    else tempSelected - app.pkg
                                }
                            )
                            Text(app.label, fontSize = 16.sp, color = Color.White,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = tempSelected
                    // Apply: set 60 min default limit for selected apps
                    val limits = selected.map { pkg ->
                        AppLimit(pkg, allApps.find { it.pkg == pkg }?.label ?: pkg, 60)
                    }
                    GuardClientHolder.updateAppLimits(limits)
                    Toast.makeText(context, "已限制 ${selected.size} 个 App", Toast.LENGTH_SHORT).show()
                    showPicker = false
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        )
    }
}
