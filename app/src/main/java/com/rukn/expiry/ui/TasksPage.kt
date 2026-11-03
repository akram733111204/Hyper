package com.rukn.expiry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rukn.core.*
import com.rukn.expiry.AppViewModel
import kotlinx.coroutines.launch

@Composable fun TasksPage(vm:AppViewModel,data:AppData,onProduct:(String)->Unit){
    val scope=rememberCoroutineScope()
    var openOnly by remember{mutableStateOf(true)}
    var message by remember{mutableStateOf("")}
    val batchMap=data.batches.associateBy{it.id}
    val tasks=data.tasks.filter{!openOnly || it.status==TaskStatus.OPEN}.sortedByDescending{it.createdAt}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        SectionHeader("المهام الميدانية","سجل الفحص والمتابعة وإغلاق الإجراءات")
        Row {Switch(checked=openOnly,onCheckedChange={openOnly=it});Text("المهام المفتوحة فقط",modifier=Modifier.padding(10.dp))}
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
        if(tasks.isEmpty()) Text("لا توجد مهام تطابق الاختيار.")
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(tasks,key={it.id}){t->ElevatedCard{
                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(t.title,style=MaterialTheme.typography.titleMedium)
                    Text("الحالة: ${if(t.status==TaskStatus.OPEN)"مفتوحة" else "منجزة"} • أُنشئت: ${t.createdAt.take(10)}")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        batchMap[t.batchId]?.let{b->TextButton(onClick={onProduct(b.productId)}){Text("تفاصيل المنتج")}}
                        if(t.status==TaskStatus.OPEN) Button(onClick={scope.launch{runCatching{vm.setTaskDone(t.id)}.onFailure{message=it.message.orEmpty()}}}){
                            Icon(Icons.Outlined.Check,null);Text("تم التنفيذ")
                        }
                    }
                }
            }}
        }
    }
}
