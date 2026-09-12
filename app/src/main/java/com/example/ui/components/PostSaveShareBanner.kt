package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class PostSaveShareData(
    val message: String,
    val onShare: () -> Unit
)

/**
 * Floating dark banner displayed at center/mid-screen matching the requested image.
 * Shows share icon on the left, remaining debt/operation info in center, and "إخفاء" on the right.
 */
@Composable
fun PostSaveShareBanner(
    data: PostSaveShareData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(data) {
        if (data != null) {
            delay(7000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 40 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { 40 }),
        modifier = modifier
    ) {
        data?.let { shareData ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2C2C2C),
                shadowElevation = 8.dp,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Share Icon
                    IconButton(
                        onClick = {
                            shareData.onShare()
                            onDismiss()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة عبر واتساب",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Remaining/Balance message (Clickable to share)
                    Text(
                        text = shareData.message,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clickable {
                                shareData.onShare()
                                onDismiss()
                            }
                    )

                    // Right: Hide Button
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "إخفاء",
                            color = Color(0xFF90CAF9),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
