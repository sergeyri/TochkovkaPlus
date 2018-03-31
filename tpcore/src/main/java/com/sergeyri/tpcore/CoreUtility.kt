package com.sergeyri.tpcore

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal


/**
 * Created by sergeyri on 8/27/17.
 */
fun log(msg: String){ Log.d("Talitha", msg) }

fun Double.round(tail: Int=5): Double {
    return BigDecimal.valueOf(this).setScale(tail, BigDecimal.ROUND_HALF_DOWN).toDouble()
}

fun JSONArray.removeObject(jsonObj: JSONObject): JSONArray {
    val new = JSONArray()
    (0 until this.length()).map { this[it] }.forEachIndexed { index, any ->
        log("JSON0: $any, $jsonObj")
        if(any != jsonObj){ log("putting ...")
            this.put(index, any)
        }
    }
    return new
}


