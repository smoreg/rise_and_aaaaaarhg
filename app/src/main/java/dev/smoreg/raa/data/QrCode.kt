package dev.smoreg.raa.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.SecureRandom

@Entity(tableName = "qr_codes")
data class QrCode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Exact text a scan must produce: our generated `raa:` token or any registered barcode. */
    val payload: String,
    val generated: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        private const val PREFIX = "raa:"

        fun newPayload(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return PREFIX + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
