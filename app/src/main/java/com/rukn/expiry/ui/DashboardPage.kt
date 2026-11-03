package com.rukn.expiry.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.core.*
import java.time.LocalDate

@Composable internal fun MetricCard(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary,onClick:()->Unit={}){
    ElevatedCard(modifier=modifier.clickable(onClick=onClick),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,null,tint=color,modifier=Modifier.size(23.dp))
            Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun SectionHeader(title:String,subtitle:String="",action:String="",onAction:()->Unit={}){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Column{Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(action.isNotBlank())TextButton(onClick=onAction){Text(action)}
    }
}
@Composable fun DashboardPage(data:AppData,onGoSearch:()->Unit,onGoAlerts:()->Unit,onGoTasks:()->Unit,onAdd:()->Unit,onProduct:(String)->Unit){
    val counts=remember(data){ExpiryEngine.alertCounts(data)}
    val urgent=remember(data){data.batches.filter{ExpiryEngine.expired(it)||ExpiryEngine.dueWithin(it,30)}.sortedBy{ExpiryEngine.daysLeft(it)}.take(8)}
    val productMap=remember(data.products){data.products.associateBy{it.id}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(17.dp)){
        item{
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),shape=RoundedCornerShape(22.dp)){
                Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Icon(Icons.Outlined.Security,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
                    Text("كل تواريخ الصلاحية تحت السيطرة",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("راقب التشغيلات، اعثر على أي منتج، وابدأ جولة الفحص من مكان واحد.",style=MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick=onAdd){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(5.dp));Text("إضافة منتج")}
                        OutlinedButton(onClick=onGoSearch){Text("بحث ذكي")}
                    }
                }
            }
        }
        item{SectionHeader("حالة البيانات","أرقام من السجلات المحفوظة — دون افتراض مخزون غير مسجل")}
        item{
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    MetricCard("المنتجات المسجلة",data.products.size.toString(),Icons.Outlined.Inventory2,Modifier.weight(1f),onClick=onGoSearch)
                    MetricCard("التشغيلات النشطة",data.batches.count{it.status==BatchStatus.ACTIVE}.toString(),Icons.Outlined.Layers,Modifier.weight(1f),onClick=onGoAlerts)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    MetricCard("انتهت صلاحيتها",counts["expired"].toString(),Icons.Outlined.ReportProblem,Modifier.weight(1f),color=MaterialTheme.colorScheme.error,onClick=onGoAlerts)
                    MetricCard("تنتهي خلال 30 يومًا",counts["30"].toString(),Icons.Outlined.EventBusy,Modifier.weight(1f),onClick=onGoAlerts)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    MetricCard("تواريخ غير معروفة",counts["unknown"].toString(),Icons.Outlined.HelpOutline,Modifier.weight(1f),onClick=onGoAlerts)
                    MetricCard("مهام مفتوحة",data.tasks.count{it.status==TaskStatus.OPEN}.toString(),Icons.Outlined.Assignment,Modifier.weight(1f),onClick=onGoTasks)
                }
            }
        }
        item{SectionHeader("التنبيهات العاجلة","الأقرب انتهاءً أولًا", "الكل",onGoAlerts)}
        if(urgent.isEmpty())item{
            Card{Text(if(data.products.isEmpty())"لا توجد منتجات بعد. ابدأ بمسح الباركود لإضافة أول منتج."
            else "لا توجد تشغيلات مسجلة تستدعي تنبيهًا خلال 30 يومًا.",modifier=Modifier.fillMaxWidth().padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        items(urgent.size){i->
            val b=urgent[i];val p=productMap[b.productId]
            Card(Modifier.fillMaxWidth().clickable{onProduct(b.productId)}){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(Icons.Outlined.Schedule,null,tint=if(ExpiryEngine.expired(b))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){
                        Text(p?.displayName?:"منتج",fontWeight=FontWeight.SemiBold)
                        Text("${b.expiryDate} • ${b.location.ifBlank{p?.shelf?:"الموقع غير محدد"}}",style=MaterialTheme.typography.bodySmall)
                    }
                    Text(ExpiryEngine.displayStatus(b),color=if(ExpiryEngine.expired(b))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        item{Spacer(Modifier.height(10.dp))}
    }
}
