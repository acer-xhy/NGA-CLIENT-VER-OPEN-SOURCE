package com.client.androidnga.message.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.justwen.androidnga.base.router.Router
import com.justwen.androidnga.base.service.api.IUserManagerService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart

class MessageListViewModel : ViewModel() {
    private val accountManager = Router.get(IUserManagerService::class.java)

    private val repository = MessageListRepository()
    @OptIn(ExperimentalCoroutinesApi::class)
    val messageListData = accountManager.activeAccountIndexState.flatMapLatest {
        repository.getMessageListData().onStart { emit(PagingData.empty()) }
    }.cachedIn(viewModelScope)

}