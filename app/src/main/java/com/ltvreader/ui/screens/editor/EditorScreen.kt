package com.ltvreader.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ltvreader.R
import com.ltvreader.app.AppContainer
import com.ltvreader.ui.components.LTVScaffold
import com.ltvreader.ui.markup.MarkupHighlighter
import kotlinx.coroutines.launch

/**
 * Главный экран: редактор текста с LTV-разметкой.
 *
 * Прямой порт `MainWindow._build_editor_panel()` из `app/ui/main_window.py`.
 *
 * Возможности:
 *   - ввод/вставка текста
 *   - подсветка LTV-разметки
 *   - сохранение в проект (Room)
 *   - переход к генерации
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    nav: NavController,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory(LocalContext.current)),
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_editor),
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                label = { Text("Project title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Текстовый редактор
            val annotated = remember(state.text) { MarkupHighlighter.highlight(state.text) }
            OutlinedTextField(
                value = annotated,
                onValueChange = { /* тред AnnotatedString -> String не идеален; используем простой TextField */ },
                label = { Text(stringResource(R.string.editor_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                colors = TextFieldDefaults.colors(),
                supportingText = {
                    Text("${state.text.length} chars • ${state.chunkCount} chunks")
                },
            )

            // Простой TextField поверх — основной для редактирования.
            // AnnotatedString используется только для подсветки превью.
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Plain text (edit here)") },
                minLines = 4,
                maxLines = 8,
            )

            // Режим разбиения
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("safe_chunks", "sentence", "paragraph").forEach { mode ->
                    FilterChip(
                        selected = state.splitMode == mode,
                        onClick = { vm.setSplitMode(mode) },
                        label = { Text(mode) },
                    )
                }
            }

            // Действия
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val projectId = vm.save(context)
                            if (projectId != null) {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.generation(projectId))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("  " + stringResource(R.string.action_save) + " & Generate")
                }
                Button(
                    onClick = {
                        scope.launch {
                            val projectId = vm.save(context)
                            if (projectId != null) {
                                nav.navigate(com.ltvreader.ui.navigation.Routes.generation(projectId))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Text("  " + stringResource(R.string.gen_start))
                }
            }
        }
    }
}
