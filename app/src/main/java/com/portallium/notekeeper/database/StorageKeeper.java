package com.portallium.notekeeper.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.beans.Notepad;
import com.portallium.notekeeper.exceptions.DuplicateUsersException;
import com.portallium.notekeeper.exceptions.NoSuchNotepadException;
import com.portallium.notekeeper.utilities.PasswordEncryptionHelper;

import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Это синглтон, сквозь который проходят все запросы всех фрагментов как к базе данных SQLite, так и к Firebase.
 */
public class StorageKeeper {
    private static final String DEFAULT_NOTEPAD_TITLE = "Default";
    private static final String DEFAULT_NOTE_TITLE = "Welcome to Note Keeper!";
    private static final String DEFAULT_NOTE_TEXT = "We hope you will like the app.";

    private static StorageKeeper instance;

    private SQLiteDatabase mDatabase;

    private DatabaseReference mReference;
    private String mCurrentUserFirebaseId;

    private StorageKeeper(Context context) {
        mDatabase = new DatabaseHelper(context.getApplicationContext()).getWritableDatabase();
        mReference = FirebaseDatabase.getInstance().getReference();
    }

    public static StorageKeeper getInstance(Context context, String currentUserFirebaseId) {
        if (instance == null) {
            instance = new StorageKeeper(context);
        }
        instance.mCurrentUserFirebaseId = currentUserFirebaseId;
        return instance;
    }



    /**
     * Этот метод не выполняется в отдельном потоке, так как вызываться он будет только методом doInBackground подкласса
     * UserLoginTask класса FirebaseLoginActivity. Для этого метода уже создается отдельный поток.
     * @return id созданного пользователя. Если пользователя создать не удалось, возвращается -1.
     */
    private int addUser(String email, String password)
    {
        try {
            byte[] salt = PasswordEncryptionHelper.generateSalt();
            byte[] encryptedPassword = PasswordEncryptionHelper.getEncryptedPassword(password, salt);
            ContentValues values = new ContentValues();
            values.put(DatabaseConstants.Users.Columns.LOGIN, email);
            values.put(DatabaseConstants.Users.Columns.ENCRYPTED_PASSWORD, encryptedPassword);
            values.put(DatabaseConstants.Users.Columns.SALT, salt);
            mDatabase.insert(DatabaseConstants.Users.TABLE_NAME, null, values);

            try (Cursor newUserCursor = mDatabase.query(
                    DatabaseConstants.Users.TABLE_NAME,
                    new String[] {DatabaseConstants.Users.Columns.ID},
                    DatabaseConstants.Users.Columns.LOGIN + " = ?",
                    new String[] {email},
                    null, null, null)) {
                newUserCursor.moveToFirst();
                int newUserId = newUserCursor.getInt(newUserCursor.getColumnIndex(DatabaseConstants.Users.Columns.ID));
                int newNotepadId = addNotepadToDatabase(new Notepad(newUserId, DEFAULT_NOTEPAD_TITLE)); //Добавляем новому пользователю дефолтный блокнот.
                addNoteToDatabase(new Note(newNotepadId, newUserId, DEFAULT_NOTE_TITLE, DEFAULT_NOTE_TEXT));
                return newUserId;
            }
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            Log.e("addUser", ex.getMessage(), ex.getCause());
            return -1;
        }
    }

    private Cursor getUserCursorByEmail(String email) {
        return mDatabase.query(DatabaseConstants.Users.TABLE_NAME,
                null,
                DatabaseConstants.Users.Columns.LOGIN + " = ?",
                new String[]{email},
                null, null, null);
    }


    public class GetUserIdByEmailTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... email) {
            if (email.length != 1)
                throw new InvalidParameterException (email.length + " parameters passed, expected 1");
            try {
                return getUserIdByEmail(email[0]);
            }
            catch (DuplicateUsersException ex) {
                Log.e("Adding user to DB", ex.getMessage(), ex);
                return -1;
            }
        }
    }

    private int getUserIdByEmail(String email) throws DuplicateUsersException {
        try (Cursor users = getUserCursorByEmail(email)) {
            users.moveToFirst();
            if (users.getCount() == 0) { //такого пользователя на обнаружено
                return addUser(email, email);
                //я оставил этот метод в неприкосновенности, чтобы сохранить обратную совместимость со старым механизмом авторизации.
            } else if (users.getCount() > 1) {
                throw new DuplicateUsersException("Holy-moly, we've got two users with the same login. How did that happen?");
            } else {
                return users.getInt(users.getColumnIndex(DatabaseConstants.Users.Columns.ID));
            }
        }
    }

    public class AddNotepadToDatabaseTask extends AsyncTask<Notepad, Void, Integer> {
        @Override
        protected Integer doInBackground(Notepad... notepads) {
            return addNotepadToDatabase(notepads[0]);
        }
    }

    /**
     * Для начала метод проверяет, есть ли блокнот с данным названием у данного пользователя.
     * Если нет, добавляет в БД новый.
     * @return id созданного блокнота
     */
    private int addNotepadToDatabase(Notepad notepad) {
        //проверяем наличие в SQLite блокнота с данным названием: его быть не должно
        try (Cursor notepadsWithIdenticalNameCursor = mDatabase.query(
                DatabaseConstants.Notepads.TABLE_NAME,
                null,
                DatabaseConstants.Notepads.Columns.CREATOR_ID + " = ? " +
                        " AND " + DatabaseConstants.Notepads.Columns.TITLE + " = ?",
                new String[] {Integer.toString(notepad.getCreatorId()), notepad.getTitle()},
                null, null, null)) {
            if (notepadsWithIdenticalNameCursor.getCount() > 0) {
                return -1;
            }
        }

        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notepads.Columns.CREATOR_ID, notepad.getCreatorId());
        values.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());
        values.put(DatabaseConstants.Notepads.Columns.CREATION_DATE, new Date().getTime());
        mDatabase.insert(DatabaseConstants.Notepads.TABLE_NAME, null, values);

        //Ищем только что созданный блокнот в БД и возвращаем его id
        try (Cursor newNotepadCursor = mDatabase.query(
                DatabaseConstants.Notepads.TABLE_NAME,
                new String [] {DatabaseConstants.Notepads.Columns.NOTEPAD_ID},
                DatabaseConstants.Notepads.Columns.CREATOR_ID + " = ? " +
                        " AND " + DatabaseConstants.Notepads.Columns.TITLE + " = ?",
                new String[] {Integer.toString(notepad.getCreatorId()), notepad.getTitle()},
                null, null, null)){
            newNotepadCursor.moveToFirst();
            int newNotepadId = newNotepadCursor.getInt(newNotepadCursor.getColumnIndex(DatabaseConstants.Notepads.Columns.NOTEPAD_ID));
            notepad.setId(newNotepadId);

            //добавляем блокнот в firebase.
            //важно понимать, что, даже если нет интернета, выполнение завершается. firebase сильно умнее, чем я думал.
            addNotepadToFirebase(mCurrentUserFirebaseId, notepad);

            return newNotepadId;
        }
    }

    public class AddNoteToDatabaseTask extends AsyncTask<Note, Void, Integer> {
        @Override
        protected Integer doInBackground(Note... notes) {
            return addNoteToDatabase(notes[0]);
        }
    }

    /**
     *
     * @return id созданной заметки.
     */
    private int addNoteToDatabase(Note note) {
        //Надо ли проверять наличие в данном блокноте заметки с данным названием? Я бы сказал, что нет.
        //Наверное, это не так важно.
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notes.Columns.NOTEPAD_ID, note.getNotepadId());
        values.put(DatabaseConstants.Notes.Columns.CREATOR_ID, note.getCreatorId());
        values.put(DatabaseConstants.Notes.Columns.TITLE, note.getTitle());
        values.put(DatabaseConstants.Notes.Columns.CREATION_DATE, note.getCreationDate().getTime());
        values.put(DatabaseConstants.Notes.Columns.TEXT, note.getText());
        mDatabase.insert(DatabaseConstants.Notes.TABLE_NAME, null, values);

        try (Cursor newNoteCursor = mDatabase.query(
                DatabaseConstants.Notes.TABLE_NAME,
                new String [] {DatabaseConstants.Notes.Columns.NOTE_ID},
                DatabaseConstants.Notes.Columns.CREATOR_ID + " = ? " +
                        " AND " + DatabaseConstants.Notes.Columns.NOTEPAD_ID + " = ? " +
                        " AND " + DatabaseConstants.Notes.Columns.TITLE + " = ? ",
                new String[] {Integer.toString(note.getCreatorId()), Integer.toString(note.getNotepadId()), note.getTitle()},
                null, null,
                DatabaseConstants.Notes.Columns.CREATION_DATE + " DESC")) {
            //чтобы первой заметкой в курсоре была вновь добавленная.
            newNoteCursor.moveToFirst();
            int newNoteId = newNoteCursor.getInt(newNoteCursor.getColumnIndex(DatabaseConstants.Notes.Columns.NOTE_ID));
            note.setId(newNoteId); //Как только заметка добавляется в БД, она получает id.

            //добавляем заметку в firebase
            addNoteToFirebase(mCurrentUserFirebaseId, note);

            return newNoteId;
        }
    }

    public class GetUserNotepadsAsListTask extends AsyncTask<Integer, Void, List<Notepad>> {
        @Override
        protected List<Notepad> doInBackground(Integer... integers) {
            return getUserNotepadsAsList(integers[0]);
        }
    }

    private List<Notepad> getUserNotepadsAsList(int userId) {
        List<Notepad> notepads = new ArrayList<>();
        try (StorageKeeperCursorWrapper notepadsCursor = new StorageKeeperCursorWrapper(getUserNotepadsAsCursor(userId))){
            notepadsCursor.moveToFirst();
            while (!notepadsCursor.isAfterLast()) {
                notepads.add(notepadsCursor.parseNotepad());
                notepadsCursor.moveToNext();
            }
            return notepads;
        }
    }

    public class GetUserNotesAsListTask extends AsyncTask<Integer, Void, List<Note>> {
        @Override
        protected List<Note> doInBackground(Integer... integers) {
            return getUserNotesAsList(integers[0], integers[1]);
        }
    }

    /**
     * Метод, который возвращает из базы данных список заметок по данным идентификаторам пользователя и блокнота.
     * @param userId id пользователя.
     * @param notepadId id блокнота. если передается 0, возвращается список из всех заметок всех блокнотов.
     * @return список заметок
     */
    private List<Note> getUserNotesAsList(int userId, int notepadId) {
        List<Note> notes = new ArrayList<>();
        try (StorageKeeperCursorWrapper cursorWrapper = notepadId > 0 ?
                new StorageKeeperCursorWrapper(getUserNotesAsCursor(userId, notepadId)) :
                new StorageKeeperCursorWrapper(getUserNotesAsCursor(userId))){
            cursorWrapper.moveToFirst();
            while (!cursorWrapper.isAfterLast()) {
                notes.add(cursorWrapper.parseNote());
                cursorWrapper.moveToNext();
            }
            return notes;
        }

    }

    /**
     * @since commit #6 в курсоре заметки расставлены по УБЫВАНИЮ id, потому что выводить на экран
     * сначала свежие заметки логичнее.
     * @since commit #7 заметки расставляются по убыванию даты создания. на результат это вообще никак не влияет,
     * но таково требование заказчика.
     */
    private Cursor getUserNotesAsCursor(int userId) {
        return mDatabase.query(
                DatabaseConstants.Notes.TABLE_NAME,
                null,
                DatabaseConstants.Notes.Columns.CREATOR_ID + " = ?",
                new String[] {Integer.toString(userId)},
                null, null,
                DatabaseConstants.Notes.Columns.CREATION_DATE + " DESC"
        );
    }

    /**
     * @since commit #6 в курсоре заметки расставлены по УБЫВАНИЮ id, потому что выводить на экран
     * сначала свежие заметки логичнее.
     * @since commit #7 заметки расставляются по убыванию даты создания. на результат это вообще никак не влияет,
     * но таково требование заказчика.
     */
    private Cursor getUserNotesAsCursor(int userId, int notepadId) {
        return mDatabase.query(
                DatabaseConstants.Notes.TABLE_NAME,
                null,
                DatabaseConstants.Notes.Columns.CREATOR_ID + " = ? AND " +
                        DatabaseConstants.Notes.Columns.NOTEPAD_ID + " = ? ",
                new String[] {Integer.toString(userId), Integer.toString(notepadId)},
                null, null,
                DatabaseConstants.Notes.Columns.CREATION_DATE + " DESC"
        );
    }

    public class GetUserNotepadsAsCursorTask extends AsyncTask<Integer, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Integer... integers) {
            return getUserNotepadsAsCursor(integers[0]);
        }
    }

    //TODO: не забывай: все методы для обращения к БД должны быть private, все обертки-классы AsyncTask - public
    /**
     * @since commit #7 в курсоре блокноты расставлены по УБЫВАНИЮ id, потому что выводить на экран
     * сначала свежесозданные блокноты логичнее.
     */
    private Cursor getUserNotepadsAsCursor(int userId) {
        return mDatabase.query(
                DatabaseConstants.Notepads.TABLE_NAME,
                null,
                DatabaseConstants.Notepads.Columns.CREATOR_ID + " = ? ",
                new String[] {Integer.toString(userId)},
                null, null,
                DatabaseConstants.Notepads.Columns.CREATION_DATE + " DESC"
        );
    }

    public class UpdateNoteTask extends AsyncTask <Note, Void, Void> {
        @Override
        protected Void doInBackground(Note... notes) {
            updateNote(notes[0]);
            return null; //выглядит, конечно, ужасно, но что поделать.
        }
    }

    private void updateNote(Note note) {
        mDatabase.update(
                DatabaseConstants.Notes.TABLE_NAME,
                parseNoteToContentValues(note),
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[] {Integer.toString(note.getId())}
        );
        //todo: update in firebase
    }

    public class UpdateNotepadTask extends AsyncTask<Notepad, Void, Void> {
        @Override
        protected Void doInBackground(Notepad... notepads) {
            updateNotepad(notepads[0]);
            return null;
        }
    }

    private void updateNotepad(Notepad notepad) {
        mDatabase.update(
                DatabaseConstants.Notepads.TABLE_NAME,
                parseNotepadToContentValues(notepad),
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ?",
                new String[]{Integer.toString(notepad.getId())}
        );
        //todo: update in firebase
    }

    public class DeleteNoteTask extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... integers) {
            deleteNote(integers[0]);
            return null;
        }
    }

    private void deleteNote(int noteId){
        mDatabase.delete(
                DatabaseConstants.Notes.TABLE_NAME,
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[]{Integer.toString(noteId)}
        );
        //todo: delete from firebase
    }

    public class GetNotepadTitleByIdTask extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... integers) {
            try {
                return getNotepadTitleById(integers[0]);
            }
            catch (NoSuchNotepadException ex) {
                Log.e("Getting notepad title", ex.getMessage(), ex);
                return null;
            }
        }
    }

    private String getNotepadTitleById(int notepadId) throws NoSuchNotepadException {
        try (Cursor cursor = mDatabase.query(
                DatabaseConstants.Notepads.TABLE_NAME,
                new String[]{DatabaseConstants.Notepads.Columns.TITLE},
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ? ",
                new String[]{Integer.toString(notepadId)},
                null, null, null)){
            if (cursor.getCount() < 1)
                throw new NoSuchNotepadException("No notepad with id = " + notepadId);
            cursor.moveToFirst();
            return cursor.getString(cursor.getColumnIndex(DatabaseConstants.Notepads.Columns.TITLE));
        }
    }

    private ContentValues parseNoteToContentValues(Note note) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notes.Columns.NOTE_ID, note.getId());
        values.put(DatabaseConstants.Notes.Columns.NOTEPAD_ID, note.getNotepadId());
        values.put(DatabaseConstants.Notes.Columns.CREATOR_ID, note.getCreatorId());
        values.put(DatabaseConstants.Notes.Columns.TITLE, note.getTitle());
        values.put(DatabaseConstants.Notes.Columns.TEXT, note.getText());
        values.put(DatabaseConstants.Notes.Columns.CREATION_DATE, note.getCreationDate().getTime());
        if (note.getFirebaseId() != null) {
            values.put(DatabaseConstants.Notes.Columns.FIREBASE_ID, note.getFirebaseId());
        }
        //иначе там останется NULL, верно?
        return values;
    }

    private ContentValues parseNotepadToContentValues(Notepad notepad) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notepads.Columns.NOTEPAD_ID, notepad.getId());
        values.put(DatabaseConstants.Notepads.Columns.CREATOR_ID, notepad.getCreatorId());
        values.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());
        values.put(DatabaseConstants.Notepads.Columns.CREATION_DATE, notepad.getCreationDate().getTime());
        if (notepad.getFirebaseId() != null) {
            values.put(DatabaseConstants.Notepads.Columns.FIREBASE_ID, notepad.getFirebaseId());
        }
        return values;
    }

    /**
     * Добавляет блокнот в базу данных firebase.
     * @param firebaseId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param notepad объект класса Notepad, который будет сохранен в базе данных.
     */
    private void addNotepadToFirebase(String firebaseId, final Notepad notepad) {
        mReference.child(firebaseId).child(DatabaseConstants.Notepads.TABLE_NAME).push().setValue(parseNotepadToMap(notepad), new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                Log.d("New notepad added", "key = " + databaseReference.getKey());
                notepad.setFirebaseId(databaseReference.getKey());
                updateNotepad(notepad);
            }
        });
    }
    //todo: добавить два listener'а: на child notes и на notepads
    //TODO: добавить методы обновления и удаления заметок.

    //Важное замечание.
    //1. Если отключить интернет, создать заметку и включить интернет, то заметка сохранится в firebase.
    //2. Если отключить интернет, создать заметку, закрыть приложение, включить интернет и открыть приложение, заметка будет потеряна.
    //Она сохранится в SQLite, но firebase о ней никогда не узнает.
    //вывод. TODO: нужен метод синхронизации: при авторизации проверять наличие в sqlite заметок, которых нет в firebase, и отправлять их туда.
    //TODO: обратный процесс тоже нужен.
    //TODO: как насчет добавления во ViewHolder'ы индикатора синхронизированности? Какой-нибудь логотип напротив данных о заметке (и блокноте тоже.)

    /**
     * Добавляет заметку в базу данных firebase.
     * @param firebaseId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param note объект класса Note, который будет сохранен в базе данных.
     */
    private void addNoteToFirebase(String firebaseId, final Note note) {
        mReference.child(firebaseId).child(DatabaseConstants.Notes.TABLE_NAME).push().setValue(parseNoteToMap(note), new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                Log.d("New note added", "key = " + databaseReference.getKey());
                //я надеюсь, референс указывает на добавленный объект
                note.setFirebaseId(databaseReference.getKey());
                updateNote(note);
            }
        });
    }

    public void deleteNoteFromFirebase(String firebaseId, String firebaseNoteKey) {
        //только вот где это значение firebaseNoteKey взять?
        //первый пришедший в голову вариант - добавить в SQLite колонку "firebaseId", что очень плохая идея.
        //она, конечно, позволит сэкономить некоторые вычислительные мощности, но тем не менее.
        mReference.child(firebaseId).child(DatabaseConstants.Notes.TABLE_NAME).child(firebaseNoteKey).removeValue();
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
