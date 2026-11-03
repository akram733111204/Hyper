package com.rukn.expiry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.core.*
import com.rukn.expiry.AppViewModel
import kotlinx.coroutines.launch

@Composable fun ReportsPage(vm:AppViewModel,data:AppData,onGoTasks:()->Unit){
    val scope=rememberCoroutineScope()
    var report by remember{mutableStateOf("due")}
    var days by remember{mutableIntStateOf(30)}
    var info by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("")}
    var location by remember{mutableStateOf("")}
    var verifiedOnly by remember{mutableStateOf(false)}
    val counts=remember(data){ExpiryEngine.alertCounts(data)}
    val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null)scope.launch{runCatching{vm.exportReport(uri,report,days,category,location,verifiedOnly)}.onSuccess{info="تم تصدير تقرير CSV"}.onFailure{info="خطأ: ${it.message}"}}
    }
    val xlsxLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){uri->
        if(uri!=null)scope.launch{runCatching{vm.exportWorkbook(uri)}.onSuccess{info="تم تصدير قاعدة Excel كاملة"}.onFailure{info="خطأ: ${it.message}"}}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        SectionHeader("التقارير والتحليلات","تقرير مستقل أو نسخة Excel كاملة")
        ElevatedCard{
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text("أنشئ تقريرك",fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    FilterChip(report=="all",onClick={report="all"},label={Text("الكل")})
                    FilterChip(report=="due",onClick={report="due"},label={Text("قريب")})
                    FilterChip(report=="expired",onClick={report="expired"},label={Text("منتهي")})
                    FilterChip(report=="unknown",onClick={report="unknown"},label={Text("ناقص")})
                }
                if(report=="due"){
                    Text("حتى كم يومًا من الآن؟",style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        listOf(7,30,60,90,120).forEach{n->FilterChip(days==n,onClick={days=n},modifier=Modifier.weight(1f),label={Text("$n")})}
                    }
                }
                OutlinedTextField(category,{category=it},modifier=Modifier.fillMaxWidth(),label={Text("القسم: تصفية اختيارية")})
                OutlinedTextField(location,{location=it},modifier=Modifier.fillMaxWidth(),label={Text("الموقع / الرف: تصفية اختيارية")})
                Row {Checkbox(checked=verifiedOnly,onCheckedChange={verifiedOnly=it});Text("التواريخ التي تمت مراجعتها فقط",modifier=Modifier.padding(top=11.dp))}
                Button(onClick={csvLauncher.launch("Rukn-Expiry-report.csv")},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.FileDownload,null);Spacer(Modifier.width(6.dp));Text("تصدير التقرير المحدد CSV / Excel")
                }
                OutlinedButton(onClick={xlsxLauncher.launch("Rukn-Expiry-data.xlsx")},modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Outlined.TableView,null);Spacer(Modifier.width(6.dp));Text("تصدير جميع القوائم إلى ملف Excel XLSX")
                }
                if(info.isNotBlank())Text(info,color=MaterialTheme.colorScheme.primary)
            }
        }
        SectionHeader("توزيع المراقبة","الأرقام تراكمية ولا ينبغي جمعها معًا")
        val chart=listOf(7,30,60,90,120).map{counts[it.toString()]?:0}
        ElevatedCard{
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                val max=chart.maxOrNull()?.coerceAtLeast(1)?:1
                Canvas(Modifier.fillMaxWidth().height(150.dp)){
                    val gap=size.width/6f;val width=gap*0.55f
                    chart.forEachIndexed{ix,n->
                        val height=(size.height-12f)*n/max.toFloat()
                        drawRoundRect(color=if(ix==0)Color(0xFFC9534D) else Color(0xFF16846B),
                            topLeft=Offset(gap*(ix+0.7f),size.height-height),size=Size(width,height),
                            cornerRadius=androidx.compose.ui.geometry.CornerRadius(8f,8f))
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("أسبوع","شهر","شهران","3 أشهر","4 أشهر").forEach{Text(it,style=MaterialTheme.typography.labelSmall)}}
                Text("المنتهية: ${counts["expired"]} • بلا تاريخ: ${counts["unknown"]} • غير مؤكد: ${counts["unverified"]}",style=MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick=onGoTasks,modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Outlined.Assignment,null);Spacer(Modifier.width(8.dp));Text("مراجعة المهام الميدانية")
        }
        Spacer(Modifier.height(16.dp))
    }
}
