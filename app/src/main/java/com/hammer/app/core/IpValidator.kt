package com.hammer.app.core

object IpValidator {

    private val IPV4_PATTERN = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    sealed class Result {
        data object Allowed : Result()
        data object RejectedIpv6 : Result()
        data object RejectedNotPrivate : Result()
        data object RejectedInvalid : Result()
    }

    fun validate(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.contains(':')) return Result.RejectedIpv6

        val match = IPV4_PATTERN.matchEntire(trimmed) ?: return Result.RejectedInvalid
        val octets = match.groupValues.drop(1).map { it.toIntOrNull() ?: return Result.RejectedInvalid }
        if (octets.any { it !in 0..255 }) return Result.RejectedInvalid

        return if (isPrivateRfc1918(octets)) Result.Allowed else Result.RejectedNotPrivate
    }

    fun isAllowed(input: String): Boolean = validate(input) is Result.Allowed

    private fun isPrivateRfc1918(octets: List<Int>): Boolean {
        val (a, b, _, _) = octets
        return when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
    }
}
