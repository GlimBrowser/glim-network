import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * shared:network —— BYOK 网络层（PRD V1.2 §2.4：Ktor 封装、OpenAI 兼容 API、SSE 解析、重试/退避；
 * 附件 D 连通性测试与能力探测）。
 *
 * 硬约束（000 §2）：
 *  - BYOK 直连：请求直达用户配置 Base URL，我方不代理、不缓存、不记录（PRD V1.1 §2.3/§3.2.5）
 *  - 强制 HTTPS（http:// 仅 localhost/127.0.0.1 放行，其余需二次确认 —— 网络层校验，附件 D 步骤一）
 *  - API Key 不进入本模块明文接口（平台 SecureKeyStore → 以 Authorization 头注入，网络层只见头值）
 *
 * 依赖：Ktor Client Core + ContentNegotiation(JSON) + SSE（附件 C 流式）；
 * HTTP 引擎（CIO/OkHttp/Darwin）由各端注入 HttpClient（附录 B：行为差异由 SseClient/HttpClient 抹平）。
 */
kotlin {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "cn.ac.oac.glimbrowser.shared.network"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.sse)
            implementation(libs.ktor.serializationKotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
        }
        appleMain.dependencies {
            implementation(libs.ktor.clientDarwin)
        }
        jsMain.dependencies {
            implementation(libs.ktor.clientJs)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.clientJs)   // ktor-client-js 覆盖 js+wasm-js 变体
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
