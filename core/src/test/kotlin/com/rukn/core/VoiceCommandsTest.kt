package com.rukn.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VoiceCommandsTest {
    @Test fun speaksMultipleFieldsInOneSentence(){
        val r=VoiceCommandEngine.interpret("الإنتاج يوم الثالث عشر من شهر تسعة سنة ستة وعشرين والانتهاء يوم خمسة وعشرين ديسمبر ثمانية وعشرين والكمية ست كراتين واربع حبات والممر الثاني الرف خمسة")
        assertEquals("2026-09-13",r.commands.first{it.field==VoiceField.PRODUCTION_DATE}.value)
        assertEquals("2028-12-25",r.commands.first{it.field==VoiceField.EXPIRY_DATE}.value)
        assertEquals("6 كرتون + 4 حبة",r.commands.first{it.field==VoiceField.QUANTITY}.value)
        assertEquals("مختلطة",r.commands.first{it.field==VoiceField.UNIT}.value)
        assertTrue(r.warnings.any{it.contains("مختصرة")})
    }
    @Test fun fullExplicitDatesAndMonthPrecision(){
        val exact=VoiceCommandEngine.interpret("تاريخ الإنتاج يوم 13 شهر 9 سنة 2026 والانتهاء يوم 15 شهر 12 عام 2028")
        assertEquals("2026-09-13",exact.commands.first{it.field==VoiceField.PRODUCTION_DATE}.value)
        assertEquals("2028-12-15",exact.commands.first{it.field==VoiceField.EXPIRY_DATE}.value)
        val monthly=VoiceCommandEngine.interpret("تاريخ الانتهاء شهر سبتمبر سنة ثمانية وعشرين")
        assertEquals("2028-09",monthly.commands.first{it.field==VoiceField.EXPIRY_DATE}.value)
    }
    @Test fun correctOnlyMonthWithoutOverwritingOtherFields(){
        val base=mapOf(VoiceField.EXPIRY_DATE to "2028-12-15",VoiceField.SHELF to "B12")
        val result=VoiceCommandEngine.apply(base,VoiceCommandEngine.interpret("عدل شهر الانتهاء الى نوفمبر"))
        assertEquals("2028-11-15",result.values[VoiceField.EXPIRY_DATE])
        assertEquals("B12",result.values[VoiceField.SHELF])
        assertEquals(setOf(VoiceField.EXPIRY_DATE),result.changed)
    }
    @Test fun cannotCorrectDateToImpossibleDay(){
        val base=mapOf(VoiceField.EXPIRY_DATE to "2028-02-29")
        val result=VoiceCommandEngine.apply(base,VoiceCommandEngine.interpret("غير سنة الانتهاء الى 2027"))
        assertEquals("2028-02-29",result.values[VoiceField.EXPIRY_DATE])
        assertTrue(result.warnings.any{it.contains("غير موجود")})
    }
    @Test fun preservesMissingAndUnknownAndClearsExplicitly(){
        assertTrue(VoiceCommandEngine.interpret("التاريخ مكتوب بشكل غير واضح").commands.isEmpty())
        val result=VoiceCommandEngine.apply(mapOf(VoiceField.BATCH_NOTES to "متضرر"),VoiceCommandEngine.interpret("احذف الملاحظات"))
        assertEquals("",result.values[VoiceField.BATCH_NOTES])
    }
    @Test fun numericAddOnlyAndRejectMixedArithmetic(){
        val base=mapOf(VoiceField.QUANTITY to "10",VoiceField.UNIT to "حبة")
        val r=VoiceCommandEngine.apply(base,VoiceCommandEngine.interpret("زود الكمية خمس حبات"))
        assertEquals("15",r.values[VoiceField.QUANTITY])
        val wrongUnit=VoiceCommandEngine.apply(mapOf(VoiceField.QUANTITY to "10",VoiceField.UNIT to "كرتون"),VoiceCommandEngine.interpret("زود الكمية خمس حبات"))
        assertEquals("10",wrongUnit.values[VoiceField.QUANTITY])
        assertEquals("كرتون",wrongUnit.values[VoiceField.UNIT])
        assertTrue(wrongUnit.warnings.any{it.contains("مختلفة")})
        val mix=VoiceCommandEngine.interpret("زود الكمية ست كراتين واربع حبات")
        assertTrue(mix.commands.isEmpty())
    }
    @Test fun mixedQuantitiesRemainCountableOnlyWithConfirmedConversion(){
        val product=Product(nameAr="شاي",unitsPerCarton="24")
        val batch=Batch(productId=product.id,quantity="6 كرتون + 4 حبة",unit="مختلطة")
        val summary=QuantityEngine.summarize(product,listOf(batch))
        assertEquals("6",summary?.cartons)
        assertEquals("4",summary?.pieces)
        assertEquals(true,summary?.complete)
        assertEquals(null,QuantityEngine.summarize(product.copy(unitsPerCarton=""),listOf(batch)))
    }
    @Test fun validatesUntrustedCloudCommand(){
        val invalid=VoiceResult(listOf(
            VoiceCommand(VoiceField.EXPIRY_DATE,VoiceAction.SET,"2027-02-31"),
            VoiceCommand(VoiceField.SHELF,VoiceAction.SET,"A-4")
        ),emptyList(),"cloud")
        val result=VoiceCommandEngine.apply(emptyMap(),invalid)
        assertFalse(result.values.containsKey(VoiceField.EXPIRY_DATE))
        assertEquals("A-4",result.values[VoiceField.SHELF])
    }
}
