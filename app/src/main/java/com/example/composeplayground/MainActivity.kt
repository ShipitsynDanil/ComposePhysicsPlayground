package com.example.composeplayground

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.monotonicFrameClock
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composeplayground.ui.theme.ComposePlaygroundTheme
import java.lang.ref.WeakReference
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposePlaygroundTheme {
                PhysicsLayout()
            }
        }
    }
}

@Composable
fun PhysicsLayout(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier

            .fillMaxSize()
    ) {

        val state = rememberPhysicsState(false)
        Box(
            modifier = Modifier
                .applyPhysics(state)
                .graphicsLayer {
                    translationX = state.offset.x
                    translationY = state.offset.y
                }
                .size(200.dp)
                .background(Color.Red)
                .align(Alignment.Center)
        )
        Column {
            Button(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                onClick = {
                    state.updateRunningState(!state.isRunning)
                }
            ) {
                Text(
                    text = if (state.isRunning) {
                        "Stop simulation"
                    } else {
                        "Start simulation"
                    }
                )
            }
            Text(text = "Velocity: ${state.velocity}")
            Text(text = "offset x: ${state.offset.x}")
            Text(text = "offset y: ${state.offset.y}")
            Text(text = "parentBounds top left x: ${state.parentBounds.topLeft.x}")
            Text(text = "parentBounds top left x y: ${state.parentBounds.topLeft.y}")
            Text(text = "offset y: ${state.parentBounds.bottomRight.x}")
            Text(text = "offset y: ${state.parentBounds.bottomRight.y}")
            Text(text = "Frame time: ${state.frameTimeMs} ms")
        }
    }
}


const val g = 9.80665

@Composable
fun rememberPhysicsState(isRunning: Boolean = true): PhysicsState {
    val state = remember {
        PhysicsState(isRunning)
    }
    state.startSimulation()
    return state
}

fun Modifier.applyPhysics(state: PhysicsState) =
    onGloballyPositioned { layoutCoordinates ->
        state.updateCoordinates(layoutCoordinates.positionInWindow())
        state.updateBounds(layoutCoordinates.boundsInWindow())
        val parentLayoutCoordinates = layoutCoordinates.parentLayoutCoordinates
            ?: throw IllegalStateException("Any physic body must be have a parent!!!")
        state.updateParentBounds(parentLayoutCoordinates.boundsInWindow())
    }
        .graphicsLayer {
            state.updateShape(shape)
        }


@Stable
fun Offset.toIntOffset() = IntOffset(x.roundToInt(), y.roundToInt())

@Stable
class SimulationState {


    val objects = mutableStateMapOf<String, WeakReference<PhysicsState>>()

    fun ok() {
        val t = WeakReference<Int>(1)

    }

}

@Stable
class PhysicsState(isRunning: Boolean) {

    var x: Float by mutableStateOf(-1f)
        private set

    var y: Float by mutableStateOf(-1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    var rect: Rect by mutableStateOf(Rect(-1f, -1f, -1f, -1f))
        private set

    var parentBounds: Rect by Delegates.notNull()

    private var restitution = 1

    var shape: Shape by mutableStateOf(RectangleShape)
        private set

    var isRunning by mutableStateOf(true)
        private set

    var velocity by mutableStateOf(0.0)
        private set

    var frameTimeMs: Long by mutableStateOf(0)
        private set

    private var lastEmulationTime: Long = 0

    init {
        this.isRunning = isRunning
    }

    fun updateCoordinates(offset: Offset) {
        x = offset.x
        y = offset.y
    }

    fun updateBounds(rect: Rect) {
        this.rect = rect
    }

    fun updateParentBounds(newBounds: Rect) {
        parentBounds = newBounds
    }

    fun updateShape(shape: Shape) {
        this.shape = shape
    }

    fun updateOffset(offset: Offset) {
        this.offset = offset
    }

    fun updateRunningState(newIsRunning: Boolean) {
        isRunning = newIsRunning
        Log.d("OKOK", "IS RUNNING ${isRunning}")
    }

    @OptIn(ExperimentalComposeApi::class)
    @Suppress("ComposableNaming")
    @Composable
    fun startSimulation() {
        val startTime =
            if (lastEmulationTime <= 0.0) System.currentTimeMillis() else lastEmulationTime
        LaunchedEffect(this, isRunning) {
            if (isRunning) {
                while (!haveCollision(rect, parentBounds)) {
                    Log.d("OKOK", "HI")
                    this.coroutineContext.monotonicFrameClock.withFrameMillis { frameTimeMillis ->
                        val currentTime = System.currentTimeMillis()
                        frameTimeMs = currentTime - frameTimeMillis

                        val t = (currentTime - startTime) / 1000.0
                        val v = velocity + g * t
                        val s = v.pow(2) / (2 * g)
                        velocity = v
                        lastEmulationTime = currentTime
                        val newY = (s.toFloat() + offset.y).roundToInt().toFloat()
                        updateOffset(
                            offset.copy(
                                y = newY
                            )
                        )

                    }
                }
            }
        }

    }

    override fun toString(): String = System.identityHashCode(this).toString()
}

fun haveCollision(rect1: Rect, rect2: Rect): Boolean {
    if (rect1.topLeft.x < rect2.bottomRight.x || rect1.bottomRight.x > rect2.topLeft.x) return false
    if (rect1.topLeft.y < rect2.bottomRight.y || rect1.bottomRight.y > rect2.topLeft.y) return false

    return true
}

data class PhysicsStateSnapshot(
    val v0: Double?,
    val offset: Offset?,
    val elapsedTime: Double?
)

@Composable
fun RenderHeader(onBackPress: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Red)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .clickable(
                        onClick = {
                            onBackPress.invoke()
                        }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_background),
                        contentDescription = stringResource(id = R.string.app_name),
                    )
                    Text(
                        text = stringResource(id = R.string.app_name),
                        color = Color.White,
                    )
                }
            }
            Spacer(modifier = Modifier.fillMaxWidth())
        }
        Box(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        ) {
            Text(
                text = stringResource(id = R.string.app_name).uppercase(),
                color = Color.White,
                fontSize = 17.sp
            )
        }
    }
}

@Preview
@Composable
fun RenderHeader_Preview() {
    RenderHeader {

    }
}
