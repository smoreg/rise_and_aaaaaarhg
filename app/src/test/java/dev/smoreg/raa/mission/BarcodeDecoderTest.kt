package dev.smoreg.raa.mission

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import dev.smoreg.raa.data.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeDecoderTest {
    /** Renders [format] into a camera-like frame: dark code on light paper with a margin. */
    private fun frame(text: String, format: BarcodeFormat, w: Int, h: Int): ByteArray {
        val m = MultiFormatWriter().encode(text, format, w, h)
        return ByteArray(w * h) { i -> if (m[i % w, i / w]) 20 else 230.toByte() }
    }

    @Test fun readsOurQrPayload() {
        val payload = QrCode.newPayload()
        assertEquals(payload, BarcodeDecoder().decode(frame(payload, BarcodeFormat.QR_CODE, 640, 480), 640, 480))
    }

    @Test fun readsSidewaysBarcodeLikeAPortraitCameraSeesIt() {
        // A shampoo barcode held level in portrait arrives rotated in the landscape sensor frame.
        val w = 480
        val h = 640
        val level = frame("4006381333931", BarcodeFormat.EAN_13, h, w)
        val sideways = ByteArray(w * h)
        for (y in 0 until w) for (x in 0 until h) sideways[(h - 1 - x) * w + y] = level[y * h + x]
        assertEquals("4006381333931", BarcodeDecoder().decode(sideways, w, h))
    }

    @Test fun blankFrameIsNothing() {
        assertNull(BarcodeDecoder().decode(ByteArray(640 * 480) { 128.toByte() }, 640, 480))
    }
}
