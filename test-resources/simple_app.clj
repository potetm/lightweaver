(ns my.job-queue)
(defn start [])
(defn stop [])

(ns my.database)
(defn start [])
(defn stop [])

(ns my.param-store)
(defn start [])
(defn stop [])

(ns my.background-jobs
  (:require
    [my.job-queue :as jq]
    [my.database :as d]
    [my.param-store :as ps]))
(defn start [])
(defn stop [])

(ns my.webserver
  (:require
    [my.database :as d]
    [my.param-store :as ps]))
(defn start [])
(defn stop [])

(ns dev.job-queue)
(defn start [])
(defn stop [])
