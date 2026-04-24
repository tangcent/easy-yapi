package com.test.adapter

/**
 * Represents an API response.
 * @param T the type of the response body
 * @property code the HTTP status code
 * @property message the response message
 * @property data the response body
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

/**
 * Sealed class representing different result states.
 */
sealed class Result {
    /**
     * Successful result.
     * @property value the success value
     */
    data class Success(val value: String) : Result()

    /**
     * Failed result.
     * @property error the error message
     * @property cause the underlying exception
     */
    data class Failure(val error: String, val cause: Throwable? = null) : Result()

    /**
     * Loading state with no data.
     */
    data object Loading : Result()
}

/**
 * Service interface with default implementations.
 * @see Result for return types
 */
interface DataService {
    /**
     * Fetches data by ID.
     * @param id the entity identifier
     * @return the result containing the data
     * @throws IllegalArgumentException if id is negative
     */
    fun fetchById(id: Long): Result

    /**
     * Validates the given input.
     * @param input the input string to validate
     * @return true if valid
     */
    fun validate(input: String): Boolean = input.isNotBlank()
}

/**
 * Kotlin enum with properties and methods.
 * @property label the display label
 */
enum class Priority(val label: String) {
    /** Low priority. */
    LOW("Low"),
    /** Medium priority. */
    MEDIUM("Medium"),
    /** High priority. */
    HIGH("High");

    /**
     * Checks if this priority is at least the given level.
     * @param other the priority to compare against
     * @return true if this >= other
     */
    fun isAtLeast(other: Priority): Boolean = this.ordinal >= other.ordinal
}

/**
 * Companion object with factory methods.
 */
class UserProfile private constructor(val name: String, val email: String) {
    companion object {
        /**
         * Creates a UserProfile from a display name.
         * @param displayName the user's display name
         * @return a new UserProfile instance
         */
        fun fromDisplayName(displayName: String): UserProfile =
            UserProfile(displayName, "")
    }
}
