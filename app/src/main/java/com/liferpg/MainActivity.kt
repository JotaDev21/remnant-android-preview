package com.liferpg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.liferpg.ui.AppRoot
import com.liferpg.ui.theme.LifeRpgTheme
import com.liferpg.ui.RemnantRoute
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val mainVm by lazy { ViewModelProvider(this, MainViewModel.Factory)[MainViewModel::class.java] }

    private val openSocialFromNotification = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null) openSocialFromNotification.value = notificationSocialRoute(intent,Graph.auth.sessao.value?.userId)
        enableEdgeToEdge()
        intent?.data?.let(mainVm::concluirLinkAuth)
        setContent {
            LifeRpgTheme {
                AppRoot(mainVm, openSocialFromNotification)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(mainVm::concluirLinkAuth)
        notificationSocialRoute(intent,Graph.auth.sessao.value?.userId)?.let { openSocialFromNotification.value = it }
    }

}

internal fun notificationSocialRoute(intent: Intent?,uid:String?=null): String? {
    if (intent?.getBooleanExtra("open_cardio", false) == true) return RemnantRoute.CARDIO
    intent?.getStringExtra("social_account")?.let { if(it!=uid) return null }
    val target=intent?.getStringExtra("open_social_section")
    if(target!=null && RemnantRoute.socialSection(target)!=null) return target
    return RemnantRoute.FRIENDS.takeIf { intent?.getBooleanExtra("open_arena",false)==true }
}
