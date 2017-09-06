# NoteKeeper2

Changes in commit #12:
- Now you're able to synchronize your notepads with the cloud! (Handy if you use the app on more than one device.) To do that, tap the left-most icon at the taskbar. Synchronization of notes is yet in development.
- The default notepad and note will not be created for new users eversince. (I've received some negative feedback on this feature.) There is a bug present: the app will crash if you try to create a note whilst there are no notepads in the storage. I don't know why you will ever try to do that, but please don't.

Changes in commits ##10-11:
- All firebase updates' attempts are now automatically organised to a queue, so none will start until there are any  unsucceeded updates.
- All notes that were deleted from local storage but weren't from Firebase are now stored in a separate table, so they will be deleted from Firebase eventually.
- Major code quality improvements.
- Minor performance improvements.

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
