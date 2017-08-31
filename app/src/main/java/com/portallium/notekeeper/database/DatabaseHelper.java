package com.portallium.notekeeper.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int VERSION = 5; //
    private static final String DATABASE_NAME = "noteKeeperDatabase.db";

    private static final String CREATE_TABLE_USERS_V5 = "CREATE TABLE " + DatabaseConstants.Users.TABLE_NAME + " (" +
            DatabaseConstants.Users.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseConstants.Users.Columns.LOGIN + ");";

    private static final String CREATE_TABLE_NOTES_V5 = "CREATE TABLE " + DatabaseConstants.Notes.TABLE_NAME +
            "( " + DatabaseConstants.Notes.Columns.NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseConstants.Notes.Columns.NOTEPAD_ID + " INTEGER, " +
            DatabaseConstants.Notes.Columns.CREATOR_ID + " INTEGER, " +
            DatabaseConstants.Notes.Columns.TITLE + ", " +
            DatabaseConstants.Notes.Columns.CREATION_DATE + ", " +
            DatabaseConstants.Notes.Columns.TEXT + ", " +
            DatabaseConstants.Notes.Columns.FIREBASE_ID + " TEXT DEFAULT NULL, " +
            DatabaseConstants.Notes.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
            DatabaseConstants.FirebaseCodes.NEEDS_ADDITION + ", FOREIGN KEY (" +
            DatabaseConstants.Notes.Columns.NOTEPAD_ID + ") REFERENCES " +
            DatabaseConstants.Notepads.TABLE_NAME + " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + "), " +
            "FOREIGN KEY (" + DatabaseConstants.Notes.Columns.CREATOR_ID + ") REFERENCES " +
            DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));";

    private static final String CREATE_TABLE_DELETED_NOTES_V5 = "CREATE TABLE " + DatabaseConstants.DeletedNotes.TABLE_NAME +
            " (" + DatabaseConstants.DeletedNotes.Columns.FIREBASE_ID + " TEXT PRIMARY KEY);";

    public DatabaseHelper(Context context) {
        super (context, DATABASE_NAME, null, VERSION);
    }

    /**
     * Выполняет запросы для создания SQLite-таблиц, в которых будут храниться заметки и блокноты.
     * @param sqLiteDatabase база данных приложения, в которой будут созданы таблицы.
     */
    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        sqLiteDatabase.execSQL("PRAGMA FOREIGN_KEYS=ON;");

        //create table users...
        sqLiteDatabase.execSQL(CREATE_TABLE_USERS_V5);

        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Notepads.TABLE_NAME +
                        " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + " INTEGER, " +
                        DatabaseConstants.Notepads.Columns.TITLE + ", " +
                        DatabaseConstants.Notepads.Columns.CREATION_DATE + ", " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_ID + " TEXT DEFAULT NULL, " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION + ", FOREIGN KEY (" +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + ") REFERENCES " +
                        DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));");

        sqLiteDatabase.execSQL(CREATE_TABLE_NOTES_V5);

        sqLiteDatabase.execSQL(CREATE_TABLE_DELETED_NOTES_V5);
    }

    /**
     * Обновляет базу данных, если на пользовательском девайсе обнаружена устаревшая схема.
     * В частности, при обновлении с версии 1 до версии 2
     * добавляет колонки firebase_id, потому что их для синхронизации приходится хранить в SQLite.
     */
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notepads.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_ID + " TEXT DEFAULT NULL");
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_ID + " TEXT DEFAULT NULL");
                Log.d("DB schema updated", "from v.1 to v.2");
                //break'a тут теперь нет, чтобы обновление продолжилось до актуальной версии.
            }
            case 2: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notepads.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notepads.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION);
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_STATUS + " INTEGER DEFAULT " +
                        DatabaseConstants.FirebaseCodes.NEEDS_ADDITION);
                //todo: и вот тут тоже вызывается метод synchronize.
                Log.d("DB schema updated", "from v.2 to v.3");
            }
            case 3: {
                sqLiteDatabase.execSQL("ALTER TABLE " + DatabaseConstants.Notes.TABLE_NAME + " ADD COLUMN " +
                        DatabaseConstants.Notes.Columns.FIREBASE_NOTEPAD_ID + " TEXT DEFAULT NULL;");
                Log.d("DB schema updated", "from v.3 to v.4");
                //это было очень, очень неудачное решение.
            }
            case 4: {
                copyUsers(sqLiteDatabase);
                copyNotes(sqLiteDatabase);
                sqLiteDatabase.execSQL(CREATE_TABLE_DELETED_NOTES_V5);
                Log.d("DB schema updated", "from v.4 to v.5");
                break;
            }
            default: {
                throw new IllegalStateException("onUpgrade() called with unknown oldVersion " + oldVersion);
            }
        }
    }

    private void copyUsers(SQLiteDatabase db) {
        //Увы, SQLite не поддерживает удаление колонок! Пойдем другим путем.
        //gather users from current table
        List<ContentValues> usersCopy = new ArrayList<>();
        try (Cursor cursorUsers = db.query(
                DatabaseConstants.Users.TABLE_NAME,
                new String[]{DatabaseConstants.Users.Columns.LOGIN},
                null, null, null, null, null)) {
            cursorUsers.moveToFirst();
            while(!cursorUsers.isAfterLast()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseConstants.Users.Columns.LOGIN, cursorUsers.getString(cursorUsers.getColumnIndex(DatabaseConstants.Users.Columns.LOGIN)));
                usersCopy.add(values);
                cursorUsers.moveToNext();
            }
        }

        //drop current table
        db.execSQL("DROP TABLE " + DatabaseConstants.Users.TABLE_NAME);

        //create new table
        db.execSQL(CREATE_TABLE_USERS_V5);
        for (ContentValues cv : usersCopy) {
            db.insert(DatabaseConstants.Users.TABLE_NAME, null, cv);
        }
    }

    private void copyNotes(SQLiteDatabase db) {
        List<ContentValues> notesCopy = new ArrayList<>();
        //select * from notes
        try (Cursor cursorNotes = db.query(DatabaseConstants.Notes.TABLE_NAME, null, null, null, null, null, null)) {
            cursorNotes.moveToFirst();
            while (!cursorNotes.isAfterLast()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseConstants.Notes.Columns.NOTEPAD_ID, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.NOTEPAD_ID)));
                values.put(DatabaseConstants.Notes.Columns.CREATOR_ID, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.CREATOR_ID)));
                values.put(DatabaseConstants.Notes.Columns.TITLE, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.TITLE)));
                values.put(DatabaseConstants.Notes.Columns.CREATION_DATE, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.CREATION_DATE)));
                values.put(DatabaseConstants.Notes.Columns.TEXT, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.TEXT)));
                values.put(DatabaseConstants.Notes.Columns.FIREBASE_ID, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.FIREBASE_ID)));
                values.put(DatabaseConstants.Notes.Columns.FIREBASE_STATUS, cursorNotes.getString(cursorNotes.getColumnIndex(DatabaseConstants.Notes.Columns.FIREBASE_STATUS)));
                notesCopy.add(values);
                cursorNotes.moveToNext();
            }
        }

        db.execSQL("DROP TABLE " + DatabaseConstants.Notes.TABLE_NAME);

        db.execSQL(CREATE_TABLE_NOTES_V5);
        for (ContentValues cv : notesCopy) {
            db.insert(DatabaseConstants.Notes.TABLE_NAME, null, cv);
        }
    }
}
