package com.portallium.notekeeper.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.portallium.notekeeper.beans.Note;
import com.portallium.notekeeper.beans.Notepad;
import com.portallium.notekeeper.exceptions.DuplicateUsersException;
import com.portallium.notekeeper.exceptions.NoSuchNotepadException;
import com.portallium.notekeeper.utilities.PasswordEncryptionHelper;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Синглтон, сквозь который проходят все запросы всех фрагментов как к базе данных SQLite, так и к Firebase.
 */
public class StorageKeeper {
    private static final String DEFAULT_NOTEPAD_TITLE = "Default";
    private static final String DEFAULT_NOTE_TITLE = "Welcome to Note Keeper!";
    private static final String DEFAULT_NOTE_TEXT = "We hope you will like the app.";

    private static final String FIREBASE_NOTEPAD_ID = "firebase_notepad_id";

    private static StorageKeeper instance;

    /**
     * Все запросы к firebase должны выполняться в порядке очереди, чтобы к моменту старта запроса мы имели результаты всех предыдущих.
     * Это важно, потому что при добавлении заметки в блокнот мы УЖЕ должны знать firebaseId вновь добавленного блокнота.
     */
    private static final Semaphore FIREBASE_SEMAPHORE = new Semaphore(1, true);

    private SQLiteDatabase mDatabase;

    private DatabaseReference mReference;
    private String mCurrentUserFirebaseId;

    private StorageKeeper(Context context) {
        mDatabase = new DatabaseHelper(context.getApplicationContext()).getWritableDatabase();
        mReference = FirebaseDatabase.getInstance().getReference();
    }

    //fixme: запускать этот метод в отдельном потоке. На время его выполнения закрыть БД для модификации другими потоками.
    private void synchronizeNotepads(int localUserId) {

        //сначала собираем в списки все присутствующие в SQLite и Firebase блокноты
        List<Notepad> localNotepadsList = getUserNotepadsAsList(localUserId);
        final List<Notepad> firebaseNotepadsList = new ArrayList<>();
        mReference.child(mCurrentUserFirebaseId).child(DatabaseConstants.Notepads.TABLE_NAME).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                firebaseNotepadsList.add(dataSnapshot.getValue(Notepad.class));
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Firebase conn. error", databaseError.getMessage());
                //видимо, нет соединения. синхронизация закончилась неудачей, и надо как-то выйти из метода synchronize.
                //todo: влепить тост. типа, синхронизация не удалась, живи теперь с этим.
            }
        }); //этот лиснер триггернется один раз для каждой записи, чтобы собрать их все из firebase в список, а потом самоуничтожится.
        //todo: для добавления блокнотов из firebase в sqlite можно использовать threadPoolExecutor. еще: сделать эту операцию единой транзакцией.
    }

    /**
     * Статический метод, используемый для доступа к единственному объекту класса.
     * @param context контекст, из которого вызывается метод. Например, активность.
     * @param currentUserFirebaseId firebase id залогиненного в приложение пользователя.
     * @return экземпляр класса StorageKeeper, через который производится доступ к базе данных.
     */
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
            //todo: окончательно избавиться от наследия прошлой системы авторизации.
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

    /**
     * При вызове метода execute(email) в отдельном потоке запускается метод, вычисляющий локальный id
     * пользователя с данным email. Вызов метода get() вернет этот id.
     */
    public class GetUserIdByEmailTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... email) {
            if (email.length != 1)
                throw new IllegalArgumentException (email.length + " parameters passed, expected 1");
            try {
                return getUserIdByEmail(email[0]);
            }
            catch (DuplicateUsersException ex) {
                Log.e("Getting user ID", ex.getMessage(), ex);
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

    /**
     * При вызове метода execute(notepad) в отдельном потоке запускается метод, добавляющий в базы данных SQLite и
     * Firebase данный блокнот. Вызов метода get() вернет id добавленного блокнота.
     */
    public class AddNotepadToDatabaseTask extends AsyncTask<Notepad, Void, Integer> {
        @Override
        protected Integer doInBackground(Notepad... notepad) {
            if (notepad.length != 1)
                throw new IllegalArgumentException (notepad.length + " parameters passed, expected 1");
            return addNotepadToDatabase(notepad[0]);
        }
    }

    /**
     * Для начала метод проверяет, есть ли блокнот с данным названием у данного пользователя.
     * Если нет, добавляет в БД новый.
     * @return id созданного блокнота
     */
    private int addNotepadToDatabase(final Notepad notepad) {
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

        ContentValues values = parseNotepadToContentValues(notepad);
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

            //добавляем блокнот в firebase, если его там до сих пор нет (он там есть, когда мы его получаем в методе синхронизации). Изменять статус не нужно: он и так needs_addition.
            //todo: все (все!) методы взаимодействия с firebase должны выполняться в отдельных потоках!
            if (notepad.getFirebaseId() == null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        addNotepadToFirebase(mCurrentUserFirebaseId, notepad);
                    }
                }).start();
                //оно работает! а AsyncTask почему-то нет. Хмммммм....
            }

            return newNotepadId;
        }
    }

    /**
     * При вызове метода execute(notepad) в отдельном потоке запускается метод, добавляющий в базы данных SQLite и
     * Firebase данную заметку. Вызов метода get() вернет id добавленной заметки.
     */
    public class AddNoteToDatabaseTask extends AsyncTask<Note, Void, Integer> {
        @Override
        protected Integer doInBackground(Note... note) {
            if (note.length != 1)
                throw new IllegalArgumentException(note.length + " parameters passed, expected 1");
            return addNoteToDatabase(note[0]);
        }
    }

    /**
     *
     * @return id созданной заметки.
     */
    private int addNoteToDatabase(final Note note) {
        ContentValues values = parseNoteToContentValues(note);
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

            //добавляем заметку в firebase, если ее там до сих пор нет. (Она там есть, если мы ее получили из firebase, из метода синхронизации.)
            //Изменять статус не нужно: он и так needs_addition.
            if (note.getFirebaseId() == null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        addNoteToFirebase(mCurrentUserFirebaseId, note);
                    }
                }).start();
            }

            return newNoteId;
        }
    }

    /**
     * При вызове метода execute(userId) в отдельном потоке запускается метод, возвращающий
     * все блокноты пользователя с данным id в виде списка.
     * Вызов метода get() вернет полученный список.
     */
    public class GetUserNotepadsAsListTask extends AsyncTask<Integer, Void, List<Notepad>> {
        @Override
        protected List<Notepad> doInBackground(Integer... userId) {
            if (userId.length != 1)
                throw new IllegalArgumentException (userId.length + " parameters passed, expected 1");
            return getUserNotepadsAsList(userId[0]);
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

    /**
     * При вызове метода execute(userId, notepadId) в отдельном потоке запускается метод, возвращающий
     * в виде списка все заметки пользователя с данным userId из блокнота с данным notepadId.
     * Вызов метода get() вернет полученный список.
     */
    public class GetUserNotesAsListTask extends AsyncTask<Integer, Void, List<Note>> {
        @Override
        protected List<Note> doInBackground(Integer... integers) {
            if (integers.length != 2)
                throw new IllegalArgumentException (integers.length + " parameters passed, expected 2");
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

    /**
     * При вызове метода execute(userId) в отдельном потоке запускается метод, возвращающий
     * все блокноты пользователя с данным id в виде курсора.
     * Вызов метода get() вернет полученный курсор.
     */
    public class GetUserNotepadsAsCursorTask extends AsyncTask<Integer, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Integer... userId) {
            if (userId.length != 1)
                throw new IllegalArgumentException (userId.length + " parameters passed, expected 1");
            return getUserNotepadsAsCursor(userId[0]);
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

    /**
     * При вызове метода execute(note) в отдельном потоке запускается метод, изменяющий значения данной заметки
     * в базах данных SQLite и Firebase на переданные.
     */
    public class UpdateNoteTask extends AsyncTask <Note, Void, Void> {
        @Override
        protected Void doInBackground(Note... note) {
            if (note.length != 1)
                throw new IllegalArgumentException (note.length + " parameters passed, expected 1");
            updateNote(note[0]);
            return null; //выглядит, конечно, ужасно, но что поделать.
        }
    }

    private void updateNote(final Note note) {
        mDatabase.update(
                DatabaseConstants.Notes.TABLE_NAME,
                parseNoteToContentValues(note),
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[] {Integer.toString(note.getId())}
        );

        //дать знать, что требуется обновление в firebase, и внести это обновление
        changeNoteFirebaseStatus(note, DatabaseConstants.FirebaseCodes.NEEDS_UPDATE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateNoteInFirebase(mCurrentUserFirebaseId, note);
            }
        }).start();
    }

    /**
     * При вызове метода execute(notepad) в отдельном потоке запускается метод, изменяющий значения данного блокнота
     * в базах данных SQLite и Firebase на переданные.
     */
    public class UpdateNotepadTask extends AsyncTask<Notepad, Void, Void> {
        @Override
        protected Void doInBackground(Notepad... notepad) {
            if (notepad.length != 1)
                throw new IllegalArgumentException (notepad.length + " parameters passed, expected 1");
            updateNotepad(notepad[0]);
            return null;
        }
    }

    private void updateNotepad(final Notepad notepad) {
        //внести обновление данных о блокноте(название, там менять больше нечего) в локальную бд
        mDatabase.update(
                DatabaseConstants.Notepads.TABLE_NAME,
                parseNotepadToContentValues(notepad),
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ?",
                new String[]{Integer.toString(notepad.getId())}
        );

        //дать знать, что требуется обновление в firebase, и внести это обновление
        changeNotepadFirebaseStatus(notepad, DatabaseConstants.FirebaseCodes.NEEDS_UPDATE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateNotepadInFirebase(mCurrentUserFirebaseId, notepad);
            }
        }).start();
    }


    /**
     * При вызове метода execute(note) в отдельном потоке запускается метод, удаляющий заметку с данным noteId
     * из баз данных SQLite и Firebase.
     */
    public class DeleteNoteTask extends AsyncTask<Note, Void, Void> {
        @Override
        protected Void doInBackground(Note... note) {
            if (note.length != 1)
                throw new IllegalArgumentException (note.length + " parameters passed, expected 1");
            deleteNote(note[0]);
            return null;
        }
    }

    private void deleteNote(final Note note){

        //удалить из sqlite
        mDatabase.delete(
                DatabaseConstants.Notes.TABLE_NAME,
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[]{Integer.toString(note.getId())}
        );
        //todo: так, стоп. как firebase о ней узнает, если из sqlite эта запись удалена? по-хорошему, здесь нужна новая таблица для удаляемых заметок, из которой заметки будут удаляться после того, как будут удалены из firebase...

        //удалить из firebase
        if (note.getFirebaseId() != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    deleteNoteFromFirebase(mCurrentUserFirebaseId, note);
                }
            }).start();
        }
    }

    /**
     * При вызове метода execute(notepadId) в отдельном потоке запускается метод, возвращающий название
     * блокнота с данным notepadId.
     * Вызов метода get() вернет полученное название.
     */
    public class GetNotepadTitleByIdTask extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... notepadId) {
            try {
                if (notepadId.length != 1)
                    throw new IllegalArgumentException (notepadId.length + " parameters passed, expected 1");
                return getNotepadTitleById(notepadId[0]);
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

    private Cursor getCursorByNoteId(int noteId) {
        return mDatabase.query(
                DatabaseConstants.Notes.TABLE_NAME,
                null,
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ?",
                new String[]{Integer.toString(noteId)},
                null, null, null
        );
    }

    private String getFirebaseNoteKeyByCursor(Cursor noteCursor) {
        noteCursor.moveToFirst();
        String firebaseNoteKey = noteCursor.getString(noteCursor.getColumnIndex(DatabaseConstants.Notes.Columns.FIREBASE_ID));
        noteCursor.close();
        return firebaseNoteKey;
    }

    public class GetNotepadFirebaseIdByLocalId extends AsyncTask<Integer, Void, String> {
        @Override
        protected String doInBackground(Integer... notepadId) {
            if (notepadId.length != 1) {
                throw new IllegalArgumentException(notepadId.length + " parameters passed, expected 1");
            }
            return getFirebaseNotepadKeyByCursor(getCursorByNotepadId(notepadId[0]));
        }
    }

    private Cursor getCursorByNotepadId(int notepadId) {
        return mDatabase.query(
                DatabaseConstants.Notepads.TABLE_NAME,
                null,
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ? ",
                new String[]{Integer.toString(notepadId)},
                null, null, null
        );
    }

    private String getFirebaseNotepadKeyByCursor(Cursor notepadCursor) {
        notepadCursor.moveToFirst();
        String firebaseNotepadKey = notepadCursor.getString(notepadCursor.getColumnIndex(DatabaseConstants.Notepads.Columns.FIREBASE_ID));
        notepadCursor.close();
        return firebaseNotepadKey;
    }

    private ContentValues parseNoteToContentValues(Note note) {
        ContentValues values = new ContentValues();
        if (note.getId() != Note.ID_NOT_YET_ASSIGNED) {
            values.put(DatabaseConstants.Notes.Columns.NOTE_ID, note.getId());
        }
        values.put(DatabaseConstants.Notes.Columns.NOTEPAD_ID, note.getNotepadId());
        values.put(DatabaseConstants.Notes.Columns.CREATOR_ID, note.getCreatorId());
        values.put(DatabaseConstants.Notes.Columns.TITLE, note.getTitle());
        values.put(DatabaseConstants.Notes.Columns.TEXT, note.getText());
        values.put(DatabaseConstants.Notes.Columns.CREATION_DATE, note.getCreationDate().getTime());
        if (note.getFirebaseId() != null) {
            values.put(DatabaseConstants.Notes.Columns.FIREBASE_ID, note.getFirebaseId());
            values.put(DatabaseConstants.Notes.Columns.FIREBASE_STATUS, note.getFirebaseStatus());
        }
        return values;
    }

    private ContentValues parseNotepadToContentValues(Notepad notepad) {
        ContentValues values = new ContentValues();
        if (notepad.getId() != Notepad.ID_NOT_YET_ASSIGNED) {
            values.put(DatabaseConstants.Notepads.Columns.NOTEPAD_ID, notepad.getId());
        }
        values.put(DatabaseConstants.Notepads.Columns.CREATOR_ID, notepad.getCreatorId());
        values.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());
        values.put(DatabaseConstants.Notepads.Columns.CREATION_DATE, notepad.getCreationDate().getTime());
        if (notepad.getFirebaseId() != null) {
            values.put(DatabaseConstants.Notepads.Columns.FIREBASE_ID, notepad.getFirebaseId());
            values.put(DatabaseConstants.Notepads.Columns.FIREBASE_STATUS, notepad.getFirebaseStatus());
        }
        return values;
    }

    private void changeNoteFirebaseStatus(Note note, int newStatus) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notes.Columns.FIREBASE_STATUS, newStatus);
        mDatabase.update(
                DatabaseConstants.Notes.TABLE_NAME,
                values,
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[]{Integer.toString(note.getId())}
        );
        note.setFirebaseStatus(newStatus);
    }

    private void changeNotepadFirebaseStatus(Notepad notepad, int newStatus) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notepads.Columns.FIREBASE_STATUS, newStatus);
        mDatabase.update(
                DatabaseConstants.Notepads.TABLE_NAME,
                values,
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ? ",
                new String[]{Integer.toString(notepad.getId())}
        );
        notepad.setFirebaseStatus(newStatus);
    }

    private void updateNotepadFirebaseIdInSQLite(Notepad notepad) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notepads.Columns.FIREBASE_ID, notepad.getFirebaseId());
        mDatabase.update(
                DatabaseConstants.Notepads.TABLE_NAME,
                values,
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ? ",
                new String[]{Integer.toString(notepad.getId())}
        );
    }

    private void updateNoteFirebaseIdInSQLite(Note note) {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notes.Columns.FIREBASE_ID, note.getFirebaseId());
        mDatabase.update(
                DatabaseConstants.Notes.TABLE_NAME,
                values,
                DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                new String[]{Integer.toString(note.getId())}
        );
    }

    /**
     * Добавляет блокнот в базу данных firebase.
     * @param firebaseUserId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param notepad объект класса Notepad, который будет сохранен в базе данных.
     */
    private void addNotepadToFirebase(String firebaseUserId, final Notepad notepad) {
        try {
            FIREBASE_SEMAPHORE.acquire();
            //fixme проблема! пока доступ к семафору не будет получен, диалог не закроется. вот отстой. Что, если для всех обращений к FB создать ЕЩЕ поток?..
        }
        catch (InterruptedException ex) {
            Log.e("adding notepad to FB", ex.getMessage(), ex);
        }
        Log.d("adding notepad to FB", "addition started, thread = " + Thread.currentThread() + ", notepad = " + notepad); //thread: AsyncTask #X
        mReference.child(firebaseUserId).child(DatabaseConstants.Notepads.TABLE_NAME).push().setValue(parseNotepadToMap(notepad), new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                Log.d("New notepad added", "key = " + databaseReference.getKey() + ", current thread = " + Thread.currentThread());//thread: main(!), для каждого! значит ли это, что весь контент лиснеров тоже надо в отдельный поток запихать?..
                changeNotepadFirebaseStatus(notepad, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                notepad.setFirebaseId(databaseReference.getKey());
                updateNotepadFirebaseIdInSQLite(notepad);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    //Важное замечание.
    //1. Если отключить интернет, создать заметку и включить интернет, то заметка сохранится в firebase.
    //2. Если отключить интернет, создать заметку, закрыть приложение, включить интернет и открыть приложение, заметка будет потеряна.
    //Она сохранится в SQLite, но firebase о ней никогда не узнает.
    //вывод. TODO: нужен метод синхронизации: при авторизации проверять наличие в sqlite заметок, которых нет в firebase, и отправлять их туда.
    //TODO: обратный процесс тоже нужен.
    //TODO: как насчет добавления во ViewHolder'ы индикатора синхронизированности? Какой-нибудь логотип напротив данных о заметке (и блокноте тоже.)

    /**
     * Добавляет заметку в базу данных firebase.
     * @param firebaseUserId уникальный идентификатор пользователя, который генерирует firebase.
     *                   {@link} https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser.html#getUid()
     * @param note объект класса Note, который будет сохранен в базе данных.
     */
    private void addNoteToFirebase(String firebaseUserId, final Note note) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("adding note to FB", ex.getMessage(), ex);
        }
        Log.d("adding note to FB", "addition started, thread = " + Thread.currentThread() + ", note = " + note);
        note.setFirebaseNotepadId(getFirebaseNotepadKeyByCursor(getCursorByNotepadId(note.getNotepadId())));
        //fixme: иногда метод просто не успевает получить firebase id блокнота. Если заметка добавляется в блокнот сразу после создания первого, например. Решение проблемы, очевидно, во многопоточности! (еееееее)

        mReference.child(firebaseUserId).child(DatabaseConstants.Notes.TABLE_NAME).push().setValue(parseNoteToMap(note), new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                Log.d("New note added", "key = " + databaseReference.getKey() + ", current thread = " + Thread.currentThread());
                note.setFirebaseId(databaseReference.getKey());
                updateNoteFirebaseIdInSQLite(note);
                changeNoteFirebaseStatus(note, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private void deleteNoteFromFirebase(String firebaseUserId, final Note note) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("delete notepad from FB", ex.getMessage(), ex);
        }
        mReference.child(firebaseUserId).child(DatabaseConstants.Notes.TABLE_NAME).child(note.getFirebaseId()).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("delete notepad from FB", "note deleted from firebase.");
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private void updateNoteInFirebase(String firebaseUserId, final Note note) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("updating note in FB", ex.getMessage(), ex);
        }
        note.setFirebaseNotepadId(getFirebaseNotepadKeyByCursor(getCursorByNotepadId(note.getNotepadId())));

        mReference.child(firebaseUserId).child(DatabaseConstants.Notes.TABLE_NAME).child(note.getFirebaseId()).setValue(parseNoteToMap(note)).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("Firebase update: done", note + " updated in firebase.");
                changeNoteFirebaseStatus(note, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private void updateNotepadInFirebase(String firebaseUserId, final Notepad notepad) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("updating notepad in FB", ex.getMessage(), ex);
        }
        mReference.child(firebaseUserId).child(DatabaseConstants.Notepads.TABLE_NAME).child(notepad.getFirebaseId()).setValue(parseNotepadToMap(notepad)).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("Firebase update: done", notepad + " updated in firebase.");
                changeNotepadFirebaseStatus(notepad, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    /**
     * Этот метод используется для последующей вставки блокнота в Firebase-БД. И больше ни для чего.
     */
    private Map<String, Object> parseNotepadToMap(Notepad notepad) {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());
        map.put(DatabaseConstants.Notepads.Columns.CREATION_DATE, notepad.getCreationDate().getTime());
        return map;
    }

    private Map<String, Object> parseNoteToMap(Note note) {
        Map<String, Object> map = new HashMap<>();
        map.put(DatabaseConstants.Notes.Columns.TITLE, note.getTitle());
        map.put(DatabaseConstants.Notes.Columns.TEXT, note.getText());
        map.put(DatabaseConstants.Notes.Columns.CREATION_DATE, note.getCreationDate().getTime());
        map.put(FIREBASE_NOTEPAD_ID, note.getFirebaseNotepadId());
        return map;
    }
}
