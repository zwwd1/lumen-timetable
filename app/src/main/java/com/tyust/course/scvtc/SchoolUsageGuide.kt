package com.tyust.course.scvtc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton

private val schoolUsageSteps = listOf(
    "连接学校" to "填写川职学号和学校统一认证密码，点击登录。密码加密保存在这台手机，身份核验后先读课表，再读成绩等数据。\n\n验证码或安全确认需要在学校认证页完成，再回应用继续。登录成功不等于课表已保存，请查看教务同步结果。\n\n可以稍后连接；以后从设置里的账号入口或教务同步页重新登录。",
    "查看课表、成绩与考试" to "底栏“课表”切换日 / 周视图和教学周，点课程看老师、教室和时间。“课程”页可打开成绩与考试、学分概况。\n\n当前学期未出分时，可查看往期或总体。“没有记录”与“读取失败”不同，先看保存时间和结果。\n\n本周空白时核对学期、周次与开学日期，不必删除原表或清数据。",
    "校准课表与提醒" to "课表右上角三点 → 课表设置，或设置 → 当前课表详情设置，核对第一周周一、节次和作息时间。按学校真实作息填写。\n\n时间正确后再启用课前提醒，并允许系统通知。桌面小组件需要在桌面里添加，读取应用已保存课表。\n\n更换学期后，再检查日期和时间，避免提醒落到错误日期。",
    "同步失败时怎么办" to "课程 → 教务同步与备份，可查看各项服务的成功时间和错误，按需重试失败项。刷新时等当前任务完成。\n\n断网或学校服务异常时保留已保存数据，联网后重试。会话过期会尝试恢复；修改学校密码、换手机或学校强制验证时，需要重新登录。\n\n后台更新受系统调度影响，需要最新结果时手动刷新，再按需检查自启动和电量设置。",
    "保存文件与迁移" to "设置里选择 CSV / ICS / HTML 等导入导出入口。导入先检查预览再确认；导出时在系统文件选择器里选择位置。\n\n使用备份功能迁移，并核对手动课程、作息和提醒；普通导出不含学校密码。换手机后需要重新登录，两款课程表不会自动共享账号和数据。\n\n更新应用直接覆盖安装，保留原数据，不必卸载。",
    "按手机表现调整效果" to "设置 → 卡片、动效与校园助手，可调整模糊、折射、色散和入场动画；液态玻璃总开关在设置 → 外观。滑动不顺时按需减少动态效果。\n\n应用遵循系统动画设置，系统关闭动画时，部分过渡会直接完成。设置 → 使用指南，可随时重看这些步骤。\n\n反馈时附版本、机型、复现步骤和错误提示，隐藏学号、密码、Cookie 和个人成绩。"
)

@Composable
fun SchoolUsageGuide(onDismiss: () -> Unit, onConnect: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val step = schoolUsageSteps[page]
    SystemDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用指南 · ${page + 1}/${schoolUsageSteps.size}",
            style = MaterialTheme.typography.titleLarge) },
        confirmButton = {
            SystemPrimaryButton(if (page == schoolUsageSteps.lastIndex) "连接教务" else "下一步", {
                if (page == schoolUsageSteps.lastIndex) onConnect() else page++
            }, Modifier.fillMaxWidth())
        },
        dismissButton = { SystemSecondaryButton(if (page == schoolUsageSteps.lastIndex) "完成" else "稍后再看",
            onDismiss, Modifier.fillMaxWidth()) }
    ) {
        key(page) {
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${step.first}\n\n${step.second}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (page > 0) SystemSecondaryButton("上一步", { page = (page - 1).coerceAtLeast(0) }, Modifier.fillMaxWidth())
    }
}
