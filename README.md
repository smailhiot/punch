# Punch – heures de travail

Application de punch IN/OUT pour Android (Samsung S22) avec :
- choix du client au punch (client détecté sur place, sinon client par défaut ; à la maison, l'app demande pour quel client) ;
- gros bouton IN / OUT ;
- quand vous quittez la zone de 2 km, l'app demande : **punch OUT**, **changer de client** ou **je travaille encore (déplacement)** ; le punch OUT automatique reste offert en option ;
- gestion des clients (nom, taux, adresse) ;
- historique avec description, matériel, taux modifiable, Facturé / Payé ;
- export CSV pour OpenOffice / LibreOffice Calc, sauvegarde et restauration ;
- configuration guidée des autorisations au premier lancement (position, notifications, batterie) ;
- widget 4x4 pour l'écran d'accueil : bouton IN/OUT, chrono et raccourcis clients.

Les données restent sur le téléphone (aucun serveur).

## Contenu du dossier

| Élément | Rôle |
|---|---|
| `www/` | L'application (une seule page web) |
| `assets/` | Icône et écran de démarrage |
| `keystore/` | Clé de signature fixe : permet d'installer les mises à jour sans désinstaller (donc sans perdre vos données) |
| `native/` | Code Android natif : widget et autorisations (ajouté par `scripts/patch-android.py`) |
| `.github/workflows/build.yml` | Construit l'APK Android et publie la version web, automatiquement sur GitHub |
| `package.json`, `capacitor.config.json` | Configuration de l'enveloppe Android (Capacitor 7) |

## Obtenir l'app Android (sans rien installer sur votre ordinateur)

1. Créez un compte gratuit sur github.com, puis un **nouveau dépôt** (ex. `punch`, privé ou public).
2. Dans le dépôt : **Add file › Upload files**, glissez tout le contenu de ce dossier (sauf `.github`, souvent caché), puis **Commit changes**.
3. Toujours dans le dépôt : **Add file › Create new file**, nommez-le exactement `.github/workflows/build.yml`, collez le contenu du fichier du même nom, puis **Commit changes**.
4. Onglet **Actions** : la construction démarre (environ 5 minutes). Quand elle est verte, l'APK apparaît dans **Releases** (colonne de droite de la page du dépôt).
5. Sur le S22, ouvrez la page **Releases**, touchez `Punch.apk`, puis autorisez « Installer des applications inconnues » pour votre navigateur quand Android le demande.

Pour une mise à jour : modifiez ou remplacez les fichiers sur GitHub ; une nouvelle version apparaît dans Releases et s'installe par-dessus l'ancienne.

## Version web (facultatif, pour essayer)

Dans le dépôt : **Settings › Pages › Source : GitHub Actions**, puis relancez la construction (**Actions › Construire Punch › Run workflow**). L'adresse apparaît dans Settings › Pages. Dans Chrome ou Samsung Internet : menu › **Ajouter à l'écran d'accueil**.

Limite de la version web : le punch OUT automatique et les rappels fonctionnent seulement quand l'app est ouverte. À la réouverture, si vous êtes sorti de la zone, l'app vous demande l'heure de fin.

## Réglages importants sur le Samsung S22 (app Android)

- Au premier lancement, acceptez **Position : Toujours / Pendant l'utilisation** et les **notifications**.
- **Paramètres › Applications › Punch › Batterie › Non restreinte**. Sinon, Samsung peut couper le suivi écran éteint.
- Pendant un quart, une notification « En service : Client » reste affichée : c'est elle qui garde le suivi actif. Ne fermez pas l'app en la glissant hors des applications récentes.

## Export vers Calc

Historique › **Exporter (Calc)**. Fichier CSV séparé par `;` avec virgule décimale. Dans LibreOffice / OpenOffice : ouvrir le fichier, choisir le séparateur « Point-virgule » et la langue « Français (Canada) ».

## Développement (facultatif)

```
npm ci
npx cap add android
npx @capacitor/assets generate --android --assetPath assets
python3 scripts/patch-android.py
npx cap sync android
npx cap open android      # ouvre Android Studio
```
