# Lightweaver

Clojure components with a simple reduce.

## Quick Start

```clj
(require '[com.potetm.lightweaver :as lw])

;; plan a system start in the current namespace
(lw/plan {:symbol 'start})

;; plan-rev takes the same arguments as plan
(lw/plan-rev {:symbol 'stop})

;; plan a start from the my.background-jobs and my.webserver namespaces
(lw/plan {:symbol 'start
          :roots '[my.background-jobs my.webserver]})

;; plan a start with a restricted list of namespaces
(lw/plan {:symbol 'start
          :roots '[my.background-jobs my.webserver]
          :namespaces [my.background-jobs
                       my.webserver
                       my.database
                       my.param-store
                       my.job-queue]})

;; replace a component with a dev-time component
(lw/plan {:symbol 'start
          :replace '{my.job-queue dev.job-queue}})

;; start takes all the same arguments as plan, plus an init value.
(def sys (lw/start {:init {:env/name "prod"}}))

;; same for stop
(lw/stop {:init sys})

;; convenience macro to start/stop a system
(lw/with-sys [sys {:init {:env/name "prod}}]
  (do-the-things sys))
```

## Rationale

Managing Clojure components ought to be managed via a simple hashmap with a
reduce:

```clj
(defn start [config]
  (reduce (fn [sys start]
            (start sys))
          config
          [param/start
           db/start
           job-queue/start
           jobs/start
           webserver/start]))
```

Each `start` function takes the current state, and returns an updated state
map. For example:

```clj
(defn start [{args :jdbc/args :as sys}]
  (let [conn (db args)]
    (assoc sys
      ::client {::c conn})))
```

Dependency injection happens via load ordering. In this case, `webserver/start`
depends on `job-queue/start`, `db/start`, and `param/start`.

The only shortcoming of this approach is it requires manual ordering of your
components. If, like the above example, you have half-a-dozen components, that
task is trivial. However, if you have dozens or hundreds of components, this
approach grows unwieldy.

That's where Lightweaver comes in!

Lightweaver makes a few assumptions:

1. You're putting each of your components in separate namespaces.
2. Each component's initialization and usage functions are together in the namespace.
3. You have a uniform start/stop function in each of your component namespaces.

If you meet all of those criteria, Lightweaver will find and sort your
component start and stop functions using your namespace dependencies to
properly order startup/shutdown.

## Usage

There are two primary ways of using Lightweaver: plan/plan-rev and start/stop.

### `plan` and `plan-rev`

`plan` returns a sorted list of vars that can be passed directly to reduce. The
primary use case for this is as a dev-time tool to generate a static list of
vars that you then copy/paste into your code.

`plan` takes a hashmap with the following keys:

* `:symbol` - The var symbol to search for in the graph (e.g. 'start).
* `:roots` - (Optional) The root namespaces used to build the graph. Defaults to `[*ns*]`.
* `:namespaces` - (Optional) Restrict plan to these namespaces.
* `:replace` - (Optional) A hashmap of `{'original.namespace 'replacement.namespace}`.

`plan-rev` is the exact same as `plan`, except the returned list is
reverse-topologically sorted (suitable for stopping).

### `start` and `stop`

`start` and `stop` work just like `plan` and `plan-rev`, except instead of
returning the plan, they actually _run_ the plan by reducing over the vars
using the provided `:init` value.

### `with-sys`

As a bonus, there is a macro `with-sys` that accepts `start` arguments and a
body, and ensures proper startup and shutdown.

## License
Copyright © 2025 Timothy Pote

Distributed under the Eclipse Public License version 1.0.
