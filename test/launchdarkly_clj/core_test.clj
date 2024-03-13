(ns launchdarkly-clj.core-test
  (:require [clojure.test :refer :all]
            [launchdarkly-clj.core :as ld]))

(deftest flag-accessors-tests
  (testing "Boolean flag"
    (with-redefs [ld/bool-flag (constantly true)]
      (is
       (= :on
          (ld/with-bool-flag nil
            (ld/context "Sandy" "context-key-123abc")
            ["flag-key-123abc" false]
            :on
            :off)))))
  (testing "String flag"
    (with-redefs [ld/string-flag (constantly "foo")]
      (is
       (= :foo
          (ld/with-string-flag nil
            "context-key-123abc"
            ["flag-key-123abc" "default"]
            "foo"     :foo
            "bar"     :bar
            "default" :absent))))
    (with-redefs [ld/string-flag (constantly "default")]
      (is
       (= :absent
          (ld/with-string-flag nil
            "context-key-123abc"
            ["flag-key-123abc" "default"]
            "foo"     :foo
            "bar"     :bar
            "default" :absent))))))

(deftest  ldvalue-conversion-tests
  (testing "Context of the test assertions"
    (let [homogenous-sample {"foo" true "bar" false}
          nested-sample     {"foo" [1 2 3] "bar" {"baz" true "zab" 5}}
          homogenous-value  (ld/homogenous-map->value :bool homogenous-sample)
          nested-value      (ld/map->value nested-sample)]
      (is (= homogenous-sample (ld/homogenous-value->map homogenous-value)))
      (is (= nested-sample     (ld/value->map nested-value))))
    
    )
  ) 
