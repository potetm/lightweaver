(ns mc.one.c (:require [mc.one.a :as-alias a]))
(ns mc.one.b (:require [mc.one.c :as c]))
(ns mc.two.c (:require [mc.one.a :as-alias a]))
(ns mc.two.b (:require [mc.two.c :as c]))

(ns mc.one.a (:require [mc.one.b :as b]
                       [mc.two.b :as tb]))
(ns mc.two.a (:require [mc.one.b :as b]))
