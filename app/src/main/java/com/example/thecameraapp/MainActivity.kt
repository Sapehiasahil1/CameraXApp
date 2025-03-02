@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.thecameraapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thecameraapp.ui.theme.TheCameraAppTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var recording: Recording? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(this, CAMERAX_PERMISSIONS, 0)
        }
        enableEdgeToEdge()
        setContent {
            TheCameraAppTheme {
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE or
                                    CameraController.VIDEO_CAPTURE
                        )
                    }
                }

                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        PhotoBottomSheetContent(
                            bitmap = bitmaps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier
                                .fillMaxSize()
                        )

                        IconButton(
                            onClick = {
                                controller.cameraSelector =
                                    if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_BACK_CAMERA

                                    }
                            },
                            modifier = Modifier
                                .offset(16.dp, 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchCamera,
                                contentDescription = "Switch camera"
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        scaffoldState.bottomSheetState.expand()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "Open gallery"
                                )
                            }
                            IconButton(
                                onClick = {
                                    takePhoto(
                                        controller = controller,
                                        onPhotoTaken = viewModel::onTakePhoto
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take photo"
                                )
                            }
                            IconButton(
                                onClick = {
                                    recordVideo(controller)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Record video"
                                )
                            }

                        }
                    }
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun recordVideo(controller: LifecycleCameraController) {

        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }

        if (!hasRequiredPermissions()) {
            return
        }
        //File name
        val fileName = "recorded_video_${System.currentTimeMillis()}.mp4"

        //Media Store Location
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraXVideos")
        }
//
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val outputFile = File(filesDir, "my-recording.mp4")
        recording = controller. startRecording(
            mediaStoreOutput,
            AudioConfig.create(true),
            ContextCompat.getMainExecutor(applicationContext),
        ) {
            when(it) {
                is VideoRecordEvent.Start -> {
                    Toast.makeText(applicationContext, "Recording started", Toast.LENGTH_SHORT).show()
                }
                is VideoRecordEvent.Finalize -> {
                    if(it.hasError()) {
                        recording?.close()
                        recording = null

                        Toast.makeText(applicationContext, "Video capture failed", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "Video captured", Toast.LENGTH_SHORT).show()
                    }
                }

            }
        }

    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {

        if (!hasRequiredPermissions())
            return

        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        decodeBitmap(image),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )
                    onPhotoTaken(rotatedBitmap)

                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)

                    Log.e("CAMERA", "Couldn't take photo", exception)
                }
            }
        )
    }

    private fun decodeBitmap(image: ImageProxy): Bitmap {

        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity()).also {
            buffer.get(it)
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}

