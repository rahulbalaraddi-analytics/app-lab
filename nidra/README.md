# Nidra

Bedtime app: guided breathing, clench-and-release relaxation and calm Indian mythology and history stories, in a calm Indian female voice. Plays with the screen off and stops by itself after the sleep timer (fades out over 30 seconds).

## Sessions (v0.1)

| Group | Sessions |
|---|---|
| Breathing | 4-7-8 breathing · Long out-breath (in 4, out 6) |
| Body | Progressive muscle relaxation, feet to head · Gentle body scan |
| Stories | Shabari's Berries (Ramayana) · Krishna Lifts Govardhan (Bhagavata Purana) · Shivaji and the Farmers' Grass |

"Begin wind-down" plays 4-7-8 → muscle relaxation → a random story.

## Audio

Voice audio is generated offline with the open-source Kokoro TTS model (Apache-2.0), voice `hf_alpha`, 96 kbps mono MP3, bundled in `app/src/main/res/raw/`. No background sounds.

## Build

Open this folder in Android Studio, or push to `main`: the `Nidra build` workflow publishes the APK at the `nidra-latest` release.
