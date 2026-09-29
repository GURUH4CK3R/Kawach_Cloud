package com.kawach.cloud

import com.kawach.cloud.data.model.CloudFile
import com.kawach.cloud.data.model.CountryRepository
import com.kawach.cloud.data.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun defaultCountry_isIndia() {
    val country = CountryRepository.DEFAULT_COUNTRY
    assertEquals("India", country.countryName)
    assertEquals("+91", country.dialCode)
    assertEquals("🇮🇳", country.flagEmoji)
    assertEquals("IN", country.countryCodeIso)
    assertTrue(country.displayLabel.contains("India"))
  }

  @Test
  fun countryList_containsMajorCountries() {
    val countries = CountryRepository.COUNTRIES
    assertTrue(countries.size > 20)
    assertNotNull(countries.find { it.countryCodeIso == "IN" })
    assertNotNull(countries.find { it.countryCodeIso == "US" })
    assertNotNull(countries.find { it.countryCodeIso == "GB" })
  }

  @Test
  fun cloudFile_categoriesAndFormatting() {
    val pdf = CloudFile(
      messageId = 1L,
      telegramFileId = 10,
      name = "report.pdf",
      size = 1024 * 1024 * 5, // 5 MB
      mimeType = "application/pdf",
      uploadDate = 1700000000L
    )
    assertEquals(FileCategory.DOCUMENTS, pdf.category)
    assertTrue(pdf.formattedSize.contains("5.0 MB"))

    val img = CloudFile(
      messageId = 2L,
      telegramFileId = 11,
      name = "photo.jpg",
      size = 500 * 1024,
      mimeType = "image/jpeg",
      uploadDate = 1700000000L
    )
    assertEquals(FileCategory.IMAGES, img.category)

    val zip = CloudFile(
      messageId = 3L,
      telegramFileId = 12,
      name = "archive.zip",
      size = 1024 * 1024 * 50,
      mimeType = "application/zip",
      uploadDate = 1700000000L
    )
    assertEquals(FileCategory.ARCHIVES, zip.category)
  }

  @Test
  fun cloudFile_downloadState_andLocalResolution() {
    val cloudFile = CloudFile(
      messageId = 100L,
      telegramFileId = 42,
      name = "document.pdf",
      size = 2048,
      mimeType = "application/pdf",
      uploadDate = 1700000000L,
      localPath = "content://media/external/downloads/100",
      isDownloaded = true
    )
    assertTrue(cloudFile.hasLocalFile)
    assertEquals("document.pdf", cloudFile.name)
    assertEquals("application/pdf", cloudFile.mimeType)
  }

  @Test
  fun duplicateFileNamePattern_correctlyFormatted() {
    val original = "photo.jpg"
    val dotIndex = original.lastIndexOf('.')
    val base = original.substring(0, dotIndex)
    val ext = original.substring(dotIndex)
    val duplicate1 = "$base (1)$ext"
    val duplicate2 = "$base (2)$ext"
    assertEquals("photo (1).jpg", duplicate1)
    assertEquals("photo (2).jpg", duplicate2)
  }

  @Test
  fun folderMetadataPattern_matchesCorrectly() {
    val text = "[KawachCloud:Folder] id:work_docs | name:Work Documents"
    val idMatch = Regex("id:([a-zA-Z0-9_-]+)").find(text)?.groupValues?.getOrNull(1)
    val nameMatch = Regex("name:(.+)").find(text)?.groupValues?.getOrNull(1)?.trim()
    assertEquals("work_docs", idMatch)
    assertEquals("Work Documents", nameMatch)
  }

  @Test
  fun videoAndAudioFiles_correctCategoryAssigned() {
    val video = CloudFile(
      messageId = 10L,
      telegramFileId = 20,
      name = "vacation.mp4",
      size = 1024 * 1024 * 15,
      mimeType = "video/mp4",
      uploadDate = 1700000000L
    )
    assertEquals(FileCategory.VIDEOS, video.category)

    val audio = CloudFile(
      messageId = 11L,
      telegramFileId = 21,
      name = "song.mp3",
      size = 1024 * 1024 * 3,
      mimeType = "audio/mpeg",
      uploadDate = 1700000000L
    )
    assertEquals(FileCategory.AUDIO, audio.category)
  }

  @Test
  fun kawachSignature_filtersUnrelatedMessages() {
    val kawachCaption = "[KawachCloud] folder:docs | id:abc-123 | name:report.pdf #KawachCloud"
    val unrelatedCaption = "Here is a forwarded photo from vacation"
    val emptyCaption = ""

    val isKawach1 = kawachCaption.contains("[KawachCloud]") || kawachCaption.contains("#KawachCloud")
    val isKawach2 = unrelatedCaption.contains("[KawachCloud]") || unrelatedCaption.contains("#KawachCloud")
    val isKawach3 = emptyCaption.contains("[KawachCloud]") || emptyCaption.contains("#KawachCloud")

    assertTrue(isKawach1)
    org.junit.Assert.assertFalse(isKawach2)
    org.junit.Assert.assertFalse(isKawach3)
  }

  @Test
  fun mediaTypeDetection_isImageAndIsVideo() {
    val formats = listOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    for (fmt in formats) {
      val f = CloudFile(
        messageId = 1L,
        telegramFileId = 1,
        name = "test.$fmt",
        size = 100L,
        mimeType = "application/octet-stream",
        uploadDate = 1000L
      )
      assertTrue("Expected $fmt to be image", f.isImage)
      org.junit.Assert.assertFalse(f.isVideo)
    }

    val videoFormats = listOf("mp4", "mkv", "webm", "mov", "avi")
    for (vfmt in videoFormats) {
      val f = CloudFile(
        messageId = 2L,
        telegramFileId = 2,
        name = "test.$vfmt",
        size = 100L,
        mimeType = "application/octet-stream",
        uploadDate = 1000L
      )
      assertTrue("Expected $vfmt to be video", f.isVideo)
      org.junit.Assert.assertFalse(f.isImage)
    }

    val doc = CloudFile(
      messageId = 3L,
      telegramFileId = 3,
      name = "notes.pdf",
      size = 100L,
      mimeType = "application/pdf",
      uploadDate = 1000L
    )
    org.junit.Assert.assertFalse(doc.isImage)
    org.junit.Assert.assertFalse(doc.isVideo)
  }
}

