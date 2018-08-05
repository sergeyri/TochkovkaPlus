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
        if(any != jsonObj){ log("putting ...")
            new.put(index, any)
        }
    }
    return new
}

fun JSONArray.removeAt(index: Int): JSONArray {
    val new = JSONArray()
    (0 until this.length()).map { this.getJSONObject(it) }.forEachIndexed { i, jsonObj ->
        if(index != i){ log("putting ...")
            new.put(jsonObj)
        }
    }
    return new
}
