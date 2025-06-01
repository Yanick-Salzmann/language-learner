import { Component, OnInit } from '@angular/core';
import { ChatService } from '../../services/chat.service';
import { ChatSession } from '../../models/chat.models';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-sidebar',
  template: `
    <six-sidebar position="left" open>
      <six-sidebar-item>
        <six-button
            type="primary"
            class="new-chat-button"
            (click)="createNewChat()">
          <six-icon>add</six-icon>
          New Chat
        </six-button>
      </six-sidebar-item>
      <six-sidebar-item>
        <six-select
            placeholder="Select Language"
            [(ngModel)]="selectedLanguage">
          <six-menu-item value="en">English</six-menu-item>
          <six-menu-item value="es">Spanish</six-menu-item>
          <six-menu-item value="fr">French</six-menu-item>
          <six-menu-item value="de">German</six-menu-item>
          <six-menu-item value="it">Italian</six-menu-item>
          <six-menu-item value="pt">Portuguese</six-menu-item>
        </six-select>
      </six-sidebar-item>

      <six-sidebar-item>
        <h3>Chat History</h3>
        <div
            *ngFor="let session of sessions$ | async"
            class="chat-session-item"
            [class.active]="session.id === (currentSession$ | async)?.id"
            (click)="selectSession(session)">
          <div class="session-title">{{ session.title }}</div>
          <div class="session-date">{{ formatDate(session.updatedAt) }}</div>
        </div>

        <div *ngIf="(sessions$ | async)?.length === 0" class="no-sessions">
          No chat history yet. Start a new conversation!
        </div>
      </six-sidebar-item>
    </six-sidebar>
  `,
  styles: [`
    .no-sessions {
      padding: 20px;
      text-align: center;
      color: #6c757d;
      font-style: italic;
    }
    
    h3 {
      margin-bottom: 16px;
      color: var(--text-color);
      font-size: 1.1rem;
    }
  `]
})
export class SidebarComponent implements OnInit {
  sessions$: Observable<ChatSession[]>;
  currentSession$: Observable<ChatSession | null>;
  selectedLanguage = 'en';

  constructor(private chatService: ChatService) {
    this.sessions$ = this.chatService.sessions$;
    this.currentSession$ = this.chatService.currentSession$;
  }

  ngOnInit(): void {}

  createNewChat(): void {
    if (this.selectedLanguage) {
      this.chatService.createSession(this.selectedLanguage).subscribe({
        next: (session) => {
          this.chatService.setCurrentSession(session);
          this.chatService.refreshSessions();
        },
        error: (error) => console.error('Error creating new chat:', error)
      });
    }
  }

  selectSession(session: ChatSession): void {
    this.chatService.setCurrentSession(session);
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
  }
}
