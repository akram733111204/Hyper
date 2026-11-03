package com.rukn.expiry.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Barcode-only scanner: dates are entered by the new voice assistant or manually. */
@Composable fun BarcodeScannerDialog(onDismiss:()->Unit,onBarcode:(String)->Unit){
    val context=LocalContext.current
    var allowed by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it}
    LaunchedEffect(Unit){if(!allowed)launcher.launch(Manifest.permission.CAMERA)}
    AlertDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick=onDismiss){Text("إغلاق")}},title={Text("قراءة باركود المنتج")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(allowed){
                BarcodeLiveCamera(Modifier.fillMaxWidth().height(320.dp),onBarcode)
                Text("وجّه الكاميرا إلى الباركود. لا تحتاج إلى تصوير العبوة أو التقاط صورة.",style=MaterialTheme.typography.bodySmall)
            }else{
                Text("اسمح بالكاميرا لمسح الباركود، أو أغلق النافذة واكتب الرمز يدويًا.")
                Button(onClick={launcher.launch(Manifest.permission.CAMERA)}){Text("إذن الكاميرا")}
            }
        }}
    )
}
@Composable private fun BarcodeLiveCamera(modifier:Modifier,onBarcode:(String)->Unit){
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val previewView=remember{PreviewView(context).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    val onCode=rememberUpdatedState(onBarcode)
    val worker=remember{Executors.newSingleThreadExecutor()}
    var torch by remember{mutableStateOf(false)}
    var zoom by remember{mutableFloatStateOf(0f)}
    val bound=remember{mutableStateOf<androidx.camera.core.Camera?>(null)}
    LaunchedEffect(torch,bound.value){bound.value?.cameraControl?.enableTorch(torch)}
    LaunchedEffect(zoom,bound.value){bound.value?.cameraControl?.setLinearZoom(zoom)}
    DisposableEffect(lifecycleOwner){
        val pending=ProcessCameraProvider.getInstance(context)
        val disposed=AtomicBoolean(false);val busy=AtomicBoolean(false);val done=AtomicBoolean(false)
        val barcodeReader=BarcodeScanning.getClient()
        var last=0L
        pending.addListener({
            if(disposed.get())return@addListener
            runCatching{
                val provider=pending.get()
                val preview=Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(worker){frame->
                    val image=frame.image
                    if(image==null||done.get()||!busy.compareAndSet(false,true)||SystemClock.elapsedRealtime()-last<220){frame.close();return@setAnalyzer}
                    last=SystemClock.elapsedRealtime()
                    barcodeReader.process(InputImage.fromMediaImage(image,frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener{barcodes->
                            barcodes.firstOrNull{!it.rawValue.isNullOrBlank()}?.rawValue?.let{code->
                                if(done.compareAndSet(false,true))onCode.value(code)
                            }
                        }.addOnCompleteListener{busy.set(false);frame.close()}
                }
                provider.unbindAll()
                bound.value=provider.bindToLifecycle(lifecycleOwner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
            }
        },ContextCompat.getMainExecutor(context))
        onDispose{
            disposed.set(true)
            if(pending.isDone)runCatching{pending.get().unbindAll()}
            barcodeReader.close();worker.shutdown()
        }
    }
    Box(modifier){
        AndroidView(factory={previewView},modifier=Modifier.fillMaxSize())
        FilledTonalIconButton(onClick={torch=!torch},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){
            Icon(if(torch)Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,contentDescription="الإضاءة")
        }
        Slider(value=zoom,onValueChange={zoom=it},modifier=Modifier.align(Alignment.BottomCenter).padding(horizontal=20.dp,vertical=8.dp))
    }
}
