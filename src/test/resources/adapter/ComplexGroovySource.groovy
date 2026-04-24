package com.test.adapter

/**
 * A Groovy trait providing logging capability.
 * Traits are a Groovy-specific construct (not available in Java).
 * @since 2.3
 */
trait Loggable {
    /**
     * Logs a message at info level.
     * @param message the message to log
     */
    void logInfo(String message) {
        println("[INFO] " + message)
    }
}

/**
 * A Groovy service demonstrating language-specific features.
 *
 * Features tested:
 * - Dynamic typing with {@code def}
 * - Closures as method parameters
 * - Property-style fields (auto getter/setter)
 * - Groovy truth and safe navigation
 *
 * @author GroovyDev
 * @see Loggable
 */
class GroovyComplexService implements Loggable {
    /** The service name. */
    String serviceName

    /** The service version. */
    int version = 1

    /**
     * Processes items using a closure.
     * Closures are a core Groovy feature not available in Java.
     * @param items the list of items to process
     * @param processor the closure to apply to each item
     * @return the processed results
     */
    List process(List items, Closure processor) {
        return items.collect { processor(it) }
    }

    /**
     * Finds an item using dynamic typing.
     * The {@code def} keyword allows dynamic return type.
     * @param criteria the search criteria map
     * @return the found item or null
     */
    def findItem(Map criteria) {
        return criteria.get("default")
    }

    /**
     * Demonstrates Groovy's safe navigation operator.
     * @param input the nullable input
     * @return the length or -1 if null
     */
    int safeLength(String input) {
        return input?.length() ?: -1
    }
}

/**
 * Groovy enum with custom methods.
 * @see GroovyComplexService
 */
enum GroovyStatus {
    /** Active status. */
    ACTIVE,
    /** Inactive status. */
    INACTIVE,
    /** Pending review. */
    PENDING

    /**
     * Checks if this status is terminal.
     * @return true if the status is final
     */
    boolean isTerminal() {
        return this == ACTIVE || this == INACTIVE
    }
}
