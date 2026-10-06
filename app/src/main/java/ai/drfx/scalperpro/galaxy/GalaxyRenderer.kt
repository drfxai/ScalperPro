package ai.drfx.scalperpro.galaxy

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GalaxyRenderer(
    private val onNodeSelected: (String) -> Unit
) : GLSurfaceView.Renderer {

    private data class Node(val name: String, val x: Float, val y: Float, val z: Float)

    private val nodes = mutableListOf<Node>()
    private lateinit var nodeBuffer: FloatBuffer
    private lateinit var edgeBuffer: FloatBuffer
    private lateinit var starBuffer: FloatBuffer
    private var edgeVertexCount = 0
    private var starCount = 0

    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uPointSize = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)

    private var width = 1
    private var height = 1

    private var pitch = -10f
    private var yaw = 0f
    private var roll = 0f
    private var autoYaw = 0f
    private var zoom = 1f
    private var userActiveUntil = 0L
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.008f, 0.01f, 0.025f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        uMvp = GLES30.glGetUniformLocation(program, "uMvp")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uPointSize = GLES30.glGetUniformLocation(program, "uPointSize")
        buildScene()
        lastFrameNanos = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projection, 0, 48f, aspect, 0.1f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val dt = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = nowNanos

        if (SystemClock.uptimeMillis() > userActiveUntil) {
            autoYaw -= 2.6f * dt
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(program)

        val cameraZ = 8.2f / zoom
        Matrix.setLookAtM(view, 0, 0f, 0.15f, cameraZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw + autoYaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, roll, 0f, 0f, 1f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glEnableVertexAttribArray(aPosition)

        drawBuffer(starBuffer, starCount, GLES30.GL_POINTS, 1.6f, 0.42f, 0.55f, 1f, 0.34f)
        drawBuffer(edgeBuffer, edgeVertexCount, GLES30.GL_LINES, 1f, 0.34f, 0.42f, 1f, 0.22f)

        val pulse = 7.0f + 1.3f * sin(SystemClock.uptimeMillis() / 650.0).toFloat()
        drawBuffer(nodeBuffer, nodes.size, GLES30.GL_POINTS, pulse, 0.55f, 0.34f, 1f, 0.92f)

        nodeBuffer.position(0)
        GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 3 * 4, nodeBuffer)
        GLES30.glUniform1f(uPointSize, 15f + 2f * sin(SystemClock.uptimeMillis() / 500.0).toFloat())
        GLES30.glUniform4f(uColor, 0.18f, 0.92f, 1f, 1f)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, 1)

        GLES30.glDisableVertexAttribArray(aPosition)
    }

    fun rotateBy(deltaPitch: Float, deltaYaw: Float) {
        pitch = (pitch + deltaPitch).coerceIn(-80f, 80f)
        yaw += deltaYaw
        markInteraction()
    }

    fun twistBy(delta: Float) {
        roll += delta
        markInteraction()
    }

    fun zoomBy(scaleFactor: Float) {
        zoom = (zoom * scaleFactor).coerceIn(0.65f, 2.15f)
        markInteraction()
    }

    fun selectAt(screenX: Float, screenY: Float, viewWidth: Int, viewHeight: Int, focus: Boolean) {
        if (nodes.isEmpty()) return
        var closest = -1
        var best = Float.MAX_VALUE
        val input = FloatArray(4)
        val output = FloatArray(4)
        nodes.forEachIndexed { index, n ->
            input[0] = n.x
            input[1] = n.y
            input[2] = n.z
            input[3] = 1f
            Matrix.multiplyMV(output, 0, mvp, 0, input, 0)
            if (output[3] <= 0f) return@forEachIndexed
            val ndcX = output[0] / output[3]
            val ndcY = output[1] / output[3]
            val sx = (ndcX * 0.5f + 0.5f) * viewWidth
            val sy = (1f - (ndcY * 0.5f + 0.5f)) * viewHeight
            val dx = sx - screenX
            val dy = sy - screenY
            val d = sqrt(dx * dx + dy * dy)
            if (d < best) {
                best = d
                closest = index
            }
        }
        if (closest >= 0 && best < 125f) {
            if (focus) zoom = 1.45f
            markInteraction()
            onNodeSelected(nodes[closest].name)
        }
    }

    private fun markInteraction() {
        userActiveUntil = SystemClock.uptimeMillis() + 3200L
    }

    private fun drawBuffer(
        buffer: FloatBuffer,
        count: Int,
        mode: Int,
        pointSize: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        buffer.position(0)
        GLES30.glVertexAttribPointer(aPosition, 3, GLES30.GL_FLOAT, false, 3 * 4, buffer)
        GLES30.glUniform1f(uPointSize, pointSize)
        GLES30.glUniform4f(uColor, r, g, b, a)
        GLES30.glDrawArrays(mode, 0, count)
    }

    private fun buildScene() {
        nodes.clear()
        nodes += Node("SCALPER AI CORE", 0f, 0f, 0f)

        val clusters = listOf(
            "MARKETS" to listOf("Market Pulse", "Forex", "Gold", "Crypto", "Indices", "Watchlist"),
            "NEWS" to listOf("News Engine", "Economic Calendar", "Macro Intelligence", "Central Banks"),
            "SIGNALS" to listOf("Live Signals", "Performance", "Risk Alerts"),
            "STRATEGIES" to listOf("Strategy Lab", "Strategy Builder", "Pattern Engine"),
            "PINE" to listOf("Pine Studio", "Indicator Forge", "Repaint Detector"),
            "MQL5" to listOf("MQL5 Studio", "EA Builder", "Trade Manager"),
            "BACKTEST" to listOf("Backtest Core", "Walk Forward", "Monte Carlo"),
            "AI" to listOf("AI Assistant", "Chart Vision", "News Intelligence"),
            "LEARNING" to listOf("Traderpedia", "Academy", "Psychology"),
            "USER DATA" to listOf("Journal", "Saved Strategies", "Favorites")
        )

        val edges = mutableListOf<Float>()
        clusters.forEachIndexed { index, pair ->
            val angle = (Math.PI * 2.0 * index / clusters.size).toFloat()
            val radius = 2.45f + 0.2f * sin(index.toFloat())
            val x = cos(angle) * radius
            val y = sin(angle * 1.7f) * 0.82f
            val z = sin(angle) * radius * 0.58f
            val clusterIndex = nodes.size
            nodes += Node(pair.first, x, y, z)
            addEdge(edges, nodes[0], nodes[clusterIndex])

            pair.second.forEachIndexed { childIndex, name ->
                val a = angle + (childIndex - pair.second.size / 2f) * 0.22f
                val childRadius = radius + 0.55f + (childIndex % 2) * 0.16f
                val child = Node(
                    name,
                    cos(a) * childRadius,
                    y + sin(childIndex * 1.4f + index) * 0.42f,
                    sin(a) * childRadius * 0.62f
                )
                nodes += child
                addEdge(edges, nodes[clusterIndex], child)
            }
        }

        val nodeFloats = mutableListOf<Float>()
        nodes.forEach { n -> nodeFloats.addAll(listOf(n.x, n.y, n.z)) }
        nodeBuffer = toBuffer(nodeFloats.toFloatArray())
        edgeVertexCount = edges.size / 3
        edgeBuffer = toBuffer(edges.toFloatArray())

        val random = Random(7381)
        val stars = FloatArray(650 * 3)
        for (i in 0 until 650) {
            val r = 4.2f + random.nextFloat() * 8f
            val a = random.nextFloat() * (Math.PI * 2).toFloat()
            val y = (random.nextFloat() - 0.5f) * 7f
            stars[i * 3] = cos(a) * r
            stars[i * 3 + 1] = y
            stars[i * 3 + 2] = sin(a) * r
        }
        starCount = 650
        starBuffer = toBuffer(stars)
    }

    private fun addEdge(target: MutableList<Float>, a: Node, b: Node) {
        target.addAll(listOf(a.x, a.y, a.z, b.x, b.y, b.z))
    }

    private fun toBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragment)
        return GLES30.glCreateProgram().also { p ->
            GLES30.glAttachShader(p, vs)
            GLES30.glAttachShader(p, fs)
            GLES30.glLinkProgram(p)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
        }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            uniform mat4 uMvp;
            uniform float uPointSize;
            in vec3 aPosition;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
        """
    }
}
