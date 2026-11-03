package com.rukn.core

import java.time.LocalDate
import java.time.Instant
import java.util.UUID

/** Identity and source fields are separated from per-lot observations. No user input is mandatory. */
data class Product(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val brand: String = "",
    val size: String = "",
    val unitsPerCarton: String = "",
    val category: String = "",
    val aisle: String = "",
    val shelf: String = "",
    val notes: String = "",
    val source: String = "manual",
    val sourceUrl: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
) {
    val displayName: String get() = nameAr.ifBlank { nameEn.ifBlank { brand.ifBlank { "منتج غير مسمى" } } }
}

enum class BatchStatus { ACTIVE, REMOVED, QUARANTINED, SOLD_OUT }
enum class DatePrecision { DAY, MONTH, UNKNOWN }
enum class DateKind { EXPIRY, BEST_BEFORE }

data class Batch(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val lot: String = "",
    val productionDate: String = "", // YYYY-MM-DD, or YYYY-MM for month precision
    val expiryDate: String = "",
    val precision: DatePrecision = DatePrecision.DAY,
    val dateKind: DateKind = DateKind.EXPIRY,
    val quantity: String = "", // text; no lossy numeric conversion
    val unit: String = "",
    val location: String = "",
    val notes: String = "",
    val status: BatchStatus = BatchStatus.ACTIVE,
    val verified: Boolean = false,
    val lastCheckedAt: String = "",
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
)

enum class TaskStatus { OPEN, DONE }
data class InspectionTask(
    val id: String = UUID.randomUUID().toString(),
    val batchId: String,
    val title: String,
    val status: TaskStatus = TaskStatus.OPEN,
    val dueDate: String = "",
    val notes: String = "",
    val createdAt: String = Instant.now().toString(),
    val closedAt: String = "",
)

data class AuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val action: String,
    val before: String = "",
    val after: String = "",
    val at: String = Instant.now().toString(),
)

data class AppData(
    val products: List<Product> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val tasks: List<InspectionTask> = emptyList(),
    val audit: List<AuditEntry> = emptyList(),
    val settings: Map<String, String> = mapOf("alertDays" to "7,30,60,90,120"),
    val catalog: List<CatalogProduct> = emptyList(),
    val catalogAliases: List<CatalogBarcodeAlias> = emptyList(),
)

fun Product.matchesBarcode(code: String): Boolean = barcode.isNotBlank() && barcode == code.trim()
