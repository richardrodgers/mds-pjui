# mds-pjui - a public web user interface for mds repositories #

PJ UI is a web application exposing mds content to unauthenticated users. 
It has only discovery and access functions, viz. Community, Collection, Item and Bitstream splash pages,
(collection) browse and search pages, and bitstream downloads. Administrative actions, submission, etc
will be provided in a different web UI.

Items have both a brief and full metadata page, and their HTML contains
additional metadata affordances using:

 * DCMI dc-html meta tags
 * Google Scholar 'citation' meta tags
 * Schema.org markup in RDFa 1.1

## Warning! - not ready for actual use ##

PJ = Play Java, i.e. stack is PlayFramework in Java.

Design goals include compact, legible, maintainable code (currently entire server and template code is about 500 lines),
and ease of deployability/scalability/SOA along the lines of the [12-factor app](http://12factor.net/). For this reason,
all _backing services_ (such as search) will be realized with external service providers, rather than local Solr.
Similarly, the UI is stateless, so horizontal scaling can be achieved by simply adding more apps.

Instructions will be added on how to 'provision' the app with external services.

## TODO ##

 * styled 404, 500? page X
 * implement page caching X
 * style pages
 * SaaS (indexDen) search box /results page X
 * Google analytics
 * more Metrics (internal)
 * app/log monitoring/exception detection, etc (Loggly, New Relic?, etc)
