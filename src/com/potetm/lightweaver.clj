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
    (java.util List)))


(defn deps
  "Returns all alias and refer dependencies of a namespace."
  [ns]
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
                        ;; make sure this ns is in ret (in case it has
                        ;; no dependents).
                        (update ret ns (fnil into #{}))
                        ds)
                (into (disj todo ns)
                      ds)))
       ret))))


(defn graph
  "Given a root namespace, returns a hashmap of
  ns -> set-of-namespaces-that-depend-on-ns."
  ([]
   (graph *ns*))
  ([ns]
   ;; avoid cyclic starts
   (dissoc (graph* ns)
           (the-ns 'com.potetm.lightweaver))))


(defn topo-sort
  "Given a graph returned by `graph, sort all namespaces topologically.

  If supplied a list () as ret, the return will be reverse topologically sorted."
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


(defn topo-sort-rev
  "Given a graph returned by `graph, reverse sort all namespaces topologically."
  [g]
  (topo-sort () g))


(defn sort-namespaces
  "Given a list of namespace symbols, turn them into namespace objects and
  sort them topologically."
  ([namespaces]
   (sort-namespaces < namespaces))
  ([comparator namespaces]
   (let [sorted (topo-sort (reduce (fn [g ns]
                                     (merge-with into
                                                 g
                                                 (graph (the-ns ns))))
                                   {}
                                   namespaces))]
     (sort-by (comp (partial List/.indexOf sorted)
                    the-ns)
              comparator
              namespaces))))


(defn run!
  "Reduce over namespaces running sym if it can be resolved and ignoring the
  namespace if sym cannot be resolved."
  [init sym namespaces]
  (reduce (fn [sys ns]
            (if-some [v (ns-resolve ns sym)]
              (v sys)
              sys))
          init
          namespaces))


(defn start
  "Start a system by running 'start in topological order for all namespaces.

  init - The initial value supplied to reduce
  namespaces - The optional list of namespaces to start. If not supplied, it
               runs over all namespaces reachable via refer or alias from *ns*."
  ([init]
   (run! init
         'start
         (topo-sort (graph))))
  ([init namespaces]
   (run! init
         'start
         (sort-namespaces namespaces))))


(defn stop
  "Start a system by running 'stop in topological order for all namespaces.

  sys - The system returned from `start.
  namespaces - The optional list of namespaces to stop. If not supplied, it
               runs over all namespaces reachable via refer or alias from *ns*."
  ([sys]
   (run! sys
         'stop
         (topo-sort-rev (graph))))
  ([sys namespaces]
   (run! sys
         'stop
         (sort-namespaces > namespaces))))


(defmacro with-sys
  "Initialize system, run body, and guarantee proper shutdown. Example usage:

  (with-sys [sys {:initial 'value}]
    (do-work sys))"
  [[binding init ?components] & body]
  `(let [sys# ~(if (seq ?components)
                 `(start ~init ~?components)
                 `(start ~init))
         ~binding sys#]
     (try
       ~@body
       (finally
         ~(if (seq ?components)
            `(stop ~binding ~?components)
            `(stop ~binding))))))
