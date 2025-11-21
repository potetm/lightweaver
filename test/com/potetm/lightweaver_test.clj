(ns com.potetm.lightweaver-test
  (:require
    [clojure.test :refer :all]
    [com.potetm.lightweaver :as lw]))

(load-file "test-resources/cycling.clj")
(load-file "test-resources/simple_app.clj")

(deftest plan
  (testing "it works"
    (in-ns 'my.webserver)
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.webserver/start]
           (lw/plan {:symbol 'start}))))

  (testing "plan-rev"
    (in-ns 'my.webserver)
    (is (= [#'my.webserver/start
            #'my.param-store/start
            #'my.database/start]
           (lw/plan-rev {:symbol 'start}))))

  (testing "roots"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.webserver/start]
           (lw/plan {:symbol 'start
                     :roots '[my.webserver]}))))

  (testing "multiple roots"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.job-queue/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {:symbol 'start
                     :roots '[my.webserver
                              my.background-jobs]}))))

  (testing "restricted namespaces"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {:symbol 'start
                     :roots '[my.background-jobs my.webserver]
                     ;; no my.job-queue
                     :namespaces '[my.background-jobs
                                   my.webserver
                                   my.database
                                   my.param-store]}))))

  (testing "replace"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'dev.job-queue/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {:symbol 'start
                     :roots '[my.background-jobs my.webserver]
                     :replace '{my.job-queue dev.job-queue}})))))

(deftest cycles
  (testing "it works"
    (is (= '[d c a]
           (lw/topo-sort (lw/graph 'a)))))

  (testing "it works for merge-graph"
    (is (= '[d c a b]
           (lw/topo-sort (lw/merge-graph ['a 'b])))))

  (testing "it finds cycle paths"
    (is (= '[[b c d a c] [a c d a]]
           (lw/cycle-paths (lw/merge-graph ['a 'b]))))))
