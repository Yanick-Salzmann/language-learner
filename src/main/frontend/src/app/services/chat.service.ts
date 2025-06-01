import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable, BehaviorSubject} from 'rxjs';
import {
    ChatMessage,
    ChatSession,
    CreateSessionRequest,
    ChatMessageChunk, StreamedChatMessage
} from '../models/chat.models';

@Injectable({
    providedIn: 'root'
})
export class ChatService {
    private readonly apiUrl = '/api';
    private currentSessionSubject = new BehaviorSubject<ChatSession | null>(null);
    private sessionsSubject = new BehaviorSubject<ChatSession[]>([]);
    private messagesSubject = new BehaviorSubject<StreamedChatMessage[]>([]);

    public currentSession$ = this.currentSessionSubject.asObservable();
    public sessions$ = this.sessionsSubject.asObservable();
    public messages$ = this.messagesSubject.asObservable();

    constructor(private http: HttpClient) {
        this.loadSessions();
    }

    createSession(language: string): Observable<ChatSession> {
        const request: CreateSessionRequest = {language};
        return this.http.post<ChatSession>(`${this.apiUrl}/chat/sessions`, request);
    }

    getAllSessions(): Observable<ChatSession[]> {
        return this.http.get<ChatSession[]>(`${this.apiUrl}/chat/sessions`);
    }

    getChatHistory(sessionId: string): Observable<ChatMessage[]> {
        return this.http.get<ChatMessage[]>(`${this.apiUrl}/chat/sessions/${sessionId}/messages`);
    }

    sendMessage(sessionId: string, content: string, language: string): Observable<ChatMessageChunk> {
        const source = new EventSource(`${this.apiUrl}/chat/sessions/${sessionId}/messages/new?message=${encodeURIComponent(content)}&language=${encodeURIComponent(language)}`)
        return new Observable(obs => {
            source.onmessage = (event) => {
                const data = JSON.parse(event.data.toString()) as ChatMessageChunk
                if (data.isEnd) {
                    obs.complete()
                    source.close()
                } else {
                    obs.next(data)
                }
            }

            source.onerror = (error) => {
                obs.error(error)
                source.close()
            }
        })
    }

    setCurrentSession(session: ChatSession | null): void {
        this.currentSessionSubject.next(session);
        if (session) {
            this.loadMessages(session.id);
        } else {
            this.messagesSubject.next([]);
        }
    }

    getCurrentSession(): ChatSession | null {
        return this.currentSessionSubject.value;
    }

    getLastAiMessageId(): number {
        const messagesByIdDesc = this.messagesSubject.value.sort((m1, m2) => m2.id - m1.id);
        const aiMessagesByIdDesc = messagesByIdDesc.filter(m => m.sender === "ASSISTANT");
        return aiMessagesByIdDesc.length > 0 ? aiMessagesByIdDesc[0].id : (messagesByIdDesc.length > 0 ? (messagesByIdDesc[0].id + 1) : 0);
    }

    addMessagePart(message: ChatMessage) {
        const id = message.id;
        const sessionId = message.sessionId;
        const part = message.content;

        let currentMessages = this.messagesSubject.value;
        const existing = currentMessages.find(m => m.id === id && m.sessionId === sessionId)
        if (existing) {
            existing.content.next(existing.content.value + part);
        } else {
            currentMessages = [...currentMessages, {
                ...message,
                content: new BehaviorSubject(message.content)
            }]
            this.messagesSubject.next(currentMessages);
        }
    }

    addMessage(message: ChatMessage): void {
        const currentMessages = this.messagesSubject.value;
        this.messagesSubject.next([...currentMessages, {...message, content: new BehaviorSubject(message.content)}]);
    }

    private loadSessions(): void {
        this.getAllSessions().subscribe({
            next: (sessions) => {
                this.sessionsSubject.next(sessions);
                if (sessions.length > 0 && !this.getCurrentSession()) {
                    this.setCurrentSession(sessions[0]);
                }
            },
            error: (error) => console.error('Error loading sessions:', error)
        });
    }

    private loadMessages(sessionId: string): void {
        this.getChatHistory(sessionId).subscribe({
            next: (messages) => this.messagesSubject.next(messages.map<StreamedChatMessage>(m => {
                return {...m, content: new BehaviorSubject(m.content)}
            })),
            error: (error) => console.error('Error loading messages:', error)
        });
    }

    refreshSessions(): void {
        this.loadSessions();
    }
}
