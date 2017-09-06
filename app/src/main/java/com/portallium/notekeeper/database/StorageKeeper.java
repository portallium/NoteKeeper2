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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Синглтон, сквозь который проходят все запросы всех фрагментов как к базе данных SQLite, так и к Firebase.
 */
public class StorageKeeper {
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
     * Важно понимать, что этот метод выполняется в том же потоке, что был вызван, т.к. предназначен только для использования
     * в SynchronizerService.
     * @return Список всех блокнотов, полученных из firebase.
     */
    private List<Notepad> getNotepadsListFromFirebase(final int localUserId) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch(InterruptedException ex) {
            Log.e("Synchronization: FB", ex.getMessage(), ex);
        }
        final List<Notepad> firebaseNotepadsList = new ArrayList<>();
        final CountDownLatch countDownLatch = new CountDownLatch(1); //пока список не будет собран, его нет смысла возвращать. За этим здесь CountDownLatch и нужен.
        //don't fixme: если соединение с firebase установить не получится, asyncTask зависнет. плохо ли это? нет. кнопка будет отключена все равно, синхронизация закончится, как только интернет появится.
        mReference.child(mCurrentUserFirebaseId).child(DatabaseConstants.Notepads.TABLE_NAME).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //этот dataSnapshot на самом деле - все блокноты сразу.
                Map<String, Map<String, Object>> notepadMap = (Map<String, Map<String, Object>>) dataSnapshot.getValue();
                for (Map.Entry<String, Map<String, Object>> notepad : notepadMap.entrySet()) {
                    //каждый Entry - это блокнот.
                    String notepadTitle = (String) notepad.getValue().get(DatabaseConstants.Notepads.Columns.TITLE);
                    Date notepadCreationDate = new Date((Long)notepad.getValue().get(DatabaseConstants.Notepads.Columns.CREATION_DATE));
                    String notepadFirebaseKey = notepad.getKey();
                    firebaseNotepadsList.add(new Notepad(localUserId, notepadTitle, notepadCreationDate, notepadFirebaseKey));
                }
                Log.i("Synchronization: FB", "list is packed with data! thread = " + Thread.currentThread());
                countDownLatch.countDown();
                FIREBASE_SEMAPHORE.release();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Firebase conn. error", databaseError.getMessage());
                //синхронизация закончилась неудачей, и надо как-то выйти из метода.
            }
        }); //этот лиснер триггернется один раз, а потом самоуничтожится.
        try {
            countDownLatch.await();
        }
        catch (InterruptedException ex) {
            Log.e("Synchronization: FB", ex.getMessage(), ex);
        }
        Log.i("Synchronization: FB", "list is being returned! thread = " + Thread.currentThread());
        return firebaseNotepadsList;
    }


    /**
     * Этот метод не выполняется в отдельном потоке, так как вызываться он будет только методом doInBackground подкласса
     * UserLoginTask класса FirebaseLoginActivity. Для этого метода уже создается отдельный поток.
     * @return id созданного пользователя. Если пользователя создать не удалось, возвращается -1.
     */
    private int addUser(String email)
    {
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Users.Columns.LOGIN, email);
        mDatabase.insert(DatabaseConstants.Users.TABLE_NAME, null, values);

        //находим нового юзера. блокнот и заметка больше не добавляются. обойдется.
        try (Cursor newUserCursor = getUserCursorByEmail(email)) {
            newUserCursor.moveToFirst();
            return newUserCursor.getInt(newUserCursor.getColumnIndex(DatabaseConstants.Users.Columns.ID));
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
                return addUser(email);
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
        //взглянем правде в глаза: идея с разными названиями блокнотов изначально была плохой.
        //теперь у блокнотов могут быть одинаковые названия. во имя корректной синхронизации. да.

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
            if (notepad.getFirebaseId() == null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        addNotepadToFirebase(notepad);
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

    //метод package-private, потому что используется в SynchronizerService.
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
                updateNoteInFirebase(note);
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
        ContentValues values = new ContentValues();
        values.put(DatabaseConstants.Notepads.Columns.TITLE, notepad.getTitle());

        mDatabase.update(
                DatabaseConstants.Notepads.TABLE_NAME,
                values,
                DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ?",
                new String[]{Integer.toString(notepad.getId())}
        );

        //дать знать, что требуется обновление в firebase, и внести это обновление
        changeNotepadFirebaseStatus(notepad, DatabaseConstants.FirebaseCodes.NEEDS_UPDATE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateNotepadInFirebase(notepad);
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

        if (note.getFirebaseId() == null) {
            Log.d("deleting note from FB", "note with firebaseId == null arrived!");
            try (Cursor thisNote = mDatabase.query(
                    DatabaseConstants.Notes.TABLE_NAME,
                    new String[]{DatabaseConstants.Notes.Columns.FIREBASE_ID},
                    DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                    new String[]{Integer.toString(note.getId())},
                    null, null, null
            )){
                thisNote.moveToFirst();
                note.setFirebaseId(thisNote.getString(thisNote.getColumnIndex(DatabaseConstants.Notes.Columns.FIREBASE_ID)));
            }
        }

        //если заметка была добавлена в firebase, попробовать удалить ее оттуда
        if (note.getFirebaseId() != null) {
            //добавим сначала упоминание о заметке в таблицу DeletedNotes, если удалить из firebase сразу не получится
            //todo: не забыть потом попробовать удалить весь контент этой таблицы при следующем вызове метода синхронизации
            ContentValues values = new ContentValues();
            values.put(DatabaseConstants.DeletedNotes.Columns.FIREBASE_ID, note.getFirebaseId());
            mDatabase.insert(DatabaseConstants.DeletedNotes.TABLE_NAME, null, values);

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
     * @param notepad объект класса Notepad, который будет сохранен в базе данных.
     */
    private void addNotepadToFirebase(final Notepad notepad) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("adding notepad to FB", ex.getMessage(), ex);
        }
        Log.d("adding notepad to FB", "addition started, thread = " + Thread.currentThread() + ", notepad = " + notepad); //thread: AsyncTask #X
        mReference.child(mCurrentUserFirebaseId).child(DatabaseConstants.Notepads.TABLE_NAME).push().setValue(parseNotepadToMap(notepad), new DatabaseReference.CompletionListener() {
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

                //удалить упоминание о заметке из deleted_notes.
                mDatabase.delete(
                        DatabaseConstants.DeletedNotes.TABLE_NAME,
                        DatabaseConstants.DeletedNotes.Columns.FIREBASE_ID + " = ? ",
                        new String[]{note.getFirebaseId()}
                );

                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private void updateNoteInFirebase(final Note note) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("updating note in FB", ex.getMessage(), ex);
        }

        if (note.getFirebaseId() == null) {
            Log.d("updating note in FB", "note with firebaseId == null arrived!");
            try (Cursor thisNote = mDatabase.query(
                    DatabaseConstants.Notes.TABLE_NAME,
                    new String[]{DatabaseConstants.Notes.Columns.FIREBASE_ID},
                    DatabaseConstants.Notes.Columns.NOTE_ID + " = ? ",
                    new String[]{Integer.toString(note.getId())},
                    null, null, null
            )){
                thisNote.moveToFirst();
                note.setFirebaseId(thisNote.getString(thisNote.getColumnIndex(DatabaseConstants.Notes.Columns.FIREBASE_ID)));
            }
        }
        note.setFirebaseNotepadId(getFirebaseNotepadKeyByCursor(getCursorByNotepadId(note.getNotepadId())));

        mReference.child(mCurrentUserFirebaseId).child(DatabaseConstants.Notes.TABLE_NAME).child(note.getFirebaseId()).setValue(parseNoteToMap(note)).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("Firebase update: done", note + " updated in firebase.");
                changeNoteFirebaseStatus(note, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private void updateNotepadInFirebase(final Notepad notepad) {
        try {
            FIREBASE_SEMAPHORE.acquire();
        }
        catch (InterruptedException ex) {
            Log.e("updating notepad in FB", ex.getMessage(), ex);
        }

        //кроме того, может случиться ситуация, когда notepad будет передан с firebaseId = null
        //(например, на первое свое обновление: тогда addNotepadToFirebase еще не успеет выполниться,
        //и в RecyclerView этот блокнот будет висеть без firebaseId
        if (notepad.getFirebaseId() == null) {
            Log.d("updating notepad in FB", "notepad with firebaseId == null arrived!");
            try (Cursor thisNotepad = mDatabase.query(
                    DatabaseConstants.Notepads.TABLE_NAME,
                    new String[]{DatabaseConstants.Notepads.Columns.FIREBASE_ID},
                    DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " = ? ",
                    new String[]{Integer.toString(notepad.getId())},
                    null, null, null
            )){
                thisNotepad.moveToFirst();
                notepad.setFirebaseId(thisNotepad.getString(thisNotepad.getColumnIndex(DatabaseConstants.Notepads.Columns.FIREBASE_ID)));
            }
        }
        //todo: джедай, помни: все, что относится к firebase, должно происходить в методах, название которых кончается на -firebase, неужели так сложно запомнить?!
        //теперь, по идее, firebaseId никогда не будет равен null (если, конечно, я настроил семафор правильно)

        mReference.child(mCurrentUserFirebaseId).
                child(DatabaseConstants.Notepads.TABLE_NAME).
                child(notepad.getFirebaseId()).
                setValue(parseNotepadToMap(notepad)).
                addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d("Firebase update: done", notepad + " updated in firebase.");
                changeNotepadFirebaseStatus(notepad, DatabaseConstants.FirebaseCodes.SYNCHRONIZED);
                FIREBASE_SEMAPHORE.release();
            }
        });
    }

    private Map<Notepad, Future<Integer>> addNotepadsListToDatabase(List<Notepad> notepads) {
        Map<Notepad, Future<Integer>> additionResults = new HashMap<>();
        mDatabase.beginTransaction(); //если что-то вдруг добавить не удастся, мы начнем заново.
        try {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
            for (final Notepad notepad : notepads) {
                Future<Integer> additionResult = executor.submit(new Callable<Integer>() {
                    @Override
                    public Integer call() {
                        return addNotepadToDatabase(notepad);
                    }
                });
                additionResults.put(notepad, additionResult);
            }
            mDatabase.setTransactionSuccessful();
        }
        finally {
            mDatabase.endTransaction();
        }
        return additionResults;
    }

    public boolean synchronizeNotepads(int userLocalId) {
        List<Notepad> firebaseNotepadsList = getNotepadsListFromFirebase(userLocalId);
        //это все заметки из firebase
        Log.i("Synchronization: FB", firebaseNotepadsList.size() + " notepads were downloaded from firebase.");
        List<Notepad> sqliteNotepadsList = getUserNotepadsAsList(userLocalId);
        //это все заметки из sqlite
        Log.i("Synchronization: SQLite", sqliteNotepadsList.size() + " notepads were found in SQLite.");

        List<Notepad> notepadsToAddToSQLite = new ArrayList<>(firebaseNotepadsList);
        notepadsToAddToSQLite.removeAll(sqliteNotepadsList); //список для добавления готов

        Log.i("Synchronization: SQLite", notepadsToAddToSQLite.size() + " notepads need to be added to SQLite.");
        Map<Notepad, Future<Integer>> additionResults = addNotepadsListToDatabase(notepadsToAddToSQLite);

        //добавить все недостающие блокноты в firebase
        //если у нас не получится, добавим в следующий раз. ничего страшного. в локальной БД сохранятся все изменения!
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        for(final Notepad notepad : sqliteNotepadsList) {
            if (notepad.getFirebaseStatus() == DatabaseConstants.FirebaseCodes.NEEDS_ADDITION) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        addNotepadToFirebase(notepad);
                    }
                });
            } else if (notepad.getFirebaseStatus() == DatabaseConstants.FirebaseCodes.NEEDS_UPDATE) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        updateNotepadInFirebase(notepad);
                    }
                });
            }
        }

        //проверим результаты добавления в sqlite
        for (Map.Entry<Notepad, Future<Integer>> result : additionResults.entrySet()) {
            try {
                if (result.getValue().get() < 1) {
                    Log.e("Synchronization: SQLite", "couldn't add " + result.getKey() + " to SQLite.");
                    return false;
                } else {
                    Log.d("Synchronization: SQLite", result.getKey() + " added to SQLite.");
                }
            }
            catch (InterruptedException | ExecutionException ex) {
                Log.e("Synchronization: SQLite", ex.getMessage(), ex);
            }
        }
        //так как я заключил все добавление в транзакцию, теперь все операции, по идее, должны возвращать
        //один и тот же результат. Что делает блок кода выше довольно бессмысленным, он делает много лишнего.
        //todo: синхронизировать заметки таким же образом.
        return true;
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
