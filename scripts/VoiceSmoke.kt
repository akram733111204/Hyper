import com.rukn.core.*
fun main(){
    fun has(text:String,field:VoiceField,value:String){
        val r=VoiceCommandEngine.interpret(text)
        check(r.commands.any{it.field==field && it.value==value}){"Voice parse failed field=$field: $r"}
    }
    has("تاريخ الانتاج يوم 13 شهر 9 سنة 2026",VoiceField.PRODUCTION_DATE,"2026-09-13")
    has("الانتهاء يوم الخامس عشر من شهر ديسمبر سنه 28",VoiceField.EXPIRY_DATE,"2028-12-15")
    has("الكمية ست كراتين واربع حبات",VoiceField.QUANTITY,"6 كرتون + 4 حبة")
    val patch=VoiceCommandEngine.interpret("عدل شهر الانتهاء الى نوفمبر")
    check(VoiceCommandEngine.apply(mapOf(VoiceField.EXPIRY_DATE to "2028-12-25"),patch).values[VoiceField.EXPIRY_DATE]=="2028-11-25")
    val clear=VoiceCommandEngine.interpret("احذف الملاحظات")
    check(clear.commands.any{it.action==VoiceAction.CLEAR})
    val wrongUnit=VoiceCommandEngine.apply(mapOf(VoiceField.QUANTITY to "10",VoiceField.UNIT to "كرتون"),VoiceCommandEngine.interpret("زود الكمية خمس حبات"))
    check(wrongUnit.values[VoiceField.QUANTITY]=="10" && wrongUnit.values[VoiceField.UNIT]=="كرتون")
    val product=Product(unitsPerCarton="24")
    val mixed=QuantityEngine.summarize(product,listOf(Batch(productId=product.id,quantity="6 كرتون + 4 حبة",unit="مختلطة")))
    check(mixed?.cartons=="6" && mixed.pieces=="4")
    val fail=VoiceCommandEngine.apply(mapOf(VoiceField.EXPIRY_DATE to "2027-02-28"),VoiceResult(listOf(VoiceCommand(VoiceField.EXPIRY_DATE,value="2027-02-31")),emptyList()))
    check(fail.values[VoiceField.EXPIRY_DATE]=="2027-02-28")
    println("Voice parsing, partial date edit, quantity and safety smoke tests passed")
}
