import {AfterViewInit, Component, ElementRef, Host, ViewChild, ViewRef} from '@angular/core';

@Component({
    selector: 'app-root',
    template: `
        <six-root #rootElem style="height: 100%" padded="false">
            <six-header slot="header">
                <six-header-item>
                    <span>AI Language Learner</span>
                </six-header-item>
            </six-header>

            <app-sidebar slot="left-sidebar"></app-sidebar>
            <app-chat #mainChat slot="main"></app-chat>
        </six-root>
    `,
    styles: [`
      app-chat {
        height: 100%;
        display: flex;
        flex-direction: column;
      }
    `]
})
export class AppComponent implements AfterViewInit {
    title = 'AI Language Learner';

    rootElem: ElementRef<HTMLElement>;

    constructor(@Host() mainChat: ElementRef) {
        this.rootElem = mainChat
    }

    ngAfterViewInit(): void {
        // six-root__container
        this.waitForInit()
    }

    private waitForInit() {
        // this is a hack because six-root has no possibility to set the main content to 100% height and it can not be styled
        // because the main slot is part of the shadow DOM

        if (!this.rootElem?.nativeElement) {
            setTimeout(() => this.waitForInit(), 100)
            return
        }

        const shadow = this.rootElem.nativeElement.querySelector("six-root")?.shadowRoot
        if (!shadow) {
            setTimeout(() => this.waitForInit(), 100)
            return
        }

        if(!shadow.firstChild) {
            setTimeout(() => this.waitForInit(), 100)
            return
        }

        const host = shadow.firstChild;
        const rootContainer = host.childNodes.item(2).childNodes.item(0) as HTMLElement
        console.log(rootContainer);
        rootContainer.style.height = "100%";
    }
}
