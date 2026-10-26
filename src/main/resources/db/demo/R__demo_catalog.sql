-- Catalogue de démonstration, chargé uniquement par le profil local (spring.flyway.locations).
-- Idempotent : ne touche pas aux produits déjà présents (même SKU), les modifications faites dans le back-office
-- sont conservées.

insert into category (id, slug, name)
values (gen_random_uuid(), 'audit-securite', 'Audit & sécurité'),
       (gen_random_uuid(), 'cloud-infrastructure', 'Cloud & infrastructure'),
       (gen_random_uuid(), 'accompagnement', 'Accompagnement & formation'),
       (gen_random_uuid(), 'ordinateurs', 'Ordinateurs'),
       (gen_random_uuid(), 'ecrans', 'Écrans'),
       (gen_random_uuid(), 'reseau', 'Réseau'),
       (gen_random_uuid(), 'accessoires', 'Accessoires')
on conflict (slug) do nothing;

insert into product (id, sku, slug, name, kind, description, price_amount, price_currency, category_id, active,
                     created_at, updated_at, version)
select gen_random_uuid(), d.sku, d.slug, d.name, d.kind, d.description, d.price, 'EUR',
       (select id from category c where c.slug = d.category), true, now(), now(), 0
from (values
    ('SRV-AUDIT-SEC', 'audit-securite-flash', 'Audit sécurité flash', 'SERVICE', 'audit-securite', 1800.00,
     E'Deux jours pour mesurer l''exposition de votre système d''information et prioriser les correctifs.\n\n'
     || E'• Revue des comptes, des droits et de la politique de mots de passe\n'
     || E'• Scan des services exposés sur Internet\n'
     || E'• Contrôle des sauvegardes et du plan de reprise\n'
     || E'• Restitution orale et rapport avec un plan d''action chiffré\n\n'
     || 'Pour les structures jusqu''à 50 postes. Au-delà, demandez un devis.'),
    ('SRV-AUDIT-RGPD', 'diagnostic-rgpd', 'Diagnostic de conformité RGPD', 'SERVICE', 'audit-securite', 1200.00,
     E'Une journée et demie pour cartographier vos traitements de données personnelles et identifier les écarts.\n\n'
     || E'• Registre des traitements pré-rempli\n'
     || E'• Analyse des sous-traitants et des transferts hors UE\n'
     || '• Liste priorisée des actions de mise en conformité'),
    ('SRV-CLOUD-MIG', 'etude-migration-cloud', 'Étude de migration cloud', 'SERVICE', 'cloud-infrastructure', 3500.00,
     E'Cinq jours pour décider quoi migrer, où et à quel coût.\n\n'
     || E'• Inventaire applicatif et dépendances\n'
     || E'• Choix de l''hébergement (AWS, Azure, OVHcloud) et estimation des coûts mensuels\n'
     || '• Feuille de route de migration par vagues'),
    ('SRV-CLOUD-FINOPS', 'revue-couts-cloud', 'Revue des coûts cloud (FinOps)', 'SERVICE', 'cloud-infrastructure', 1500.00,
     E'Deux jours pour réduire la facture cloud sans dégrader le service.\n\n'
     || E'• Analyse de la facturation des 3 derniers mois\n'
     || E'• Ressources inutilisées, surdimensionnées ou mal réservées\n'
     || '• Plan d''économies chiffré, gains rapides appliqués avec vous'),
    ('SRV-ACC-DSI', 'dsi-temps-partage', 'DSI à temps partagé — 1 jour', 'SERVICE', 'accompagnement', 850.00,
     E'Une journée d''un DSI expérimenté pour piloter vos sujets informatiques : budget, fournisseurs, sécurité, '
     || E'projets.\n\nCommandez autant de journées que nécessaire ; elles sont planifiées ensemble après la commande.'),
    ('SRV-ACC-FORM', 'formation-cybersecurite', 'Atelier de sensibilisation cybersécurité', 'SERVICE', 'accompagnement', 900.00,
     E'Une demi-journée pour vos équipes (jusqu''à 15 personnes), sur site ou à distance.\n\n'
     || E'• Hameçonnage, mots de passe, poste de travail, déplacements\n'
     || '• Exercices pratiques et quiz, supports remis aux participants'),
    ('HW-LAPTOP-14', 'portable-pro-14', 'Portable Pro 14" — Core Ultra 7, 32 Go, 1 To', 'PRODUCT', 'ordinateurs', 1649.00,
     E'Portable professionnel léger (1,3 kg), écran 14" 2,8K, 32 Go de RAM, SSD 1 To.\n'
     || 'Windows 11 Pro, garantie 3 ans sur site.'),
    ('HW-LAPTOP-16', 'portable-pro-16', 'Portable Pro 16" — Ryzen 9, 64 Go, 2 To', 'PRODUCT', 'ordinateurs', 2399.00,
     E'Station mobile pour le développement et la donnée : 64 Go de RAM, SSD 2 To, écran 16" 120 Hz.\n'
     || 'Garantie 3 ans sur site.'),
    ('HW-MINI-PC', 'mini-pc-bureau', 'Mini PC de bureau — Core i5, 16 Go, 512 Go', 'PRODUCT', 'ordinateurs', 649.00,
     'Poste de travail compact et silencieux, fixation VESA derrière l''écran. Windows 11 Pro.'),
    ('HW-SCREEN-27', 'ecran-27-qhd', 'Écran 27" QHD USB-C', 'PRODUCT', 'ecrans', 379.00,
     'Dalle IPS 2560 × 1440, USB-C 90 W (un seul câble pour l''image et la charge), pied réglable en hauteur.'),
    ('HW-SCREEN-34', 'ecran-34-ultrawide', 'Écran 34" ultra-large incurvé', 'PRODUCT', 'ecrans', 649.00,
     'Dalle 3440 × 1440, hub USB-C intégré, KVM pour deux ordinateurs.'),
    ('HW-NET-AP', 'borne-wifi-7', 'Borne Wi-Fi 7 professionnelle', 'PRODUCT', 'reseau', 289.00,
     'Point d''accès Wi-Fi 7 tri-bande, alimentation PoE+, gestion centralisée, jusqu''à 200 appareils.'),
    ('HW-NET-SW', 'switch-24-poe', 'Switch 24 ports PoE+ administrable', 'PRODUCT', 'reseau', 459.00,
     '24 ports Gigabit PoE+ (370 W), 4 ports SFP+ 10G, VLAN et supervision.'),
    ('HW-NET-FW', 'pare-feu-tpe', 'Pare-feu nouvelle génération TPE/PME', 'PRODUCT', 'reseau', 799.00,
     'Filtrage applicatif, VPN pour le télétravail, jusqu''à 50 utilisateurs. Licence de sécurité 1 an incluse.'),
    ('HW-ACC-DOCK', 'station-accueil-usb-c', 'Station d''accueil USB-C', 'PRODUCT', 'accessoires', 219.00,
     'Deux écrans 4K, Ethernet 2,5G, charge 100 W, 6 ports USB.'),
    ('HW-ACC-KIT', 'kit-clavier-souris', 'Kit clavier et souris sans fil', 'PRODUCT', 'accessoires', 89.00,
     'Clavier AZERTY silencieux et souris ergonomique, connexion Bluetooth ou récepteur USB.')
) as d (sku, slug, name, kind, category, price, description)
on conflict (sku) do nothing;

insert into stock (product_id, on_hand, reserved, tracked, version)
select p.id, case when p.kind = 'SERVICE' then 0 else d.on_hand end, 0, p.kind <> 'SERVICE', 0
from product p
join (values ('HW-LAPTOP-14', 25), ('HW-LAPTOP-16', 8), ('HW-MINI-PC', 40), ('HW-SCREEN-27', 60),
             ('HW-SCREEN-34', 12), ('HW-NET-AP', 30), ('HW-NET-SW', 10), ('HW-NET-FW', 0),
             ('HW-ACC-DOCK', 50), ('HW-ACC-KIT', 120), ('SRV-AUDIT-SEC', 0), ('SRV-AUDIT-RGPD', 0),
             ('SRV-CLOUD-MIG', 0), ('SRV-CLOUD-FINOPS', 0), ('SRV-ACC-DSI', 0), ('SRV-ACC-FORM', 0)
     ) as d (sku, on_hand) on d.sku = p.sku
on conflict (product_id) do nothing;
