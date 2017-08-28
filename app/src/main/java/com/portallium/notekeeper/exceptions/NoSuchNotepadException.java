package com.portallium.notekeeper.exceptions;

/**
 * Исключение, которое будет выброшено при попытке найти в таблице Notepads блокнот с несуществующим id.
 */
public class NoSuchNotepadException extends Exception {

    public NoSuchNotepadException(String message) {
        super(message);
    }
}
