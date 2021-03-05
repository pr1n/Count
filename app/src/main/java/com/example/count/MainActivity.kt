/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.count

import android.app.Application
import android.content.ContextWrapper
import android.graphics.Point
import android.graphics.PointF
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.count.ui.theme.MyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

private lateinit var INSTANCE: Application

object AppContext : ContextWrapper(INSTANCE)

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyTheme {
                MyApp()
            }
        }
    }
}

val statusHeight by lazy {
    val resources = AppContext.resources
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    resources.getDimensionPixelSize(resourceId)
}

// Start building your app here!
@Composable
fun MyApp() {
    var centerX = 0
    var centerY = 0
    var touchX: Int
    var touchY: Int

    var offsetAngle by rememberSaveable { mutableStateOf(0F) }
    var currentAngle by rememberSaveable { mutableStateOf(0F) }

    var contentAnim by remember { mutableStateOf(0F) }

    var rotation by remember { mutableStateOf(PointF(0F, 0F)) }

    var countDownAnimEnable by remember { mutableStateOf(false) }
    var countDownAnim by remember { mutableStateOf(0F) }
    val countDownTransition = updateTransition(targetState = countDownAnim)
    val countDownAnimation by countDownTransition.animateFloat(
        transitionSpec = {
            tween(durationMillis = 1000, easing = LinearEasing)
        }
    ) { it }

    val dateTime = (contentAnim / 360 * 60 + 0.5).toInt()

    var dateTimeText by remember { mutableStateOf("$dateTime") }
    if (!countDownAnimEnable) dateTimeText = "$dateTime"

    val composableScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                centerX = placeable.width / 2
                centerY = placeable.height / 2
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        val modifier = Modifier
            .size(300.dp)
            .padding(16.dp)
            .align(Alignment.Center)
            .shadow(
                elevation = if (rotation.x == 0F && rotation.y == 0F) 5.dp else 0.dp,
                shape = CircleShape,
                clip = true
            )
            .graphicsLayer {
                rotationX = rotation.x
                rotationY = rotation.y
                scaleX = if (rotation.x == 0F && rotation.y == 0F) 1F else 0.98F
                scaleY = if (rotation.x == 0F && rotation.y == 0F) 1F else 0.98F
                rotationZ = if (countDownAnimation != 0F) countDownAnimation else contentAnim
            }
            .pointerInteropFilter {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchX = it.rawX.toInt()
                        touchY = it.rawY.toInt() - statusHeight
                        val angle = getAngle(Point(touchX, touchY), Point(centerX, centerY))
                        offsetAngle = angle.toFloat()
                        if (!countDownAnimEnable) rotation = getRotation(angle)
                        !countDownAnimEnable
                    }
                    MotionEvent.ACTION_MOVE -> {
                        touchX = it.rawX.toInt()
                        touchY = it.rawY.toInt() - statusHeight
                        val angle = getAngle(Point(touchX, touchY), Point(centerX, centerY))
                        rotation = getRotation(angle)
                        val tmp = currentAngle + angle.toFloat() - offsetAngle
                        contentAnim = if (tmp < 0) 360 + tmp else tmp
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        touchX = it.rawX.toInt()
                        touchY = it.rawY.toInt() - statusHeight
                        val angle = getAngle(Point(touchX, touchY), Point(centerX, centerY))
                        rotation = PointF(0F, 0F)
                        currentAngle = currentAngle + angle.toFloat() - offsetAngle
                        composableScope.launch(Dispatchers.IO) {
                            countDownAnimEnable = true
                            val dataTimeFlow = dataTimeFlow(dateTime)
                            dataTimeFlow.collect { dt ->
                                if (countDownAnimEnable) dateTimeText = "$dt"
                                val tmp = dt / 60F * 360
                                if (dt == 0) {
                                    countDownAnimEnable = false
                                    offsetAngle = 0F
                                    currentAngle = 0F
                                    contentAnim = 0F
                                }
                                countDownAnim = tmp
                            }
                        }
                        false
                    }
                    else -> false
                }
            }

        Content(
            modifier = modifier,
            dateTimeText = dateTimeText,
            textModifer = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
fun Content(modifier: Modifier, dateTimeText: String, textModifer: Modifier) {
    Text(
        text = dateTimeText,
        modifier = textModifer
            .padding()
            .padding(top = 50.dp),
        style = MaterialTheme.typography.h2
    )

    Layout(
        content = {
            Image(
                painter = painterResource(id = R.mipmap.clock),
                modifier = Modifier.fillMaxSize(),
                contentDescription = "block"
            )
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints = constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    )
}

fun getAngle(touchPoint: Point, centerPoint: Point): Double {
    val lenX = (touchPoint.x - centerPoint.x)
    val lenY = (touchPoint.y - centerPoint.y)
    val lenXY = sqrt((lenX * lenX + lenY * lenY).toDouble()).toFloat()
    val radian = acos((lenX / lenXY).toDouble()) * if (touchPoint.y < centerPoint.y) -1 else 1
    val tmp = (radian / Math.PI * 180).roundToInt().toDouble()
    return ((if (tmp >= 0) tmp else 360 + tmp) + 90) % 360
}

fun getRotation(angle: Double, offset: Float = 10F): PointF {
    return when (val temp = angle % 360) {
        in 0.0..90.0 -> {
            val tempAngle = (temp - 0) / 90
            PointF((offset - tempAngle * offset).toFloat(), (tempAngle * offset).toFloat())
        }
        in 90.0..180.0 -> {
            val tempAngle = (temp - 90) / 90
            PointF(-(tempAngle * offset).toFloat(), (offset - tempAngle * offset).toFloat())
        }
        in 180.0..270.0 -> {
            val tempAngle = (temp - 180) / 90
            PointF(-(offset - tempAngle * offset).toFloat(), -(tempAngle * offset).toFloat())
        }
        in 270.0..360.0 -> {
            val tempAngle = (temp - 270) / 90
            PointF((tempAngle * offset).toFloat(), -(offset - tempAngle * offset).toFloat())
        }
        else -> PointF(0F, 0F)
    }
}

suspend fun dataTimeFlow(dateTime: Int) =
    flow {
        for (i in dateTime downTo 0) {
            if (dateTime > i) delay(1000)
            emit(i)
        }
    }

@Preview("Light Theme", widthDp = 360, heightDp = 640)
@Composable
fun LightPreview() {
    MyTheme {
        MyApp()
    }
}

@Preview("Dark Theme", widthDp = 360, heightDp = 640)
@Composable
fun DarkPreview() {
    MyTheme(darkTheme = true) {
        MyApp()
    }
}
