package com.onthecrow.vpnadmin.threexui

import io.ktor.client.HttpClient

/**
 * Per-call HTTP client. We allocate fresh because TLS verification is a per-server choice
 * (self-signed certs / IP-based panels). [skipTlsVerify] = true installs a trust-all
 * X509TrustManager — only safe for admin/dev contexts.
 */
expect fun createPanelHttpClient(skipTlsVerify: Boolean): HttpClient
