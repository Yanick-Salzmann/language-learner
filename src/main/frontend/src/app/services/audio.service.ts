import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, Subject, BehaviorSubject} from 'rxjs';
import {TTSRequest} from '../models/chat.models';

export interface AudioState {
    messageId: number;
    state: 'idle' | 'loading' | 'playing';
}

@Injectable({
    providedIn: 'root'
})
export class AudioService {
    private readonly apiUrl = '/api';
    private audioStateSubject = new BehaviorSubject<AudioState>({ messageId: -1, state: 'idle' });
    public audioState$ = this.audioStateSubject.asObservable();

    constructor(private http: HttpClient) {
    }

    generateSpeech(text: string, language: string = 'en'): Observable<Blob> {
        const request: TTSRequest = {text, language};
        return this.http.post(`${this.apiUrl}/tts`, request, {
            responseType: 'blob'
        });
    }

    generateAndPlaySpeech(msgId: number, sessionId: string, language: string = 'en'): void {
        // Set loading state
        this.audioStateSubject.next({ messageId: msgId, state: 'loading' });
        
        const url = `${this.apiUrl}/chat/sessions/${sessionId}/messages/${msgId}/tts?language=${encodeURIComponent(language)}`;
        const audio = new Audio(url);
        audio.preload = 'auto';
        
        // Set playing state when audio starts
        audio.onloadstart = () => {
            this.audioStateSubject.next({ messageId: msgId, state: 'playing' });
        };
        
        // Reset to idle state when audio ends or fails
        audio.onended = () => {
            this.audioStateSubject.next({ messageId: msgId, state: 'idle' });
        };
        
        audio.onerror = () => {
            console.error('Error playing audio');
            this.audioStateSubject.next({ messageId: msgId, state: 'idle' });
        };
        
        audio.play().catch(error => {
            console.error('Error playing audio:', error);
            this.audioStateSubject.next({ messageId: msgId, state: 'idle' });
        });
    }

    getAudioState(messageId: number): Observable<'idle' | 'loading' | 'playing'> {
        return new Observable(observer => {
            const subscription = this.audioState$.subscribe(state => {
                if (state.messageId === messageId) {
                    observer.next(state.state);
                } else {
                    observer.next('idle');
                }
            });
            
            return () => subscription.unsubscribe();
        });
    }
}
