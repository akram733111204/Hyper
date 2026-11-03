package com.rukn.expiry.voice

import com.rukn.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Optional cloud semantics. Server owns provider API keys and returns a constrained command proposal. */
object VoiceInterpreterNetwork {
    suspend fun interpret(baseUrl:String,token:String,transcript:String,current:Map<VoiceField,String>,model:String="gpt-6.1-sol"):VoiceResult=withContext(Dispatchers.IO){
        require(baseUrl.startsWith("https://") && token.isNotBlank()){ "خادم الفهم الصوتي لم يُضبط بأمان" }
        val conn=(URL(baseUrl.trimEnd('/')+"/v1/voice/interpret").openConnection() as HttpURLConnection)
        try{
            conn.requestMethod="POST";conn.connectTimeout=12000;conn.readTimeout=25000;conn.doOutput=true
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.setRequestProperty("Authorization","Bearer $token")
            val fields=JSONObject();current.forEach{(k,v)->if(v.isNotBlank())fields.put(k.name,v.take(200))}
            val body=JSONObject().put("transcript",transcript.take(3000)).put("current",fields).put("model",model)
            conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            if(conn.responseCode!=200)throw IllegalStateException("خدمة الفهم الصوتي رفضت الطلب: HTTP ${conn.responseCode}")
            val json=conn.inputStream.bufferedReader().use{it.readText().take(20000)}
            val parsed=JSONObject(json);val commands=mutableListOf<VoiceCommand>();val warnings=mutableListOf<String>()
            parsed.optJSONArray("warnings")?.let{w->for(i in 0 until minOf(w.length(),20))warnings+=w.optString(i).take(200)}
            parsed.optJSONArray("commands")?.let{array->for(i in 0 until minOf(array.length(),24)){
                val entry=array.optJSONObject(i)?:continue
                val field=runCatching{VoiceField.valueOf(entry.optString("field"))}.getOrNull()
                val action=runCatching{VoiceAction.valueOf(entry.optString("action","SET"))}.getOrNull()
                if(field!=null && action!=null)commands+=VoiceCommand(field,action,entry.optString("value").take(400))
                else warnings+="تجاهل النظام أمرًا غير معروف من خدمة الفهم"
            }}
            VoiceCommandEngine.validate(VoiceResult(commands,warnings,"cloud"))
        }finally{conn.disconnect()}
    }
}
