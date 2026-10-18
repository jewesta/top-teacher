import React from 'react';
import './tt-application-error.css';

export function ApplicationError(): React.ReactElement {
  return (
    <main className="tt-application-error" lang="de" aria-labelledby="tt-error-heading">
      <h1 id="tt-error-heading">Das hat leider nicht geklappt</h1>
      <p>Beim Anzeigen dieser Seite ist ein Fehler aufgetreten. Bitte lade die Seite neu.</p>
      <p>Nicht gespeicherte Eingaben können dabei verloren gehen.</p>
      <button type="button" onClick={() => window.location.reload()}>Seite neu laden</button>
    </main>
  );
}
