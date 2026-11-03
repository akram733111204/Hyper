package com.rukn.expiry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable fun ProductDetailPage(vm:AppViewModel,data:AppData,productId:String,onEdit:()->Unit,onNewBatch:()->Unit,onEditBatch:(String)->Unit){
    val p=data.products.find{it.id==productId}
    if(p==null){Box(Modifier.padding(25.dp)){Text("المنتج غير موجود")};return}
    val batches=data.batches.filter{it.productId==p.id}.sortedBy{ExpiryEngine.expiryDate(it)}
    val scope= rememberCoroutineScope()
    val totals=QuantityEngine.summarize(p,batches)
    var error by remember{mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            ElevatedCard(shape=RoundedCornerShape(18.dp)){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(p.displayName,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    if(p.nameEn.isNotBlank())Text(p.nameEn,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(p.brand.isNotBlank())Text("العلامة: ${p.brand}")
                    Text("الباركود: ${p.barcode.ifBlank{"غير متوفر"}}")
                    Text("الحجم: ${p.size.ifBlank{"غير محدد"}} • القسم: ${p.category.ifBlank{"غير محدد"}}")
                    Text("الممر: ${p.aisle.ifBlank{"—"}} • الرف: ${p.shelf.ifBlank{"—"}}")
                    if(p.unitsPerCarton.isNotBlank())Text("معامل الكرتون: ${p.unitsPerCarton} قطعة")
                    if(totals!=null){
                        Text("الكمية المدخلة: ${totals.cartons} كرتون + ${totals.pieces} حبة",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                        if(!totals.complete)Text("تنبيه: لم تُحتسب ${totals.excluded} تشغيلات لغياب الكمية أو الوحدة أو معامل التحويل. هذا مجموع جزئي.",style=MaterialTheme.typography.bodySmall)
                    }
                    if(p.notes.isNotBlank())Text("ملاحظات: ${p.notes}")
                    Text("مصدر المعلومات: ${p.source}",style=MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick=onNewBatch){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(4.dp));Text("إضافة تشغيلة")}
                        OutlinedButton(onClick=onEdit){Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(4.dp));Text("تعديل المنتج")}
                    }
                }
            }
        }
        item{SectionHeader("التشغيلات المسجلة","${batches.size} تشغيلة، ولكل منها تاريخ ومكان مستقل")}
        if(batches.isEmpty())item{Text("لم تُسجل تشغيلات لهذا المنتج بعد.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(batches,key={it.id}){b->
            ElevatedCard(shape=RoundedCornerShape(16.dp)){
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
                        Text(ExpiryEngine.displayStatus(b),fontWeight=FontWeight.Bold,
                            color=if(ExpiryEngine.expired(b))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(if(b.verified)"✓ تمت مراجعة التاريخ" else "لم يؤكد التاريخ",style=MaterialTheme.typography.labelSmall)
                    }
                    Text("الإنتاج: ${b.productionDate.ifBlank{"—"}}   |   الانتهاء: ${b.expiryDate.ifBlank{"—"}}")
                    Text("LOT: ${b.lot.ifBlank{"—"}}   |   الكمية: ${b.quantity.ifBlank{"غير مسجلة"}} ${b.unit}")
                    Text("الموقع: ${b.location.ifBlank{p.shelf.ifBlank{"غير محدد"}}}")
                    Text("الحالة: ${b.status.name} • آخر فحص: ${b.lastCheckedAt.take(10).ifBlank{"—"}}",style=MaterialTheme.typography.bodySmall)
                    if(b.notes.isNotBlank())Text("ملاحظات: ${b.notes}",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={onEditBatch(b.id)}){Icon(Icons.Outlined.Edit,null);Text("تعديل")}
                        TextButton(onClick={scope.launch{runCatching{vm.addTask(b.id,"فحص التشغيلة: ${p.displayName}")}.onFailure{error=it.message.orEmpty()}}}){
                            Icon(Icons.Outlined.Assignment,null);Text("مهمة فحص")
                        }
                    }
                }
            }
        }
        if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
    }
}
