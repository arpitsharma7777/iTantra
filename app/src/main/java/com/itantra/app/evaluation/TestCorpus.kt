package com.itantra.app.evaluation

import com.itantra.app.core.model.Language

/**
 * Test corpus for WER evaluation.
 *
 * Each test case has:
 * - A unique ID
 * - A language
 * - A reference transcription (ground truth)
 * - An audio duration estimate
 * - A category (short/long sentence, emergency, etc.)
 *
 * When STT is implemented, the actual audio input will be paired with these
 * references to measure WER. For now, the corpus defines the test cases
 * that the benchmark runner will use.
 */
object TestCorpus {

    data class TestCase(
        val id: String,
        val language: Language,
        val referenceText: String,
        val estimatedAudioDurationMs: Long = 3000L,
        val category: TestCategory = TestCategory.GENERAL
    )

    enum class TestCategory {
        GENERAL,
        EMERGENCY,
        SHORT,
        LONG,
        NUMERICAL,
        MIXED_SCRIPT
    }

    val testCases: List<TestCase> = listOf(
        // English
        TestCase("EN_001", Language.ENGLISH, "I need help", 2000L, TestCategory.EMERGENCY),
        TestCase("EN_002", Language.ENGLISH, "There is a fire", 2500L, TestCategory.EMERGENCY),
        TestCase("EN_003", Language.ENGLISH, "Send the rescue team", 3000L, TestCategory.EMERGENCY),
        TestCase("EN_004", Language.ENGLISH, "Everyone is safe", 2500L, TestCategory.GENERAL),
        TestCase("EN_005", Language.ENGLISH, "Where is the nearest hospital", 3500L, TestCategory.GENERAL),
        TestCase("EN_006", Language.ENGLISH, "Please call an ambulance immediately", 4000L, TestCategory.EMERGENCY),
        TestCase("EN_007", Language.ENGLISH, "I am injured and need medical assistance", 4500L, TestCategory.LONG),
        TestCase("EN_008", Language.ENGLISH, "There are five people trapped in the building", 5000L, TestCategory.LONG),
        TestCase("EN_009", Language.ENGLISH, "The water level is rising rapidly", 3500L, TestCategory.GENERAL),
        TestCase("EN_010", Language.ENGLISH, "We need food and clean water", 3000L, TestCategory.GENERAL),
        TestCase("EN_011", Language.ENGLISH, "Hello how are you", 2000L, TestCategory.SHORT),
        TestCase("EN_012", Language.ENGLISH, "My name is John", 2000L, TestCategory.SHORT),
        TestCase("EN_013", Language.ENGLISH, "The earthquake measured 6.5 on the richter scale", 5500L, TestCategory.NUMERICAL),
        TestCase("EN_014", Language.ENGLISH, "Three buildings have collapsed near the main road", 5000L, TestCategory.LONG),
        TestCase("EN_015", Language.ENGLISH, "Please evacuate all residents from zone A", 4500L, TestCategory.EMERGENCY),

        // Hindi
        TestCase("HI_001", Language.HINDI, "मुझे मदद चाहिए", 2500L, TestCategory.EMERGENCY),
        TestCase("HI_002", Language.HINDI, "आग लग गई है", 2000L, TestCategory.EMERGENCY),
        TestCase("HI_003", Language.HINDI, "बचाव दल को बुलाइए", 3000L, TestCategory.EMERGENCY),
        TestCase("HI_004", Language.HINDI, "सभी लोग सुरक्षित हैं", 2500L, TestCategory.GENERAL),
        TestCase("HI_005", Language.HINDI, "निकटतम अस्पताल कहाँ है", 3000L, TestCategory.GENERAL),
        TestCase("HI_006", Language.HINDI, "कृपया तुरंत एम्बुलेंस बुलाइए", 3500L, TestCategory.EMERGENCY),
        TestCase("HI_007", Language.HINDI, "मुझे चोट लगी है और मुझे चिकित्सा सहायता चाहिए", 5000L, TestCategory.LONG),
        TestCase("HI_008", Language.HINDI, "पाँच लोग इमारत में फंसे हुए हैं", 4000L, TestCategory.LONG),
        TestCase("HI_009", Language.HINDI, "पानी का स्तर तेजी से बढ़ रहा है", 3500L, TestCategory.GENERAL),
        TestCase("HI_010", Language.HINDI, "हमें भोजन और स्वच्छ पानी चाहिए", 3500L, TestCategory.GENERAL),
        TestCase("HI_011", Language.HINDI, "नमस्ते आप कैसे हैं", 2000L, TestCategory.SHORT),
        TestCase("HI_012", Language.HINDI, "मेरा नाम राम है", 2000L, TestCategory.SHORT),
        TestCase("HI_013", Language.HINDI, "भूकंप की तीव्रता 6.5 थी", 3500L, TestCategory.NUMERICAL),
        TestCase("HI_014", Language.HINDI, "मुख्य सड़क के पास तीन इमारतें गिर गई हैं", 5000L, TestCategory.LONG),
        TestCase("HI_015", Language.HINDI, "कृपया क्षेत्र ए से सभी निवासियों को निकालें", 4500L, TestCategory.EMERGENCY),

        // Gujarati
        TestCase("GU_001", Language.GUJARATI, "મને મદદની જરૂર છે", 2500L, TestCategory.EMERGENCY),
        TestCase("GU_002", Language.GUJARATI, "અગ્નિ લાગી ગઈ છે", 2000L, TestCategory.EMERGENCY),
        TestCase("GU_003", Language.GUJARATI, "બચાવ ટીમને બોલાવો", 2500L, TestCategory.EMERGENCY),
        TestCase("GU_004", Language.GUJARATI, "બધા લોકો સુરક્ષિત છે", 2500L, TestCategory.GENERAL),
        TestCase("GU_005", Language.GUJARATI, "નજીકની હોસ્પિટલ ક્યાં છે", 3000L, TestCategory.GENERAL),

        // Marathi
        TestCase("MR_001", Language.MARATHI, "मला मदतीची गरज आहे", 2500L, TestCategory.EMERGENCY),
        TestCase("MR_002", Language.MARATHI, "आग लागली आहे", 2000L, TestCategory.EMERGENCY),
        TestCase("MR_003", Language.MARATHI, "बचाव दलाला बोलावा", 2500L, TestCategory.EMERGENCY),
        TestCase("MR_004", Language.MARATHI, "सर्व लोक सुरक्षित आहेत", 2500L, TestCategory.GENERAL),
        TestCase("MR_005", Language.MARATHI, "जवळचे रुग्णालय कुठे आहे", 3000L, TestCategory.GENERAL),

        // Kannada
        TestCase("KN_001", Language.KANNADA, "ನನಗೆ ಸಹಾಯ ಬೇಕು", 2500L, TestCategory.EMERGENCY),
        TestCase("KN_002", Language.KANNADA, "ಬೆಂಕಿ ಹತ್ತಿಕೊಂಡಿದೆ", 2000L, TestCategory.EMERGENCY),
        TestCase("KN_003", Language.KANNADA, "ರಕ್ಷಣಾ ತಂಡವನ್ನು ಕರೆಯಿರಿ", 3000L, TestCategory.EMERGENCY),
        TestCase("KN_004", Language.KANNADA, "ಎಲ್ಲರೂ ಸುರಕ್ಷಿತರಾಗಿದ್ದಾರೆ", 2500L, TestCategory.GENERAL),
        TestCase("KN_005", Language.KANNADA, "ಹತ್ತಿರದ ಆಸ್ಪತ್ರೆ ಎಲ್ಲಿದೆ", 3000L, TestCategory.GENERAL),

        // Malayalam
        TestCase("ML_001", Language.MALAYALAM, "എനിക്ക് സഹായം വേണം", 2500L, TestCategory.EMERGENCY),
        TestCase("ML_002", Language.MALAYALAM, "തീ പടർന്നു", 2000L, TestCategory.EMERGENCY),
        TestCase("ML_003", Language.MALAYALAM, "രക്ഷാസംഘത്തെ വിളിക്കൂ", 2500L, TestCategory.EMERGENCY),
        TestCase("ML_004", Language.MALAYALAM, "എല്ലാവരും സുരക്ഷിതരാണ്", 2500L, TestCategory.GENERAL),
        TestCase("ML_005", Language.MALAYALAM, "അടുത്തുള്ള ആശുപത്രി എവിടെയാണ്", 3500L, TestCategory.GENERAL),

        // Tamil
        TestCase("TA_001", Language.TAMIL, "எனக்கு உதவி தேவை", 2500L, TestCategory.EMERGENCY),
        TestCase("TA_002", Language.TAMIL, "தீ பரவியது", 2000L, TestCategory.EMERGENCY),
        TestCase("TA_003", Language.TAMIL, "மீட்பு குழுவை அழையுங்கள்", 3000L, TestCategory.EMERGENCY),
        TestCase("TA_004", Language.TAMIL, "அனைவரும் பாதுகாப்பாக இருக்கிறார்கள்", 3500L, TestCategory.GENERAL),
        TestCase("TA_005", Language.TAMIL, "அருகிலுள்ள மருத்துவமனை எங்கே", 3000L, TestCategory.GENERAL),

        // Telugu
        TestCase("TE_001", Language.TELUGU, "నాకు సహాయం కావాలి", 2500L, TestCategory.EMERGENCY),
        TestCase("TE_002", Language.TELUGU, "మంట చెలరేగింది", 2000L, TestCategory.EMERGENCY),
        TestCase("TE_003", Language.TELUGU, "రక్షణ బృందాన్ని పిలవండి", 3000L, TestCategory.EMERGENCY),
        TestCase("TE_004", Language.TELUGU, "అందరూ సురక్షితంగా ఉన్నారు", 3000L, TestCategory.GENERAL),
        TestCase("TE_005", Language.TELUGU, "సమీపంలోని ఆసుపత్రి ఎక్కడ ఉంది", 3500L, TestCategory.GENERAL),

        // Odia
        TestCase("OR_001", Language.ODIA, "ମୋତେ ସାହାଯ୍ୟ ଦରକାର", 2500L, TestCategory.EMERGENCY),
        TestCase("OR_002", Language.ODIA, "ଅଗ୍ନି ଲାଗିଛି", 2000L, TestCategory.EMERGENCY),
        TestCase("OR_003", Language.ODIA, "ଉଦ୍ଧାର ଦଳକୁ ଡାକନ୍ତୁ", 2500L, TestCategory.EMERGENCY),
        TestCase("OR_004", Language.ODIA, "ସମସ୍ତେ ସୁରକ୍ଷିତ ଅଛନ୍ତି", 2500L, TestCategory.GENERAL),
        TestCase("OR_005", Language.ODIA, "ନିକଟତମ ଡାକ୍ତରଖାନା କୁଆଡେ ଅଛି", 3500L, TestCategory.GENERAL),

        // Bengali
        TestCase("BN_001", Language.BENGALI, "আমার সাহায্য দরকার", 2500L, TestCategory.EMERGENCY),
        TestCase("BN_002", Language.BENGALI, "আগুন লেগে গেছে", 2000L, TestCategory.EMERGENCY),
        TestCase("BN_003", Language.BENGALI, "উদ্ধার দলকে ডাকুন", 2500L, TestCategory.EMERGENCY),
        TestCase("BN_004", Language.BENGALI, "সবাই নিরাপদ", 2000L, TestCategory.GENERAL),
        TestCase("BN_005", Language.BENGALI, "নিকটতম হাসপাতাল কোথায়", 3000L, TestCategory.GENERAL)
    )

    /**
     * Gets test cases for a specific language.
     */
    fun getForLanguage(language: Language): List<TestCase> {
        return testCases.filter { it.language == language }
    }

    /**
     * Gets test cases for a specific category.
     */
    fun getForCategory(category: TestCategory): List<TestCase> {
        return testCases.filter { it.category == category }
    }

    /**
     * Gets a test case by ID.
     */
    fun getById(id: String): TestCase? {
        return testCases.find { it.id == id }
    }

    /**
     * Gets supported languages in the corpus.
     */
    fun supportedLanguages(): List<Language> {
        return testCases.map { it.language }.distinct()
    }
}
