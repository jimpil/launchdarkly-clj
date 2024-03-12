(ns launchdarkly-clj.core
  (:require [launchdarkly-clj.global :as global]
            [jsonista.core :as json])
  (:import (com.launchdarkly.sdk ContextKind LDContext LDValue)
           (com.launchdarkly.sdk.server FlagsStateOption LDClient)))

;; LDValue <-> map (via JSON)

(defn map->value
  ^LDValue [m]
  (-> m json/write-value-as-string LDValue/parse))

(defn value->map
  [^LDValue v]
  (-> v .toJsonString json/read-value))
;-------------------------------------------------
;; Top-level flag accessors 

(defn- variation
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
    :json   (value->map (.jsonValueVariation client k ctx not-found))))

(defn bool-flag
  [client ctx k not-found]
  (variation :bool client ctx k not-found))

(defn int-flag
  [client ctx k not-found]
  (variation :int client ctx k not-found))

(defn double-flag
  [client ctx k not-found]
  (variation :double client ctx k not-found))

(defn string-flag
  [client ctx k not-found]
  (variation :string client ctx k not-found))

(defn json-flag
  [client ctx k not-found]
  (variation :json client ctx k not-found))
;------------------------------------------------
;; Client creation/shutdown

(defn client
  "Returns a new `LDClient` instance, which is exepected to live as long as 
   your application (and reused throughout - i.e. don't create multiple clients)."
  ^LDClient [^String sdk-key]
  (LDClient. sdk-key))

(defn shutdown-client
  ([] 
   (some-> (global/client) shutdown-client))
  ([^LDClient client]
   (.close client)))
;--------------------------------------------------
;; Context(Kind) creation

(defn context-kind
  ^ContextKind [^String k]
  (ContextKind/of k))

(defn context
  (^LDContext [ctx-key]
   (context nil ctx-key))
  (^LDContext [ctx-name ctx-key]
   (context ContextKind/DEFAULT ctx-name ctx-key))
  (^LDContext [^ContextKind ctx-kind ctx-name ctx-key]
   (cond-> (LDContext/builder ctx-kind ctx-key)
     ctx-name (.name ctx-name)
     true     .build)))

(defn multi-context
  ^LDContext [& ctxs]
  (LDContext/createMulti (into-array LDContext ctxs)))

(defn all-context-flags
  [^LDClient client
   ^LDContext ctx]
  (->> (into-array FlagsStateOption [])
       (.allFlagsState client ctx)
       (.toValuesMap)
       (into {}
             (map
              (fn [[k v]]
                [k (value->map v)])))))
;-------------------------------------------------------
;; Helper macros

(defmacro with-bool-flag
  "Emits code which evaluates either <on-expr> or <off-expr>, depending on
   whether feature <flag-key> is on VS off, within the given context <ctx>."
  [client ctx [flag-key not-found] on-expr off-expr]
  `(let [ctx-provided# ~ctx
         ctx# (cond-> ctx-provided# (string? ctx-provided#) context)
         client# (or ~client (global/client))
         on?# (bool-flag client# ctx# ~flag-key (or ~not-found false))]
     (if on?# ~on-expr ~off-expr)))

(defmacro with-string-flag
  "Emits code which invokes one of the (no-arg) functions in the <outcomes> map,
   depending on what feature <flag-key> is set to, within the given context <ctx>.
   In case of no match, you have two options - either provide an outcome which matches
   <not-found> (defaults to 'default'), or let this function throw."
  [client ctx [flag-key not-found] & outcomes]
  `(let [ctx-provided# ~ctx
         ctx#  (cond-> ctx-provided# (string? ctx-provided#) context)
         client# (or ~client (global/client))
         flag# (string-flag client# ctx# ~flag-key (or ~not-found "default"))
         outcomes# ~(into {}
                          (map (fn [[k# expr#]]
                                 [k# `(fn [] ~expr#)]))
                          (partition 2 outcomes))]
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