package com.rukn.expiry.network

import com.rukn.core.Product
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The official Saudi connector uses an owner-deployed HTTPS proxy with authorized API credentials.
 * Never embed keys in APK or infer official approval from a public endpoint.
 */
data class LookupResponse(val product:Product?,val message:String)
object ProductLookup {
    private fun fetch(url:String):JSONObject? {
        val connection=(URL(url).openConnection() as HttpURLConnection)
        connection.connectTimeout=7000; connection.readTimeout=9000
        connection.setRequestProperty("Accept","application/json")
        connection.setRequestProperty("User-Agent","RuknExpiry/0.1 (manual-confirmation)")
        return try{
            if(connection.responseCode !in 200..299) return null
            val text=connection.inputStream.bufferedReader().use{it.readText().take(600_000)}
            JSONObject(text)
        } finally {connection.disconnect()}
    }
    suspend fun lookup(barcode:String,proxy:String,allowCommunity:Boolean):LookupResponse =withContext(Dispatchers.IO){
        if(barcode.isBlank())return@withContext LookupResponse(null,"الباركود فارغ")
        if(proxy.isNotBlank()) {
            try {
                val base=proxy.trim().trimEnd('/')
                require(base.startsWith("https://",ignoreCase=true)){"يجب أن يبدأ رابط مصدر البيانات بـ https://"}
                val data=fetch("$base/v1/products/${java.net.URLEncoder.encode(barcode,"UTF-8")}")
                val obj=data?.optJSONObject("product")
                if(obj!=null){
                    val p=Product(barcode=barcode,nameAr=obj.optString("nameAr"),nameEn=obj.optString("nameEn"),
                        brand=obj.optString("brand"),size=obj.optString("size"),category=obj.optString("category"),
                        source="SFDA-proxy",sourceUrl=obj.optString("sourceUrl"))
                    if(p.nameAr.isNotBlank()||p.nameEn.isNotBlank()||p.brand.isNotBlank())
                        return@withContext LookupResponse(p,"معلومات من مصدر خارجي: يرجى التأكد من مطابقتها للعبوة")
                }
            }catch(e:Exception){
                if(!allowCommunity)return@withContext LookupResponse(null,"تعذر الوصول إلى خدمة المنتجات الرسمية: ${e.message}. يمكنك الإدخال يدويًا")
            }
        }
        if(allowCommunity){
            try{
                val data=fetch("https://world.openfoodfacts.org/api/v2/product/${java.net.URLEncoder.encode(barcode,"UTF-8")}.json?fields=code,product_name,product_name_ar,brands,quantity,categories")
                val obj=data?.optJSONObject("product")
                if(data?.optInt("status")==1 && obj!=null){
                    val p=Product(barcode=barcode,nameAr=obj.optString("product_name_ar"),nameEn=obj.optString("product_name"),
                        brand=obj.optString("brands"),size=obj.optString("quantity"),category=obj.optString("categories"),
                        source="OpenFoodFacts-community",sourceUrl="https://world.openfoodfacts.org/product/$barcode")
                    return@withContext LookupResponse(p,"بيانات مجتمعية غير رسمية: تحقق من المنتج قبل حفظه")
                }
            }catch(_:Exception){}
        }
        LookupResponse(null,"لم نعثر على معلومات للباركود. يمكن إضافة المنتج يدويًا دون أي حقول إلزامية")
    }
}
