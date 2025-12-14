(ns com.potetm.lightweaver-test
  (:require
    [clojure.test :refer :all]
    [com.potetm.lightweaver :as lw]))

(load-file "test-resources/cycling.clj")
(load-file "test-resources/simple_app.clj")
(load-file "test-resources/multi_cycle.clj")

(deftest plan
  (testing "it works"
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.webserver/start]
           (lw/plan {::lw/symbol 'start
                     ::lw/roots #{'my.webserver}}))))

  (testing "plan-rev"
    (is (= [#'my.webserver/start
            #'my.param-store/start
            #'my.database/start]
           (lw/plan-rev {::lw/symbol 'start
                         ::lw/roots #{'my.webserver}}))))

  (testing "roots"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.webserver/start]
           (lw/plan {::lw/symbol 'start
                     ::lw/roots '[my.webserver]}))))

  (testing "multiple roots"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.job-queue/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {::lw/symbol 'start
                     ::lw/roots '[my.webserver
                              my.background-jobs]}))))

  (testing "restricted namespaces"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {::lw/symbol 'start
                     ::lw/roots '[my.background-jobs my.webserver]
                     ;; no my.job-queue
                     ::lw/xf (lw/namespaces '[my.background-jobs
                                          my.webserver
                                          my.database
                                          my.param-store])}))))

  (testing "replace"
    (in-ns 'com.potetm.lightweaver-test)
    (is (= [#'my.database/start
            #'dev.job-queue/start
            #'my.param-store/start
            #'my.background-jobs/start
            #'my.webserver/start]
           (lw/plan {::lw/symbol 'start
                     ::lw/roots '[my.background-jobs my.webserver]
                     ::lw/xf (lw/replace '{my.job-queue dev.job-queue})})))))

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


(deftest multi-cycles
  (testing "it works"
    (is (= '[mc.two.c mc.two.b mc.one.c mc.one.b mc.one.a]
           (lw/topo-sort (lw/graph 'mc.one.a)))))

  (testing "merge-graph"
    (is (= '[mc.two.c mc.two.b mc.one.c mc.one.b mc.one.a mc.two.a]
           (lw/topo-sort (lw/merge-graph ['mc.one.a 'mc.two.a]))))))


(comment
  (lw/plan {::lw/symbol 'start
            ::lw/roots '[my.background-jobs my.webserver]
            ::lw/xf (lw/replace '{my.job-queue dev.job-queue})})

  )


