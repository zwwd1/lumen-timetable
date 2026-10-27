package cn.scvtc.campus.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val guideLabels = listOf("连接", "课表", "作息", "同步", "迁移", "体验")
private val guideColors = listOf(Color(0xFF3978F6), Color(0xFF8A63DC), Color(0xFF159BA5),
    Color(0xFF2A9E70), Color(0xFFE69938), Color(0xFF5876DC))

/** Native illustrations and practice controls; examples never write academic or appearance data. */
@Composable
fun SchoolGuideDeck(
    appName: String,
    settingsLabel: String,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    reduceMotion: Boolean = false,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    onConnect: () -> Unit
) {
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, 5)) { 6 }
    val scope = rememberCoroutineScope()
    val progressCallback by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager.settledPage) { progressCallback(pager.settledPage) }
    val page = pager.currentPage
    val accent = guideColors[page]
    val colors = MaterialTheme.colorScheme
    fun navigate(index: Int) { scope.launch {
        if (reduceMotion) pager.scrollToPage(index) else pager.animateScrollToPage(index)
    } }
    BackHandler(enabled = page > 0) { navigate(page - 1) }
    Column(modifier.background(Brush.verticalGradient(listOf(colors.surface, colors.surfaceContainerLow)))
        .padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha = .13f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.School, null, tint = accent, modifier = Modifier.size(23.dp)) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(appName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("校园生活，从容开始", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "稍后再看使用指南")
            }
        }
        Spacer(Modifier.height(17.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            guideLabels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { navigate(index) }
                    .semantics { contentDescription = "第 ${index + 1} 步，$label${if (index == page) "，当前步骤" else ""}" }
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(
                        if (index <= page) accent else colors.outlineVariant.copy(alpha = .45f), CircleShape))
                    Spacer(Modifier.height(8.dp))
                    Text(label, fontSize = 11.sp, fontWeight = if (index == page) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == page) accent else colors.onSurfaceVariant)
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { index ->
            val (title, subtitle, detail) = guideCopy(index, settingsLabel)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 17.dp, bottom = 16.dp)) {
                Text("0${index + 1} / 新手旅程", color = guideColors[index], fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Text(title, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(subtitle, color = colors.onSurfaceVariant, fontSize = 14.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(20.dp))
                GuideIllustration(index, guideColors[index])
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = guideColors[index], modifier = Modifier.padding(top = 3.dp).size(18.dp))
                    Text(detail, Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall,
                        lineHeight = 21.sp, color = colors.onSurfaceVariant)
                }
                if (index == 0 || index == 5) TextButton(onClick = onConnect, modifier = Modifier.padding(top = 8.dp)) {
                    Text("连接教务系统", color = guideColors[index])
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 6.dp).size(16.dp), tint = guideColors[index])
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (page == 0) onConnect() else navigate(page - 1) },
                modifier = Modifier.weight(.36f).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text(if (page == 0) "连接" else "上一步") }
            Button(onClick = { if (page == 5) onFinish() else navigate(page + 1) },
                modifier = Modifier.weight(.64f).heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text(if (page == 5) "开始使用" else "下一步", fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 8.dp).size(18.dp))
            }
        }
        Text("左右翻页 · 上下滚动查看完整演示与说明", Modifier.padding(top = 12.dp),
            color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

private fun guideCopy(page: Int, settings: String): Triple<String, String, String> {
    val detailEntry = if (settings == "个人中心") "当前课表详细设置" else "当前课表详情设置"
    return when (page) {
    0 -> Triple("把校园，装进口袋", "课表、成绩与校园信息，按自己的节奏开始。先认识一下，再连接学校。",
        "使用川职学号与学校统一认证密码登录。验证码和安全确认需在学校认证页完成；等待“同步成功”后，课表才真正保存。也可以先手动添加或导入。")
    1 -> Triple("下一节课，一眼就懂", "试着点开下面的课程卡。老师、教室、时间，都在你需要的位置。",
        "切换日 / 周视图和教学周，点课程看详情，长按可编辑。本周空白时先核对学期、周次与同步结果；未出分可以切到往期或总体查看。")
    2 -> Triple("让时间，刚刚好", "点选上午、下午与晚上的作息预览，再试试课前提醒的样子。",
        "$settings → $detailEntry，核对第一周周一与节次。普通教学默认十节，已有自定义保留。实训为 08:30–11:40、14:00–17:10，课间十分钟；分节以学校安排为准。确认时间后再开启真实提醒与通知权限。")
    3 -> Triple("偶尔离线，也从容", "在示例中切换联网状态。同步没完成时，已保存的课表依然陪着你。",
        "从教务同步入口查看各项结果、保存时间和错误，按需重试失败项。修改学校密码或遇到学校安全确认时重新登录；后台任务可能被系统延后，需要最新结果时手动刷新。")
    4 -> Triple("把课程，安心带走", "打开下面的迁移核对清单。先看导入预览，再核对你的上课安排。",
        "${settings}里的 CSV / ICS / HTML 入口可导出课程，导入前先核对预览，导入后重新检查学期、作息与提醒。本机历史保护不等同于可迁移的完整备份，云服务目前暂停。普通导出不包含学校密码，换手机需重新登录。")
    else -> Triple("好看，也顺手", "选一种预览风格，找到舒服的节奏。你的校园，按你的习惯来。",
        "在外观设置中调整材质与动效，滑动不顺时可先减少动态效果。更新使用本项目原签名包直接覆盖安装。反馈时附版本、机型与步骤，并隐藏学号、密码和个人成绩。")
}
}

@Composable
private fun GuideIllustration(page: Int, accent: Color) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = .13f), accent.copy(alpha = .035f))))
        .border(1.dp, accent.copy(alpha = .14f), RoundedCornerShape(25.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TouchApp, null, tint = accent, modifier = Modifier.size(15.dp))
            Text("互动演示", Modifier.padding(start = 5.dp), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("示例内容", color = colors.onSurfaceVariant, fontSize = 10.sp)
        }
        when (page) {
            0 -> ConnectionIllustration(accent)
            1 -> TimetableIllustration(accent)
            2 -> ClockIllustration(accent)
            3 -> SyncIllustration(accent)
            4 -> BackupIllustration(accent)
            else -> ExperienceIllustration(accent)
        }
    }
}

@Composable
private fun ExampleCard(icon: ImageVector, title: String, subtitle: String, accent: Color,
    onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(accent.copy(alpha = .11f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun ConnectionIllustration(accent: Color) {
    var connected by rememberSaveable { mutableStateOf(false) }
    ExampleCard(Icons.Outlined.School, "四川职业技术学院", "统一身份认证", accent)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(if (connected) Icons.Outlined.CheckCircle else Icons.Outlined.South, null, tint = accent, modifier = Modifier.size(24.dp))
    }
    ExampleCard(if (connected) Icons.Outlined.CalendarMonth else Icons.Outlined.TouchApp,
        if (connected) "示例课表已保存" else "点一下，预览连接结果",
        if (connected) "身份已确认 → 课程读取成功" else "连接成功与同步完成是两个步骤", accent, { connected = !connected })
}

@Composable
private fun TimetableIllustration(accent: Color) {
    var details by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("周一", "周二", "周三", "周四", "周五").forEachIndexed { i, day ->
            Box(Modifier.background(if (i == 0) accent else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 9.dp)) {
                Text(day, fontSize = 11.sp, color = if (i == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    ExampleCard(Icons.Outlined.MenuBook, "大学英语", "08:30–09:15 · 尚学楼 A301", accent, { details = !details }) {
        Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = accent)
    }
    if (details) Text("课程详情示例\n李老师 · 第 1 节 · 第 1–16 周\n再次点课程卡即可收起", fontSize = 12.sp, lineHeight = 21.sp)
    else Text("点课程卡，展开老师与上课周次", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
}

@Composable
private fun ClockIllustration(accent: Color) {
    var part by rememberSaveable { mutableIntStateOf(0) }
    var reminder by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("上午", "下午", "晚上").forEachIndexed { i, label -> FilterChip(selected = part == i, onClick = { part = i }, label = { Text(label, fontSize = 12.sp) }) }
    }
    val times = listOf("08:30–12:10", "14:00–17:30", "19:20–21:00")
    Text(times[part], color = accent, fontSize = 25.sp, fontWeight = FontWeight.Bold)
    ExampleCard(Icons.Outlined.Notifications, "提醒预览", if (reminder) "上课前十分钟，留一点从容" else "试着打开右侧开关", accent,
        trailing = { Switch(reminder, { reminder = it }, Modifier.semantics { contentDescription = "预览课前提醒，不会修改真实提醒" }) })
}

@Composable
private fun SyncIllustration(accent: Color) {
    var online by rememberSaveable { mutableStateOf(true) }
    ExampleCard(if (online) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
        if (online) "网络连接正常" else "暂时离线", "切换状态，看看课表会怎样", accent,
        trailing = { Switch(online, { online = it }, Modifier.semantics { contentDescription = "切换同步演示的联网状态" }) })
    ExampleCard(Icons.Outlined.EventAvailable, "已保存的课表", if (online) "可查看，联网后可刷新" else "依然可查看，联网后再同步", accent)
    Text(if (online) "同步结果会标注最近成功时间" else "同步失败不等于课表丢失", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun BackupIllustration(accent: Color) {
    var preview by rememberSaveable { mutableStateOf(false) }
    ExampleCard(Icons.Outlined.FolderOpen, "课程迁移核对清单", "点开，看看需要检查什么", accent, { preview = !preview })
    if (preview) {
        listOf("课程名称、时间与地点", "学期日期与节次作息", "导入后重新核对提醒").forEach { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = accent, modifier = Modifier.size(17.dp))
                Text(label, Modifier.padding(start = 8.dp), fontSize = 12.sp)
            }
        }
        Text("先核对，再确认导入", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    } else Text("课程导出不等于完整设置备份，迁移前后都要核对", fontSize = 12.sp, lineHeight = 20.sp)
}

@Composable
private fun ExperienceIllustration(accent: Color) {
    var vivid by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(!vivid, { vivid = false }, label = { Text("均衡预览") })
        FilterChip(vivid, { vivid = true }, label = { Text("明亮预览") })
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(if (vivid) listOf(accent, Color(0xFFA596F5)) else listOf(accent.copy(alpha = .7f), accent.copy(alpha = .45f))))
        .padding(20.dp)) {
        Column {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text("好看的每一天", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("轻触有回应，滑动更从容", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        }
    }
}
