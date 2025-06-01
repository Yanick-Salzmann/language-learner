package ch.yanick.ai.ailanguagelearner.service

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class TextToSpeechService {
    
    private val pythonScriptPath = "scripts/tts_xtts.py"
    private val outputDir = "temp/audio"
    
    init {
        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(outputDir))
        createPythonScript()
    }
    
    fun generateSpeech(text: String, language: String = "en"): Flux<DataBuffer> {
        return Flux.create { sink ->
            try {
                val outputFile = "$outputDir/${System.currentTimeMillis()}.wav"
                
                val processBuilder = ProcessBuilder(
                    "python", pythonScriptPath, 
                    "--text", text,
                    "--language", language,
                    "--output", outputFile
                )
                
                val process = processBuilder.start()
                val exitCode = process.waitFor()
                
                if (exitCode == 0 && Files.exists(Paths.get(outputFile))) {
                    // Stream the audio file
                    val audioFile = File(outputFile)
                    val inputStream = FileInputStream(audioFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val dataBuffer = DefaultDataBufferFactory().allocateBuffer(bytesRead)
                        dataBuffer.write(buffer, 0, bytesRead)
                        sink.next(dataBuffer)
                    }
                    
                    inputStream.close()
                    // Clean up temporary file
                    Files.deleteIfExists(Paths.get(outputFile))
                    sink.complete()
                } else {
                    sink.error(RuntimeException("Failed to generate speech"))
                }
            } catch (e: Exception) {
                sink.error(e)
            }
        }
    }
    
    private fun createPythonScript() {
        val scriptDir = File("scripts")
        if (!scriptDir.exists()) {
            scriptDir.mkdirs()
        }
        
        val scriptFile = File(pythonScriptPath)
        if (!scriptFile.exists()) {
            scriptFile.writeText("""
import argparse
import torch
from TTS.api import TTS
import soundfile as sf
import numpy as np

def main():
    parser = argparse.ArgumentParser(description='Generate speech using XTTS-v2')
    parser.add_argument('--text', required=True, help='Text to convert to speech')
    parser.add_argument('--language', default='en', help='Language code')
    parser.add_argument('--output', required=True, help='Output WAV file path')
    
    args = parser.parse_args()
    
    # Initialize XTTS-v2
    device = "cuda" if torch.cuda.is_available() else "cpu"
    
    # Use XTTS-v2 model
    tts = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2", progress_bar=False).to(device)
    
    # Generate speech
    wav = tts.tts(text=args.text, 
                  speaker_wav="voice_samples/default_speaker.wav",  # You might want to add a default speaker
                  language=args.language)
    
    # Save to file
    sf.write(args.output, np.array(wav), 22050)
    print(f"Audio saved to {args.output}")

if __name__ == "__main__":
    main()
            """.trimIndent())
        }
        
        // Create requirements.txt for Python dependencies
        val requirementsFile = File("scripts/requirements.txt")
        if (!requirementsFile.exists()) {
            requirementsFile.writeText("""
TTS>=0.22.0
torch>=2.0.0
soundfile>=0.12.1
numpy>=1.21.0
            """.trimIndent())
        }
    }
}
