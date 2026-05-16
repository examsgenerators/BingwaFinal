package com.bingwa.mobile

data class DataOffer(
    val name: String,
    val price: Int,
    val ussdCode: String,
    val executionMode: String = "SIMPLE",
    val mode: String = "daily"
)

data class Transaction(
    val description: String,
    val amount: String,
    val date: String,
    val status: String
)
