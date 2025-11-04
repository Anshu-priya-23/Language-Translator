// ================================
// 🎧 Voice Translator Frontend JS
// ================================

const BACKEND_URL = 'http://localhost:8080/api/v1/transcribe';

const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const statusDiv = document.getElementById('status');
const heardTextDiv = document.getElementById('heardText');
const outputDiv = document.getElementById('output');
const inputLangSelect = document.getElementById('inputLang');
const outputLangSelect = document.getElementById('outputLang');

let recorder;
let stream;
let translatedText = ""; // 🌐 store translated text for replay

// 🎙️ Start Recording
startBtn.onclick = async () => {
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    recorder = RecordRTC(stream, {
      type: 'audio',
      mimeType: 'audio/webm;codecs=opus',
      recorderType: StereoAudioRecorder,
      numberOfAudioChannels: 1,
      desiredSampRate: 16000
    });

    recorder.startRecording();
    statusDiv.textContent = "🎤 Recording... Speak clearly!";
    startBtn.disabled = true;
    stopBtn.disabled = false;
  } catch (err) {
    console.error(err);
    statusDiv.textContent = "❌ Microphone permission denied or not supported.";
  }
};

// ⏹ Stop Recording
stopBtn.onclick = () => {
  if (!recorder) {
    statusDiv.textContent = "⚠️ No active recording found.";
    return;
  }

  recorder.stopRecording(() => {
    const blob = recorder.getBlob();
    const audioURL = URL.createObjectURL(blob);

    // 🎧 Add playback in "What I Heard" box
    const audioElement = document.createElement('audio');
    audioElement.src = audioURL;
    audioElement.controls = true;
    audioElement.autoplay = false;

    heardTextDiv.innerHTML = `
      <div class="section-title">🎧 What I Heard</div>
      <p><strong>Your Recording:</strong></p>
    `;
    heardTextDiv.appendChild(audioElement);

    stream.getTracks().forEach(track => track.stop());
    statusDiv.textContent = "⏳ Uploading recorded audio...";

    uploadAudio(blob, statusDiv, outputDiv);
  });

  startBtn.disabled = false;
  stopBtn.disabled = true;
};

// 🚀 Upload & Transcribe
async function uploadAudio(audioBlob, statusBox, resultBox) {
  const formData = new FormData();
  formData.append("audioFile", audioBlob, "recording.wav");

  const inputLang = inputLangSelect.value;
  const outputLang = outputLangSelect.value;

  try {
    const res = await fetch(`${BACKEND_URL}?lang=${inputLang}`, {
      method: "POST",
      body: formData
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(`HTTP ${res.status}: ${text}`);
    }

    const data = await res.json();
    statusBox.textContent = "✅ Transcription successful!";

    heardTextDiv.innerHTML += `
      <p><strong>🗣 You said (${getLangName(inputLang)}):</strong> ${data.transcribed_text || "(nothing recognized)"} </p>
    `;

    // 🌍 Translate
    const translation = await translateText(data.transcribed_text, inputLang, outputLang);
    translatedText = translation; // ✅ store translated text globally

    // 🧾 Display translation result + replay button
    resultBox.innerHTML = `
      <div class="section-title">📝 Result</div>
      <p><strong>🗣 Transcribed (${getLangName(inputLang)}):</strong> ${data.transcribed_text || "(empty)"} </p>
      <p><strong>🌐 Translated (${getLangName(outputLang)}):</strong> ${translation || "(translation failed)"} </p>
      <button id="playTranslatedBtn" style="
        background-color:#5a189a;
        color:white;
        border:none;
        border-radius:8px;
        padding:8px 15px;
        margin-top:10px;
        cursor:pointer;
      ">🔁 Play Translation</button>
    `;

    // 🔊 Speak out translated text automatically
    speakTranslation(outputLang);

    // 🎧 Allow replay as many times as user wants
    document.getElementById("playTranslatedBtn").addEventListener("click", () => speakTranslation(outputLang));

  } catch (err) {
    console.error("Upload error:", err);
    statusBox.textContent = "❌ Transcription failed.";
    resultBox.textContent = err.message;
  }
}

// 🗣 Speak translated text (used for replay button and auto playback)
function speakTranslation(langCode) {
  if (!translatedText) {
    alert("No translated text available yet!");
    return;
  }

  // Stop any ongoing speech before replay
  if (window.speechSynthesis.speaking) {
    window.speechSynthesis.cancel();
  }

  const utterance = new SpeechSynthesisUtterance(translatedText);
  utterance.lang = langCode;
  utterance.rate = 1;
  utterance.pitch = 1;
  utterance.volume = 1;
  window.speechSynthesis.speak(utterance);
}

// 🌍 Translate using MyMemory API
async function translateText(text, sourceLang = "en", targetLang = "hi") {
  try {
    const response = await fetch(
      `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${sourceLang}|${targetLang}`
    );
    const data = await response.json();
    return data?.responseData?.translatedText || "(no translation found)";
  } catch (e) {
    console.error("Translation error:", e);
    return "(translation failed)";
  }
}

// 🧠 Helper: Language Names
function getLangName(code) {
  const map = {
    en: "English", hi: "Hindi", ta: "Tamil",
    bn: "Bengali", pa: "Punjabi",
    fr: "French", es: "Spanish"
  };
  return map[code] || code;
}
