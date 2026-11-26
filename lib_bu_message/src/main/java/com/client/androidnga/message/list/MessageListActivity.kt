package com.client.androidnga.message.list

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import com.justwen.androidnga.base.router.Router
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.base.service.api.IUserManagerService
import com.justwen.androidnga.base.router.LoginGuard
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.OptionMenuData

class MessageListActivity : BaseComposeActivity() {

    private val viewModel: MessageListViewModel by lazy {
        ViewModelProvider(this)[MessageListViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LoginGuard.intercept(this, ARouterConstants.ACTIVITY_MESSAGE_LIST)) {
            finish()
        }
    }

    override fun getOptionMenuData(): List<OptionMenuData> {
        val accountSwitch = OptionMenuData(title = "切换账号", action = {
            showAccountSwitchDialog()
        })
        return arrayListOf(accountSwitch)
    }

    private fun showAccountSwitchDialog() {
        val userManager = Router.get(IUserManagerService::class.java)
        userManager.showUserSwitchDialog(this)
    }

    override fun getFabClickAction(): () -> Unit {
        return {
            Router.build(ARouterConstants.ACTIVITY_MESSAGE_POST)
                .withString("action", "new")
                .navigation(this)
        }
    }

    @Composable
    override fun ContentView() {
        MessageListScreen(viewModel, onMessageClick = ::startMessageDetail)
    }

    private fun startMessageDetail(mid: String, subject: String, recipient: String) {
        Router.build(ARouterConstants.ACTIVITY_MESSAGE_DETAIL)
            .withString("mid", mid)
            .withString("subject", subject)
            .withString("recipient", recipient)
            .navigation(this)
    }

}

