import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Chave de upload da Play Store. O arquivo keystore.properties fica só no seu computador (está no .gitignore);
// veja android/play-store/README.md. Sem ele, a versão de produção sai sem assinatura.
val assinatura = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "br.com.valtrea.pay"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.valtrea.pay"
        minSdk = 26
        targetSdk = 37
        // Cada envio para a Play Store precisa de um versionCode maior que o anterior.
        versionCode = 1
        versionName = "0.1.0"
        // Endereço (DSN) do projeto Android no Sentry. Vem da variável VALTREA_SENTRY_DSN ou da propriedade
        // valtreaSentryDsn (no gradle.properties do seu usuário). Vazio: o app não envia nada.
        val sentryDsn = providers.gradleProperty("valtreaSentryDsn")
            .orElse(providers.environmentVariable("VALTREA_SENTRY_DSN"))
            .getOrElse("")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    signingConfigs {
        if (!assinatura.isEmpty) {
            create("upload") {
                storeFile = file(assinatura.getProperty("storeFile"))
                storePassword = assinatura.getProperty("storePassword")
                keyAlias = assinatura.getProperty("keyAlias")
                keyPassword = assinatura.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 10.0.2.2 é o "localhost" do computador visto de dentro do emulador.
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_URL", "\"https://pay.valtrea.com.br/\"")
            signingConfig = signingConfigs.findByName("upload")
        }
        // Igual à de produção (R8, API publicada, só HTTPS), mas assinada com a chave de desenvolvimento para
        // instalar no emulador ou no celular ao lado da versão debug. Não serve para a Play Store.
        create("previa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".previa"
            versionNameSuffix = "-previa"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Testes das telas rodam no computador (Robolectric), sem emulador.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Gera o QR Code de recebimento e lê o de pagamento (a câmera é aberta pelo Google Play Services).
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)

    // Erros e travamentos do app (só com SENTRY_DSN definido na compilação).
    implementation(libs.sentry.android.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
