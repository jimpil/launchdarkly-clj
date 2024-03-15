(ns launchdarkly-clj.core
  (:require [launchdarkly-clj.global :as global]
            [jsonista.core :as json])
  (:import (com.launchdarkly.sdk ContextKind LDContext LDValue LDValue$Convert)
           (com.launchdarkly.sdk.server FlagsStateOption LDClient)
           (com.launchdarkly.sdk.json LDJackson)))

;; LDValue <-> map

(defonce ld-obj-mapper
  (doto json/default-object-mapper
    (.registerModule (LDJackson/module))))

(defn clj->value
  "Converts arbitrary clojure data into a `LDValue` via its JSON representation."
  ^LDValue [x]
  (-> x (json/write-value-as-string ld-obj-mapper) LDValue/parse))

(defn homogenous-map->value
  "Converts a flat/homogenous map (String => Boolean/Long/Double/String) 
   into an `LDValue`. May be faster than `map->value`, as there is no JSON involved."
  ^LDValue [t ^java.util.Map m]
  (case t
    :bool   (.objectFrom LDValue$Convert/Boolean m)
    :int    (.objectFrom LDValue$Convert/Long m)
    :double (.objectFrom LDValue$Convert/Double m)
    :string (.objectFrom LDValue$Convert/String m)))

(defn value->clj
  "Converts an arbitrary `LDValue` into clojure data via its JSON representation."
  [^LDValue v]
  (-> v .toJsonString (json/read-value ld-obj-mapper)))

(defn homogenous-value->map
  "Converts a flat/homogenous `LDValue` into a  map.
   May be faster than `value->map`, as there is no JSON involved."
  [^LDValue ldv]
  (let [ld-vs (.values ldv)
        t     (.getType ^LDValue (first ld-vs))
        f     (case (.ordinal t)
                1 (fn [^LDValue v] (.booleanValue v))
                2 (fn [^LDValue v] (if (.isInt v) (.longValue v) (.doubleValue v)))
                3 (fn [^LDValue v] (.stringValue v)))]
    (zipmap (.keys ldv) (map f ld-vs))))

;-------------------------------------------------
;; Top-level flag accessors 

(defn- variation*
  [t
   ^LDClient client
   ^LDContext ctx
   k
   not-found]
  (case t
    :bool   (.boolVariation   client k ctx not-found)
    :int    (.intVariation    client k ctx not-found)
    :double (.doubleVariation client k ctx not-found)
    :string (.stringVariation client k ctx not-found)
    :json   (value->clj (.jsonValueVariation client k ctx not-found))))

(defn bool-flag
  ([ctx k not-found]
   (some-> (global/client) (bool-flag ctx k not-found)))
  ([client ctx k not-found]
   (variation* :bool client ctx k not-found)))

(defn int-flag
  ([ctx k not-found]
   (some-> (global/client) (int-flag ctx k not-found)))
  ([client ctx k not-found]
   (variation* :int client ctx k not-found)))

(defn double-flag
  ([ctx k not-found]
   (some-> (global/client) (double-flag ctx k not-found)))
  ([client ctx k not-found]
   (variation* :double client ctx k not-found)))

(defn string-flag
  ([ctx k not-found]
   (some-> (global/client) (string-flag ctx k not-found)))
  ([client ctx k not-found]
   (variation* :string client ctx k not-found)))

(defn json-flag
  ([ctx k not-found]
   (some-> (global/client) (json-flag ctx k not-found)))
  ([client ctx k not-found]
   (variation* :json client ctx k not-found)))
;------------------------------------------------
;; Client creation/shutdown

(defn client
  "Returns a new `LDClient` instance, which is exepected to live as long as 
   your application (and reused throughout - i.e. don't create multiple clients).
   The <sdk-key> can be either a symbol (implying an environment variable), or 
   a String (implying an actual key)."
  ^LDClient [sdk-key]
  (let [^String sdk-key (cond-> sdk-key
                          (symbol? sdk-key)
                          (System/getenv))]
    (LDClient. sdk-key)))

(defn shutdown-client
  ([]
   (some-> (global/client) shutdown-client))
  ([^LDClient client]
   (.close client)))
;--------------------------------------------------
;; Context(Kind) creation

(defn context-kind
  "Returns a new `ContextKind` instance given the provided key."
  ^ContextKind [^String k]
  (ContextKind/of k))

(defn context
  "Returns a new `LDContext` instance given the provided
   <ctx-kind> (defaults to 'user'), <ctx-name> (optional),
   and <ctx-key> (mandatory)."
  (^LDContext [ctx-key]
   (context nil ctx-key))
  (^LDContext [ctx-name ctx-key]
   (context ContextKind/DEFAULT ctx-name ctx-key))
  (^LDContext [^ContextKind ctx-kind ctx-name ctx-key]
   (cond-> (LDContext/builder ctx-kind ctx-key)
     ctx-name (.name ctx-name)
     true     .build)))

(defn multi-context
  "Returns an `LDContext` instance which wraps the 
   provided <ctxs> instances."
  ^LDContext [& ctxs]
  (LDContext/createMulti (into-array LDContext ctxs)))

(defn all-context-flags
  "Returns a map of all the flags in the given <ctx>."
  ([ctx]
   (some-> (global/client) (all-context-flags ctx)))
  ([^LDClient client
    ^LDContext ctx]
   (->> (into-array FlagsStateOption [])
        (.allFlagsState client ctx)
        (.toValuesMap)
        (into {}
              (map
               (fn [[k v]]
                 [k (value->clj v)]))))))
;-------------------------------------------------------
;; Helper macros

(defmacro with-bool-flag
  "Emits code which evaluates either <on-expr> or <off-expr>, depending on
   whether feature <flag-key> is on VS off, within the given context <ctx>."
  [client ctx [flag-key not-found] on-expr off-expr]
  `(let [ctx-provided# ~ctx
         ctx#    (cond-> ctx-provided# (string? ctx-provided#) context)
         client# (or ~client (global/client))
         on?#    (bool-flag client# ctx# ~flag-key (or ~not-found false))]
     (case on?#
       true  ~on-expr
       false ~off-expr)))

(defmacro with-string-flag
  "Emits code which invokes one of the (no-arg) functions in the <outcomes> map,
   depending on what feature <flag-key> is set to, within the given context <ctx>.
   In case of no match, you have two options - either provide an outcome which matches
   <not-found> (defaults to 'default'), or let this function throw."
  [client ctx [flag-key not-found] & outcomes]
  `(let [ctx-provided# ~ctx
         ctx#    (cond-> ctx-provided# (string? ctx-provided#) context)
         client# (or ~client (global/client))
         flag#   (string-flag client# ctx# ~flag-key (or ~not-found "default"))
         outcomes# ~(->> (partition 2 outcomes)
                         (into {}
                               (map (fn [[k# expr#]]
                                      [k# `(fn ~(symbol (str "_handle-ld-flag-" k#)) [] ~expr#)]))))]
     (if-some [f# (get outcomes# flag#)]
       (f#)
       (throw
        (IllegalStateException.
         (str "No outcome matches the feature-flag: " [~flag-key flag#]))))))

(comment
  ;; https://docs.launchdarkly.com/sdk/server-side/java
  (def sdk-key "...")

  (with-open [some-client (client sdk-key)] ;; implements Closeable
    (with-bool-flag
      some-client
      (context "Sandy" "context-key-123abc")
      ["flag-key-123abc" false]
      (println "FLAG is on!")
      (println "FLAG is off!"))

    (with-string-flag
      some-client
      "context-key-123abc"
      ["flag-key-123abc" "default"]
      "foo"     (println "FLAG is set to 'foo'!")
      "bar"     (println "FLAG is set to 'bar'!")
      "default" (println "FLAG is not set!")))

  (with-redefs [string-flag (constantly "foo")]
    (with-string-flag
      nil
      "context-key-123abc"
      ["flag-key-123abc" "default"]
      "foo"     (println "FLAG is set to 'foo'!")
      "bar"     (println "FLAG is set to 'bar'!")
      "default" (println "FLAG is not set!"))))
