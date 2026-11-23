package com.example.sjassistant.ai

import com.example.sjassistant.core.i18n.AppLanguage

data class KnowledgeEntry(
    val id: String,
    val category: String,
    val titleMr: String,
    val titleHi: String,
    val titleEn: String,
    val bodyMr: String,
    val bodyHi: String,
    val bodyEn: String,
    val keywords: List<String>
) {
    fun title(lang: AppLanguage): String = when (lang) {
        AppLanguage.MARATHI -> titleMr
        AppLanguage.HINDI -> titleHi
        AppLanguage.ENGLISH -> titleEn
    }

    fun body(lang: AppLanguage): String = when (lang) {
        AppLanguage.MARATHI -> bodyMr
        AppLanguage.HINDI -> bodyHi
        AppLanguage.ENGLISH -> bodyEn
    }
}

/**
 * Curated offline multilingual knowledge base optimized for Indian Android users
 * (emergency helplines, first aid, weather/monsoon safety, digital/UPI safety,
 * government documents, farming & water tips, battery optimization).
 */
object OfflineKnowledgeBase {

    val entries: List<KnowledgeEntry> = listOf(
        KnowledgeEntry(
            id = "kb_emergency",
            category = "Safety",
            titleMr = "भारतातील आपत्कालीन हेल्पलाइन क्रमांक",
            titleHi = "भारत के आपातकालीन हेल्पलाइन नंबर",
            titleEn = "India Emergency Helpline Numbers",
            bodyMr = "• ११२ — राष्ट्रीय एकच आपत्कालीन क्रमांक (पोलीस, अग्निशमन, रुग्णवाहिका)\n• १०८ / १०२ — रुग्णवाहिका (Ambulance)\n• १०० — पोलीस, १०१ — अग्निशमन दल\n• १०९८ — चाइल्डलाइन, १८१ — महिला हेल्पलाइन\n• १९३० — सायबर फसवणूक (Cyber Crime)",
            bodyHi = "• 112 — राष्ट्रीय एकल आपातकालीन नंबर\n• 108 / 102 — एम्बुलेंस सेवा\n• 100 — पुलिस, 101 — फायर ब्रिगेड\n• 1098 — चाइल्डलाइन, 181 — महिला हेल्पलाइन\n• 1930 — साइबर फ्रॉड हेल्पलाइन",
            bodyEn = "• 112 — National Single Emergency Number\n• 108 / 102 — Ambulance\n• 100 — Police, 101 — Fire Brigade\n• 1098 — Childline, 181 — Women Helpline\n• 1930 — Cyber Fraud Reporting",
            keywords = listOf(
                "emergency", "police", "ambulance", "helpline", "112", "108", "cyber",
                "आपत्कालीन", "रुग्णवाहिका", "पोलीस", "मदत", "क्रमांक",
                "आपातकालीन", "एम्बुलेंस", "पुलिस", "हेल्पलाइन", "नंबर"
            )
        ),
        KnowledgeEntry(
            id = "kb_first_aid_heat",
            category = "Health",
            titleMr = "ताप व उष्माघात (Heatstroke) प्रथमोपचार",
            titleHi = "बुखार और लू (Heatstroke) प्राथमिक उपचार",
            titleEn = "Fever & Heatstroke First Aid",
            bodyMr = "• व्यक्तीला तात्काळ सावलीत किंवा थंड जागी न्या.\n• कपाळावर व मानेवर थंड ओल्या कापडाची पट्टी ठेवा.\n• शुद्धीत असल्यास ORS, लिंबूपाणी किंवा ताक थोड्या-थोड्या वेळाने द्या.\n• खूप ताप (१०३°F+), चक्कर किंवा बेशुद्धी असल्यास तात्काळ १०८ वर कॉल करून डॉक्टरांचा सल्ला घ्या.",
            bodyHi = "• व्यक्ति को तुरंत छाया या ठंडी जगह पर ले जाएं।\n• माथे और गर्दन पर ठंडे गीले कपड़े की पट्टी रखें।\n• होश में होने पर ORS, नींबू पानी या छाछ थोड़ा-थोड़ा पिलाएं।\n• तेज़ बुखार (103°F+) या बेहोशी होने पर तुरंत 108 पर कॉल कर डॉक्टर को दिखाएं।",
            bodyEn = "• Move the person immediately to shade or a cool area.\n• Apply cool damp cloths to the forehead, neck, and wrists.\n• If conscious, give small sips of ORS, lemon water, or buttermilk.\n• Seek urgent medical care (108) if fever exceeds 103°F or confusion occurs.",
            keywords = listOf(
                "fever", "heatstroke", "first aid", "ors", "health", "doctor",
                "ताप", "उष्माघात", "प्रथमोपचार", "आरोग्य", "चक्कर",
                "बुखार", "लू", "प्राथमिक", "उपचार", "स्वास्थ्य"
            )
        ),
        KnowledgeEntry(
            id = "kb_weather_offline",
            category = "Weather",
            titleMr = "ऑफलाइन हवामान व मान्सून काळजी मार्गदर्शन",
            titleHi = "ऑफलाइन मौसम और मानसून सुरक्षा मार्गदर्शिका",
            titleEn = "Offline Weather & Monsoon Preparedness",
            bodyMr = "• हा अ‍ॅप १००% ऑफलाइन असल्याने रिअल-टाइम तापमान इंटरनेटशिवाय उपलब्ध नाही.\n• मान्सून/वादळापूर्वी फोन व पॉवर बँक पूर्ण चार्ज ठेवा.\n• विजा कडाडताना उघड्या शेतात किंवा झाडाखाली उभे राहू नका; पक्क्या घरात आश्रय घ्या.\n• पिण्याचं पाणी उकळून किंवा फिल्टर करून वापरा.",
            bodyHi = "• यह ऐप 100% ऑफलाइन है, इसलिए इंटरनेट के बिना लाइव तापमान उपलब्ध नहीं है।\n• बारिश या तूफान से पहले फोन और पावर बैंक चार्ज रखें।\n• बिजली चमकने पर खुले खेत या पेड़ के नीचे न खड़े हों; सुरक्षित पक्के मकान में रहें।\n• पीने का पानी उबालकर या छानकर पिएं।",
            bodyEn = "• Because SJ Assistant runs 100% offline, live weather forecasts require network access.\n• Offline safety tips: keep your phone and power bank charged before heavy rain.\n• Avoid open fields or tall trees during lightning; stay indoors.\n• Boil or filter drinking water during monsoon season.",
            keywords = listOf(
                "weather", "rain", "monsoon", "storm", "temperature", "forecast",
                "हवामान", "पाऊस", "मान्सून", "वादळ", "थंडी", "उन्हाळा",
                "मौसम", "बारिश", "मानसून", "तूफान", "तापमान", "havaman", "mausam"
            )
        ),
        KnowledgeEntry(
            id = "kb_upi_safety",
            category = "Digital Safety",
            titleMr = "UPI आणि डिजिटल पेमेंट सुरक्षा नियम",
            titleHi = "UPI और डिजिटल पेमेंट सुरक्षा नियम",
            titleEn = "UPI & Digital Payment Safety Rules",
            bodyMr = "• पैसे स्वीकारण्यासाठी कधीही UPI PIN टाकावा लागत नाही — PIN फक्त पैसे पाठवताना लागतो.\n• अनोळखी लिंक, स्क्रीन-शेअरिंग अ‍ॅप्स किंवा 'लॉटरी/KYC अपडेट' कॉलवर OTP शेअर करू नका.\n• फसवणूक झाल्यास तात्काळ १९३० वर तक्रार करा.",
            bodyHi = "• पैसे प्राप्त करने के लिए कभी UPI PIN नहीं डालना पड़ता — PIN केवल पैसे भेजने के लिए होता है।\n• अनजान लिंक, स्क्रीन-शेयरिंग ऐप या KYC कॉल पर OTP साझा न करें।\n• धोखाधड़ी होने पर तुरंत 1930 पर कॉल करें।",
            bodyEn = "• You NEVER need to enter your UPI PIN to receive money—PIN is only for sending.\n• Never share OTPs or install screen-sharing apps for unknown callers.\n• Report financial cyber fraud immediately by dialing 1930.",
            keywords = listOf(
                "upi", "scam", "fraud", "otp", "bank", "payment", "kyc", "safety",
                "पेमेंट", "फसवणूक", "बँक", "सुरक्षा", "यूपीआय",
                "यूपीआई", "धोखाधड़ी", "बैंक", "सुरक्षा"
            )
        ),
        KnowledgeEntry(
            id = "kb_gov_docs",
            category = "Documents",
            titleMr = "आवश्यक सरकारी कागदपत्रांची यादी",
            titleHi = "महत्वपूर्ण सरकारी दस्तावेज़ चेकलिस्ट",
            titleEn = "Essential Government Document Checklist",
            bodyMr = "• आधार कार्ड (मोबाईल नंबर लिंक असलेले)\n• पॅन कार्ड (बँकिंग व कर कामासाठी)\n• मतदार ओळखपत्र व शिधापत्रिका (Ration Card)\n• डिजिटल प्रत ऑफलाइन ठेवण्यासाठी DigiLocker अ‍ॅप मधील डाउनलोड PDF सुरक्षित ठेवा.",
            bodyHi = "• आधार कार्ड (मोबाइल नंबर से लिंक)\n• पैन कार्ड (बैंकिंग और टैक्स के लिए)\n• वोटर आईडी और राशन कार्ड\n• ऑफलाइन उपयोग के लिए DigiLocker से डाउनलोड की गई PDF प्रतियां सुरक्षित रखें।",
            bodyEn = "• Aadhaar Card (with updated mobile number linked)\n• PAN Card (required for banking & tax)\n• Voter ID & Ration Card\n• Keep downloaded PDFs from DigiLocker safely on device for offline verification.",
            keywords = listOf(
                "aadhaar", "pan", "ration", "voter", "document", "checklist", "digilocker",
                "आधार", "पॅन", "कागदपत्रे", "रेशन", "ओळखपत्र",
                "आधार", "पैन", "दस्तावेज़", "राशन", "पहचान"
            )
        ),
        KnowledgeEntry(
            id = "kb_agri_soil",
            category = "Agriculture",
            titleMr = "शेती व पाणी बचतीच्या सोप्या पद्धती",
            titleHi = "खेती और जल संरक्षण के सरल उपाय",
            titleEn = "Farming & Water Conservation Basics",
            bodyMr = "• ठिबक सिंचन (Drip Irrigation) वापरल्यास ४०% ते ६०% पाण्याची बचत होते.\n• पिकांच्या मुळाशी आच्छादन (Mulching) केल्यास जमिनीतील ओलावा टिकून राहतो.\n• पेरणीपूर्वी माती परीक्षण (Soil Health Card) केल्यास खताचा खर्च कमी होतो.",
            bodyHi = "• ड्रिप सिंचाई (टपक सिंचाई) से 40% से 60% तक पानी की बचत होती है।\n• मल्चिंग (पलवार) करने से मिट्टी की नमी लंबे समय तक बनी रहती है।\n• बुवाई से पहले मिट्टी की जांच (Soil Health Card) कराने से खाद की लागत घटती है।",
            bodyEn = "• Drip irrigation saves 40–60% water compared to flood irrigation.\n• Organic mulching keeps soil moist and suppresses weeds.\n• Soil testing before sowing reduces unnecessary fertilizer cost.",
            keywords = listOf(
                "farm", "farming", "agriculture", "crop", "soil", "water", "irrigation",
                "शेती", "पीक", "पाणी", "ठिबक", "माती", "शेतकरी",
                "खेती", "फसल", "मिट्टी", "सिंचाई", "किसान"
            )
        ),
        KnowledgeEntry(
            id = "kb_battery_tips",
            category = "Phone Care",
            titleMr = "कमी रॅम फोनसाठी बॅटरी आणि स्पीड टिप्स",
            titleHi = "कम RAM वाले फोन के लिए बैटरी और स्पीड टिप्स",
            titleEn = "Battery & Speed Tips for 2GB–4GB RAM Phones",
            bodyMr = "• अ‍ॅपमध्ये डार्क मोड चालू ठेवा — OLED स्क्रीनवर बॅटरी वाचते.\n• न वापरलेले बॅकग्राउंड अ‍ॅप्स बंद ठेवा व कॅशे वेळोवेळी साफ करा.\n• ऑफलाइन AI वापरताना ५०० MB पेक्षा हलकी मॉडेल्स (उदा. SmolLM2 किंवा Qwen2.5 0.5B) वापरा.",
            bodyHi = "• ऐप में डार्क मोड चालू रखें — इससे बैटरी की बचत होती है।\n• उपयोग में न आने वाले बैकग्राउंड ऐप्स बंद रखें।\n• कम RAM में बेहतर गति के लिए SmolLM2 360M या Qwen2.5 0.5B जैसे हल्के मॉडल चुनें।",
            bodyEn = "• Keep Dark Mode enabled to reduce screen power draw.\n• Close unused heavy background apps on 2GB–4GB RAM devices.\n• Prefer quantized Q4_K_M models under 500MB RAM (Qwen2.5 0.5B or SmolLM2 360M).",
            keywords = listOf(
                "battery", "ram", "slow", "hang", "speed", "storage", "model", "memory",
                "बॅटरी", "रॅम", "मेमरी", "हँग", "वेग", "मॉडेल",
                "बैटरी", "स्पीड", "मेमोरी", "मॉडल"
            )
        )
    )

    fun search(query: String, limit: Int = 3): List<KnowledgeEntry> {
        val tokens = query.lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        if (tokens.isEmpty()) return entries.take(limit)

        return entries.map { entry ->
            var score = 0
            val searchable = (entry.titleMr + " " + entry.titleHi + " " + entry.titleEn + " " +
                entry.bodyMr + " " + entry.bodyHi + " " + entry.bodyEn + " " +
                entry.keywords.joinToString(" ")).lowercase()

            for (t in tokens) {
                if (entry.keywords.any { it.equals(t, ignoreCase = true) }) score += 5
                if (entry.keywords.any { it.contains(t, ignoreCase = true) }) score += 3
                if (searchable.contains(t)) score += 2
            }
            entry to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
