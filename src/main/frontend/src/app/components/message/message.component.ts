import {Component, Input, OnInit, OnDestroy} from '@angular/core';
import {ChatMessage, StreamedChatMessage} from '../../models/chat.models';
import {AudioService} from '../../services/audio.service';
import {marked} from 'marked';
import {Observable, Subscription} from 'rxjs';

@Component({
    selector: 'app-message',
    template: `
        <div class="message chat-message-container" [class.user]="message.sender === 'USER'"
             [class.ai]="message.sender === 'ASSISTANT'">
            <div class="message-avatar" [class.ai]="message.sender === 'ASSISTANT'">
                <six-icon *ngIf="message.sender === 'ASSISTANT'">smart_toy</six-icon>
                <six-icon *ngIf="message.sender === 'USER'">person</six-icon>
            </div>
            <div class="message-content">
                <div class="message-text"
                     *ngIf="message.sender === 'USER'">{{ message.content.value }}
                </div>
                <markdown class="message-text markdown-content"
                          *ngIf="message.sender === 'ASSISTANT'" emoji
                          [data]="message.content.asObservable() | async"></markdown>
                <div class="message-actions" [class.playing]="audioState === 'playing'"
                     *ngIf="message.sender === 'ASSISTANT'">
                    <button
                            class="speak-button"
                            [class.playing]="audioState === 'playing'"
                            [class.loading]="audioState === 'loading'"
                            (click)="speakMessage()"
                            [disabled]="audioState === 'loading' || audioState === 'other-playing'"
                            title="Read aloud">
                        <six-icon *ngIf="audioState === 'idle' || audioState === 'other-playing'">volume_up</six-icon>
                        <six-icon *ngIf="audioState === 'loading'" class="loading-icon">hourglass_empty</six-icon>
                        <six-icon *ngIf="audioState === 'playing'" class="playing-icon">pause</six-icon>
                    </button>
                </div>
                <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
        </div>    `, styles: [`
    .message-time {
      font-size: 0.75rem;
      opacity: 0.6;
      margin-top: 4px;
    }

    .markdown-content li {
      margin-left: 1em;
    }

    .message.user .message-time {
      text-align: right;
    }

    .speak-button {
      border: none;
      background: transparent;
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background-color 0.2s ease;
    }

    .speak-button:hover:not(:disabled) {
      background-color: rgba(0, 0, 0, 0.1);
    }

    .speak-button:disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }

    .speak-button.playing:hover {
      background-color: rgba(0, 122, 204, 0.2);
    }

    .loading-icon {
      animation: pulse 1.5s ease-in-out infinite;
      color: #666;
    }

    .playing-icon {
      color: #007acc !important;
      animation: pulse 2s ease-in-out infinite;
    }

    @keyframes pulse {
      0%, 100% {
        opacity: 1;
      }
      50% {
        opacity: 0.5;
      }
    }
    `]
})
export class MessageComponent implements OnInit, OnDestroy {
    @Input() message!: StreamedChatMessage;
    audioState: 'idle' | 'loading' | 'playing' | 'other-playing' = 'idle';
    private audioStateSubscription?: Subscription;

    constructor(private audioService: AudioService) {
    }

    ngOnInit(): void {
        // Subscribe to audio state changes for this specific message
        this.audioStateSubscription = this.audioService.getAudioState(this.message.id).subscribe(
            state => this.audioState = state
        );
    }

    ngOnDestroy(): void {
        if (this.audioStateSubscription) {
            this.audioStateSubscription.unsubscribe();
        }
    }

    speakMessage(): void {
        if (this.message.content && this.audioState === 'idle') {
            const language = this.message.language || 'en';
            this.audioService.generateAndPlaySpeech(this.message.id, this.message.sessionId, language);
        }
    }

    formatTime(timestamp: string): string {
        const date = new Date(timestamp);
        return date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
    }
}
