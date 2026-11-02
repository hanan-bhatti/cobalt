import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Process;
import android.system.Os;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Emulator/debug-only fixture writer. Uses Android SQLite transactions; never replaces a database or deletes WAL/SHM. */
public final class CobaltLegacyNoteSeeder {
    private static final File ROOT = new File("/data/user/0/com.hanan_bhatti.cobalt.debug");
    private static final int[] SIZES = {1000,2000,4000,8000,16000,32000,64000,128000,256000,512000,1000000,2000000};
    private static final String[] STYLES = {"markdown","links","tables","tasks-code","clean-text"};
    private static final String[] TEXT = {
        "## Ideas and observations\n\nA **bold** thought, some *emphasis*, and `inline code`.\n\n> Keep this complete original note.\n\n",
        "[Reference](https://example.org/reference)\n\n[Archive](https://example.org/archive)\n\n",
        "| Topic | Status | Value |\n| --- | --- | --- |\n| Notes | Ready | 42 |\n| Archive | Local | 19 |\n\n",
        "- [ ] Review this note\n- [x] Keep local data\n\n```kotlin\nval answer = 42\nprintln(answer)\n```\n\n",
        "A quiet morning and a clean desk. This paragraph contains ordinary words and no links.\n\n"
    };
    private static long scalar(SQLiteDatabase db, String sql, String[] args) {
        try (Cursor c = db.rawQuery(sql,args)) { if (!c.moveToFirst()) throw new IllegalStateException("No result"); return c.getLong(0); }
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        return hex.toString();
    }
    private static void atomic(File file, String text) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = atomic.startWrite();
        try { output.write(text.getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(output); }
        catch (Exception error) { atomic.failWrite(output); throw error; }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Expected safe batch ID");
        if (!(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))) throw new IllegalStateException("Emulator only");
        if (Process.myUid() < 10000 || Os.stat(ROOT.getAbsolutePath()).st_uid != Process.myUid()) throw new SecurityException("Wrong package UID");
        String batch = args[0];
        long before, noteCount, totalBytes = 0;
        int added = 0;
        List<String> requiredNames = new ArrayList<>();
        List<String> requiredTypes = new ArrayList<>();
        StringBuilder manifest = new StringBuilder("{\"batch\":\"").append(batch).append("\",\"notes\":[");
        SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(ROOT,"databases/cobalt_db").getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);
        try {
            if (db.getVersion() != 9) throw new IllegalStateException("Expected installed Room v9 schema");
            if (scalar(db,"SELECT count(*) FROM saved_items WHERE id LIKE ?",new String[]{batch+"%"}) != 0) throw new IllegalStateException("Batch exists");
            before = scalar(db,"SELECT count(*) FROM saved_items",null);
            try (Cursor schema = db.rawQuery("PRAGMA table_info(saved_items)",null)) {
                while (schema.moveToNext()) if (schema.getInt(3) != 0) {
                    requiredNames.add(schema.getString(1)); requiredTypes.add(schema.getString(2));
                }
            }
            db.beginTransaction();
            try {
                long now = System.currentTimeMillis();
                for (int style = 0; style < STYLES.length; style++) for (int size : SIZES) {
                    String title = batch+"-"+STYLES[style]+"-"+(size/1000)+"KB";
                    String id = batch+"-"+STYLES[style]+"-"+size;
                    StringBuilder body = new StringBuilder(size).append("# ").append(title).append("\n\n");
                    while (body.length()+TEXT[style].length() <= size) body.append(TEXT[style]);
                    while (body.length() < size-1) body.append(' ');
                    if (body.length() < size) body.append('\n');
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    if (bytes.length != size) throw new IllegalStateException("Fixture length mismatch");
                    ContentValues values = new ContentValues();
                    for (int i=0; i<requiredNames.size(); i++) {
                        if (requiredTypes.get(i).contains("TEXT")) values.put(requiredNames.get(i),"");
                        else values.put(requiredNames.get(i),0);
                    }
                    values.put("id",id); values.put("type","TEXT"); values.put("title",title); values.put("content",body.toString());
                    values.put("timestamp",now+added); values.put("updatedAt",now+added); values.put("orderIndex",(double)(now+added));
                    values.put("foldersJson","[]"); values.put("genresJson","[]"); values.put("watchProvidersJson","[]");
                    values.put("sizeBytes",size); values.put("bodyLocation","INLINE"); values.putNull("contentHash");
                    values.put("byteLength",0); values.put("formatVersion",0); values.put("preview",""); values.put("bodyMissing",0);
                    db.insertOrThrow("saved_items",null,values);
                    if (added > 0) manifest.append(',');
                    manifest.append("{\"id\":\"").append(id).append("\",\"style\":\"").append(STYLES[style])
                        .append("\",\"bytes\":").append(size).append(",\"sha256\":\"").append(hash(bytes)).append("\"}");
                    added++; totalBytes += size;
                }
                if (scalar(db,"SELECT count(*) FROM saved_items",null) != before+60) throw new IllegalStateException("Count mismatch");
                if (scalar(db,"SELECT sum(length(CAST(content AS BLOB))) FROM saved_items WHERE id LIKE ?",new String[]{batch+"%"}) != totalBytes) throw new IllegalStateException("Stored size mismatch");
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            noteCount = scalar(db,"SELECT count(*) FROM saved_items WHERE type IN ('TEXT','CODE')",null);
            try (Cursor check = db.rawQuery("PRAGMA quick_check",null)) {
                if (!check.moveToFirst() || !"ok".equals(check.getString(0))) throw new IllegalStateException("SQLite integrity check failed");
            }
        } finally { db.close(); }
        manifest.append("],\"count\":").append(added).append(",\"totalBytes\":").append(totalBytes).append('}');
        atomic(new File(ROOT,"files/"+batch+".json"),manifest.toString());
        atomic(new File(ROOT,"shared_prefs/note_migration.xml"),
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map>"+
            "<boolean name=\"migration_required\" value=\"true\"/><int name=\"completed_format\" value=\"0\"/>"+
            "<int name=\"prompt_shown\" value=\"0\"/><string name=\"phase\">READY</string>"+
            "<int name=\"total\" value=\""+noteCount+"\"/><int name=\"converted\" value=\"0\"/>"+
            "<int name=\"verified\" value=\"0\"/><int name=\"failed\" value=\"0\"/>"+
            "<long name=\"last_run\" value=\"0\"/><string name=\"errors\">[]</string></map>");
        System.out.println("{\"added\":"+added+",\"totalBytes\":"+totalBytes+",\"existingPreserved\":"+before+
            ",\"totalNotes\":"+noteCount+",\"migrationStarted\":false,\"batch\":\""+batch+"\"}");
    }
}
