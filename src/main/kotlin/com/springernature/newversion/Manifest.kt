package com.springernature.newversion

import com.fasterxml.jackson.annotation.JsonCreator
import com.springernature.newversion.Setting.GIT_HUB_API_URL
import net.swiftzer.semver.SemVer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class CFApplication(val buildpacks: List<VersionedBuildpack>)

data class VersionedBuildpack(val name: String, val url: String, val version: Version) {

    fun getLatestBuildpack(client: HttpClient, settings: Settings): VersionedBuildpack {
        val url = "${settings.lookup(GIT_HUB_API_URL)}/repos/${name}/releases/latest"
        val request = HttpRequest.newBuilder().uri(URI.create(url)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Unexpected response from GitHub for URL $url: ${response.statusCode()}; ${response.body()}")
        }
        val message = response.body()
        return """"tag_name":\s*"v([^"]+)"""".toRegex().find(message)?.let {
            copy(version = SemanticVersion(it.groups[1]!!.value))
        } ?: throw Exception("Couldn't get latest version of buildpack $name")
    }

    companion object {

        @JvmStatic
        @JsonCreator
        fun create(value: String) =
            VersionedBuildpack(value.name(), value.buildpackUrl(), value.buildpackVersion())

        private fun String.buildpackUrl(): String =
            "(.*github.com/.*?)(?:#v.*)?$".toRegex().find(this)?.groups?.get(1)?.value
                ?: throw Exception("Cannot parse buildpack URL: $this")

        private fun String.buildpackVersion(): Version =
            ".*github.com/.*#v(.*)$".toRegex().find(this)?.groups?.get(1)?.value?.let { SemanticVersion(it) }
                ?: Latest()

        private fun String.name(): String =
            ".*github.com/(.*?)(?:#v.*)?$".toRegex().find(this)?.groups?.get(1)?.value
                ?: throw Exception("Cannot parse buildpack URL: $this")
    }
}

sealed class Version
data class SemanticVersion(private val versionString: String) : Version() {
    fun toSemVer(): SemVer = SemVer.parse(versionString.trimStart('v'))
}

class Latest : Version() {
    override fun equals(other: Any?): Boolean = when (other) {
        is Latest -> true
        else -> false
    }

    override fun hashCode(): Int = 1
}