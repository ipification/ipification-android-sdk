package com.ipification.mobile.sdk.im.viewModel

//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.viewModelScope
//import com.ipification.mobile.sdk.im.model.IMSession
//import com.ipification.mobile.sdk.im.base.BaseViewModel
//import com.ipification.mobile.sdk.im.data.SessionResponse
//import com.ipification.mobile.sdk.im.di.RepositoryModule
//import com.ipification.mobile.sdk.im.repository.SessionRepository
//import com.ipification.mobile.sdk.im.util.SingleLiveEvent
//import kotlinx.coroutines.launch


//class IMVerificationViewModel : BaseViewModel(){
//    private var initResult = SingleLiveEvent<IMSession>()
//    private var initError = SingleLiveEvent<Boolean>()
//
//    fun getInitResult(): SingleLiveEvent<IMSession>{
//        return initResult
//    }
//    fun getInitError(): SingleLiveEvent<Boolean>{
//        return initError
//    }
//
//    fun init(){
//        viewModelScope.launch {
//            val sessionInfo = getSessionRepository().getSavedSessionInfo() ?: IMSession()
//            loadingState.postValue(true)
//            initResult.postValue(sessionInfo)
//        }
//    }
//    private fun getSessionRepository(): SessionRepository {
//        return RepositoryModule.getInstance().getSessionRepository()
//    }
//
//    fun checkStatus(): MutableLiveData<SessionResponse> {
//        return RepositoryModule.getInstance().checkSessionStatus()
//    }getInitResult
//
//
//    fun completeSession(): SingleLiveEvent<SessionResponse>? {
//        return RepositoryModule.getInstance().completeSession()
//    }
//
//    fun getSessionID(): String? {
//        return RepositoryModule.getInstance().getSessionRepository().getSavedSessionInfo()?.sessionId
//    }
//}