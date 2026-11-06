package com.mockloc.assistant.mock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 检测使用模拟定位所需的前置条件，并对不同 Android 版本与厂商 ROM 给出差异化提示。
 *
 * 关键限制：Android 不提供读取「当前选择模拟位置信息应用」的公开 API，
 * 因此这里采用保守策略 —— 仅在能确定的情况下给出结论，否则标记为未知，
 * 让用户自行确认，避免误报导致用户困惑。
 */
object MockPrerequisites {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun developerOptionsEnabled(context: Context): Boolean {
        val globalEnabled = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0,
            )
        }.getOrDefault(0) == 1
        if (globalEnabled) return true
        // 个别 ROM 只暴露 adb 启用标记
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                "adb_enabled",
                0,
            )
        }.getOrDefault(0) == 1
    }

    /**
     * 在已开启开发者选项的前提下，尝试从系统安全设置中读取模拟位置应用。
     * Android 10+ 该值对普通应用不可读，读取失败返回 null 表示「未知」。
     */
    fun selectedMockApp(context: Context): String? = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            "mock_location",
        )
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 通过 AppOps 判断本应用是否被授权模拟位置。
     * 这是公开 API，比读取 Secure 设置可靠得多：
     * MODE_ALLOWED 表示用户已在开发者选项中选择本应用。
     */
    fun isMockLocationAllowed(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            ?: return false
        return runCatching {
            val mode = appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                context.packageName,
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    /** 厂商 ROM 识别，用于展示针对性引导 */
    fun vendorGuide(context: Context): VendorGuide {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val combined = "$brand $manufacturer"

        return when {
            combined.contains("xiaomi") || combined.contains("redmi") || combined.contains("poco") ->
                VendorGuide("小米 / Redmi / POCO（HyperOS、MIUI）", HyperOsSteps)
            combined.contains("huawei") || combined.contains("honor") ->
                VendorGuide("华为 / 荣耀（HarmonyOS、EMUI、MagicOS）", HuaweiSteps)
            combined.contains("oppo") || combined.contains("oneplus") || combined.contains("realme") ->
                VendorGuide("OPPO / 一加 / realme（ColorOS）", ColorOsSteps)
            combined.contains("vivo") || combined.contains("iqoo") ->
                VendorGuide("vivo / iQOO（OriginOS、FuntouchOS）", OriginOsSteps)
            combined.contains("samsung") ->
                VendorGuide("三星（One UI）", SamsungSteps)
            else -> VendorGuide("通用 Android", GenericSteps)
        }
    }

    data class VendorGuide(val title: String, val steps: List<String>)

    private val HyperOsSteps = listOf(
        "打开「设置」→「我的设备」→「全部参数与信息」",
        "连续点击「OS 版本」7 次，输入锁屏密码即可开启开发者模式",
        "返回「设置」→「更多设置」→「开发者选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "若列表为空：先在「设置 → 应用设置 → 应用管理 → 定位助手 → 权限」中允许定位",
    )

    private val HuaweiSteps = listOf(
        "打开「设置」→「关于手机」",
        "连续点击「版本号」7 次，输入锁屏密码开启开发者模式",
        "返回「设置」→「系统和更新」→「开发人员选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "HarmonyOS 4 及以上路径为「设置 → 系统和更新 → 开发人员选项」",
    )

    private val ColorOsSteps = listOf(
        "打开「设置」→「关于本机」→「版本信息」",
        "连续点击「版本号」7 次开启开发者模式",
        "返回「设置」→「系统设置」→「开发者选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "ColorOS 13+ 需在「设置 → 应用 → 自启动管理」中允许本应用后台运行",
    )

    private val OriginOsSteps = listOf(
        "打开「设置」→「系统管理」→「关于手机」",
        "连续点击「软件版本号」7 次开启开发者模式",
        "返回「设置」→「系统管理」→「开发者选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "OriginOS 需在「设置 → 应用与权限 → 权限管理 → 后台高耗电」中允许本应用",
    )

    private val SamsungSteps = listOf(
        "打开「设置」→「关于手机」→「软件信息」",
        "连续点击「编译编号」7 次开启开发者模式",
        "返回「设置」→「开发者选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "One UI 6 需在「设置 → 电池 → 后台使用限制」中把本应用加入「永不休眠」",
    )

    private val GenericSteps = listOf(
        "打开「设置」→「关于手机」",
        "连续点击「版本号」7 次开启开发者模式",
        "返回「设置」→「系统」→「开发者选项」",
        "找到「选择模拟位置信息应用」，选择「定位助手」",
        "部分机型列表为空时，请先授予本应用定位权限再回来选择",
    )
}
