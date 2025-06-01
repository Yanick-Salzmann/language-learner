import {Component, OnInit} from '@angular/core';
import {ChatService} from '../../services/chat.service';
import {ChatMessage, ChatMessageChunk, ChatSession, StreamedChatMessage} from '../../models/chat.models';
import {Observable} from 'rxjs';

@Component({
    selector: 'app-chat',
    template: `
        <div class="chat-main">
            <div class="chat-messages" #messagesContainer>
                <div *ngIf="!(currentSession$ | async)" class="welcome-message">
                    <six-card>
                        <h2>Welcome to AI Language Learner!</h2>
                        <p>Select a language and start a new conversation to begin learning.</p>
                    </six-card>
                </div>

                <div *ngIf="currentSession$ | async">
                    <app-message
                            *ngFor="let message of messages$ | async; trackBy: trackByMessageId"
                            [message]="message">
                    </app-message>

                    <div *ngIf="isLoading" class="loading-message">
                        <div class="message ai">
                            <div class="message-avatar ai">
                                <six-icon>smart_toy</six-icon>
                            </div>
                            <div class="message-content">
                                <six-spinner></six-spinner>
                                <span>AI is thinking...</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <app-chat-input
                    *ngIf="currentSession$ | async"
                    (messageSent)="onMessageSent($event)">
            </app-chat-input>
        </div>
    `,
    styles: [`
      .welcome-message {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        text-align: center;
      }

      .welcome-message six-card {
        max-width: 400px;
        padding: 40px;
      }

      .welcome-message h2 {
        color: var(--primary-color);
        margin-bottom: 16px;
      }

      .loading-message {
        margin-bottom: 16px;
      }

      .loading-message .message-content {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    `]
})
export class ChatComponent implements OnInit {
    messages$: Observable<StreamedChatMessage[]>;
    currentSession$: Observable<ChatSession | null>;
    isLoading = false;

    constructor(private chatService: ChatService) {
        this.messages$ = this.chatService.messages$;
        this.currentSession$ = this.chatService.currentSession$;
    }

    ngOnInit(): void {
    }

    onMessageSent(content: string): void {
        const currentSession = this.chatService.getCurrentSession();
        if (!currentSession) return;

        this.isLoading = true;

        // Add user message to chat immediately
        const userMessage: ChatMessage = {
            id: Date.now(), // Temporary ID
            sessionId: currentSession.id,
            sender: 'USER',
            content: content,
            timestamp: new Date().toISOString(),
            language: currentSession.language
        };
        this.chatService.addMessage(userMessage)

        // Send message to backend
        this.chatService.sendMessage(currentSession.id, content, currentSession.language).subscribe({
            next: (aiMessage: ChatMessageChunk) => {
                this.chatService.addMessagePart({
                        content: aiMessage.content,
                        id: aiMessage.id,
                        language: currentSession.language,
                        sender: "ASSISTANT",
                        sessionId: currentSession.id,
                        timestamp: new Date().toISOString()
                    }
                )

                this.isLoading = false
                this.scrollToBottom()
            },
            error: (error) => {
                console.error('Error sending message:', error);
                this.isLoading = false
            }
        });

        this.scrollToBottom()
    }

    trackByMessageId(index: number, message: StreamedChatMessage): number {
        return message.id
    }

    private scrollToBottom(): void {
        setTimeout(() => {
                const container = document.querySelector('.chat-messages')
                if (container) {
                    container.scrollTop = container.scrollHeight
                }
            }, 100
        )
    }
}
