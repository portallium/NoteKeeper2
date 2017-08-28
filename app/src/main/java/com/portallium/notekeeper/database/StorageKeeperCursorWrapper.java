package com.portallium.notekeeper.database;

import android.database.Cursor;
import android.database.CursorWrapper;

import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.beans.Notepad;

import java.util.Date;


public class StorageKeeperCursorWrapper extends CursorWrapper {
    public StorageKeeperCursorWrapper (Cursor cursor) {
        super(cursor);
    }

    /**
     * Превращает ряд таблицы, на который указывает курсор, в объект класса Notepad.
     * @return полученный объект класса Notepad.
     */
    public Notepad parseNotepad () {
        int id = getInt(getColumnIndex(DatabaseConstants.Notepads.Columns.NOTEPAD_ID));
        String title = getString(getColumnIndex(DatabaseConstants.Notepads.Columns.TITLE));
        Date creationDate = new Date(getLong(getColumnIndex(DatabaseConstants.Notepads.Columns.CREATION_DATE)));
        int creatorId = getInt(getColumnIndex(DatabaseConstants.Notepads.Columns.CREATOR_ID));
        return new Notepad(creatorId, title, creationDate, id);
    }

    /**
     * Превращает ряд таблицы, на который указывает курсор, в объект класса Note.
     * @return полученный объект класса Note.
     */
    public Note parseNote () {
        int id = getInt(getColumnIndex(DatabaseConstants.Notes.Columns.NOTE_ID));
        int notepadId = getInt(getColumnIndex(DatabaseConstants.Notes.Columns.NOTEPAD_ID));
        int creatorId = getInt(getColumnIndex(DatabaseConstants.Notes.Columns.CREATOR_ID));
        String title = getString(getColumnIndex(DatabaseConstants.Notes.Columns.TITLE));
        String text = getString(getColumnIndex(DatabaseConstants.Notes.Columns.TEXT));
        Date creationDate = new Date(getLong(getColumnIndex(DatabaseConstants.Notes.Columns.CREATION_DATE)));
        return new Note (id, notepadId, creatorId, title, creationDate, text);
    }
}
