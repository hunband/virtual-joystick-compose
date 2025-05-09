package com.hunband.virtualjoystickcompose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.smarttoolfactory.gesture.pointerMotionEvents
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.round

// ========================
// Constants
// ========================

/**
 * Default color for button
 */
private val DEFAULT_COLOR_BUTTON = Color.Black

/**
 * Default color for border
 */
private val DEFAULT_COLOR_BORDER = Color.Transparent

/**
 * Default background color
 */
private val DEFAULT_BACKGROUND_COLOR = Color.Transparent

/**
 * Default border's width
 */
private const val DEFAULT_WIDTH_BORDER = 3f

/**
 * Default behavior to fixed center (not auto-defined)
 */
private const val DEFAULT_FIXED_CENTER = true

/**
 * Default behavior to auto re-center button (automatically recenter the button)
 */
private const val DEFAULT_AUTO_RECENTER_BUTTON = true

/**
 * Default behavior to button stickToBorder (button stay on the border)
 */
private const val DEFAULT_BUTTON_STICK_TO_BORDER = false

/**
 * Default allowed direction of the button.
 * Both direction correspond to horizontal and vertical movement.
 */
private val DEFAULT_BUTTON_DIRECTION = JoystickDirection.BOTH

enum class JoystickDirection { BOTH, HORIZONTAL, VERTICAL }

data class JoystickState(
    val angle: Float,
    val strength: Float,
    val normalizedX: Float,
    val normalizedY: Float,
)

// ========================
// Composable
// ========================

@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    buttonColor: Color = DEFAULT_COLOR_BUTTON,
    borderColor: Color = DEFAULT_COLOR_BORDER,
    backgroundColor: Color = DEFAULT_BACKGROUND_COLOR,
    borderWidth: Float = DEFAULT_WIDTH_BORDER,
//    isFixedCenter: Boolean = DEFAULT_FIXED_CENTER,
    isAutoReCenterButton: Boolean = DEFAULT_AUTO_RECENTER_BUTTON,
    isButtonStickToBorder: Boolean = DEFAULT_BUTTON_STICK_TO_BORDER,
    isEnabled: Boolean = true,
    buttonSizeRatio: Float = 0.25f,
    backgroundSizeRatio: Float = 0.75f,
    buttonDirection: JoystickDirection = DEFAULT_BUTTON_DIRECTION,
    onMove: (JoystickState) -> Unit,
    onRelease: (JoystickState) -> Unit,
) {
    val normalizedButtonSizeRatio = buttonSizeRatio.coerceIn(0f, 1f)
    val normalizedBackgroundSizeRatio = backgroundSizeRatio.coerceIn(0f, 1f)

    var buttonRadius = 0f
    var borderRadius = 0f
    var backgroundRadius = 0f
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // COORDINATE
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }

    fun initPosition() {
        // get the center of view to position circle
        val positionX = canvasSize.width / 2
        val positionY = canvasSize.height / 2

        buttonPosition = Offset(positionX.toFloat(), positionY.toFloat())
        center = Offset(positionX.toFloat(), positionY.toFloat())
    }

    /**
     * Reset the button position to the center.
     */
    fun resetButtonPosition() {
        buttonPosition = center
    }

    fun distance(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    /**
     * Projects the given [point] onto the edge of the joystick circle,
     * preserving the direction from [center] to [point].
     *
     * The circle's edge is defined by [borderRadius], which limits how far
     * the joystick knob can move from the center.
     *
     * If the [point] is exactly at the center, returns the [center] itself.
     *
     * @param point The touch or input position to project.
     * @return An [Offset] on the edge of the joystick circle, in the direction of [point].
     */
    fun clampToJoystickCircle(point: Offset): Offset {
        val dx = point.x - center.x
        val dy = point.y - center.y
        val length = hypot(dx, dy)

        return Offset(
            dx * borderRadius / length + center.x,
            dy * borderRadius / length + center.y,
        )
    }

    /**
     * Adjusts the knob position based on the input [position] and allowed movement direction.
     *
     * If the movement is restricted to one axis (horizontal or vertical), the corresponding
     * coordinate is fixed to the center. Otherwise, the input position is used as-is.
     *
     * If the adjusted position exceeds the joystick's border (defined by [borderRadius]),
     * the position is clamped to stay within the circular area.
     *
     * @param position The raw touch/input position.
     * @return The adjusted [Offset] within the allowed joystick area.
     */
    fun limitKnobWithinBounds(position: Offset): Offset {
        // to move the button according to the finger coordinate
        // or limited to one axis according to direction option
        val posY = if (buttonDirection == JoystickDirection.HORIZONTAL) center.y else position.y
        val posX = if (buttonDirection == JoystickDirection.VERTICAL) center.x else position.x

        val adjustedPosition = Offset(posX, posY)

        val distanceToCenter = distance(center, adjustedPosition)

        // (distanceToCenter > borderRadius) means button is too far therefore we limit to border
        // (isButtonStickToBorder && distanceToCenter != 0) means wherever is the button we stick it to the border except when distanceToCenter == 0
        return if (distanceToCenter > borderRadius || isButtonStickToBorder && distanceToCenter != 0f) {
            clampToJoystickCircle(adjustedPosition)
        } else {
            adjustedPosition
        }
    }

    /**
     * Process the angle following the 360° counter-clock protractor rules.
     * @return the angle of the button
     */
    fun getAngle(): Float {
        val dx = buttonPosition.x - center.x
        val dy = center.y - buttonPosition.y
        val radians = atan2(dy, dx)
        val degrees = Math.toDegrees(radians.toDouble()).toFloat()
        return if (degrees < 0) {
            degrees + 360
        } else {
            degrees
        }
    }

    /**
     * Process the strength as a percentage of the distance between the center and the border.
     * @return the strength of the button
     */
    fun getStrength(): Float {
        val dx = buttonPosition.x - center.x
        val dy = buttonPosition.y - center.y

        val distance = hypot(dx, dy)

        return 100 * (distance / borderRadius).coerceIn(-1f, 1f)
    }

    /**
     * Return the relative X coordinate of button center related
     * to top-left virtual corner of the border
     * @return coordinate of X (normalized between 0 and 100)
     */
    fun getNormalizedX(): Float {
        if (borderRadius == 0f) return 50f
        val dx = buttonPosition.x - center.x
        return ((dx + borderRadius) / (2 * borderRadius) * 100f).coerceIn(0f, 100f)
    }

    /**
     * Return the relative Y coordinate of the button center related
     * to top-left virtual corner of the border
     * @return coordinate of Y (normalized between 0 and 100)
     */
    fun getNormalizedY(): Float {
        if (borderRadius == 0f) return 50f

        return round((canvasSize.height - buttonPosition.y - buttonRadius) * 100.0f / (canvasSize.height - buttonRadius * 2))
    }

    fun createJoystickScope() = JoystickState(
        angle = getAngle(),
        strength = getStrength(),
        normalizedX = getNormalizedX(),
        normalizedY = getNormalizedY(),
    )

    fun handleInput(position: Offset) {
        buttonPosition = limitKnobWithinBounds(position)
        onMove(createJoystickScope())
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { size ->
                canvasSize = size
                initPosition()

                // radius based on smallest size : height OR width
                val diameter = min(size.width, size.height)
                buttonRadius = diameter / 2 * normalizedButtonSizeRatio
                borderRadius = diameter / 2 * normalizedBackgroundSizeRatio
                backgroundRadius = borderRadius - (borderWidth / 2f)
            }
            .pointerMotionEvents(
                onDown = { if (isEnabled) handleInput(it.position) },
                onMove = { if (isEnabled) handleInput(it.position) },
                onUp = {
                    if (isEnabled) {
                        if (isAutoReCenterButton) resetButtonPosition()
                        onRelease(createJoystickScope())
                    }
                }
            )
    ) {
        // Draw the background
        drawCircle(
            center = center,
            radius = backgroundRadius,
            color = backgroundColor,
        )

        // Draw the circle border
        drawCircle(
            center = center,
            radius = borderRadius,
            color = borderColor,
            style = Stroke(borderWidth),
        )

        // TODO: Implement drawing the button from the image later

        // Draw the button as simple circle
        drawCircle(
            center = buttonPosition,
            color = buttonColor,
            radius = buttonRadius,
        )
    }
}
