/**
 * Shared, test-only protocol support for the XEP-0313 mam:2#extended feature set (before-id/after-id/ids,
 * flipped pages, and archive metadata - including the latter's appearance inline in a Bind 2 (XEP-0386)
 * {@code <bound/>} response), used by integration tests in this project. Not (yet) supported by Smack's own MAM
 * implementation.
 * <p>
 * Named distinctly from (rather than nested under) Smack's own {@code org.jivesoftware.smackx.mam} package to
 * avoid a split package across the Smack jars and this project's test sources.
 *
 * @see <a href="https://xmpp-interop-testing.github.io/">XMPP Interop Testing framework</a>
 */
package org.jivesoftware.smackx.mam.extended;
