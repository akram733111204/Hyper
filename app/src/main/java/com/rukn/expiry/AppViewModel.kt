package com.rukn.expiry

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rukn.core.*
import com.rukn.expiry.data.ExcelRepository
import com.rukn.expiry.network.LookupResponse
import com.rukn.expiry.network.ProductLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class AppViewModel(application:Application): AndroidViewModel(application){
    private val repository=ExcelRepository(application)
    private val _data=MutableStateFlow(AppData())
    val data=_data.asStateFlow()
    private val _busy=MutableStateFlow(true)
    val busy=_busy.asStateFlow()
    private val _notice=MutableStateFlow("")
    val notice=_notice.asStateFlow()
    init { refresh() }
    private fun refresh()=viewModelScope.launch{try{_data.value=repository.snapshot()}catch(e:Exception){_notice.value="فشل قراءة بيانات Excel: ${e.message}"}finally{_busy.value=false}}
    fun dismissNotice(){_notice.value=""}
    suspend fun resolveBarcode(barcode:String,mode:LookupMode):LookupResponse {
        val code=BarcodeRules.normalize(barcode)
        if(code.isBlank())return LookupResponse(null,"لم يُقرأ باركود؛ أكمل البيانات يدويًا")
        if(mode==LookupMode.MANUAL) return LookupResponse(null,"الإدخال اليدوي متاح؛ احتفظنا بالباركود المقروء")
        if(mode==LookupMode.AUTO || mode==LookupMode.LOCAL){
            _data.value.products.firstOrNull{BarcodeRules.normalize(it.barcode)==code}?.let{active->
                val reference=CatalogImportEngine.resolve(code,_data.value)
                val enriched=reference?.let{CatalogImportEngine.enrichActiveProduct(active,it)} ?: active
                return LookupResponse(enriched,if(enriched!=active)
                    "منتج محفوظ مسبقًا؛ اقترحنا ملء حقول ناقصة من الكتالوج، راجعها قبل الحفظ"
                    else "وجدنا المنتج ضمن سجلاتك السابقة")
            }
            CatalogImportEngine.resolve(code,_data.value)?.let{
                return LookupResponse(it.forEditor(),"تم التعرف على المنتج من كتالوج Excel المحلي؛ راجع معلوماته")
            }
        }
        if(mode==LookupMode.LOCAL) return LookupResponse(null,"لم يُعثر على المنتج محليًا؛ أكمل بياناته يدويًا")
        val settings=_data.value.settings
        return ProductLookup.lookup(code,settings["officialProxy"].orEmpty(),settings["communityFallback"]=="true")
    }
    suspend fun resolveBarcode(barcode:String):LookupResponse=resolveBarcode(barcode,LookupMode.parse(_data.value.settings["lookupMode"]))

    suspend fun createCatalogTemplate(uri:Uri)=repository.createCatalogTemplate(uri)
    suspend fun previewCatalog(uri:Uri):CatalogImportPreview=repository.previewCatalog(uri)
    suspend fun importCatalog(preview:CatalogImportPreview):CatalogImportPreview {
        val (saved,result)=repository.importCatalog(preview)
        _data.value=saved
        return result
    }
    suspend fun exportCatalog(uri:Uri)=repository.exportCatalog(uri)
    private fun merge(previous:Product,new:Product):Product=previous.copy(
        barcode=new.barcode.ifBlank{previous.barcode},nameAr=new.nameAr.ifBlank{previous.nameAr},
        nameEn=new.nameEn.ifBlank{previous.nameEn},brand=new.brand.ifBlank{previous.brand},
        size=new.size.ifBlank{previous.size},unitsPerCarton=new.unitsPerCarton.ifBlank{previous.unitsPerCarton},category=new.category.ifBlank{previous.category},
        aisle=new.aisle.ifBlank{previous.aisle},shelf=new.shelf.ifBlank{previous.shelf},
        notes=new.notes.ifBlank{previous.notes},source=previous.source.ifBlank{new.source},
        sourceUrl=new.sourceUrl.ifBlank{previous.sourceUrl},updatedAt=Instant.now().toString()
    )
    suspend fun saveProduct(product:Product,batch:Batch?,allowDuplicateBatch:Boolean=false):String {
        var finalId=product.id
        val result=repository.update("save_product_and_batch","Product",product.id){old->
            val sameById=old.products.firstOrNull{it.id==product.id}
            val code=BarcodeRules.normalize(product.barcode)
            val sameByCode=if(code.isBlank())null else old.products.firstOrNull{BarcodeRules.normalize(it.barcode)==code}
            require(sameById==null || sameByCode==null || sameById.id==sameByCode.id){
                "الباركود مرتبط بمنتج آخر. لا يمكن دمج السجلين تلقائيًا؛ راجع المنتجات المسجلة."
            }
            val existing=sameById?:sameByCode
            // Editing the same stable product ID is an explicit update: honor intentional
            // clears of notes/fields. Barcode-only matching still merges nonblank values
            // defensively so an import/collision never wipes existing manual details.
            val updated=when {
                existing==null->product
                sameById!=null->product.copy(id=existing.id,createdAt=existing.createdAt,updatedAt=Instant.now().toString())
                else->merge(existing,product)
            }
            finalId=updated.id
            val products=if(existing==null) old.products+updated else old.products.map{if(it.id==existing.id)updated else it}
            val fixedBatch=batch?.copy(productId=finalId,updatedAt=Instant.now().toString())
            if(!allowDuplicateBatch && fixedBatch!=null && (fixedBatch.lot.isNotBlank() || fixedBatch.expiryDate.isNotBlank())) {
                require(old.batches.none{it.id!=fixedBatch.id && it.productId==finalId &&
                    it.lot==fixedBatch.lot && it.expiryDate==fixedBatch.expiryDate && it.location==fixedBatch.location}){
                    "تشغيلة متشابهة مسجلة مسبقًا؛ راجعها قبل الحفظ أو أكد رغبتك في التكرار"
                }
            }
            val batches=if(fixedBatch==null)old.batches else if(old.batches.any{it.id==fixedBatch.id})
                old.batches.map{if(it.id==fixedBatch.id)fixedBatch else it} else old.batches+fixedBatch
            val audit=if(fixedBatch!=null)old.audit+AuditEntry(entityType="Batch",entityId=fixedBatch.id,
                action=if(old.batches.any{it.id==fixedBatch.id})"update_batch_with_product" else "create_batch_with_product",
                before=old.batches.firstOrNull{it.id==fixedBatch.id}?.toString().orEmpty().take(3000),
                after=fixedBatch.toString().take(3000)) else old.audit
            old.copy(products=products,batches=batches,audit=audit)
        }
        _data.value=result
        return finalId
    }
    suspend fun updateBatch(batch:Batch){
        val result=repository.update("update_batch","Batch",batch.id){old->
            old.copy(batches=old.batches.map{if(it.id==batch.id)batch.copy(updatedAt=Instant.now().toString()) else it})
        };_data.value=result
    }
    suspend fun addTask(batchId:String,title:String){
        val task=InspectionTask(batchId=batchId,title=title)
        _data.value=repository.update("create_task","Task",task.id){it.copy(tasks=it.tasks+task)}
    }
    suspend fun setTaskDone(taskId:String){
        _data.value=repository.update("complete_task","Task",taskId){old->old.copy(tasks=old.tasks.map{if(it.id==taskId) it.copy(status=TaskStatus.DONE,closedAt=Instant.now().toString()) else it})}
    }
    suspend fun saveSetting(key:String,value:String){
        _data.value=repository.update("change_setting","Setting",key){it.copy(settings=it.settings+(key to value))}
    }
    suspend fun exportWorkbook(uri:Uri)=repository.exportTo(uri)
    suspend fun importWorkbook(uri:Uri){_data.value=repository.importFrom(uri)}
    suspend fun exportReport(uri:Uri,type:String,days:Int,category:String="",location:String="",verifiedOnly:Boolean=false)=
        repository.exportCsv(uri,type,days,category,location,verifiedOnly)
}
