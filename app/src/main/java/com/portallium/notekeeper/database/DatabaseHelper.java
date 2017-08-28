package com.portallium.notekeeper.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final int VERSION = 1;
    private static final String DATABASE_NAME = "noteKeeperDatabase.db";

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
        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Users.TABLE_NAME + " (" +
                        DatabaseConstants.Users.Columns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Users.Columns.LOGIN + ", " +
                        DatabaseConstants.Users.Columns.ENCRYPTED_PASSWORD + ", " +
                        DatabaseConstants.Users.Columns.SALT + ");");

        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Notepads.TABLE_NAME +
                        " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + " INTEGER, " +
                        DatabaseConstants.Notepads.Columns.TITLE + ", " +
                        DatabaseConstants.Notepads.Columns.CREATION_DATE + ", FOREIGN KEY (" +
                        DatabaseConstants.Notepads.Columns.CREATOR_ID + ") REFERENCES " +
                        DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));");

        sqLiteDatabase.execSQL("CREATE TABLE " + DatabaseConstants.Notes.TABLE_NAME +
                        "( " + DatabaseConstants.Notes.Columns.NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        DatabaseConstants.Notes.Columns.NOTEPAD_ID + " INTEGER, " +
                        DatabaseConstants.Notes.Columns.CREATOR_ID + " INTEGER, " +
                        DatabaseConstants.Notes.Columns.TITLE + ", " +
                        DatabaseConstants.Notes.Columns.CREATION_DATE + ", " +
                        DatabaseConstants.Notes.Columns.TEXT + ", FOREIGN KEY (" +
                        DatabaseConstants.Notes.Columns.NOTEPAD_ID + ") REFERENCES " +
                        DatabaseConstants.Notepads.TABLE_NAME + " (" + DatabaseConstants.Notepads.Columns.NOTEPAD_ID + "), " +
                        "FOREIGN KEY (" + DatabaseConstants.Notes.Columns.CREATOR_ID + ") REFERENCES " +
                        DatabaseConstants.Users.TABLE_NAME + " (" + DatabaseConstants.Users.Columns.ID + "));");
    }

    /**
     * Метод пуст, потому что ситуации с возможным обновлением схемы БД пока не ожидается.
     */
    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {}
}
