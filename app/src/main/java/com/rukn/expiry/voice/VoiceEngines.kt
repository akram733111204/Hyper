package com.rukn.expiry.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** No microphone activity is started without RECORD_AUDIO permission. Never stores raw audio. */
enum class VoiceEngineMode {
    AUTO, DEVICE_OFFLINE, DEVICE_SYSTEM, OPENAI_LIVE;
    companion object { fun parse(v:String?)=entries.firstOrNull{it.name==v}?:AUTO }
}

interface VoiceSession {
    fun start()
    fun stop()
    fun release()
}

class DeviceVoiceSession(
    private val context:Context,private val forceOffline:Boolean,
    private val onPartial:(String)->Unit,private val onFinal:(String)->Unit,
    private val onState:(String)->Unit,private val onError:(String)->Unit
): VoiceSession {
    private var recognizer:SpeechRecognizer?=null
    private var finished=false
    override fun start(){
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){onError("يلزم إذن الميكروفون");return}
        if(forceOffline && !(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))){
            onError("محرك التعرف المحلي غير متوفر على هذا الجهاز. اختر محرك الهاتف أو السحابي.");return
        }
        try {
            finished=false
            val r=if(forceOffline && Build.VERSION.SDK_INT>=31)SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer=r
            r.setRecognitionListener(object:RecognitionListener{
                override fun onReadyForSpeech(params:Bundle?){onState("استمع الآن... تحدث بشكل طبيعي")}
                override fun onBeginningOfSpeech(){onState("جاري الاستماع")}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){onState("أحلل الكلام الآن...")}
                override fun onError(error:Int){if(!finished){finished=true;onError("تعذر التعرف الصوتي على الهاتف (رمز $error). أعد المحاولة أو اكتب يدويًا.")}}
                override fun onResults(results:Bundle?){
                    if(!finished){finished=true;val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if(text.isBlank())onError("لم تُسمع عبارة مفهومة") else onFinal(text)
                    }
                }
                override fun onPartialResults(partialResults:Bundle?){
                    val text=partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if(text.isNotBlank() && !finished)onPartial(text)
                }
                override fun onEvent(eventType:Int,params:Bundle?){}
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-SA")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-SA")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
                if(forceOffline)putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            }
            r.startListening(intent)
            onState("يتم تهيئة الميكروفون...")
        }catch(e:Exception){onError("تعذر بدء التعرف الصوتي: ${e.message}");release()}
    }
    override fun stop(){recognizer?.stopListening();onState("يجري إنهاء التعرف...")}
    override fun release(){recognizer?.cancel();recognizer?.destroy();recognizer=null;finished=true}
}

/** Real PCM16/24 kHz streaming to an owner-deployed WSS proxy. No OpenAI key is included in the APK. */
class CloudVoiceSession(
    private val context:Context,private val proxyBase:String,private val clientToken:String,
    private val onPartial:(String)->Unit,private val onFinal:(String)->Unit,
    private val onState:(String)->Unit,private val onError:(String)->Unit
):VoiceSession {
    private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS).build()
    private val alive=AtomicBoolean(false)
    private val stopRequested=AtomicBoolean(false)
    private val committed=AtomicBoolean(false)
    private var socket:WebSocket?=null
    private var recorder:AudioRecord?=null
    private val worker=CoroutineScope(Dispatchers.IO+Job())
    private var accumulated=""
    private var finalized=false
    override fun start(){
        if(!proxyBase.startsWith("https://") || clientToken.isBlank()){
            onError("يجب إعداد خادم الصوت HTTPS ورمز الوصول الخاص به أولًا.");return
        }
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){onError("يلزم إذن الميكروفون");return}
        if(!alive.compareAndSet(false,true))return
        accumulated="";finalized=false;stopRequested.set(false);committed.set(false)
        val url=proxyBase.trimEnd('/').replaceFirst("https://","wss://")+"/v1/voice/stream"
        val request=Request.Builder().url(url).header("Authorization","Bearer $clientToken").build()
        socket=http.newWebSocket(request,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){onState("متصل بنموذج الصوت؛ تحدث الآن")
                worker.launch{captureAudio(webSocket)}
            }
            override fun onMessage(webSocket:WebSocket,text:String){
                runCatching{JSONObject(text)}.onSuccess{evt->when(evt.optString("type")){
                    "delta"->{accumulated+=evt.optString("text");onPartial(accumulated)}
                    "final"->{if(!finalized){finalized=true;onFinal(evt.optString("text").ifBlank{accumulated})};alive.set(false)}
                    "error"->{alive.set(false);onError(evt.optString("message","الخدمة الصوتية غير متاحة"))}
                }}
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){
                if(alive.getAndSet(false))onError("تعذر الاتصال بخدمة الصوت: ${t.message}")
            }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){
                if(alive.getAndSet(false) && !finalized)onError("انتهى اتصال الصوت قبل اعتماد النص")
            }
        })
    }
    private suspend fun captureAudio(ws:WebSocket){
        val rate=24000
        val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(min<=0){onError("الهاتف لا يدعم إعدادات التسجيل المطلوبة");return}
        try {
            val r=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*2,8192))
            if(r.state!=AudioRecord.STATE_INITIALIZED){r.release();onError("تعذر تهيئة مسجل الصوت");return}
            recorder=r;r.startRecording()
            val buffer=ByteArray(4800)
            val started=System.currentTimeMillis()
            while(alive.get() && !stopRequested.get() && System.currentTimeMillis()-started<45_000){
                val count=r.read(buffer,0,buffer.size)
                if(count>0){
                    if(stopRequested.get())break
                    if(ws.queueSize()>1_500_000){onError("اتصال الإنترنت بطيء جدًا لنقل الصوت بشكل آمن");alive.set(false);break}
                    val payload=Base64.encodeToString(buffer,0,count,Base64.NO_WRAP)
                    ws.send(JSONObject().put("type","audio").put("data",payload).toString())
                } else if(count<0)break
            }
        }catch(e:Exception){if(alive.getAndSet(false))onError("فشل تسجيل الصوت: ${e.message}")}
        finally{
            runCatching{recorder?.stop()};recorder?.release();recorder=null
            if(alive.get() && committed.compareAndSet(false,true)){
                ws.send("{\"type\":\"commit\"}")
                onState("أنتظر النص النهائي من المحرك السحابي...")
                worker.launch { delay(16000); if(alive.get() && !finalized){alive.set(false);onError("انتهت مهلة التفريغ؛ أعد المحاولة أو اكتب يدويًا");socket?.close(1000,"timeout")} }
            }
        }
    }
    override fun stop(){
        stopRequested.set(true)
        // Stop unblocks AudioRecord.read; the audio coroutine sends commit AFTER all chunks.
        runCatching{recorder?.stop()}
        onState("جار إنهاء التسجيل وإرسال الصوت...")
    }
    override fun release(){alive.set(false);stopRequested.set(true);runCatching{recorder?.stop()};recorder?.release();recorder=null;socket?.close(1000,"user left");socket=null;worker.cancel()}
}

fun resolveVoiceMode(mode:VoiceEngineMode,context:Context,cloudConfigured:Boolean):VoiceEngineMode = when(mode){
    VoiceEngineMode.AUTO->if(cloudConfigured)VoiceEngineMode.OPENAI_LIVE
        else if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))VoiceEngineMode.DEVICE_OFFLINE
        else VoiceEngineMode.DEVICE_SYSTEM
    else->mode
}
