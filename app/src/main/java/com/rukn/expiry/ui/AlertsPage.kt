package com.rukn.expiry.ui

import androidx.compose.foundation.clickable
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

@Composable fun AlertsPage(data:AppData,onProduct:(String)->Unit){
    var mode by remember{mutableStateOf("due")}
    var days by remember{mutableIntStateOf(30)}
    var cumulative by remember{mutableStateOf(true)}
    var category by remember{mutableStateOf("")}
    val thresholds=remember(data.settings["alertDays"]){
        (data.settings["alertDays"]?:"7,30,60,90,120").split(',').mapNotNull{it.toIntOrNull()}.filter{it in 1..730}.sorted().ifEmpty{listOf(7,30,60,90,120)}
    }
    val dataRows=remember(data,mode,days,cumulative,category){
        data.batches.filter{b->
            val p=data.products.firstOrNull{it.id==b.productId}
            val categoryMatch=category.isBlank()||p?.category?.contains(category,ignoreCase=true)==true
            categoryMatch && when(mode){
                "expired"->ExpiryEngine.expired(b)
                "unknown"->ExpiryEngine.isActive(b) && ExpiryEngine.expiryDate(b)==null
                "unverified"->ExpiryEngine.isActive(b) && !b.verified
                else->{val left=ExpiryEngine.daysLeft(b)
                    ExpiryEngine.isActive(b) && left!=null && if(cumulative)left in 0L..days.toLong()
                    else left> (thresholds.lastOrNull{it<days}?:0) && left<=days
                }
            }
        }.sortedWith(compareBy(nullsLast()){ExpiryEngine.daysLeft(it)})
    }
    val productMap=remember(data.products){data.products.associateBy{it.id}}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        SectionHeader("مركز تنبيهات الصلاحية","المنتهية منفصلة عن الفترات القادمة، لتجنب تكرار العد")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(mode=="due",onClick={mode="due"},label={Text("قادمة")})
            FilterChip(mode=="expired",onClick={mode="expired"},label={Text("منتهية")})
            FilterChip(mode=="unknown",onClick={mode="unknown"},label={Text("بدون تاريخ")})
        }
        if(mode=="due"){
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()){
                thresholds.take(8).forEach{n->FilterChip(days==n,onClick={days=n},modifier=Modifier.weight(1f),label={Text("$n",maxLines=1,style=MaterialTheme.typography.labelSmall)})}
            }
            Row {Checkbox(checked=cumulative,onCheckedChange={cumulative=it});Text("حساب تراكمي (0–$days يومًا)، أو شريحة منفصلة",modifier=Modifier.padding(top=11.dp),style=MaterialTheme.typography.bodySmall)}
        }
        OutlinedTextField(category,{category=it},label={Text("تصفية بحسب القسم (اختياري)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("النتائج: ${dataRows.size} تشغيلة",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
        if(dataRows.isEmpty())Card{Text("لا توجد تشغيلات تطابق الفلاتر.",Modifier.fillMaxWidth().padding(18.dp))}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(dataRows,key={it.id}){b->
                val p=productMap[b.productId]
                ElevatedCard(Modifier.fillMaxWidth().clickable{onProduct(b.productId)},shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            Text(p?.displayName.orEmpty(),fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            Text(ExpiryEngine.displayStatus(b),color=if(ExpiryEngine.expired(b))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                        }
                        Text("الانتهاء: ${b.expiryDate.ifBlank{"غير محدد"}} • المتبقي: ${ExpiryEngine.daysLeft(b)?.let{"$it يوم"}?:"مجهول"}")
                        Text("الموقع: ${b.location.ifBlank{p?.shelf?:"غير محدد"}} • الكمية: ${b.quantity.ifBlank{"غير مسجلة"}} ${b.unit}",style=MaterialTheme.typography.bodySmall)
                        Text(if(b.verified)"تمت مراجعة التاريخ" else "يحتاج إلى تأكيد تاريخ",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
