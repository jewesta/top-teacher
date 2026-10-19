import assert from 'node:assert/strict';
import test from 'node:test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { loadFrontendModule } from './load-frontend-module.mjs';

const { ApplicationError } = loadFrontendModule('tt-application-error.tsx');

test('the error page uses German copy and reloads only when the user chooses recovery', (t) => {
  let reloads = 0;
  const originalWindow = globalThis.window;
  globalThis.window = { location: { reload: () => { reloads++; } } };
  t.after(() => { globalThis.window = originalWindow; });
  const page = ApplicationError();
  const markup = renderToStaticMarkup(page);
  assert.match(markup, /lang="de"/);
  assert.match(markup, /Das hat leider nicht geklappt/);
  assert.match(markup, /Nicht gespeicherte Eingaben/);
  assert.equal(reloads, 0);
  const button = page.props.children.find((child) => child.type === 'button');
  assert.equal(button.props.children, 'Seite neu laden');
  button.props.onClick();
  assert.equal(reloads, 1);
});

test('the application route boundary replaces technical errors, including under a context path', async (t) => {
  const originalDocument = globalThis.document;
  globalThis.document = { baseURI: 'https://topteacher.example/schule/' };
  t.after(() => { globalThis.document = originalDocument; });
  const { router } = loadFrontendModule('routes.tsx', {
    'Frontend/generated/flow/Flow': {
      serverSideRoutes: [{
        path: '/*',
        loader: () => { throw new Error('private database detail'); },
        element: React.createElement('p', null, 'Should not render'),
      }],
    },
    'react-router': {
      createBrowserRouter: (routes, options) => createMemoryRouter(routes, {
        ...options, initialEntries: ['/schule/exams'],
      }),
    },
  });
  t.after(() => router.dispose());
  if (!router.state.initialized) {
    await new Promise((resolve) => {
      const unsubscribe = router.subscribe((state) => {
        if (state.initialized) { unsubscribe(); resolve(); }
      });
    });
  }
  assert.ok(router.state.errors);
  const markup = renderToStaticMarkup(React.createElement(RouterProvider, { router }));
  assert.match(markup, /Das hat leider nicht geklappt/);
  assert.match(markup, /Seite neu laden/);
  assert.doesNotMatch(markup, /private database detail|Unexpected Application Error|Hey developer|ErrorBoundary|Should not render/);
});
