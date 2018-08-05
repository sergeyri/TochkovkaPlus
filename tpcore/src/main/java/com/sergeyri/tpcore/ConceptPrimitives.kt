package com.sergeyri.tpcore

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by sergeyri on 8/31/17.
 */
open class Group {
    enum class GroupTheme(var bg: Int, var txt: Int) {
        C0(Color.rgb(0, 118, 255), Color.rgb(255, 255, 255)),
        C1(Color.rgb(237, 0, 215), Color.rgb(51, 7, 0)),
        C2(Color.rgb(241, 0, 0), Color.rgb(255, 251, 118)),
        C3(Color.rgb(241, 107, 0), Color.rgb(37, 120, 0)),
        C4(Color.rgb(0, 126, 5), Color.rgb(142, 255, 247)),
        C5(Color.rgb(151, 241, 0), Color.rgb(0, 38, 135)),
        C6(Color.rgb(0, 105, 97), Color.rgb(174, 213, 255)),
        C7(Color.rgb(25, 156, 255), Color.rgb(63, 0, 117)),
        C8(Color.rgb(75, 0, 237), Color.rgb(255, 146, 148))
    }

    class GroupMeta {
        companion object {
            val PREFIX: String = "gmi_"
            // keys
            val KEY_SID: String = "${PREFIX}sid"
            val KEY_TITLE: String = "${PREFIX}title"
            val KEY_THEME: String = "${PREFIX}theme"
            val KEY_PRICE: String = "${PREFIX}price"

            fun getDefaultTitle(context: Context): String
                    = context.resources.getString(R.string.def_groupSort0)

            fun toJson(groupMeta: GroupMeta): JSONObject { // сохранение в JSON
                val jsonObj = JSONObject()
                jsonObj.put(KEY_SID, groupMeta.sid)
                jsonObj.put(KEY_TITLE, groupMeta.title)
                jsonObj.put(KEY_THEME, groupMeta.theme.name)
                jsonObj.put(KEY_PRICE, groupMeta.price)
                return jsonObj
            }

            fun fromJson(jsonObj: JSONObject): GroupMeta { // восстановление из JSON
                val sid = jsonObj.getString(KEY_SID)
                val title = jsonObj.getString(KEY_TITLE)
                val theme: GroupTheme = when(jsonObj.getString(KEY_THEME)){
                    GroupTheme.C1.name -> GroupTheme.C1
                    GroupTheme.C2.name -> GroupTheme.C2
                    GroupTheme.C3.name -> GroupTheme.C3
                    GroupTheme.C4.name -> GroupTheme.C4
                    GroupTheme.C5.name -> GroupTheme.C5
                    GroupTheme.C6.name -> GroupTheme.C6
                    GroupTheme.C7.name -> GroupTheme.C7
                    GroupTheme.C8.name -> GroupTheme.C8
                    else -> GroupTheme.C0
                }
                val price = jsonObj.getDouble(KEY_PRICE)
                return GroupMeta(sid, title, theme, price)
            }
        }

        val sid: String
        var title: String
        var theme: GroupTheme
        var price: Double

        init{ SystemClock.sleep(1) }

        //for new object
        constructor(title: String, theme: GroupTheme = GroupTheme.C0, price: Double = 0.0) {
            this.sid = PREFIX + System.currentTimeMillis()
            this.title = title
            this.theme = theme
            this.price = price
        }
        //for restore object
        constructor(sid: String, title: String, theme: GroupTheme, price: Double){
            this.sid = sid
            this.title = title
            this.theme = theme
            this.price = price
        }
    }

    class GroupData {
        companion object {
            val PREFIX: String = "gd_"
            val KEY_SID: String = "${PREFIX}sid"
            val KEY_CAPACITY: String = "${PREFIX}capacity"
            val KEY_COUNT: String = "${PREFIX}count"
            val KEY_HISTORY_JSON: String = "${PREFIX}history"

            fun toJson(data: GroupData): JSONObject {
                val jsonObj = JSONObject()
                jsonObj.put(KEY_SID, data.sid)
                jsonObj.put(KEY_CAPACITY, data.capacity)
                jsonObj.put(KEY_COUNT, data.count)
                val hJsonArr = JSONArray()
                data.listH.forEach { hJsonArr.put(History.toJson(it)) }
                jsonObj.put(KEY_HISTORY_JSON, hJsonArr)
                return jsonObj
            }

            fun fromJson(jsonObj: JSONObject): GroupData {
                val sid = jsonObj.getString(KEY_SID)
                val capacity = jsonObj.getDouble(KEY_CAPACITY)
                val count = jsonObj.getInt(KEY_COUNT)
                val hJsonArr = jsonObj.getJSONArray(KEY_HISTORY_JSON)
                val listH: MutableList<History> = mutableListOf()
                (0 until hJsonArr.length()).map { hJsonArr.getJSONObject(it) }.forEach { listH.add(History.fromJson(it)) }
                return GroupData(sid, capacity, count, listH)
            }
        }

        val sid: String // sid объединяющий этот объект с GroupMeta
        var capacity: Double
        var count: Int
        val listH: MutableList<History> = mutableListOf()

        // for new
        constructor(sid: String, capacity: Double, count: Int=0){
            this.sid = sid
            this.capacity = capacity
            this.count = count
        }

        // for restore
        constructor(sid: String, capacity: Double, count: Int=0, listH: MutableList<History>){
            this.sid = sid
            this.capacity = capacity
            this.count = count
            this.listH.addAll(listH)
        }



        fun increment(createDate: Long){
            this.count += 1
            listH.add(History(sid, createDate))
        }

        fun decrement(createDate: Long){
            val removeIndex = listH.indexOfFirst { it.createDate == createDate }
            if(removeIndex > -1){
                this.count -= 1
                listH.removeAt(removeIndex)
            }
        }

        fun calculateVolume(): Double {
            return (capacity * count).round()
        }

        fun calculateCost(price: Double): Double {
            return (calculateVolume() * price).round(2)
        }

    }

    class History {
        companion object {
            val PREFIX: String = "h_"
            // keys
            val KEY_GROUPDATA_SID: String = "${PREFIX}groupdatasid"
            val KEY_CREATE_DATE: String = "${PREFIX}createdate"

            fun toJson(history: History): JSONObject {
                val jsonObj = JSONObject()
                jsonObj.put(KEY_CREATE_DATE, history.createDate)
                jsonObj.put(KEY_GROUPDATA_SID, history.sid)
                return jsonObj
            }

            fun fromJson(jsonObj: JSONObject): History {
                val createDate = jsonObj.getLong(KEY_CREATE_DATE)
                val sid = jsonObj.getString(KEY_GROUPDATA_SID)
                return History(sid, createDate)
            }
        }

        val sid: String
        val createDate: Long
        // for new
        constructor(groupDataSid: String){
            this.sid = groupDataSid
            this.createDate = System.currentTimeMillis()
        }
        //for restore
        constructor(groupDataSid: String, createDate: Long){
            this.sid = groupDataSid
            this.createDate = createDate
        }
    }
}



open class Sheet {
    enum class St{GOST_2708_75, ISO_4480_83}
    companion object {
        val PREFIX: String = "sh_"
        // keys
        val KEY_SID: String = "${PREFIX}sid"
        val KEY_CREATE_DATE: String = "${PREFIX}createdate"
        val KEY_FML_FILTER: String = "${PREFIX}fml"
        val KEY_CHANGE_DATE: String = "${PREFIX}changedate"
        val KEY_TITLE: String = "${PREFIX}title"
        val KEY_UNIT: String = "${PREFIX}unit"
        val KEY_GROUPINFO_LIST: String = "${PREFIX}groupinfolist"
        val KEY_EXT: String = "${PREFIX}ext"
        val KEY_COMMENT: String = "${PREFIX}comment"
        val KEY_PRICE_ENABLED = "${PREFIX}priceenabled"

        val KEY_GS = "${PREFIX}gs"
        val GS_MIN = "gs_min"
        val GS_MAX = "gs_max"
        val FML = "fml_filter"
        val FML_ROUNDWOOD: String = "fml_rw"
        val FML_UNIVERSAL: String = "fml_universal"
        const val KEY_STANDART: String = "rw_standart"
        const val KEY_LENGTH: String = "rw_length"

        fun toJson(sheet: Sheet): JSONObject {
            val jsonObj = JSONObject()
            jsonObj.put(KEY_SID, sheet.sid)
            jsonObj.put(KEY_CREATE_DATE, sheet.createDate)
            jsonObj.put(KEY_FML_FILTER, sheet.family)
            jsonObj.put(KEY_CHANGE_DATE, sheet.changeDate)
            jsonObj.put(KEY_TITLE, sheet.title)
            jsonObj.put(KEY_UNIT, sheet.unit)
            val giJsonArr = JSONArray()
            sheet.listGMI.forEach { giJsonArr.put(Group.GroupMeta.toJson(it)) }
            jsonObj.put(KEY_GROUPINFO_LIST, giJsonArr)
            jsonObj.put(KEY_EXT, sheet.ext)
            jsonObj.put(KEY_COMMENT, sheet.comment)
            jsonObj.put(KEY_PRICE_ENABLED, sheet.priceEnabled)
            return jsonObj
        }

        fun fromJson(jsonObj: JSONObject): Sheet {
            val createDate = jsonObj.getLong(KEY_CREATE_DATE)
            val family = jsonObj.getString(KEY_FML_FILTER)
            val changeDate = jsonObj.getLong(KEY_CHANGE_DATE)
            val title = jsonObj.getString(KEY_TITLE)
            val unit = jsonObj.getString(KEY_UNIT)
            val gmiJsonArr = jsonObj.getJSONArray(KEY_GROUPINFO_LIST)
            val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
            (0 until gmiJsonArr.length()).map { gmiJsonArr.getJSONObject(it) }.forEach { listGMI.add(Group.GroupMeta.fromJson(it)) }
            val ext = jsonObj.getJSONObject(KEY_EXT)
            val comment = jsonObj.getString(KEY_COMMENT)
            val priceEnabled = jsonObj.getBoolean(KEY_PRICE_ENABLED)
            return Sheet(createDate, family, changeDate, title, unit, listGMI, ext, comment, priceEnabled)
        }

    }

    val sid: String
    val createDate: Long
    var family: String
    var changeDate: Long
    var title: String
    var unit: String
    val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
    var ext: JSONObject
    var comment: String
    var priceEnabled: Boolean = false

    // for new object
    constructor(title: String, unit: String, tmpListGMI: List<Group.GroupMeta>, family: String){
        this.createDate = System.currentTimeMillis()
        this.sid = PREFIX + this.createDate
        this.family = family
        this.changeDate = this.createDate
        this.title = title
        this.unit = unit
        if(tmpListGMI.isNotEmpty()){
            this.listGMI.addAll(tmpListGMI)
        }
        this.ext = JSONObject()
        this.comment = ""
    }
    //for restore object
    constructor(createDate: Long, family: String, changeDate: Long, title: String, unit: String, listGMI: List<Group.GroupMeta>, ext: JSONObject, comment: String, priceEnabled: Boolean) {
        this.createDate = createDate
        this.sid = PREFIX + this.createDate
        this.family = family
        this.changeDate = changeDate
        this.title = title
        this.unit = unit
        this.listGMI.addAll(listGMI)
        this.ext = ext
        this.comment = comment
        this.priceEnabled = priceEnabled
    }

}

open class Component {
    companion object {
        val PREFIX = "comp_"
        //keys
        val KEY_SID = "${PREFIX}sid"
        val KEY_CREATE_DATE = "${PREFIX}createdate"
        val KEY_CHANGE_DATE = "${PREFIX}changedate"
        val KEY_TITLE = "${PREFIX}title"
        val KEY_GROUPDATA_LIST = "${PREFIX}groupdatalist"

        fun toJson(component: Component): JSONObject {
            val jsonObj = JSONObject()
            jsonObj.put(KEY_CREATE_DATE, component.createDate)
            jsonObj.put(KEY_CHANGE_DATE, component.changeDate)
            jsonObj.put(KEY_TITLE, component.title)
            val gdJsonArr = JSONArray()
            component.listGD.forEach { gdJsonArr.put(Group.GroupData.toJson(it)) }
            jsonObj.put(KEY_GROUPDATA_LIST, gdJsonArr)
            return jsonObj
        }

        fun fromJson(jsonObj: JSONObject): Component {
            val createDate = jsonObj.getLong(KEY_CREATE_DATE)
            val changeDate = jsonObj.getLong(KEY_CHANGE_DATE)
            val title = jsonObj.getString(KEY_TITLE)
            val gdJsonArr = jsonObj.getJSONArray(KEY_GROUPDATA_LIST)
            val listGD: MutableList<Group.GroupData> = mutableListOf()
            (0 until gdJsonArr.length()).map { gdJsonArr.getJSONObject(it) }.forEach { listGD.add(Group.GroupData.fromJson(it)) }
            return Component(createDate, changeDate, title, listGD)
        }
    }

    val sid: String
    val createDate: Long
    var changeDate: Long
    var title: String
    val listGD: MutableList<Group.GroupData> = mutableListOf()

    //for new
    constructor(title: String, listGD: List<Group.GroupData>){
        val date = System.currentTimeMillis()
        this.sid = PREFIX + date
        this.createDate = date
        this.changeDate = date
        this.title = title
        this.listGD.addAll(listGD)
    }
    //for restore
    constructor(createDate: Long, changeDate: Long, title: String, listGD: List<Group.GroupData>){
        this.sid = PREFIX + createDate
        this.createDate = createDate
        this.changeDate = changeDate
        this.title = title
        this.listGD.addAll(listGD)
    }

    fun calculateCountAt(gmiList: List<Group.GroupMeta>): Int {
        var count = 0
        gmiList.forEach {
            val groupData = findDataOf(it.sid)
            if(groupData != null){
                count += groupData.count
            } else{log("error GroupData by sid ${it.sid} not found!")}
        }
        return count
    }

    fun calculateVolumeAt(gmiList: List<Group.GroupMeta>): Double {
        var volume = 0.0
        gmiList.forEach {
            val groupData = findDataOf(it.sid)
            if(groupData != null){
                volume += groupData.calculateVolume()
            } else{log("error GroupData by sid ${it.sid} not found!")}
        }
        return volume.round()
    }

    fun calculateCostAt(gmiList: List<Group.GroupMeta>): Double {
        var cost = 0.0
        gmiList.forEach {
            val groupData = findDataOf(it.sid)
            if(groupData != null){
                cost += (groupData.calculateVolume() * it.price)
            } else{log("error GroupData by sid ${it.sid} not found!")}
        }
        return cost.round(2)
    }

    fun compileHistory(): List<Group.History> {
        val history: MutableList<Group.History> = mutableListOf()
        listGD.forEach { history.addAll(it.listH) }
        history.sortByDescending { it.createDate }
        return history
    }

    fun findDataOf(gmiSid: String): Group.GroupData? = listGD.find { it.sid == gmiSid }
}

class Tmpl {
    companion object {
        val PREFIX: String = "tmpl_"
        // keys
        val KEY_SID: String = "${PREFIX}sid"
        val KEY_CREATE_DATE: String = "${PREFIX}createdate"
        val KEY_FML_FILTER: String = "${PREFIX}fml"
        val KEY_CHANGE_DATE: String = "${PREFIX}changedate"
        val KEY_TITLE: String = "${PREFIX}title"
        val KEY_UNIT: String = "${PREFIX}unit"
        val KEY_GROUPINFO_LIST: String = "${PREFIX}groupinfolist"
        val KEY_EXT: String = "${PREFIX}ext"
        val KEY_PRICE_ENABLED = "${PREFIX}priceenabled"

        fun toJson(tmpl: Tmpl): JSONObject {
            val jsonObj = JSONObject()
            jsonObj.put(KEY_SID, tmpl.sid)
            jsonObj.put(KEY_CREATE_DATE, tmpl.createDate)
            jsonObj.put(KEY_FML_FILTER, tmpl.family)
            jsonObj.put(KEY_CHANGE_DATE, tmpl.changeDate)
            jsonObj.put(KEY_TITLE, tmpl.title)
            jsonObj.put(KEY_UNIT, tmpl.unit)
            val gmiJsonArr = JSONArray()
            tmpl.listGMI.forEach { gmiJsonArr.put(Group.GroupMeta.toJson(it)) }
            jsonObj.put(KEY_GROUPINFO_LIST, gmiJsonArr)
            jsonObj.put(KEY_EXT, tmpl.ext)
            jsonObj.put(KEY_PRICE_ENABLED, tmpl.priceEnabled)
            return jsonObj
        }

        fun fromJson(jsonObj: JSONObject): Tmpl {
            val createDate = jsonObj.getLong(KEY_CREATE_DATE)
            val family = jsonObj.getString(KEY_FML_FILTER)
            val changeDate = jsonObj.getLong(KEY_CHANGE_DATE)
            val title = jsonObj.getString(KEY_TITLE)
            val unit = jsonObj.getString(KEY_UNIT)
            val gmiJsonArr = jsonObj.getJSONArray(KEY_GROUPINFO_LIST)
            val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
            (0 until gmiJsonArr.length()).map { gmiJsonArr.getJSONObject(it) }.forEach { listGMI.add(Group.GroupMeta.fromJson(it)) }
            val ext = jsonObj.getJSONObject(KEY_EXT)
            val priceEnabled = jsonObj.getBoolean(KEY_PRICE_ENABLED)
            return Tmpl(createDate, family, changeDate, title, unit, listGMI, ext, priceEnabled)
        }
    }

    val sid: String
    val createDate: Long
    var family: String
    var changeDate: Long
    var title: String
    var unit: String
    val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
    var ext: JSONObject
    var priceEnabled: Boolean = false

    init{ SystemClock.sleep(1) }

    constructor(createDate: Long, family: String, changeDate: Long, title: String, unit: String, listGMI: List<Group.GroupMeta>, ext: JSONObject, priceEnabled: Boolean){
        this.createDate = createDate
        this.sid = PREFIX + this.createDate
        this.family = family
        this.changeDate = changeDate
        this.title = title
        this.unit = unit
        this.listGMI.addAll(listGMI)
        this.ext = ext
        this.priceEnabled = priceEnabled
    }

    constructor(sheet: Sheet){
        this.createDate = System.currentTimeMillis()
        this.sid = PREFIX + this.createDate
        this.family = sheet.family
        this.changeDate = this.createDate
        this.title = sheet.title
        this.unit = sheet.unit
        this.listGMI.addAll(sheet.listGMI)
        this.ext = sheet.ext
        this.priceEnabled = sheet.priceEnabled
    }
}

class TmplComponent {
    companion object {
        val PREFIX: String = "tp_"
        // keys
        val KEY_SID: String = "${PREFIX}sid"
        val KEY_CHANGE_DATE: String = "${PREFIX}changedate"
        val KEY_TITLE: String = "${PREFIX}title"
        val KEY_GROUPDATA_LIST: String = "${PREFIX}groupdatalist"
    }

    val sid: String
    var changeDate: Long
    var title: String
    val listGD: MutableList<Group.GroupData> = mutableListOf()

    init{ SystemClock.sleep(1) }

    constructor(sid: String, changeDate: Long, title: String, listGD: List<Group.GroupData>){
        this.sid = sid
        this.changeDate = changeDate
        this.title = title
        this.listGD.addAll(listGD)
    }

    fun findDataOf(gmiSid: String): Group.GroupData? = listGD.find { it.sid == gmiSid }
}
