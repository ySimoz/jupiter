package com.jupiter.app.core.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date

class AndroidCaBundleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `formatPemBundle wraps certificate DER bytes in valid PEM blocks`() {
        val der1 = ByteArray(70) { (it + 1).toByte() }
        val der2 = ByteArray(12) { (it + 65).toByte() }

        val pem = AndroidCaBundle.formatPemBundle(
            listOf(FakeX509Certificate(der1), FakeX509Certificate(der2)),
        )

        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(pem.endsWith("\n-----END CERTIFICATE-----\n"))
        assertEquals(2, pem.split("-----BEGIN CERTIFICATE-----").size - 1)
        assertEquals(2, pem.split("-----END CERTIFICATE-----").size - 1)
        val lines = pem.lines().filter { it.isNotEmpty() && !it.startsWith("-----") }
        assertTrue(lines.all { it.length <= 64 && '\r' !in it })
    }

    @Test
    fun `ensureCaBundleFile exports once and reuses non-empty cached cacert pem`() {
        val configDir = tempFolder.newFolder("mpv-config")
        var providerCalls = 0
        val cert = FakeX509Certificate("sample-ca-der".toByteArray())

        val first = AndroidCaBundle.ensureCaBundleFile(configDir.absolutePath) {
            providerCalls++
            listOf(cert)
        }

        assertNotNull(first)
        assertEquals(File(configDir, "cacert.pem").absolutePath, first!!.absolutePath)
        assertTrue(first.length() > 0L)
        assertEquals(1, providerCalls)
        assertTrue(first.readText().contains("-----BEGIN CERTIFICATE-----"))

        // Second call must reuse existing non-empty file without calling provider again.
        val second = AndroidCaBundle.ensureCaBundleFile(configDir.absolutePath) {
            providerCalls++
            emptyList()
        }
        assertEquals(first.absolutePath, second?.absolutePath)
        assertEquals(1, providerCalls)
    }

    @Test
    fun `ensureCaBundleFile returns null when no certificates are provided`() {
        val configDir = tempFolder.newFolder("empty-mpv-config")
        val file = AndroidCaBundle.ensureCaBundleFile(configDir.absolutePath) { emptyList() }
        assertNull(file)
    }

    private class FakeX509Certificate(private val der: ByteArray) : X509Certificate() {
        override fun getEncoded(): ByteArray = der
        override fun checkValidity() = Unit
        override fun checkValidity(date: Date?) = Unit
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
        override fun getIssuerDN(): Principal = Principal { "CN=Test" }
        override fun getSubjectDN(): Principal = Principal { "CN=Test" }
        override fun getNotBefore(): Date = Date(0L)
        override fun getNotAfter(): Date = Date(Long.MAX_VALUE)
        override fun getTBSCertificate(): ByteArray = der
        override fun getSignature(): ByteArray = byteArrayOf()
        override fun getSigAlgName(): String = "SHA256withRSA"
        override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = 0
        override fun getExtensionValue(oid: String?): ByteArray? = null
        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
        override fun hasUnsupportedCriticalExtension(): Boolean = false
        override fun toString(): String = "FakeX509Certificate"
        override fun verify(key: PublicKey?) = Unit
        override fun verify(key: PublicKey?, sigProvider: String?) = Unit
        override fun getPublicKey(): PublicKey = object : PublicKey {
            override fun getAlgorithm(): String = "RSA"
            override fun getFormat(): String = "X.509"
            override fun getEncoded(): ByteArray = byteArrayOf()
        }
    }
}
