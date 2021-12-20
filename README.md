### You will need

* npm 
* shadow-cljs

### Development mode
App will run on http://localhost:3000/
```
npm install
npx shadow-cljs watch app
```

start a ClojureScript REPL
```
npx shadow-cljs browser-repl
```
### Building for production

```
npx shadow-cljs release app
```
