# Lightweaver

Topological namespace sorting for making Clojure components with a simple
reduce.

## Current Release
Status: Alpha - subject to change. I'm using this in my production
environments, but there have been enough gotchas during development that I may
need to make changes in the next few weeks.

That said, if you use `plan` during dev time, it cannot break your production
environment.

```clj
com.potetm/lightweaver {:mvn/version "0.0.3"}
```

## Quick Start

```clj
(require '[com.potetm.lightweaver :as lw])

;; plan a system start in 'my.webserver
(lw/plan {::lw/symbol 'lw:start
          ::lw/roots ['my.webserver]})
=> [#'my.database/lw:start
    #'my.param-store/lw:start
    #'my.webserver/lw:start]

;; plan-rev takes the same arguments as plan
(lw/plan-rev {::lw/symbol 'stop
              ::lw/roots ['my.webserver]})
=> [#'my.webserver/stop
    #'my.param-store/stop
    #'my.database/stop]

;; plan a start from the my.background-jobs and my.webserver namespaces
(lw/plan {::lw/symbol 'lw:start
          ::lw/roots '[my.background-jobs my.webserver]})
=> [#'my.database/lw:start
    #'my.job-queue/lw:start
    #'my.param-store/lw:start
    #'my.background-jobs/lw:start
    #'my.webserver/lw:start]

;; plan takes an :xf argument and provides some simple helpers.
;; For example, `lw/namespaces restricts the set of returned namespaces.
(lw/plan {::lw/symbol 'lw:start
          ::lw/roots '[my.background-jobs my.webserver]
          ;; no my.job-queue
          ::lw/xf (lw/namespaces '[my.background-jobs
                                   my.webserver
                                   my.database
                                   my.param-store])})
=> [#'my.database/lw:start
    #'my.param-store/lw:start
    #'my.background-jobs/lw:start
    #'my.webserver/lw:start]

;; Or you can replace a component with a dev-time component
(lw/plan {::lw/symbol 'lw:start
          ::lw/roots '[my.background-jobs my.webserver]
          ::lw/xf (lw/replace '{my.job-queue dev.job-queue})})
=> [#'my.database/lw:start
    #'dev.job-queue/lw:start
    #'my.param-store/lw:start
    #'my.background-jobs/lw:start
    #'my.webserver/lw:start]

;; start takes a map with all the same arguments as plan and passes.
;; the map is passed directly to reduce.
(def sys (lw/start {:env/name "prod"
                    ::lw/roots '[my.background-jobs my.webserver]}))

;; since ::lw/roots was passed to start, you can just reuse it for stop.
(lw/stop sys)

;; convenience macro to start/stop a system
(lw/with-sys [sys {:env/name "prod"
                   ::lw/roots '[my.background-jobs my.webserver]}]
  (do-the-things sys))
```

## Rationale

You do not need this library! This library is nifty. It's perhaps even
necessary for some cases. But all of the goodness comes from writing your own
simple start/stop functions that take a hashmap and return a hashmap, like so:

```clj
(defn start [{args :jdbc/args :as sys}]
  (let [conn (db args)]
    (assoc sys
      ::client {::c conn})))

(defn stop [{{c ::c} ::client}]
  (let [conn (db args)]
    (.close c)
    (dissoc sys ::client)))
```

Once you do that, all you have to do is order your start functions and call
them via `reduce`:

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

That's it. Dependency injection via load ordering. (In this case,
`webserver/start` depends on `job-queue/start`, `db/start`, and `param/start`.)
You don't need anything else. There are no caveats. It will work no matter how
you structure your components or your start functions. You do. not. need. this.
library.

That said.

There is _one_ shortcoming of this approach: It requires manual ordering of
your components. If you, like the above example, have half-a-dozen components,
that task is trivial. However, if you have dozens or hundreds of components,
this approach grows unwieldy.

That's where Lightweaver comes in.

Lightweaver makes a few assumptions:

1. You're putting each of your components in separate namespaces.
2. Each component's initialization and usage functions are together in the namespace.
3. You have a uniform start/stop function in each of your component namespaces.

If you meet all of those criteria, Lightweaver will find and sort your
component start and stop functions using your namespace dependencies to
properly order startup/shutdown.

## Usage

All Lightweaver does is attempt to give you an accurate sorting for your
component start/stop functions. There are two primary ways of using
it: plan/plan-rev and start/stop.

### `plan` and `plan-rev`

`plan` returns a sorted list of vars that can be passed directly to reduce. The
primary use case for this is as a dev-time tool to generate a static list of
vars that you then copy/paste into your code.

`plan` takes a hashmap with the following keys:

* `::lw/symbol` - The var symbol to search for in the graph (default `'lw:start`).
* `::lw/roots` - The root namespaces used to build the graph.
* `::lw/xf` - xform to apply to the sorted namespaces. See also namespaces, replace.

`plan-rev` is the exact same as `plan`, except the returned list is
reverse-topologically sorted (suitable for stopping).

### `start` and `stop`

`start` and `stop` work just like `plan` and `plan-rev`, except instead of
returning the plan, they actually _run_ the plan by reducing over the vars
with the provided hashmap as initial state.

### `with-sys`

As a bonus, there is a macro `with-sys` that accepts `start` arguments and a
body, and ensures proper startup and shutdown.

## General Purpose Namespace Tool

Given that Lightweaver is, at its core, a topological sorting tool, I've opted
to expose its internals for general-purpose use. This means that you can use it
to examine, walk, and sort your namespaces independent of its component
lifecycle management features.

### `graph`

Graph returns a hashmap of namespace -> dependant-namespaces. Take for example
the simple system in the Quick Start:

```clj
(lw/graph 'my.webserver)
=> {my.webserver #{},
    my.database #{my.webserver},
    my.param-store #{my.webserver}}
```

In addition, there is metadata on the graph to indicate what the root node of
this graph is. (See [Graph Cycles](#graph-cycles) below).

```clj
(meta (lw/graph 'my.webserver))
=> {::lw/roots #{my.webserver}}
```

### `topo-sort`

Given a graph supplied by `graph`, topologically sort the graph. Note that this
is a pure function and is not dependent on namespaces whatsoever, so it may be
used as a general-purpose topological sort as long as your graph matches the
form outlined in [`graph`](#graph).

```clj
(lw/topo-sort (graph 'my.webserver))
=> [my.database my.param-store my.webserver]
```

### `topo-compare-keyfn`

Given a graph supplied by `graph`, return a comparator that can be used as the
first argument to `clojure.core/sort-by`.

```clj
(sort-by (topo-compare-keyfn (graph 'my.webserver))
         '[my.webserver my.database])
=> (my.database my.webserver)
```

## Gotchas
### Aliases and Refers *only*

Lightweaver works by looking at the in-memory namespaces loaded by Clojure. It
_will_ attempt to load any namespace you give it, however it does not parse
`ns` declarations from the disk.

The problem is that Clojure does not track namespace requires. It only tracks
aliases and refers. That means in order to use Lightweaver, you _must_ provide
an alias or refer for any dependency you express.

```clj
;; do this
(ns my.namespace
  (:require
    [my.job-queue :as jq]))


;; or this
(ns my.namespace
  (:require
    [my.job-queue :refer [status]]))


;; NOT THIS
(ns my.namespace
  (:require
    my.job-queue))
```

This should never be a problem if you put all your component usage functions in
the same namespace as your component initialization functions, because you'll
have to declare an alias or refer in order to _use_ the component anyways.

### `:as-alias` in root namespaces

It's important to remember that Lightweaver works by examining your namespace
dependencies. If you require an actual namespace using `:as-alias`, Clojure
will not actually load the namespace. This is unimportant in most
circumstances, but you do need to make sure the _root namespaces_ (i.e. the
ones you pass via `:roots`) actually load the namespaces.

Therefore, the rule is: Don't use `:as-alias` for real namespaces in your roots.

### Graph Cycles

Prior to Clojure 1.11, it was difficult-but-possible to create cyclic namespace
dependencies in Clojure. However, with the introduction of `:as-alias` it's not
only possible, but common to create cycles. In addition, Clojure doesn't store
any information in the namespace to differentiate between `:as` and `:as-alias`
dependencies.

Therefore, Lightweaver has to find some way to break namespace cycles.

The way it works is:

1. The root node of a graph is tracked in metadata.
2. If a cycle is detected, all paths from the root nodes to the cycle are
   walked, and the shortest path is selected.
3. Using the shortest path, the last node _prior_ to the cycle is removed from
   the graph and added to the topological sort.

For example, given a graph like so:

```clj
(ns d (:require [a :as-alias a]))
(ns c (:require [d :as d]))
(ns b (:require [c :as c]))
(ns a (:require [c :as c]))
````

And taking a graph with two root nodes `a` and `b`, Lightweaver will find the
following cycles:

```clj
(lw/cycle-paths (lw/merge-graph ['a 'b]))
=> [[b c d a c] [a c d a]]
```

From those, it will select the shortest path (`[a c d a]`), remove the node
_before_ the cycle (`d`) from the graph, and add it to the topological sort
first, resulting in the following sort order:

```clj
(lw/topo-sort (lw/merge-graph ['a 'b]))
=> [d c a b]
```

None of this should matter for normal usage. However if you see any funny
business around cycling, it's _probably_ this algorithm that's throwing you
off.

## Acknowledgements

Big thank you to [Jacob O'Bryant](https://github.com/jacobobryant) for tipping
me off to the pattern of using start/stop functions with a reduce during a
dinner at Clojure Conj 2025! You should check out his framework
[biff](https://github.com/jacobobryant/biff) which uses this pattern for its
component management.

## License

Copyright © 2025 Timothy Pote

Distributed under the Eclipse Public License version 1.0.
