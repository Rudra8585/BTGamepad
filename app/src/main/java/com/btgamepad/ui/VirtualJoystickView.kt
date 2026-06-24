package com.btgamepad.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * VirtualJoystickView
 *
 * A circular on-screen joystick. Reports normalised X/Y in the range -1f…1f
 * via [onMoveListener]. The thumb snaps back to centre on finger lift.
 *
 * Set the tag to "ls" or "rs" to match [RemappingManager] axis keys:
 *   "ls" → axis tags "ls_x" / "ls_y"
 *   "rs" → axis tags "rs_x" / "rs_y"
 */
class VirtualJoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Called whenever the thumb position changes. */
    var onMoveListener: ((axisX: String, axisY: String, x: Float, y: Float) -> Unit)? = null

    // ── Geometry ──────────────────────────────────────────────────────────────
    private var centreX = 0f
    private var centreY = 0f
    private var baseRadius = 0f
    private var thumbRadius = 0f

    private var thumbX = 0f
    private var thumbY = 0f

    // ── Paint ─────────────────────────────────────────────────────────────────
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color  = Color.argb(80, 255, 255, 255)
        style  = Paint.Style.FILL
    }
    private val baseStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 100, 180, 255)
        style = Paint.Style.FILL
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        centreX = w / 2f
        centreY = h / 2f
        baseRadius  = min(w, h) / 2f * 0.88f
        thumbRadius = baseRadius * 0.38f
        thumbX = centreX
        thumbY = centreY
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(centreX, centreY, baseRadius, basePaint)
        canvas.drawCircle(centreX, centreY, baseRadius, baseStrokePaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centreX
                val dy = event.y - centreY
                val dist = hypot(dx, dy)
                val maxDist = baseRadius - thumbRadius

                thumbX = if (dist <= maxDist) centreX + dx else centreX + dx / dist * maxDist
                thumbY = if (dist <= maxDist) centreY + dy else centreY + dy / dist * maxDist

                val normX = ((thumbX - centreX) / maxDist).coerceIn(-1f, 1f)
                val normY = ((thumbY - centreY) / maxDist).coerceIn(-1f, 1f)

                val stickTag = tag?.toString() ?: "ls"
                onMoveListener?.invoke("${stickTag}_x", "${stickTag}_y", normX, normY)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                thumbX = centreX
                thumbY = centreY
                val stickTag = tag?.toString() ?: "ls"
                onMoveListener?.invoke("${stickTag}_x", "${stickTag}_y", 0f, 0f)
                invalidate()
            }
        }
        return true
    }
}
