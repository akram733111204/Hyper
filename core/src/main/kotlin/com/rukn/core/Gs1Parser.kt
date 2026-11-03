package com.rukn.core

import java.time.LocalDate
import java.time.YearMonth

/** Decodes common GS1 AIs from DataMatrix / GS1-128 (including FNC1 group separator). */
object Gs1Parser {
    data class Fields(val gtin:String,val lot:String="",val production:String="",val expiry:String="",val kind:DateKind=DateKind.EXPIRY)
    private fun gsDate(s:String):String =try{
        require(s.matches(Regex("\\d{6}")))
        val year=2000+s.substring(0,2).toInt()
        val month=s.substring(2,4).toInt()
        val day=s.substring(4,6).toInt()
        if(day==0)YearMonth.of(year,month).toString() else LocalDate.of(year,month,day).toString()
    }catch(_:Exception){""}
    fun parse(raw:String):Fields? {
        val code=raw.removePrefix("]d2").removePrefix("]C1")
        val fields=linkedMapOf<String,String>()
        if(code.contains("(01)")){
            val pattern=Regex("\\((01|10|11|15|17)\\)")
            val matches=pattern.findAll(code).toList()
            matches.forEachIndexed{ix,m->
                val end=matches.getOrNull(ix+1)?.range?.first?:code.length
                fields[m.groupValues[1]]=code.substring(m.range.last+1,end).trimEnd('\u001D')
            }
        }else{
            var i=0
            // GS1 fixed AIs, variable LOT (10) delimited by FNC1 when followed by another AI.
            while(i+2<=code.length){
                val ai=code.substring(i,i+2);i+=2
                when(ai){
                    "01"->{if(i+14>code.length)break;fields[ai]=code.substring(i,i+14);i+=14}
                    "11","15","17"->{if(i+6>code.length)break;fields[ai]=code.substring(i,i+6);i+=6}
                    "10"->{val end=code.indexOf('\u001D',i).let{if(it<0)code.length else it};fields[ai]=code.substring(i,end).take(20);i=if(end<code.length)end+1 else end}
                    else -> break
                }
            }
        }
        val gtin=fields["01"].orEmpty()
        if(!gtin.matches(Regex("\\d{14}")))return null
        return Fields(gtin=if(gtin.startsWith('0'))gtin.drop(1)else gtin,
            lot=fields["10"].orEmpty(),production=gsDate(fields["11"].orEmpty()),
            expiry=gsDate(fields["17"]?:fields["15"].orEmpty()),
            kind=if(fields["17"].isNullOrBlank()&&!fields["15"].isNullOrBlank())DateKind.BEST_BEFORE else DateKind.EXPIRY)
    }
}
