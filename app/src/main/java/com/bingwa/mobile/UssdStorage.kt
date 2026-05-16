package com.bingwa.mobile

import android.content.Context
import android.content.SharedPreferences

class UssdStorage(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ussd_prefs", Context.MODE_PRIVATE)

    val definedUssds: Map<Double, String> = mapOf(
        5.0 to "*188*8*1*1*pppp*1*2#", 10.0 to "*188*8*1*2*pppp*1*2#",
        20.0 to "*180*5*2*pppp*6*1#", 55.0 to "*180*5*2*pppp*8*1#",
        19.0 to "*180*5*2*pppp*5*1#", 99.0 to "*180*5*2*pppp*7*1#",
        49.0 to "*180*5*2*pppp*2*1#", 50.0 to "*180*5*2*pppp*1*1#",
        51.0 to "*456*3*5*1*3*pppp*2*1#", 30.0 to "*188*8*2*2*pppp*1*2#",
        250.0 to "*180*5*2*pppp*0*1*1#", 700.0 to "*180*5*2*pppp*4*1#",
        300.0 to "*180*5*2*pppp*3*1#", 25.0 to "*180*5*2*pppp*1*1#",
        201.0 to "*188*8*3*2*pppp*1*1#", 500.0 to "*180*5*2*pppp*0*2*1#",
        1001.0 to "*180*5*2*pppp*0*6*1#", 21.0 to "*100*3*5*1*8*2*pppp*1#",
        52.0 to "*100*3*5*1*8*3*pppp*1#", 120.0 to "*100*3*5*1*8*1*pppp*1#",
        23.0 to "*444#*3*1*pppp*1*1#", 54.0 to "*444*5*2*pppp*0*6*1#",
        22.0 to "*444*5*2*pppp*0*6*1#", 53.0 to "*444*5*2*pppp*0*6*1#"
    )

    val defaultLabels: Map<Double, String> = mapOf(
        5.0 to "20 SMS Daily", 10.0 to "200 SMS Daily", 20.0 to "250MBs@20 Valid 24 Hours",
        55.0 to "1.25GB@55 Till Midnight", 19.0 to "1GB@19 Valid 1 Hour", 99.0 to "1GB@99 Valid 24 Hours",
        49.0 to "350MB Weekly", 50.0 to "1.5GB@50 Valid 3 Hours", 51.0 to "Minutes Daily",
        30.0 to "1000 Weekly SMS", 250.0 to "1.2GB Monthly", 700.0 to "6GB 7 Days",
        300.0 to "2.5GB Weekly", 25.0 to "2GB DABO DABO", 201.0 to "Monthly SMS",
        500.0 to "2.5GB Monthly", 1001.0 to "10GB Monthly", 52.0 to "Sh50 1.5GB 3HRS",
        22.0 to "Sh20 45 Mins 3hrs", 21.0 to "Sh20 1GB 1hr", 53.0 to "Sh50 150 Bob Kredo Midnight",
        120.0 to "Sh100 2GB 1hr", 23.0 to "45 Minutes 3hrs", 54.0 to "50 Minutes Midnight"
    )

    fun getUssds(): Map<Double, String> = definedUssds
    fun getLabels(): Map<Double, String> = defaultLabels

    fun getUssdForAmount(amount: Double): String? {
        return definedUssds[amount]
    }
}
