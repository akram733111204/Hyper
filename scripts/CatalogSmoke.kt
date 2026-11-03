import com.rukn.core.*
import com.rukn.expiry.data.XlsxCodec
import java.io.ByteArrayOutputStream
import java.io.File

fun main(){
    val template=File("app/src/main/assets/rukn_catalog_template.xlsx")
    check(template.isFile)
    val empty=XlsxCodec.readCatalog(template.inputStream())
    check(empty.products.isEmpty())
    check(empty.aliases.isEmpty())
    val barcode="00628123456789"
    val catalog=CatalogProduct(barcode=barcode,nameAr="شاي الربيع الأسود",nameEn="Al Rabie Black Tea",
        brand="الربيع",size="400 g",source="supplier")
    val alias=CatalogBarcodeAlias(barcode,"6281234567890","EAN-13")
    val exported=ByteArrayOutputStream().also {
        XlsxCodec.writeCatalog(AppData(catalog=listOf(catalog),catalogAliases=listOf(alias)),it)
    }.toByteArray()
    val incoming=XlsxCodec.readCatalog(exported.inputStream())
    check(incoming.products.single()["barcode"]==barcode)
    check(incoming.aliases.single()["alternateBarcode"]=="6281234567890")
    val product=Product(barcode=barcode,nameAr="شاي الربيع الأسود",notes="لا تحذف الملاحظات",shelf="B12")
    val batch=Batch(productId=product.id,productionDate="2026-09-01",expiryDate="2027-09-01",notes="ثابت")
    val initial=AppData(products=listOf(product),batches=listOf(batch))
    val plan=CatalogImportEngine.plan(incoming,initial)
    check(plan.newCount==1 && plan.aliasNewCount==1)
    val imported=CatalogImportEngine.merge(incoming,initial).data
    check(imported.products==initial.products && imported.batches==initial.batches)
    check(CatalogImportEngine.resolve("6281234567890",imported)?.barcode==barcode)
    val second=CatalogImportEngine.merge(incoming,imported)
    check(second.preview.newCount==0 && second.preview.aliasNewCount==0)
    check(second.data.catalog.size==1 && second.data.catalogAliases.size==1)
    val conflict=CatalogImportEngine.merge(CatalogRows(listOf(mapOf("barcode" to barcode,"nameAr" to "مختلف")),emptyList()),imported)
    check(conflict.preview.rejectedCount==1)
    check(conflict.data.catalog.single().nameAr=="شاي الربيع الأسود")
    val forced=CatalogImportEngine.merge(CatalogRows(listOf(mapOf("barcode" to barcode,"nameAr" to "اسم بديل")),emptyList()),imported,CatalogConflictPolicy.UPDATE_REFERENCE_ONLY)
    check(forced.preview.rejectedCount==0 && forced.data.catalog.single().nameAr=="اسم بديل")
    check(ArabicSearch.searchCatalog(imported.catalog,"شاى الربيع").isNotEmpty())
    val duplicate=CatalogImportEngine.merge(CatalogRows(listOf(mapOf("barcode" to barcode),mapOf("barcode" to barcode)),emptyList()),initial)
    check(duplicate.preview.rejectedCount==1 && duplicate.data.catalog.size==1)
    val full=ByteArrayOutputStream().also{XlsxCodec.write(imported,it)}.toByteArray()
    val reread=XlsxCodec.read(full.inputStream())
    check(reread.products.single().notes=="لا تحذف الملاحظات")
    check(reread.batches.single().expiryDate=="2027-09-01")
    check(reread.catalog.single().barcode==barcode && reread.catalogAliases.single().alternateBarcode=="6281234567890")
    val legacy=File("core/src/test/resources/legacy_v1.xlsx").inputStream().use{XlsxCodec.read(it)}
    check(legacy.products.single().notes=="ملاحظات قديمة")
    check(legacy.batches.single().expiryDate=="2027-01-01")
    println("Catalog smoke passed; template bytes=${template.length()}, exported bytes=${exported.size}; v1 upgrade OK")
}
