(ns build
  (:require
    [clojure.edn :as edn]
    [clojure.tools.build.api :as b]
    [deps-deploy.deps-deploy :as deploy]))

(def basis
  (delay
    (b/create-basis {:project "deps.edn"})))


(def lib 'com.potetm/lightweaver)
(def version "0.0.2") ;; Did you update the README?
(def jar-file (str "target/lightweaver-" version ".jar"))
(def sources ["src"])
(def classes "target/classes")


(defn clean [& _]
  (b/delete {:path "target"}))


(defn jar [& _]
  (b/git-process {:git-args ["tag" version "-a" "-m" (str "version" version)]})
  (b/write-pom {:basis @basis
                :lib lib
                :version version
                :class-dir classes
                :src-dirs sources
                :scm {:url "https://github.com/potetm/lightweaver"
                      :connection "scm:git:git://github.com/potetm/lightweaver.git"
                      :developerConnection "scm:git:ssh://git@github.com:potetm/lightweaver.git"
                      :tag version}
                :pom-data [[:licenses
                            [:license
                             [:name "Eclipse Public License - v 1.0"]
                             [:url "https://opensource.org/license/epl-1-0/"]]]]})
  (b/copy-dir {:src-dirs sources
               :target-dir classes})
  (b/jar {:class-dir classes
          :jar-file jar-file}))


(defn deploy [& _]
  (b/git-process {:git-args ["push" "origin" "--tags"]})
  (deploy/deploy {:installer :remote
                  :sign-releases? false
                  :artifact jar-file
                  :pom-file (str classes "/META-INF/maven/com.potetm/lightweaver/pom.xml")
                  :repository (edn/read-string (slurp (str (System/getProperty "user.home")
                                                           "/.clojars")))}))


(defn run [& _]
  (clean)
  (jar)
  (deploy))

(comment
  (clean)
  (jar)
  (deploy)

  ;; Did you update the README?
  (run))

