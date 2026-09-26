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
}
