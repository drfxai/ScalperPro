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

    private data class Node(
        val name: String,
        val x: Float,
        val y: Float,
        val z: Float,
        val tier: Int
    )

    private data class Cluster(
        val name: String,
        val children: List<String>,
        val radius: Float,
        val verticalScale: Float
    )

    private val nodes = mutableListOf<Node>()

    private lateinit var coreBuffer: FloatBuffer
    private lateinit var primaryNodeBuffer: FloatBuffer
    private lateinit var secondaryNodeBuffer: FloatBuffer
    private lateinit var edgeBuffer: FloatBuffer
    private lateinit var starBuffer: FloatBuffer

    private var primaryNodeCount = 0
    private var secondaryNodeCount = 0
    private var edgeVertexCount = 0
    private var starCount = 0

    private var program = 0
    private var aPosition = 0
    private var uMvp = 0
    private var uColor = 0
    private var uPointSize = 0
    private var uPointMode = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)

    private var pitch = -10f
    private var yaw = 0f
    private var roll = 0f
    private var autoYaw = 0f
    private var zoom = 1f
    private var userActiveUntil = 0L
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?
    ) {
        GLES30.glClearColor(0.004f, 0.006f, 0.018f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES30.glGetAttribLocation(program, "aPosition")
        uMvp = GLES30.glGetUniformLocation(program, "uMvp")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uPointSize = GLES30.glGetUniformLocation(program, "uPointSize")
        uPointMode = GLES30.glGetUniformLocation(program, "uPointMode")

        buildScene()
        lastFrameNanos = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, safeWidth, safeHeight)
        Matrix.perspectiveM(
            projection,
            0,
            47f,
            safeWidth.toFloat() / safeHeight.toFloat(),
            0.1f,
            70f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val dt = (
            (nowNanos - lastFrameNanos) /
                1_000_000_000.0
            ).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = nowNanos

        if (SystemClock.uptimeMillis() > userActiveUntil) {
            autoYaw -= 2.35f * dt
        }

        GLES30.glClear(
            GLES30.GL_COLOR_BUFFER_BIT or
                GLES30.GL_DEPTH_BUFFER_BIT
        )
        GLES30.glUseProgram(program)

        val cameraZ = 8.4f / zoom
        Matrix.setLookAtM(
            view,
            0,
            0f,
            0.1f,
            cameraZ,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw + autoYaw, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, roll, 0f, 0f, 1f)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)

        GLES30.glUniformMatrix4fv(
            uMvp,
            1,
            false,
            mvp,
            0
        )
        GLES30.glEnableVertexAttribArray(aPosition)

        val time = SystemClock.uptimeMillis().toFloat()
        val slowPulse = sin(time / 760f)
        val fastPulse = sin(time / 520f)

        drawBuffer(
            buffer = starBuffer,
            count = starCount,
            mode = GLES30.GL_POINTS,
            pointSize = 1.45f + slowPulse * 0.18f,
            r = 0.45f,
            g = 0.62f,
            b = 1f,
            a = 0.33f,
            pointMode = true
        )

        drawBuffer(
            buffer = edgeBuffer,
            count = edgeVertexCount,
            mode = GLES30.GL_LINES,
            pointSize = 1f,
            r = 0.34f,
            g = 0.38f,
            b = 1f,
            a = 0.19f + slowPulse * 0.025f,
            pointMode = false
        )

        drawBuffer(
            buffer = secondaryNodeBuffer,
            count = secondaryNodeCount,
            mode = GLES30.GL_POINTS,
            pointSize = 7.0f + slowPulse * 0.8f,
            r = 0.57f,
            g = 0.34f,
            b = 1f,
            a = 0.78f,
            pointMode = true
        )

        drawBuffer(
            buffer = primaryNodeBuffer,
            count = primaryNodeCount,
            mode = GLES30.GL_POINTS,
            pointSize = 11.2f + fastPulse * 1.35f,
            r = 0.18f,
            g = 0.88f,
            b = 1f,
            a = 0.96f,
            pointMode = true
        )

        drawBuffer(
            buffer = coreBuffer,
            count = 1,
            mode = GLES30.GL_POINTS,
            pointSize = 20f + fastPulse * 2.4f,
            r = 0.96f,
            g = 0.74f,
            b = 0.28f,
            a = 1f,
            pointMode = true
        )

        GLES30.glDisableVertexAttribArray(aPosition)
    }

    fun rotateBy(
        deltaPitch: Float,
        deltaYaw: Float
    ) {
        pitch = (pitch + deltaPitch).coerceIn(-80f, 80f)
        yaw += deltaYaw
        markInteraction()
    }

    fun twistBy(delta: Float) {
        roll += delta
        markInteraction()
    }

    fun zoomBy(scaleFactor: Float) {
        zoom = (zoom * scaleFactor).coerceIn(0.62f, 2.25f)
        markInteraction()
    }

    fun selectAt(
        screenX: Float,
        screenY: Float,
        viewWidth: Int,
        viewHeight: Int,
        focus: Boolean
    ) {
        if (nodes.isEmpty()) return

        var closest = -1
        var best = Float.MAX_VALUE
        val input = FloatArray(4)
        val output = FloatArray(4)

        nodes.forEachIndexed { index, node ->
            input[0] = node.x
            input[1] = node.y
            input[2] = node.z
            input[3] = 1f

            Matrix.multiplyMV(
                output,
                0,
                mvp,
                0,
                input,
                0
            )

            if (output[3] <= 0f) return@forEachIndexed

            val ndcX = output[0] / output[3]
            val ndcY = output[1] / output[3]
            val sx = (ndcX * 0.5f + 0.5f) * viewWidth
            val sy =
                (1f - (ndcY * 0.5f + 0.5f)) *
                    viewHeight

            val dx = sx - screenX
            val dy = sy - screenY
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < best) {
                best = distance
                closest = index
            }
        }

        if (closest >= 0 && best < 128f) {
            if (focus) {
                zoom = 1.58f
            }
            markInteraction()
            onNodeSelected(nodes[closest].name)
        }
    }

    private fun markInteraction() {
        userActiveUntil =
            SystemClock.uptimeMillis() + 3400L
    }

    private fun drawBuffer(
        buffer: FloatBuffer,
        count: Int,
        mode: Int,
        pointSize: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        pointMode: Boolean
    ) {
        buffer.position(0)
        GLES30.glVertexAttribPointer(
            aPosition,
            3,
            GLES30.GL_FLOAT,
            false,
            3 * 4,
            buffer
        )
        GLES30.glUniform1f(uPointSize, pointSize)
        GLES30.glUniform1f(
            uPointMode,
            if (pointMode) 1f else 0f
        )
        GLES30.glUniform4f(uColor, r, g, b, a)
        GLES30.glDrawArrays(mode, 0, count)
    }

    private fun buildScene() {
        nodes.clear()
        nodes += Node(
            name = "SCALPER AI CORE",
            x = 0f,
            y = 0f,
            z = 0f,
            tier = 0
        )

        val clusters = listOf(
            Cluster(
                name = "AI LAB",
                children = listOf(
                    "AI Supervisor",
                    "Requirements Analyst",
                    "Indicator Architect",
                    "Strategy Strategist",
                    "TradingView QA",
                    "Beginner Coach"
                ),
                radius = 1.72f,
                verticalScale = 0.62f
            ),
            Cluster(
                name = "PINE",
                children = listOf(
                    "Indicator Forge",
                    "Strategy Forge",
                    "Pine Studio",
                    "Repaint Lab",
                    "HTF Security Lab",
                    "Alert Builder"
                ),
                radius = 1.92f,
                verticalScale = 0.72f
            ),
            Cluster(
                name = "QUANT LAB",
                children = listOf(
                    "Quant Runtime",
                    "Chart Sandbox",
                    "Backtest Core",
                    "Diagnostics",
                    "Saved Tools"
                ),
                radius = 2.1f,
                verticalScale = 0.78f
            ),
            Cluster(
                name = "MQL5",
                children = listOf(
                    "MQL5 Translator",
                    "MetaTrader EA",
                    "Compile QA",
                    "Trade Manager"
                ),
                radius = 2.42f,
                verticalScale = 0.82f
            ),
            Cluster(
                name = "NEWS",
                children = listOf(
                    "News Intelligence",
                    "Economic Calendar",
                    "Macro Analysis"
                ),
                radius = 3.05f,
                verticalScale = 0.88f
            ),
            Cluster(
                name = "MARKETS",
                children = listOf(
                    "Market Pulse",
                    "Forex",
                    "Gold",
                    "Crypto"
                ),
                radius = 3.25f,
                verticalScale = 0.92f
            ),
            Cluster(
                name = "LEARNING",
                children = listOf(
                    "Traderpedia",
                    "Academy",
                    "Pine Lessons",
                    "Risk Basics"
                ),
                radius = 3.45f,
                verticalScale = 0.96f
            )
        )

        val edges = mutableListOf<Float>()

        clusters.forEachIndexed { index, cluster ->
            val angle =
                (Math.PI * 2.0 * index / clusters.size)
                    .toFloat()

            val x = cos(angle) * cluster.radius
            val y =
                sin(angle * 1.55f) *
                    cluster.verticalScale
            val z =
                sin(angle) *
                    cluster.radius *
                    0.57f

            val clusterNode = Node(
                name = cluster.name,
                x = x,
                y = y,
                z = z,
                tier = 1
            )
            nodes += clusterNode
            addEdge(edges, nodes.first(), clusterNode)

            cluster.children.forEachIndexed {
                    childIndex,
                    childName ->
                val childAngle =
                    angle +
                        (childIndex -
                            cluster.children.size / 2f) *
                        0.19f

                val childRadius =
                    cluster.radius +
                        0.52f +
                        (childIndex % 2) * 0.17f

                val child = Node(
                    name = childName,
                    x = cos(childAngle) * childRadius,
                    y = y +
                        sin(
                            childIndex * 1.36f +
                                index
                        ) * 0.4f,
                    z = sin(childAngle) *
                        childRadius *
                        0.61f,
                    tier = if (
                        childName.contains("AI") ||
                        childName.contains("Pine") ||
                        childName.contains("Indicator") ||
                        childName.contains("Strategy") ||
                        childName.contains("Quant") ||
                        childName.contains("Chart")
                    ) {
                        1
                    } else {
                        2
                    }
                )

                nodes += child
                addEdge(edges, clusterNode, child)

                if (
                    cluster.name == "AI LAB" ||
                    cluster.name == "PINE"
                ) {
                    addEdge(
                        edges,
                        nodes.first(),
                        child
                    )
                }
            }
        }

        val primary = mutableListOf<Float>()
        val secondary = mutableListOf<Float>()

        nodes.drop(1).forEach { node ->
            val target =
                if (node.tier == 1) primary
                else secondary

            target += node.x
            target += node.y
            target += node.z
        }

        coreBuffer = toBuffer(
            floatArrayOf(0f, 0f, 0f)
        )
        primaryNodeCount = primary.size / 3
        primaryNodeBuffer = toBuffer(
            primary.toFloatArray()
        )
        secondaryNodeCount = secondary.size / 3
        secondaryNodeBuffer = toBuffer(
            secondary.toFloatArray()
        )

        edgeVertexCount = edges.size / 3
        edgeBuffer = toBuffer(edges.toFloatArray())

        val random = Random(7381)
        val stars = FloatArray(STAR_COUNT * 3)

        for (index in 0 until STAR_COUNT) {
            val armBias =
                1f +
                    0.2f *
                    sin(index * 0.41f)

            val radius =
                (3.8f +
                    random.nextFloat() * 9.5f) *
                    armBias

            val angle =
                random.nextFloat() *
                    (Math.PI * 2).toFloat() +
                    radius * 0.07f

            val vertical =
                (random.nextFloat() - 0.5f) *
                    (2.0f + radius * 0.33f)

            stars[index * 3] =
                cos(angle) * radius
            stars[index * 3 + 1] = vertical
            stars[index * 3 + 2] =
                sin(angle) * radius
        }

        starCount = STAR_COUNT
        starBuffer = toBuffer(stars)
    }

    private fun addEdge(
        target: MutableList<Float>,
        first: Node,
        second: Node
    ) {
        target += first.x
        target += first.y
        target += first.z
        target += second.x
        target += second.y
        target += second.z
    }

    private fun toBuffer(
        values: FloatArray
    ): FloatBuffer =
        ByteBuffer
            .allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun buildProgram(
        vertex: String,
        fragment: String
    ): Int {
        val vertexShader =
            compileShader(
                GLES30.GL_VERTEX_SHADER,
                vertex
            )
        val fragmentShader =
            compileShader(
                GLES30.GL_FRAGMENT_SHADER,
                fragment
            )

        return GLES30.glCreateProgram().also {
                program ->
            GLES30.glAttachShader(
                program,
                vertexShader
            )
            GLES30.glAttachShader(
                program,
                fragmentShader
            )
            GLES30.glLinkProgram(program)
        }
    }

    private fun compileShader(
        type: Int,
        source: String
    ): Int =
        GLES30.glCreateShader(type).also {
                shader ->
            GLES30.glShaderSource(
                shader,
                source
            )
            GLES30.glCompileShader(shader)
        }

    companion object {
        private const val STAR_COUNT = 900

        private const val VERTEX_SHADER = """
            #version 300 es
            uniform mat4 uMvp;
            uniform float uPointSize;
            in vec3 aPosition;

            void main() {
                gl_Position =
                    uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;

            uniform vec4 uColor;
            uniform float uPointMode;
            out vec4 fragColor;

            void main() {
                if (uPointMode > 0.5) {
                    vec2 p =
                        gl_PointCoord -
                        vec2(0.5);
                    float d = length(p);

                    if (d > 0.5) {
                        discard;
                    }

                    float halo =
                        1.0 -
                        smoothstep(
                            0.08,
                            0.5,
                            d
                        );

                    fragColor =
                        vec4(
                            uColor.rgb,
                            uColor.a * halo
                        );
                } else {
                    fragColor = uColor;
                }
            }
        """
    }
}
