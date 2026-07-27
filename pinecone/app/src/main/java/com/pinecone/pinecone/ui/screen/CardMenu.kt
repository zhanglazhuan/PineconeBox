package com.pinecone.pinecone.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pinecone.pinecone.data.CourseItem
import com.pinecone.pinecone.ui.theme.*

/** Simple SharedPreferences-backed store for favorites and ratings */
object CardPrefs {
    private const val PREFS = "card_prefs"

    fun isFavorite(ctx: Context, id: Long): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean("fav_$id", false)

    fun setFavorite(ctx: Context, id: Long, fav: Boolean) =
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("fav_$id", fav).apply()

    fun getRating(ctx: Context, id: Long): Int =
        ctx.getSharedPreferences(PREFS, 0).getInt("rate_$id", 0)

    fun setRating(ctx: Context, id: Long, stars: Int, comment: String) =
        ctx.getSharedPreferences(PREFS, 0).edit().putInt("rate_$id", stars).putString("comment_$id", comment).apply()

    fun getComment(ctx: Context, id: Long): String =
        ctx.getSharedPreferences(PREFS, 0).getString("comment_$id", "") ?: ""

    fun isDeleted(ctx: Context, id: Long): Boolean =
        ctx.getSharedPreferences(PREFS, 0).getBoolean("del_$id", false)

    fun setDeleted(ctx: Context, id: Long) =
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean("del_$id", true).apply()
}

/** Long-press popup menu — centered overlay with glass background */
@Composable
fun CardContextMenu(
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onRate: () -> Unit,
    onDelete: () -> Unit,
    isFav: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PineElevated)
                .padding(8.dp)
        ) {
            // Favorite
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onFavorite() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (isFav) "★" else "☆", fontSize = 22.sp, color = if (isFav) PineWarning else PineTextSecondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(if (isFav) "取消收藏" else "收藏", fontSize = 18.sp, color = Color.White)
            }
            // Rate
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onRate() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 22.sp, color = PineTextSecondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("评分", fontSize = 18.sp, color = Color.White)
            }
            // Delete
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onDelete() }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("🗑", fontSize = 22.sp, color = PineError)
                Spacer(modifier = Modifier.width(12.dp))
                Text("删除", fontSize = 18.sp, color = PineError)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center) {
                Text("取消", fontSize = 15.sp, color = PineTextMuted)
            }
        }
    }
}

/** Star rating selector 1-5 */
@Composable
fun StarSelector(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.Center) {
        (1..5).forEach { star ->
            Text(
                text = if (star <= rating) "★" else "☆",
                fontSize = 40.sp,
                color = if (star <= rating) PineWarning else PineTextMuted,
                modifier = Modifier.clickable { onRatingChange(star) }.padding(4.dp)
            )
        }
    }
}

/** Rating dialog — stars + comment */
@Composable
fun RatingDialog(
    item: CourseItem,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var stars by remember { mutableIntStateOf(CardPrefs.getRating(ctx, item.id)) }
    var comment by remember { mutableStateOf(CardPrefs.getComment(ctx, item.id)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("评分: ${item.title}", color = Color.White, fontSize = 20.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StarSelector(rating = stars, onRatingChange = { stars = it })
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("写下你的评价…", color = PineTextMuted) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                CardPrefs.setRating(ctx, item.id, stars, comment)
                onDismiss()
            }) { Text(if (stars > 0) "提交 ${stars} 星评价" else "跳过", color = PinePrimary, fontSize = 16.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = PineTextSecondary, fontSize = 16.sp) }
        }
    )
}

/** Delete confirmation dialog */
@Composable
fun DeleteConfirmDialog(
    item: CourseItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除", color = Color.White, fontSize = 20.sp) },
        text = { Text("确定要删除「${item.title}」吗？\n删除后将不再显示此卡片。", color = PineTextSecondary, fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = {
                CardPrefs.setDeleted(ctx, item.id)
                onConfirm()
            }) { Text("删除", color = PineError, fontSize = 16.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = PineTextSecondary, fontSize = 16.sp) }
        }
    )
}
