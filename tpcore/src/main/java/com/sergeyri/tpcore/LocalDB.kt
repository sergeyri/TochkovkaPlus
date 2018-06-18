package com.sergeyri.tpcore

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Created by sergeyri on 9/3/17.
 */
class LocalDB(context: Context) : TPNode.DatabaseActions {
    companion object {
        const val DBNAME = "TPv2.db"
        const val DBVERSION = 2
        const val TP_SHEETINFO = "tp_sheetorder"
        const val TP_TMPLINFO = "tp_tmplorder"
    }

    object Old {
        val TBL_INFO_LIST_NAME = "table_info_list_name"//таблица списка таблиц-точковок
        val TBL_PREFIX = "tpv2_tbl_"//префикс имени таблицы-точковки
        val TMPL_INFO_LIST_NAME = "template_info_list_name"//таблица списка таблиц-шаблонов
        val TMPL_PREFIX = "tpv2_tmpl_"//префикс имени таблицы-шаблона

        val COL_TITLE = "title"//название

        val COL_DATE = "date"// временная метка создания таблицы-точковки
        val COL_SYSTEM_NAME = "system_name"//системное имя таблицы-точковки (в sqlite будет создана отдельная таблица с этим именем)

        val COL_TYPE_T = "type_t"//тип точковки (лес, другое)
        val COL_ED = "ed"//единица измерения, применяемая в таблице-точковке
        val COL_COMMENT = "comment" // комментарий к таблице
        val COL_WOOD_LENGTH = "wood_length"
        val COL_WOOD_STANDART = "wood_standart"

        val COL_CAPACITY = "capacity"
        val COL_C0_ENABLED = "c0_enabled"
        val COL_C1_ENABLED = "c1_enabled"
        val COL_C2_ENABLED = "c2_enabled"
        val COL_C3_ENABLED = "c3_enabled"
        val COL_C4_ENABLED = "c4_enabled"
        val COL_C5_ENABLED = "c5_enabled"
        val COL_C0_COUNT = "c0_count"
        val COL_C1_COUNT = "c1_count"
        val COL_C2_COUNT = "c2_count"
        val COL_C3_COUNT = "c3_count"
        val COL_C4_COUNT = "c4_count"
        val COL_C5_COUNT = "c5_count"

        fun gi0Title(context: Context): String = context.resources.getString(R.string.def_groupSort0)
        fun gi1Title(context: Context, family: String): String = context.resources.getString(if(family == Sheet.FML_ROUNDWOOD) R.string.def_rwGroupSort1 else R.string.def_univGroupSort1)
        fun gi2Title(context: Context, family: String): String = context.resources.getString(if(family == Sheet.FML_ROUNDWOOD) R.string.def_rwGroupSort2 else R.string.def_univGroupSort2)
        fun gi3Title(context: Context, family: String): String = context.resources.getString(if(family == Sheet.FML_ROUNDWOOD) R.string.def_rwGroupSort3 else R.string.def_univGroupSort3)
        fun gi4Title(context: Context, family: String): String = context.resources.getString(if(family == Sheet.FML_ROUNDWOOD) R.string.def_rwGroupSort4 else R.string.def_univGroupSort4)
        fun gi5Title(context: Context, family: String): String = context.resources.getString(if(family == Sheet.FML_ROUNDWOOD) R.string.def_rwGroupSort5 else R.string.def_univGroupSort5)
    }


    override fun getSheetlist(familyName: String): List<Sheet> {
        val sheetList: MutableList<Sheet> = mutableListOf()
        open()
        val cursor = get(TP_SHEETINFO, arrayOf("*"), "${Sheet.KEY_FML_FILTER}=?", arrayOf(familyName), "${Sheet.KEY_CREATE_DATE} ASC")
        if(cursor != null){
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast){
                    val createDate = cursor.getLong(cursor.getColumnIndex(Sheet.KEY_CREATE_DATE))
                    val family = cursor.getString(cursor.getColumnIndex(Sheet.KEY_FML_FILTER))
                    val changeDate = cursor.getLong(cursor.getColumnIndex(Sheet.KEY_CHANGE_DATE))
                    val title = cursor.getString(cursor.getColumnIndex(Sheet.KEY_TITLE))
                    val unit = cursor.getString(cursor.getColumnIndex(Sheet.KEY_UNIT))
                    val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
                    val jarr = JSONArray(cursor.getString(cursor.getColumnIndex(Sheet.KEY_GROUPINFO_LIST)))
                    (0 until jarr.length()).mapTo(listGMI) { Group.GroupMeta.fromJson(jarr[it] as JSONObject) }
                    val ext = JSONObject(cursor.getString(cursor.getColumnIndex(Sheet.KEY_EXT)))
                    val comment = cursor.getString(cursor.getColumnIndex(Sheet.KEY_COMMENT))
                    val priceEnabled = (cursor.getInt(cursor.getColumnIndex(Sheet.KEY_PRICE_ENABLED)) == 1)
                    val sheet = Sheet(createDate, family, changeDate, title, unit, listGMI, ext, comment, priceEnabled)
                    sheetList.add(sheet)
                    cursor.moveToNext()
                }
            }
            cursor.close()
        }
        close()
        return sheetList
    }

    override fun createSheet(sheet: Sheet): Boolean {
        var result = false
        val columns: Array<String> = arrayOf(
                "${Component.KEY_SID} TEXT",
                "${Component.KEY_CREATE_DATE} INTEGER",
                "${Component.KEY_CHANGE_DATE} INTEGER",
                "${Component.KEY_TITLE} TEXT",
                "${Component.KEY_GROUPDATA_LIST} MEDIUMTEXT"
        )

        open()
        if(createSQLiteTable(sheet.sid, columns)){
            val values = ContentValues()
            values.put(Sheet.KEY_SID, sheet.sid)
            values.put(Sheet.KEY_CREATE_DATE, sheet.createDate)
            values.put(Sheet.KEY_FML_FILTER, sheet.family)
            values.put(Sheet.KEY_CHANGE_DATE, sheet.changeDate)
            values.put(Sheet.KEY_TITLE, sheet.title)
            values.put(Sheet.KEY_UNIT, sheet.unit)
            val jarr = JSONArray()
            sheet.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
            values.put(Sheet.KEY_GROUPINFO_LIST, jarr.toString())
            values.put(Sheet.KEY_EXT, sheet.ext.toString())
            values.put(Sheet.KEY_COMMENT, sheet.comment)
            values.put(Sheet.KEY_PRICE_ENABLED, sheet.priceEnabled)
            val id = ins(TP_SHEETINFO, values)
            result = (id > -1)
        }
        close()
        return result
    }

    override fun editSheet(sheet: Sheet): Boolean {
        val values = ContentValues()
        values.put(Sheet.KEY_CHANGE_DATE, sheet.changeDate)
        values.put(Sheet.KEY_TITLE, sheet.title)
        values.put(Sheet.KEY_UNIT, sheet.unit)
        val jarr = JSONArray()
        sheet.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
        values.put(Sheet.KEY_GROUPINFO_LIST, jarr.toString())
        values.put(Sheet.KEY_EXT, sheet.ext.toString())
        values.put(Sheet.KEY_COMMENT, sheet.comment)
        values.put(Sheet.KEY_PRICE_ENABLED, sheet.priceEnabled)
        open()
        val result = upd(TP_SHEETINFO, values, "${Sheet.KEY_SID}=?", arrayOf(sheet.sid)) > 0
        close()
        return result
    }

    override fun deleteSheet(sheets: List<Sheet>): Boolean {
        val successList: MutableList<Boolean> = mutableListOf()
        open()
        sheets.forEach {
            if(deleteSQLiteTable(it.sid)) { // очистка данных и удаление таблицы
                successList.add(del(TP_SHEETINFO, "${Sheet.KEY_SID}=?", arrayOf(it.sid)) > 0) // удаление информации о таблице
            }
        }
        close()
        return (sheets.size == successList.size)
    }

    override fun getComponentList(sheetSid: String, callback: TPNode.ComponentCallback?): List<Component> {
        val componentList: MutableList<Component> = mutableListOf()
        open()
        val cursor = get(sheetSid, arrayOf("*"), null, null, "${Component.KEY_SID} ASC")
        if(cursor != null){
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast){
                    val createDate = cursor.getLong(cursor.getColumnIndex(Component.KEY_CREATE_DATE))
                    val changeDate = cursor.getLong(cursor.getColumnIndex(Component.KEY_CHANGE_DATE))
                    val title = cursor.getString(cursor.getColumnIndex(Component.KEY_TITLE))
                    val listGD: MutableList<Group.GroupData> = mutableListOf()
                    val jarrGD = JSONArray(cursor.getString(cursor.getColumnIndex(Component.KEY_GROUPDATA_LIST)))
                    (0 until jarrGD.length()).mapTo(listGD) { Group.GroupData.fromJson(jarrGD[it] as JSONObject) }
                    val component = Component(createDate, changeDate, title, listGD)

                    if(callback != null){
                        if(callback.preLoadFilter(component)){
                            componentList.add(component)
                        }
                    } else{
                        componentList.add(component)
                    }
                    cursor.moveToNext()
                }
            }
            cursor.close()
        }
        close()
        return componentList
    }

    override fun createComponent(sheetSid: String, component: Component): Boolean {
        open()
        val values = ContentValues()
        values.put(Component.KEY_SID, component.sid)
        values.put(Component.KEY_CREATE_DATE, component.createDate)
        values.put(Component.KEY_CHANGE_DATE, component.changeDate)
        values.put(Component.KEY_TITLE, component.title)
        val jarrGD = JSONArray()
        component.listGD.forEach { jarrGD.put(Group.GroupData.toJson(it)) }
        values.put(Component.KEY_GROUPDATA_LIST, jarrGD.toString())
        val result = ins(sheetSid, values) > -1
        close()
        return result
    }

    override fun editComponent(sheetSid: String, component: Component): Boolean {
        open()
        val values = ContentValues()
        values.put(Component.KEY_TITLE, component.title)
        values.put(Component.KEY_CHANGE_DATE, component.changeDate)
        val jarrGD = JSONArray()
        component.listGD.forEach { jarrGD.put(Group.GroupData.toJson(it)) }
        values.put(Component.KEY_GROUPDATA_LIST, jarrGD.toString())
        val result = upd(sheetSid, values, "${Component.KEY_SID}=?", arrayOf(component.sid)) > 0
        close()
        return result
    }

    override fun deleteComponent(sheetSid: String, components: List<Component>): Boolean {
        val successList: MutableList<Boolean> = mutableListOf()
        open()
        components.forEach {
            successList.add(del(sheetSid, "${Component.KEY_SID}=?", arrayOf(it.sid)) > 0)
        }
        close()
        return (components.size == successList.size)
    }

    override fun incrementComponent(sheetSid: String, componentSid: String, jsonArrayGD: JSONArray): Boolean {
        val values = ContentValues()
        values.put(Component.KEY_GROUPDATA_LIST, jsonArrayGD.toString())
        open()
        val result = (upd(sheetSid, values, "${Component.KEY_SID}=?", arrayOf(componentSid)) > 0)
        close()
        return result
    }

    override fun decrementComponent(sheetSid: String, componentSid: String, jsonArrayGD: JSONArray): Boolean {
        val values = ContentValues()
        values.put(Component.KEY_GROUPDATA_LIST, jsonArrayGD.toString())
        open()
        val result = upd(sheetSid, values, "${Component.KEY_SID}=?", arrayOf(componentSid)) > 0
        close()
        return result
    }

    override fun clearDataComponent(sheetSid: String, components: List<Component>): Boolean {
        val successList: MutableList<Boolean> = mutableListOf()
        open()
        components.forEach {
            val jsonArrayGD = JSONArray()
            it.listGD.forEach {
                it.count = 0
                it.listH.clear()
                jsonArrayGD.put(Group.GroupData.toJson(it))
            }

            val values = ContentValues()
            values.put(Component.KEY_GROUPDATA_LIST, jsonArrayGD.toString())
            successList.add(upd(sheetSid, values, "${Component.KEY_SID}=?", arrayOf(it.sid)) > 0)
        }
        close()
        return (components.size == successList.size)
    }

	override fun getTmpllist(familyName: String): List<Tmpl> {
        val tmplList: MutableList<Tmpl> = mutableListOf()
        open()
        val cursor = get(TP_TMPLINFO, arrayOf("*"), "${Tmpl.KEY_FML_FILTER}=?", arrayOf(familyName), "${Tmpl.KEY_CREATE_DATE} ASC")
        if(cursor != null){
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast){
                    val createDate = cursor.getLong(cursor.getColumnIndex(Tmpl.KEY_CREATE_DATE))
                    val family = cursor.getString(cursor.getColumnIndex(Tmpl.KEY_FML_FILTER))
                    val changeDate = cursor.getLong(cursor.getColumnIndex(Tmpl.KEY_CHANGE_DATE))
                    val title = cursor.getString(cursor.getColumnIndex(Tmpl.KEY_TITLE))
                    val unit = cursor.getString(cursor.getColumnIndex(Tmpl.KEY_UNIT))
                    val listGI: MutableList<Group.GroupMeta> = mutableListOf()
                    val jarr = JSONArray(cursor.getString(cursor.getColumnIndex(Tmpl.KEY_GROUPINFO_LIST)))
                    (0 until jarr.length()).mapTo(listGI) { Group.GroupMeta.fromJson(jarr[it] as JSONObject) }
                    val ext = JSONObject(cursor.getString(cursor.getColumnIndex(Tmpl.KEY_EXT)))
                    val priceEnabled = (cursor.getInt(cursor.getColumnIndex(Tmpl.KEY_PRICE_ENABLED)) == 1)
                    tmplList.add(Tmpl(createDate, family, changeDate, title, unit, listGI, ext, priceEnabled))
                    cursor.moveToNext()
                }
            }
            cursor.close()
        }
        close()
        return tmplList
    }

    override fun createTmpl(tmpl: Tmpl): Boolean {
        var result = false
        val columns: Array<String> = arrayOf(
                "${TmplComponent.KEY_SID} TEXT",
                "${TmplComponent.KEY_CHANGE_DATE} INTEGER",
                "${TmplComponent.KEY_TITLE} TEXT",
                "${TmplComponent.KEY_GROUPDATA_LIST} MEDIUMTEXT"
        )

        open()
        if(createSQLiteTable(tmpl.sid, columns)){
            /* регистрация шаблона в стеке */
            val values = ContentValues()
            values.put(Tmpl.KEY_SID, tmpl.sid)
            values.put(Tmpl.KEY_CREATE_DATE, tmpl.createDate)
            values.put(Tmpl.KEY_FML_FILTER, tmpl.family)
            values.put(Tmpl.KEY_CHANGE_DATE, tmpl.changeDate)
            values.put(Tmpl.KEY_TITLE, tmpl.title)
            values.put(Tmpl.KEY_UNIT, tmpl.unit)
            val jarr = JSONArray()
            tmpl.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
            values.put(Tmpl.KEY_GROUPINFO_LIST, jarr.toString())
            values.put(Tmpl.KEY_EXT, tmpl.ext.toString())
            values.put(Tmpl.KEY_PRICE_ENABLED, tmpl.priceEnabled)
            result = (ins(TP_TMPLINFO, values) > -1)
        }
        close()
        return result
    }

    override fun editTmpl(tmpl: Tmpl): Boolean {
        val values = ContentValues()
        values.put(Tmpl.KEY_TITLE, tmpl.title)
        open()
        val result = upd(TP_TMPLINFO, values, "${Tmpl.KEY_SID}=?", arrayOf(tmpl.sid)) > 0
        close()
        return result
    }

    override fun deleteTmpl(tmplList: List<Tmpl>): Boolean {
        val successList: MutableList<Boolean> = mutableListOf()
        open()
        tmplList.forEach {
            if(deleteSQLiteTable(it.sid)) { // очистка данных и удаление таблицы
                successList.add(del(TP_TMPLINFO, "${Tmpl.KEY_SID}=?", arrayOf(it.sid)) > 0) // удаление информации о шаблоне
            }
        }
        close()
        return (tmplList.size == successList.size)
    }

	override fun getTmplComponentList(tmplSid: String): List<TmplComponent> {
        val tmplParticleList: MutableList<TmplComponent> = mutableListOf()
        open()
        val cursor = get(tmplSid, arrayOf("*"), null, null, "${TmplComponent.KEY_SID} ASC")
        if(cursor != null){
            if(cursor.moveToFirst()){
                while(!cursor.isAfterLast){
                    val sid = cursor.getString(cursor.getColumnIndex(TmplComponent.KEY_SID))
                    val changeDate = cursor.getLong(cursor.getColumnIndex(TmplComponent.KEY_CHANGE_DATE))
                    val title = cursor.getString(cursor.getColumnIndex(TmplComponent.KEY_TITLE))
                    val listGD: MutableList<Group.GroupData> = mutableListOf()
                    val jarr = JSONArray(cursor.getString(cursor.getColumnIndex(TmplComponent.KEY_GROUPDATA_LIST)))
                    (0 until jarr.length()).mapTo(listGD) { Group.GroupData.fromJson(jarr[it] as JSONObject) }
                    tmplParticleList.add(TmplComponent(sid, changeDate, title, listGD))

                    cursor.moveToNext()
                }
            }
            cursor.close()
        }
        close()
        return tmplParticleList
    }

    override fun createTmplComponent(sid: String, component: Component): Boolean {
        open()
        val values = ContentValues()
        values.put(TmplComponent.KEY_SID, component.sid)
        values.put(TmplComponent.KEY_CHANGE_DATE, component.changeDate)
        values.put(TmplComponent.KEY_TITLE, component.title)
        val jarr = JSONArray()
        component.listGD.forEach { jarr.put(Group.GroupData.toJson(it)) }
        values.put(TmplComponent.KEY_GROUPDATA_LIST, jarr.toString())
        val result = ins(sid, values) > -1
        close()
        return result
    }

    override fun editTmplComponent(tmplComponent: TmplComponent): Boolean {
        return false
    }
    override fun deleteTmplComponent(tmplComponentSidArray: Array<String>): Boolean {
        return false
    }

    // Native Database code
    var mSQLite: SQLiteDatabase? = null
    val mHelper: Helper

    init {
        mHelper = Helper(context, DBNAME, null, DBVERSION)
        open()
        log("< ИНИЦИАЛИЗАЦИЯ БАЗЫ ДАННЫХ >")
    }

    private fun open(){
        if(isOpen()){close()}
        mSQLite = mHelper.writableDatabase
    }

    private fun close(){
        mHelper.close()
    }

    private fun ins(sid: String, values: ContentValues): Long{
        var result: Long = -1 // ID добавленной записи
        if(isOpen()){
            result = mSQLite?.insert(sid, null, values) as Long
        } else{
            log("< ОШИБКА ДОБАВЛЕНИЯ ДАННЫХ В ТАБЛИЦУ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    private fun del(sid: String, where: String, whereArgs: Array<String>): Int{
        var result = 0 // кол-во удаленных строк
        if(isOpen()){
            result = mSQLite?.delete(sid, where, whereArgs) as Int
        } else{
            log("< ОШИБКА УДАЛЕНИЯ ДАННЫХ ИЗ ТАБЛИЦЫ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    private fun upd(sid: String, values: ContentValues, where: String, whereArgs: Array<String>): Int {
        var result = 0
        if(isOpen()){
            result = mSQLite?.update(sid, values, where, whereArgs) as Int
        } else{
            log("< ОШИБКА ОБНОВЛЕНИЯ ДАННЫХ В ТАБЛИЦЕ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    private fun isOpen(): Boolean {
        return (mSQLite != null && (mSQLite as SQLiteDatabase).isOpen)
    }

    fun get(sid: String, columns: Array<String>, where: String?, whereArgs: Array<String>?, orderBy: String?): Cursor? {
        var result: Cursor? = null
        if(isOpen()){
            result = mSQLite?.query(sid, columns, where, whereArgs, null, null, orderBy)
        } else{
            log("< ОШИБКА ПОЛУЧЕНИЯ ДАННЫХ ИЗ ТАБЛИЦЫ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    fun createSQLiteTable(sid: String, columns: Array<String>): Boolean {
        var result = false
        if(isOpen()){
            val sb = StringBuilder()
            sb.append("CREATE TABLE $sid (")
            for(i in 0 until columns.size){
                sb.append(columns[i])
                if(i < columns.size - 1){sb.append(", ")}
            }
            sb.append(");")
            mSQLite?.execSQL(sb.toString())
            result = true
        } else{
            log("< ОШИБКА ПРИ СОЗДАНИИ НОВОЙ ТАБЛИЦЫ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    private fun deleteSQLiteTable(sid: String): Boolean {
        var result = false
        if(isOpen()){
            mSQLite?.delete(sid, null, null) //очистка данных таблицы
            mSQLite?.execSQL("DROP TABLE $sid")
            result = true
        } else{
            log("< ОШИБКА ПРИ УДАЛЕНИИ ТАБЛИЦЫ $sid. БАЗА ДАННЫХ НЕ ПОДГОТОВЛЕНА. >")
        }
        return result
    }

    inner class Helper(val context: Context, name: String, factory: SQLiteDatabase.CursorFactory?, version: Int) : SQLiteOpenHelper(context, name, factory, version){
        inner class SheetSource(val listGMI: List<Group.GroupMeta>, val listComponent: List<Component>)
        inner class TmplSource(val listGMI: List<Group.GroupMeta>, val listTmplComponent: List<TmplComponent>)

        override fun onUpgrade(SQLite: SQLiteDatabase?, oldVersion: Int, newVersion: Int) { log("onUpgradeLocalDB")
            if(SQLite != null){
                mSQLite = SQLite
                val upgHandler = UpgradeLocalDB(SQLite, oldVersion, newVersion)
                upgHandler.migrateTo2()
            }
        }

        override fun onCreate(sqlite: SQLiteDatabase?) {
            mSQLite = sqlite
            val tmplColumns: Array<String> = arrayOf(
                    "${Tmpl.KEY_SID} TEXT",
                    "${Tmpl.KEY_CREATE_DATE} INTEGER",
                    "${Tmpl.KEY_FML_FILTER} TEXT",
                    "${Tmpl.KEY_CHANGE_DATE} INTEGER",
                    "${Tmpl.KEY_TITLE} TEXT",
                    "${Tmpl.KEY_UNIT} TEXT",
                    "${Tmpl.KEY_GROUPINFO_LIST} MEDIUMTEXT",
                    "${Tmpl.KEY_EXT} MEDIUMTEXT",
                    "${Tmpl.KEY_PRICE_ENABLED} INTEGER"
            )

            createSQLiteTable(TP_TMPLINFO, tmplColumns)

            val sheetColumns: Array<String> = arrayOf(
                     "${Sheet.KEY_SID} TEXT",
                     "${Sheet.KEY_CREATE_DATE} INTEGER",
                     "${Sheet.KEY_FML_FILTER} TEXT",
                     "${Sheet.KEY_CHANGE_DATE} INTEGER",
                     "${Sheet.KEY_TITLE} TEXT",
                     "${Sheet.KEY_UNIT} TEXT",
                     "${Sheet.KEY_GROUPINFO_LIST} MEDIUMTEXT",
                     "${Sheet.KEY_EXT} MEDIUMTEXT",
                     "${Sheet.KEY_COMMENT} TEXT",
                     "${Sheet.KEY_PRICE_ENABLED} INTEGER"
            )

            createSQLiteTable(TP_SHEETINFO, sheetColumns)
            log("onCreateLocalDB")
        }

        inner class UpgradeLocalDB(private val SQLite: SQLiteDatabase, private val oldVersion: Int, private val newVersion: Int) {

            fun migrateTo2(){
                if(oldVersion == 1 && newVersion == 2 && isOpen()){
                    onCreate(SQLite)

                    val cursorSheetList = get(Old.TBL_INFO_LIST_NAME, arrayOf("*"), null, null, null)
                    if(cursorSheetList != null){
                        if(cursorSheetList.moveToFirst()){
                            while(!cursorSheetList.isAfterLast){
                                SQLite.beginTransaction()
                                try{
                                    val createDate: Long = cursorSheetList.getLong(cursorSheetList.getColumnIndex(Old.COL_DATE))
                                    val oldSid = cursorSheetList.getString(cursorSheetList.getColumnIndex(Old.COL_SYSTEM_NAME))
                                    val title = cursorSheetList.getString(cursorSheetList.getColumnIndex(Old.COL_TITLE))
                                    val familyCode = cursorSheetList.getInt(cursorSheetList.getColumnIndex(Old.COL_TYPE_T))
                                    val unit = cursorSheetList.getString(cursorSheetList.getColumnIndex(Old.COL_ED))
                                    val comment = cursorSheetList.getString(cursorSheetList.getColumnIndex(Old.COL_COMMENT))
                                    val rwLength = cursorSheetList.getDouble(cursorSheetList.getColumnIndex(Old.COL_WOOD_LENGTH))
                                    val rwStandartCode = cursorSheetList.getInt(cursorSheetList.getColumnIndex(Old.COL_WOOD_STANDART))
                                    val family = if(familyCode == 1) Sheet.FML_ROUNDWOOD else Sheet.FML_UNIVERSAL

                                    val ext = JSONObject()
                                    if(family == Sheet.FML_ROUNDWOOD){
                                        val st: String = if(rwStandartCode == 1) Sheet.St.ISO_4480_83.name else Sheet.St.GOST_2708_75.name
                                        ext.put(Sheet.KEY_LENGTH, rwLength)
                                        ext.put(Sheet.KEY_STANDART, st)
                                    }

                                    val oldSheetSource: SheetSource = getOldComponentList(oldSid, family)
                                    val listGMI: List<Group.GroupMeta> = oldSheetSource.listGMI
                                    val sheet = Sheet(createDate, family, createDate, title, unit, listGMI, ext, comment, false)
                                    if(migrateSheet(sheet)){
                                        val values = ContentValues()
                                        val componentList: List<Component> = oldSheetSource.listComponent
                                        componentList.forEach {
                                            values.put(Component.KEY_SID, it.sid)
                                            values.put(Component.KEY_CREATE_DATE, it.createDate)
                                            values.put(Component.KEY_CHANGE_DATE, it.changeDate)
                                            values.put(Component.KEY_TITLE, it.title)
                                            val jarrGD = JSONArray()
                                            it.listGD.forEach { jarrGD.put(Group.GroupData.toJson(it)) }
                                            values.put(Component.KEY_GROUPDATA_LIST, jarrGD.toString())
                                            ins(sheet.sid, values)
                                            values.clear()
                                        }

                                        // delete old tables
                                        deleteSQLiteTable(oldSid)
                                    }

                                    SQLite.setTransactionSuccessful()
                                    cursorSheetList.moveToNext()
                                } finally {
                                    SQLite.endTransaction()
                                }
                            }
                        }

                        deleteSQLiteTable(Old.TBL_INFO_LIST_NAME)
                        cursorSheetList.close()
                    }

                    val cursorTmplList = get(Old.TMPL_INFO_LIST_NAME, arrayOf("*"), null, null, null)
                    if(cursorTmplList != null){
                        if(cursorTmplList.moveToFirst()){
                            while(!cursorTmplList.isAfterLast){
                                SQLite.beginTransaction()
                                try{
                                    val createDate: Long = cursorTmplList.getLong(cursorTmplList.getColumnIndex(Old.COL_DATE))
                                    val oldSid = cursorTmplList.getString(cursorTmplList.getColumnIndex(Old.COL_SYSTEM_NAME))
                                    val title = cursorTmplList.getString(cursorTmplList.getColumnIndex(Old.COL_TITLE))
                                    val familyCode = cursorTmplList.getInt(cursorTmplList.getColumnIndex(Old.COL_TYPE_T))
                                    val unit = cursorTmplList.getString(cursorTmplList.getColumnIndex(Old.COL_ED))

                                    val rwLength = cursorTmplList.getDouble(cursorTmplList.getColumnIndex(Old.COL_WOOD_LENGTH))
                                    val rwStandartCode = cursorTmplList.getInt(cursorTmplList.getColumnIndex(Old.COL_WOOD_STANDART))
                                    val family = if(familyCode == 1) Sheet.FML_ROUNDWOOD else Sheet.FML_UNIVERSAL

                                    val ext = JSONObject()
                                    if(family == Sheet.FML_ROUNDWOOD){
                                        val st: String = if(rwStandartCode == 1) Sheet.St.ISO_4480_83.name else Sheet.St.GOST_2708_75.name
                                        ext.put(Sheet.KEY_LENGTH, rwLength)
                                        ext.put(Sheet.KEY_STANDART, st)
                                    }

                                    val oldTmplSource: TmplSource = getOldTmplComponentList(oldSid, family)
                                    val listGMI: List<Group.GroupMeta> = oldTmplSource.listGMI

                                    val tmpl = Tmpl(createDate, family, createDate, title, unit, listGMI, ext, false)
                                    if(migrateTmpl(tmpl)){
                                        val values = ContentValues()
                                        val tmplComponentList: List<TmplComponent> = oldTmplSource.listTmplComponent
                                        tmplComponentList.forEach {
                                            values.put(TmplComponent.KEY_SID, it.sid)
                                            values.put(TmplComponent.KEY_CHANGE_DATE, it.changeDate)
                                            values.put(TmplComponent.KEY_TITLE, it.title)
                                            val jarrGD = JSONArray()
                                            it.listGD.forEach { jarrGD.put(Group.GroupData.toJson(it)) }
                                            values.put(TmplComponent.KEY_GROUPDATA_LIST, jarrGD.toString())
                                            ins(tmpl.sid, values)
                                            values.clear()
                                        }

                                        // delete old tmpls
                                        deleteSQLiteTable(oldSid)
                                    }

                                    SQLite.setTransactionSuccessful()
                                    cursorTmplList.moveToNext()
                                } finally {
                                    SQLite.endTransaction()
                                }
                            }
                        }
                        deleteSQLiteTable(Old.TMPL_INFO_LIST_NAME)
                        cursorTmplList.close()
                    }
                }
            }

            private fun getOldComponentList(sid: String, family: String): SheetSource {
                val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
                val componentList: MutableList<Component> = mutableListOf()

                val cursor = get(sid, arrayOf("*"), null, null, null)
                if(cursor != null){
                    if(cursor.moveToFirst()){
                        while (!cursor.isAfterLast){
                            val listGD: MutableList<Group.GroupData> = mutableListOf()

                            val title = cursor.getString(cursor.getColumnIndex(Old.COL_TITLE))
                            val capacity = cursor.getDouble(cursor.getColumnIndex(Old.COL_CAPACITY))

                            val c0State = cursor.getInt(cursor.getColumnIndex(Old.COL_C0_ENABLED))
                            if(c0State == 1){
                                val c0Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C0_COUNT))
                                val defTitle = Old.gi0Title(context)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C0, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c0Count).forEach {
                                    history.add(Group.History(gmi!!.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c0Count, history)
                                listGD.add(groupData)
                            }

                            val c1State = cursor.getInt(cursor.getColumnIndex(Old.COL_C1_ENABLED))
                            if(c1State == 1){
                                val c1Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C1_COUNT))
                                val defTitle = Old.gi1Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C1, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c1Count).forEach {
                                    history.add(Group.History(gmi!!.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c1Count, history)
                                listGD.add(groupData)
                            }

                            val c2State = cursor.getInt(cursor.getColumnIndex(Old.COL_C2_ENABLED))
                            if(c2State == 1){
                                val c2Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C2_COUNT))
                                val defTitle = Old.gi2Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C2, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c2Count).forEach {
                                    history.add(Group.History(gmi!!.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c2Count, history)
                                listGD.add(groupData)
                            }

                            val c3State = cursor.getInt(cursor.getColumnIndex(Old.COL_C3_ENABLED))
                            if(c3State == 1){
                                val c3Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C3_COUNT))
                                val defTitle = Old.gi3Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C3, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c3Count).forEach {
                                    history.add(Group.History(gmi!!.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c3Count, history)
                                listGD.add(groupData)
                            }

                            val c4State = cursor.getInt(cursor.getColumnIndex(Old.COL_C4_ENABLED))
                            if(c4State == 1){
                                val c4Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C4_COUNT))
                                val defTitle = Old.gi4Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C4, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c4Count).forEach {
                                    history.add(Group.History(gmi!!.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c4Count, history)
                                listGD.add(groupData)
                            }

                            val c5State = cursor.getInt(cursor.getColumnIndex(Old.COL_C5_ENABLED))
                            if(c5State == 1){
                                val c5Count = cursor.getInt(cursor.getColumnIndex(Old.COL_C5_COUNT))
                                val defTitle = Old.gi5Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C5, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val history: MutableList<Group.History> = mutableListOf()
                                (0 until c5Count).forEach {
                                    history.add(Group.History(gmi.sid, System.currentTimeMillis()))
                                    SystemClock.sleep(1)
                                }
                                val groupData = Group.GroupData(gmi.sid, capacity, c5Count, history)
                                listGD.add(groupData)
                            }

                            val date = System.currentTimeMillis()
                            val source = Component(date, date, title, listGD)
                            componentList.add(source)
                            SystemClock.sleep(1)
                            cursor.moveToNext()
                        }

                        componentList.forEach {
                            val component = it

                            listGMI.forEach {
                                val groupInfo = it
                                val findedGroup = component.listGD.find { it.sid == groupInfo.sid }
                                if(findedGroup == null){
                                    component.listGD.add(Group.GroupData(groupInfo.sid, component.listGD[0].capacity, 0))
                                }
                            }
                        }
                    }
                    cursor.close()
                }
                return SheetSource(listGMI, componentList)
            }
            private fun migrateSheet(sheet: Sheet): Boolean {
                var result = false
                val columns: Array<String> = arrayOf(
                        "${Component.KEY_SID} TEXT",
                        "${Component.KEY_CREATE_DATE} INTEGER",
                        "${Component.KEY_CHANGE_DATE} INTEGER",
                        "${Component.KEY_TITLE} TEXT",
                        "${Component.KEY_GROUPDATA_LIST} MEDIUMTEXT"
                )

                if(createSQLiteTable(sheet.sid, columns)){
                    val values = ContentValues()
                    values.put(Sheet.KEY_SID, sheet.sid)
                    values.put(Sheet.KEY_CREATE_DATE, sheet.createDate)
                    values.put(Sheet.KEY_FML_FILTER, sheet.family)
                    values.put(Sheet.KEY_CHANGE_DATE, sheet.changeDate)
                    values.put(Sheet.KEY_TITLE, sheet.title)
                    values.put(Sheet.KEY_UNIT, sheet.unit)
                    val jarr = JSONArray()
                    sheet.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
                    values.put(Sheet.KEY_GROUPINFO_LIST, jarr.toString())
                    values.put(Sheet.KEY_EXT, sheet.ext.toString())
                    values.put(Sheet.KEY_COMMENT, sheet.comment)
                    values.put(Sheet.KEY_PRICE_ENABLED, sheet.priceEnabled)
                    val id = ins(TP_SHEETINFO, values)
                    result = (id > -1)
                }
                return result
            }

            private fun getOldTmplComponentList(sid: String, family: String): TmplSource {
                val listGMI: MutableList<Group.GroupMeta> = mutableListOf()
                val tmplComponentList: MutableList<TmplComponent> = mutableListOf()

                val cursor = get(sid, arrayOf("*"), null, null, null)
                if(cursor != null){
                    if(cursor.moveToFirst()){
                        while (!cursor.isAfterLast){
                            val listGD: MutableList<Group.GroupData> = mutableListOf()

                            val title = cursor.getString(cursor.getColumnIndex(Old.COL_TITLE))
                            val capacity = cursor.getDouble(cursor.getColumnIndex(Old.COL_CAPACITY))

                            val c0State = cursor.getInt(cursor.getColumnIndex(Old.COL_C0_ENABLED))
                            if(c0State == 1){
                                val defTitle = Old.gi0Title(context)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C0, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val c1State = cursor.getInt(cursor.getColumnIndex(Old.COL_C1_ENABLED))
                            if(c1State == 1){
                                val defTitle = Old.gi1Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C1, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val c2State = cursor.getInt(cursor.getColumnIndex(Old.COL_C2_ENABLED))
                            if(c2State == 1){
                                val defTitle = Old.gi2Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C2, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val c3State = cursor.getInt(cursor.getColumnIndex(Old.COL_C3_ENABLED))
                            if(c3State == 1){
                                val defTitle = Old.gi3Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C3, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val c4State = cursor.getInt(cursor.getColumnIndex(Old.COL_C4_ENABLED))
                            if(c4State == 1){
                                val defTitle = Old.gi4Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C4, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val c5State = cursor.getInt(cursor.getColumnIndex(Old.COL_C5_ENABLED))
                            if(c5State == 1){
                                val defTitle = Old.gi5Title(context, family)
                                var gmi = listGMI.find { it.title == defTitle }
                                if(gmi == null){
                                    gmi = Group.GroupMeta(Group.GroupMeta.PREFIX + System.currentTimeMillis(), defTitle, Group.GroupTheme.C5, 0.0)
                                    listGMI.add(gmi)
                                    SystemClock.sleep(1)
                                }

                                val groupData = Group.GroupData(gmi.sid, capacity)
                                listGD.add(groupData)
                            }

                            val date = System.currentTimeMillis()
                            val source = TmplComponent(TmplComponent.PREFIX + date, date, title, listGD)
                            tmplComponentList.add(source)
                            SystemClock.sleep(1)
                            cursor.moveToNext()
                        }

                        tmplComponentList.forEach {
                            val tmplComponent = it

                            listGMI.forEach {
                                val groupInfo = it
                                val findedGroup = tmplComponent.listGD.find { it.sid == groupInfo.sid }
                                if(findedGroup == null){
                                    tmplComponent.listGD.add(Group.GroupData(groupInfo.sid, tmplComponent.listGD[0].capacity))
                                }
                            }
                        }
                    }
                    cursor.close()
                }
                return TmplSource(listGMI, tmplComponentList)
            }
            private fun migrateTmpl(tmpl: Tmpl): Boolean {
                var result = false
                val columns: Array<String> = arrayOf(
                        "${TmplComponent.KEY_SID} TEXT",
                        "${TmplComponent.KEY_CHANGE_DATE} INTEGER",
                        "${TmplComponent.KEY_TITLE} TEXT",
                        "${TmplComponent.KEY_GROUPDATA_LIST} MEDIUMTEXT"
                )

                if(createSQLiteTable(tmpl.sid, columns)){
                    val values = ContentValues()
                    values.put(Tmpl.KEY_SID, tmpl.sid)
                    values.put(Tmpl.KEY_CREATE_DATE, tmpl.createDate)
                    values.put(Tmpl.KEY_FML_FILTER, tmpl.family)
                    values.put(Tmpl.KEY_CHANGE_DATE, tmpl.changeDate)
                    values.put(Tmpl.KEY_TITLE, tmpl.title)
                    values.put(Tmpl.KEY_UNIT, tmpl.unit)
                    val jarr = JSONArray()
                    tmpl.listGMI.forEach { jarr.put(Group.GroupMeta.toJson(it)) }
                    values.put(Tmpl.KEY_GROUPINFO_LIST, jarr.toString())
                    values.put(Tmpl.KEY_EXT, tmpl.ext.toString())
                    values.put(Tmpl.KEY_PRICE_ENABLED, tmpl.priceEnabled)
                    val id = ins(TP_TMPLINFO, values)
                    result = (id > -1)
                }
                return result
            }
        }
    }

}
