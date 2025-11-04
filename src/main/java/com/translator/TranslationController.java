package com.translator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
            // 1️⃣ Validate file
            if (audioFile == null || audioFile.isEmpty()) {
                response.put("error", "No audio file provided.");
                return ResponseEntity.badRequest().body(response);
            }

            // 2️⃣ Model path selection
            String modelPath = switch (sourceLang.toLowerCase()) {
                case "hi" -> "C:\\Users\\myida\\Downloads\\vosk-model-small-hi-0.22";
                case "ta" -> "C:\\Users\\myida\\Downloads\\vosk-model-small-ta-0.22";
                default -> "C:\\Users\\myida\\Downloads\\vosk-model-en-in-0.5";
            };

            // 3️⃣ Load model dynamically
            sttService.loadModel(modelPath);
            if (!sttService.isModelLoaded()) {
                response.put("error", "Vosk model not loaded. Check backend logs for path issues.");
                return ResponseEntity.status(500).body(response);
            }

            // 4️⃣ Transcribe
            String transcribedText = sttService.transcribe(audioFile);

            if (transcribedText.isEmpty()) {
                response.put("error", "No speech detected.");
                return ResponseEntity.ok(response);
            }

            // 5️⃣ Translate using online API
            RestTemplate restTemplate = new RestTemplate();
            String url = String.format(TRANSLATE_API,
                    java.net.URLEncoder.encode(transcribedText, "UTF-8"),
                    sourceLang, targetLang);

            Map<String, Object> apiResponse = restTemplate.getForObject(url, Map.class);
            String translatedText = ((Map<String, String>) apiResponse.get("responseData")).get("translatedText");

            // 6️⃣ Convert translated text → Speech (Google TTS)
            String ttsUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q="
                    + java.net.URLEncoder.encode(translatedText, "UTF-8")
                    + "&tl=" + targetLang + "&client=tw-ob";

            // Download MP3
            byte[] audioBytes = restTemplate.getForObject(ttsUrl, byte[].class);
            File audioOutput = File.createTempFile("translated_", ".mp3");
            try (FileOutputStream fos = new FileOutputStream(audioOutput)) {
                fos.write(audioBytes);
            }

            // 7️⃣ Prepare response
            response.put("transcribed_text", transcribedText);
            response.put("translated_text", translatedText);
            response.put("speech_file_path", audioOutput.getAbsolutePath());
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
}
