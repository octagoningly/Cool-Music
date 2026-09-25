package moe.ouom.neriplayer.util.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class GitHubSyncProxySelectorTest {
    private class FixedProxySelector(
        private val proxies: List<Proxy>
    ) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> = proxies
        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: java.io.IOException?) = Unit
    }

    private var originalDefault: ProxySelector? = null

    @Before
    fun setUp() {
        originalDefault = ProxySelector.getDefault()
    }

    @After
    fun tearDown() {
        ProxySelector.setDefault(originalDefault)
        GitHubSyncProxySelector.useSystemProxy = true
    }

    @Test
    fun defaultUsesSystemProxy() {
        assertTrue(GitHubSyncProxySelector.useSystemProxy)
    }

    @Test
    fun usesSystemProxyWhenEnabled() {
        val systemProxy = Proxy(
            Proxy.Type.HTTP,
            java.net.InetSocketAddress.createUnresolved("127.0.0.1", 7890)
        )
        ProxySelector.setDefault(FixedProxySelector(listOf(systemProxy)))
        GitHubSyncProxySelector.useSystemProxy = true

        val selected = GitHubSyncProxySelector.select(URI("https://api.github.com/user"))

        assertEquals(listOf(systemProxy), selected)
    }

    @Test
    fun bypassesProxyWhenDisabled() {
        val systemProxy = Proxy(
            Proxy.Type.HTTP,
            java.net.InetSocketAddress.createUnresolved("127.0.0.1", 7890)
        )
        ProxySelector.setDefault(FixedProxySelector(listOf(systemProxy)))
        GitHubSyncProxySelector.useSystemProxy = false

        val selected = GitHubSyncProxySelector.select(URI("https://api.github.com/user"))

        assertEquals(listOf(Proxy.NO_PROXY), selected)
    }

    @Test
    fun nullUriReturnsNoProxy() {
        assertEquals(listOf(Proxy.NO_PROXY), GitHubSyncProxySelector.select(null))
    }
}
