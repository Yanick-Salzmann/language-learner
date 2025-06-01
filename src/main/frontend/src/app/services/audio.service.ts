import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TTSRequest } from '../models/chat.models';

@Injectable({
  providedIn: 'root'
})
export class AudioService {
  private readonly apiUrl = '/api';

  constructor(private http: HttpClient) {}

  generateSpeech(text: string, language: string = 'en'): Observable<Blob> {
    const request: TTSRequest = { text, language };
    return this.http.post(`${this.apiUrl}/tts`, request, {
      responseType: 'blob'
    });
  }

  playAudio(audioBlob: Blob): void {
    const audioUrl = URL.createObjectURL(audioBlob);
    const audio = new Audio(audioUrl);
    
    audio.onended = () => {
      URL.revokeObjectURL(audioUrl);
    };
    
    audio.play().catch(error => {
      console.error('Error playing audio:', error);
      URL.revokeObjectURL(audioUrl);
    });
  }

  generateAndPlaySpeech(text: string, language: string = 'en'): void {
    this.generateSpeech(text, language).subscribe({
      next: (audioBlob) => {
        this.playAudio(audioBlob);
      },
      error: (error) => {
        console.error('Error generating speech:', error);
      }
    });
  }
}
