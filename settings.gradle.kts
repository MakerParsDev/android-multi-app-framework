pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

buildscript {
    configurations.all {
        resolutionStrategy {
            // Force secure versions for Netty (HTTP/2 DoS, Request Smuggling, etc.)
            val nettyVersion = "4.2.14.Final"
            force("io.netty:netty-codec-http2:$nettyVersion")
            force("io.netty:netty-handler:$nettyVersion")
            force("io.netty:netty-codec-http:$nettyVersion")
            force("io.netty:netty-codec:$nettyVersion")
            force("io.netty:netty-common:$nettyVersion")
            force("io.netty:netty-handler-proxy:$nettyVersion")
            
            // Force secure BouncyCastle, Guava, and other transitive build plugin deps
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("com.google.guava:guava:33.6.0-jre")
            force("org.jdom:jdom2:2.0.6.1")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.apache.commons:commons-lang3:3.18.0")
            force("ch.qos.logback:logback-core:1.5.25")
            force("org.apache.httpcomponents:httpclient:4.5.14")
        }
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ContentApp"
include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":core:datastore")
include(":core:database")
include(":core:firebase")
include(":core:auth")
include(":feature:content")
include(":feature:audio")
include(":feature:ads")
include(":feature:billing")
include(":feature:auth")
include(":feature:notifications")
include(":feature:messages")
include(":feature:settings")
include(":feature:otherapps")
include(":feature:prayertimes")
include(":feature:qibla")
include(":feature:counter")
include(":feature:quran")
