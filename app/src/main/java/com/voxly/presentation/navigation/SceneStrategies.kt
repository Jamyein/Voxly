package com.voxly.presentation.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Bottom Sheet Scene - displays content in a ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden  // 允许中间状态 (默认 enabledValues 包含全部三个状态)
        )
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = sheetState,
            properties = modalBottomSheetProperties,
            dragHandle = {  // 添加拖拽手柄
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        ) {
            entry.Content()
        }
    }
}

/**
 * Bottom Sheet Scene Strategy - displays entries with bottomSheet metadata in a ModalBottomSheet
 *
 * Note: Navigation 3 does not ship an official BottomSheetSceneStrategy yet, so we provide a
 * custom implementation that wraps a ModalBottomSheet around the entry's content. The
 * ModalBottomSheetProperties are passed via the entry's metadata map.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull()
        val bottomSheetProperties = lastEntry?.metadata?.get(BOTTOM_SHEET_KEY) as? ModalBottomSheetProperties
        return bottomSheetProperties?.let { properties ->
            @Suppress("UNCHECKED_CAST")
            BottomSheetScene(
                key = lastEntry.contentKey as T,
                previousEntries = entries.dropLast(1),
                overlaidEntries = entries.dropLast(1),
                entry = lastEntry,
                modalBottomSheetProperties = properties,
                onBack = onBack
            )
        }
    }

    companion object {
        const val BOTTOM_SHEET_KEY = "voxly_bottomsheet"

        @OptIn(ExperimentalMaterial3Api::class)
        fun bottomSheet(
            modalBottomSheetProperties: ModalBottomSheetProperties = ModalBottomSheetProperties()
        ): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to modalBottomSheetProperties)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> rememberBottomSheetSceneStrategy(): SceneStrategy<T> = BottomSheetSceneStrategy<T>()
