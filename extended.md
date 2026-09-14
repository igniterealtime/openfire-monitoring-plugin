# XEP-0313 "Extended" Namespace (`urn:xmpp:mam:2#extended`) — Gap Analysis

This document analyzes the current state of the Openfire Monitoring Plugin's XEP-0313 (Message
Archive Management) implementation against the requirements of the **extended namespace**
(`urn:xmpp:mam:2#extended`), as introduced in XEP-0313 v0.7.0 (2020-03-20) and carried forward in
later versions.

Relevant code:

- `src/java/com/reucon/openfire/plugin/archive/xep0313/IQQueryHandler.java` — shared query handling
  logic for all MAM namespace versions (`getFeatures()` at line 634, `buildSupportedFieldsResult()`
  at line 594, `getSupportedFieldVariables()` at line 622, the `index`-based-paging rejection at
  lines 240-243).
- `src/java/com/reucon/openfire/plugin/archive/xep0313/IQQueryHandler0.java` / `IQQueryHandler1.java`
  / `IQQueryHandler2.java` — namespace-specific subclasses (`urn:xmpp:mam:0/1/2`).
- `src/java/com/reucon/openfire/plugin/archive/xep0313/Xep0313Support.java` / `Xep0313Support1.java`
  / `Xep0313Support2.java` — disco feature/module registration (`AbstractXepSupport`).
- `src/java/com/reucon/openfire/plugin/archive/xep0313/QueryRequest.java` — parses the incoming
  `<query/>` element (`queryid`, data form, RSM `<set/>`).
- `src/java/com/reucon/openfire/plugin/archive/xep0059/XmppResultSet.java` — XEP-0059 Result Set
  Management (RSM) implementation used for paging (`after`, `before`, `max`, `index`).
- `src/java/com/reucon/openfire/plugin/archive/PersistenceManager.java` (interface) and
  `impl/JdbcPersistenceManager.java` (lines 281-390) / `impl/MucMamPersistenceManager.java` —
  message retrieval and paging logic, including translation of RSM `before`/`after` values to
  either database IDs or XEP-0359 stable/stanza IDs (`useStableID`).

## What the spec requires for "extended" support

Per XEP-0313 (§7 "Determining support" and §4.1 "Filtering results"):

1. **Disco feature advertisement.** A server that hosts archives and supports MAM MUST advertise
   both `urn:xmpp:mam:2` and `urn:xmpp:mam:2#extended` in response to Service Discovery (XEP-0030)
   requests to archiving JIDs. The `#extended` feature **MUST NOT** be advertised without also
   advertising `mam:2`.
2. **`before-id` and `after-id` data-form fields.** Two additional (optional-for-client,
   mandatory-for-server) filter fields, distinct from RSM's `before`/`after`: they reference a
   message ID directly, are not tied to "paging direction" semantics the way RSM `before`/`after`
   are, and their simultaneous use is well-defined (return messages between the two IDs).
3. **`ids` data-form field (list-multi).** Allows requesting a specific set of messages by ID —
   this underlies **single-item retrieval** (query with only `ids` set, no `start`/`end`/`with`).
4. **Flipped pages (`<flip-page/>`).** An empty child element of `<query/>` that a client can
   include to request that, when paging backwards (i.e. using `before`/`before-id`), the returned
   page of messages is still delivered in **forward chronological order** rather than the reverse
   order implied by backwards paging.
5. **Archive metadata query (§5).** When an archive advertises `urn:xmpp:mam:2#extended`, it MUST
   also support an `<iq type="get"/>` sent directly to the archive's JID with an empty `<metadata
   xmlns="urn:xmpp:mam:2"/>` payload (no `<query/>` wrapper — this is a separate, dedicated IQ
   payload distinct from the field-discovery `<iq type="get"><query/></iq>` used elsewhere). The
   server replies with a `<metadata/>` element containing `<start id="..." timestamp=".../>` and
   `<end id="..." timestamp=".../>` children describing the first and last archived message
   (omitted entirely if the archive is empty).
6. Servers advertising `#extended` MUST implement 1–5 together; a partial implementation must not
   advertise the feature.

## Current implementation status

| Extended-namespace requirement                              | Status | Notes |
|---------------------------------------------------------------|--------|-------|
| Advertise `urn:xmpp:mam:2#extended` in disco#info             | ❌ Missing | `IQQueryHandler.getFeatures()` (line 634-641) only returns `NAMESPACE` (e.g. `urn:xmpp:mam:2`) and, conditionally, `urn:xmpp:fulltext:0`. No `#extended` feature is ever added, for any namespace version. |
| `before-id` field (data form)                                  | ❌ Missing | Not declared in `buildSupportedFieldsResult()` (line 594-613) nor in `getSupportedFieldVariables()` (line 622-631, allow-list used to reject "unsupported" fields). Not parsed in `retrieveMessages()` (line 352-493) or `QueryRequest`. |
| `after-id` field (data form)                                   | ❌ Missing | Same as above. |
| `ids` field (data form, list-multi) / single-item retrieval    | ❌ Missing | No handling anywhere; a query containing only `ids` would currently either be rejected (`bad_request`, if `PROP_ALLOW_UNRECOGNIZED_SEARCH_FIELDS` is false) or silently ignored (if true), rather than returning the requested messages. |
| `<flip-page/>` support                                          | ❌ Missing | `QueryRequest` (lines 20-40) parses only `queryid`, the data form `<x/>`, and the RSM `<set/>` element; it has no knowledge of a `<flip-page/>` sibling element. `XmppResultSet`/persistence layer have no "flip" concept — backwards paging (`pagingBackwards`, `XmppResultSet.java` line 25/41-43) always returns results in reverse-chronological (page) order via `PaginatedMessageDatabaseQuery`/`PaginatedMessageLuceneQuery.getPage(after, before, max, isPagingBackwards)`. |
| Equivalent RSM `before`/`after` semantics (§9)                  | ✅ Partially present | `JdbcPersistenceManager.findMessages()` (lines 298-334) already resolves RSM `after`/`before` to a database ID or, for `mam:2` (`useStableID=true`), a XEP-0359 stable/origin ID via `ConversationManager.getMessageIdForStableId`, and raises `item-not-found` if unresolved — this is functionally close to (but not the same protocol surface as) `before-id`/`after-id`. |
| RSM `index`-based paging                                        | ❌ Explicitly rejected | Lines 240-243 of `IQQueryHandler.java` return `feature-not-implemented` for any request specifying an RSM `index`; comment at line 300-303 of `JdbcPersistenceManager.java` says "TODO re-enable search-by-index" — this is not part of `#extended` itself but is related infrastructure for paging. |
| `usesUniqueAndStableIDs()` (XEP-0359 stanza-id prerequisite for `mam:2`) | ✅ Present | `IQQueryHandler2.usesUniqueAndStableIDs()` returns `true`; `IQQueryHandler0`/`IQQueryHandler1` return `false`. This is a prerequisite the extended namespace builds on (message IDs referenced by `before-id`/`after-id`/`ids` should be stable IDs), and is already satisfied for `mam:2`. |
| Archive metadata query (§5, `<iq type="get"><metadata xmlns="urn:xmpp:mam:2"/></iq>`) | ❌ Missing | `IQQueryHandler` is only registered (via `AbstractIQHandler`/`IQHandler`) for a child element named `query` in the MAM namespace (constructor `super(moduleName, "query", namespace)`, line 80); there is no handler for a top-level `metadata` child element in the same namespace, so such a request would currently go unanswered (or fall through to a generic "feature not implemented"/`service-unavailable` response from the server's IQ dispatcher) rather than returning `<metadata/>` with `<start/>`/`<end/>` info. This requires a dedicated `IQHandler` registration (e.g. via `Xep0313Support2`) in addition to the existing query handler. |

## Summary of gaps

The plugin has **no implementation of the `urn:xmpp:mam:2#extended` namespace at all**:

1. It is never advertised via Service Discovery.
2. The `before-id`, `after-id`, and `ids` data-form fields are not recognized, declared, or acted
   upon.
3. Single-item retrieval (querying by `ids` alone) is unsupported.
4. `<flip-page/>` is not parsed or honored.
5. The dedicated archive metadata query (§5, `<iq type="get"><metadata/></iq>`) is not handled by
   any registered `IQHandler`.

The only closely related capability that already exists is RSM-based `before`/`after` paging
(including resolution against XEP-0359 stable IDs), which the spec itself notes is a near
equivalent for `before-id`/`after-id` — but this does not satisfy the letter of the `#extended`
feature contract (different element/field, different "before implies backwards paging" semantics,
undefined combined use of RSM `before`+`after`) and does not cover `ids` or `<flip-page/>` at all.

## Suggested scope for implementation (not yet done)

1. Add `before-id`, `after-id`, and `ids` (list-multi) fields to `buildSupportedFieldsResult()` and
   `getSupportedFieldVariables()` in `IQQueryHandler.java`, restricted to namespace versions that
   support stable IDs (i.e. `mam:2`, via `usesUniqueAndStableIDs()`).
2. Parse these fields in `retrieveMessages()` / `QueryRequest`, add corresponding parameters (or a
   richer query-criteria object) to `PersistenceManager.findMessages(...)` and its
   `JdbcPersistenceManager`/`MucMamPersistenceManager` implementations, translating message/stable
   IDs to underlying database identifiers similarly to the existing RSM `before`/`after` handling.
3. Parse `<flip-page/>` in `QueryRequest` and thread a "flip" flag through to
   `PaginatedMessageDatabaseQuery`/`PaginatedMessageLuceneQuery` so that backwards-paged results
   can optionally be returned in forward chronological order.
4. Implement single-item retrieval: when a query specifies only `ids` (no `start`/`end`/`with`),
   return exactly the matching archived messages (by stable ID) regardless of date range limits.
5. Add a new `IQHandler` (registered alongside `IQQueryHandler2`, e.g. from `Xep0313Support2`) for
   the child element `metadata` in the `urn:xmpp:mam:2` namespace, that answers `<iq type="get"/>`
   requests addressed to an archive JID with a `<metadata/>` element containing `<start/>`/`<end/>`
   (id + timestamp) describing the oldest/newest archived message for that archive, sourced from
   `PersistenceManager`/`JdbcPersistenceManager` (e.g. min/max message id and timestamp for the
   owner), omitting `<start/>`/`<end/>` if the archive is empty.
6. Advertise `urn:xmpp:mam:2#extended` from `IQQueryHandler2.getFeatures()` (or an override thereof)
   once 1–5 are implemented, ensuring it is only advertised together with `urn:xmpp:mam:2` and only
   for handlers where `usesUniqueAndStableIDs()` is `true`.
7. Add/extend tests covering: disco feature advertisement, `before-id`/`after-id` filtering,
   `ids`-only single-item retrieval, `<flip-page/>` ordering, the `<metadata/>` query response, and
   error handling for unresolved IDs (`item-not-found`), mirroring the existing RSM before/after
   test coverage.

*This file is an analysis artifact only; it is intentionally left uncommitted and does not modify
any plugin source code.*
