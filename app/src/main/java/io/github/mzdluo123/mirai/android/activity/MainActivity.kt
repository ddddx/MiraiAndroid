package io.github.mzdluo123.mirai.android.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import io.github.mzdluo123.mirai.android.BotApplication
import io.github.mzdluo123.mirai.android.BuildConfig
import io.github.mzdluo123.mirai.android.NotificationFactory
import io.github.mzdluo123.mirai.android.R
import io.github.mzdluo123.mirai.android.utils.RequestUtil
import io.github.mzdluo123.mirai.android.utils.SafeDns
import io.github.mzdluo123.mirai.android.utils.shareText
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import splitties.alertdialog.appcompat.alertDialog
import splitties.alertdialog.appcompat.cancelButton
import splitties.alertdialog.appcompat.message
import splitties.alertdialog.appcompat.okButton
import splitties.lifecycle.coroutines.coroutineScope
import splitties.toast.toast
import java.io.File
import java.io.FileReader


class MainActivity : AppCompatActivity() {
    companion object {
        const val CRASH_FILE_PREFIX = "crashdata"
        const val CRASH_FILE_DIR = "crash"
        const val UPDATE_URL = "https://api.github.com/repos/mzdluo123/MiraiAndroid/releases/latest"
        const val TAG = "MainActivity"
    }

    private val appBarConfiguration: AppBarConfiguration by lazy {
        AppBarConfiguration(
            setOf(
                R.id.nav_console,
                R.id.nav_plugins,
                R.id.nav_scripts,
                R.id.nav_setting,
                R.id.nav_about,
                R.id.nav_tools
            ), drawer_layout
        )
    }
    private val navController: NavController by lazy {
        findNavController(R.id.nav_host_fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        setupActionBarWithNavController(navController, appBarConfiguration)
        nav_view.setupWithNavController(navController)
        (application as BotApplication).startBotService()
        setupListeners()
        crashCheck()
        if (BuildConfig.DEBUG) toast("跳过更新检查")
        else updateCheck()
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private fun setupListeners(){
        btn_exit.setOnClickListener { exit() }
        btn_reboot.setOnClickListener {
            lifecycleScope.launch { quickReboot() }
        }
    }

    private fun exit(){
        (application as BotApplication).stopBotService()
        NotificationFactory.dismissAllNotification()
        finish()
    }

    private suspend fun quickReboot(){
        NotificationFactory.dismissAllNotification()
        (application as BotApplication).stopBotService()
        delay(1000)
        (application as BotApplication).startBotService()
        navController.popBackStack()
        navController.navigate(R.id.nav_console)  // 重新启动console fragment，使其能够链接到服务
        drawer_layout.closeDrawers()
    }

    private fun crashCheck() {
        val crashDataFile = File(getExternalFilesDir(CRASH_FILE_DIR), CRASH_FILE_PREFIX)
        if (!crashDataFile.exists()) return
        val crashData = crashDataFile.readText()
        alertDialog {
            message = "检测到你上一次异常退出，是否上传崩溃日志？"
            okButton {
                shareText(crashData, lifecycleScope)
            }
            cancelButton { }
        }.show()
        crashDataFile.renameTo(File(getExternalFilesDir(CRASH_FILE_DIR), CRASH_FILE_PREFIX + System.currentTimeMillis()))
    }

    private fun updateCheck(){
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            toast("检查更新失败")
            throwable.printStackTrace()
            Log.e(TAG, throwable.message ?: return@CoroutineExceptionHandler)
//            finish()
//            BotApplication.context.stopBotService()
        }
        lifecycleScope.launch(exceptionHandler) {
            val responseText = RequestUtil.get(UPDATE_URL){ dns(SafeDns()) }
            val responseJsonObject = Json.parseToJsonElement(responseText).jsonObject
            if (!responseJsonObject.containsKey("url")) throw IllegalStateException("检查更新失败")
            val body = responseJsonObject["body"]?.jsonPrimitive?.content ?: "暂无更新记录"
            val htmlUrl = responseJsonObject["html_url"]!!.jsonPrimitive.content
            val version = responseJsonObject["tag_name"]!!.jsonPrimitive.content
            if (version == BuildConfig.VERSION_NAME) return@launch
            alertDialog(title = "发现新版本 $version", message = body) {
                setPositiveButton("立即更新") { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                }
            }.show()
        }
    }
}
