package com.rukn.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.LocalDate

class SearchAndExpiryTest {
    private val products=listOf(
        Product(nameAr="شاي الربيع الأسود",brand="الربيع",barcode="6281234567890",shelf="A12"),
        Product(nameAr="شاي أخضر نعناع",brand="الربيع",shelf="B02"),
        Product(nameAr="قهوة سريعة التحضير",brand="نستله")
    )
    @Test fun normalizesArabicSearch(){
        assertEquals(2,ArabicSearch.search(products,"شاي").size)
        assertEquals(2,ArabicSearch.search(products,"الربيع").size)
        assertEquals("شاي الربيع الأسود",ArabicSearch.search(products,"شاى الربيع الاسود").first().nameAr)
        assertEquals("شاي الربيع الأسود",ArabicSearch.search(products,"شاي الربيع الاسود").first().nameAr)
    }
    @Test fun gs1BarcodeContainsBatchAndExpiry(){
        val parsed=Gs1Parser.parse("(01)06281234567895(11)261001(17)271225(10)BATCH42")!!
        assertEquals("6281234567895",parsed.gtin)
        assertEquals("2026-10-01",parsed.production)
        assertEquals("2027-12-25",parsed.expiry)
        assertEquals("BATCH42",parsed.lot)
    }
    @Test fun quantityConversionOnlyWhenUnitsKnown(){
        val p=Product(unitsPerCarton="24")
        val batches=listOf(Batch(productId=p.id,quantity="2",unit="كرتون"),Batch(productId=p.id,quantity="5",unit="حبة"))
        val result=QuantityEngine.summarize(p,batches)!!
        assertEquals("2",result.cartons)
        assertEquals("5",result.pieces)
        assertTrue(result.complete)
    }
    @Test fun barcodeExact(){assertEquals(products.first(), ArabicSearch.search(products,"6281234567890").first())}
    @Test fun expiryCountsAndUnknown(){
        val b=Batch(productId="a",expiryDate="2026-11-09")
        assertTrue(ExpiryEngine.dueWithin(b,7,LocalDate.of(2026,11,3)))
        assertEquals("2027-02-28",ExpiryEngine.expiryDate(Batch(productId="a",expiryDate="2027-02",precision=DatePrecision.MONTH)).toString())
        assertEquals(null,ExpiryEngine.expiryDate(Batch(productId="a",expiryDate="")))
    }
    @Test fun parsesDatesAndArabicDigits(){
        val v=DateTextParser.parse("PROD ٢٠٢٦/١٠/١٥\nEXP: 2027-12-25\nLOT: AB123")
        assertEquals("2026-10-15",v.production?.iso)
        assertEquals("2027-12-25",v.expiry?.iso)
        assertEquals("AB123",v.lot)
        assertEquals("2027-12",DateTextParser.parse("BBE 12/2027").expiry?.iso)
        assertTrue(DateTextParser.parse("EXP 11/12/2027").expiry?.ambiguous == true)
        val sameLine=DateTextParser.parse("MFG 2026/10/15 EXP 2027/10/15")
        assertEquals("2026-10-15",sameLine.production?.iso)
        assertEquals("2027-10-15",sameLine.expiry?.iso)
    }
}
