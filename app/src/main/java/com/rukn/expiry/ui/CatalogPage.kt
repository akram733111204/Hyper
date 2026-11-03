package com.rukn.expiry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.core.*
import com.rukn.expiry.AppViewModel
import kotlinx.coroutines.launch

/** Separate catalog import: never calls full-backup restore, never edits batches. */
@Composable
fun CatalogPage(vm:AppViewModel,data:AppData){
    val scope=rememberCoroutineScope()
    var preview by remember { mutableStateOf<CatalogImportPreview?>(null) }
    var searching by remember{mutableStateOf("")}
    var notice by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var updateReference by remember { mutableStateOf(false) }
    val templateLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ){uri->if(uri!=null)scope.launch{
        busy=true
        runCatching{vm.createCatalogTemplate(uri)}
            .onSuccess{notice="تم إنشاء القالب الفارغ. يمكنك تعبئته ثم رفعه من زر استيراد الكتالوج."}
            .onFailure{notice="فشل إنشاء القالب: ${it.message}"}
        busy=false
    }}
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)scope.launch {
            busy=true;preview=null;updateReference=false
            runCatching { vm.previewCatalog(uri) }
                .onSuccess { preview=it;notice="هذه معاينة فقط. لم تُحفظ أي بيانات بعد." }
                .onFailure { notice="تعذر قراءة كتالوج Excel: ${it.message}" }
            busy=false
        }
    }
    val exportLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ){uri->if(uri!=null)scope.launch{
        busy=true
        runCatching{vm.exportCatalog(uri)}
            .onSuccess{notice="تم تصدير كتالوج المنتجات والباركودات البديلة"}
            .onFailure{notice="فشل التصدير: ${it.message}"}
        busy=false
    }}
    val found=remember(data.catalog,searching){
        if(searching.isBlank()) data.catalog.take(50)
        else {
            val q=ArabicSearch.normalize(searching)
            data.catalog.asSequence().filter {
                listOf(it.barcode,it.nameAr,it.nameEn,it.brand,it.category,it.subcategory,it.searchKeywords,it.internalSku)
                    .any { value->ArabicSearch.normalize(value).contains(q) }
            }.take(50).toList()
        }
    }
    LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item { SectionHeader("كتالوج المنتجات والباركود", "ملفات تعريف منتجات مستقلة تمامًا عن التشغيلات والتواريخ والملاحظات") }
        item { ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("قالب Excel المرجعي",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("أنشئ قالبًا فارغًا من داخل التطبيق. تتضمن أوراقه ProductCatalog وBarcodeAliases ودليل الأعمدة وتعليمات الاستيراد.",style=MaterialTheme.typography.bodySmall)
                Button(onClick={templateLauncher.launch("Rukn-Product-Barcode-Catalog.xlsx")},enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.NoteAdd,null);Spacer(Modifier.width(6.dp));Text("إنشاء قالب Excel فارغ")
                }
                OutlinedButton(onClick={importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/zip","application/octet-stream"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.UploadFile,null);Spacer(Modifier.width(6.dp));Text("استيراد كتالوج المنتجات من Excel")
                }
                OutlinedButton(onClick={exportLauncher.launch("Rukn-Catalog-Export.xlsx")},enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(6.dp));Text("تصدير كتالوج المنتجات الحالي")
                }
                Text("استيراد الكتالوج يضيف بيانات تعريفية فقط ولا يُستعمل لاستعادة النسخة الكاملة. لا يحذف تواريخ أو تشغيلات أو مواقع أو ملاحظات.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(notice.isNotBlank())item{Text(notice,color=MaterialTheme.colorScheme.primary)}
        val p=preview
        if(p!=null){
            item { ElevatedCard{
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("معاينة الاستيراد قبل الحفظ",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(p.fileName.isNotBlank())Text("اسم الملف: ${p.fileName}",style=MaterialTheme.typography.labelSmall)
                    Text("منتجات جديدة: ${p.newCount} · استكمال حقول فارغة: ${p.fillCount} · دون تغيير: ${p.unchangedCount}")
                    Text("صفوف مرفوضة/متعارضة: ${p.rejectedCount} · باركودات بديلة جديدة: ${p.aliasNewCount}")
                    Text("ملاحظات/تحذيرات: ${p.issues.size}. عند الاعتماد، تُستورد السجلات الآمنة فقط؛ ولا تتغير الحقول المتعارضة.",style=MaterialTheme.typography.bodySmall)
                    if(p.issues.isNotEmpty()){
                        HorizontalDivider()
                        p.issues.take(12).forEach{issue->
                            Text("${if(issue.line>0) "صف ${issue.line}: " else ""}${issue.reason}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        }
                        if(p.issues.size>12)Text("و${p.issues.size-12} ملاحظات إضافية؛ صحح الملف وأعد رفعه عند الحاجة.",style=MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Row {
                        Checkbox(checked=updateReference,onCheckedChange={selected->
                            updateReference=selected
                            val policy=if(selected)CatalogConflictPolicy.UPDATE_REFERENCE_ONLY else CatalogConflictPolicy.KEEP_EXISTING
                            preview=CatalogImportEngine.plan(p.rows,data,policy).copy(fileName=p.fileName)
                        })
                        Text("السماح بتحديث القيم المتعارضة في الكتالوج المرجعي فقط، بعد مراجعتها. لا يمس السجلات والتواريخ اليدوية.",
                            style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=10.dp))
                    }
                    Button(onClick={scope.launch{
                        busy=true
                        runCatching{vm.importCatalog(p)}.onSuccess{result->
                            notice="نجح الاستيراد: ${result.newCount} جديد، ${result.fillCount} استكمال، ${result.aliasNewCount} باركود بديل، ${result.rejectedCount} مرفوض"
                            preview=null
                        }.onFailure{notice="فشل الاستيراد؛ لم تُعتمد تغييرات جزئية: ${it.message}"}
                        busy=false
                    }},enabled=!busy && p.newCount+p.fillCount+p.aliasNewCount>0,modifier=Modifier.fillMaxWidth()){
                        Text("اعتماد السجلات السليمة ودمجها")
                    }
                    TextButton(onClick={preview=null}){Text("إلغاء المعاينة")}
                }
            } }
        }
        item {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("الكتالوج المحلي",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("${data.catalog.size} منتج مرجعي · ${data.catalogAliases.size} باركود بديل",style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(searching,{searching=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                        leadingIcon={Icon(Icons.Outlined.Search,null)},label={Text("البحث في الكتالوج")},
                        placeholder={Text("باركود، اسم عربي أو إنجليزي، علامة، كلمة مفتاحية")})
                    Text("النتائج: ${found.size}${if(found.size==50) " (أول 50)" else ""}",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        if(found.isEmpty())item{Text("لا توجد سجلات مطابقة. أنشئ القالب واملأه ثم ارفعه.",style=MaterialTheme.typography.bodySmall)}
        items(found,key={it.id}){product->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(product.nameAr.ifBlank{product.nameEn.ifBlank{"منتج دون اسم"}},fontWeight=FontWeight.SemiBold)
                    Text("الباركود: ${product.barcode} · ${product.brand}",style=MaterialTheme.typography.bodySmall)
                    Text("المصدر: ${product.source} · الحجم: ${product.size.ifBlank{"غير محدد"}}",style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        item{Spacer(Modifier.height(18.dp))}
    }
}
