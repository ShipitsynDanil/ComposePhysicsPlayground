package com.example.composeplayground

import android.graphics.PointF
import android.graphics.RuntimeShader
import android.os.Build
import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.example.composeplayground.ui.theme.ComposePlaygroundTheme
import com.example.composeplayground.ui.theme.White
import com.example.composeplayground.ui.theme.Yellow
import com.example.composeplayground.ui.theme.YellowVariant
import org.intellij.lang.annotations.Language
import kotlin.math.pow

class MainScreenViewModel : ViewModel() {

}

@Stable
class WaterState {
    var progress by mutableStateOf(0.0f)
        private set


}

val paint = Paint().apply {
    color = Color.Blue
    pathEffect = PathEffect.cornerPathEffect(100f)
}

@Stable
data class PointsData(
    val points: List<PointF>,
    val xRange: IntRange,
    val yRange: IntRange
)

fun generatePath(
    points: PointsData
): Path {
    val path = Path()
    val sorted = points.points
        .sortedWith(
            comparator = { o1, o2 ->
                val condition = (o1.x < o2.x) || ((o1.x == o2.x) && (o1.y < o2.y))
                when {
                    condition -> -1
                    o1.x == o2.x && o1.y == o2.y -> 0
                    else -> 1
                }
            }
        )

    path.moveTo(sorted.first().x, sorted.first().y)
    sorted.forEachIndexed { index, point ->
        val prevPoint = sorted[(index - 1).coerceAtLeast(0)]
        val controlPoint1X = prevPoint.x + (point.x - prevPoint.x) / 2

        val controlPoint1Y = prevPoint.y

        val controlPoint2X = prevPoint.x + (point.x - prevPoint.x) / 2

        val controlPoint2Y = point.y

        path.cubicTo(
            controlPoint1X, controlPoint1Y,
            controlPoint2X, controlPoint2Y,
            point.x, point.y
        )
        //path.lineTo(point.x, point.y)
    }
    return path
}

fun generatePointsData(
    pointsCount: Int,
    xRange: IntRange,
    yRange: IntRange
): PointsData {
    val generatedPoints = mutableListOf<PointF>()
    for (i in 1..pointsCount) {
        val x = xRange.random()
        val y = yRange.random()
        generatedPoints.add(PointF(x.toFloat(), y.toFloat()))
    }
    return PointsData(generatedPoints, xRange, yRange)
}

@Composable
fun rememberGeneratedPoints(
    pointsCount: Int,
    xRange: IntRange,
    yRange: IntRange
): PointsData = remember {
    generatePointsData(pointsCount, xRange, yRange)
}

fun Modifier.drawWaves() = drawBehind {

}

fun Modifier.yellowBackground(): Modifier = this.composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // produce updating time in seconds variable to pass into shader
        val time by produceState(0f) {
            while (true) {
                withInfiniteAnimationFrameMillis {
                    value = it / 1000f
                }
            }
        }
        Modifier.drawWithCache {
            val shader = RuntimeShader(SHADER)
            val shaderBrush = ShaderBrush(shader)
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            // Pass the color to support color space automatically
            shader.setColorUniform(
                "iColor",
                android.graphics.Color.valueOf(Yellow.red, Yellow.green, Yellow.blue, Yellow.alpha)
            )
            onDrawBehind {
                drawRect(shaderBrush)
            }
        }
    } else {
        Modifier.drawWithCache {
            val gradientBrush = Brush.verticalGradient(listOf(Yellow, YellowVariant, White))
            onDrawBehind {
                drawRect(gradientBrush)
            }
        }
    }
}

@Language("AGSL")
val SHADER = """
    uniform float2 iResolution;
    uniform float iTime;
    layout(color) uniform half4 iColor;
    
    float calculateColorMultiplier(float yCoord, float factor) {
        return step(yCoord, 1.0 + factor * 2.0) - step(yCoord, factor - 0.1);
    }

    float4 main(in float2 fragCoord) {
        // Config values
        const float speedMultiplier = 1.5;
        const float waveDensity = 1.0;
        const float loops = 8.0;
        const float energy = 0.6;
        
        // Calculated values
        float2 uv = fragCoord / iResolution.xy;
        float3 color = iColor.rgb;
        float timeOffset = iTime * speedMultiplier;
        float hAdjustment = uv.x * 4.3;
        float3 loopColor = vec3(1.0 - color.r, 1.0 - color.g, 1.0 - color.b) / loops;
        
        for (float i = 1.0; i <= loops; i += 1.0) {
            float loopFactor = i * 0.1;
            float sinInput = (timeOffset + hAdjustment) * energy;
            float curve = sin(sinInput) * (1.0 - loopFactor) * 0.05;
            float colorMultiplier = calculateColorMultiplier(uv.y, loopFactor);
            color += loopColor * colorMultiplier;
            
            // Offset for next loop
            uv.y += curve;
        }
        
        return float4(color, 1.0);
    }
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val waterLevelDp by remember {
        derivedStateOf {

        }
    }
    val configuration = LocalConfiguration.current
    Scaffold(
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .height(300.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .yellowBackground()
                    .drawWithCache {
                        /*
                        onDrawBehind {
                            val xRange = ((size.width.roundToInt() * 0.1).toInt()..size.width.roundToInt())
                            val yRange = (size.height.roundToInt() / 2..size.height.roundToInt())
                            val path = generatePath(generatePointsData(15, xRange, yRange))
                            drawPath(
                                path,
                                Color.Red,
                                style = Stroke(
                                    2.dp.toPx(),
                                )
                            )
                        }

                         */
                        onDrawBehind { }
                    }
                    .matchParentSize()
            )
        }
    }
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TransformSquareToCircleAndScaleExample() {
    val state = rememberInfiniteTransition()
    val rotY by state.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(1000, 1000),
            repeatMode = RepeatMode.Restart
        )
    )
    var pressed by remember {
        mutableStateOf(false)
    }
    val cornerDp by animateDpAsState(
        targetValue = if (pressed) 100.dp else 0.dp
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.5f else 1f
    )
    Column(
        modifier = Modifier
            //.background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInteropFilter { motionEvent ->
                    pressed = when (motionEvent.action) {
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_DOWN -> {
                            true
                        }

                        else -> {
                            false
                        }
                    }
                    true
                }
                .clip(RoundedCornerShape(cornerDp))
                .size(150.dp),
            color = Color.Red,
            content = {

            }
        )
    }
}

@Preview
@Composable
fun MainScreen_Preview() {
    ComposePlaygroundTheme {
        MainScreen()
    }
}
