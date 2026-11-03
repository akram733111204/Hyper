import com.rukn.core.*
fun main(){
 val v=DateTextParser.parse("PROD ٢٠٢٦/١٠/١٥\nEXP: 2027-12-25\nLOT: AB123")
 check(v.production?.iso=="2026-10-15") {"mfg $v"}; check(v.expiry?.iso=="2027-12-25") {"exp $v"}; check(v.lot=="AB123")
 println(DateTextParser.parse("BBE 12/2027")); check(DateTextParser.parse("BBE 12/2027").expiry?.iso=="2027-12")
 check(DateTextParser.parse("EXP 11/12/2027").expiry?.ambiguous==true)
 val p=listOf(Product(nameAr="شاي الربيع الأسود",brand="الربيع"),Product(nameAr="شاي اخضر",brand="الربيع"),Product(nameAr="قهوة"))
 check(ArabicSearch.search(p,"شاى الربيع الاسود").first().nameAr=="شاي الربيع الأسود")
 val product=Product(unitsPerCarton="24")
 val b=listOf(Batch(productId=product.id,quantity="2",unit="كرتون"),Batch(productId=product.id,quantity="5",unit="حبة"))
 val total=QuantityEngine.summarize(product,b)!!
 check(total.cartons=="2" && total.pieces=="5") {"qty $total"}
 println("Core smoke passed")
}
