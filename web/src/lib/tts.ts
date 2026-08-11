/** Web Speech API stand-in for the Android TextToSpeechHelper. */

const localeFor: Record<string, string> = {
  en: "en-US",
  es: "es-ES",
  fr: "fr-FR",
  de: "de-DE",
  it: "it-IT",
  ja: "ja-JP",
  pt: "pt-PT",
  pl: "pl-PL",
};

export const ttsSupported = typeof window !== "undefined" && "speechSynthesis" in window;

export function speak(text: string, language: string): void {
  if (!ttsSupported || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = localeFor[language.toLowerCase()] ?? language;
  utterance.rate = 0.9;
  window.speechSynthesis.speak(utterance);
}
