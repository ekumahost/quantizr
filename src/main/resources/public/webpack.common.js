// Note on compiling SASS/SCSS to CSS: That happens in 'on-build-start.sh'.

const webpack = require("webpack");
const CircularDependencyPlugin = require("circular-dependency-plugin");
const WebpackShellPlugin = require("webpack-shell-plugin");
//const HtmlWebpackPlugin = require('html-webpack-plugin');

const prod = process.argv.indexOf("-p") !== -1;
const env = prod ? "prod" : "dev";

console.log("TARGET ENV: " + env);

module.exports = {
    entry: "./ts/index.ts",
    output: {
        filename: "bundle.js",
        path: __dirname
    },

    resolve: {
        // Add '.ts' and '.tsx' as resolvable extensions.
        extensions: [".ts", ".js", ".json"]
    },

    module: {
        rules: [
            // All files with a '.ts' or '.tsx' extension will be handled by 'awesome-typescript-loader'.
            {
                test: /\.ts$/,
                loader: "awesome-typescript-loader",
                query: {
                    // Use this to point to your tsconfig.json.
                    configFileName: "./tsconfig." + env + ".json"
                }
            },

            // All output '.js' files will have any sourcemaps re-processed by 'source-map-loader'.
            {
                enforce: "pre",
                test: /\.js$/,
                loader: "source-map-loader"
            },

            {
                test: /\.htm$/,
                use: ["html-loader"]
            },
        ]
    },

    plugins: [
        new WebpackShellPlugin({
            onBuildStart: ["./on-build-start.sh"],
            //onBuildEnd: ['whatever else']
        }),
        new CircularDependencyPlugin({
            // `onDetected` is called for each module that is cyclical
            onDetected({ module: webpackModuleRecord, paths, compilation }) {
                // `paths` will be an Array of the relative module paths that make up the cycle
                // `module` will be the module record generated by webpack that caused the cycle
                var fullPath = paths.join(" -> ");
                if (fullPath.indexOf("node_modules") === -1) {
                    compilation.errors.push(new Error("CIRC. REF: " + fullPath));
                }
            }
        })
        /* With thymeleaf enabled, we no longer need this, and the index.htm folder is moved into 'templates' also,
        but lets keep this code commented for future reference. */

        // new HtmlWebpackPlugin({
        //     template: './html/index.html',
        //     filename: 'index.html',
        //     hash: true,
        //     cachebuster: '' + new Date().getTime(),
        //     // todo-1: need to pull this from a bash variable, and not have hard-coded here, alghough this is not a 
        //     // security risk, and is public not secret, and safe to check into Github.
        //     reCaptcha3SiteKey: "6LeGyK4ZAAAAAPbF4hI0rtRwSveBdDVXnmhOsfff"
        // }),
    ]
};
