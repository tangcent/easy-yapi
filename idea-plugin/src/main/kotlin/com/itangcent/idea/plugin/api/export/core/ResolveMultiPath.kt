package com.itangcent.idea.plugin.api.export.core

import com.itangcent.common.model.URL
import com.itangcent.common.utils.longest
import com.itangcent.common.utils.shortest

/**
 * This enumeration defines strategies to resolve multiple URLs to a single URL,
 * based on different criteria like the first URL, last URL, longest URL, or shortest URL.
 * It also provides a strategy to return all URLs as-is without any resolution.
 */
enum class ResolveMultiPath {
    FIRST {
        override fun resolve(url: URL): URL = URL.of(url.urls().firstOrNull())
    },
    LAST {
        override fun resolve(url: URL): URL = URL.of(url.urls().lastOrNull())
    },
    LONGEST {
        override fun resolve(url: URL): URL = URL.of(url.urls().longest())
    },
    SHORTEST {
        override fun resolve(url: URL): URL = URL.of(url.urls().shortest())
    },
    ALL {
        override fun resolve(url: URL): URL = url
    };

    /**
     * resolve the given URL based on the implemented strategy
     */
    abstract fun resolve(url: URL): URL
}