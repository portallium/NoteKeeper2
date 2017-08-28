package com.portallium.notekeeper.database;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.beans.Notepad;

import java.util.HashMap;
import java.util.Map;

public class FirebaseDatabaseHelper {

    private static FirebaseDatabaseHelper instance;

    private DatabaseReference mReference;

    private FirebaseDatabaseHelper () {
        mReference = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * @return единственный в своем роде объект класса FirebaseDatabaseHelper, через который будут производиться все запросы к базе данных firebase.
     */
    public static FirebaseDatabaseHelper getInstance () {
        if (instance == null)
            instance = new FirebaseDatabaseHelper();
        return instance;
    }

    /**
     * Добавляет блокнот в базу данных firebase.
     * @param firebaseId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param notepad объект класса Notepad, который будет сохранен в базе данных.
     */
    public void addNotepadToDatabase(String firebaseId, Notepad notepad) {
        mReference.child(firebaseId).child(DatabaseConstants.Notepads.TABLE_NAME).push().setValue(parseNotepadToMap(notepad));
    }
    //todo: добавить два listener'а: на child notes и на notepads
    //TODO: добавить методы обновления и удаления заметок.

    //Важное замечание.
    //1. Если отключить интернет, создать заметку и включить интернет, то заметка сохранится в firebase.
    //2. Если отключить интернет, создать заметку, закрыть приложение, включить интернет и открыть приложение, заметка будет потеряна.
    //Она сохранится в SQLite, но firebase о ней никогда не узнает.
    //вывод. TODO: нужен метод синхронизации: при авторизации проверять наличие в sqlite заметок, которых нет в firebase, и отправлять их туда.
    //TODO: обратный процесс тоже нужен.

    /**
     * Добавляет заметку в базу данных firebase.
     * @param firebaseId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param note объект класса Note, который будет сохранен в базе данных.
     */
    public void addNoteToDatabase(String firebaseId, Note note) {
        mReference.child(firebaseId).child(DatabaseConstants.Notes.TABLE_NAME).push().setValue(parseNoteToMap(note));
    }

    private Map<String, Object> parseNotepadToMap(Notepad notepad) {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseConstants.Notepads.Columns.NOTEPAD_ID, notepad.getId());
        map.put(DatabaseConstants.Notepads.Columns.CREATOR_ID, notepad.getCreatorId());
        map.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());
        map.put(DatabaseConstants.Notepads.Columns.CREATION_DATE, notepad.getCreationDate().getTime());
        return map;
    }

    private Map<String, Object> parseNoteToMap(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseConstants.Notes.Columns.NOTE_ID, note.getId());
        map.put(DatabaseConstants.Notes.Columns.NOTEPAD_ID, note.getNotepadId());
        map.put(DatabaseConstants.Notes.Columns.CREATOR_ID, note.getCreatorId());
        map.put(DatabaseConstants.Notes.Columns.TITLE, note.getTitle());
        map.put(DatabaseConstants.Notes.Columns.TEXT, note.getText());
        map.put(DatabaseConstants.Notes.Columns.CREATION_DATE, note.getCreationDate().getTime());
        return map;
    }
}
