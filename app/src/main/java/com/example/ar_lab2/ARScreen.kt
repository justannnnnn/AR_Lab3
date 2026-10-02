package com.example.ar_lab2

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARSceneView

@Composable
fun ARScreen() {
    //Все созданные AR-объекты.
    val objects = remember { mutableStateListOf<ARObject>() }

    //ID выбранного объекта.null означает, что объект не выбран.
    var selectedObjectId by remember { mutableStateOf<Long?>(null) }

    //ID для следующего объекта.
    var nextObjectId by remember { mutableLongStateOf(0L) }

    // Последний полученный ARCore Frame.
    var currentFrame by remember { mutableStateOf<Frame?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = true,
            onSessionUpdated = { _, frame ->
                currentFrame = frame
            },
            onTouchEvent = { event, _ ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val frame = currentFrame ?: return@ARSceneView false

                    val hitResult = frame.hitTest(event.x, event.y).firstOrNull { hit ->
                        val trackable = hit.trackable
                        trackable is Plane &&
                                trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                trackable.isPoseInPolygon(hit.hitPose)
                    }

                    if (hitResult != null) {
                        val anchor = hitResult.createAnchor()
                        val objectId = nextObjectId++
                        objects += ARObject(
                            id = objectId,
                            anchor = anchor
                        )

                        selectedObjectId = objectId
                    }
                }
                true
            }
        ) {
            objects.forEach { arObject ->

                val modelInstance = remember(arObject.id) {
                    modelLoader.createModelInstance(
                        assetFileLocation = "squirrel.glb"
                    )
                }

                AnchorNode(anchor = arObject.anchor) {
                    ModelNode(
                        modelInstance = modelInstance,
                        scale = io.github.sceneview.math.Scale(0.3f * arObject.scale),
                        rotation = io.github.sceneview.math.Rotation(y = arObject.rotation)
                    )
                }
            }
        }

        ControlPanel(
            objects = objects,
            selectedObjectId = selectedObjectId,
            onSelectPrevious = {
                selectPreviousObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId,
                    onSelected = { selectedObjectId = it }
                )
            },
            onSelectNext = {
                selectNextObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId,
                    onSelected = { selectedObjectId = it }
                )
            },
            onRotate = {
                rotateSelectedObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId
                )
            },
            onScaleUp = {
                scaleSelectedObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId,
                    factor = 1.1f
                )
            },
            onScaleDown = {
                scaleSelectedObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId,
                    factor = 0.9f
                )
            },
            onDelete = {
                deleteSelectedObject(
                    objects = objects,
                    selectedObjectId = selectedObjectId,
                    onDeleted = {
                        selectedObjectId = null
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ControlPanel(
    objects: List<ARObject>,
    selectedObjectId: Long?,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    onRotate: () -> Unit,
    onScaleUp: () -> Unit,
    onScaleDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (selectedObjectId == null) {
                "Объект не выбран"
            } else {
                "Выбран объект №$selectedObjectId"
            },
            color = Color.White
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = objects.isNotEmpty(),
                onClick = onSelectPrevious
            ) {
                Text("←")
            }

            Button(
                enabled = objects.isNotEmpty(),
                onClick = onSelectNext
            ) {
                Text("→")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                enabled = selectedObjectId != null,
                onClick = onScaleDown
            ) {
                Text("−")
            }

            Button(
                enabled = selectedObjectId != null,
                onClick = onRotate
            ) {
                Text("↻")
            }

            Button(
                enabled = selectedObjectId != null,
                onClick = onScaleUp
            ) {
                Text("+")
            }
        }

        Button(
            enabled = selectedObjectId != null,
            onClick = onDelete
        ) {
            Text("Удалить")
        }
    }
}

private fun selectPreviousObject(
    objects: List<ARObject>,
    selectedObjectId: Long?,
    onSelected: (Long) -> Unit
) {
    if (objects.isEmpty()) return
    val currentIndex = objects.indexOfFirst { it.id == selectedObjectId }
    val newIndex = if (currentIndex <= 0) objects.lastIndex else currentIndex - 1
    onSelected(objects[newIndex].id)
}

private fun selectNextObject(
    objects: List<ARObject>,
    selectedObjectId: Long?,
    onSelected: (Long) -> Unit
) {
    if (objects.isEmpty()) return
    val currentIndex = objects.indexOfFirst { it.id == selectedObjectId }

    val newIndex = if (currentIndex == -1 || currentIndex == objects.lastIndex) {
        0
    } else {
        currentIndex + 1
    }

    onSelected(objects[newIndex].id)
}

private fun scaleSelectedObject(
    objects: MutableList<ARObject>,
    selectedObjectId: Long?,
    factor: Float
) {
    val index = objects.indexOfFirst { it.id == selectedObjectId }

    if (index == -1) return
    val objectToUpdate = objects[index]
    val newScale = (objectToUpdate.scale * factor).coerceIn(0.3f, 3f)
    objects[index] = objectToUpdate.copy(scale = newScale)
}

private fun rotateSelectedObject(
    objects: MutableList<ARObject>,
    selectedObjectId: Long?
) {
    val index = objects.indexOfFirst { it.id == selectedObjectId }
    if (index == -1) return
    val objectToUpdate = objects[index]
    objects[index] = objectToUpdate.copy(rotation = objectToUpdate.rotation + 15f)
}

private fun deleteSelectedObject(
    objects: MutableList<ARObject>,
    selectedObjectId: Long?,
    onDeleted: () -> Unit
) {
    val objectToDelete = objects.firstOrNull { it.id == selectedObjectId }
    objectToDelete?.anchor?.detach()
    objects.removeAll { it.id == selectedObjectId }
    onDeleted()
}