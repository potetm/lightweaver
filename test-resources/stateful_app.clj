(ns stateful.database)
(defn lw:start [sys] (update sys :called conj 'stateful.database/lw:start))
(defn lw:stop [sys] (update sys :called conj 'stateful.database/lw:stop))
(defn boot [sys] (update sys :called conj 'stateful.database/boot))
(defn shutdown [sys] (update sys :called conj 'stateful.database/shutdown))

(ns stateful.webserver
  (:require [stateful.database :as db]))
(defn lw:start [sys] (update sys :called conj 'stateful.webserver/lw:start))
(defn lw:stop [sys] (update sys :called conj 'stateful.webserver/lw:stop))
(defn boot [sys] (update sys :called conj 'stateful.webserver/boot))
(defn shutdown [sys] (update sys :called conj 'stateful.webserver/shutdown))

;; Error-throwing variants
(ns stateful.error-start
  (:require [stateful.database :as db]))
(defn lw:start [sys] (throw (ex-info "start error" {})))
(defn lw:stop [sys] (update sys :called conj 'stateful.error-start/lw:stop))

(ns stateful.error-stop
  (:require [stateful.database :as db]))
(defn lw:start [sys] (update sys :called conj 'stateful.error-stop/lw:start))
(defn lw:stop [sys] (throw (ex-info "stop error" {})))
