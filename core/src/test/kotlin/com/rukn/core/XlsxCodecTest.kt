package com.rukn.core

import com.rukn.expiry.data.XlsxCodec
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XlsxCodecTest {
    @Test fun roundtripArabicBarcodeNotesAndMonthPrecision(){
        val p=Product(barcode="00628900123456",nameAr="شاي الربيع — أسود",shelf="A-12",unitsPerCarton="24",notes="لا تحذف & لا تستبدل")
        val b=Batch(productId=p.id,expiryDate="2027-05",precision=DatePrecision.MONTH,quantity="3",unit="قطعة",notes="موقع الرف 5",verified=true)
        val original=AppData(products=listOf(p),batches=listOf(b),settings=mapOf("alertDays" to "7,30,90"))
        val bytes=ByteArrayOutputStream().also{XlsxCodec.write(original,it)}.toByteArray()
        val restored=XlsxCodec.read(bytes.inputStream())
        assertEquals(original.products,restored.products)
        assertEquals(original.batches,restored.batches)
        assertEquals(original.settings,restored.settings)
        assertTrue(bytes.size>1000)
    }
    @Test fun oldVersionOneBackupsOpenWithoutDataLoss(){
        val source=checkNotNull(javaClass.classLoader.getResourceAsStream("legacy_v1.xlsx"))
        val old=source.use{XlsxCodec.read(it)}
        assertEquals("00628900123",old.products.single().barcode)
        assertEquals("ملاحظات قديمة",old.products.single().notes)
        assertEquals("2027-01-01",old.batches.single().expiryDate)
        assertEquals("لا تحذفني",old.batches.single().notes)
        assertTrue(old.catalog.isEmpty())
        val upgraded=ByteArrayOutputStream().also{XlsxCodec.write(old,it)}.toByteArray()
        val restored=XlsxCodec.read(upgraded.inputStream())
        assertEquals(old.products,restored.products)
        assertEquals(old.batches,restored.batches)
    }
    @Test fun nonWorkbookRejected(){
        assertFailsWith<Exception>{XlsxCodec.read("not a real XLSX".byteInputStream())}
    }
}
