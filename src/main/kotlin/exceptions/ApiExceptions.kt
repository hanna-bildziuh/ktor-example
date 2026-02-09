package exceptions

class ValidationException(message: String) : RuntimeException(message)

class AuthenticationException(message: String) : RuntimeException(message)

class AuthenticationRequiredException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)
