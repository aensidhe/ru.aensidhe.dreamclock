package ru.aensidhe.dreamclock.pairing

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

object PairingQr {
    fun matrix(
        url: String,
        size: Int = 512,
    ): BitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size)

    fun bitmap(
        url: String,
        size: Int = 512,
    ): Bitmap {
        val matrix = matrix(url, size)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
