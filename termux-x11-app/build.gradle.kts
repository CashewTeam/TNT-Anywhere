import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.exclude
import java.io.StringReader

plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.x11"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
        buildConfigField("String", "COMMIT", "\"unknown\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.preference:preference:1.2.1")
    compileOnly(project(":hidden-api-stub"))
}

afterEvaluate {
    tasks.register("generatePrefs") {
        doLast {
            val xml = groovy.xml.DOMBuilder.parse(StringReader(file("src/main/res/xml/preferences.xml").readText()))
            val preferenceNodes = xml.documentElement.getElementsByTagName("*")
            val preferences = mutableListOf<Map<String, String>>()

            for (i in 0 until preferenceNodes.length) {
                val node = preferenceNodes.item(i)
                when (node.nodeName) {
                    "EditTextPreference" -> if (node.attributes.getNamedItem("app:key")?.nodeValue != "extra_keys_config") {
                        preferences.add(mapOf(
                            "type" to "String",
                            "key" to (node.attributes.getNamedItem("app:key")?.nodeValue ?: ""),
                            "default" to (node.attributes.getNamedItem("app:defaultValue")?.nodeValue ?: "")
                        ))
                    }
                    "SeekBarPreference" -> preferences.add(mapOf(
                        "type" to "Int",
                        "key" to (node.attributes.getNamedItem("app:key")?.nodeValue ?: ""),
                        "default" to (node.attributes.getNamedItem("app:defaultValue")?.nodeValue ?: "")
                    ))
                    "ListPreference" -> {
                        val entries = node.attributes.getNamedItem("app:entries")?.nodeValue ?: ""
                        val values = node.attributes.getNamedItem("app:entryValues")?.nodeValue ?: ""
                        preferences.add(mapOf(
                            "type" to "List",
                            "key" to (node.attributes.getNamedItem("app:key")?.nodeValue ?: ""),
                            "default" to (node.attributes.getNamedItem("app:defaultValue")?.nodeValue ?: ""),
                            "entries" to entries.substring(7),
                            "values" to values.substring(7)
                        ))
                    }
                    "SwitchPreferenceCompat" -> preferences.add(mapOf(
                        "type" to "Boolean",
                        "key" to (node.attributes.getNamedItem("app:key")?.nodeValue ?: ""),
                        "default" to (node.attributes.getNamedItem("app:defaultValue")?.nodeValue ?: "")
                    ))
                }
            }

            val out = file("build/generated/java/com/termux/x11/Prefs.java")
            out.parentFile.mkdirs()
            out.delete()
            out.createNewFile()

            out.writeText("""
                package com.termux.x11;
                import java.util.HashMap;
                import android.content.Context;
                import com.termux.x11.utils.TermuxX11ExtraKeys;

                public class Prefs extends LoriePreferences.PrefsProto {
                    ${preferences.joinToString("\n") { pref ->
                        when (pref["type"]) {
                            "Int", "Boolean" -> "  public final ${pref["type"]}Preference ${pref["key"]} = new ${pref["type"]}Preference(\"${pref["key"]}\", ${pref["default"]});"
                            "String" -> "  public final StringPreference ${pref["key"]} = new StringPreference(\"${pref["key"]}\", \"${pref["default"]}\");"
                            "List" -> "  public final ${pref["type"]}Preference ${pref["key"]} = new ${pref["type"]}Preference(\"${pref["key"]}\", \"${pref["default"]}\", R.array.${pref["entries"]}, R.array.${pref["values"]});"
                            else -> ""
                        }
                    }}
                    public final StringPreference extra_keys_config = new StringPreference("extra_keys_config", TermuxX11ExtraKeys.DEFAULT_IVALUE_EXTRA_KEYS);
                    public final HashMap<String, Preference> keys = new HashMap<>() {{
                        ${preferences.joinToString("\n") { pref -> "    put(\"${pref["key"]}\", ${pref["key"]});" }}
                        put("extra_keys_config", extra_keys_config);
                    }};

                    public Prefs(Context ctx) {
                        super(ctx);
                    }
                }
            """.trimIndent())
        }
    }
    android.sourceSets.getByName("main") {
        java.srcDirs("build/generated/java")
    }
    tasks.named("preBuild") {
        dependsOn("generatePrefs")
    }
}