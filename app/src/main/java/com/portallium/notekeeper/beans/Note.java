package com.portallium.notekeeper.beans;

import java.io.Serializable;
import java.util.Date;

public class Note implements Serializable {

    private int mId;
    private int mNotepadId;
    private int mCreatorId;
    private String mTitle;
    private Date mCreationDate;
    private String mText;

    private String mFirebaseId;
    private int mFirebaseStatus;

    private Note() {} //этот конструктор нужен firebase. В коде непосредственно не используется.

    public Note(int id, int notepadId, int creatorId, String title, Date creationDate, String text, String firebaseId, int firebaseStatus) {
        this(id, notepadId, creatorId, title, creationDate, text);
        this.mFirebaseId = firebaseId;
        this.mFirebaseStatus = firebaseStatus;
    }

    /**
     * Конструктор заметки, использующийся при парсинге существующей заметки из SQLite.
     * @param id локальный id заметки
     * @param notepadId локальный id блокнота
     * @param creatorId локальный id создателя заметки
     * @param title название заметки
     * @param creationDate unix-время создания заметки (в миллисекундах)
     * @param text текст заметки
     */
    public Note(int id, int notepadId, int creatorId, String title, Date creationDate, String text) {
        this(notepadId, creatorId, title, text);
        this.mId = id;
        this.mCreationDate = creationDate;
    }

    /**
     * Первоначальный конструктор заметки.
     * @param notepadId локальный id блокнота
     * @param creatorId локальный id создателя заметки
     * @param title название заметки
     * @param text текст заметки
     */
    public Note(int notepadId, int creatorId, String title, String text) {
        this.mId = -1;
        this.mNotepadId = notepadId;
        this.mCreatorId = creatorId;
        this.mTitle = title;
        this.mCreationDate = new Date();
        this.mText = text;
    }

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        this.mId = id;
    }

    public int getNotepadId() {
        return mNotepadId;
    }

    public void setNotepadId(int notepadId) {
        this.mNotepadId = notepadId;
    }

    public int getCreatorId() {
        return mCreatorId;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        this.mTitle = title;
    }

    public Date getCreationDate() {
        return mCreationDate;
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        this.mText = text;
    }

    public String getFirebaseId() {
        return mFirebaseId;
    }

    public void setFirebaseId(String firebaseId) {
        this.mFirebaseId = firebaseId;
    }

    public int getFirebaseStatus() {
        return mFirebaseStatus;
    }

    public void setFirebaseStatus(int firebaseStatus) {
        this.mFirebaseStatus = firebaseStatus;
    }

    /**
     * Метод, проверяющий идентичность двух заметок.
     * Заметки считаются идентичными, если их параметры (id, notepadId, creatorId, Text, Title, CreationDate)
     * равны между собой.
     * Метод используется для сравнения заметок, полученных из Firebase и SQLite.
     * @param obj вторая заметка
     * @return true, если заметки идентичны, false иначе
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (obj.getClass() != Note.class)
            return false;
        Note note = (Note) obj;
        return ((!mFirebaseId.isEmpty() && mFirebaseId.equals(note.getFirebaseId()))
                || (this.mId == note.getId()
                && this.mNotepadId == note.getNotepadId()
                && this.mCreatorId == note.getCreatorId()
                && this.mText.equals(note.getText())
                && this.mTitle.equals(note.getTitle())
                && this.mCreationDate.getTime() == note.getCreationDate().getTime()));
    }

    /**
     * Метод подсчитывает хэш-код заметки. Он вычисляется как сумма хэш-кодов полей объекта.
     * @return хэш-код заметки.
     */
    @Override
    public int hashCode() {
        return Integer.valueOf(mId).hashCode() +
                Integer.valueOf(mNotepadId).hashCode() +
                Integer.valueOf(mCreatorId).hashCode() +
                mText.hashCode() +
                mTitle.hashCode() +
                mCreationDate.hashCode();
    }

    @Override
    public String toString() {
        return "Note \"" + mTitle + "\"";
    }
}
