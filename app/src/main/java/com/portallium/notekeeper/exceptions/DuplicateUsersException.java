package com.portallium.notekeeper.exceptions;

/**
 * Исключение, которое будет выброшено, если в базе данных SQLite найдутся два пользователя с одним и тем же логином.
 */
public class DuplicateUsersException extends Exception {
    public DuplicateUsersException(String message) {
        super(message);
    }
}