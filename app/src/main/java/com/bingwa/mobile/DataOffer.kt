package com.bingwa.mobile

enum class TransactionStatus(val value: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    RETRYING("Retrying");

    companion object {
        fun fromString(text: String): TransactionStatus {
            return entries.find { it.value.equals(text, ignoreCase = true) } ?: PENDING
        }
    }
}

data class DataOffer(
    val name: String,
    val price: Int,
    val ussdCode: String,
    val executionMode: String = "SIMPLE",
    val mode: String = "daily"
)

data class Transaction(
    val id: Int = 0,
    val description: String,
    val amount: String,
    val amountValue: Double = 0.0,
    val date: String,
    val status: String,
    val statusEnum: TransactionStatus = TransactionStatus.PENDING,
    val ussdCode: String = "",
    val phoneNumber: String = "",
    val response: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
