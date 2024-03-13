# launchdarkly-clj

A thin Clojure wrapper around the core components of [LaunchDarkly](https://launchdarkly.com/) Java SDK.

## Usage

```clj
(require '[launchdarkly-clj.core :as ld])
```

### Client

The first thing you want to do is to acquire an SDK key, and use it to create a client. This is supposed to be a long-lived (as long as your application) object, reused throughout, and destroyed at the end. If you're using some sort of
component system, this should sound familiar, and you probably know what to do.

```clj
(def ld-client (ld/client "your-sdk-key-here")) 
;; shut it down with `(ld/shutdown-client ld-client)`
```
If you're **not** using any component system, you can define the client as a global 
singleton with `global/with-global-client!` at the start of your app. 
All the functions requiring a client (as the first arg) are overloaded, 
so that the first arg can be skipped, and defaults to the global singleton 
(if one exists).


### Context(s)

The second thing you want to do is to define your context(s). 
This is a core concept in LD. In order to define one, you need at least a key,
optionally a name, and ideally a context-kind. The default context-kind is 'user', 
but your can define your own  - e.g. 'account', 'workspace' etc. Here is an example:

```clj
(def ld-ctx 
  (-> (ld/context-kind "workspace") 
      (ld/context "some-ctx-name" "some-ctx-key")))
```
### Flags

With a client and a context at-hand, you are now ready to query for flags. 
Here is how you do that depending on the type (Boolean/String/Long/Double/Map):


```clj
(ld/bool-flag   ld-client ld-ctx "some-ctx-key" false)
(ld/string-flag ld-client ld-ctx "some-ctx-key" "default")
(ld/int-flag    ld-client ld-ctx "some-ctx-key" 0)
(ld/double-flag ld-client ld-ctx "some-ctx-key" 0.0)
(ld/json-flag   ld-client ld-ctx "some-ctx-key" nil) ;; returns map
```
After retrieving a flag, the assumption/expectation is that you will use it in some 
sort of conditional, in order to differentiate what will happen (depending on the flag value).

### Helper macros 

For boolean/String flags, you may want to look at the 
`with-bool-flag`/`with-string-flag` macros. They let you specify 
what code will run depending on the flag returned, declaratively (i.e. no conditionals).

```clj
(with-bool-flag ld-client ld-ctx ["some-ctx-key" false]
  ;; enumerate the two possible outcomes
  (println "FLAG is on!")
  (println "FLAG is off!"))

(with-string-flag ld-client ld-ctx ["some-other-ctx-key" "default"]
  ;; enumerate all the possible outcomes
  "foo"     (println "FLAG is set to 'foo'!")
  "bar"     (println "FLAG is set to 'bar'!")
  "default" (println "FLAG is not set!"))

```


## License

Copyright © 2024 Dimitrios Piliouras

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
