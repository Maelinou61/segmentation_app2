buildscript {
    repositories {
        google()
        jcenter()
        mavenLocal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("de.undercouch:gradle-download-task:4.1.2")
    }
}
allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
        maven{
            name = "ossrh-snapshot"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
        mavenLocal()
    }
}