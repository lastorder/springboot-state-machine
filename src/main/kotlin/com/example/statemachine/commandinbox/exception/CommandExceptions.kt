package com.example.statemachine.commandinbox.exception

class DuplicateCommandException(
    message: String,
) : RuntimeException(message)

class ExpiredCommandException(
    message: String,
) : RuntimeException(message)

class CommandNotFoundException(
    message: String,
) : RuntimeException(message)
