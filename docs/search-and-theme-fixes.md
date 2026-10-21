# Search and theme fixes (APK 4)

The drawer theme icon follows the effective Android night configuration: a sun
in light mode and a moon in dark mode, including after activity recreation.

An uncached post query now starts a remote refresh even when automatic refresh
is disabled. Cached queries retain the existing automatic-refresh preference.
Non-successful post API responses become retryable errors instead of successful
empty responses. This applies to each supported engine's post reader.

SearchActivity regression coverage uses the real Retrofit, Room and Paging
pipeline with intercepted synthetic responses. It checks a fresh search with
automatic refresh disabled, activity recreation, an HTTP 503 followed by a
successful retry, and a successful empty response.

During diagnosis, Yande.re's anonymous API returned HTTP 200 with zero posts for
both `muscular` and `muscular rating:safe`. The same endpoint returned posts for
`landscape rating:safe`. The reported `muscular` example therefore did not
reproduce a client-side loss of matching results. Server tag availability can
change; searches use each site's tags rather than general text matching.
