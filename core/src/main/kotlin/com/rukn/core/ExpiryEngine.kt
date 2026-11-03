package com.rukn.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object ExpiryEngine {
    /** Month-only date remains month-only in storage; end-of-month is ONLY used for provisional sorting. */
    fun expiryDate(batch: Batch): LocalDate? = try {
        when {
            batch.expiryDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> LocalDate.parse(batch.expiryDate)
            batch.expiryDate.matches(Regex("\\d{4}-\\d{2}")) -> YearMonth.parse(batch.expiryDate).atEndOfMonth()
            else -> null
        }
    } catch (_: Exception) { null }

    fun daysLeft(batch:Batch, today:LocalDate=LocalDate.now()):Long? = expiryDate(batch)?.let { ChronoUnit.DAYS.between(today,it) }

    fun isActive(b:Batch)=b.status==BatchStatus.ACTIVE

    fun dueWithin(b: Batch, days: Int, today:LocalDate=LocalDate.now()):Boolean =
        isActive(b) && daysLeft(b,today)?.let{it in 0L..days.toLong()}==true

    fun expired(b:Batch,today:LocalDate=LocalDate.now()):Boolean =
        isActive(b) && daysLeft(b,today)?.let{it<0}==true

    fun displayStatus(b:Batch,today:LocalDate=LocalDate.now()):String {
        if(b.status!=BatchStatus.ACTIVE) return when(b.status){
            BatchStatus.REMOVED->"مسحوب";BatchStatus.QUARANTINED->"معزول";BatchStatus.SOLD_OUT->"نفد";else->"نشط"
        }
        val days=daysLeft(b,today) ?: return "تاريخ غير معروف"
        return when {
            days<0 -> "منتهي"; days<=7 -> "خلال أسبوع";days<=30->"خلال شهر"
            days<=60->"خلال شهرين";days<=90->"خلال 3 أشهر";days<=120->"خلال 4 أشهر";else->"لاحق"
        }
    }
    fun alertCounts(data: AppData,today:LocalDate=LocalDate.now()):Map<String,Int> {
        val active=data.batches.filter(::isActive)
        return linkedMapOf(
            "expired" to active.count{expired(it,today)},
            "7" to active.count{dueWithin(it,7,today)},
            "30" to active.count{dueWithin(it,30,today)},
            "60" to active.count{dueWithin(it,60,today)},
            "90" to active.count{dueWithin(it,90,today)},
            "120" to active.count{dueWithin(it,120,today)},
            "unknown" to active.count{expiryDate(it)==null},
            "unverified" to active.count{!it.verified}
        )
    }
}
