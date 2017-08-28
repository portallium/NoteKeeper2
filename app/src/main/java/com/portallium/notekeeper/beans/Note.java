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

    private Note() {} //такой конструктор нужен Firebase

    public Note(int id, int notepadId, int creatorId, String title, Date creationDate, String text) {
        this(id, notepadId, creatorId, title, text);
        this.mCreationDate = creationDate;
    }

    public Note(int id, int notepadId, int creatorId, String title, String text) {
        this(notepadId, creatorId, title, text);
        this.mId = id;
    }

    //-1 в поле id заметки означает, что она еще не добавлена в БД.
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

}
