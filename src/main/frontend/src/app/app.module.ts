import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import {HttpClientModule, provideHttpClient} from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { UiLibraryAngularModule } from '@six-group/ui-library-angular';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ChatComponent } from './components/chat/chat.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { MessageComponent } from './components/message/message.component';
import { ChatInputComponent } from './components/chat-input/chat-input.component';

import { ChatService } from './services/chat.service';
import { AudioService } from './services/audio.service';
import {MarkdownComponent, MarkdownModule} from "ngx-markdown";

@NgModule({
  declarations: [
    AppComponent,
    ChatComponent,
    SidebarComponent,
    MessageComponent,
    ChatInputComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule,
    ReactiveFormsModule,
    UiLibraryAngularModule.forRoot(),
    MarkdownModule.forRoot()
  ],
  providers: [
    ChatService,
    AudioService,
    provideHttpClient()
  ],
  bootstrap: [AppComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class AppModule { }
