package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/** Converts every MySQL BINARY(16) UUID column to canonical CHAR(36), preserving rows and keys. */
public class V3__UuidChar36 extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        String catalog = connection.getCatalog();
        DatabaseMetaData md = connection.getMetaData();
        List<Table> tables = new ArrayList<>();
        try (ResultSet rs = md.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                Table table = readTable(md, catalog, name);
                if (!table.uuidColumns.isEmpty()) tables.add(table);
            }
        }

        // Read and validate the entire schema before the first DDL statement (MySQL DDL commits implicitly).
        for (Table t : tables) {
            for (Column c : t.uuidColumns) {
                if (t.columns.stream().anyMatch(existing -> existing.name.equalsIgnoreCase(c.shadowName()))) {
                    throw new SQLException("Migration shadow column already exists: " + t.name + "." + c.shadowName());
                }
            }
        }

        try (Statement s = connection.createStatement()) {
            for (Table t : tables) {
                for (Column c : t.uuidColumns) {
                    s.execute("ALTER TABLE " + q(t.name) + " ADD COLUMN " + q(c.shadowName()) +
                            " CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL");
                    s.execute("UPDATE " + q(t.name) + " SET " + q(c.shadowName()) +
                            " = BIN_TO_UUID(" + q(c.name) + ", 0)");
                }
            }

            for (Table t : tables) for (ForeignKey fk : t.foreignKeys)
                s.execute("ALTER TABLE " + q(t.name) + " DROP FOREIGN KEY " + q(fk.name));

            for (Table t : tables) for (Index ix : t.indexes)
                s.execute("ALTER TABLE " + q(t.name) + (ix.primary ? " DROP PRIMARY KEY" : " DROP INDEX " + q(ix.name)));

            for (Table t : tables) {
                for (Column c : t.uuidColumns) {
                    s.execute("ALTER TABLE " + q(t.name) + " DROP COLUMN " + q(c.name));
                    s.execute("ALTER TABLE " + q(t.name) + " CHANGE COLUMN " + q(c.shadowName()) + " " + q(c.name) +
                            " CHAR(36) CHARACTER SET ascii COLLATE ascii_bin " + (c.nullable ? "NULL" : "NOT NULL"));
                }
            }

            for (Table t : tables) for (Index ix : t.indexes) {
                String cols = String.join(", ", ix.parts.stream().map(IndexPart::sql).toList());
                String ddl = ix.primary ? "ADD PRIMARY KEY (" + cols + ")" :
                        "ADD " + (ix.unique ? "UNIQUE " : "") + "INDEX " + q(ix.name) + " (" + cols + ")";
                s.execute("ALTER TABLE " + q(t.name) + " " + ddl);
            }

            for (Table t : tables) for (ForeignKey fk : t.foreignKeys) {
                String local = String.join(", ", fk.columns);
                String remote = String.join(", ", fk.referencedColumns);
                s.execute("ALTER TABLE " + q(t.name) + " ADD CONSTRAINT " + q(fk.name) +
                        " FOREIGN KEY (" + local + ") REFERENCES " + q(fk.referencedTable) + " (" + remote + ")" +
                        " ON DELETE " + fk.onDelete + " ON UPDATE " + fk.onUpdate);
            }
        }
    }

    private static Table readTable(DatabaseMetaData md, String catalog, String name) throws SQLException {
        Table t = new Table(name);
        try (ResultSet rs = md.getColumns(catalog, null, name, "%")) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                int type = rs.getInt("DATA_TYPE");
                int size = rs.getInt("COLUMN_SIZE");
                boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                Column c = new Column(col, nullable);
                t.columns.add(c);
                if (type == java.sql.Types.BINARY && size == 16) t.uuidColumns.add(c);
            }
        }
        Map<String, Index> indexes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = md.getIndexInfo(catalog, null, name, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (indexName == null || column == null) continue;
                boolean primary = "PRIMARY".equalsIgnoreCase(indexName);
                Index ix = indexes.computeIfAbsent(indexName, n -> new Index(n, primary, !rsBoolean(rs, "NON_UNIQUE")));
                int prefix = rs.getInt("SUB_PART");
                String direction = "D".equalsIgnoreCase(rsString(rs, "ASC_OR_DESC")) ? " DESC" : "";
                ix.parts.add(new IndexPart(rs.getInt("ORDINAL_POSITION"), column, prefix, direction));
            }
        }
        for (Index ix : indexes.values()) { ix.parts.sort(Comparator.comparingInt(p -> p.ordinal)); t.indexes.add(ix); }

        Map<String, ForeignKey> fks = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (ResultSet rs = md.getImportedKeys(catalog, null, name)) {
            while (rs.next()) {
                String fkName = rs.getString("FK_NAME");
                if (fkName == null) throw new SQLException("Unnamed FK in " + name + " is not supported by this migration");
                ForeignKey fk = fks.computeIfAbsent(fkName, n -> new ForeignKey(n,
                        rsString(rs, "PKTABLE_NAME"), rule(rsInt(rs, "DELETE_RULE")), rule(rsInt(rs, "UPDATE_RULE"))));
                fk.parts.add(new ForeignKeyPart(rs.getInt("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME")));
            }
        }
        for (ForeignKey fk : fks.values()) {
            fk.parts.sort(Comparator.comparingInt(p -> p.seq));
            fk.columns.addAll(fk.parts.stream().map(p -> q(p.local)).toList());
            fk.referencedColumns.addAll(fk.parts.stream().map(p -> q(p.remote)).toList());
            t.foreignKeys.add(fk);
        }
        t.indexes.sort(Comparator.comparing((Index i) -> !i.primary).thenComparing(i -> i.name));
        return t;
    }

    private static boolean rsBoolean(ResultSet rs, String col) { try { return rs.getBoolean(col); } catch (SQLException e) { throw new IllegalStateException(e); } }
    private static String rsString(ResultSet rs, String col) { try { return rs.getString(col); } catch (SQLException e) { throw new IllegalStateException(e); } }
    private static int rsInt(ResultSet rs, String col) { try { return rs.getInt(col); } catch (SQLException e) { throw new IllegalStateException(e); } }
    private static String rule(int rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            default -> "RESTRICT";
        };
    }
    private static String q(String id) { return "`" + id.replace("`", "``") + "`"; }

    private static final class Table {
        final String name; final List<Column> columns = new ArrayList<>(), uuidColumns = new ArrayList<>();
        final List<Index> indexes = new ArrayList<>(); final List<ForeignKey> foreignKeys = new ArrayList<>();
        Table(String name) { this.name = name; }
    }
    private record Column(String name, boolean nullable) { String shadowName() { return "__uuid36_" + name; } }
    private static final class Index {
        final String name; final boolean primary, unique; final List<IndexPart> parts = new ArrayList<>();
        Index(String name, boolean primary, boolean unique) { this.name = name; this.primary = primary; this.unique = unique; }
    }
    private record IndexPart(int ordinal, String column, int prefix, String direction) {
        String sql() { return q(column) + (prefix > 0 ? "(" + prefix + ")" : "") + direction; }
    }
    private static final class ForeignKey {
        final String name, referencedTable, onDelete, onUpdate;
        final List<ForeignKeyPart> parts = new ArrayList<>(); final List<String> columns = new ArrayList<>(), referencedColumns = new ArrayList<>();
        ForeignKey(String name, String referencedTable, String onDelete, String onUpdate) {
            this.name = name; this.referencedTable = referencedTable; this.onDelete = onDelete; this.onUpdate = onUpdate;
        }
    }
    private record ForeignKeyPart(int seq, String local, String remote) { }
}
