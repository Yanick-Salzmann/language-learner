import {BehaviorSubject, Observable} from "rxjs";

export interface ChatMessage {
    id: number;
    sessionId: string;
    sender: 'USER' | 'ASSISTANT';
    content: string;
    timestamp: string;
    language?: string;
}

export interface StreamedChatMessage {
    id: number
    sessionId: string
    sender: 'USER' | 'ASSISTANT';
    content: BehaviorSubject<string>;
    timestamp: string;
    language?: string;
}

export interface ChatSession {
    id: string;
    title: string;
    language: string;
    createdAt: string;
    updatedAt: string;
}

export interface CreateSessionRequest {
    language: string;
}

export interface ChatMessageRequest {
    content: string;
    language: string;
}

export interface TTSRequest {
    text: string;
    language: string;
}

export interface ChatMessageChunk {
    id: number,
    isEnd: boolean,
    content: string
}