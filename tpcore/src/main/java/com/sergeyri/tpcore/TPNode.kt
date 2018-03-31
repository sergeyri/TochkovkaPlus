package com.sergeyri.tpcore

import android.app.Activity
import android.os.SystemClock
import org.json.JSONArray

/**
 * Created by sergeyri on 8/27/17.
 */
class TPNode(mainUI: Activity){
    enum class AccountType { FREE, VIP }
    class Task(val bgName: String, val suspendFun: () -> Boolean)
    interface ComponentCallback{
        fun onComponentUpdate(component: Component)
        fun preLoadFilter(component: Component): Boolean
    }

    interface DatabaseActions{
        fun getSheetlist(familyName: String): List<Sheet>
        fun createSheet(sheet: Sheet): Boolean
        fun editSheet(sheet: Sheet): Boolean
        fun deleteSheet(sheets: List<Sheet>): Boolean
        
        fun getComponentList(sheetSid: String, callback: ComponentCallback?=null): List<Component>
        fun createComponent(sheetSid: String, component: Component): Boolean
        fun editComponent(sheetSid: String, component: Component): Boolean
        fun deleteComponent(sheetSid: String, components: List<Component>): Boolean
        fun incrementComponent(sheetSid: String, componentSid: String, jsonArrayGD: JSONArray): Boolean
        fun decrementComponent(sheetSid: String, componentSid: String, jsonArrayGD: JSONArray): Boolean
        fun clearDataComponent(sheetSid: String, components: List<Component>): Boolean

        fun getTmpllist(familyName: String): List<Tmpl>
        fun createTmpl(tmpl: Tmpl): Boolean
        fun editTmpl(tmpl: Tmpl): Boolean
        fun deleteTmpl(tmplList: List<Tmpl>): Boolean
        
        fun getTmplComponentList(tmplSid: String): List<TmplComponent>
        fun createTmplComponent(sid: String, component: Component): Boolean
        fun editTmplComponent(tmplComponent: TmplComponent): Boolean
        fun deleteTmplComponent(tmplComponentSidArray: Array<String>): Boolean
    }

    val accountType: AccountType = AccountType.FREE
    val database: DatabaseActions

    init { database = LocalDB(mainUI) }

    fun fmlFilterReload(tmplOperator: TmplOperator, sheetOperator: SheetOperator): Boolean {
        log("fml:" + sheetOperator.fml)
        return (tmplOperator.load() && sheetOperator.load()) // перезагрузить списки и вернуть результат
    }

    inner class SheetOperator(var fml: String) {
        val list: MutableList<Sheet> = mutableListOf()

        fun filter(predicate: String): List<Sheet> = list.filter { it.title.contains(predicate, true) }

        fun load(): Boolean { // обновить список Sheet-объектов, принадлежащий к этому Family-разделу
            log("load at fml: $fml")
            list.clear()
            list.addAll(database.getSheetlist(fml))
            return true
        }

        fun create(sheet: Sheet): Boolean {
            var result = false
            SystemClock.sleep(1)
            if(database.createSheet(sheet)){
                val sheetJson = Sheet.toJson(sheet)
                log("sheet: $sheetJson")

                list.add(sheet)
                result = true
            }
            return result
        }

        fun edit(sheet: Sheet): Boolean {
            var result = false
            if(database.editSheet(sheet)){
                list.forEachIndexed { tmpIndex, tmpSheet ->
                    if (tmpSheet.sid == sheet.sid){
                        list[tmpIndex] = sheet
                        result = true
                        return@forEachIndexed
                    }
                }
            }
            return result
        }

        fun delete(sheets: List<Sheet>): Boolean {
            var result = false
            if(database.deleteSheet(sheets)){
                sheets.forEach {
                    val deletedObj = it
                    val deletedSheet = list.find { it.sid == deletedObj.sid }
                    if(deletedSheet != null){
                        list.remove(deletedSheet)
                    }
                }
                result = true
            }
            return result
        }
    }

    inner class ComponentOperator(val parent: Sheet) {
        val list: MutableList<Component> = mutableListOf()
		
    	fun filter(predicate: String): List<Component> = list.filter { it.title.contains(predicate, true) }
    	
    	fun load(callback: ComponentCallback?): Boolean {
            list.clear()
            list.addAll(database.getComponentList(parent.sid, callback))
            return true
        }

        fun create(component: Component): Boolean {
            var result = false
            SystemClock.sleep(1)
            if(database.createComponent(parent.sid, component)){
                list.add(component)
                result = true
            }
            return result
        }

        fun edit(component: Component): Boolean {
            var result = false
            if(database.editComponent(parent.sid, component)){
                list.forEachIndexed { tmpIndex, tmpComponent ->
                    if (tmpComponent.sid == component.sid){
                        list[tmpIndex] = component
                        result = true
                        return@forEachIndexed
                    }
                }
            }
            return result
        }

        fun delete(components: List<Component>): Boolean {
            var result = false
            if(database.deleteComponent(parent.sid, components)){
                components.forEach {
                    val deletedObj = it
                    val deletedComponent = list.find { it.sid == deletedObj.sid }
                    if(deletedComponent != null){
                        list.remove(deletedComponent)
                    }
                }
                result = true
            }
            return result
        }
        
        fun incrementGD(component: Component, groupData: Group.GroupData, callback: ComponentCallback): Boolean {
            var result = false
            val jsonArrayGD = JSONArray()
            var createDate: Long = 0
            component.listGD.forEach {
                val jsonGD = Group.GroupData.toJson(it)

                val jsonArrayH = JSONArray()
                it.listH.forEach {
                    jsonArrayH.put(Group.History.toJson(it))
                }

                if(it.sid == groupData.sid){
                    jsonGD.put(Group.GroupData.KEY_COUNT, it.count+1)
                    val history = Group.History(it.sid)
                    jsonArrayH.put(Group.History.toJson(history))
                    createDate = history.createDate
                }

                jsonGD.put(Group.GroupData.KEY_HISTORY_JSON, jsonArrayH)
                jsonArrayGD.put(jsonGD)
            }

            if(database.incrementComponent(parent.sid, component.sid, jsonArrayGD)){
                groupData.increment(createDate)

                val c = list.indexOfFirst { it.sid == component.sid }
                if(c > -1){
                    list[c] = component
                    callback.onComponentUpdate(component)
                    result = true
                }
            }
            return result
        }

        fun decrementGD(component: Component, groupData: Group.GroupData, historyDate: Long, callback: ComponentCallback): Boolean {
            var result = false
            val jsonArrayGD = JSONArray()
            component.listGD.forEach {
                val jsonGD = Group.GroupData.toJson(it)
                val jsonArrayH = JSONArray()
                it.listH.forEach {
                    if(it.createDate != historyDate){
                        jsonArrayH.put(Group.History.toJson(it))
                    }
                }
                jsonGD.put(Group.GroupData.KEY_HISTORY_JSON, jsonArrayH)

                if(it.sid == groupData.sid){
                    jsonGD.put(Group.GroupData.KEY_COUNT, it.count-1)
                }
                jsonArrayGD.put(jsonGD)
            }

            if(database.decrementComponent(parent.sid, component.sid, jsonArrayGD)){
                groupData.decrement(historyDate)

                val c = list.indexOfFirst { it.sid == component.sid }
                if(c > -1){
                    list[c] = component
                    callback.onComponentUpdate(component)
                    result = true
                }
            }
            return result
        }

        fun clearData(components: List<Component>): Boolean {
            var result = false
            if(database.clearDataComponent(parent.sid, components)){
                components.forEach {
                    val component = it
                    list.forEachIndexed { tmpIndex, tmpComponent ->
                        if (tmpComponent.sid == it.sid){
                            list[tmpIndex] = component
                            result = true
                            return@forEachIndexed
                        }
                    }
                }
                result = true
            }
            return result
        }

    }

    inner class TmplOperator(var fml: String) {
        val list: MutableList<Tmpl> = mutableListOf()

        fun filter(predicate: String): List<Tmpl> = list.filter { it.title.contains(predicate, true) }

        fun load(): Boolean {
            list.clear()
            list.addAll(database.getTmpllist(fml))
            return true
        }

        fun create(sheet: Sheet): Boolean {
            var result = false
            val tmpl = Tmpl(sheet)
            SystemClock.sleep(1)
            if(database.createTmpl(tmpl)){
                val particleList = database.getComponentList(sheet.sid)
                particleList.forEach {
                    it.listGD.forEach {
                        it.count = 0
                        it.listH.clear()
                    }
                    database.createTmplComponent(tmpl.sid, it)
                }
                list.add(tmpl)
                result = true
            }
            return result
        }

        fun edit(tmpl: Tmpl): Boolean {
            var result = false
            if(database.editTmpl(tmpl)){
                list.forEachIndexed { tmpIndex, tmpSheet ->
                    if (tmpSheet.sid == tmpl.sid){
                        list[tmpIndex] = tmpl
                        result = true
                        return@forEachIndexed
                    }
                }
            }
            return result
        }

        fun delete(tmplList: List<Tmpl>): Boolean {
            var result = false
            if(database.deleteTmpl(tmplList)){
                tmplList.forEach {
                    val deletedObj = it
                    val deletedTmpl = list.find { it.sid == deletedObj.sid }
                    if(deletedTmpl != null){
                        list.remove(deletedTmpl)
                    }
                }
                result = true
            }
            return result
        }

    }

    inner class TmplParticleOperator(val parent: Tmpl) {
    	val list: MutableList<TmplComponent> = mutableListOf()
		
    	fun filter(predicate: String): List<TmplComponent> = list.filter { it.title.contains(predicate, true) }
    	
    	fun load(): Boolean {
            list.clear()
            list.addAll(database.getTmplComponentList(parent.sid))
            return true
        }
    }

}
