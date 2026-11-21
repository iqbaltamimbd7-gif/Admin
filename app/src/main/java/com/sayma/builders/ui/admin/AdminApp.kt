package com.sayma.builders.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*

sealed interface AdminScreen {
    data class WorkerDetail(val worker: WorkerRec) : AdminScreen
    data class ProjectDetail(val project: ProjectRec) : AdminScreen
    data object Documents : AdminScreen
    data object Attendance : AdminScreen
    data object Requests : AdminScreen
    data object SalarySlips : AdminScreen
    data object Tools : AdminScreen
    data object Reminders : AdminScreen
}

private enum class AdminTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home), Workers("Workers", Icons.Filled.Groups), Sites("Sites", Icons.Filled.Apartment),
    Finance("Finance", Icons.Filled.AccountBalanceWallet), More("More", Icons.Filled.Settings)
}

@Composable
fun AdminApp(username: String, onLogout: () -> Unit) {
    val api = apiOf()
    var tab by remember { mutableStateOf(AdminTab.Home) }
    var screen by remember { mutableStateOf<AdminScreen?>(null) }
    var year by remember { mutableStateOf("") }
    val (state, reload) = useLoad(Unit, onAuthLost = onLogout) { loadAdminData(api) }

    BackHandler(enabled = screen != null) { screen = null }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (screen == null) SaymaBottomBar(
                items = AdminTab.entries.map { SaymaNavItem(it.label, it.icon) },
                selected = tab.label,
                onSelect = { label -> tab = AdminTab.entries.first { it.label == label } }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(ScreenBrush).statusBarsPadding()) {
            val s = screen
            when {
                s is AdminScreen.Documents -> DocumentsScreen { screen = null }
                s is AdminScreen.Attendance -> AttendanceScreen { screen = null }
                s is AdminScreen.Requests -> RequestsScreen { screen = null }
                s is AdminScreen.SalarySlips -> SalarySlipsScreen { screen = null }
                s is AdminScreen.Tools -> ToolsScreen { screen = null }
                else -> Loaded(state, reload) { data ->
                    val y = year.ifBlank { data.years.lastOrNull { it == "2026" } ?: data.years.last() }
                    when {
                        s is AdminScreen.WorkerDetail -> WorkerDetailScreen(data, s.worker, y, onRefresh = { screen = null; reload.trigger() }) { screen = null }
                        s is AdminScreen.ProjectDetail -> ProjectDetailScreen(data, s.project, onRefresh = { screen = null; reload.trigger() }) { screen = null }
                        s is AdminScreen.Reminders -> RemindersScreen(data) { screen = null }
                        else -> when (tab) {
                            AdminTab.Home -> HomeScreen(data, username, y, { year = it }, { screen = it })
                            AdminTab.Workers -> WorkersScreen(data) { screen = AdminScreen.WorkerDetail(it) }
                            AdminTab.Sites -> SitesScreen(data) { screen = AdminScreen.ProjectDetail(it) }
                            AdminTab.Finance -> FinanceScreen(data, y) { year = it }
                            AdminTab.More -> MoreScreen(username, { screen = it }, onLogout, reload)
                        }
                    }
                }
            }
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0x2276C9C3)

@Composable
internal fun ScreenScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), content = content)
}

@Composable
internal fun BackBar(title: String, subtitle: String = "", onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Teal) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted)
        }
    }
}
