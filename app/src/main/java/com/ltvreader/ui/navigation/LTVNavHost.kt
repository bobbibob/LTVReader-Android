package com.ltvreader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ltvreader.ui.screens.editor.EditorScreen
import com.ltvreader.ui.screens.generation.GenerationScreen
import com.ltvreader.ui.screens.music.MusicMixScreen
import com.ltvreader.ui.screens.projects.ProjectsScreen
import com.ltvreader.ui.screens.review.ReviewScreen
import com.ltvreader.ui.screens.settings.SettingsScreen
import com.ltvreader.ui.screens.voices.VoicesScreen
import com.ltvreader.ui.screens.models.ModelsScreen
import com.ltvreader.ui.screens.onboarding.OnboardingScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Editor = "editor"
    const val Projects = "projects"
    const val Voices = "voices"
    const val Settings = "settings"
    const val Models = "models"
    const val Generation = "generation/{projectId}"
    const val Review = "review/{audiobookId}"
    const val MusicMix = "music/{audiobookId}"

    fun generation(projectId: Long) = "generation/$projectId"
    fun review(audiobookId: Long) = "review/$audiobookId"
    fun musicMix(audiobookId: Long) = "music/$audiobookId"
}

@Composable
fun LTVNavHost(nav: NavHostController, startDestination: String = Routes.Editor) {
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.Onboarding) { OnboardingScreen(nav) }
        composable(Routes.Editor) { EditorScreen(nav) }
        composable(Routes.Projects) { ProjectsScreen(nav) }
        composable(Routes.Generation) { entry ->
            val id = entry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
            GenerationScreen(nav, id)
        }
        composable(Routes.Review) { entry ->
            val id = entry.arguments?.getString("audiobookId")?.toLongOrNull() ?: 0L
            ReviewScreen(nav, id)
        }
        composable(Routes.MusicMix) { entry ->
            val id = entry.arguments?.getString("audiobookId")?.toLongOrNull() ?: 0L
            MusicMixScreen(nav, id)
        }
        composable(Routes.Voices) { VoicesScreen(nav) }
        composable(Routes.Models) { ModelsScreen(nav) }
        composable(Routes.Settings) { SettingsScreen(nav) }
    }
}
