// jest-dom adds custom matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';

// jsdom's test environment doesn't define TextEncoder/TextDecoder on the
// global object, but react-router v7 requires them at import time - without
// this, importing react-router-dom throws "TextEncoder is not defined"
// before any test code even runs. Node's own util module has real
// implementations, so those are used directly instead of a custom stand-in.
import { TextEncoder, TextDecoder } from 'util';
if (typeof global.TextEncoder === 'undefined') {
    global.TextEncoder = TextEncoder;
}
if (typeof global.TextDecoder === 'undefined') {
    global.TextDecoder = TextDecoder;
}

// Note: this project's CRA/Jest setup used to need a hand-rolled localStorage
// polyfill here, because jsdom only provides localStorage for a real
// (non-opaque) origin, and CRA's Jest config always used the opaque
// "about:blank" test origin with no supported way to override it short of
// ejecting. Under Vitest, the jsdom origin is directly configurable
// (vite.config.js's test.environmentOptions.jsdom.url), so that's no longer
// needed. Node 22+'s own experimental global localStorage instead became the
// problem here - see the NODE_OPTIONS flag on the test scripts in
// package.json for how that's handled.
