(ns com.potetm.lightweaver-test
  (:require
    [clojure.test :refer :all]
    [com.potetm.lightweaver :as lw])
  (:import (clojure.lang ExceptionInfo)))

(load-file "test-resources/cycling.clj")
(load-file "test-resources/simple_app.clj")
(load-file "test-resources/multi_cycle.clj")
(load-file "test-resources/stateful_app.clj")

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


(deftest start-test
  (testing "calls start functions"
    (let [{c :called} (lw/start {::lw/roots '[stateful.webserver]
                                 :called []})]
      (is (= '[stateful.database/lw:start
               stateful.webserver/lw:start]
             c))))

  (testing "uses custom ::start-sym"
    (let [{c :called} (lw/start {::lw/roots '[stateful.webserver]
                                 ::lw/start-sym 'boot
                                 :called []})]
      (is (= '[stateful.database/boot
               stateful.webserver/boot]
             c))))

  (testing "on error, calls stop then throws"
    (let [{{c :called} :sys} (try
                               (lw/start {::lw/roots '[stateful.error-start]
                                          :called []})
                               (catch ExceptionInfo ei
                                 (ex-data ei)))]
      (is (= c
             '[stateful.database/lw:start
               stateful.error-start/lw:stop
               stateful.database/lw:stop])))))


(deftest stop-test
  (testing "calls stop functions"
    (let [{c :called} (lw/stop {::lw/roots '[stateful.webserver]
                                :called []})]
      (is (= '[stateful.webserver/lw:stop
               stateful.database/lw:stop]
             c))))

  (testing "uses custom ::stop-sym"
    (let [{c :called} (lw/stop {::lw/roots '[stateful.webserver]
                                ::lw/stop-sym 'shutdown
                                :called []})]
      (is (= '[stateful.webserver/shutdown
               stateful.database/shutdown]
             c))))

  (testing "continues on error"
    ;; Should not throw, and database stop should still be called
    (let [{c :called} (lw/stop {::lw/roots '[stateful.error-stop]
                                :called []})]
      (is (= '[stateful.database/lw:stop])
          c))))


(comment
  (lw/plan {::lw/symbol 'start
            ::lw/roots '[my.background-jobs my.webserver]
            ::lw/xf (lw/replace '{my.job-queue dev.job-queue})})

  )


