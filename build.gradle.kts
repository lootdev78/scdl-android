plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
}

allprojects {
    group = "io.github.lootdev.scdl"
    version = "1.0.0"
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
