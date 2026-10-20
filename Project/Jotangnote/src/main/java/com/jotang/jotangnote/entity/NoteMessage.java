package com.jotang.jotangnote.entity;

public class NoteMessage {
    private String operation;  // CREATE / UPDATE / DELETE
    private Note note;

    public NoteMessage() {}

    public NoteMessage(String operation, Note note) {
        this.operation = operation;
        this.note = note;
    }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public Note getNote() { return note; }
    public void setNote(Note note) { this.note = note; }
}