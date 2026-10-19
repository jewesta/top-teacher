import React from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';
import { serverSideRoutes } from 'Frontend/generated/flow/Flow';
import { ApplicationError } from './tt-application-error';

export const routes: RouteObject[] = [
  ...serverSideRoutes
].map((route) => ({ ...route, errorElement: <ApplicationError /> }));

export const router = createBrowserRouter(routes, {
  basename: new URL(document.baseURI).pathname,
});
