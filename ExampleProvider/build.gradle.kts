dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

version = 1

cloudstream {
    // Eklentinin sistemdeki görünen adı
    setPluginName("Anizium")
    description = "Anizium CloudStream Eklentisi"
    authors = listOf("forzupsz")
    status = 1
    tvTypes = listOf("Anime")
    requiresResources = true
    language = "tr"
    iconUrl = "https://anizium.com/favicon.ico"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
