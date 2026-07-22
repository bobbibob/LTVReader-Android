package com.ltvreader.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ltvreader.R
import com.ltvreader.ui.navigation.Routes

/**
 * Нижняя навигация + Scaffold для всех экранов.
 */
@Composable
fun LTVScaffold(
    nav: NavController,
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            LTVTopBar(title = title, onBack = onBack)
        },
        bottomBar = {
            NavigationBar {
                NavItem(nav, current, Routes.Editor, R.string.nav_editor, Icons.Default.Edit)
                NavItem(nav, current, Routes.Projects, R.string.nav_projects, Icons.Default.Folder)
                NavItem(nav, current, Routes.Voices, R.string.nav_voices, Icons.Default.MicNone)
                NavItem(nav, current, Routes.Settings, R.string.nav_settings, Icons.Default.Settings)
            }
        },
        content = content,
    )
}

@Composable
private fun NavItem(
    nav: NavController,
    currentRoute: String?,
    route: String,
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val isSelected = currentRoute?.startsWith(route) == true
    NavigationBarItem(
        selected = isSelected,
        onClick = {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}
