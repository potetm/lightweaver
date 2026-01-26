;; Copyright (c) Timothy Pote, 2025. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

(ns com.potetm.lightweaver
  (:refer-clojure :exclude [requiring-resolve replace])
  (:require
    [clojure.core :as cc]
    [clojure.tools.logging :as log])
  (:import
    (java.io FileNotFoundException)))


(defn deps
  "Returns all alias and refer dependencies of a namespace."
  [ns]
  (-> (into #{}
            (comp (map val)
                  (map ns-name))
            (ns-aliases (the-ns ns)))
      (into (comp (map #(:ns (meta (val %))))
                  (map ns-name)
                  (remove #(= % 'clojure.core)))
            (ns-refers (the-ns ns)))))


(defn graph
  "Given a root namespace, returns a hashmap of ns -> set-of-namespaces-that-depend-on-ns."
  ([]
   (graph (ns-name *ns*)))
  ([ns]
   ;; User may or may not have required the root ns.
   ;; Preemptively require it.
   (require ns)
   (loop [ret {}
          todo #{ns}
          done #{}]
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
                      (remove done)
                      ds)
                (conj done ns)))
       (with-meta ret
         {::roots #{ns}})))))


(defn dups? [coll]
  (= :dups
     (reduce (fn [seen v]
               (if (seen v)
                 (reduced :dups)
                 (conj seen v)))
             #{}
             coll)))


(defn cycle-paths
  "Return all cycling paths in graph g."
  [g]
  (loop [q (into []
                 (map vector)
                 (::roots (meta g)))
         ret []]
    (if (seq q)
      (let [path (peek q)
            n (peek path)
            deps (into []
                       (comp (filter #(contains? (val %)
                                                 n))
                             (map key))
                       g)]
        (if (dups? path)
          (recur (pop q)
                 (conj ret path))
          (recur (into (pop q)
                       (map #(conj path %))
                       deps)
                 ret)))
      ret)))


(defn cycle-node
  "Walk from the known root of the tree to the cycle, find the shortest path to
  a cycle, and return the last node before the cycle occurs.

  For example, a cycle of [a b c b] will return c."
  [g]
  (peek (pop (apply min-key count (cycle-paths g)))))


(defn topo-sort
  "Given a graph returned by `graph, sort all namespaces topologically.

  If supplied a list () as ret, the return will be reverse topologically sorted."
  ([g]
   (topo-sort [] g))
  ([ret g]
   (loop [ret ret
          g' g]
     (if (seq g')
       (let [deps (into #{}
                        cat
                        (vals g'))
             leaves (into #{}
                          (remove deps)
                          (keys g'))]
         (if (seq leaves)
           (recur (into ret (sort leaves))
                  (reduce dissoc g' leaves))
           (let [n (cycle-node g')]
             (recur (conj ret n)
                    (dissoc g' n)))))
       ret))))


(defn topo-sort-rev
  "Given a graph returned by `graph, reverse sort all namespaces topologically."
  [g]
  (topo-sort () g))


(defn merge-graph
  "Given a list of namespace symbols, return a graph that includes the
  dependency graphs of all the namespaces."
  [nss]
  (reduce (fn [g ns]
            (merge-with into
                        (vary-meta g update ::roots (fnil conj #{}) ns)
                        (graph ns)))
          {}
          nss))


(defn requiring-resolve [ns sym]
  (try
    (cc/requiring-resolve (symbol (str ns)
                                  (str sym)))
    ;; as-alias deps cannot be required
    (catch FileNotFoundException _)))


(defn plan-xf [sym]
  (comp (remove (fn [ns]
                  ;; avoid cyclic starts
                  (= ns 'com.potetm.lightweaver)))
        (keep (fn [ns]
                ;; If replacement is used, or an unloaded namespace is provided
                ;; as a root, ns may not be loaded. Load it first, then look
                ;; for sym.
                (requiring-resolve ns sym)))))


(defn namespaces
  "A transducer that will restrict a plan to the provided set of namespaces."
  [nss]
  (filter (set nss)))


(defn replace
  "A transducer that takes a hashmap of {'original.namespace 'replacement.namespace}
  and replaces namespaces (e.g. for testing)."
  [kmap]
  (map #(get kmap % %)))


(defn plan
  "Given var search criteria, return the list of vars in topological order.

  ::symbol - The var symbol to search for in the graph (e.g. 'start).
  ::roots - The root namespaces used to build the graph.
  ::xf - xform to apply to the sorted namespaces. See also `namespaces, `replace."
  [{sym ::symbol
    rs ::roots
    xf ::xf}]
  (into []
        (if xf
          (comp xf (plan-xf sym))
          (plan-xf sym))
        (topo-sort (merge-graph rs))))


(defn plan-rev
  "Given var search criteria, return the list of vars in reverse topological
  order.

  ::symbol - The var symbol to search for in the graph (e.g. 'stop).
  ::roots - The root namespaces used to build the graph.
  ::xf - xform to apply to the sorted namespaces. See also `namespaces, `replace."
  [{sym ::symbol
    rs ::roots
    xf ::xf}]
  (into []
        (if xf
          (comp xf (plan-xf sym))
          (plan-xf sym))
        (topo-sort-rev (merge-graph rs))))


(defn run
  "Reduce over namespaces running sym if it can be resolved and ignoring the
  namespace if sym cannot be resolved."
  [init vars]
  (reduce (fn [sys v]
            (v sys))
          init
          vars))


(defn stop
  "Stop a system by running 'stop in topological order for all namespaces.

  ::stop-sym - The symbol to search for in the graph. Defaults to 'lw:stop.
  ::roots - The root namespaces to initialize the graph. (Usually provided from `start.)"
  ([{s ::stop-sym :as sys
     :or {s 'lw:stop}}]
   (reduce (fn [sys f]
             (try
               (f sys)
               (catch Exception e
                 (log/error e
                            "Error stopping system. Continuing..."
                            {:sys sys
                             :fn f})
                 sys)))
           sys
           (plan-rev (merge {::symbol s}
                            sys)))))


(defn start
  "Start a system by running 'start in topological order for all namespaces.

  ::start-sym - The symbol to search for in the graph. Defaults to 'lw:start.
  ::roots - The root namespaces to initialize the graph.
  ::xf - xform to apply to the sorted namespaces. See also `namespaces, `replace."
  ([{s ::start-sym :as sys
     :or {s 'lw:start}}]
   (reduce (fn [sys f]
             (try
               (f sys)
               (catch Exception e
                 (log/error e
                            "Error starting system. Stopping..."
                            {:sys sys
                             :fn f})
                 (throw (ex-info "Error starting component system. System stopped."
                                 {:sys (stop sys)
                                  :fn f}
                                 e)))))
           sys
           (plan (merge {::symbol s}
                        sys)))))


(defmacro with-sys
  "Initialize system, run body, and guarantee proper shutdown. Example usage:

  (with-sys [sys {:my-val 123
                  ::roots '[my.root.ns]}]
    (do-work sys))"
  [[binding args] & body]
  `(let [sys# (start ~args)
         ~binding sys#]
     (try
       ~@body
       (finally
         (stop sys#)))))


(comment
  (merge-graph ['my.webserver
                'my.background-jobs])

  (in-ns 'my.webserver)
  (require '[com.potetm.lightweaver :as lw])
  (sort-by (topo-compare-keyfn (graph 'my.webserver))
           '[my.webserver my.database])
  (topo-sort (graph 'a))
  (topo-sort (merge-graph ['a 'b]))
  )
