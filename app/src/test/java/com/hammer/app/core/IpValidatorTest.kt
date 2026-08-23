package com.hammer.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IpValidatorTest {

    @Test
    fun `accepts start and end of 10-0-0-0 slash 8`() {
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("10.0.0.0"))
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("10.255.255.255"))
    }

    @Test
    fun `accepts start and end of 172-16-0-0 slash 12`() {
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("172.16.0.0"))
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("172.31.255.255"))
    }

    @Test
    fun `rejects just outside the 172-16 slash 12 boundary`() {
        assertEquals(IpValidator.Result.RejectedNotPrivate, IpValidator.validate("172.15.255.255"))
        assertEquals(IpValidator.Result.RejectedNotPrivate, IpValidator.validate("172.32.0.0"))
    }

    @Test
    fun `accepts start and end of 192-168-0-0 slash 16`() {
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("192.168.0.0"))
        assertEquals(IpValidator.Result.Allowed, IpValidator.validate("192.168.255.255"))
    }

    @Test
    fun `rejects well-known public addresses`() {
        assertEquals(IpValidator.Result.RejectedNotPrivate, IpValidator.validate("8.8.8.8"))
        assertEquals(IpValidator.Result.RejectedNotPrivate, IpValidator.validate("1.1.1.1"))
        assertEquals(IpValidator.Result.RejectedNotPrivate, IpValidator.validate("192.169.0.1"))
    }

    @Test
    fun `rejects ipv6 addresses outright`() {
        assertEquals(IpValidator.Result.RejectedIpv6, IpValidator.validate("::1"))
        assertEquals(IpValidator.Result.RejectedIpv6, IpValidator.validate("fe80::1"))
        assertEquals(IpValidator.Result.RejectedIpv6, IpValidator.validate("fc00::1"))
    }

    @Test
    fun `rejects malformed input`() {
        assertEquals(IpValidator.Result.RejectedInvalid, IpValidator.validate("not-an-ip"))
        assertEquals(IpValidator.Result.RejectedInvalid, IpValidator.validate("192.168.1"))
        assertEquals(IpValidator.Result.RejectedInvalid, IpValidator.validate("192.168.1.1.1"))
        assertEquals(IpValidator.Result.RejectedInvalid, IpValidator.validate("192.168.1.256"))
        assertEquals(IpValidator.Result.RejectedInvalid, IpValidator.validate(""))
    }

    @Test
    fun `isAllowed convenience matches validate`() {
        assert(IpValidator.isAllowed("192.168.0.20"))
        assert(!IpValidator.isAllowed("8.8.8.8"))
    }
}
