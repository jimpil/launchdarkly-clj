(ns launchdarkly-clj.core-test
  (:require [clojure.test :refer :all]
            [launchdarkly-clj.core :as ld]))

(deftest a-test
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
