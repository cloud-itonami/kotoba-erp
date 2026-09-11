(ns kotoba-erp.graph-test
  "Unit coverage for the StateGraph runner shim itself."
  (:require [clojure.test :refer [deftest is]]
            [kotoba-erp.graph :as g]))

(deftest test-linear-and-conditional
  (let [graph (-> (g/state-graph)
                  (g/add-node "a" (fn [s] {:n (inc (:n s))}))
                  (g/add-node "left"  (fn [_] {:branch :left}))
                  (g/add-node "right" (fn [_] {:branch :right}))
                  (g/add-edge g/START "a")
                  (g/add-conditional-edges "a" (fn [s] (if (even? (:n s)) "even" "odd"))
                                           {"even" "left" "odd" "right"})
                  (g/add-edge "left" g/END)
                  (g/add-edge "right" g/END)
                  (g/compile-graph))]
    (is (= :left  (:branch (g/invoke graph {:n 1}))))   ; 1->2 even
    (is (= :right (:branch (g/invoke graph {:n 2}))))))  ; 2->3 odd

(deftest test-missing-start-throws
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
               (g/compile-graph (g/state-graph)))))
