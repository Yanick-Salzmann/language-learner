import { Component, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-chat-input',
  template: `
    <div class="chat-input-area">
      <div class="chat-input-form">
        <div class="chat-input">
          <six-textarea
            placeholder="Type your message here..."
            [(ngModel)]="messageContent"
            (keydown)="onKeyDown($event)"
            rows="3"
            resize="none">
          </six-textarea>
        </div>
        
        <six-button 
          type="primary"
          class="send-button"
          [disabled]="!messageContent.trim()"
          (click)="sendMessage()">
          <six-icon name="send"></six-icon>
          Send
        </six-button>
      </div>
    </div>
  `,
  styles: [`
    .chat-input-form {
      display: flex;
      gap: 12px;
      align-items: flex-end;
    }
    
    .chat-input {
      flex: 1;
    }
    
    .send-button {
      flex-shrink: 0;
      height: fit-content;
    }
  `]
})
export class ChatInputComponent {
  @Output() messageSent = new EventEmitter<string>();
  
  messageContent = '';

  sendMessage(): void {
    if (this.messageContent.trim()) {
      this.messageSent.emit(this.messageContent.trim());
      this.messageContent = '';
    }
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }
}
