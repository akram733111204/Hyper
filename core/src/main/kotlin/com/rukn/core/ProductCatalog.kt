package com.rukn.core

import java.time.Instant
import java.util.UUID

/** Reference catalog, never an authoritative source of dates, stock or user notes. */
data class CatalogProduct(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val brand: String = "",
    val size: String = "",
    val unitsPerCarton: String = "",
    val category: String = "",
    val subcategory: String = "",
    val descriptionAr: String = "",
    val descriptionEn: String = "",
    val packageType: String = "",
    val salesUnit: String = "",
    val manufacturer: String = "",
    val countryOfOrigin: String = "",
    val internalSku: String = "",
    val searchKeywords: String = "",
    val source: String = "",
    val sourceUrl: String = "",
    val importedAt: String = Instant.now().toString()
) {
    fun forEditor(scannedBarcode: String = barcode): Product = Product(
        barcode = barcode.ifBlank { scannedBarcode },
        nameAr = nameAr, nameEn = nameEn, brand = brand, size = size,
        unitsPerCarton = unitsPerCarton, category = category,
        source = "catalog:${source.ifBlank { "excel" }}", sourceUrl = sourceUrl
    )
}

data class CatalogBarcodeAlias(
    val primaryBarcode: String,
    val alternateBarcode: String,
    val barcodeType: String = "",
    val notes: String = ""
)

enum class LookupMode { AUTO, LOCAL, ONLINE, MANUAL;
    companion object { fun parse(v: String?): LookupMode = entries.firstOrNull { it.name == v } ?: AUTO }
}

/** Never drop leading zeros, never numerically coerce barcode identifiers. */
object BarcodeRules {
    fun normalize(raw: String): String = raw.trim().replace(" ", "").replace("\u200e", "")
    fun valid(raw: String): Boolean {
        val s = normalize(raw)
        return s.isNotBlank() && s.length <= 64 && !s.contains(Regex("[\\r\\n\\t]")) &&
            !s.startsWith('=') && !s.startsWith('+') && !s.startsWith('@') &&
            !s.matches(Regex("[0-9]+(\\.[0-9]+)?[eE][+-]?[0-9]+"))
    }
}

data class CatalogRows(
    val products: List<Map<String, String>>,
    val aliases: List<Map<String, String>>,
    val warnings: List<String> = emptyList()
)

data class CatalogIssue(val line: Int, val reason: String)

enum class CatalogConflictPolicy { KEEP_EXISTING, UPDATE_REFERENCE_ONLY }

data class CatalogImportPreview(
    val rows: CatalogRows,
    val newCount: Int,
    val fillCount: Int,
    val unchangedCount: Int,
    val rejectedCount: Int,
    val aliasNewCount: Int,
    val issues: List<CatalogIssue>,
    val fileName: String = "",
    val conflictPolicy: CatalogConflictPolicy = CatalogConflictPolicy.KEEP_EXISTING
)

data class CatalogMergeResult(val data: AppData, val preview: CatalogImportPreview)

/** All rules are deterministic; importing the same file twice does not duplicate catalog records. */
object CatalogImportEngine {
    private val productHeaders = listOf("barcode", "nameAr", "nameEn", "brand", "size", "unitsPerCarton",
        "category", "subcategory", "descriptionAr", "descriptionEn", "packageType", "salesUnit",
        "manufacturer", "countryOfOrigin", "internalSku", "searchKeywords", "source", "sourceUrl")

    fun plan(rows: CatalogRows, current: AppData, policy: CatalogConflictPolicy=CatalogConflictPolicy.KEEP_EXISTING): CatalogImportPreview = merge(rows, current, policy).preview

    fun merge(rows: CatalogRows, current: AppData, policy: CatalogConflictPolicy=CatalogConflictPolicy.KEEP_EXISTING): CatalogMergeResult {
        val issues = mutableListOf<CatalogIssue>()
        val out = current.catalog.toMutableList()
        val aliases = current.catalogAliases.toMutableList()
        val byBarcode = out.filter { it.barcode.isNotBlank() }.associateBy { BarcodeRules.normalize(it.barcode) }.toMutableMap()
        val byBarcodeIndex = out.withIndex().filter { it.value.barcode.isNotBlank() }
            .associate { BarcodeRules.normalize(it.value.barcode) to it.index }.toMutableMap()
        var added = 0; var enriched = 0; var unchanged = 0; var rejected = 0; var aliasAdded = 0
        val seenIncoming = mutableSetOf<String>()
        val seenAliases = aliases.map { BarcodeRules.normalize(it.alternateBarcode) }.toMutableSet()
        val activeCodes = current.products.map { BarcodeRules.normalize(it.barcode) }.filter { it.isNotBlank() }.toSet()

        rows.products.forEachIndexed { index, raw ->
            val line = index + 2
            val row = productHeaders.associateWith { raw[it].orEmpty().trim() }
            val code = BarcodeRules.normalize(row.getValue("barcode"))
            if (!BarcodeRules.valid(code)) {
                issues += CatalogIssue(line, if (code.isBlank()) "الباركود غير موجود: يُتجاوز السجل حتى يمكن المطابقة الآمنة" else "باركود غير صالح نصيًا")
                rejected++; return@forEachIndexed
            }
            if (!seenIncoming.add(code)) {
                issues += CatalogIssue(line, "تكرار الباركود داخل ملف الكتالوج؛ تم تجاهل الصف المكرر")
                rejected++; return@forEachIndexed
            }
            if (seenAliases.contains(code)) {
                issues += CatalogIssue(line, "الباركود مستخدم مسبقًا كرمز بديل لمنتج آخر")
                rejected++; return@forEachIndexed
            }
            val v = CatalogProduct(barcode = code, nameAr = row.getValue("nameAr"), nameEn = row.getValue("nameEn"),
                brand = row.getValue("brand"), size = row.getValue("size"), unitsPerCarton = row.getValue("unitsPerCarton"),
                category = row.getValue("category"), subcategory = row.getValue("subcategory"),
                descriptionAr = row.getValue("descriptionAr"), descriptionEn = row.getValue("descriptionEn"),
                packageType = row.getValue("packageType"), salesUnit = row.getValue("salesUnit"),
                manufacturer = row.getValue("manufacturer"), countryOfOrigin = row.getValue("countryOfOrigin"),
                internalSku = row.getValue("internalSku"), searchKeywords = row.getValue("searchKeywords"),
                source = row.getValue("source").ifBlank { "excel" }, sourceUrl = row.getValue("sourceUrl"))
            val old = byBarcode[code]
            if (old == null) {
                byBarcodeIndex[code]=out.size
                out += v; byBarcode[code] = v; added++
            } else {
                // Conflicting nonempty fields never silently overwrite existing values.
                val fields = productHeaders.filter { it != "barcode" && it != "source" && it != "sourceUrl" }
                fun value(p: CatalogProduct, key: String) = when (key) {
                    "nameAr" -> p.nameAr; "nameEn" -> p.nameEn; "brand" -> p.brand; "size" -> p.size
                    "unitsPerCarton" -> p.unitsPerCarton; "category" -> p.category; "subcategory" -> p.subcategory
                    "descriptionAr" -> p.descriptionAr; "descriptionEn" -> p.descriptionEn; "packageType" -> p.packageType
                    "salesUnit" -> p.salesUnit; "manufacturer" -> p.manufacturer; "countryOfOrigin" -> p.countryOfOrigin
                    "internalSku" -> p.internalSku; "searchKeywords" -> p.searchKeywords; else -> ""
                }
                val conflicts = fields.filter { value(old,it).isNotBlank() && value(v,it).isNotBlank() &&
                    value(old,it) != value(v,it) }
                if (conflicts.isNotEmpty() && policy==CatalogConflictPolicy.KEEP_EXISTING) {
                    issues += CatalogIssue(line,"معلومات متعارضة للباركود نفسه في: ${conflicts.joinToString()}؛ لم تُغيّر البيانات السابقة")
                    rejected++; return@forEachIndexed
                }
                if (conflicts.isNotEmpty()) issues += CatalogIssue(line,
                    "تحديث كتالوج مرجعي بطلب صريح: ${conflicts.joinToString()}؛ دون تغيير التشغيلات أو السجلات اليدوية")
                fun pick(a:String,b:String)=if(policy==CatalogConflictPolicy.UPDATE_REFERENCE_ONLY) b.ifBlank{a} else a.ifBlank{b}
                val merged=old.copy(nameAr=pick(old.nameAr,v.nameAr), nameEn=pick(old.nameEn,v.nameEn),
                    brand=pick(old.brand,v.brand), size=pick(old.size,v.size), unitsPerCarton=pick(old.unitsPerCarton,v.unitsPerCarton),
                    category=pick(old.category,v.category), subcategory=pick(old.subcategory,v.subcategory),
                    descriptionAr=pick(old.descriptionAr,v.descriptionAr),descriptionEn=pick(old.descriptionEn,v.descriptionEn),
                    packageType=pick(old.packageType,v.packageType), salesUnit=pick(old.salesUnit,v.salesUnit),
                    manufacturer=pick(old.manufacturer,v.manufacturer),countryOfOrigin=pick(old.countryOfOrigin,v.countryOfOrigin),
                    internalSku=pick(old.internalSku,v.internalSku), searchKeywords=pick(old.searchKeywords,v.searchKeywords),
                    sourceUrl=pick(old.sourceUrl,v.sourceUrl),
                    source=if(policy==CatalogConflictPolicy.UPDATE_REFERENCE_ONLY) v.source.ifBlank{old.source} else old.source)
                if (merged == old) unchanged++ else { enriched++; out[byBarcodeIndex.getValue(code)] = merged; byBarcode[code] = merged }
            }
        }
        val allPrimaryCodes = (out.map { BarcodeRules.normalize(it.barcode) } + activeCodes).toSet()
        rows.aliases.forEachIndexed { index, row ->
            val primary = BarcodeRules.normalize(row["primaryBarcode"].orEmpty())
            val alias = BarcodeRules.normalize(row["alternateBarcode"].orEmpty())
            val line = index + 2
            when {
                !BarcodeRules.valid(primary) || !BarcodeRules.valid(alias) -> issues += CatalogIssue(line,"رمز أساسي أو بديل فارغ/غير صالح في BarcodeAliases")
                !byBarcode.containsKey(primary) -> issues += CatalogIssue(line,"الباركود الأساسي غير موجود في كتالوج المنتجات")
                primary == alias || alias in allPrimaryCodes -> issues += CatalogIssue(line,"الباركود البديل يساوي رمزًا أساسيًا موجودًا")
                alias in seenAliases -> {
                    val existing=aliases.firstOrNull{BarcodeRules.normalize(it.alternateBarcode)==alias}
                    if(existing?.primaryBarcode!=primary)issues += CatalogIssue(line,"رمز بديل مرتبط مسبقًا بمنتج آخر")
                }
                else -> { aliases += CatalogBarcodeAlias(primary,alias,row["barcodeType"].orEmpty(),row["notes"].orEmpty()); seenAliases += alias; aliasAdded++ }
            }
        }
        val updated=current.copy(catalog=out,catalogAliases=aliases)
        return CatalogMergeResult(updated,CatalogImportPreview(rows,added,enriched,unchanged,rejected,aliasAdded,issues + rows.warnings.map { CatalogIssue(0,it) }, conflictPolicy=policy))
    }

    /** Prefill missing fields on an existing product; never overwrite a user's values or batches. */
    fun enrichActiveProduct(p: Product, reference: CatalogProduct): Product = p.copy(
        nameAr=p.nameAr.ifBlank{reference.nameAr}, nameEn=p.nameEn.ifBlank{reference.nameEn},
        brand=p.brand.ifBlank{reference.brand}, size=p.size.ifBlank{reference.size},
        unitsPerCarton=p.unitsPerCarton.ifBlank{reference.unitsPerCarton},
        category=p.category.ifBlank{reference.category}, sourceUrl=p.sourceUrl.ifBlank{reference.sourceUrl}
    )

    fun resolve(code: String, data: AppData): CatalogProduct? {
        val normalized = BarcodeRules.normalize(code)
        if (normalized.isBlank()) return null
        data.catalog.firstOrNull { BarcodeRules.normalize(it.barcode)==normalized }?.let { return it }
        val primary=data.catalogAliases.firstOrNull { BarcodeRules.normalize(it.alternateBarcode)==normalized }?.primaryBarcode
            ?:return null
        return data.catalog.firstOrNull { BarcodeRules.normalize(it.barcode)==BarcodeRules.normalize(primary) }
    }
}
