package com.example.reconocimiento_billetes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.reconocimiento_billetes.data.SQLiteHelper
import com.example.reconocimiento_billetes.data.TfLiteBilletesClassifier
import com.example.reconocimiento_billetes.domain.Classification
import com.example.reconocimiento_billetes.presentation.BilletesImageAnalyzer
import com.example.reconocimiento_billetes.presentation.CombinedImageAnalyzer
import com.example.reconocimiento_billetes.presentation.LuminosityAnalyzer
import com.example.reconocimiento_billetes.presentation.getCurrentDateTime
import com.example.reconocimiento_billetes.ui.theme.ReconocimientobilletesTheme
import com.example.reconocimiento_billetes.ui.theme.ScanBillActivityTheme

private var mediaPlayer: MediaPlayer? = null
private var canVibrate = false

class ScanBillActivity : ComponentActivity() {

    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mediaPlayer = MediaPlayer.create(this, R.raw.escaneo_billetes)
        mediaPlayer?.start()

        if (!hasCameraPermission())
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 0
            )

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        canVibrate = vibrator.hasVibrator()

        setContent {
            ReconocimientobilletesTheme {
                App()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun vibrateDevice(duration: Long = 300L) {
        if (canVibrate) vibrator.vibrate(duration)
    }

    private fun reproducirAudio(billete: Int) {
        val audioResId = when (billete) {
            8 -> R.raw.answer_10
            7 -> R.raw.answer_20
            6 -> R.raw.answer_50
            5 -> R.raw.answer_100
            4 -> R.raw.answer_200
            3 -> R.raw.answer_500
            2 -> R.raw.answer_1000
            1 -> R.raw.answer_2000
            0 -> R.raw.answer_10000
            else -> null
        }

        audioResId?.let {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }

            mediaPlayer = MediaPlayer.create(this, it)
            mediaPlayer?.let { mp ->
                mp.start()

                mp.setOnCompletionListener { player ->
                    player.release()
                    mediaPlayer = null
                }

                mp.setOnErrorListener { player, _, _ ->
                    player.release()
                    mediaPlayer = null
                    true
                }
            }
        }
    }

    private fun playLowLightSound() {
        if (mediaPlayer == null) mediaPlayer = MediaPlayer.create(this, R.raw.campana)
        mediaPlayer?.let { if (!it.isPlaying) it.start() }
    }

    private fun guardarBaseDeDatos(billete: Int) {
        val db = SQLiteHelper(this)
        db.insertBill(billete, getCurrentDateTime())
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        val inputStream = this.assets.open("labels.txt")

        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { labels.add(it) }
        }

        return labels
    }

    private fun getLabelFromIndex(index: Int): String {
        val labels = loadLabels()
        return if (index < labels.size) labels[index] else "Desconocido"
    }

    @Composable
    private fun App() {
        var classification by remember { mutableStateOf<Classification?>(null) }

        val billetesAnalyzer = remember {
            BilletesImageAnalyzer(
                classifier = TfLiteBilletesClassifier(
                    context = this
                ),
                onResult = { result ->
                    classification = result
                }
            )
        }

        val cameraController = remember {
            LifecycleCameraController(this).apply {
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                cameraController.unbind()
            }
        }

        val lightAnalyzer = remember {
            LuminosityAnalyzer { isLowLight ->
                if (isLowLight) {
                    cameraController.enableTorch(true)
                    playLowLightSound()
                } else cameraController.enableTorch(false)
            }
        }

        /*
        val combinedAnalyzer = remember {
            CombinedImageAnalyzer(billetesAnalyzer, lightAnalyzer)
        }
        */
        Log.d("Scanner","Iniciando el escaneo")

        //ya no analiza luminosidad

        analizar(cameraController, lightAnalyzer);

        analizar(cameraController, billetesAnalyzer)


        ScanBillActivityTheme(
            classification,
            cameraController,
            ::reproducirAudio,
            ::vibrateDevice,
            ::guardarBaseDeDatos,
            ::getLabelFromIndex,
            onFinish = { finish() }
        )
    }

    private fun analizar(cameraController: CameraController, analyzer:Analyzer){
        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this@ScanBillActivity),
            analyzer
        )
    }

}