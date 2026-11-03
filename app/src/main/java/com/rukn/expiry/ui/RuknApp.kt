package com.rukn.expiry.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.expiry.AppViewModel
import com.rukn.expiry.R

private data class Tab(val id:String,val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val tabs=listOf(Tab("dashboard","الرئيسية",Icons.Outlined.Dashboard),
    Tab("search","البحث",Icons.Outlined.Search),Tab("add","إضافة",Icons.Outlined.AddCircleOutline),
    Tab("alerts","التنبيهات",Icons.Outlined.NotificationsActive),Tab("reports","التقارير",Icons.Outlined.Assessment))
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RuknApp(vm:AppViewModel){
    val data by vm.data.collectAsState()
    val busy by vm.busy.collectAsState()
    val notice by vm.notice.collectAsState()
    var screen by remember {mutableStateOf("dashboard")}
    var selectedProduct by remember{mutableStateOf("")}
    var selectedBatch by remember{mutableStateOf("")}
    var selectedCatalog by remember{mutableStateOf("")}
    var draftKey by remember{mutableIntStateOf(0)}
    val snack=remember{SnackbarHostState()}
    LaunchedEffect(notice){if(notice.isNotBlank()){snack.showSnackbar(notice);vm.dismissNotice()}}
    fun nav(new:String){screen=new}
    Scaffold(
        topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Image(painterResource(R.drawable.rukn_logo),null,modifier=Modifier.size(44.dp))
            Column {Text("الركن | رقابة الصلاحية",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                Text(when(screen){"dashboard"->"نظرة شاملة";"search"->"بحث ذكي بالأسماء والباركود";
                    "add"->"إضافة منتج أو تشغيلة";"detail"->"ملف المنتج";"alerts"->"مراقبة المواعيد";
                    "reports"->"تحليل وتصدير";"settings"->"إعدادات النظام";"catalog"->"كتالوج الباركود";"tasks"->"المهام الميدانية";else->"المتابعة"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }},actions={
            if(screen=="detail"||screen=="settings"||screen=="catalog"||screen=="tasks") IconButton(onClick={nav("dashboard")}){Icon(Icons.Outlined.Home,"الرئيسية")}
            else IconButton(onClick={nav("settings")}){Icon(Icons.Outlined.Settings,"الإعدادات")}
        })},
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
            tabs.forEach{tab->NavigationBarItem(selected=screen==tab.id,onClick={
                if(tab.id=="add"){selectedProduct="";selectedBatch="";selectedCatalog="";draftKey++}
                nav(tab.id)
            },icon={Icon(tab.icon,tab.title)},label={Text(tab.title,maxLines=1)})}
        }},snackbarHost={SnackbarHost(snack)}
    ){padding->
        Box(Modifier.fillMaxSize().padding(padding)){
            when(screen){
                "dashboard"->DashboardPage(data,onGoSearch={nav("search")},onGoAlerts={nav("alerts")},onGoTasks={nav("tasks")},onAdd={selectedProduct="";selectedBatch="";draftKey++;nav("add")},onProduct={selectedProduct=it;nav("detail")})
                "search"->SearchPage(data,onProduct={selectedProduct=it;nav("detail")},onCatalog={
                    selectedCatalog=it.id;selectedProduct="";selectedBatch="";draftKey++;nav("add")
                })
                "add"->key(draftKey,selectedProduct,selectedBatch,selectedCatalog){EditorPage(vm,data,selectedProduct,selectedBatch,
                    initialCatalog=data.catalog.firstOrNull{it.id==selectedCatalog},onSaved={selectedProduct=it;selectedBatch="";selectedCatalog="";nav("detail")})}
                "detail"->ProductDetailPage(vm,data,selectedProduct,onEdit={selectedBatch="";draftKey++;nav("add")},onNewBatch={selectedBatch="";draftKey++;nav("add")},onEditBatch={selectedBatch=it;draftKey++;nav("add")})
                "alerts"->AlertsPage(data,onProduct={selectedProduct=it;nav("detail")})
                "reports"->ReportsPage(vm,data,onGoTasks={nav("tasks")})
                "tasks"->TasksPage(vm,data,onProduct={selectedProduct=it;nav("detail")})
                "settings"->SettingsPage(vm,data,onTasks={nav("tasks")},onCatalog={nav("catalog")})
                "catalog"->CatalogPage(vm,data)
            }
            if(busy) CircularProgressIndicator(modifier=Modifier.align(Alignment.Center))
        }
    }
}
