package com.translator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", methods = {RequestMethod.POST, RequestMethod.GET})
public class TranslationController {

    @Autowired
    private SpeechToTextService sttService;

    private static final String TRANSLATE_API = "https://api.mymemory.translated.net/get?q=%s&langpair=%s|%s";

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, String>> home() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "✅ Voice Translator Backend running successfully!");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transcribe")
    public ResponseEntity<Map<String, String>> transcribeAudio(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "sourceLang", defaultValue = "en") String sourceLang,
            @RequestParam(value = "targetLang", defaultValue = "hi") String targetLang) {

        Map<String, String> response = new HashMap<>();

        try {
            if (audioFile == null || audioFile.isEmpty()) {
                response.put("error", "No audio file provided.");
                return ResponseEntity.badRequest().body(response);
            }

            // Select model path
            String modelPath = switch (sourceLang.toLowerCase()) {
                case "hi" -> "C:\\Users\\myida\\Downloads\\vosk-model-small-hi-0.22";
                case "ta" -> "C:\\Users\\myida\\Downloads\\vosk-model-small-ta-0.22";
                default -> "C:\\Users\\myida\\Downloads\\vosk-model-en-in-0.5";
            };

            // Load model
            sttService.loadModel(modelPath);
            if (!sttService.isModelLoaded()) {
                response.put("error", "Vosk model not loaded. Check backend logs for path issues.");
                return ResponseEntity.status(500).body(response);
            }

            // Transcribe
            String transcribedText = sttService.transcribe(audioFile);
            if (transcribedText.isEmpty()) {
                response.put("error", "No speech detected.");
                return ResponseEntity.ok(response);
            }

            // ✅ Translate text using new method (much more reliable)
            String translatedText = translateText(transcribedText, sourceLang, targetLang);

            // Convert translated text → Speech
            File audioOutput = null;
            try {
                RestTemplate restTemplate = new RestTemplate();
                String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q="
                        + URLEncoder.encode(translatedText, StandardCharsets.UTF_8)
                        + "&tl=" + targetLang + "&client=tw-ob";

                byte[] audioBytes = restTemplate.getForObject(ttsUrl, byte[].class);
                audioOutput = File.createTempFile("translated_", ".mp3");
                try (FileOutputStream fos = new FileOutputStream(audioOutput)) {
                    fos.write(audioBytes);
                }
            } catch (Exception ttsEx) {
                // ⚠️ TTS failed, but we still return translation text
                System.err.println("⚠️ TTS failed: " + ttsEx.getMessage());
            }

            // Prepare response
            response.put("transcribed_text", transcribedText);
            response.put("translated_text", translatedText);
            if (audioOutput != null) response.put("speech_file_path", audioOutput.getAbsolutePath());
            response.put("status", "✅ All stages successful");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "❌ Failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/model/status")
    public ResponseEntity<Map<String, String>> checkModelStatus() {
        Map<String, String> response = new HashMap<>();
        boolean loaded = sttService.isModelLoaded();
        response.put("model_loaded", String.valueOf(loaded));
        response.put("status", loaded ? "✅ Model active" : "⚠️ Model not loaded");
        return ResponseEntity.ok(response);
    }

    // ==============================
    // ✅ NEW RELIABLE TRANSLATE METHOD
    // ==============================
    private String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return "(no text to translate)";

        RestTemplate restTemplate = new RestTemplate(getClientHttpRequestFactory());
        try {
            System.out.println("➡️ Translating: " + text + " (" + sourceLang + " → " + targetLang + ")");

            // 1️⃣ Try LibreTranslate first
            String libreUrl = "https://libretranslate.de/translate";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("q", text);
            body.put("source", sourceLang);
            body.put("target", targetLang);
            body.put("format", "text");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            try {
                Map<String, Object> resp = restTemplate.postForObject(libreUrl, request, Map.class);
                System.out.println("📥 LibreTranslate response: " + resp);
                if (resp != null && resp.containsKey("translatedText")) {
                    String translated = resp.get("translatedText").toString();
                    if (!translated.isBlank()) {
                        System.out.println("✅ LibreTranslate returned: " + translated);
                        return translated;
                    }
                }
            } catch (RestClientException re) {
                System.err.println("⚠️ LibreTranslate failed: " + re.getMessage());
            }

            // 2️⃣ Fallback to MyMemory
            System.out.println("🔁 Falling back to MyMemory...");
            String url = String.format(TRANSLATE_API,
                    URLEncoder.encode(text, StandardCharsets.UTF_8),
                    sourceLang, targetLang);

            Map<String, Object> apiResponse = restTemplate.getForObject(url, Map.class);
            System.out.println("📥 MyMemory raw response: " + apiResponse);

            if (apiResponse != null && apiResponse.containsKey("responseData")) {
                Map<?, ?> rd = (Map<?, ?>) apiResponse.get("responseData");
                if (rd != null && rd.containsKey("translatedText")) {
                    String translated = rd.get("translatedText").toString();
                    System.out.println("✅ MyMemory returned: " + translated);
                    return translated;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Translation error: " + e.getMessage());
        }

        return "(translation failed)";
    }

    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        int timeout = 5000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}