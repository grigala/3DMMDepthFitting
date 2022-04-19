package ch.unibas.cs.gravis.thriftservice.exceptions

final case class NotAMoMoInstanceException(private val message: String = "",
                                           private val cause: Throwable = None.orNull)
    extends Exception(message, cause)
