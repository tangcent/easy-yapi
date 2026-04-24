package com.test.adapter

/**
 * Represents an API response in Scala.
 * @tparam T the type of the response body
 * @param code the HTTP status code
 * @param message the response message
 * @param data the response body
 */
case class ScalaApiResponse[T](code: Int, message: String, data: Option[T])

/**
 * Sealed trait representing different result states.
 * Sealed traits are a core Scala pattern for ADTs.
 */
sealed trait ScalaResult

/**
 * Successful result.
 * @param value the success value
 */
case class ScalaSuccess(value: String) extends ScalaResult

/**
 * Failed result.
 * @param error the error message
 * @param cause the underlying exception
 */
case class ScalaFailure(error: String, cause: Option[Throwable] = None) extends ScalaResult

/** Loading state with no data. */
case object ScalaLoading extends ScalaResult

/**
 * Abstract service class with type bounds.
 * @tparam T the entity type, must be Serializable
 */
abstract class ScalaBaseService[T <: Serializable] {
    /**
     * Fetches an entity by ID.
     * @param id the entity identifier
     * @return an Option containing the entity
     */
    def fetchById(id: Long): Option[T]

    /**
     * Saves an entity.
     * @param entity the entity to save
     * @return true if saved successfully
     */
    def save(entity: T): Boolean
}

/**
 * Scala object (singleton) with utility methods.
 * Objects are a Scala-specific construct (no Java equivalent).
 */
object ScalaUtils {
    /**
     * Parses a string to an integer safely.
     * @param s the string to parse
     * @return Some(int) if parseable, None otherwise
     */
    def parseInt(s: String): Option[Int] = {
        try { Some(s.toInt) } catch { case _: NumberFormatException => None }
    }
}

/**
 * Scala enum-like pattern using sealed trait + case objects.
 */
sealed trait ScalaPriority

/** Low priority. */
case object ScalaLow extends ScalaPriority

/** Medium priority. */
case object ScalaMedium extends ScalaPriority

/** High priority. */
case object ScalaHigh extends ScalaPriority

/**
 * Class with companion object demonstrating Scala's companion pattern.
 * @param name the user name
 * @param email the user email
 */
class ScalaUserProfile(val name: String, val email: String)

/** Companion object for ScalaUserProfile. */
object ScalaUserProfile {
    /**
     * Creates a ScalaUserProfile from a display name.
     * @param displayName the user's display name
     * @return a new ScalaUserProfile instance
     */
    def fromDisplayName(displayName: String): ScalaUserProfile =
        new ScalaUserProfile(displayName, "")
}
