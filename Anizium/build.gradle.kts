dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

version = 1

cloudstream {
    description = "Anizium CloudStream Eklentisi"
    authors = listOf("forzupsz")
    status = 1
    tvTypes = listOf("Anime")
    requiresResources = false
    language = "tr"
    iconUrl = "https://raw.githubusercontent.com/forzupsz/forzups-plugins/master/Anizium/icon.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
