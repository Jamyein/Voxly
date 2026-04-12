package com.voxly.di

import okhttp3.CertificatePinner
import com.voxly.BuildConfig

/**
 * Certificate pinner configuration for network security.
 * 
 * To obtain certificate pins:
 * 1. Visit each domain and get the SHA-256 fingerprint from the certificate chain
 * 2. Encode the fingerprint in base64
 * 3. Add pins to the corresponding domain configuration
 * 
 * Recommended tools:
 * - https://www.ssllabs.com/ssltest/ (Server Certificate Test)
 * - Chrome DevTools → Security tab → Certificate chain
 * - OpenSSL: openssl s_client -connect domain:443 | openssl x509 -pubkey -noout | openssl dgst -sha256 -binary | base64
 * 
 * IMPORTANT: Always provide backup pins to allow certificate rotation.
 * Pins expire should be set before certificate expiry to ensure rotation works.
 */
object CertificatePinnerConfig {

    /**
     * Domains requiring certificate pinning.
     * Each domain should have primary and backup pins.
     */
    private val PINNED_DOMAINS = mapOf(
        "musicbrainz.org" to listOf(
            // Primary pin - musicbrainz.org certificate
            "JYmchuxqGetRqwgtTDwChOQSyfeDFxS67ijzwKaLxX0=",
            // Backup pin - REPLACE with actual backup pin (get new pin before certificate rotation)
            "0dflgFofXiuLoZvgRpP8N9xrpDTgZ7c1xbmTjIxym7o="
        ),
        "itunes.apple.com" to listOf(
            "O2cTB75tClUpBpRgrr0T5USjBm+5VKKXFH7fxEIXhNw=",
            "9C7mf4J789KvLX59lcMyYpsH6bpdmoAGTByZNhcusLA="
        ),
        "c.y.qq.com" to listOf(
            "UQ9uhYYPWXCIb9jyqsobUnL7p1xQiBn8V15FNaIg+GM=",
            "iRVt6vFAG0+gB4h7KFntBfxhDjw2OHuU0tsCfL5BCW8="
        ),
        "y.qq.com" to listOf(
            "UQ9uhYYPWXCIb9jyqsobUnL7p1xQiBn8V15FNaIg+GM=",
            "iRVt6vFAG0+gB4h7KFntBfxhDjw2OHuU0tsCfL5BCW8="
        ),
        "y.gtimg.cn" to listOf(
            "oDKfHb/h3pDTvwOspXnHfoh4rUFmT3j9wjYAZfoKIEg=",
            "Wec45nQiFwKvHtuHxSAMGkt19k+uPSw9JlEkxhvYPHk="
        ),
        "music.163.com" to listOf(
            "4RXsXeq4TMtib3vDXVWFDWZusqntrDkhfnq/qFSFgeA=",
            "udvVO5HfT3B83McRDgEhj2WVTnsk+Sb9NBCszsRWAqY="
        ),
        "coverartarchive.org" to listOf(
            "DNpHAt9piREYbggYFg3lRcTJYS6W7MLvZ8jFut0fnDM=",
            "y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
        )
    )

    /**
     * Creates a CertificatePinner for OkHttpClient.
     * Only pins domains that have been configured with real pins.
     * Domains with placeholder pins are excluded to avoid build failures.
     */
    fun createCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        PINNED_DOMAINS.forEach { (domain, pins) ->
            // Only add domains that have real (non-placeholder) pins
            val realPins = pins.filter { !it.startsWith("REPLACE_") }
            if (realPins.isNotEmpty()) {
                realPins.forEach { pin ->
                    builder.add(domain, "sha256/$pin")
                }
            }
        }

        return builder.build()
    }

    /**
     * Checks if all pinned domains have valid (non-placeholder) pins configured.
     */
    fun hasAllValidPins(): Boolean {
        return PINNED_DOMAINS.values.all { pins ->
            pins.all { !it.startsWith("REPLACE_") }
        }
    }

    /**
     * Returns list of domains that still need pin configuration.
     */
    fun getDomainsNeedingPins(): List<String> {
        return PINNED_DOMAINS.filter { (_, pins) ->
            pins.any { it.startsWith("REPLACE_") }
        }.keys.toList()
    }

    /**
     * Gets the pin expiration date string for the network security config.
     * Format: yyyy-MM-dd
     * 
     * Set this to a date before your certificate expires to ensure rotation.
     * Typically 2-3 years from now is reasonable.
     */
    const val PIN_EXPIRATION_DATE = "2028-01-01"
}
