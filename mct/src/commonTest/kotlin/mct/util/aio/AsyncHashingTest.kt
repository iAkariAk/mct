package mct.util.aio

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8

class AsyncHashingTest : FreeSpec({

    "AsyncHashingSource md5" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello")

            val hashing = AsyncHashingSource.md5(inner)
            val buf = okio.Buffer()
            hashing.read(buf, 5)
            hashing.hash.hex() shouldBe "5d41402abc4b2a76b9719d911017c592"
        }
    }

    "AsyncHashingSource sha1" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello")

            val hashing = AsyncHashingSource.sha1(inner)
            val buf = okio.Buffer()
            hashing.read(buf, 5)
            hashing.hash.hex() shouldBe "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"
        }
    }

    "AsyncHashingSource sha256" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello")

            val hashing = AsyncHashingSource.sha256(inner)
            val buf = okio.Buffer()
            hashing.read(buf, 5)
            hashing.hash.hex() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        }
    }

    "AsyncHashingSource sha512" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello")

            val hashing = AsyncHashingSource.sha512(inner)
            val buf = okio.Buffer()
            hashing.read(buf, 5)
            hashing.hash.hex() shouldBe "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca7" +
                "2323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043"
        }
    }

    "AsyncHashingSink md5" {
        runBlocking {
            val inner = AsyncBuffer()
            val hashing = AsyncHashingSink.md5(inner)

            hashing.write(okio.Buffer().writeUtf8("hello"), 5)

            // Data should have been forwarded to inner (read before close clears it)
            inner.readUtf8() shouldBe "hello"
            hashing.hash.hex() shouldBe "5d41402abc4b2a76b9719d911017c592"

            hashing.close()
        }
    }

    "AsyncHashingSink sha256" {
        runBlocking {
            val inner = AsyncBuffer()
            val hashing = AsyncHashingSink.sha256(inner)

            hashing.write(okio.Buffer().writeUtf8("hello"), 5)
            hashing.close()

            hashing.hash.hex() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        }
    }

    "AsyncHashingSink hmacSha256" {
        runBlocking {
            val inner = AsyncBuffer()
            val key = "key".encodeUtf8()
            val hashing = AsyncHashingSink.hmacSha256(inner, key)

            hashing.write(okio.Buffer().writeUtf8("hello"), 5)
            hashing.close()

            // Known HMAC-SHA256 value for "hello" with key "key"
            hashing.hash.hex() shouldBe "9307b3b915efb5171ff14d8cb55fbcc798c6c0ef1456d66ded1a6aa723a58b7b"
        }
    }

    "AsyncHashingSource hmacSha256" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello")

            val key = "key".encodeUtf8()
            val hashing = AsyncHashingSource.hmacSha256(inner, key)

            val buf = okio.Buffer()
            hashing.read(buf, 5)
            hashing.hash.hex() shouldBe "9307b3b915efb5171ff14d8cb55fbcc798c6c0ef1456d66ded1a6aa723a58b7b"
        }
    }

    "AsyncHashingSource forwards data to caller" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello world")

            val hashing = AsyncHashingSource.sha256(inner)
            val buf = okio.Buffer()
            val read = hashing.read(buf, 11)
            read shouldBe 11
            buf.readUtf8() shouldBe "hello world"
        }
    }

    "AsyncHashingSink forwards data to delegate" {
        runBlocking {
            val inner = AsyncBuffer()
            val hashing = AsyncHashingSink.sha256(inner)

            hashing.write(okio.Buffer().writeUtf8("forwarded"), 9)
            hashing.flush()

            inner.readUtf8() shouldBe "forwarded"
        }
    }

    "AsyncHashingSource multiple reads" {
        runBlocking {
            val inner = AsyncBuffer()
            inner.writeUtf8("hello world")

            val hashing = AsyncHashingSource.sha256(inner)

            val buf = okio.Buffer()
            hashing.read(buf, 5) // "hello"
            hashing.read(buf, 6) // " world"

            buf.readUtf8() shouldBe "hello world"
            // Hash should reflect all data
            hashing.hash.hex() shouldBe "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        }
    }
})
