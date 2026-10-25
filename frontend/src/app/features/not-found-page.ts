import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <h1>Page introuvable</h1>
    <p><a routerLink="/">Retour au catalogue</a></p>
  `,
})
export class NotFoundPage {}
