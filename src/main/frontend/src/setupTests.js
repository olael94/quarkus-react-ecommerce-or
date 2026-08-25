// jest-dom adds custom jest matchers for asserting on DOM nodes.
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

// jsdom only provides localStorage for a real (non-opaque) origin, and the
// default test environment here uses "about:blank", which is opaque - just
// accessing window.localStorage throws a SecurityError. Setting a URL via
// testEnvironmentOptions would normally fix this, but that option isn't among
// CRA's supported jest config overrides without ejecting - so this is a
// minimal in-memory polyfill instead, for tests (like CartContext's) that
// rely on localStorage persistence.
let hasWorkingLocalStorage = true;
try {
    // jest-environment-jsdom doesn't throw here - it just leaves localStorage
    // undefined instead, so both cases have to be checked, not just "did this throw"
    hasWorkingLocalStorage = Boolean(window.localStorage);
} catch (error) {
    hasWorkingLocalStorage = false;
}

if (!hasWorkingLocalStorage) {
    class MemoryStorage {
        constructor() {
            this.store = {};
        }

        clear() {
            this.store = {};
        }

        getItem(key) {
            return Object.prototype.hasOwnProperty.call(this.store, key) ? this.store[key] : null;
        }

        setItem(key, value) {
            this.store[key] = String(value);
        }

        removeItem(key) {
            delete this.store[key];
        }
    }

    Object.defineProperty(window, 'localStorage', {
        value: new MemoryStorage(),
        writable: true,
    });
}
