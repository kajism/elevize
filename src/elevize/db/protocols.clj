(ns elevize.db.protocols)

(defprotocol CommonDatabase
  (select [this table where]
    "Selects rows from a table")
  (save! [this table row]
    "Insert or update table row")
  (delete! [this table id]
    "Delete a row with given ID from table"))

(defprotocol ElevizeService
  (login [this user-name pwd]
    "Find and return user by user-name and password. Throw exception otherwise. ")
  (select-users [this where]
    "Select users (without passwd hashes)")
  (save-user [this row]
    "Save user and change passwd if provided"))
