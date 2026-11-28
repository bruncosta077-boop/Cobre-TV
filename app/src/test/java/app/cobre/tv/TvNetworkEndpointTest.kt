package app.cobre.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvNetworkEndpointTest {
    @Test fun acceptsLocalAndTailscaleHttpHosts() {
        listOf(
            "http://192.168.1.20:8787",
            "http://10.0.0.4:8787",
            "http://100.64.0.1:8787",
            "http://100.127.255.254:8787",
            "http://desktop-name.example-tailnet.ts.net:8787",
        ).forEach { assertEquals(it, TvNetworkEndpoint.normalize(it, "Desktop")) }
    }

    @Test fun acceptsPublicHttpsButRejectsPublicHttp() {
        assertEquals("https://music.example.com:8787", TvNetworkEndpoint.normalize("https://music.example.com:8787", "Desktop"))
        assertFalse(TvNetworkEndpoint.isAllowedHttpHost("8.8.8.8"))
        assertFalse(TvNetworkEndpoint.isAllowedHttpHost("100.63.255.255"))
        assertFalse(TvNetworkEndpoint.isAllowedHttpHost("100.128.0.1"))
    }

    @Test fun rejectsCredentialsPathsQueriesAndFragments() {
        listOf(
            "http://user:pass@192.168.1.20:8787",
            "http://192.168.1.20:8787/path",
            "http://192.168.1.20:8787?token=secret",
            "http://192.168.1.20:8787#fragment",
            "http://192.168.1.20:99999",
            "http://desktop.ts.net.evil.com:8787",
        ).forEach { address ->
            assertTrue("Expected rejection for $address", runCatching { TvNetworkEndpoint.normalize(address, "Desktop") }.isFailure)
        }
    }
}
