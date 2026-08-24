# Multi TG — App Android TV multi-schermo

App per Smart TV Android che mostra **2 o 4 telegiornali (stream m3u8) in contemporanea**, con selezione audio, fullscreen e timer di spegnimento.

## Installazione sulla TV

`MultiTG.apk` è il file da installare. Due modi:

**Con chiavetta USB / app "Send Files to TV":** copia `MultiTG.apk` sulla TV, aprilo con un file manager e installa (abilita "Origini sconosciute" nelle impostazioni della TV se richiesto).

**Con ADB dal Mac** (TV e Mac sulla stessa rete, "Debug USB/rete" attivo sulla TV):
```
adb connect IP_DELLA_TV
adb install MultiTG.apk
```

## Uso con il telecomando

| Tasto | Azione |
|---|---|
| Frecce | Ti muovi tra i riquadri (bordo giallo = selezionato) |
| **OK** su un riquadro | Attiva l'**audio** di quel canale (icona 🔊) |
| **OK** sul riquadro che ha già l'audio | Apre quel canale a **schermo intero** |
| **INDIETRO** | Dal fullscreen torna alla griglia; dalla griglia chiede se uscire |
| **OK tenuto premuto** (o tasto MENU) | Apre il **menu** |

## Menu (OK lungo)

- **Layout 2 / 4 finestre** — passa da 2 a 4 riquadri e viceversa
- **Timer di spegnimento** — 15 / 30 / 60 / 90 / 120 minuti (conto alla rovescia in basso a destra; allo scadere l'app si chiude)
- **Modifica canali** — nome e URL m3u8 dei 4 canali (salvati in modo permanente)
- **Riavvia tutti gli stream**
- **Esci dall'app**

## Canali

Alla prima apertura i 4 riquadri usano uno stream demo di test. Inserisci i tuoi URL m3u8 da **menu → Modifica canali**. Digitare URL lunghi col telecomando è scomodo: conviene farlo una volta sola (restano salvati), oppure con una tastiera USB attaccata alla TV, oppure via ADB.

## Funzionamento continuo

- Se uno stream si interrompe o dà errore, l'app **riprova automaticamente ogni 4 secondi** finché non torna.
- Lo schermo non va mai in standby mentre l'app è aperta.

## Ricompilare l'APK (sorgenti in `MultiTG/`)

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
cd MultiTG
gradle assembleDebug     # APK in app/build/outputs/apk/debug/
```
