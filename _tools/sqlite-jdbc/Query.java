import java.io.File;
import java.sql.*;

/**
 * Pembaca SQLite read-only tanpa perlu sqlite3/python.
 *
 * Pemakaian:
 *   javac -cp sqlite-jdbc.jar Query.java
 *   java -cp .:sqlite-jdbc.jar:slf4j-api.jar:slf4j-nop.jar Query /path/db.sqlite "SELECT ..."
 *
 * Tanpa argumen SQL, program menampilkan daftar tabel + jumlah barisnya.
 * Database selalu dibuka mode read-only, tidak akan mengubah file.
 */
public class Query {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Pemakaian: java Query <file.db> [SQL]");
            System.exit(1);
        }
        File db = new File(args[0]).getAbsoluteFile();
        if (!db.isFile()) {
            System.err.println("File tidak ditemukan: " + db);
            System.exit(1);
        }
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:file:" + db.getPath() + "?mode=ro";
        try (Connection c = DriverManager.getConnection(url)) {
            if (args.length > 1) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (i > 1) sb.append(' ');
                    sb.append(args[i]);
                }
                run(c, sb.toString());
            } else {
                introspect(c);
            }
        }
    }

    static void introspect(Connection c) throws SQLException {
        java.util.List<String> tables = new java.util.ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (r.next()) tables.add(r.getString(1));
        }
        System.out.println("== TABLES ==");
        for (String t : tables) {
            long n = -1;
            try (Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT count(*) FROM \"" + t + "\"")) {
                r.next();
                n = r.getLong(1);
            } catch (SQLException ignored) {
            }
            System.out.println(t + ": " + n);
        }
    }

    static void run(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            ResultSetMetaData m = r.getMetaData();
            int n = m.getColumnCount();
            StringBuilder h = new StringBuilder();
            for (int i = 1; i <= n; i++) {
                if (i > 1) h.append(" | ");
                h.append(m.getColumnLabel(i));
            }
            System.out.println(h);
            while (r.next()) {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) b.append(" | ");
                    Object o = r.getObject(i);
                    b.append(o == null ? "NULL" : o);
                }
                System.out.println(b);
            }
        }
    }
}
