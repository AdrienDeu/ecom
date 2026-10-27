import { Component, inject, resource } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from '../../api/api';
import { catalogProducts } from '../../api/fn/catalog-controller/catalog-products';
import { BRAND } from '../../core/brand';
import { ProductGrid } from '../catalog/product-grid';
import { ServiceList } from '../catalog/service-list';
import { NetworkDiagram } from './network-diagram';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, NetworkDiagram, ServiceList, ProductGrid],
  template: `
    <section class="hero">
      <div class="hero__text">
        <h1>On conseille.<br />On équipe.</h1>
        <p class="lede">
          Audit de sécurité, migration cloud, DSI à temps partagé : des forfaits de conseil au prix affiché, commandés
          en ligne. Postes, écrans et réseau : le matériel qui va avec, livré et garanti.
        </p>
        <div class="row">
          <a routerLink="/conseil" class="btn large">Voir les forfaits de conseil</a>
          <a routerLink="/materiel" class="btn secondary large">Parcourir le matériel</a>
        </div>
      </div>
      <app-network-diagram class="hero__diagram" />
    </section>

    <section class="block" aria-labelledby="services-title">
      <div class="block__head">
        <h2 id="services-title">Forfaits de conseil</h2>
        <p class="muted">Un périmètre défini, un prix fixe. Vous commandez, nous planifions l'intervention avec vous.</p>
      </div>
      @if (services.value(); as page) {
        <app-service-list [services]="page.content" />
      } @else if (services.error()) {
        <p class="muted">Les forfaits ne peuvent pas être affichés pour le moment. <a routerLink="/conseil">Réessayer</a></p>
      }
      <a routerLink="/conseil" class="more">Tous les forfaits de conseil</a>
    </section>

    <section class="block" aria-labelledby="hardware-title">
      <div class="block__head">
        <h2 id="hardware-title">Matériel informatique</h2>
        <p class="muted">Sélectionné pour un usage professionnel, garanti, livré prêt à l'emploi.</p>
      </div>
      @if (products.value(); as page) {
        <app-product-grid [products]="page.content" />
      } @else if (products.error()) {
        <p class="muted">Le matériel ne peut pas être affiché pour le moment. <a routerLink="/materiel">Réessayer</a></p>
      }
      <a routerLink="/materiel" class="more">Tout le matériel</a>
    </section>

    <section class="block steps" aria-labelledby="steps-title">
      <h2 id="steps-title">Comment se déroule une prestation</h2>
      <ol>
        <li>
          <h3>Vous commandez le forfait</h3>
          <p>En ligne, comme un produit. Le paiement est sécurisé par PayPal.</p>
        </li>
        <li>
          <h3>Nous planifions ensemble</h3>
          <p>Un consultant vous appelle sous {{ brand.responseTime }} pour cadrer le périmètre et fixer les dates.</p>
        </li>
        <li>
          <h3>Nous intervenons</h3>
          <p>Sur site ou à distance, avec un interlocuteur unique du début à la fin.</p>
        </li>
        <li>
          <h3>Vous recevez la restitution</h3>
          <p>Un rapport clair et un plan d'action chiffré, présentés à votre équipe.</p>
        </li>
      </ol>
    </section>

    <section class="quote" aria-labelledby="quote-title">
      <div>
        <h2 id="quote-title">Un besoin qui ne rentre pas dans un forfait ?</h2>
        <p>Décrivez votre projet : nous revenons vers vous sous {{ brand.responseTime }} avec une proposition sur mesure.</p>
      </div>
      <a routerLink="/devis" class="btn large">Demander un devis</a>
    </section>
  `,
  styles: `
    .hero {
      display: grid;
      gap: 2.5rem 3rem;
      align-items: center;
      padding-block: 1rem 3.5rem;
    }
    @media (min-width: 60rem) {
      .hero { grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); padding-block: 2.5rem 5rem; }
    }
    h1 {
      font-size: clamp(2.5rem, 1.5rem + 4vw, 4.25rem);
      font-weight: 820;
      font-stretch: 125%;
      letter-spacing: -0.035em;
      line-height: 0.98;
      margin: 0 0 1.5rem;
    }
    .lede { font-size: 1.1875rem; max-width: 34rem; margin: 0 0 2rem; color: var(--text-muted); }
    .hero__diagram { max-width: 30rem; width: 100%; justify-self: center; }

    .block { padding-block: 3rem; border-top: 1px solid var(--border); }
    .block__head { display: grid; gap: 0.25rem 2rem; margin-bottom: 1.75rem; }
    @media (min-width: 48rem) {
      .block__head { grid-template-columns: auto 1fr; align-items: baseline; }
    }
    .block__head h2, .steps h2 { margin: 0; font-size: 1.625rem; }
    .block__head p { margin: 0; max-width: 40rem; }
    .more { display: inline-block; margin-top: 1.5rem; font-weight: 600; }

    .steps ol {
      list-style: none;
      counter-reset: step;
      margin: 2rem 0 0;
      padding: 0;
      display: grid;
      gap: 2rem;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 14rem), 1fr));
    }
    .steps li { counter-increment: step; padding-top: 1rem; border-top: 2px solid var(--text); }
    .steps li::before {
      content: counter(step);
      display: block;
      margin-bottom: 0.75rem;
      font-size: 2rem;
      font-weight: 800;
      font-stretch: 125%;
      line-height: 1;
      color: var(--accent);
    }
    .steps h3 { margin: 0 0 0.4rem; }
    .steps p { margin: 0; color: var(--text-muted); }

    .quote {
      display: grid;
      gap: 1.5rem 3rem;
      align-items: center;
      margin-top: 3rem;
      padding: 2rem;
      border-radius: var(--radius-lg);
      background: var(--text);
      color: var(--bg);
    }
    @media (min-width: 48rem) {
      .quote { grid-template-columns: 1fr auto; padding: 2.5rem 3rem; }
    }
    .quote h2 { margin: 0 0 0.5rem; font-size: 1.625rem; }
    .quote p { margin: 0; max-width: 40rem; opacity: 0.8; }
    .quote .btn { background: var(--bg); border-color: var(--bg); color: var(--text); }
    .quote .btn:hover { background: var(--accent-soft); border-color: var(--accent-soft); }
  `,
})
export class HomePage {
  private readonly api = inject(Api);
  protected readonly brand = BRAND;

  protected readonly services = resource({
    loader: () => this.api.invoke(catalogProducts, { kind: 'SERVICE', size: 4, sort: ['price.amount,desc'] }),
  });

  protected readonly products = resource({
    loader: () => this.api.invoke(catalogProducts, { kind: 'PRODUCT', size: 4, sort: ['price.amount,desc'] }),
  });
}
