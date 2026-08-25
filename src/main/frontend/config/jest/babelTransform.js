// This mirrors react-scripts' own config/jest/babelTransform.js (same preset,
// same options) - the only change is at the bottom. See the comment there for
// why this file exists instead of using react-scripts' version directly.

// react-scripts declares babel-jest/babel-preset-react-app as its own
// dependencies, not this project's - under pnpm's strict node_modules layout
// they aren't resolvable via a plain require() from this file's location, so
// they have to be resolved relative to react-scripts' own install location.
const reactScriptsDir = require('path').dirname(require.resolve('react-scripts/package.json'));
const babelJest = require(require.resolve('babel-jest', { paths: [reactScriptsDir] })).default;

const hasJsxRuntime = (() => {
    if (process.env.DISABLE_NEW_JSX_TRANSFORM === 'true') {
        return false;
    }

    try {
        require.resolve('react/jsx-runtime');
        return true;
    } catch (e) {
        return false;
    }
})();

const transformer = babelJest.createTransformer({
    presets: [
        [
            require.resolve('babel-preset-react-app', { paths: [reactScriptsDir] }),
            {
                runtime: hasJsxRuntime ? 'automatic' : 'classic',
            },
        ],
    ],
    babelrc: false,
    configFile: false,
});

// @jest/transform's ScriptTransformer re-invokes `.createTransformer()` on any
// exported transformer module that has that property, discarding whatever
// options were baked into the transformer above (like the babel-preset-react-app
// config) and building a blank one instead - the replacement's presets end up
// empty, so JSX parsing silently breaks in every test file. This is what was
// breaking react-scripts' own babelTransform.js on this project's currently
// installed @jest/transform version, even though the exact same preset config
// works fine when applied directly outside of Jest. Stripping createTransformer
// from the exported object avoids that re-invocation entirely, so the properly
// configured transformer above is what actually runs.
const { createTransformer: _unusedFactory, ...safeTransformer } = transformer;

module.exports = safeTransformer;
