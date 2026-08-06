/**
 * "in 9 Minuten" für Lernschritte, "am 2. August 2026" für den Tagesrhythmus — ein
 * Datum allein wäre bei einer Karte, die gleich wiederkommt, keine Auskunft.
 */
export function formatDue(iso: string): string {
  const due = new Date(iso);
  const minutes = Math.round((due.getTime() - Date.now()) / 60_000);

  if (minutes <= 1) {
    return 'gleich';
  }
  if (minutes < 60) {
    return `in ${minutes} Minuten`;
  }
  if (due.toDateString() === new Date().toDateString()) {
    return `heute um ${new Intl.DateTimeFormat('de-DE', { timeStyle: 'short' }).format(due)}`;
  }
  return `am ${new Intl.DateTimeFormat('de-DE', { dateStyle: 'long' }).format(due)}`;
}
