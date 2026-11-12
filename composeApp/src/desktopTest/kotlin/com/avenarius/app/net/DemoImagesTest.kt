package com.avenarius.app.net

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The demo's bundled images load through Coil from the URIs the demo hands out. */
class DemoImagesTest {
    private val loader = ImageLoader(PlatformContext.INSTANCE)

    private fun load(uri: String) =
        runBlocking { loader.execute(ImageRequest.Builder(PlatformContext.INSTANCE).data(uri).build()) }
            .also { if (it is ErrorResult) throw AssertionError("$uri did not load", it.throwable) }

    @Test
    fun everyImageTheDemoUsesLoads() {
        val f = DemoFixtures(now = 1_800_000_000_000L)
        val messages = f.history.values.flatten()
        val uris =
            (
                f.users.map { it.avatarUrl } + f.chats.map { it.avatarUrl } + f.stickers.map { it.url } +
                    messages.flatMap { m -> m.media.map { it.url } + m.stickerUrl + m.linkPreview?.imageUrl }
            ).filterNotNull().toSet()
        assertTrue(uris.size >= 10, "$uris")
        for (uri in uris) assertIs<SuccessResult>(load(uri))
    }

    /** A packaged desktop app serves resources from its jar (`jar:file:…!/…`), which Coil reads too. */
    @Test
    fun aDemoImageLoadsFromInsideAJar() {
        val source = URI(DemoFixtures.img("photo_meadow.jpg"))
        assertTrue(source.scheme == "file" || source.scheme == "jar", source.toString())
        val bytes = source.toURL().readBytes()
        val jar = createTempFile(suffix = ".jar").toFile().also(File::deleteOnExit)
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("composeResources/demo/photo_meadow.jpg"))
            zip.write(bytes)
        }
        assertIs<SuccessResult>(load("jar:${jar.toURI()}!/composeResources/demo/photo_meadow.jpg"))
    }
}
