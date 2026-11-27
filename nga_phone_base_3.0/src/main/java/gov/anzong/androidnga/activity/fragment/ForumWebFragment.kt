package gov.anzong.androidnga.activity.fragment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.MenuProvider
import com.justwen.androidnga.ui.fragment.WebViewFragment
import gov.anzong.androidnga.R

class ForumWebFragment : WebViewFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_webview, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.menu_open_by_browser) {
                    return startExternalBrowser(requireContext(), webView!!.url)
                }
                return false
            }

        }, this)
    }

    private fun startExternalBrowser(context: Context, url: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(url))
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            return false
        }
    }

    private fun initWebView() {
        webView?.apply {
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.javaScriptCanOpenWindowsAutomatically = true
            webSettings.loadWithOverviewMode = true
            webSettings.textZoom = 100
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.useWideViewPort = true

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val urlStr = request.url.toString()
                    val host = request.url.host
                    if (host.isNullOrEmpty()) {
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                    if ((host.contains("nga") || host.contains("178"))
                        && (urlStr.contains("&rand") || urlStr.contains("?rand"))
                    ) {
                        return super.shouldOverrideUrlLoading(view, request)
                    }
                    view.loadUrl(urlStr)
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.title?.let {
                        setTitle(it)
                    }
                    fixTopOffset(view)
                    super.onPageFinished(view, url)
                }
            }

        }
    }

    /**
     * NGA 网页识别到运行在 App 内置浏览器里时，会给 body 加 notop 类，用固定的负偏移
     * 把网页自己的顶栏（你好xxx / 登录 / 消息）藏起来。窄视口下这条会换行变高，
     * 固定负偏移遮不干净，页面顶部就露出一截文字残影。
     * 这里按元素真实的偏移量补足，让页面从完整位置开始渲染。
     */
    private fun fixTopOffset(view: WebView) {
        view.evaluateJavascript(
            "(function(){try{var b=document.body,m=document.getElementById('mmc');" +
                "if(!b||!m||b.className.indexOf('notop')<0){return;}" +
                "var t=m.getBoundingClientRect().top;" +
                "if(t<0){m.style.marginTop=(-t)+'px';}}catch(e){}})();",
            null
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initWebView()
        super.onViewCreated(view, savedInstanceState)
    }
}
