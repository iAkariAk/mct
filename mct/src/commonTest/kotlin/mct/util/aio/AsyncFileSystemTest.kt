package mct.util.aio

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class AsyncFileSystemTest : FreeSpec({

    "zio adapter wraps FileSystem" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("hello") }
            val async = fake.zio()
            async.exists("/test.txt".toPath()) shouldBe true
        }
    }

    "source reads file content" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("hello") }
            val async = fake.zio()
            val src = async.source("/test.txt".toPath())
            val buf = okio.Buffer()
            src.read(buf, 5)
            buf.readUtf8() shouldBe "hello"
            src.close()
        }
    }

    "sink writes file content" {
        runBlocking {
            val fake = FakeFileSystem()
            val async = fake.zio()
            val snk = async.sink("/test.txt".toPath())
            snk.write(okio.Buffer().writeUtf8("hello"), 5)
            snk.close()

            val content = fake.read("/test.txt".toPath()) { readUtf8() }
            content shouldBe "hello"
        }
    }

    "read() inline helper" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("hello world") }
            val async = fake.zio()
            val content = async.read("/test.txt".toPath()) { readUtf8() }
            content shouldBe "hello world"
        }
    }

    "write() inline helper" {
        runBlocking {
            val fake = FakeFileSystem()
            val async = fake.zio()
            async.write("/test.txt".toPath()) { writeUtf8("hello world") }
            val content = fake.read("/test.txt".toPath()) { readUtf8() }
            content shouldBe "hello world"
        }
    }

    "createDirectory" {
        runBlocking {
            val fake = FakeFileSystem()
            val async = fake.zio()
            async.createDirectory("/newdir".toPath())
            async.exists("/newdir".toPath()) shouldBe true
        }
    }

    "createDirectories recursive" {
        runBlocking {
            val fake = FakeFileSystem()
            val async = fake.zio()
            async.createDirectories("/a/b/c".toPath())
            async.exists("/a".toPath()) shouldBe true
            async.exists("/a/b".toPath()) shouldBe true
        }
    }

    "delete" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("data") }
            val async = fake.zio()
            async.delete("/test.txt".toPath())
            async.exists("/test.txt".toPath()) shouldBe false
        }
    }

    "atomicMove" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/source.txt".toPath()) { writeUtf8("data") }
            val async = fake.zio()
            async.atomicMove("/source.txt".toPath(), "/target.txt".toPath())
            async.exists("/source.txt".toPath()) shouldBe false
            async.exists("/target.txt".toPath()) shouldBe true
        }
    }

    "copy" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/source.txt".toPath()) { writeUtf8("data") }
            val async = fake.zio()
            async.copy("/source.txt".toPath(), "/target.txt".toPath())
            async.exists("/source.txt".toPath()) shouldBe true
            async.exists("/target.txt".toPath()) shouldBe true
        }
    }

    "appendingSink" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("hello ") }
            val async = fake.zio()
            val snk = async.appendingSink("/test.txt".toPath())
            snk.write(okio.Buffer().writeUtf8("world"), 5)
            snk.close()

            val content = fake.read("/test.txt".toPath()) { readUtf8() }
            content shouldBe "hello world"
        }
    }

    "list files in directory" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/a.txt".toPath()) { writeUtf8("a") }
            fake.write("/b.txt".toPath()) { writeUtf8("b") }
            val async = fake.zio()
            val files = async.list("/".toPath())
            files.size shouldBe 2
        }
    }

    "SYSTEM_TEMPORARY_DIRECTORY should exist" {
        AsyncFileSystem.SYSTEM_TEMPORARY_DIRECTORY shouldNotBe null
    }

    "readText and writeText" {
        runBlocking {
            val fake = FakeFileSystem()
            val async = fake.zio()
            async.writeText("/test.txt".toPath(), "hello")
            val text = async.readText("/test.txt".toPath())
            text shouldBe "hello"
        }
    }

    "openReadOnly file handle" {
        runBlocking {
            val fake = FakeFileSystem()
            fake.write("/test.txt".toPath()) { writeUtf8("hello") }
            val async = fake.zio()
            val handle = async.openReadOnly("/test.txt".toPath())
            val buf = ByteArray(5)
            handle.read(0, buf, 0, 5)
            buf shouldBe "hello".encodeToByteArray()
            handle.close()
        }
    }
})
