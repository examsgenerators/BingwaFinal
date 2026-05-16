package com.bingwa.mobile

import android.content.Context

class UssdStorage(private val context: Context) {
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
        22.0 to "*444*5*2*pppp*0*6*1#", 53.0 to "*444*5*2*pppp*0*6*1#",
        130.0 to "*188#", 200.0 to "*544*4#", 480.0 to "*544*5#"
    )
    val defaultLabels: Map<Double, String> = mapOf(
        5.0 to "20 SMS Daily", 10.0 to "200 SMS Daily", 20.0 to "250MB Daily",
        50.0 to "1.5GB 3Hrs", 100.0 to "Monthly SMS", 130.0 to "Airtel Weekly",
        200.0 to "Big Bundle", 480.0 to "Mega Bundle", 25.0 to "2GB DABO DABO"
    )
    fun getUssdForAmount(amount: Double): String? = definedUssds[amount]
    fun getLabels(): Map<Double, String> = defaultLabels
}
