package com.rukn.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/** Arabic-aware ranked fuzzy search: Arabic folds, token scoring, prefix, trigrams and edit distance. */
object ArabicSearch {
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    fun normalize(input: String): String = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        .replace(diacritics, "")
        .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ٱ','ا')
        .replace('ى','ي').replace('ؤ','و').replace('ئ','ي').replace('ة','ه')
        .replace('ک','ك').replace('ی','ي').replace('ۀ','ه')
        .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
        .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
        .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
        .replace(punctuation," ").trim().replace(Regex("\\s+"), " ")

    private fun stripArticle(w: String): String = if(w.startsWith("ال") && w.length >= 5) w.drop(2) else w
    private fun trigrams(text: String): Set<String> {
        if(text.length < 3) return setOf(text)
        return (0..text.length-3).map { text.substring(it,it+3) }.toSet()
    }
    private fun edit(a: String,b: String,maxDistance: Int): Int {
        if(kotlin.math.abs(a.length-b.length)>maxDistance) return maxDistance+1
        var prev=IntArray(b.length+1){it}
        for(i in a.indices){
            val cur=IntArray(b.length+1)
            cur[0]=i+1
            var rowMin=cur[0]
            for(j in b.indices){
                cur[j+1]=min(min(cur[j]+1,prev[j+1]+1),prev[j]+if(a[i]==b[j]) 0 else 1)
                rowMin=min(rowMin,cur[j+1])
            }
            if(rowMin>maxDistance) return maxDistance+1
            prev=cur
        }
        return prev[b.length]
    }
    private fun similarity(q:String, v:String): Double {
        if(q==v) return 1.0
        if(v.startsWith(q)) return 0.95
        if(v.contains(q) && q.length>=3) return 0.84
        val qBare=stripArticle(q); val vBare=stripArticle(v)
        if(qBare==vBare) return 0.96
        if(vBare.startsWith(qBare) && qBare.length>=2) return 0.88
        if(qBare.length < 4) return 0.0
        val allowed=if(qBare.length>=7) 2 else 1
        val dist=edit(qBare,vBare,allowed)
        if(dist<=allowed) return 0.77-(0.11*dist)
        val g1=trigrams(qBare);val g2=trigrams(vBare)
        val dice=2.0*g1.intersect(g2).size/(g1.size+g2.size).coerceAtLeast(1)
        return if(dice>=0.62) dice*0.62 else 0.0
    }

    /** Imported reference products participate in the same Arabic fuzzy search as saved products. */
    fun searchCatalog(catalog: List<CatalogProduct>, query: String, limit: Int = 50): List<CatalogProduct> {
        val synthetic = catalog.map { c -> Product(id=c.id,barcode=c.barcode,nameAr=c.nameAr,nameEn=c.nameEn,
            brand=c.brand,size=c.size,category=listOf(c.category,c.subcategory,c.searchKeywords,c.internalSku)
                .filter{it.isNotBlank()}.joinToString(" ")) }
        val byId=catalog.associateBy{it.id}
        return search(synthetic,query,limit).mapNotNull{byId[it.id]}
    }

    fun search(products: List<Product>, query: String, limit: Int = 100): List<Product> {
        val q=normalize(query)
        if(q.isBlank()) return products.take(limit)
        val terms=q.split(' ').filter {it.isNotBlank()}
        return products.asSequence().mapNotNull { p ->
            if(p.barcode.isNotBlank() && p.barcode == q) return@mapNotNull p to 10000.0
            val fields = listOf(
                p.nameAr to 1.0, p.nameEn to 0.95, p.brand to 0.9,
                p.category to 0.55, p.size to 0.4, p.shelf to 0.3,
                p.aisle to 0.3, p.barcode to 0.95
            )
            val scoreParts=terms.map { term ->
                fields.maxOfOrNull { (str,weight) ->
                    val normalized=normalize(str)
                    if(normalized.isBlank()) 0.0 else {
                        val whole=if(normalized==term) 1.0 else if(normalized.contains(term) && term.length>=3) 0.9 else 0.0
                        val tokens=normalized.split(' ').maxOfOrNull{ similarity(term,it) }?:0.0
                        maxOf(whole,tokens)*weight
                    }
                } ?: 0.0
            }
            if(scoreParts.any {it < 0.28}) null
            else p to (scoreParts.sum()*100.0 + scoreParts.minOrNull()!!*10.0)
        }.sortedByDescending{it.second}.take(limit).map{it.first}.toList()
    }
}
