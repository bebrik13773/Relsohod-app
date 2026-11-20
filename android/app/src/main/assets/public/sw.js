/**
 * Copyright 2018 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// If the loader is already loaded, just stop.
if (!self.define) {
  let registry = {};

  // Used for `eval` and `importScripts` where we can't get script URL by other means.
  // In both cases, it's safe to use a global var because those functions are synchronous.
  let nextDefineUri;

  const singleRequire = (uri, parentUri) => {
    uri = new URL(uri + ".js", parentUri).href;
    return registry[uri] || (
      
        new Promise(resolve => {
          if ("document" in self) {
            const script = document.createElement("script");
            script.src = uri;
            script.onload = resolve;
            document.head.appendChild(script);
          } else {
            nextDefineUri = uri;
            importScripts(uri);
            resolve();
          }
        })
      
      .then(() => {
        let promise = registry[uri];
        if (!promise) {
          throw new Error(`Module ${uri} didn’t register its module`);
        }
        return promise;
      })
    );
  };

  self.define = (depsNames, factory) => {
    const uri = nextDefineUri || ("document" in self ? document.currentScript.src : "") || location.href;
    if (registry[uri]) {
      // Module is already loading or loaded.
      return;
    }
    let exports = {};
    const require = depUri => singleRequire(depUri, uri);
    const specialDeps = {
      module: { uri },
      exports,
      require
    };
    registry[uri] = Promise.all(depsNames.map(
      depName => specialDeps[depName] || require(depName)
    )).then(deps => {
      factory(...deps);
      return exports;
    });
  };
}
define(['./workbox-afac4cd2'], (function (workbox) { 'use strict';

  self.skipWaiting();
  workbox.clientsClaim();
  /**
   * The precacheAndRoute() method efficiently caches and responds to
   * requests for URLs in the manifest.
   * See https://goo.gl/S9QRab
   */
  workbox.precacheAndRoute([{
    "url": "pwa-maskable-512x512.png",
    "revision": "b3b47282f7cfbe43e5937e3c59ba1c28"
  }, {
    "url": "pwa-512x512.png",
    "revision": "8546e639de41ded4211793c57d211c21"
  }, {
    "url": "pwa-192x192.png",
    "revision": "cd52ef3705cbee720bfdcb833b46fedd"
  }, {
    "url": "index.html",
    "revision": "e1f9250041c1cde485a5513503ea8f5f"
  }, {
    "url": "icon.svg",
    "revision": "f2258dfbfbd520e23167598f7897f8c8"
  }, {
    "url": "apple-touch-icon.png",
    "revision": "5f7d036ff611cf4d913cacfeb887f87e"
  }, {
    "url": "src/assets/images/spb_vasilievsky_spit_1795153258899.jpg",
    "revision": "75310c56d88b9212e83ecb5ef29a2ede"
  }, {
    "url": "src/assets/images/spb_trinity_bridge_tram_1795153259516.jpg",
    "revision": "067b2b1b120caa12e9144920f0e8ce25"
  }, {
    "url": "src/assets/images/spb_smolny_cathedral_1795153259753.jpg",
    "revision": "89410502180792258900d409cff5a05b"
  }, {
    "url": "src/assets/images/spb_narvskaya_arch_1795153259152.jpg",
    "revision": "10a54950b93fa174bf36d1ef30091955"
  }, {
    "url": "src/assets/images/spb_kronverksky_park_1795153259206.jpg",
    "revision": "855c73a7eb3cbcff12b771e570d9fd81"
  }, {
    "url": "src/assets/images/spb_kolomna_canal_1795153259145.jpg",
    "revision": "5f0c6a0a57b885d6fa0737042ae94e7f"
  }, {
    "url": "src/assets/images/spb_cozy_coffee_interior_1795153259586.jpg",
    "revision": "54d93741f08e85b5878d4775af22fd31"
  }, {
    "url": "src/assets/images/spb_chizhik_modern_tram_1795153259608.jpg",
    "revision": "8c87344191af29ef2d1f8854bb1963b3"
  }, {
    "url": "assets/workbox-window.prod.es5-Bd17z0YL.js",
    "revision": null
  }, {
    "url": "assets/index-DDqjc0XM.css",
    "revision": null
  }, {
    "url": "assets/index-0iruP1VN.js",
    "revision": null
  }, {
    "url": "apple-touch-icon.png",
    "revision": "5f7d036ff611cf4d913cacfeb887f87e"
  }, {
    "url": "icon.svg",
    "revision": "f2258dfbfbd520e23167598f7897f8c8"
  }, {
    "url": "pwa-192x192.png",
    "revision": "cd52ef3705cbee720bfdcb833b46fedd"
  }, {
    "url": "pwa-512x512.png",
    "revision": "8546e639de41ded4211793c57d211c21"
  }, {
    "url": "pwa-maskable-512x512.png",
    "revision": "b3b47282f7cfbe43e5937e3c59ba1c28"
  }, {
    "url": "manifest.webmanifest",
    "revision": "79736783ae492577b98bf07fe0ced481"
  }], {});
  workbox.cleanupOutdatedCaches();
  workbox.registerRoute(new workbox.NavigationRoute(workbox.createHandlerBoundToURL("index.html")));
  workbox.registerRoute(/^https:\/\/fonts\.googleapis\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "google-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 10,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');
  workbox.registerRoute(/^https:\/\/fonts\.gstatic\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "gstatic-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 10,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');

}));
