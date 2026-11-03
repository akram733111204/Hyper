import com.rukn.core.*
import com.rukn.expiry.data.XlsxCodec
import java.io.ByteArrayOutputStream
fun main(){
 val p=Product(barcode="00628900123456",nameAr="شاي الربيع أسود & مجفّف",shelf="A-12",unitsPerCarton="24")
 val b=Batch(productId=p.id,productionDate="2026-01",expiryDate="2027-05",quantity="5",unit="قطعة",verified=true)
 val data=AppData(products=listOf(p),batches=listOf(b),settings=mapOf("alertDays" to "7,30,60"))
 val bytes=ByteArrayOutputStream().also{XlsxCodec.write(data,it)}.toByteArray()
 val next=XlsxCodec.read(bytes.inputStream())
 check(next.products==data.products){"products not identical: ${next.products}"}
 check(next.batches==data.batches){"batches not identical: ${next.batches}"}
 check(next.settings==data.settings)
 println("XLSX roundtrip OK; bytes=${bytes.size}")
}
