package com.rukn.expiry.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rukn.core.*
import kotlinx.coroutines.launch

private val titles=mapOf(
    VoiceField.PRODUCTION_DATE to "الإنتاج", VoiceField.EXPIRY_DATE to "الانتهاء / الأفضل قبل",
    VoiceField.QUANTITY to "الكمية", VoiceField.UNIT to "الوحدة", VoiceField.LOT to "LOT",
    VoiceField.LOCATION to "الموقع", VoiceField.AISLE to "الممر",VoiceField.SHELF to "الرف",
    VoiceField.PRODUCT_NAME_AR to "الاسم العربي",VoiceField.PRODUCT_NAME_EN to "الاسم الإنجليزي",
    VoiceField.BRAND to "العلامة التجارية",VoiceField.SIZE to "الحجم",VoiceField.CATEGORY to "القسم",
    VoiceField.UNITS_PER_CARTON to "عدد الحبات/كرتون",VoiceField.PRODUCT_NOTES to "ملاحظات المنتج",VoiceField.BATCH_NOTES to "ملاحظات التشغيلة",
    VoiceField.BARCODE to "الباركود",VoiceField.EXPIRY_KIND to "نوع التاريخ",VoiceField.BATCH_STATUS to "حالة التشغيلة"
)
private fun actionName(action:VoiceAction)=when(action){
    VoiceAction.SET->"تعبئة";VoiceAction.CLEAR->"مسح";VoiceAction.ADD->"زيادة";VoiceAction.SUBTRACT->"نقصان"
    VoiceAction.SET_MONTH->"تعديل الشهر";VoiceAction.SET_DAY->"تعديل اليوم";VoiceAction.SET_YEAR->"تعديل السنة"
}

@Composable
fun VoiceEntryDialog(
    current:Map<VoiceField,String>, settings:Map<String,String>,
    onDismiss:()->Unit,onApply:(VoiceResult)->Unit
){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val main=remember{Handler(Looper.getMainLooper())}
    val proxy=settings["voiceProxyUrl"].orEmpty()
    val secret=remember{VoiceSecureConfig.getToken(context)}
    val configured=proxy.startsWith("https://") && secret.isNotBlank()
    val defaultMode=VoiceEngineMode.parse(settings["voiceEngine"])
    var mode by remember{mutableStateOf(defaultMode)}
    var cloudConsent by remember{mutableStateOf(false)}
    var allowed by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)}
    var listening by remember{mutableStateOf(false)}
    var state by remember{mutableStateOf("جاري إعداد الصوت...")}
    var partial by remember{mutableStateOf("")}
    var finalText by remember{mutableStateOf("")}
    var proposal by remember{mutableStateOf<VoiceResult?>(null)}
    var processing by remember{mutableStateOf(false)}
    var retrySignal by remember{mutableIntStateOf(0)}
    var activeSession by remember{mutableStateOf<VoiceSession?>(null)}
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed=it
        if(!it)state="يمكنك إدخال النص يدويًا، لكن يلزم إذن الميكروفون للاستماع"
    }
    LaunchedEffect(Unit){if(!allowed)permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)}

    suspend fun process(text:String){
        if(text.isBlank()){state="لم نتلق نصًا لتحليله";return}
        processing=true
        finalText=text
        state="أوزع المعلومات على حقول المنتج والتشغيلة..."
        val local=VoiceCommandEngine.interpret(text)
        val cloudUnderstanding=settings["voiceCloudUnderstanding"]=="true" && configured && cloudConsent
        proposal=if(cloudUnderstanding){
            runCatching{VoiceInterpreterNetwork.interpret(proxy,secret,text,current,settings["voiceSemanticModel"]?:"gpt-6.1-sol")}
                .getOrElse{err->local.copy(warnings=local.warnings+"تعذر فهم الأوامر سحابيًا؛ استخدمت المحرك المحلي: ${err.message}")}
        } else local
        processing=false
        state="راجع الحقول المقترحة قبل تطبيقها"
    }

    // Audio starts upon entering the date step, after permission and explicit cloud consent.
    val effective=resolveVoiceMode(mode,context,configured && cloudConsent)
    val blockedCloud=effective==VoiceEngineMode.OPENAI_LIVE && (!configured || !cloudConsent)
    DisposableEffect(allowed,effective,retrySignal,blockedCloud){
        if(allowed && !blockedCloud){
            val device=effective!=VoiceEngineMode.OPENAI_LIVE
            val post:(()->Unit)->Unit={task->main.post { task() }}
            val onPartial:(String)->Unit={text->post{partial=text;state="جاري التفريغ المباشر..."}}
            val onFinal:(String)->Unit={text->post{listening=false;partial=text;scope.launch{process(text)}}}
            val onState:(String)->Unit={message->post{state=message}}
            val onError:(String)->Unit={message->post{listening=false;state=message}}
            val session=if(device)DeviceVoiceSession(context,effective==VoiceEngineMode.DEVICE_OFFLINE,onPartial,onFinal,onState,onError)
                else CloudVoiceSession(context,proxy,secret,onPartial,onFinal,onState,onError)
            activeSession=session
            listening=true
            session.start()
            onDispose {session.release();activeSession=null;listening=false}
        }else onDispose{activeSession?.release();activeSession=null;listening=false}
    }
    val preview=proposal?.let{VoiceCommandEngine.apply(current,it)}
    AlertDialog(
        onDismissRequest={activeSession?.release();onDismiss()},
        title={Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Outlined.Mic,null);Text("ركن Voice AI — إدخال ذكي")
        }},
        text={Column(Modifier.fillMaxWidth().heightIn(max=580.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("قل بحرية: الإنتاج يوم 13 شهر 9 سنة 2026، الانتهاء 15 ديسمبر 2028، الكمية ست كراتين، الممر اثنين، الرف خمسة...",style=MaterialTheme.typography.bodySmall)
            var open by remember{mutableStateOf(false)}
            Box{
                OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){
                    Text(when(mode){
                        VoiceEngineMode.AUTO->"تلقائي ذكي: محرك متاح حسب الإعدادات"
                        VoiceEngineMode.DEVICE_OFFLINE->"محلي على الجهاز (دون إنترنت)"
                        VoiceEngineMode.DEVICE_SYSTEM->"خدمة التعرف المتاحة بالهاتف"
                        VoiceEngineMode.OPENAI_LIVE->"OpenAI Live — عبر خادم خاص"
                    },modifier=Modifier.weight(1f));Icon(Icons.Outlined.ArrowDropDown,null)
                }
                DropdownMenu(open,onDismissRequest={open=false}){
                    listOf(VoiceEngineMode.AUTO to "تلقائي",VoiceEngineMode.DEVICE_OFFLINE to "محلي دون إنترنت",
                        VoiceEngineMode.DEVICE_SYSTEM to "محرك الهاتف",VoiceEngineMode.OPENAI_LIVE to "OpenAI Live (يتطلب خادمًا)").forEach{(k,v)->
                        DropdownMenuItem(text={Text(v)},onClick={mode=k;open=false;proposal=null;partial=""})
                    }
                }
            }
            if(mode==VoiceEngineMode.OPENAI_LIVE || (mode==VoiceEngineMode.AUTO && configured)){
                Row{Checkbox(cloudConsent,onCheckedChange={cloudConsent=it})
                    Text("أوافق على إرسال الصوت والنص لخادمي السحابي لهذه الجلسة فقط",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=10.dp))
                }
            }
            if(blockedCloud){
                Text(if(!configured)"المحرك السحابي غير مهيأ: اضبط رابط HTTPS ورمز الخدمة في الإعدادات."
                    else "وافق على إرسال الصوت، أو اختر المحرك المحلي.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            }
            Text(state,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            if(listening)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={
                    activeSession?.release();listening=false;partial="";finalText="";proposal=null;retrySignal++
                },enabled=allowed && !blockedCloud && !processing){Icon(Icons.Outlined.Mic,null);Spacer(Modifier.width(4.dp));Text("إعادة الاستماع")}
                OutlinedButton(onClick={activeSession?.stop()},enabled=listening){Icon(Icons.Outlined.Stop,null);Text("إنهاء الكلام")}
            }
            if(!allowed)TextButton(onClick={permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)}){Text("السماح للميكروفون")}
            OutlinedTextField(value=if(finalText.isBlank())partial else finalText,onValueChange={
                finalText=it;partial=it;proposal=null
            },modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,
                label={Text("التفريغ المباشر — يمكنك تصحيح النص أيضًا")})
            OutlinedButton(onClick={scope.launch{process(finalText.ifBlank{partial})}},enabled=!processing && (finalText.isNotBlank()||partial.isNotBlank())){
                Icon(Icons.Outlined.Psychology,null);Spacer(Modifier.width(4.dp));Text("فهم الأوامر وملء الحقول")
            }
            if(processing)CircularProgressIndicator(Modifier.size(28.dp))
            proposal?.let{p->
                Text("التغييرات المقترحة — لن تطبق إلا بموافقتك",fontWeight=FontWeight.Bold)
                if(p.commands.isEmpty())Text("لم أفهم حقولًا محددة. أعد التعبير أو عدّل يدويًا.")
                p.commands.forEach{cmd->
                    Text("${actionName(cmd.action)} ${titles[cmd.field]?:cmd.field.name}: ${cmd.value.ifBlank{"[فارغ]"}}",style=MaterialTheme.typography.bodySmall)
                }
                preview?.warnings?.forEach{w->Text("⚠ $w",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                if(preview!=null && preview.changed.isNotEmpty())Text("سيتم تعديل ${preview.changed.size} حقول، ويمكن تعديلها يدويًا قبل الحفظ النهائي.",style=MaterialTheme.typography.labelSmall)
            }
            Text("لا تُحفظ أي تواريخ إلا بعد مراجعتها مقابل العبوة. لا نحتفظ بالصوت الخام.",style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(onClick={proposal?.let(onApply);activeSession?.release();onDismiss()},enabled=preview?.changed?.isNotEmpty()==true && !processing){Text("تطبيق الحقول للمراجعة")}},
        dismissButton={TextButton(onClick={activeSession?.release();onDismiss()}){Text("إغلاق دون تعديل")}}
    )
}
