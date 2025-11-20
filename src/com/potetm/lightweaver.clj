;; Copyright (c) Timothy Pote, 2025. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns com.potetm.lightweaver
  (:refer-clojure :exclude [run!])
  (:import
    (clojure.lang APersistentVector)))


(defn deps [ns]
  (into (set (vals (ns-aliases ns)))
        (comp (map #(:ns (meta (val %))))
              (remove #(= % (the-ns 'clojure.core))))
        (ns-refers ns)))


(defn graph*
  ([]
   (graph* *ns*))
  ([ns]
   (loop [ret {}
          todo #{ns}]
     (if (seq todo)
       (let [ns (first todo)
             ds (deps ns)]
         (recur (reduce (fn [r d]
                          (update r d (fnil conj #{}) ns))
                        (update ret ns (fnil into #{}))
                        ds)
                (into (disj todo ns)
                      ds)))
       ret))))


(defn graph
  ([]
   (graph *ns*))
  ([ns]
   ;; avoid cyclic starts
   (dissoc (graph* ns)
           (the-ns 'com.potetm.lightweaver))))


(defn topo-sort
  ([g]
   (topo-sort [] g))
  ([ret g]
   (loop [ret ret
          g g]
     (if (seq g)
       (let [deps (into #{}
                        cat
                        (vals g))
             roots (into #{}
                         (remove deps)
                         (keys g))]
         (recur (into ret (sort-by ns-name roots))
                (reduce dissoc g roots)))
       ret))))


(defn topo-sort-rev [g]
  (topo-sort () g))


(defn sort-namespaces
  ([namespaces]
   (sort-namespaces < namespaces))
  ([comparator namespaces]
   (let [sorted (topo-sort (reduce (fn [g ns]
                                     (merge-with into
                                                 g
                                                 (graph (the-ns ns))))
                                   {}
                                   namespaces))]
     (sort-by (comp (partial APersistentVector/.indexOf sorted)
                    the-ns)
              comparator
              namespaces))))


(defn run! [init sym namespaces]
  (reduce (fn [sys ns]
            (if-some [v (ns-resolve ns sym)]
              (v sys)
              sys))
          init
          namespaces))


(defn start
  ([init]
   (run! init
         'start
         (topo-sort (graph))))
  ([init namespaces]
   (run! init
         'start
         (sort-namespaces namespaces))))


(defn stop
  ([init]
   (run! init
         'stop
         (topo-sort-rev (graph))))
  ([init namespaces]
   (run! init
         'stop
         (sort-namespaces > namespaces))))
