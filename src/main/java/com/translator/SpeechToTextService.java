package com.translator;

import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import jakarta.annotation.PreDestroy;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

@Service
public class SpeechToTextService {

    // 🔹 Store already loaded models in memory to avoid reloading
    private final Map<String, Model> loadedModels = new HashMap<>();

    // 🔹 Currently active model
    private Model currentModel;

    // 🧠 Load model dynamically based on language
    public void loadModel(String modelPath) throws IOException {
        if (loadedModels.containsKey(modelPath)) {
            currentModel = loadedModels.get(modelPath);
            System.out.println("✅ Using cached model: " + modelPath);
            return;
        }

        File modelDir = new File(modelPath);
        if (!modelDir.exists()) {
            throw new IOException("❌ Model path not found: " + modelDir.getAbsolutePath());
        }

        System.out.println("🔄 Loading model from: " + modelPath);
        Model model = new Model(modelPath);
        loadedModels.put(modelPath, model);
        currentModel = model;
        System.out.println("✅ Model loaded successfully: " + modelPath);
    }

    // 🎧 Transcribe Audio
    public String transcribe(MultipartFile audioFile) throws IOException {
        if (currentModel == null) {
            throw new IOException("⚠️ No model loaded! Call loadModel() before transcribing.");
        }

        // Convert multipart to temp file
        File tempFile = File.createTempFile("upload_", ".wav");
        audioFile.transferTo(tempFile);

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(tempFile)) {

            AudioFormat baseFormat = ais.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,     // sample rate
                    16,        // bits per sample
                    1,         // mono
                    2,         // frame size
                    16000,     // frame rate
                    false
            );

            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, ais);
            Recognizer recognizer = new Recognizer(currentModel, targetFormat.getSampleRate());

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = convertedStream.read(buffer)) >= 0) {
                recognizer.acceptWaveForm(buffer, bytesRead);
            }

            String result = recognizer.getFinalResult();
            JSONObject json = new JSONObject(result);
            return json.optString("text", "(No speech recognized)");

        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio format. Please ensure WAV 16kHz Mono PCM.", e);
        } catch (Exception e) {
            throw new IOException("Error during transcription: " + e.getMessage(), e);
        } finally {
            tempFile.delete(); // cleanup
        }
    }

    // ✅ Check if model loaded
    public boolean isModelLoaded() {
        return currentModel != null;
    }

    // 🧹 Cleanup models when app shuts down
    @PreDestroy
    public void cleanup() {
        System.out.println("🧹 Releasing Vosk models...");
        for (Model model : loadedModels.values()) {
            model.close();
        }
        loadedModels.clear();
    }
}
