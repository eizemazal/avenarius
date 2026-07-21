package com.avenarius.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen QR scanner: CameraX preview + ML Kit barcode decoder. Returns the
 * decoded string in [EXTRA_RESULT] with RESULT_OK, or RESULT_CANCELED on back /
 * denied permission. ML Kit handles dense, rotated and inverted (dark-mode) QR
 * codes automatically, which zxing did not.
 */
class QrScanActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // Guard so we only return the first decoded code (the analyzer runs on many frames).
    @Volatile private var handled = false

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finishCancel("Нет доступа к камере")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        previewView = PreviewView(this)
        setContentView(previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview =
                Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val scanner =
                BarcodeScanning.getClient(
                    BarcodeScannerOptions
                        .Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build(),
                )
            val analysis =
                ImageAnalysis
                    .Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor) { proxy -> analyze(scanner, proxy) } }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { finishCancel("Не удалось открыть камеру") }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        proxy: ImageProxy,
    ) {
        val media = proxy.image
        if (media == null || handled) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner
            .process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstOrNull()?.rawValue
                if (!value.isNullOrEmpty() && !handled) {
                    handled = true
                    finishOk(value)
                }
            }.addOnCompleteListener { proxy.close() }
    }

    private fun finishOk(contents: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, contents))
        finish()
    }

    private fun finishCancel(message: String?) {
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_RESULT = "qr_result"
    }
}
