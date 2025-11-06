const uploadBtn = document.getElementById('uploadBtn');
const audioFileInput = document.getElementById('audioFile');
const uploadStatus = document.getElementById('uploadStatus');
const uploadOutput = document.getElementById('uploadOutput');
const inputLangSelect = document.getElementById('inputLang');
const outputLangSelect = document.getElementById('outputLang');

uploadBtn.addEventListener('click', async () => {
    const file = audioFileInput.files[0];
    if (!file) {
        alert("Please select an audio file first!");
        return;
    }

    uploadBtn.disabled = true;
    uploadStatus.textContent = "⏳ Uploading and transcribing file...";

    const formData = new FormData();
	formData.append("audioFile", file); 

    const sourceLang = inputLangSelect.value;
    const targetLang = outputLangSelect.value;

    try {
        const url = `http://localhost:8080/api/v1/transcribe?sourceLang=${sourceLang}&targetLang=${targetLang}`;
        const res = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!res.ok) {
            const text = await res.text();
            throw new Error(`HTTP ${res.status}: ${text}`);
        }

        const data = await res.json();
        uploadStatus.textContent = "✅ File processed successfully!";

        const transcription = data.transcribed_text || "(nothing recognized)";
        const translation = data.translated_text || "(translation failed)";
        const audioSrc = data.speech_file_path ? `http://localhost:8080${data.speech_file_path}` : null;

        uploadOutput.innerHTML = `
            <p><strong>🗣 Transcribed (${getLangName(sourceLang)}):</strong> ${transcription}</p>
            <p><strong>🌐 Translated (${getLangName(targetLang)}):</strong> ${translation}</p>
            ${audioSrc ? `<audio controls src="${audioSrc}"></audio>` : ''}
        `;

    } catch (err) {
        console.error(err);
        uploadStatus.textContent = "❌ Error processing file.";
        uploadOutput.textContent = err.message;
    } finally {
        uploadBtn.disabled = false;
    }
});

// Helper function for language names
function getLangName(code) {
    const map = {
        en: "English", hi: "Hindi", ta: "Tamil",
        bn: "Bengali", pa: "Punjabi",
        fr: "French", es: "Spanish"
    };
    return map[code] || code;
}
