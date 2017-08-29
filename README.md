# NoteKeeper2

Changes in commit #9:
- Every update or deletion of a note or a notepad is now backed up in Firebase if the device is connected to the Internet.
- Notes' and notepads' firebase IDs are now stored in the SQLite DB in order to implement constant synchronization of SQLite and Firebase. (Coming in future releases.)
- More Javadoc.
- Integration with Fabric's Crashlytics is added.
- Minor code quality improvements.
- Minor bug fixes.

Changes in Commit #8:
- New authorisation mechanism. 
- Every newly created note and notepad are henceforth added to Firebase. (The integration process is yet far from completion.)
- Minor code quality improvements.
- Minor bug fixes.
