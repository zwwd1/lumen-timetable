package com.tyust.course.scvtc

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.scvtc.campus.guide.SchoolGuideDeck
import com.tyust.course.ui.system.SystemDialog

@Composable
fun SchoolUsageGuide(onDismiss: () -> Unit, onConnect: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences("school-illustrated-guide-v2", Context.MODE_PRIVATE) }
    val initialPage = remember(prefs) { prefs.getInt("page", 0).coerceIn(0, 5) }
    val height = (LocalConfiguration.current.screenHeightDp.dp - 80.dp).coerceAtMost(650.dp).coerceAtLeast(220.dp)
    SystemDialog(onDismissRequest = onDismiss, useVisualEffects = false,
        contentPadding = PaddingValues(0.dp), ownerKey = "school-illustrated-guide-v2") {
        SchoolGuideDeck("Lumen 课程表", "设置", Modifier.fillMaxWidth().height(height), initialPage,
            reduceMotion = !android.animation.ValueAnimator.areAnimatorsEnabled() || NextAppearance.theme.reduceMotion,
            onPageChanged = { prefs.edit().putInt("page", it).apply() },
            onClose = onDismiss,
            onFinish = { prefs.edit().putInt("page", 0).putBoolean("completed", true).apply(); onDismiss() },
            onConnect = onConnect)
    }
}
