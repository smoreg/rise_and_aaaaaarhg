package dev.smoreg.raa.mission

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** Back camera preview that reports every barcode it reads. [torch] helps in a dark bathroom. */
@Composable
fun QrScanner(onScan: (String) -> Unit, torch: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scan by rememberUpdatedState(onScan)
    var camera by remember { mutableStateOf<Camera?>(null) }
    val preview = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // The default SurfaceView stays black over the lock screen until the next layout pass.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor()
        val decoder = BarcodeDecoder()
        val main = ContextCompat.getMainExecutor(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val p = future.get().also { provider = it }
            val usePreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { proxy ->
                val text = proxy.use(decoder::decode)
                if (text != null) main.execute { if (!disposed) scan(text) }
            }
            p.unbindAll()
            camera = p.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, usePreview, analysis)
        }, main)
        onDispose {
            disposed = true
            provider?.unbindAll()
            camera = null
            executor.shutdown()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }

    AndroidView({ preview }, modifier)
}

/**
 * ZXing on the luminance plane of a camera frame. Frames arrive in sensor orientation, which in a
 * portrait app is sideways, so a one-dimensional barcode also gets a try rotated by 90°.
 */
internal class BarcodeDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC,
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.ITF,
                ),
            ),
        )
    }

    fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val buffer = plane.buffer.apply { rewind() }
        val luma = ByteArray(w * h)
        for (row in 0 until h) {
            buffer.position(row * stride)
            buffer.get(luma, row * w, w)
        }
        return decode(luma, w, h)
    }

    /** [luma] is one byte of brightness per pixel, row by row. */
    fun decode(luma: ByteArray, w: Int, h: Int): String? = read(luma, w, h) ?: read(rotate(luma, w, h), h, w)

    private fun read(luma: ByteArray, w: Int, h: Int): String? = try {
        val source = PlanarYUVLuminanceSource(luma, w, h, 0, 0, w, h, false)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } finally {
        reader.reset()
    }

    private fun rotate(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) for (x in 0 until w) out[x * h + (h - 1 - y)] = src[y * w + x]
        return out
    }
}
