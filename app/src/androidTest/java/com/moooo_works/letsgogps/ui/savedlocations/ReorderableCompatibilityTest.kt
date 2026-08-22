package com.moooo_works.letsgogps.ui.savedlocations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class ReorderableCompatibilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nonEmptyReorderableList_rendersAndMovesItemFromDragHandle() {
        lateinit var locations: SnapshotStateList<String>
        var dragStopped = false

        composeRule.setContent {
            locations = remember { mutableStateListOf("First location", "Second location") }
            val lazyListState = rememberLazyListState()
            val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                locations.add(to.index, locations.removeAt(from.index))
            }

            MaterialTheme {
                LazyColumn(state = lazyListState) {
                    items(locations, key = { it }) { location ->
                        ReorderableItem(state = reorderState, key = location) {
                            Row {
                                Text(location)
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .testTag("drag-handle-$location")
                                        .draggableHandle(onDragStopped = { dragStopped = true })
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("First location").assertIsDisplayed()
        composeRule.onNodeWithTag("drag-handle-First location").apply {
            assertIsDisplayed()
            performTouchInput {
                swipe(
                    start = center,
                    end = center + Offset(0f, 350f),
                    durationMillis = 1_000
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(listOf("Second location", "First location"), locations.toList())
            assertTrue(dragStopped)
        }
    }
}
