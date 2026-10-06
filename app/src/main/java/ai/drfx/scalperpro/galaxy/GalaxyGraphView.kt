package ai.drfx.scalperpro.galaxy

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.atan2

class GalaxyGraphView(
    context: Context,
    private val onNodeSelected: (String) -> Unit
) : GLSurfaceView(context) {

    private val galaxyRenderer = GalaxyRenderer { name ->
        post { onNodeSelected(name) }
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastTwist = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                queueEvent { galaxyRenderer.zoomBy(factor) }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                queueEvent { galaxyRenderer.selectAt(e.x, e.y, width, height, false) }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                queueEvent { galaxyRenderer.selectAt(e.x, e.y, width, height, true) }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                queueEvent { galaxyRenderer.selectAt(e.x, e.y, width, height, true) }
            }
        }
    )

    init {
        setEGLContextClientVersion(3)
        setRenderer(galaxyRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        setPreserveEGLContextOnPause(true)
    }

    fun setLightTheme(
        enabled: Boolean
    ) {
        queueEvent {
            galaxyRenderer.setLightTheme(
                enabled
            )
        }
    }

    fun setAutoOrbit(
        enabled: Boolean
    ) {
        queueEvent {
            galaxyRenderer.setAutoOrbit(
                enabled
            )
        }
    }

    fun resetCamera() {
        queueEvent {
            galaxyRenderer.resetView()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when {
            event.pointerCount == 1 -> {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        lastY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        lastX = event.x
                        lastY = event.y
                        queueEvent { galaxyRenderer.rotateBy(dy * 0.22f, dx * 0.22f) }
                    }
                }
            }
            event.pointerCount >= 2 -> {
                val dx = event.getX(1) - event.getX(0)
                val dy = event.getY(1) - event.getY(0)
                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    lastTwist = angle
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    var delta = angle - lastTwist
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    lastTwist = angle
                    queueEvent { galaxyRenderer.twistBy(delta * 0.35f) }
                }
            }
        }
        return true
    }
}
