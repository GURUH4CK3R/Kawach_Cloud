package com.example

import com.example.data.model.CloudFile
import com.example.data.model.CountryRepository
import com.example.data.model.FileCategory
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
}
