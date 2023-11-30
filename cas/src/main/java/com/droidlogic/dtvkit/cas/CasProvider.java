package com.droidlogic.dtvkit.cas;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

public class CasProvider extends ContentProvider {
    private static final String TAG = "DtvCasProvider";
    public static final String CAS_PROVIDER_READ_PERMISSION =
            "com.droidlogic.android.cas.provider.permission.READ_USER_DATA";
    public static final String CAS_PROVIDER_WRITE_PERMISSION =
            "com.droidlogic.android.cas.provider.permission.WRITE_USER_DATA";

    private static final UriMatcher mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private DbOpenHelper mDbOpenHelper = null;
    private DbOpenHelper mMemoryDbOpenHelper = null;

    public static final String DB_NAME = "cas.db";
    /* versions:*/
    /* 1 = initial provider */
    public static final int DB_VERSION = 1;
    public static final int MATCH_MEMORY_ID_MASK = 0x100;
    public static final int MATCH_MEMORY_STAR_MASK = 0x200;
    public static final int MATCH_MAX_ID_NO_AUTOINCREMENT = 0x10;

    public static final String TABLE_SETTINGS = "settings";
    public static final int TABLE_SETTINGS_CODE = MATCH_MEMORY_ID_MASK + 1;
    public static final int TABLE_SETTINGS_ID_CODE = MATCH_MEMORY_STAR_MASK + TABLE_SETTINGS_CODE;
    public static final String TABLE_ACTIONS = "actions";
    public static final int TABLE_ACTIONS_CODE = MATCH_MEMORY_ID_MASK + 2;
    public static final int TABLE_ACTIONS_ID_CODE = MATCH_MEMORY_STAR_MASK + TABLE_ACTIONS_CODE;
    public static final String TABLE_SCREEN_MESSAGES = "screen_messages";
    public static final int TABLE_SCREEN_MESSAGES_CODE = MATCH_MAX_ID_NO_AUTOINCREMENT + 1;
    public static final String TABLE_SO_USER_MESSAGE = "so_user_message";
    public static final int TABLE_SO_USER_MESSAGE_CODE = MATCH_MAX_ID_NO_AUTOINCREMENT + 2;

    public static final String AUTHORITY = "com.droidlogic.android.cas.authorities";
    public static final String CONTENT_URI = "content://" + AUTHORITY + "/";

    static {
        mUriMatcher.addURI(AUTHORITY, TABLE_SETTINGS,
                TABLE_SETTINGS_CODE);
        mUriMatcher.addURI(AUTHORITY, TABLE_SETTINGS + "/*",
                TABLE_SETTINGS_ID_CODE);
        mUriMatcher.addURI(AUTHORITY, TABLE_ACTIONS,
                TABLE_ACTIONS_CODE);
        mUriMatcher.addURI(AUTHORITY,
                TABLE_ACTIONS + "/*",
                TABLE_ACTIONS_ID_CODE);
        mUriMatcher.addURI(AUTHORITY, TABLE_SCREEN_MESSAGES,
                TABLE_SCREEN_MESSAGES_CODE);
        mUriMatcher.addURI(AUTHORITY, TABLE_SO_USER_MESSAGE, TABLE_SO_USER_MESSAGE_CODE);
    }

    @Override
    public boolean onCreate() {
        mDbOpenHelper = new DbOpenHelper(getContext(), false);
        mMemoryDbOpenHelper = new DbOpenHelper(getContext(), true);
        return true;
    }

    private static class DbOpenHelper extends SQLiteOpenHelper {
        private final boolean isMemoryTable;

        public DbOpenHelper(final Context context, boolean memory) {
            super(context, memory ? null : DB_NAME, null, DB_VERSION);
            isMemoryTable = memory;
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (isMemoryTable) {
                String SQL_CREATE_TABLE_SETTINGS = "create table if not exists " +
                        TABLE_SETTINGS +
                        "(_id TEXT UNIQUE ON CONFLICT REPLACE, value TEXT);";
                String SQL_CREATE_ACTION_SETTINGS = "create table if not exists " +
                        TABLE_ACTIONS +
                        "(_id TEXT UNIQUE ON CONFLICT REPLACE, value TEXT);";
                db.execSQL(SQL_CREATE_TABLE_SETTINGS);
                db.execSQL(SQL_CREATE_ACTION_SETTINGS);
            } else {
                String SQL_CREATE_TABLE_SCREEN_MESSAGES_CODE = "create table if not exists " +
                        TABLE_SCREEN_MESSAGES +
                        "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "message_type INTEGER, attributed INTEGER, enhanced INTEGER, " +
                        "duration INTEGER, flashing INTEGER, banner INTEGER, " +
                        "coverage_code INTEGER, fingerprint_type INTEGER, scroll INTEGER, " +
                        "text BLOB, option BLOB, flag1 INTEGER);";
                String SQL_CREATE_TABLE_SO_USER_MESSAGE = "create table if not exists " +
                        TABLE_SO_USER_MESSAGE +
                        "(_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "message BLOB, flag1 INTEGER);";
                db.execSQL(SQL_CREATE_TABLE_SCREEN_MESSAGES_CODE);
                db.execSQL(SQL_CREATE_TABLE_SO_USER_MESSAGE);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    private void checkQueryPermissions() {
        if (Objects.requireNonNull(getContext()).checkCallingOrSelfPermission(
                CAS_PROVIDER_READ_PERMISSION) == PackageManager.PERMISSION_GRANTED)
            return;
        throw new SecurityException("No permission to read cas provider");
    }

    private void checkWritePermissions() {
        if (Objects.requireNonNull(getContext()).checkCallingOrSelfPermission(
                CAS_PROVIDER_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED)
            return;
        throw new SecurityException("No permission to write cas provider");
    }

    private String getTableName(final Uri uri) {
        String tableName = "";
        int match = mUriMatcher.match(uri);
        switch (match) {
            case TABLE_SETTINGS_CODE:
            case TABLE_SETTINGS_ID_CODE:
                tableName = TABLE_SETTINGS;
                break;
            case TABLE_ACTIONS_CODE:
            case TABLE_ACTIONS_ID_CODE:
                tableName = TABLE_ACTIONS;
                break;
            case TABLE_SCREEN_MESSAGES_CODE:
                tableName = TABLE_SCREEN_MESSAGES;
                break;
            case TABLE_SO_USER_MESSAGE_CODE:
                tableName = TABLE_SO_USER_MESSAGE;
                break;
        }
        return tableName;
    }

    private boolean isMemoryProvider(final Uri uri) {
        int match = mUriMatcher.match(uri);
        return (match & MATCH_MEMORY_ID_MASK) == MATCH_MEMORY_ID_MASK;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        checkWritePermissions();
        DbOpenHelper dbh = mDbOpenHelper;

        String table = getTableName(uri);
        if (isMemoryProvider(uri)) {
            dbh = mMemoryDbOpenHelper;
        }

        SQLiteDatabase db = dbh.getWritableDatabase();
        if (db != null) {
            Log.v(TAG, "delete db = " + db.getPath() + ", SQL uri:" +
                    uri + " selection:" +
                    selection + " selectionArgs:" +
                    (selectionArgs != null ? Arrays.toString(selectionArgs) : ""));
        } else {
            Log.v(TAG, "delete db null");
            return -1;
        }
        return db.delete(table, selection, selectionArgs);
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        checkWritePermissions();
        DbOpenHelper dbh = mDbOpenHelper;

        int match = mUriMatcher.match(uri);
        String table = getTableName(uri);
        if (isMemoryProvider(uri)) {
            dbh = mMemoryDbOpenHelper;
        }
        SQLiteDatabase db = dbh.getWritableDatabase();
        if (db != null) {
            Log.v(TAG, "insert db = " + db.getPath() + ", SQL uri:" +
                    uri + " values:" +
                    values.toString());
        } else {
            Log.v(TAG, "insert db null");
            return null;
        }
        long rowId = db.insert(table, null, values);
        int tableId = match & 0xff;
        Uri newUri = uri.buildUpon().fragment(values.toString()).build();
        if (tableId > MATCH_MAX_ID_NO_AUTOINCREMENT) {
            newUri = ContentUris.withAppendedId(uri, rowId);
        }
        Objects.requireNonNull(getContext()).getContentResolver().notifyChange(newUri,
                null);
        return newUri;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        checkQueryPermissions();
        DbOpenHelper dbh = mDbOpenHelper;

        int match = mUriMatcher.match(uri);
        String table = getTableName(uri);
        if (isMemoryProvider(uri)) {
            dbh = mMemoryDbOpenHelper;
        }

        String adjustSelection = selection;
        String[] adjustSelectionArgs = selectionArgs;
        if ((match & MATCH_MEMORY_STAR_MASK) == MATCH_MEMORY_STAR_MASK) {
            if (selection == null && selectionArgs == null) {
                adjustSelection = "_id=?1";
                adjustSelectionArgs = new String[]{uri.getLastPathSegment()};
            }
        }
        SQLiteDatabase db = dbh.getReadableDatabase();
        if (db != null) {
            Log.v(TAG, "query db = " + db.getPath() +
                    ", SQL uri:" + uri +
                    " projection:" + (projection != null ? Arrays.toString(projection) : "") +
                    " selection:" + selection + " selectionArgs:" +
                    (selectionArgs != null ? Arrays.toString(selectionArgs) : "") +
                    " sortOrder:" + sortOrder);
        } else {
            Log.v(TAG, "query db null");
            return null;
        }
        return db.query(table, projection, adjustSelection, adjustSelectionArgs,
                null, null, sortOrder);
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        checkWritePermissions();
        DbOpenHelper dbh = mDbOpenHelper;

        int match = mUriMatcher.match(uri);
        String table = getTableName(uri);
        if (isMemoryProvider(uri)) {
            dbh = mMemoryDbOpenHelper;
        }
        SQLiteDatabase db = dbh.getWritableDatabase();
        if (db != null) {
            Log.d(TAG, "update db = " + db.getPath() + ", SQL uri:" +
                    uri + " values:" +
                    (values != null ? values.toString() : "") + " selection:"
                    + selection + " selectionArgs:" +
                    (selectionArgs != null ? Arrays.toString(selectionArgs) : ""));
        } else {
            Log.v(TAG, "update db null");
            return -1;
        }
        int ret = db.update(table, values, selection, selectionArgs);
        if (ret > 0 && values != null) {
            int tableId = match & 0xff;
            Uri newUri = uri.buildUpon().fragment(values.toString()).build();
            if (tableId > MATCH_MAX_ID_NO_AUTOINCREMENT) {
                Objects.requireNonNull(getContext()).getContentResolver().notifyChange(uri,
                        null);
            } else {
                Objects.requireNonNull(getContext()).getContentResolver().notifyChange(newUri,
                        null);
            }
        }
        return ret;
    }
}