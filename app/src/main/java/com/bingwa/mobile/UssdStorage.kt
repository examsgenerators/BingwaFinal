package com.bingwa.mobile

import android.content.Context

class UssdStorage(private val context: Context) {
    val definedUssds: Map<Double, String> = mapOf(
        5.0 to "*188*8*1*1*pn*1*2#", 10.0 to "*188*8*1*2*pn*1*2#",
        20.0 to "*180*5*2*pn*6*1#", 55.0 to "*180*5*2*pn*8*1#",
        19.0 to "*180*5*2*pn*5*1#", 99.0 to "*180*5*2*pn*7*1#",
        49.0 to "*180*5*2*pn*2*1#", 50.0 to "*180*5*2*pn*1*1#",
        30.0 to "*188*8*2*2*pn*1*2#", 25.0 to "*180*5*2*pn*1*1#",
        130.0 to "*188#", 200.0 to "*544*4#", 480.0 to "*544*5#",
        250.0 to "*180*5*2*pn*0*1*1#", 700.0 to "*180*5*2*pn*4*1#",
        300.0 to "*180*5*2*pn*3*1#", 500.0 to "*180*5*2*pn*0*2*1#"
    )
    val defaultLabels: Map<Double, String> = mapOf(
        5.0 to "20 SMS Daily", 10.0 to "200 SMS Daily", 20.0 to "250MB Daily",
        50.0 to "1.5GB 3Hrs", 100.0 to "Monthly SMS", 130.0 to "Airtel Weekly",
        200.0 to "Big Bundle", 480.0 to "Mega Bundle", 25.0 to "2GB DABO DABO",
        55.0 to "1.25GB Till Midnight", 19.0 to "1GB Valid 1 Hour",
        30.0 to "1000 Weekly SMS", 250.0 to "1.2GB Monthly", 700.0 to "6GB 7 Days",
        300.0 to "2.5GB Weekly", 500.0 to "2.5GB Monthly"
    )
    fun getUssdForAmount(amount: Double): String? = definedUssds[amount]
    fun getLabels(): Map<Double, String> = defaultLabels
}
