package com.pinecone.pinecone.ui.guard.editors

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.*

/** Stored in app-level SharedPreferences, not guard data. */
object BrowserPrefs {
    private const val PREFS = "pinecone_browser"
    private const val KEY_ALLOW_FREE = "allow_free_browsing"

    fun isAllowFreeBrowsing(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_FREE, false)

    fun setAllowFreeBrowsing(context: Context, allow: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ALLOW_FREE, allow).apply()
    }
}

class BrowserRestrictActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "浏览器限制", onBack = { finish() }) {
                    BrowserRestrictEditor()
                }
            }
        }
    }
}

@Composable
private fun BrowserRestrictEditor() {
    val context = LocalContext.current
    var allow by remember {
        mutableStateOf(BrowserPrefs.isAllowFreeBrowsing(context))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "自由输入网址",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Text(
            text = if (allow) "开启：用户可以输入任意网址浏览" else "关闭：只能访问学习白名单中的网站",
            fontSize = 14.sp,
            color = PineTextSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        Switch(
            checked = allow,
            onCheckedChange = { checked ->
                allow = checked
                BrowserPrefs.setAllowFreeBrowsing(context, checked)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "⚠️ 关闭时，浏览器内点击外部链接会被拦截，无法跳转到非学习网站。",
            fontSize = 14.sp,
            color = PineTextSecondary
        )
    }
}
