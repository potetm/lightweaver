(ns com.potetm.deps
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import
    (clojure.lang RT)
    (java.io File)
    (java.net URLClassLoader)))


(defn base-cp []
  (into []
        (comp (take-while some?)
              (filter #(instance? URLClassLoader %))
              (mapcat URLClassLoader/.getURLs)
              (map io/as-file))
        (iterate #(.getParent ^ClassLoader %)
                 (RT/baseLoader))))


(defn sys-cp []
  (into []
        (comp (map io/as-file))
        (str/split (System/getProperty "java.class.path")
                   (re-pattern (System/getProperty "path.separator")))))


(meta *ns*)

ns
(clojure.repl/dir *ns*
  )
require
