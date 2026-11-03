package com.rukn.core

import com.rukn.expiry.data.XlsxCodec
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogImportTest {
    private val barcode="06280000001235"
    private fun file(): CatalogRows {
        val reference=CatalogProduct(barcode=barcode,nameAr="شاي الربيع الأسود",brand="الربيع",size="400 جرام")
        val alias=CatalogBarcodeAlias(barcode,"06280000006789")
        val bytes=ByteArrayOutputStream().also {
            XlsxCodec.writeCatalog(AppData(catalog=listOf(reference),catalogAliases=listOf(alias)),it)
        }.toByteArray()
        return XlsxCodec.readCatalog(bytes.inputStream())
    }
    @Test fun catalogImportIsSeparateAndIdempotent(){
        val p=Product(barcode=barcode,nameAr="الاسم اليدوي",notes="لا تحذفني",shelf="A12")
        val b=Batch(productId=p.id,expiryDate="2027-04-15",notes="ملاحظة صلاحية")
        val original=AppData(products=listOf(p),batches=listOf(b))
        val first=CatalogImportEngine.merge(file(),original)
        assertEquals(1,first.preview.newCount)
        assertEquals(1,first.preview.aliasNewCount)
        assertEquals(original.products,first.data.products)
        assertEquals(original.batches,first.data.batches)
        assertEquals(barcode,CatalogImportEngine.resolve("06280000006789",first.data)?.barcode)
        val repeated=CatalogImportEngine.merge(file(),first.data)
        assertEquals(0,repeated.preview.newCount)
        assertEquals(0,repeated.preview.aliasNewCount)
        assertEquals(1,repeated.data.catalog.size)
    }
    @Test fun conflictsAreNeverOverwrittenUnlessReferenceOnlyApproved(){
        val base=CatalogImportEngine.merge(file(),AppData()).data
        val incoming=CatalogRows(listOf(mapOf("barcode" to barcode,"nameAr" to "اسم آخر")),emptyList())
        val safe=CatalogImportEngine.merge(incoming,base)
        assertEquals(1,safe.preview.rejectedCount)
        assertEquals("شاي الربيع الأسود",safe.data.catalog.single().nameAr)
        val accepted=CatalogImportEngine.merge(incoming,base,CatalogConflictPolicy.UPDATE_REFERENCE_ONLY)
        assertEquals("اسم آخر",accepted.data.catalog.single().nameAr)
    }
    @Test fun missingBarcodeAndDuplicateAreFlagged(){
        val rows=CatalogRows(listOf(mapOf("barcode" to barcode),mapOf("barcode" to barcode),
            mapOf("nameAr" to "بدون باركود")),emptyList())
        val outcome=CatalogImportEngine.merge(rows,AppData())
        assertEquals(1,outcome.preview.newCount)
        assertEquals(2,outcome.preview.rejectedCount)
        assertTrue(outcome.preview.issues.size>=2)
    }
    @Test fun catalogSearchArabicIsFuzzy(){
        val results=ArabicSearch.searchCatalog(listOf(CatalogProduct(barcode=barcode,nameAr="شاي الربيع الأسود",searchKeywords="شاي ربيع")),"شاى الربيع")
        assertEquals(1,results.size)
    }
}
