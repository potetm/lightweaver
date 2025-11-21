(ns d (:require [a :as-alias a]))
(ns c (:require [d :as d]))
(ns b (:require [c :as c]))
(ns a (:require [c :as c]))
