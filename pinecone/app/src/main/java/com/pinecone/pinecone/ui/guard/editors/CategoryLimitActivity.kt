package com.pinecone.pinecone.ui.guard.editors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.guard.data.model.CategoryLimit
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.PineconeTheme

class CategoryLimitActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "内容分类限制", onBack = { finish() }) {
                    CategoryLimitEditor()
                }
            }
        }
    }
}

@Composable
private fun CategoryLimitEditor() {
    val labels = mapOf(
        "english" to "🔤 英语学习", "reading" to "📚 绘本阅读",
        "documentary" to "🎬 纪录片", "apps" to "🧩 学习 App"
    )
    val options = listOf(null to "不限", 15 to "15分钟", 30 to "30分钟",
        45 to "45分钟", 60 to "60分钟", 90 to "90分钟")

    val initialLimits = GuardClientHolder.cachedRules.categoryLimits.associate { it.categoryId to it.dailyMinutes }
    val limits = remember {
        mutableStateMapOf(
            "english" to (initialLimits["english"] ?: 60),
            "reading" to (initialLimits["reading"] ?: null),
            "documentary" to (initialLimits["documentary"] ?: null),
            "apps" to (initialLimits["apps"] ?: 45)
        )
    }

    fun save() {
        val newLimits = limits.map { (cid, min) -> CategoryLimit(cid, labels[cid] ?: cid, min) }
        GuardClientHolder.updateCategoryLimits(newLimits)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        limits.keys.forEach { id ->
            var optIdx by remember { mutableIntStateOf(options.indexOfFirst { it.first == limits[id] }.coerceAtLeast(0)) }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(labels[id] ?: id, fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = {
                    optIdx = (optIdx + 1) % options.size
                    limits[id] = options[optIdx].first
                    save()
                }) {
                    Text(options[optIdx].second, fontSize = 18.sp)
                }
            }
        }
    }
}
