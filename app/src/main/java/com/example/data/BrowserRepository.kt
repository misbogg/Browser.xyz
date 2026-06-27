package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val dao: BrowserDao) {

    // Tab Operations
    val allTabsFlow: Flow<List<Tab>> = dao.getAllTabsFlow()
    
    suspend fun getAllTabs(): List<Tab> = dao.getAllTabs()
    
    suspend fun insertTab(tab: Tab): Long = dao.insertTab(tab)
    
    suspend fun updateTab(tab: Tab) = dao.updateTab(tab)
    
    suspend fun deleteTab(tab: Tab) = dao.deleteTab(tab)
    
    suspend fun deleteAllTabs() = dao.deleteAllTabs()

    // Bookmark Operations
    val allBookmarksFlow: Flow<List<Bookmark>> = dao.getAllBookmarksFlow()
    
    suspend fun insertBookmark(bookmark: Bookmark): Long = dao.insertBookmark(bookmark)
    
    suspend fun deleteBookmarkById(id: Int) = dao.deleteBookmarkById(id)
    
    fun isBookmarked(url: String): Flow<Boolean> = dao.isBookmarkedFlow(url)

    // History Operations
    val allHistoryFlow: Flow<List<HistoryItem>> = dao.getAllHistoryFlow()
    
    suspend fun insertHistory(historyItem: HistoryItem): Long = dao.insertHistory(historyItem)
    
    suspend fun deleteHistoryById(id: Int) = dao.deleteHistoryById(id)
    
    suspend fun clearHistory() = dao.clearHistory()

    // Custom Script Operations
    val allScriptsFlow: Flow<List<CustomScript>> = dao.getAllScriptsFlow()
    
    suspend fun getActiveScripts(): List<CustomScript> = dao.getActiveScripts()
    
    suspend fun insertScript(script: CustomScript): Long = dao.insertScript(script)
    
    suspend fun deleteScriptById(id: Int) = dao.deleteScriptById(id)

    // Site Rule Operations
    val allRulesFlow: Flow<List<SiteRule>> = dao.getAllRulesFlow()
    
    suspend fun getRuleForDomain(domain: String): SiteRule? = dao.getRuleForDomain(domain)
    
    suspend fun insertRule(rule: SiteRule) = dao.insertRule(rule)
    
    suspend fun deleteRuleForDomain(domain: String) = dao.deleteRuleForDomain(domain)
}
