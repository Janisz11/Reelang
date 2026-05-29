package com.example.reelang.ui.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reelang.ui.onboarding.ReelangRed

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
fun ReelTopBar(reel: ReelItem, onChannelClick: ((String) -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable(
                enabled = reel.ownerId != null && onChannelClick != null
            ) {
                reel.ownerId?.let { onChannelClick?.invoke(it) }
            }
        ) {
            val isUploadedReel = reel.youtubeId == null
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUploadedReel) ReelangRed
                        else Color.White.copy(alpha = 0.15f)
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isUploadedReel && reel.ownerAvatarInitials != null) {
                    Text(
                        text = reel.ownerAvatarInitials,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                } else {
                    Text(text = reel.avatarEmoji, fontSize = 18.sp)
                }
            }
            Column {
                Text(
                    text = reel.channelName.ifEmpty {
                        reel.ownerUsername ?: if (reel.ownerId != null) "User" else "Unknown"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (reel.ownerId != null && reel.channelName.isEmpty()) {
                    Text(
                        text = "tap to view profile",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor(reel.level))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = reel.level,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE65100).copy(alpha = 0.9f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "🔥", fontSize = 12.sp)
            Column {
                Text(
                    text = "${reel.streakDays} DAY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 12.sp
                )
                Text(
                    text = "STREAK",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 10.sp
                )
            }
        }
    }
}

// ─── Action Buttons ───────────────────────────────────────────────────────────

@Composable
fun ReelActions(
    reel: ReelItem,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likeColor by animateColorAsState(
        targetValue = if (reel.isLiked) ReelangRed else Color.White,
        animationSpec = tween(200), label = "likeColor"
    )
    val saveColor by animateColorAsState(
        targetValue = if (reel.isSaved) Color(0xFFFFD700) else Color.White,
        animationSpec = tween(200), label = "saveColor"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        FeedActionButton(
            icon = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            count = formatCount(reel.likes),
            tint = likeColor,
            onClick = onLike
        )
        FeedActionButton(
            icon = if (reel.isSaved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
            count = formatCount(reel.saves),
            tint = saveColor,
            onClick = onSave
        )
        FeedActionButton(
            icon = Icons.Outlined.Share,
            count = null,
            tint = Color.White,
            onClick = onShare
        )
    }
}

@Composable
fun FeedActionButton(
    icon: ImageVector,
    count: String?,
    tint: Color,
    onClick: () -> Unit
) {
    var bounced by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bounced) 1.35f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { bounced = false },
        label = "btnScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                bounced = true
                onClick()
            }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        if (count != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = count, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

// ─── Subtitle / Caption overlay ───────────────────────────────────────────────

@Composable
fun ClickableSubtitle(
    text: String,
    clickableWord: String,
    onWordClick: (String) -> Unit
) {
    val annotatedText = remember(text, clickableWord) {
        val idx = text.lowercase().indexOf(clickableWord.lowercase())
        buildAnnotatedString {
            val base = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            val highlight = SpanStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFD700),
                textDecoration = TextDecoration.Underline
            )
            if (idx < 0) {
                withStyle(base) { append(text) }
            } else {
                withStyle(base) { append(text.substring(0, idx)) }
                pushStringAnnotation("word", text.substring(idx, idx + clickableWord.length))
                withStyle(highlight) { append(text.substring(idx, idx + clickableWord.length)) }
                pop()
                withStyle(base) { append(text.substring(idx + clickableWord.length)) }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedText,
        lineHeight = 28.sp,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(onWordClick) {
            detectTapGestures { offset ->
                layoutResult?.let { lr ->
                    val pos = lr.getOffsetForPosition(offset)
                    annotatedText
                        .getStringAnnotations("word", pos, pos)
                        .firstOrNull()
                        ?.let { onWordClick(it.item) }
                }
            }
        }
    )
}

