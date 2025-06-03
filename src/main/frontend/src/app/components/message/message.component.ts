import {Component, Input, OnInit} from '@angular/core';
import {ChatMessage, StreamedChatMessage} from '../../models/chat.models';
import {AudioService} from '../../services/audio.service';
import {marked} from 'marked';

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
                <div class="message-actions" *ngIf="message.sender === 'ASSISTANT'">
                    <button
                            class="speak-button"
                            (click)="speakMessage()"
                            title="Read aloud">
                        <six-icon>volume_up</six-icon>
                    </button>
                </div>
                <div class="message-time">{{ formatTime(message.timestamp) }}</div>
            </div>
        </div>
    `, styles: [`
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
    `]
})
export class MessageComponent implements OnInit {
    @Input() message!: StreamedChatMessage;

    constructor(private audioService: AudioService) {
    }

    ngOnInit(): void {

    }

    speakMessage(): void {
        if (this.message.content) {
            const language = this.message.language || 'en';
            this.audioService.generateAndPlaySpeech(this.message.id, this.message.sessionId, language);
        }
    }

    formatTime(timestamp: string): string {
        const date = new Date(timestamp);
        return date.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
    }
}
