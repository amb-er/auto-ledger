package space.pengtao.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本地 SQLite 账本，纯本地存储，不联网。 */
public class LedgerDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "ledger.db";
    private static final int DB_VERSION = 5;
    private static final String TABLE = "transactions";

    public LedgerDbHelper(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "amount REAL NOT NULL," +
                "category TEXT," +
                "merchant TEXT," +
                "source TEXT," +
                "note TEXT," +
                "comment TEXT," +
                "status INTEGER NOT NULL DEFAULT 1," +
                "member TEXT DEFAULT ''," +
                "remark TEXT DEFAULT ''," +
                "time INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN comment TEXT");
        }
        if (oldV < 3) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN status INTEGER NOT NULL DEFAULT 1");
        }
        if (oldV < 4) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN member TEXT DEFAULT ''");
        }
        if (oldV < 5) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN remark TEXT DEFAULT ''");
        }
    }

    public long insert(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount", t.amount);
        v.put("category", t.category);
        v.put("merchant", t.merchant);
        v.put("source", t.source);
        v.put("note", t.note);
        v.put("comment", t.comment);
        v.put("status", t.status);
        v.put("member", t.member);
        v.put("remark", t.remark);
        v.put("time", t.time);
        return getWritableDatabase().insert(TABLE, null, v);
    }

    /** 批量插入，用于 CSV 导入；返回成功条数。 */
    public int insertBatch(List<Transaction> list) {
        if (list == null || list.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        int count = 0;
        try {
            for (Transaction t : list) {
                ContentValues v = new ContentValues();
                v.put("amount", t.amount);
                v.put("category", t.category);
                v.put("merchant", t.merchant);
                v.put("source", t.source);
                v.put("note", t.note);
                v.put("comment", t.comment);
                v.put("status", t.status);
                v.put("member", t.member);
                v.put("remark", t.remark);
                v.put("time", t.time);
                long id = db.insert(TABLE, null, v);
                if (id > 0) count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return count;
    }

    /** 回写某条记录的 AI 辣评。 */
    public void updateComment(long id, String comment) {
        ContentValues v = new ContentValues();
        v.put("comment", comment);
        getWritableDatabase().update(TABLE, v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 确认一条待确认账目。 */
    public void confirm(long id) {
        ContentValues v = new ContentValues();
        v.put("status", 1);
        getWritableDatabase().update(TABLE, v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 更新一条账目（编辑用）。 */
    public void update(Transaction t) {
        ContentValues v = new ContentValues();
        v.put("amount", t.amount);
        v.put("category", t.category);
        v.put("merchant", t.merchant);
        v.put("source", t.source);
        v.put("note", t.note);
        v.put("comment", t.comment);
        v.put("status", t.status);
        v.put("time", t.time);
        getWritableDatabase().update(TABLE, v, "id = ?", new String[]{String.valueOf(t.id)});
    }

    /** 删除一条账目。 */
    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    /** 清空所有账目。 */
    public void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /** 按关键词模糊搜索（商户/分类/备注/辣评）。 */
    public List<Transaction> search(String keyword) {
        List<Transaction> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String kw = "%" + keyword + "%";
        Cursor c = db.query(TABLE, null,
                "merchant LIKE ? OR category LIKE ? OR note LIKE ? OR comment LIKE ?",
                new String[]{kw, kw, kw, kw}, null, null, "time DESC", "500");
        while (c.moveToNext()) out.add(readCursor(c));
        c.close();
        return out;
    }

    public List<Transaction> list(int limit) {
        List<Transaction> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null,
                "time DESC", String.valueOf(limit));
        while (c.moveToNext()) out.add(readCursor(c));
        c.close();
        return out;
    }

    private Transaction readCursor(Cursor c) {
        Transaction t = new Transaction();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.amount = c.getDouble(c.getColumnIndexOrThrow("amount"));
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
        t.source = c.getString(c.getColumnIndexOrThrow("source"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.comment = c.getString(c.getColumnIndexOrThrow("comment"));
        t.status = c.getInt(c.getColumnIndexOrThrow("status"));
        t.member = c.getString(c.getColumnIndexOrThrow("member"));
        t.remark = c.getString(c.getColumnIndexOrThrow("remark"));
        t.time = c.getLong(c.getColumnIndexOrThrow("time"));
        return t;
    }

    /** 从 startMs 起的支出总额（正数）。 */
    public double monthExpense(long startMs) {
        double sum = 0;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT SUM(amount) FROM " + TABLE + " WHERE amount < 0 AND time >= ?",
                new String[]{String.valueOf(startMs)});
        if (c.moveToFirst() && !c.isNull(0)) {
            sum = -c.getDouble(0);
        }
        c.close();
        return sum;
    }

    /** 从 startMs 起的收入总额（正数）。 */
    public double monthIncome(long startMs) {
        double sum = 0;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT SUM(amount) FROM " + TABLE + " WHERE amount > 0 AND time >= ?",
                new String[]{String.valueOf(startMs)});
        if (c.moveToFirst() && !c.isNull(0)) {
            sum = c.getDouble(0);
        }
        c.close();
        return sum;
    }

    /** 回填最近一条待补（amount=0、source 匹配）记录，返回记录 id，未回填返回 -1。 */
    public long fillPending(double amount, String source) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.query(TABLE, new String[]{"id"},
                "amount = 0 AND source = ?", new String[]{source},
                null, null, "time DESC", "1");
        if (c.moveToFirst()) {
            long id = c.getLong(0);
            c.close();
            ContentValues v = new ContentValues();
            v.put("amount", amount);
            db.update(TABLE, v, "id = ?", new String[]{String.valueOf(id)});
            return id;
        }
        c.close();
        return -1;
    }

    /** 检查交易单号是否已存在（CSV 导入去重用）。 */
    public boolean hasNote(String note) {
        if (note == null || note.isEmpty()) return false;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, new String[]{"id"},
                "note = ?", new String[]{note}, null, null, null, "1");
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    /** 从 startMs 起各分类的支出汇总（Map<分类, 金额正数>），按金额升序。 */
    public Map<String, Double> categoryStats(long startMs) {
        Map<String, Double> map = new LinkedHashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT category, SUM(amount) FROM " + TABLE +
                        " WHERE amount < 0 AND time >= ? GROUP BY category ORDER BY SUM(amount) ASC",
                new String[]{String.valueOf(startMs)});
        while (c.moveToNext()) {
            String cat = c.getString(0);
            if (cat == null || cat.isEmpty()) cat = "其他";
            map.put(cat, -c.getDouble(1));
        }
        c.close();
        return map;
    }
}
