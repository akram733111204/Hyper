package com.rukn.expiry.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.rukn.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/** XLSX is the durable authority. No silent migration or hidden authoritative SQLite DB. */
class ExcelRepository(private val context:Context){
    private val file=AtomicFile(File(context.filesDir,"rukn_expiry.xlsx"))
    private val mutex=Mutex()
    private var current:AppData?=null

    suspend fun snapshot():AppData=withContext(Dispatchers.IO){mutex.withLock{
        current ?: if(file.baseFile.exists()) file.openRead().use{XlsxCodec.read(it)}.also{current=it}
        else AppData().also{writeAtomic(it);current=it}
    }}
    private fun writeAtomic(data:AppData){
        val stream=file.startWrite()
        try{XlsxCodec.write(data,stream);file.finishWrite(stream)}
        catch(e:Exception){file.failWrite(stream);throw e}
    }
    suspend fun update(action:String,entityType:String,entityId:String,transform:(AppData)->AppData):AppData =withContext(Dispatchers.IO){mutex.withLock{
        val previous=current?:if(file.baseFile.exists()) file.openRead().use{XlsxCodec.read(it)} else AppData()
        val updated=transform(previous)
        fun state(data:AppData):String = when(entityType){
            "Product" -> data.products.firstOrNull{it.id==entityId}?.toString().orEmpty()
            "Batch" -> data.batches.firstOrNull{it.id==entityId}?.toString().orEmpty()
            "Task" -> data.tasks.firstOrNull{it.id==entityId}?.toString().orEmpty()
            "Setting" -> data.settings[entityId].orEmpty()
            else -> ""
        }
        val logged=updated.copy(audit=updated.audit+AuditEntry(entityType=entityType,entityId=entityId,action=action,
            before=state(previous).take(3000),after=state(updated).take(3000)))
        writeAtomic(logged)
        current=logged
        logged
    }}
    suspend fun exportTo(uri:Uri)=withContext(Dispatchers.IO){mutex.withLock{
        val data=current?:if(file.baseFile.exists()) file.openRead().use{XlsxCodec.read(it)} else AppData()
        val output=context.contentResolver.openOutputStream(uri)?:error("لا يمكن فتح ملف التصدير")
        output.use{XlsxCodec.write(data,it)}
    }}
    suspend fun importFrom(uri:Uri):AppData=withContext(Dispatchers.IO){mutex.withLock{
        val stream=context.contentResolver.openInputStream(uri)?:error("لا يمكن قراءة ملف الاستيراد")
        val replacement=stream.use{XlsxCodec.read(it)}
        // Never erase user data through unreviewed import. Import is REPLACE after UI confirmation.
        writeAtomic(replacement);current=replacement
        replacement
    }}
    suspend fun createCatalogTemplate(uri:Uri)=withContext(Dispatchers.IO){
        val output=context.contentResolver.openOutputStream(uri)?:error("لا يمكن إنشاء ملف القالب")
        context.assets.open("rukn_catalog_template.xlsx").use { source -> output.use { source.copyTo(it) } }
    }
    suspend fun previewCatalog(uri:Uri):CatalogImportPreview=withContext(Dispatchers.IO){
        val input=context.contentResolver.openInputStream(uri)?:error("لا يمكن فتح ملف الكتالوج")
        val rows=input.use { XlsxCodec.readCatalog(it) }
        val fileName=runCatching {
            context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)
                ?.use { cursor ->if(cursor.moveToFirst())cursor.getString(0) else "" }
        }.getOrNull().orEmpty().take(160)
        CatalogImportEngine.plan(rows,snapshot()).copy(fileName=fileName)
    }
    suspend fun importCatalog(preview:CatalogImportPreview):Pair<AppData,CatalogImportPreview> {
        var applied:CatalogImportPreview?=null
        val saved=update("import_reference_catalog","Catalog","catalog"){old ->
            val result=CatalogImportEngine.merge(preview.rows,old,preview.conflictPolicy)
            applied=result.preview.copy(fileName=preview.fileName)
            val summary="file=${preview.fileName};new=${result.preview.newCount};filled=${result.preview.fillCount};"+
                "unchanged=${result.preview.unchangedCount};rejected=${result.preview.rejectedCount};"+
                "aliases=${result.preview.aliasNewCount};policy=${preview.conflictPolicy}"
            result.data.copy(audit=result.data.audit+AuditEntry(entityType="CatalogImport",entityId="catalog",
                action="catalog_import_summary",after=summary))
        }
        return saved to (applied?:error("تعذر التحقق من الاستيراد"))
    }
    suspend fun exportCatalog(uri:Uri)=withContext(Dispatchers.IO){
        val output=context.contentResolver.openOutputStream(uri)?:error("لا يمكن إنشاء تصدير الكتالوج")
        output.use{XlsxCodec.writeCatalog(snapshot(),it)}
    }
    suspend fun exportCsv(uri:Uri,type:String,days:Int,category:String="",location:String="",verifiedOnly:Boolean=false):Unit=withContext(Dispatchers.IO){
        val data=snapshot()
        val productMap=data.products.associateBy{it.id}
        val selected=data.batches.filter{b->
            val p=productMap[b.productId]
            (category.isBlank() || p?.category?.contains(category,ignoreCase=true)==true) &&
            (location.isBlank() || b.location.contains(location,ignoreCase=true) || p?.shelf?.contains(location,ignoreCase=true)==true) &&
            (!verifiedOnly || b.verified) && when(type){
                "expired"->ExpiryEngine.expired(b)
                "due"->ExpiryEngine.dueWithin(b,days)
                "unknown"->ExpiryEngine.expiryDate(b)==null
                else->true
            }
        }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { w ->
            w.write("\uFEFF")
            w.appendLine("المنتج,الباركود,التشغيلة,تاريخ الإنتاج,تاريخ الانتهاء,حالة التحقق,الكمية,الوحدة,الموقع,الحالة,الملاحظات")
            fun csv(s:String):String {
                val first=s.trimStart().firstOrNull()
                val escaped=if(first in listOf('=','+','-','@')) "'"+s else s
                return "\""+escaped.replace("\"","\"\"")+"\""
            }
            for(b in selected){
                val p=productMap[b.productId]
                w.appendLine(listOf(p?.displayName.orEmpty(),p?.barcode.orEmpty(),b.lot,b.productionDate,b.expiryDate,
                    if(b.verified) "تمت المراجعة" else "غير مؤكد",b.quantity,b.unit,b.location,b.status.name,b.notes
                ).joinToString(",",transform=::csv))
            }
        }?:error("فشل فتح ملف التقرير")
    }
}
