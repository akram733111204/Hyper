package com.rukn.expiry.data

import com.rukn.core.*
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Small, dependency-free OOXML codec. All values are written as literal strings, never formulas. */
object XlsxCodec {
    private val sheets = listOf("Products", "Batches", "Tasks", "AuditLog", "Settings", "Metadata", "CatalogProducts", "BarcodeAliases")
    private val catalogFields=listOf("id","barcode","nameAr","nameEn","brand","size","unitsPerCarton","category","subcategory","descriptionAr","descriptionEn","packageType","salesUnit","manufacturer","countryOfOrigin","internalSku","searchKeywords","source","sourceUrl","importedAt")
    private val aliasFields=listOf("primaryBarcode","alternateBarcode","barcodeType","notes")
    private val productFields=listOf("id","barcode","nameAr","nameEn","brand","size","unitsPerCarton","category","aisle","shelf","notes","source","sourceUrl","createdAt","updatedAt")
    private val batchFields=listOf("id","productId","lot","productionDate","expiryDate","precision","dateKind","quantity","unit","location","notes","status","verified","lastCheckedAt","createdAt","updatedAt")
    private val taskFields=listOf("id","batchId","title","status","dueDate","notes","createdAt","closedAt")
    private val auditFields=listOf("id","entityType","entityId","action","before","after","at")
    private fun productRow(p:Product)=listOf(p.id,p.barcode,p.nameAr,p.nameEn,p.brand,p.size,p.unitsPerCarton,p.category,p.aisle,p.shelf,p.notes,p.source,p.sourceUrl,p.createdAt,p.updatedAt)
    private fun catalogRow(p:CatalogProduct)=listOf(p.id,p.barcode,p.nameAr,p.nameEn,p.brand,p.size,p.unitsPerCarton,p.category,p.subcategory,p.descriptionAr,p.descriptionEn,p.packageType,p.salesUnit,p.manufacturer,p.countryOfOrigin,p.internalSku,p.searchKeywords,p.source,p.sourceUrl,p.importedAt)
    private fun aliasRow(a:CatalogBarcodeAlias)=listOf(a.primaryBarcode,a.alternateBarcode,a.barcodeType,a.notes)
    private fun batchRow(b:Batch)=listOf(b.id,b.productId,b.lot,b.productionDate,b.expiryDate,b.precision.name,b.dateKind.name,b.quantity,b.unit,b.location,b.notes,b.status.name,b.verified.toString(),b.lastCheckedAt,b.createdAt,b.updatedAt)
    private fun taskRow(t:InspectionTask)=listOf(t.id,t.batchId,t.title,t.status.name,t.dueDate,t.notes,t.createdAt,t.closedAt)
    private fun auditRow(a:AuditEntry)=listOf(a.id,a.entityType,a.entityId,a.action,a.before,a.after,a.at)
    private fun xmlText(str:String):String = buildString {
        str.forEach { ch -> when(ch){
            '&'->append("&amp;"); '<'->append("&lt;");'>'->append("&gt;");'"'->append("&quot;");'\''->append("&apos;")
            else-> if (ch=='\n' || ch=='\r' || ch=='\t' || ch.code>=32) append(ch)
        } }
    }
    private fun address(index:Int):String {
        var n=index+1; var result=""
        while(n>0){ n--;result=('A'+(n%26)).toString()+result;n/=26 }
        return result
    }
    private fun sheetXml(headers:List<String>, rows:List<List<String>>):String {
        val sb=StringBuilder(512+rows.size*180)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"1\"/></sheetViews><sheetData>")
        fun row(index:Int, values:List<String>){
            sb.append("<row r=\"").append(index).append("\">")
            values.forEachIndexed{col,value->
                val cell=address(col)+index
                sb.append("<c r=\"").append(cell).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xmlText(value)).append("</t></is></c>")
            }
            sb.append("</row>")
        }
        row(1,headers);rows.forEachIndexed{ix,values->row(ix+2,values)}
        return sb.append("</sheetData></worksheet>").toString()
    }
    fun write(data:AppData,out:OutputStream){
        val rows= listOf(
            productFields to data.products.map(::productRow),
            batchFields to data.batches.map(::batchRow),
            taskFields to data.tasks.map(::taskRow),
            auditFields to data.audit.map(::auditRow),
            listOf("key","value") to data.settings.entries.sortedBy{it.key}.map{listOf(it.key,it.value)},
            listOf("key","value") to listOf(listOf("schema","2"),listOf("updatedAt",Instant.now().toString()),listOf("notice","الأعمدة النصية محفوظة للحفاظ على الباركود والأصفار الابتدائية")),
            catalogFields to data.catalog.map(::catalogRow),
            aliasFields to data.catalogAliases.map(::aliasRow)
        )
        ZipOutputStream(out).use { zip ->
            fun entry(name:String,text:String){ zip.putNextEntry(ZipEntry(name));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry() }
            val overrides=sheets.indices.joinToString(""){"<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"}
            entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$overrides</Types>""")
            entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            val tags=sheets.mapIndexed{i,name->"<sheet name=\"$name\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")
            entry("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$tags</sheets></workbook>""")
            val rels=sheets.indices.joinToString(""){"<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>"}
            entry("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
            rows.forEachIndexed{i,(headers,table)->entry("xl/worksheets/sheet${i+1}.xml",sheetXml(headers,table))}
        }
    }

    private fun saxFactory():SAXParserFactory {
        val f=SAXParserFactory.newInstance()
        f.isNamespaceAware=false
        runCatching{f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)}
        runCatching{f.setFeature("http://xml.org/sax/features/external-general-entities",false)}
        runCatching{f.setFeature("http://xml.org/sax/features/external-parameter-entities",false)}
        return f
    }
    private fun readCells(xml:ByteArray,shared:List<String>):List<Map<String,String>> {
        val rows=mutableListOf<List<String>>()
        val handler=object:DefaultHandler(){
            var current=mutableMapOf<Int,String>()
            var column=-1
            var cellType=""
            var inRow=false
            var inValue=false
            var value=StringBuilder()
            override fun startElement(uri:String?,localName:String?,qName:String?,attributes:Attributes){
                when(qName?.substringAfter(':')){
                    "row"->{inRow=true;current=mutableMapOf()}
                    "c"->{
                        val letters=attributes.getValue("r").orEmpty().takeWhile{it.isLetter()}
                        column=letters.fold(0){a,ch->a*26+(ch.uppercaseChar()-'A'+1)}-1
                        cellType=attributes.getValue("t").orEmpty()
                        value=StringBuilder()
                    }
                    "v","t" -> if(inRow)inValue=true
                }
            }
            override fun characters(ch:CharArray,start:Int,length:Int){if(inValue)value.append(ch,start,length)}
            override fun endElement(uri:String?,localName:String?,qName:String?){
                when(qName?.substringAfter(':')){
                    "t","v"->inValue=false
                    "c"->{
                        if(column in 0..63){val str=value.toString()
                            current[column]=if(cellType=="s")shared.getOrNull(str.toIntOrNull()?:-1).orEmpty() else str
                        }
                    }
                    "row"->{
                        if(rows.size>=200_000)throw IllegalArgumentException("عدد الصفوف يتجاوز الحد الآمن")
                        val max=current.keys.maxOrNull()?:-1
                        rows+=(0..max).map{current[it].orEmpty()}
                        inRow=false
                    }
                }
            }
        }
        if(xml.toString(Charsets.UTF_8).contains("<!DOCTYPE",ignoreCase=true) ||
            xml.toString(Charsets.UTF_8).contains("<!ENTITY",ignoreCase=true))
            throw IllegalArgumentException("لا يسمح بوثائق XML تحتوي على كيانات خارجية")
        saxFactory().newSAXParser().parse(xml.inputStream(),handler)
        if(rows.isEmpty())return emptyList()
        val keys=rows.first()
        return rows.drop(1).map{row->keys.mapIndexed{ix,key->key to row.getOrElse(ix){""}}.toMap()}
    }
    private fun sharedStrings(xml:ByteArray):List<String>{
        val strings=mutableListOf<String>()
        val handler=object:DefaultHandler(){
            var inText=false
            var builder=StringBuilder()
            override fun startElement(uri:String?,localName:String?,qName:String?,attributes:Attributes){
                when(qName?.substringAfter(':')){"si"->builder=StringBuilder();"t"->inText=true}
            }
            override fun characters(ch:CharArray,start:Int,length:Int){if(inText)builder.append(ch,start,length)}
            override fun endElement(uri:String?,localName:String?,qName:String?){
                when(qName?.substringAfter(':')){"t"->inText=false;"si"->strings+=builder.toString()}
            }
        }
        if(xml.toString(Charsets.UTF_8).contains("<!DOCTYPE",ignoreCase=true) ||
            xml.toString(Charsets.UTF_8).contains("<!ENTITY",ignoreCase=true))
            throw IllegalArgumentException("لا يسمح بوثائق XML تحتوي على كيانات خارجية")
        saxFactory().newSAXParser().parse(xml.inputStream(),handler)
        return strings
    }
    fun read(input:InputStream):AppData{
        val blobs=mutableMapOf<String,ByteArray>()
        ZipInputStream(input).use{zip->
            var size=0L;var entry=zip.nextEntry
            while(entry!=null){
                val name=entry.name
                if(name=="xl/sharedStrings.xml" || name.matches(Regex("xl/worksheets/sheet[1-8]\\.xml"))){
                    val output=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(16_384)
                    while(true){
                        val count=zip.read(buffer)
                        if(count<0)break
                        size+=count
                        if(size>40_000_000L)throw IllegalArgumentException("ملف Excel يتجاوز الحجم المسموح")
                        output.write(buffer,0,count)
                    }
                    blobs[name]=output.toByteArray()
                }
                zip.closeEntry();entry=zip.nextEntry
            }
        }
        if(blobs["xl/worksheets/sheet1.xml"]==null || blobs["xl/worksheets/sheet2.xml"]==null)
            throw IllegalArgumentException("ملف Excel لا يطابق قالب قاعدة التطبيق (Products/Batches)")
        if(blobs["xl/worksheets/sheet6.xml"]==null)
            throw IllegalArgumentException("قالب Excel لا يحتوي على صفحة Metadata المطلوبة")
        val shared=blobs["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        fun table(i:Int)=blobs["xl/worksheets/sheet$i.xml"]?.let{readCells(it,shared)}.orEmpty()
        fun Map<String,String>.s(k:String)=get(k).orEmpty()
        val schema = table(6).firstOrNull{it.s("key")=="schema"}?.s("value")
        if(schema !in setOf("1","2")) throw IllegalArgumentException("إصدار قاعدة Excel غير مدعوم أو بيانات التعريف مفقودة")
        if(schema=="2" && (blobs["xl/worksheets/sheet7.xml"]==null || blobs["xl/worksheets/sheet8.xml"]==null))
            throw IllegalArgumentException("كتالوج Excel غير مكتمل")
        fun <T:Enum<T>> enumOr(default:T,value:String,values:Array<T>)=values.firstOrNull{it.name==value}?:default
        val products=table(1).filter{it.s("id").isNotBlank()}.map{r->Product(
            id=r.s("id"),barcode=r.s("barcode"),nameAr=r.s("nameAr"),nameEn=r.s("nameEn"),brand=r.s("brand"),
            size=r.s("size"),unitsPerCarton=r.s("unitsPerCarton"),category=r.s("category"),aisle=r.s("aisle"),shelf=r.s("shelf"),notes=r.s("notes"),
            source=r.s("source"),sourceUrl=r.s("sourceUrl"),createdAt=r.s("createdAt"),updatedAt=r.s("updatedAt")
        )}
        val batches=table(2).filter{it.s("id").isNotBlank()}.map{r->Batch(
            id=r.s("id"),productId=r.s("productId"),lot=r.s("lot"),productionDate=r.s("productionDate"),expiryDate=r.s("expiryDate"),
            precision=enumOr(DatePrecision.UNKNOWN,r.s("precision"),DatePrecision.entries.toTypedArray()),
            dateKind=enumOr(DateKind.EXPIRY,r.s("dateKind"),DateKind.entries.toTypedArray()),
            quantity=r.s("quantity"),unit=r.s("unit"),location=r.s("location"),notes=r.s("notes"),
            status=enumOr(BatchStatus.ACTIVE,r.s("status"),BatchStatus.entries.toTypedArray()),verified=r.s("verified")=="true",
            lastCheckedAt=r.s("lastCheckedAt"),createdAt=r.s("createdAt"),updatedAt=r.s("updatedAt")
        )}
        val tasks=table(3).filter{it.s("id").isNotBlank()}.map{r->InspectionTask(
            id=r.s("id"),batchId=r.s("batchId"),title=r.s("title"),
            status=enumOr(TaskStatus.OPEN,r.s("status"),TaskStatus.entries.toTypedArray()),dueDate=r.s("dueDate"),
            notes=r.s("notes"),createdAt=r.s("createdAt"),closedAt=r.s("closedAt")
        )}
        val audit=table(4).filter{it.s("id").isNotBlank()}.map{r->AuditEntry(
            id=r.s("id"),entityType=r.s("entityType"),entityId=r.s("entityId"),action=r.s("action"),
            before=r.s("before"),after=r.s("after"),at=r.s("at")
        )}
        val settings=table(5).filter{it.s("key").isNotBlank()}.associate{it.s("key") to it.s("value")}
        if(products.map{it.id}.toSet().size!=products.size || batches.map{it.id}.toSet().size!=batches.size)
            throw IllegalArgumentException("تم العثور على معرفات مكررة داخل ملف Excel")
        val ids=products.map{it.id}.toSet()
        if(batches.any{it.productId !in ids})throw IllegalArgumentException("تشغيلات مرتبطة بمنتجات غير موجودة")
        val catalog=if(schema=="2")table(7).filter{it.s("id").isNotBlank()}.map{r->CatalogProduct(
            id=r.s("id"),barcode=r.s("barcode"),nameAr=r.s("nameAr"),nameEn=r.s("nameEn"),brand=r.s("brand"),
            size=r.s("size"),unitsPerCarton=r.s("unitsPerCarton"),category=r.s("category"),subcategory=r.s("subcategory"),
            descriptionAr=r.s("descriptionAr"),descriptionEn=r.s("descriptionEn"),packageType=r.s("packageType"),
            salesUnit=r.s("salesUnit"),manufacturer=r.s("manufacturer"),countryOfOrigin=r.s("countryOfOrigin"),
            internalSku=r.s("internalSku"),searchKeywords=r.s("searchKeywords"),source=r.s("source"),sourceUrl=r.s("sourceUrl"),importedAt=r.s("importedAt")
        )} else emptyList()
        val aliases=if(schema=="2")table(8).filter{it.s("primaryBarcode").isNotBlank()}.map{r->CatalogBarcodeAlias(
            r.s("primaryBarcode"),r.s("alternateBarcode"),r.s("barcodeType"),r.s("notes")
        )} else emptyList()
        if(catalog.map{it.id}.toSet().size!=catalog.size)throw IllegalArgumentException("معرفات كتالوج مكررة")
        return AppData(products,batches,tasks,audit,settings.ifEmpty{AppData().settings},catalog,aliases)
    }


    /** Export the reference catalog; this is NOT a full app backup. */
    fun writeCatalog(data:AppData,out:OutputStream){
        val rows=listOf(
            listOf("barcode","nameAr","nameEn","brand","size","unitsPerCarton","category","subcategory",
                "descriptionAr","descriptionEn","packageType","salesUnit","manufacturer","countryOfOrigin",
                "internalSku","searchKeywords","source","sourceUrl") to data.catalog.map { catalogRow(it).drop(1).dropLast(1) },
            aliasFields to data.catalogAliases.map(::aliasRow)
        )
        val names=listOf("ProductCatalog","BarcodeAliases")
        ZipOutputStream(out).use { zip ->
            fun entry(name:String,value:String) {
                zip.putNextEntry(ZipEntry(name));zip.write(value.toByteArray(Charsets.UTF_8));zip.closeEntry()
            }
            val overrides=names.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }
            entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>$overrides</Types>""")
            entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            val tags=names.mapIndexed{i,n->"<sheet name=\"$n\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")
            entry("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$tags</sheets></workbook>""")
            val relationships=names.indices.joinToString(""){ "<Relationship Id=\"rId${it+1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it+1}.xml\"/>" }
            entry("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$relationships</Relationships>""")
            rows.forEachIndexed{i,(headers,table)->entry("xl/worksheets/sheet${i+1}.xml",sheetXml(headers,table))}
        }
    }

    /** Reads ONLY reference catalog sheets. Does not import or replace the app's main workbook. */
    fun readCatalog(input:InputStream):CatalogRows {
        val blobs=mutableMapOf<String,ByteArray>()
        var total=0L
        ZipInputStream(input).use { zip ->
            var entry=zip.nextEntry
            var count=0
            while(entry!=null) {
                count++
                if(count>1000)throw IllegalArgumentException("عدد مكونات ملف Excel كبير جدًا")
                val name=entry.name.removePrefix("/")
                val keep=name=="xl/workbook.xml" || name=="xl/_rels/workbook.xml.rels" ||
                    name=="xl/sharedStrings.xml" || name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))
                val buf=if(keep)java.io.ByteArrayOutputStream() else null
                val chunk=ByteArray(8192)
                while(true){
                    val n=zip.read(chunk)
                    if(n<0)break
                    total+=n
                    if(total>55_000_000L) throw IllegalArgumentException("حجم بيانات Excel غير المضغوطة كبير جدًا")
                    buf?.write(chunk,0,n)
                }
                if(keep) blobs[name]=buf!!.toByteArray()
                zip.closeEntry();entry=zip.nextEntry
            }
        }
        val workbook=blobs["xl/workbook.xml"] ?: throw IllegalArgumentException("ملف Excel غير صالح: workbook")
        val rels=blobs["xl/_rels/workbook.xml.rels"] ?: throw IllegalArgumentException("علاقات أوراق Excel مفقودة")
        val sheetIds=mutableMapOf<String,String>()
        val workbookHandler=object:DefaultHandler(){
            override fun startElement(uri:String?, localName:String?, qName:String?, attrs:Attributes){
                if(qName?.substringAfter(':')=="sheet"){
                    val name=attrs.getValue("name")
                    val id=attrs.getValue("r:id") ?: attrs.getValue("id")
                    if(name!=null && id!=null)sheetIds[name]=id
                }
            }
        }
        saxFactory().newSAXParser().parse(workbook.inputStream(),workbookHandler)
        val relTargets=mutableMapOf<String,String>()
        val relHandler=object:DefaultHandler(){
            override fun startElement(uri:String?, localName:String?, qName:String?,attrs:Attributes){
                if(qName?.substringAfter(':')=="Relationship"){
                    val id=attrs.getValue("Id") ?: return
                    val target=attrs.getValue("Target") ?: return
                    if(attrs.getValue("Type").orEmpty().endsWith("/worksheet")) {
                        val path=if(target.startsWith("/"))target.removePrefix("/") else "xl/"+target
                        if(!path.contains(".."))relTargets[id]=path
                    }
                }
            }
        }
        saxFactory().newSAXParser().parse(rels.inputStream(),relHandler)
        val shared=blobs["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        fun table(name:String,optional:Boolean=false):List<Map<String,String>> {
            val id=sheetIds[name]
            if(id==null){if(optional)return emptyList();throw IllegalArgumentException("الورقة المطلوبة مفقودة: $name")}
            val file=relTargets[id] ?: throw IllegalArgumentException("تعذر تحديد ملف الورقة: $name")
            val bytes=blobs[file] ?: throw IllegalArgumentException("تعذر قراءة ورقة: $name")
            val xml=bytes.toString(Charsets.UTF_8)
            if(Regex("<(?:[A-Za-z0-9_]+:)?f(?:\\s|>)").containsMatchIn(xml))
                throw IllegalArgumentException("تحتوي ورقة $name على صيغ Excel؛ يُسمح بالقيم النصية فقط")
            return readCells(bytes,shared).filter{row->row.values.any{it.isNotBlank()}}
        }
        val products=table("ProductCatalog")
        // A blank template has no records but still has a header. Check header separately.
        val keys=products.firstOrNull()?.keys.orEmpty()
        val aliases=table("BarcodeAliases",optional=true)
        if(products.isNotEmpty() && !keys.contains("barcode"))
            throw IllegalArgumentException("عمود barcode مفقود من ProductCatalog")
        if(aliases.isNotEmpty() && !aliases.first().keys.containsAll(listOf("primaryBarcode","alternateBarcode")))
            throw IllegalArgumentException("عناوين أعمدة BarcodeAliases غير صحيحة")
        return CatalogRows(products,aliases)
    }
}
