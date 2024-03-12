(ns launchdarkly-clj.global
  (:import (com.launchdarkly.sdk.server LDClient)))

(def ^:private CLIENT (promise))

(defn with-global-client!
  [client]
  (when-not (realized? CLIENT)
    (deliver CLIENT client)
    (->> (Thread. #(.close ^LDClient @CLIENT))
         (.addShutdownHook (Runtime/getRuntime)))))

(defn client []
  (when (realized? CLIENT)
    @CLIENT))