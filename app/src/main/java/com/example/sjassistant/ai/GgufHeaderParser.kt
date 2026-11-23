package com.example.sjassistant.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufInspectionResult(
    val isValidGguf: Boolean,
    val fileName: String,
    val fileSizeMb: Long,
    val ggufVersion: Int,
    val tensorCount: Long,
    val kvCount: Long,
    val estimatedRamMb: Long,
    val statusSummary: String
)

/**
 * Pure-Kotlin O(1)-memory GGUF binary header parser.
 * Reads only the first 24 bytes (magic + version + tensorCount + kvCount) via ContentResolver
 * so multi-hundred-MB quantized models never spike Java heap during validation.
 */
object GgufHeaderParser {
    private const val GGUF_MAGIC_LE = 0x46554747 // "GGUF" in ASCII little-endian

    fun inspectUri(context: Context, uri: Uri): GgufInspectionResult {
        var fileName = "model.gguf"
        var fileSizeBytes = 0L

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                if (sizeIdx >= 0) fileSizeBytes = cursor.getLong(sizeIdx)
            }
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            parseHeader(stream, fileName, fileSizeBytes)
        } ?: GgufInspectionResult(
            isValidGguf = false,
            fileName = fileName,
            fileSizeMb = fileSizeBytes / (1024 * 1024),
            ggufVersion = 0,
            tensorCount = 0,
            kvCount = 0,
            estimatedRamMb = 0,
            statusSummary = "Unable to open file stream"
        )
    }

    fun parseHeader(stream: InputStream, fileName: String, fileSizeBytes: Long): GgufInspectionResult {
        val header = ByteArray(24)
        val dis = DataInputStream(stream)
        val bytesRead = runCatching {
            dis.readFully(header)
            24
        }.getOrDefault(0)

        val sizeMb = (fileSizeBytes / (1024L * 1024L)).coerceAtLeast(1L)
        if (bytesRead < 24) {
            return GgufInspectionResult(
                isValidGguf = false,
                fileName = fileName,
                fileSizeMb = sizeMb,
                ggufVersion = 0,
                tensorCount = 0,
                kvCount = 0,
                estimatedRamMb = 0,
                statusSummary = "File too small for GGUF header"
            )
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        val version = buf.int
        val tensorCount = buf.long
        val kvCount = buf.long

        val valid = magic == GGUF_MAGIC_LE && version in 1..4 && tensorCount >= 0
        val estimatedRam = if (valid) (sizeMb * 1.22).toLong().coerceAtLeast(sizeMb + 48) else 0L

        val summary = if (valid) {
            "GGUF v$version • $tensorCount tensors • $kvCount metadata keys • Est. ${estimatedRam}MB RAM"
        } else {
            "Not a valid GGUF header (magic=0x${magic.toUInt().toString(16)})"
        }

        return GgufInspectionResult(
            isValidGguf = valid,
            fileName = fileName,
            fileSizeMb = sizeMb,
            ggufVersion = version,
            tensorCount = tensorCount,
            kvCount = kvCount,
            estimatedRamMb = estimatedRam,
            statusSummary = summary
        )
    }
}
