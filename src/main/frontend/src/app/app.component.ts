import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <six-root style="height: 100%">
      <six-header slot="header">
        <six-header-item>
          <span>AI Language Learner</span>
        </six-header-item>
      </six-header>
      
        <app-sidebar slot="left-sidebar"></app-sidebar>
        <app-chat slot="main"></app-chat>
    </six-root>
  `,
  styles: [`
  app-chat {
    height: 100%;
    display: block;
  }
  `]
})
export class AppComponent {
  title = 'AI Language Learner';
}
