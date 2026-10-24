package com.deactivated0.tv9

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

class QrCodeUtil {

    fun createQRCodeBitmap(
        content: String,
        width: Int,
        height: Int,
        characterSet: String = "UTF-8",
        errorCorrection: String = "L",
        margin: String = "1",
        @ColorInt colorBlack: Int = Color.BLACK,
        @ColorInt colorWhite: Int = Color.WHITE,
    ): Bitmap? {
        if (width <= 0 || height <= 0) {
            return null
        }
        try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            if (characterSet.isNotEmpty()) {
                hints[EncodeHintType.CHARACTER_SET] = characterSet
            }
            if (errorCorrection.isNotEmpty()) {
                hints[EncodeHintType.ERROR_CORRECTION] = errorCorrection
            }
            if (margin.isNotEmpty()) {
                hints[EncodeHintType.MARGIN] = margin
            }
            val bitMatrix =
                QR_CODE_WRITER.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            val pixels = IntArray(width * height)
            var rowOffset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (bitMatrix[x, y]) colorBlack else colorWhite
                }
                rowOffset += width
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        private val QR_CODE_WRITER = QRCodeWriter()
    }
}