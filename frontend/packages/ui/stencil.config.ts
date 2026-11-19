import { Config } from '@stencil/core';

export const config: Config = {
  namespace: 'habitloop',
  sourceMap: true,
  outputTargets: [
    { type: 'dist', esmLoaderPath: '../loader' },
    {
      type: 'dist-custom-elements',
      dir: 'dist/custom-elements',
      customElementsExportBehavior: 'bundle'
    },
    { type: 'www', serviceWorker: null }
  ]
};
