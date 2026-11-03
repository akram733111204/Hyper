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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rukn.core.*
import com.rukn.expiry.camera.BarcodeScannerDialog
import com.rukn.expiry.voice.VoiceSearchDialog
import kotlinx.coroutines.delay

@Composable fun SearchPage(data:AppData,onProduct:(String)->Unit,onCatalog:(CatalogProduct)->Unit){
    var query by remember{mutableStateOf("")}
    var settled by remember{mutableStateOf("")}
    var scanner by remember{mutableStateOf(false)}
    var voiceSearch by remember{mutableStateOf(false)}
    LaunchedEffect(query){delay(150);settled=query}
    val results=remember(data.products,settled){ArabicSearch.search(data.products,settled,150)}
    val batchesByProduct=remember(data.batches){data.batches.groupBy{it.productId}}
    val references=remember(data.catalog,data.products,settled){
        if(settled.isBlank())emptyList()
        else {
            val savedCodes=data.products.map{BarcodeRules.normalize(it.barcode)}.filter{it.isNotBlank()}.toSet()
            ArabicSearch.searchCatalog(data.catalog,settled,50)
                .filter{BarcodeRules.normalize(it.barcode) !in savedCodes}
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("اعثر على المنتج حتى لو أخطأت في كتابة اسمه",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
            placeholder={Text("مثل: شاي الربيع الأسود، الربيع، أو باركود")},
            leadingIcon={Icon(Icons.Outlined.Search,null)},
            trailingIcon={Row{
                IconButton(onClick={voiceSearch=true}){Icon(Icons.Outlined.Mic,"البحث بالصوت")}
                IconButton(onClick={scanner=true}){Icon(Icons.Outlined.QrCodeScanner,"المسح بالباركود")}
            }},
            shape=RoundedCornerShape(18.dp))
        Text("${results.size} منتج محفوظ · ${references.size} منتج مرجعي${if(results.size==150)" (أول 150)" else ""}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(results.isEmpty() && references.isEmpty()) Card{Text("لا توجد نتائج مطابقة. جرب جزءًا من الاسم أو العلامة أو مسح الباركود.",Modifier.fillMaxWidth().padding(20.dp))}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){
            if(results.isNotEmpty()) item { Text("المنتجات ذات سجلات الصلاحية",style=MaterialTheme.typography.titleSmall) }
            items(results,key={it.id}){p->
                val bs=batchesByProduct[p.id].orEmpty()
                ElevatedCard(Modifier.fillMaxWidth().clickable{onProduct(p.id)},shape=RoundedCornerShape(14.dp)){
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(Icons.Outlined.Inventory2,null,modifier=Modifier.size(27.dp),tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)){
                            Text(p.displayName,fontWeight=FontWeight.SemiBold)
                            Text(listOf(p.brand,p.size,p.category).filter{it.isNotBlank()}.joinToString(" • ").ifBlank{"تفاصيل المنتج غير مكتملة"},
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("الممر: ${p.aisle.ifBlank{"—"}} • الرف: ${p.shelf.ifBlank{"—"}} • التشغيلات: ${bs.size}",style=MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Outlined.ChevronLeft,null)
                    }
                }
            }
            if(references.isNotEmpty()) item { Text("من كتالوج الباركود — لم تُسجّل صلاحيتها بعد",style=MaterialTheme.typography.titleSmall) }
            items(references,key={"catalog_${it.id}"}){c->
                ElevatedCard(Modifier.fillMaxWidth().clickable{onCatalog(c)},shape=RoundedCornerShape(14.dp)){
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(Icons.Outlined.BookmarkAdd,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)){
                            Text(c.nameAr.ifBlank{c.nameEn.ifBlank{"منتج دون اسم"}},fontWeight=FontWeight.SemiBold)
                            Text(listOf(c.brand,c.size,c.barcode).filter{it.isNotBlank()}.joinToString(" • "),
                                style=MaterialTheme.typography.bodySmall)
                            Text("المصدر: ${c.source} · اضغط لبدء تسجيل تشغيلة",style=MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Outlined.ChevronLeft,null)
                    }
                }
            }
        }
    }
    if(scanner) BarcodeScannerDialog(onDismiss={scanner=false},onBarcode={query=Gs1Parser.parse(it)?.gtin?:it;scanner=false})
    if(voiceSearch) VoiceSearchDialog(settings=data.settings,onDismiss={voiceSearch=false},onSearch={query=it;voiceSearch=false})
}
