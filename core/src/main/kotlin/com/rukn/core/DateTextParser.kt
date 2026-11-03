package com.rukn.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** OCR outputs are only candidates: these MUST be reviewed by a person before committing. */
object DateTextParser {
    data class Candidate(val iso: String, val precision: DatePrecision, val label: String, val raw: String, val ambiguous: Boolean)
    data class Result(val production: Candidate?, val expiry: Candidate?, val other: List<Candidate>, val lot: String = "")
    private val numericDate= Regex("(?<!\\d)(\\d{4}[./-]\\d{1,2}(?:[./-]\\d{1,2})?|\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}|\\d{1,2}[./-]\\d{4}|\\d{1,2}[./-]\\d{1,2})(?!\\d)")
    private val compactDate=Regex("(?<!\\d)(?:20\\d{6})(?!\\d)")
    private val monthName=Regex("(?<![A-Za-z])(\\d{1,2})\\s+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC)[A-Z]*\\s+(20\\d{2}|\\d{2})(?!\\d)",RegexOption.IGNORE_CASE)
    private val productionWords=Regex("(?i)(MFG|MFD|MANUFACTURED|PROD|PRODUCTION|PACK(?:ED)?|انتاج|الإنتاج|التصنيع|تاريخ الصنع|تعبئة)")
    private val expiryWords=Regex("(?i)(EXP|EXPIRY|EXPIRES|USE[ -]?BY|USE BEFORE|انتهاء|الانتهاء|صلاحية|الصلاحيه|تاريخ الانتهاء)")
    private val bestBeforeWords=Regex("(?i)(BBE|BEST[ -]?BEFORE|يفضل استهلاكه|الأفضل قبل|افضل قبل)")
    private val lotWords=Regex("(?i)(?:LOT|BATCH|تشغيلة|رقم التشغيلة)\\s*[:#-]?\\s*([A-Za-z0-9-]{3,25})")
    private val mons=listOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")
    private fun normalizeDigits(s:String):String = s
        .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
        .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
        .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
        .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
    private fun parseCandidate(raw:String,label:String):Candidate? {
        val valText=raw.trim().uppercase(Locale.ROOT)
        val mon=monthName.matchEntire(valText)
        if(mon!=null){
            val day=mon.groupValues[1].toInt()
            val month=mons.indexOfFirst{mon.groupValues[2].startsWith(it)}+1
            val year=fullYear(mon.groupValues[3].toInt())
            if(month !in 1..12) return null
            return try { Candidate(LocalDate.of(year,month,day).toString(),DatePrecision.DAY,label,raw,false) }catch(_:Exception){null}
        }
        if(valText.matches(Regex("20\\d{6}"))) {
            return try { Candidate(LocalDate.of(valText.substring(0,4).toInt(),valText.substring(4,6).toInt(),valText.substring(6,8).toInt()).toString(),DatePrecision.DAY,label,raw,false) }catch(_:Exception){null}
        }
        val parts=valText.split(Regex("[./-]")).mapNotNull{it.toIntOrNull()}
        if(parts.size !in 2..3)return null
        return try {
            if(parts.size==2) {
                val (a,b)=parts
                val ym=if(a>=2000) YearMonth.of(a,b) else if(b>=2000) YearMonth.of(b,a) else return null
                Candidate(ym.toString(),DatePrecision.MONTH,label,raw,false)
            } else {
                val (a,b,c)=parts
                val year=if(a>=2000) a else fullYear(c)
                val (month,day,ambiguous)=if(a>=2000) Triple(b,c,false)
                    else if(a>12 && b<=12) Triple(b,a,false)
                    else if(b>12 && a<=12) Triple(a,b,false) // US-style only when unambiguous
                    else Triple(b,a,a!=b && a in 1..12 && b in 1..12)
                val date=LocalDate.of(year,month,day)
                Candidate(date.toString(),DatePrecision.DAY,label,raw,ambiguous)
            }
        } catch(_:Exception){ null }
    }
    private fun fullYear(y:Int)=when{y>=1900->y;y>=70->1900+y;else->2000+y}
    fun parse(text:String):Result {
        val normalized=normalizeDigits(text)
        val lines=normalized.lines().filter{it.isNotBlank()}
        val found=mutableListOf<Candidate>()
        for((ix,line) in lines.withIndex()){
            val preceding=lines.getOrNull(ix-1).orEmpty()
            val matches=(numericDate.findAll(line).toList()+compactDate.findAll(line).toList()+monthName.findAll(line).toList())
                .distinctBy{it.range}
            for(match in matches){
                // The closest label preceding each date wins; handles MFG and EXP on one printed line.
                val prefix=line.substring(0,match.range.first)
                val labels=(productionWords.findAll(prefix).map{it.range.first to "production"}+
                    expiryWords.findAll(prefix).map{it.range.first to "expiry"}+
                    bestBeforeWords.findAll(prefix).map{it.range.first to "best_before"}).toList()
                val closest=labels.maxByOrNull{it.first}
                val label=if(closest!=null && match.range.first-closest.first<=60)closest.second else when {
                    productionWords.containsMatchIn(preceding)->"production"
                    bestBeforeWords.containsMatchIn(preceding)->"best_before"
                    expiryWords.containsMatchIn(preceding)->"expiry"
                    else->"unknown"
                }
                parseCandidate(match.value,label)?.let{found+=it}
            }
        }
        val prod=found.firstOrNull{it.label=="production"}
        val exp=found.firstOrNull{it.label=="expiry" || it.label=="best_before"}
        val unknown=found.filter{it!=prod && it!=exp}
        return Result(prod,exp,unknown,lotWords.find(normalized)?.groupValues?.getOrNull(1).orEmpty())
    }
}
