package com.example.data.model

data class CountryCode(
    val countryName: String,
    val dialCode: String,
    val flagEmoji: String,
    val countryCodeIso: String
) {
    val displayLabel: String
        get() = "$flagEmoji $countryName ($dialCode)"
}

object CountryRepository {
    val DEFAULT_COUNTRY = CountryCode(
        countryName = "India",
        dialCode = "+91",
        flagEmoji = "🇮🇳",
        countryCodeIso = "IN"
    )

    val COUNTRIES = listOf(
        DEFAULT_COUNTRY,
        CountryCode("United States", "+1", "🇺🇸", "US"),
        CountryCode("United Kingdom", "+44", "🇬🇧", "GB"),
        CountryCode("Canada", "+1", "🇨🇦", "CA"),
        CountryCode("Australia", "+61", "🇦🇺", "AU"),
        CountryCode("Germany", "+49", "🇩🇪", "DE"),
        CountryCode("France", "+33", "🇫🇷", "FR"),
        CountryCode("United Arab Emirates", "+971", "🇦🇪", "AE"),
        CountryCode("Saudi Arabia", "+966", "🇸🇦", "SA"),
        CountryCode("Singapore", "+65", "🇸🇬", "SG"),
        CountryCode("Malaysia", "+60", "🇲🇾", "MY"),
        CountryCode("Japan", "+81", "🇯🇵", "JP"),
        CountryCode("South Korea", "+82", "🇰🇷", "KR"),
        CountryCode("Brazil", "+55", "🇧🇷", "BR"),
        CountryCode("Russia", "+7", "🇷🇺", "RU"),
        CountryCode("Turkey", "+90", "🇹🇷", "TR"),
        CountryCode("Indonesia", "+62", "🇮🇩", "ID"),
        CountryCode("Bangladesh", "+880", "🇧🇩", "BD"),
        CountryCode("Pakistan", "+92", "🇵🇰", "PK"),
        CountryCode("Nepal", "+977", "🇳🇵", "NP"),
        CountryCode("Sri Lanka", "+94", "🇱🇰", "LK"),
        CountryCode("South Africa", "+27", "🇿🇦", "ZA"),
        CountryCode("Nigeria", "+234", "🇳🇬", "NG"),
        CountryCode("Kenya", "+254", "🇰🇪", "KE"),
        CountryCode("Spain", "+34", "🇪🇸", "ES"),
        CountryCode("Italy", "+39", "🇮🇹", "IT"),
        CountryCode("Netherlands", "+31", "🇳🇱", "NL"),
        CountryCode("Switzerland", "+41", "🇨🇭", "CH"),
        CountryCode("Sweden", "+46", "🇸🇪", "SE"),
        CountryCode("Norway", "+47", "🇳🇴", "NO"),
        CountryCode("New Zealand", "+64", "🇳🇿", "NZ"),
        CountryCode("Mexico", "+52", "🇲🇽", "MX"),
        CountryCode("Argentina", "+54", "🇦🇷", "AR"),
        CountryCode("Egypt", "+20", "🇪🇬", "EG"),
        CountryCode("Philippines", "+63", "🇵🇭", "PH"),
        CountryCode("Vietnam", "+84", "🇻🇳", "VN"),
        CountryCode("Thailand", "+66", "🇹🇭", "TH"),
        CountryCode("Qatar", "+974", "🇶🇦", "QA"),
        CountryCode("Kuwait", "+965", "🇰🇼", "KW"),
        CountryCode("Oman", "+968", "🇴🇲", "OM")
    ).sortedWith(compareBy({ if (it.countryCodeIso == "IN") 0 else 1 }, { it.countryName }))
}
