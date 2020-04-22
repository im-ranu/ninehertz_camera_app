package com.ninehertz.cameraapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ninehertz.cameraapp.camera.FrameAnalyser
import com.ninehertz.cameraapp.constants.Constants
import com.ninehertz.cameraapp.constants.Constants.REQUEST_CODE_PERMISSIONS
import com.ninehertz.cameraapp.constants.Constants.REQUIRED_PERMISSIONS
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(),LifecycleOwner,ImageCapture.OnImageSavedListener
                , VideoCapture.OnVideoSavedListener{

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: TextureView
     var TAG = MainActivity::class.java.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)

        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }

    }

    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        viewFinder.setTransform(matrix)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun getPreview() : Preview{
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(1080, 1920))

        }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)
            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }
        return preview

    }


    private fun startCamera() {
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {

            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, FrameAnalyser())
        }
        CameraX.bindToLifecycle(
            this, getPreview(), getImageCapture(),analyzerUseCase)
    }

    private fun getImageCapture() : ImageCapture{
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setFlashMode(FlashMode.AUTO)
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }.build()

        val imageCapture = ImageCapture(imageCaptureConfig)
        findViewById<Button>(R.id.btCapture).setOnClickListener {
            Log.e(TAG,externalMediaDirs.first().toString())
            val file = File(
                externalMediaDirs.first(),
                "${System.currentTimeMillis()}.jpg"
            )
            imageCapture.takePicture(file, executor,this)
        }
        return imageCapture
    }


    private fun getVideoCapture() : VideoCapture{
        val videoCaptureConfig = VideoCaptureConfig.Builder()
            .apply {
                setTargetRotation(viewFinder.display.rotation)
            }.build()

        val videoCapture = VideoCapture(videoCaptureConfig)
        findViewById<Button>(R.id.btCapture).setOnClickListener {
            Log.e(TAG,externalMediaDirs.first().toString())
            val file = File(
                externalMediaDirs.first(),
                "${System.currentTimeMillis()}.jpg"
            )
            btCapture.visibility = View.GONE
            btRecord.visibility = View.GONE
            btCapture.isEnabled = false
            btStopRecording.visibility = View.VISIBLE
            videoCapture.startRecording(file, executor,this)
        }

        findViewById<Button>(R.id.btStopRecording).setOnClickListener {

            btCapture.visibility = View.GONE
            btRecord.visibility = View.GONE
            btCapture.isEnabled = true
            btStopRecording.visibility = View.VISIBLE
            videoCapture.stopRecording()
        }

        return videoCapture
    }

    override fun onImageSaved(file: File) {
        val msg = "Photo capture succeeded: ${file.absolutePath}"
        viewFinder.post {
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onError(
        imageCaptureError: ImageCapture.ImageCaptureError,
        message: String,
        cause: Throwable?
    ) {
        val msg = "Photo capture failed: $message"
        viewFinder.post {
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onVideoSaved(file: File) {
        val msg = "Video recording succeeded: ${file.absolutePath}"
        viewFinder.post {
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onError(
        videoCaptureError: VideoCapture.VideoCaptureError,
        message: String,
        cause: Throwable?
    ) {
        val msg = "Video recording failed: $message"
        viewFinder.post {
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
