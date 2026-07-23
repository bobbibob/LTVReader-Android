package com.ltvreader.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ltvreader.R
import com.ltvreader.ui.navigation.Routes

/**
 * Bottom navigation + scaffold for all screens.
 * Marked as ExperimentalMaterial3Api because NavigationBarItem is experimental
 * in Compose Material 3 1.2.x.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                BottomNavItem(nav, current, Routes.Editor, R.string.nav_editor, Icons.Default.Edit)
                BottomNavItem(nav, current, Routes.Projects, R.string.nav_projects, Icons.Default.Folder)
                BottomNavItem(nav, current, Routes.Voices, R.string.nav_voices, Icons.Default.MicNone)
                BottomNavItem(nav, current, Routes.Settings, R.string.nav_settings, Icons.Default.Settings)
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomNavItem(
    nav: NavController,
    currentRoute: String?,
    route: String,
    labelRes: Int,
    icon: ImageVector,
) {
    val isSelected = currentRoute?.startsWith(route) == true
    val onClickLambda: () -> Unit = {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavigationBarItem(
        selected = isSelected,
        onClick = onClickLambda,
        icon = { Icon(imageVector = icon, contentDescription = null) },
        label = { Text(text = stringResource(labelRes)) },
    )
}
