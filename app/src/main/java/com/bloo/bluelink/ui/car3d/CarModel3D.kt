package com.bloo.bluelink.ui.car3d

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp

/** Which effects the 3D car shows: heating or cooling air, as the car's climate runs. */
enum class ClimateFx { HEAT, COOL }

/** What the 3D car should show. */
data class CarPose(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val frunk: Boolean = false,
    val trunk: Boolean = false,
    val pluggedIn: Boolean = false,
    val charging: Boolean = false,
    val climate: ClimateFx? = null,
    val defrost: Boolean = false,
)

/**
 * Hinged parts of the rigged model (tools/model/rig.py). Each node's origin sits on its hinge,
 * so opening is a rotation about the node's own axis. glTF axes: +X = car's left (driver side),
 * +Y = up, +Z = front.
 */
private enum class Part(val node: String, val axis: FloatArray, val openDegrees: Float) {
    DOOR_FL("door_fl", floatArrayOf(0f, 1f, 0f), -45f),
    DOOR_RL("door_rl", floatArrayOf(0f, 1f, 0f), -45f),
    DOOR_FR("door_fr", floatArrayOf(0f, 1f, 0f), 45f),
    DOOR_RR("door_rr", floatArrayOf(0f, 1f, 0f), 45f),
    HOOD("hood", floatArrayOf(1f, 0f, 0f), -38f),
    TAILGATE("tailgate", floatArrayOf(1f, 0f, 0f), 60f);

    fun isOpen(p: CarPose) = when (this) {
        DOOR_FL -> p.frontLeft
        DOOR_RL -> p.rearLeft
        DOOR_FR -> p.frontRight
        DOOR_RR -> p.rearRight
        HOOD -> p.frunk
        TAILGATE -> p.trunk
    }
}

/**
 * Renders the car with Filament into a TextureView: an orbit camera the user can drag (and that
 * swings round to whatever just changed), hinged parts easing open and shut, and the charging
 * cable sliding into the port. Based on Filament's ModelViewer, with our own camera so the
 * effects overlay can project points exactly as the renderer does.
 */
private class CarRenderer(
    context: Context,
    private val textureView: TextureView,
    body: CarBody,
    private val shape: CarShape,
) : Choreographer.FrameCallback {
    val camera = CarCamera(shape)

    /** How far the charging cable is pushed in, 0 (away) to 1 (seated). Read by the overlay. */
    var cablePlugged = 0f
        private set

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val filamentCamera = engine.createCamera(EntityManager.get().create())
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null
    private val materials = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materials, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine, true)
    private val asset: FilamentAsset
    private val sun = EntityManager.get().create()
    private var indirectLight: IndirectLight? = null
    private var reflections: Texture? = null
    private val readyRenderables = IntArray(128)

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var destroyed = false
    private var lastFrame = 0L

    private val transforms = mutableMapOf<Part, Pair<Int, FloatArray>>() // transform instance, rest pose
    private val current = FloatArray(Part.entries.size) // 0 = closed, 1 = open
    private var cable: Triple<Int, Int, FloatArray>? = null // transform instance, renderable instance, rest pose
    private var cableShown = false
    private var shadowEntity = 0

    // Camera motion: the user's drag and fling, and automatic swings toward what changed.
    private var spin = 0.0 // degrees per second, from a fling
    private var touching = false
    private var focusAzimuth: Double? = null
    private var focusElevation: Double? = null
    private var hasPose = false

    var pose = CarPose()
        set(next) {
            val prev = field
            field = next
            if (!hasPose) {
                hasPose = true
                if (next.pluggedIn) jump(CarCamera.PORT_AZIMUTH)
                return
            }
            when {
                next.pluggedIn && !prev.pluggedIn || next.charging && !prev.charging -> focus(CarCamera.PORT_AZIMUTH)
                next.frontRight && !prev.frontRight || next.rearRight && !prev.rearRight -> focus(-45.0)
                next.frontLeft && !prev.frontLeft || next.rearLeft && !prev.rearLeft -> focus(45.0)
                next.trunk && !prev.trunk -> focus(150.0, 16.0)
                next.frunk && !prev.frunk -> focus(25.0, 20.0)
                next.climate != null && prev.climate == null -> focus(CarCamera.HOME_AZIMUTH)
            }
        }

    init {
        uiHelper.isOpaque = false
        textureView.isOpaque = false
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(textureView)

        view.scene = scene
        view.camera = filamentCamera
        filamentCamera.setExposure(16f, 1f / 125f, 100f)
        // Transparent background so the app shows through.
        view.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = renderer.clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
            sampleCount = 4
        }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = true }

        // Image-based lighting from a studio environment (assets/envs/studio.hdr, generated by
        // tools/env). The car's metallic materials need something to reflect or they render black.
        indirectLight = studioLight(context) ?: IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.95f, 0.96f, 1.0f))
            .intensity(32_000f)
            .build(engine)
        scene.indirectLight = indirectLight
        val (r, g, b) = Colors.cct(6_500f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(60_000f)
            .direction(0.45f, -1f, -0.55f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        val bytes = context.assets.open(body.glb).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.rewind() }
        asset = assetLoader.createAsset(buffer) ?: error("Couldn't read the car model")
        resourceLoader.asyncBeginLoad(asset)
        asset.releaseSourceData()

        val tm = engine.transformManager
        val rm = engine.renderableManager
        for (part in Part.entries) {
            val entity = asset.getFirstEntityByName(part.node)
            if (entity == 0) continue
            val inst = tm.getInstance(entity)
            transforms[part] = inst to FloatArray(16).also { tm.getTransform(inst, it) }
        }
        asset.getFirstEntityByName("charge_cable").takeIf { it != 0 }?.let { e ->
            val inst = tm.getInstance(e)
            val ri = rm.getInstance(e)
            if (ri != 0) {
                rm.setLayerMask(ri, 0xFF, 0x00) // hidden until plugged in
                cable = Triple(inst, ri, FloatArray(16).also { tm.getTransform(inst, it) })
            }
        }
        shadowEntity = asset.getFirstEntityByName("ground_shadow")

        setUpTouch(context)
        textureView.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = start()
            override fun onViewDetachedFromWindow(v: android.view.View) = destroy()
        })
        if (textureView.isAttachedToWindow) start()
    }

    /** The body colour (the models' "paint" material). */
    var paint: CarPaint = CarPaint.WHITE
        set(p) {
            field = p
            for (mi in asset.instance.materialInstances) {
                if (mi.name == "paint") runCatching { mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, p.rgb[0], p.rgb[1], p.rgb[2], 1f) }
            }
        }

    private fun focus(azimuth: Double, elevation: Double = CarCamera.HOME_ELEVATION) {
        if (touching) return
        spin = 0.0
        focusAzimuth = azimuth
        focusElevation = elevation
    }

    private fun jump(azimuth: Double) {
        camera.azimuth = azimuth
        focusAzimuth = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpTouch(context: Context) {
        val degPerPx = 0.55 / context.resources.displayMetrics.density
        var scaling = false
        val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                spin = 0.0
                focusAzimuth = null
                focusElevation = null
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (scaling) return true
                camera.azimuth += dx * degPerPx
                camera.elevation = (camera.elevation - dy * degPerPx * 0.6)
                    .coerceIn(CarCamera.MIN_ELEVATION, CarCamera.MAX_ELEVATION)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (!scaling) spin = -vx * degPerPx
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                camera.zoom = 1.0
                focusAzimuth = CarCamera.HOME_AZIMUTH
                focusElevation = CarCamera.HOME_ELEVATION
                return true
            }
        })
        val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector) = true.also { scaling = true }
            override fun onScale(d: ScaleGestureDetector): Boolean {
                camera.zoom = (camera.zoom / d.scaleFactor).coerceIn(0.7, 1.25)
                return true
            }
        })
        textureView.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    scaling = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
            }
            pinch.onTouchEvent(e)
            gestures.onTouchEvent(e)
            true
        }
    }

    private fun start() {
        if (running || destroyed) return
        running = true
        lastFrame = 0L
        choreographer.postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        choreographer.postFrameCallback(this)
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1e9f).coerceAtMost(0.1f)
        lastFrame = frameTimeNanos

        resourceLoader.asyncUpdateLoad()
        addReadyRenderables()
        moveCamera(dt.toDouble())
        animateParts(dt)
        animateCable(dt)

        val e = camera.eye
        filamentCamera.lookAt(
            e[0], e[1], e[2],
            CarCamera.TARGET[0], CarCamera.TARGET[1], CarCamera.TARGET[2],
            0.0, 1.0, 0.0,
        )
        val chain = swapChain ?: return
        if (uiHelper.isReadyToRender && renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun addReadyRenderables() {
        val rm = engine.renderableManager
        while (true) {
            val count = asset.popRenderables(readyRenderables)
            if (count == 0) break
            for (i in 0 until count) {
                val ri = rm.getInstance(readyRenderables[i])
                if (readyRenderables[i] == shadowEntity) {
                    rm.setCastShadows(ri, false)
                    rm.setReceiveShadows(ri, false)
                } else {
                    rm.setScreenSpaceContactShadows(ri, true)
                }
            }
            scene.addEntities(readyRenderables.copyOf(count))
        }
    }

    private fun moveCamera(dt: Double) {
        if (!touching && abs(spin) > 0.5) {
            camera.azimuth += spin * dt
            spin *= exp(-dt * 3.2)
        }
        val k = 1 - exp(-dt * 3.5)
        focusAzimuth?.let { target ->
            // Shortest way round.
            val diff = ((target - camera.azimuth) % 360 + 540) % 360 - 180
            camera.azimuth += diff * k
            if (abs(diff) < 0.05) focusAzimuth = null
        }
        focusElevation?.let { target ->
            val diff = target - camera.elevation
            camera.elevation += diff * k
            if (abs(diff) < 0.05) focusElevation = null
        }
        camera.azimuth = ((camera.azimuth % 360) + 360) % 360
        if (camera.azimuth > 180) camera.azimuth -= 360
        camera.update()
    }

    private val rotation = FloatArray(16)
    private val out = FloatArray(16)

    private fun animateParts(dt: Float) {
        val tm = engine.transformManager
        for (part in Part.entries) {
            val (inst, rest) = transforms[part] ?: continue
            val target = if (part.isOpen(pose)) 1f else 0f
            val i = part.ordinal
            val prev = current[i]
            // Ease toward the target (~0.4 s), like a door swinging.
            current[i] = if (dt == 0f) target else prev + (target - prev) * (1f - exp(-dt * 7f))
            if (abs(current[i] - prev) < 1e-4f && dt != 0f) continue
            val t = current[i]
            val eased = t * t * (3 - 2 * t)
            Matrix.setRotateM(rotation, 0, part.openDegrees * eased, part.axis[0], part.axis[1], part.axis[2])
            Matrix.multiplyMM(out, 0, rest, 0, rotation, 0)
            tm.setTransform(inst, out)
        }
    }

    /** Slides the plug in along the port's normal when plugged in, and back out when not. */
    private fun animateCable(dt: Float) {
        val (inst, ri, rest) = cable ?: return
        val target = if (pose.pluggedIn) 1f else 0f
        val prev = cablePlugged
        cablePlugged = if (dt == 0f) target else prev + (target - prev) * (1f - exp(-dt * 5f))
        if (abs(target - cablePlugged) < 1e-3f) cablePlugged = target
        val show = cablePlugged > 0.002f
        if (show != cableShown) {
            engine.renderableManager.setLayerMask(ri, 0xFF, if (show) 0xFF else 0x00)
            cableShown = show
        }
        if (cablePlugged == prev && dt != 0f) return
        val t = cablePlugged
        val away = (1 - t * t * (3 - 2 * t)) * 0.35f
        rest.copyInto(out)
        val n = shape.chargePortNormal
        out[12] += (n[0] * away).toFloat()
        out[13] += (n[1] * away).toFloat()
        out[14] += (n[2] * away).toFloat()
        engine.transformManager.setTransform(inst, out)
    }

    private fun destroy() {
        if (destroyed) return
        running = false
        destroyed = true
        choreographer.removeFrameCallback(this)
        uiHelper.detach()
        resourceLoader.asyncCancelLoad()
        resourceLoader.evictResourceData()
        scene.removeEntities(asset.entities)
        assetLoader.destroyAsset(asset)
        assetLoader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        resourceLoader.destroy()
        indirectLight?.let { engine.destroyIndirectLight(it) }
        reflections?.let { engine.destroyTexture(it) }
        engine.destroyEntity(sun)
        EntityManager.get().destroy(sun)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(filamentCamera.entity)
        EntityManager.get().destroy(filamentCamera.entity)
        engine.destroy()
    }

    private inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            textureView.display?.let { displayHelper.attach(renderer, it) }
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            camera.setSize(width, height)
            camera.update()
            filamentCamera.setCustomProjection(camera.projection, CarCamera.NEAR, CarCamera.FAR)
        }
    }

    /** Prefilters the studio HDR into a reflections cubemap on the GPU. Null if anything fails. */
    private fun studioLight(context: Context): IndirectLight? = runCatching {
        val hdr = context.assets.open("envs/studio.hdr").use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(hdr.size).put(hdr).also { it.rewind() }
        val equirect = HDRLoader.createTexture(engine, buf) ?: return@runCatching null
        val ibl = IBLPrefilterContext(engine)
        val toCube = IBLPrefilterContext.EquirectangularToCubemap(ibl)
        val filter = IBLPrefilterContext.SpecularFilter(ibl)
        val cube = toCube.run(equirect)
        val filtered = filter.run(cube)
        engine.destroyTexture(equirect)
        engine.destroyTexture(cube)
        toCube.destroy()
        filter.destroy()
        ibl.destroy()
        reflections = filtered
        // No explicit irradiance: Filament derives it from the reflections cubemap.
        IndirectLight.Builder().reflections(filtered).intensity(28_000f).build(engine)
    }.onFailure { android.util.Log.w("OpenBlue", "Studio lighting unavailable", it) }.getOrNull()

    companion object {
        init {
            Utils.init()
        }
    }
}

/**
 * The car in 3D: doors, frunk and tailgate follow [pose], air streams into the intake and
 * through the cabin while climate runs, and the charging cable is plugged in while it charges.
 * Drag to look around, pinch to zoom, double-tap to reset.
 *
 * Where 3D can't run (previews, screenshot tests, or if Filament fails) it shows a still render
 * of the same model from the default angle, with the same effects over it.
 */
@Composable
fun Car3D(pose: CarPose, modifier: Modifier = Modifier, body: CarBody = CarBody.IONIQ5, paint: CarPaint = CarPaint.WHITE) {
    var failed by remember { mutableStateOf(false) }
    var renderer by remember(body) { mutableStateOf<CarRenderer?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shape = remember(body) { runCatching { CarBody.shape(context, body) }.getOrDefault(CarShape.IONIQ5) }
    if (failed) {
        Box(modifier, contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("3D view unavailable on this phone")
        }
        return
    }
    val currentPose by rememberUpdatedState(pose)
    val currentPaint by rememberUpdatedState(paint)
    // A different car model needs a new renderer: key the view on it.
    androidx.compose.runtime.key(body) { Box(modifier) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    val r = runCatching { CarRenderer(ctx, tv, body, shape) }
                        .onFailure { android.util.Log.e("OpenBlue", "3D car unavailable", it) }
                        .getOrNull()
                    if (r == null) failed = true
                    r?.pose = currentPose
                    r?.paint = currentPaint
                    tv.tag = r
                    renderer = r
                }
            },
            update = { tv ->
                (tv.tag as? CarRenderer)?.let { r ->
                    r.pose = currentPose
                    if (r.paint != currentPaint) r.paint = currentPaint
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // The air and charge overlay, projected with the same camera the renderer uses.
        renderer?.let { r ->
            CarEffects(r.camera, pose.climate, pose.defrost, pose.charging, { r.cablePlugged }, Modifier.fillMaxSize(), shape = shape)
        }
    } }
}

