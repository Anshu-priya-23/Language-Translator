// voice-translator-web/frontend/script.js

const micButton = document.getElementById('micButton');
const statusDiv = document.getElementById('status');
const outputDiv = document.getElementById('output');

// --- Configuration: CHECK PORT AND PATH ---
// Correct URL: http://localhost:[PORT]/api/v1/transcribe
const BACKEND_URL = 'http://localhost:8080/api/v1/transcribe';
// ------------------------------------------

let mediaRecorder;
let audioChunks = [];
let isRecording = false;

// Event binding
micButton.onclick = toggleRecording;

// 1. Start/Stop Recording
async function toggleRecording() {
    if (isRecording) {
        // --- STOP RECORDING ---
        if (mediaRecorder.state !== 'inactive') {
            mediaRecorder.stop();
        }
        isRecording = false;
        micButton.textContent = '🎙️ Speak (Start Recording)';
        statusDiv.textContent = 'Processing audio...';
        micButton.style.backgroundColor = '#28a745';
        micButton.disabled = true; // Disable button while uploading
    } else {
        // --- START RECORDING ---
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            mediaRecorder = new MediaRecorder(stream);
            audioChunks = [];

            mediaRecorder.ondataavailable = event => {
                audioChunks.push(event.data);
            };

            mediaRecorder.onstop = uploadAudio;

            mediaRecorder.start();
            isRecording = true;
            micButton.textContent = '🛑 Stop Recording';
            statusDiv.textContent = 'Recording... Speak clearly now!';
            micButton.style.backgroundColor = '#dc3545'; // Red while recording
            micButton.disabled = false;
        } catch (e) {
            statusDiv.textContent = 'Error accessing microphone. Please allow microphone permissions.';
            console.error(e);
        }
    }
}

// 2. Upload Audio Data to Java Backend
function uploadAudio() {
    // 1. Combine chunks
    // Use 'audio/wav' as the audio format, which is easiest for Vosk to process 
    // after spring converts it to the required PCM format.
    const audioBlob = new Blob(audioChunks, { 'type': 'audio/wav' }); 
    
    // 2. Create FormData payload
    const formData = new FormData();
    // The key 'audioFile' MUST match the @RequestParam("audioFile") in TranslationController.java
    formData.append('audioFile', audioBlob, 'recording.wav');

    statusDiv.textContent = 'Sending audio to backend...';
    outputDiv.textContent = 'Awaiting server response...';
    
    // 3. Send the request
    fetch(BACKEND_URL, {
        method: 'POST',
        body: formData 
    })
    .then(response => {
        micButton.disabled = false; // Enable button after receiving response
        if (!response.ok) {
            // Handle HTTP error statuses (404, 500, etc.)
            return response.text().then(text => {
                throw new Error(`HTTP Error ${response.status}: ${response.statusText}. Response Body: ${text}`);
            });
        }
        return response.json();
    })
    .then(data => {
        // 4. Display the result
        statusDiv.textContent = 'Transcription successful!';
        outputDiv.innerHTML = `
            <p><strong>Transcribed Text:</strong> ${data.transcribed_text}</p>
            <p><strong>Translated Text:</strong> ${data.translated_text}</p>
        `;
    })
    .catch(error => {
        // 5. Display failure details
        statusDiv.textContent = `ERROR: Failed to transcribe. See console for details.`;
        outputDiv.textContent = `Error Details: ${error.message}`;
        console.error('Frontend Error:', error);
        micButton.disabled = false;
    });
}
