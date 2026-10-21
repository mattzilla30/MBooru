package com.mattzilla30.mbooru.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.repository.suggestion.SuggestionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SuggestionViewModel(private val repo: SuggestionRepository) : androidx.lifecycle.ViewModel() {
    private val mutable = MutableStateFlow<List<String>>(emptyList())
    val suggestionState = mutable.asStateFlow()
    private var request: Job? = null

    fun fetchSuggestions(actionTag: ActionTag) {
        request?.cancel()
        val snapshot = actionTag.copy(booru = actionTag.booru.copy())
        request = viewModelScope.launch {
            delay(150)
            mutable.value = repo.fetchSuggestions(snapshot)?.map { it.name }.orEmpty()
        }
    }
}
