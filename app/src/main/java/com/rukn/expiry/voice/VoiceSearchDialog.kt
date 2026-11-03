package com.rukn.expiry.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Voice search only proposes a query and never modifies stock/expiry records. */
@Composable fun VoiceSearchDialog(settings:Map<String,String>,onDismiss:()->Unit,onSearch:(String)->Unit){
    val context=LocalContext.current
    val main=remember{Handler(Looper.getMainLooper())}
    val proxy=settings["voiceProxyUrl"].orEmpty()
    val secret=remember{VoiceSecureConfig.getToken(context)}
    val configured=proxy.startsWith("https://")&&secret.isNotBlank()
    var mode by remember{mutableStateOf(VoiceEngineMode.parse(settings["voiceEngine"]))}
    var consent by remember{mutableStateOf(false)}
    var permission by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permission=it}
    var transcript by remember{mutableStateOf("")}
    var listening by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var retry by remember{mutableIntStateOf(0)}
    var popup by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){if(!permission)request.launch(Manifest.permission.RECORD_AUDIO)}
    var session by remember{mutableStateOf<VoiceSession?>(null)}
    val effective=resolveVoiceMode(mode,context,configured&&consent)
    val blocked=effective==VoiceEngineMode.OPENAI_LIVE && (!configured||!consent)
    DisposableEffect(permission,effective,retry,blocked){
        if(permission && !blocked){
            val partial:(String)->Unit={text->main.post{transcript=text}}
            val state:(String)->Unit={text->main.post{error=text}}
            val completed:(String)->Unit={text->main.post{transcript=text;listening=false}}
            val failed:(String)->Unit={text->main.post{listening=false;error=text}}
            val rec=if(effective==VoiceEngineMode.OPENAI_LIVE)CloudVoiceSession(context,proxy,secret,partial,completed,state,failed)
                else DeviceVoiceSession(context,effective==VoiceEngineMode.DEVICE_OFFLINE,partial,completed,state,failed)
            session=rec;listening=true;rec.start()
            onDispose{rec.release();session=null;listening=false}
        }else onDispose{session?.release();session=null;listening=false}
    }
    AlertDialog(onDismissRequest={session?.release();onDismiss()},title={Text("البحث الصوتي عن المنتجات")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("قل مثلًا: ابحث عن شاي الربيع الأسود، أو: وريني منتجات الشاي")
            Box{
                OutlinedButton(onClick={popup=true},modifier=Modifier.fillMaxWidth()){
                    Text(when(mode){
                        VoiceEngineMode.AUTO->"تلقائي"
                        VoiceEngineMode.DEVICE_OFFLINE->"محلي دون إنترنت"
                        VoiceEngineMode.DEVICE_SYSTEM->"محرك هاتف Android"
                        VoiceEngineMode.OPENAI_LIVE->"OpenAI Live — خادم خاص"
                    },modifier=Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(popup,onDismissRequest={popup=false}){
                    VoiceEngineMode.entries.forEach{m->
                        DropdownMenuItem(text={Text(m.name)},onClick={mode=m;popup=false;transcript=""})
                    }
                }
            }
            if(mode==VoiceEngineMode.OPENAI_LIVE || (mode==VoiceEngineMode.AUTO&&configured))Row{
                Checkbox(consent,onCheckedChange={consent=it})
                Text("أوافق على إرسال الصوت لهذه الجلسة فقط",modifier=Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodySmall)
            }
            if(blocked)Text(if(!configured)"يلزم إعداد الخادم الآمن في الإعدادات" else "يلزم قبول إرسال الصوت",color=MaterialTheme.colorScheme.error)
            if(listening)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(error,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(transcript,{transcript=it},label={Text("النص المسموع — قابل للتعديل")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={session?.stop()},enabled=listening){Icon(Icons.Outlined.Stop,null);Text("إنهاء")}
                OutlinedButton(onClick={transcript="";retry++},enabled=permission&&!blocked){Icon(Icons.Outlined.Mic,null);Text("إعادة")}
            }
        }},confirmButton={Button(onClick={
            val q=transcript.trim().replace(Regex("^(?:ابحث(?:\\s+لي)?\\s+عن|دور\\s+على|وريني|اريد|ابي|عرض\\s+منتجات|اعرض\\s+منتجات)\\s*"),"").trim()
            if(q.isNotBlank()){session?.release();onSearch(q)}
        },enabled=transcript.isNotBlank()){Text("بحث")}},
        dismissButton={TextButton(onClick={session?.release();onDismiss()}){Text("إغلاق")}}
    )
}
