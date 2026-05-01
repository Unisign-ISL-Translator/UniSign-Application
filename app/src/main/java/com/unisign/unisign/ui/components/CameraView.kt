package com.unisign.unisign.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture

@Composable
fun LiveCameraView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraSelector = remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    val cameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember { PreviewView(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission.value = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission.value, cameraSelector.value) {
        if (!hasCameraPermission.value) return@LaunchedEffect

        try {
            val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = 
                ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider.value = provider
                        
                        provider.unbindAll()
                        
                        val preview = Preview.Builder()
                            .build()
                            .also { preview ->
                                preview.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector.value,
                            preview
                        )
                        
                        Log.d("LiveCameraView", "Camera bound successfully with selector: ${cameraSelector.value}")
                    } catch (exc: Exception) {
                        Log.e("LiveCameraView", "Error binding camera", exc)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        } catch (exc: Exception) {
            Log.e("LiveCameraView", "Error getting camera provider", exc)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission.value) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { previewView },
                update = { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            )

            IconButton(
                onClick = {
                    cameraSelector.value = if (cameraSelector.value == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera permission required",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
